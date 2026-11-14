# 31 — Guard intent, hunt gate, and the shield duel (2026-11-14, 02:40–03:05)

Follow-up to `30_TEST_RUN_2026-11-14.md`. That run produced five bugs; this one fixes
two of them and turns the third open question — does the shield actually block — into a
measured result.

## 1. ModuleGuard now honours intent

**Bug:** `deactivate()` replayed the snapshot taken at `activate()`, so a module the
operator enabled while the bot ran was switched off the moment the brain stopped.

**Fix:** the guard records any change it did not make as *intent*
(`ModuleGuard.INTENT`, static), keeps it across ticks, and applies it on teardown.
`ModuleGuard.setIntent(name, enabled)` gives code a deterministic forced state that
survives both the 20-tick re-enable pass and teardown. Intent OFF also stops the guard
from re-enabling a module it thinks should be on.

**Evidence** — `scripts/guard-intent-check.sh` against the live worker, run after
deploying `0.1.0+4ba26e1`:

```
initial swarm-shield = true
--- 1. start BotBrain (guard active) ---
--- 2. operator toggles swarm-shield while the guard runs ---
PASS  swarm-shield survives guard tick (expected false, got false)
--- 3. stop BotBrain (guard teardown) ---
PASS  swarm-shield survives guard teardown (expected false, got false)
--- 4. force API on, with the guard running ---
PASS  forced-on swarm-shield holds during guard (expected true, got true)
--- 5. force API off, with the guard running ---
PASS  forced-off swarm-shield holds during guard (expected false, got false)
--- 6. forced-off survives guard teardown ---
PASS  forced-off swarm-shield survives teardown (expected false, got false)
--- 7. forced-on outranks a later manual toggle ---
PASS  forced-on swarm-shield survives a manual toggle (expected true, got true)

RESULT: PASS (all intent checks held)
```

Log lines from the same run show the mechanism, not just the outcome:

```
[ModuleGuard] Manual toggle kept: swarm-shield off
[ModuleGuard] Restored guarded module state (12 changes, 1 kept by intent)
[ModuleGuard] Applied intent: swarm-shield on
```

Two harness endpoints exist for this: `GET /harness/module?name=` returns
`{active, intent}` and `POST /harness/force {name, enabled}` / `{name, clear:true}` sets
or clears intent.

## 2. BotBrain refuses a hunt it cannot survive

**Gates before HUNTING** (`huntRefusalReason()`):

| gate | rule | default |
|---|---|---|
| weapon | held stack must carry the `minecraft:weapon` component | — |
| health | `getHealth() >= hunt-min-health` | 14.0 |
| food | hunger bar `>= hunt-min-food` | 6 |
| escape | at least 2 standable adjacent exits **and** Baritone provider present | — |

A refusal logs `Hunt refused: <reason>`, fails the task honestly, and retries in 20 s
instead of the 120 s failure cooldown, because conditions can change in seconds.

**Evidence** — same build, driven from the harness with real state changes:

| case | setup | log line |
|---|---|---|
| A no weapon | `/clear`, brain on | `Hunt refused: no effective weapon (held: minecraft:air)` |
| B low health | iron sword held, `max_health base set 8` | `Hunt refused: health too low (8.0 < 14.0)` |
| C no retreat | sword + full health, boxed in on the platform | `Hunt refused: no retreat path (open exits 0 < 2)` |
| D allowed | sword, 20 hp, hunger 20, open platform | `Food hunt started (food=0 target=20)` |

The state snapshot exposes the same reason (`huntRefusal`) so a script can assert it.

### Two version quirks found while staging this

- **`/gamerule naturalRegeneration false` is rejected** on this MC build
  (`Incorrect argument for command`), and so is `natural_regeneration`. Use
  `/attribute <player> minecraft:max_health base set <n>` to create a deterministic
  low-health state instead — it applies immediately and clamps current HP.
- **The client's `ATTACK_DAMAGE` attribute ignores the held item here**: 1.0 while
  holding an iron sword. The gate therefore reads the weapon *component*, not the
  attribute. Do not rebuild the gate on the attribute.

Also: setting max health to 8 and then applying 12 damage **kills** the player. Obvious
in hindsight; it cost one death and one lost sword. Set the cap, do not also hit them.

## 3. SwarmShield, staged 1v1: PROVEN

Rig: stone sky platform at y=150, x≈-516 z≈602, far from the base. Peaceful while
building, `difficulty easy` + `time night` for the duel. Attacker: an **invulnerable**
tagged zombie 2.5 blocks away, so KillAura cannot end the fight before the sample window
ends. Driver: `scripts/shield_duel_test.py`, which forces the module state through
`/harness/force` (no toggle-ordering tricks), samples `/harness/state` every 150 ms for
14 s, and prints the HP trace.

| run | module | min HP | max HP | HP drop events | `shieldBlocking` |
|---|---|---|---|---|---|
| control | forced OFF | **13.0** | 18.5 | **5** (`16.0, 15.5, 14.0, 14.5, 13.0`) | false |
| experiment | forced ON | **20.0** | 20.0 | **0** | **true** |

Same arena, same zombie type, same window, only the module differs. The control run took
five hits; the shield run took none while the client reported it was holding the use key
for a shield. Verdict: **`DONE`** — not just "equips the offhand", but blocks damage.

Honest caveats:
- `shieldBlocking` reads `SwarmShield.isBlocking()` (this module holding the use key),
  not vanilla `isBlocking()`. Damage is the independent evidence.
- The offhand read at setup showed `None` because the module equips only once KillAura
  has a target; the same run then reports `blocking=true`, so the equip did happen. The
  next test should sample the offhand after target acquisition, not before.
- Flee remains `CODE ONLY`: not re-tested after the repath-throttle fix.

## Final state left behind

- Both clients on `0.1.0+e02c64f` at time of the duel; final jar restamped after the
  docs commit (see the commit it points at).
- World: peaceful, day, no zombies, chamber blocks removed, no lava.
- jamal: 20 hp / 20 hunger at the sky platform; BotBrain, KillAura and SwarmShield off,
  all forced intents cleared.
- Repo: guard fix, hunt gate, harness endpoints, and both test drivers committed
  separately and pushed.
