# 29 — Reference Corpus Recon (capability map + retrieval index)

Status stamp: 2026-11-14, HEAD `04c09a0`. Corpus read from `reference/` (11 addons, 1,401 files).
Purpose: make everything we learned **retrievable** — by capability, not by remembering which addon had it.

> **Truth rule.** Every card below cites a real file + symbol. Anything I inferred rather than read is
> marked `[derived]`. If a citation and the source disagree, the source wins — fix this doc.

---

## 0. How to use this document

1. **Before writing a module**, search this file for the capability ("shield", "rotation", "mining", "flee",
   "trade", "placement"). If a card exists, read its source citation before designing.
2. **Status values mean:**
   - `ADOPT` — verified API exists in our jar; build it when its turn comes.
   - `CANDIDATE` — good idea, needs a decision or a test first.
   - `REFERENCE-ONLY` — read for understanding; do not port.
   - `REJECTED` — deliberately not doing this; reason recorded.
3. **Section 7 is the keyword index.** It maps plain words to files so a future session can grep fast.
4. **When reality changes, edit the card** — same rule as the rest of `docs/`.

---

## 1. Corpus inventory

| addon | files | what it is | verdict |
|---|---|---|---|
| `meteor-client-master` | ~800 | Meteor itself, 26.3 | **primary API source** |
| `MeteorPlus-26.3` | ~230 | big feature addon, mixin-heavy | **best mechanics source** |
| `Seija-Printer-26.3` | ~90 | schematic printer, precision placement | **M6 blueprint source** |
| `dino-printer-main` | ~70 | printer + rotation manager | **rotation source** |
| `PowHax-main` | ~60 | PvP/misc utilities | select cards (velocity, anti-trample) |
| `6Bees-main` | ~40 | base-finding macros | mostly REJECTED |
| `Numby-hack-main` | ~50 | small hacks incl. SafeFire | one gem (SafeFire) |
| `Baritone-Controller-main` | ~15 | Baritone API driver | **pathing source** |
| `meteor-villager-roller-main` | ~20 | lectern re-roll bot | **KEEP — enchant pipeline** |
| `MeteorPlus` (older) | ~40 | superseded by 26.3 fork | skip |
| misc utilities | ~80 | shared bits | per-card |

Coverage: **1,401 files inventoried, ~40 read in depth (~9,000 lines)**. Backlog in §8.

---

## 2. Capability cards

### C1 — Lava/fire immunity (SafeFire)
- **Status:** `ADOPT` (first build item)
- **Source:** `Numby-hack-main/.../general/SafeFire.java` (30 lines)
- **Mechanism:** handles `CollisionShapeEvent` and substitutes a full cube for fire/lava block states,
  so the client's own collision math refuses to walk you in. No packets, no server interaction.
- **Why it matters:** the bot dies to lava more than to players. Fire/lava in the path is invisible to
  pathfinding lookahead until it is too late.
- **Our use:** always-on survival module for every bot.
- **Risk:** client-side only — if the bot is *already* standing in lava it still burns; it prevents entry,
  not damage. Also must not fire while a task genuinely wants to cross lava (bridging) — gate on task type.
- **Verified:** `CollisionShapeEvent` exists in our Meteor 26.3 jar.

### C2 — Rotation that survives movement (move-fix)
- **Status:** `ADOPT` (second build item)
- **Source:** `dino-printer-main/.../utils/RotationManager.java` (287 lines)
- **Mechanism:** a **priority queue** of rotation requests plus two separate concerns:
  1. `ServerboundMovePlayerPacket.Rot` spoofing so servers see the look direction without the client
     snapping the camera;
  2. **move-fix** — `setRotationPlayerInput` / `getInputFromRelativeYaw` (credited there to Lambda Client)
     which recomputes WASD relative to the spoofed yaw. Without it, rotating while walking makes the bot
     walk in the wrong direction and rubberband.
- **Secondary source:** `Seija-Printer-26.3/.../util/ShadyRotation.java` (179 lines) — simpler per-tick
  interpolation with a completion callback and a `running` flag. Fallback if the queue is too invasive.
- **Our use:** one shared rotation primitive for mining, placing, and combat. Fixes the mining drift we
  already see, and makes combat rotation honest instead of cosmetic.
- **Risk:** move-fix touches input at a low level; a bug there means the bot walks into walls. Test with
  a straight-line walk + rotate-to-block before trusting it.

### C3 — Shield / offhand layer
- **Status:** `CANDIDATE` (approved direction, not yet built)
- **Sources:**
  - `meteor-client-master/.../combat/Offhand.java` — equips a chosen item to offhand, `InvUtils.move().from(slot).toOffhand()`
  - `Numby-hack-main/.../ShieldSwap.java` — same, shield-specific
  - `meteor-client-master/.../combat/AutoTotem.java` — public `locked` flag; `PlayerUtils.possibleHealthReductions(explosion, fall)` for "would this kill me"
- **Mechanism:** hold `Options.keyUse` to block; equip via `InvUtils`; coordinate with totem by reading
  `AutoTotem.locked` so the shield never steals the totem slot at lethal HP.
- ****Critical constraint discovered:** `MeteorPlus/.../combat/TriggerBot.java` `onTick()` returns early on
  `mc.player.isBlocking()`. **TriggerBot cannot attack while shielding.** Meteor's `KillAura` has no such
  check. → **The shield must pair with KillAura, never with TriggerBot.** This alone invalidated a draft design.
- **Our use:** companion module: observe `KillAura.getTarget()` / `KillAura.attacking`, raise shield when a
  target is in range, keep attacking, walk away when HP drops (see C5).
- **Risk:** blocking slows movement 100%→20%; flee must not run while blocking, or the bot "runs" at
  sneak speed and dies.

### C4 — Attack layer we do not need to write
- **Status:** `ADOPT AS-IS` (stop rewriting Meteor)
- **Sources:** `meteor-client-master/.../combat/KillAura.java` (512 lines), `MeteorPlus/.../killaura/modes/Matrix.java`
- **Facts:** `attacking` and `getTarget()` are **public**; `attack`, `delayCheck`, `entityCheck` are private
  (extend by mixin, MeteorPlus-style, or just observe). Swing is `SwingMode.Both.attackSwing(hand)`.
  `Matrix` verifies hits by raycast before swinging — a cheap legitimacy win on anarchy servers.
- **Our use:** KillAura does targeting + timing + packets. We only add: shield (C3), flee (C5), arrow
  avoidance (C6), and a target filter that excludes fleet members (C11).
- **Risk:** none — this is the "don't duplicate" rule that killed the first combat attempt.

### C5 — Disengage / flee
- **Status:** `ADOPT` (step 4)
- **Sources:** `MeteorPlus/.../combat/Hunt.java` (`PathManagers.get().moveTo(entity.blockPosition())`),
  Meteor `AutoWalk` (`PathManagers.get().moveInDirection(yaw)`), `SafetyNet`, `AutoLeave`, `AutoLogPlus`
- **Mechanism:** approach and retreat are both Baritone goals, not key mashing. `Hunt` shows the whole
  approach loop is 3 lines once you accept the path manager as the movement authority.
- **Our use:** flee = `moveInDirection(awayYaw)` with KillAura disabled while retreating, re-engaged when
  distance is restored. Last resort: `AutoLogPlus`-style disconnect.
- **Risk:** Baritone owns movement; if a task is also pathing, two goals fight. Needs a single movement
  arbiter (see D5).

### C6 — Arrow / projectile awareness
- **Status:** `CANDIDATE`
- **Source:** `meteor-client-master/.../combat/ArrowDodge.java`
- **Mechanism:** `ProjectileEntitySimulator` predicts arrow paths, steps out of the line.
- **Our use:** enable only when Baritone is idle. Dodging mid-path cancels the path and the bot jitters
  between dodge and walk.
- **Risk:** skeletons shoot in volleys; repeated cancel/repath can freeze the bot in place.

### C7 — Anti-knockback
- **Status:** `CANDIDATE` (needs a live test)
- **Sources:** `MeteorPlus/.../velocity/modes/GrimSkip.java`, `GrimCancel_v2.java`
- **Mechanism:** on `ClientboundDamageEventPacket` for our entity id, cancel the following
  `ClientboundSetEntityMotionPacket` / `ClientboundExplodePacket`. `GrimSkip` additionally drops 6
  outbound `ServerboundMovePlayerPacket`s.
- **Our use:** skeleton arrows and creeper blasts knock bots into lava and off cliffs — this is the counter.
- **Risk:** skipping outbound movement packets is exactly the pattern anticheats flag. Test on the target
  server before enabling fleet-wide; prefer plain velocity-cancel first.

### C8 — Fall protection
- **Status:** `REFERENCE-ONLY` (Meteor already has it; we just configure it)
- **Source:** `meteor-client-master/.../movement/NoFall.java` (505 lines)
- **Modes:** `Packet`, `NoGround`, `Place`, `AirPlace` (+ `Bucket`, `anchor` centring), injected via
  `SendMovementPacketsEvent`. Our guard currently sets `mode=Packet`.
- **Our use:** for a bot that paths off ledges, `AirPlace` + water bucket is the survivable option; keep
  `Packet` as default and switch per task.
- **Risk:** `Place`/`AirPlace` need a free hand slot and a bucket; they fail silently when out of water.

### C9 — Mining loop architecture (the big win)
- **Status:** `ADOPT` (post-Gate-A, feeds M3)
- **Source:** `meteor-client-master/.../world/HighwayBuilder.java` (2,923 lines)
- **Four reusable pieces:**
  1. **`tickDoubleMine()`** — two breaks per tick: a normal client break (`normalMining`, with
     `ABORT_DESTROY_BLOCK` on failure and `DoubleMineBlock.rateLimited`) **plus** a packet-mined block.
     Roughly doubles gather throughput. Its own comment warns clientside breaking is "too vulnerable to
     desyncs" — which is exactly the lesson our ghost-block bugs taught us.
  2. **State machine** — `Center → ReLevel → Mining → Paving → Inventory`, with `suspended` handling and
     container detection via `ClientboundContainerSetContentPacket`.
  3. **`input` wrapper** — `forward/back/left/right/sneak/stop` instead of raw `key.setDown`. A crash can
     never leave a key stuck.
  4. **Pause conditions** — yields to AutoEat / AutoGap / `KillAura.attacking` and to
     `TickRate.getTimeSinceLastTick() >= 1.5`.
- **Our use:** (3) and (4) immediately — they are small and fix real bot bugs. (1) is a throughput
  multiplier for gather tasks. (2) is the shape our task executor should have.
- **Risk:** (1) doubles desync exposure; enable per-task, never during a reconciled deposit.

### C10 — Baritone as an API, not chat
- **Status:** `ADOPT`
- **Source:** `Baritone-Controller-main/.../BaritoneManager.java`
- **Mechanism:** drives `BuilderProcess.build`, `SelectionManager` directly instead of `#goto` chat commands.
- **Our use:** our commands currently go through chat; the API removes parsing, localization, and the
  "did the command land" ambiguity.
- **Risk:** API surface differs by Baritone build; pin the version.

### C11 — Precision placement (M6 groundwork)
- **Status:** `REFERENCE-ONLY` until blueprints
- **Sources:** `Seija-Printer-26.3/.../util/BlockUtil.java` (478), `.../vanilla_precision_placer/DataGetter.java` (269),
  `dino-printer-main/.../utils/BlockPrint.java`
- **Mechanism:** build a **`FakePlacementContext`** for every candidate (direction × click-vector) and ask
  `canPlace()` **before** touching the world; handle `canBeReplaced(context)`; then either `useItemOn` or
  `ServerboundUseItemOnPacket` + `PunchPacket`; sneak-toggle around the placement; blacklist failures so
  the same spot is not retried blindly.
- **Our use:** anything that places blocks — bridging, shelter, blueprint printing. Even without
  blueprints, `canPlace()` pre-validation kills the "placed into air, block vanished" class of bug.
- **Risk:** packet placement is the most detectable thing in the corpus. Default to `useItemOn`.

### C12 — Fleet safety (don't hit your own bots)
- **Status:** `ADOPT` (small, high value)
- **Source:** `MeteorPlus/.../combat/Teams.java`
- **Mechanism:** `player.isAlliedTo(entity.getTeam())` + name-colour comparison.
- **Our use:** we run multiple bots with no ally check anywhere. Add every fleet peer to Meteor `Friends`
  automatically and/or a `FleetSafety` filter consulted by our target selection.
- **Risk:** none; scoreboard teams may be absent on anarchy, so the friend-list path is the reliable one.

### C13 — Enchantment sourcing (VillagerRoller) — **KEEP**
- **Status:** `ADOPT LATER` — explicitly retained; this is how *any* enchantment on *any* kit becomes
  farmable instead of luck.
- **Source:** `meteor-villager-roller-main/.../VillagerRoller.java` (959 lines)
- **State machine (reusable verbatim as our executor's model):**
  `ROLLING_BREAKING_BLOCK → WAITING_FOR_VILLAGER_PROFESSION_CLEAR → PLACING_BLOCK →
   WAITING_FOR_VILLAGER_PROFESSION_NEW → WAITING_FOR_VILLAGER_TRADES` and back.
  Every transition is gated on **observed world state** (block is lectern / is air, villager profession is
  NONE / not NONE), never on a timer. Timers are only used as *timeouts*, and a timeout sends the machine
  back a step instead of stalling.
- **Offer parsing:** `ClientboundMerchantOffersPacket` → `getOffers()`, filter `ItemStack.is(Items.ENCHANTED_BOOK)`,
  read `DataComponents.STORED_ENCHANTMENTS`, resolve via `Registries.ENCHANTMENT`, compare
  `minLevel` / `maxCost` against `offer.getBaseCostA().getCount()`.
- **Operational details worth stealing:** `interactRetry` (re-send interact if the server never opened the
  screen), `maxProfessionWaitTime` as a *timeout not a wait*, civbreak via
  `ServerboundPlayerActionPacket.STOP_DESTROY_BLOCK` for instant lectern mining, NBT persistence of the
  wish-list, and a `placeFailed(msg)` path that records **why** instead of silently looping.
- **Our use (pipeline):** task `enchant:sharpness5` → bot queries a villager station → rolls until the
  wish-list hits → writes the result to the kit manifest → deposit to chest. Turns "any enchantment for any
  kit" into a normal task with a chest-verified output, which is exactly our Gate A contract.
- **Risk:** villager stations are stationary infrastructure — needs a home base and emerald supply (C12/M9
  economy). Also the biggest NBT/registry surface we would depend on; version-bump sensitive.

### C14 — Module coordination patterns (meta)
- **Status:** `ADOPT`
- **Sources:** scattered — `AutoTotem.locked`, `KillAura.attacking`, `HighwayBuilder` pause block,
  `Surround.toggleModules/toggleBack`, `Modules.get().get(X.class)`
- **Patterns:**
  1. **Public state flags** instead of events (`attacking`, `locked`, `isEating()`).
  2. **Pause hooks**: every long-running module checks the short-running ones before acting.
  3. **Single movement authority**: exactly one thing issues movement per tick.
  4. **Observed-state state machine**: transitions on world facts; timeouts only as escape hatches (C13).
- **Our use:** replaces our ad-hoc `ModuleGuard` booleans with a named, testable arbitration layer (D5).

---

## 3. Synthesized designs (ours, informed by the above)

### D1 — `ShieldCompanion`
Reads `KillAura.attacking` + `getTarget()`. While a target is within X blocks and HP > threshold:
ensure shield in offhand (or hotbar + swap), hold `keyUse`, do **not** touch movement. Releases shield
when target lost, HP critical (hand over to totem), or a task needs both hands.
**Never** combined with TriggerBot (C3). *Derived from C3 + C14.*

### D2 — `SafeFire`
40 lines, `CollisionShapeEvent`, always on, gated off for bridging tasks. *C1.*

### D3 — Shared `Rotation` primitive
Priority queue + move-fix + optional interpolation. One instance, used by mining, placing, combat. *C2.*

### D4 — `Flee`
`PathManagers.moveInDirection(away)` + KillAura off + re-arm on distance. Escalation: shield → run →
disconnect. *C5.*

### D5 — `MovementArbiter` (single authority)
Every module that wants to move registers a request with priority (task > flee > dodge > idle). One
winner per tick issues the movement. Fixes the double-goal problem in C5/C6 and makes "why did the bot
walk into lava" answerable from a log line. *Derived from C14.*

### D6 — Double-mine for gather tasks
Port `tickDoubleMine` semantics with the rate-limit guard. Per-task opt-in, off during deposit/sorting.
Expected ~2× gather rate — **must be measured**, not claimed. *C9.*

### D7 — Enchant pipeline task
`enchant:<id>:<level>` task type: villager station → roller loop (C13) → kit manifest → deposit.
The roller's state machine is the template for our task executor's internals. *C13.*

---

## 4. Rejected / watch-list

| what | why not |
|---|---|
| `CrystalAura` (1,368 lines) | player PvP only; nothing a mining bot needs |
| `BaseMacro` / `6Bees` | chat-macro automation, not task-driven; would bypass our reconciliation |
| Packet-only block placement | highest detection risk; use `useItemOn` unless a task proves otherwise |
| `GrimSkip` outbound packet skipping | anticheat-shaped; test narrow, never fleet-wide by default |
| Own aura implementation | duplicates KillAura; first attempt was reverted for exactly this reason |
| `HighwayBuilder` paving/blockade wholesale | assumes a human watching; we take (1)(3)(4) of C9 only |

---

## 5. What the corpus does **not** solve (our actual moat)

- Multi-bot coordination (who mines, who builds, who trades).
- Task contracts and **reconciliation against chests** — no reference addon has this.
- Persistence / reconnect / resume mid-task.
- Chest accounting and the "undergathered is bad" invariant.
- Fleet-safe targeting (C12 is a start, not a solution).

That gap is the addon. Everything above is tooling.

---

## 6. License & attribution

Corpus addons and this project are **GPLv3**; porting is compatible. When code is ported rather than
reimplemented: keep the original copyright header, add a one-line `// Ported from <project> (<file>)`
comment, and note it in the commit message. Ideas need no header; code does.

---

## 7. Retrieval index (keyword → source)

| keyword | file |
|---|---|
| shield, block, offhand | `meteor-client-master/.../combat/Offhand.java`, `Numby-hack-main/.../ShieldSwap.java` |
| totem, locked, lethal | `meteor-client-master/.../combat/AutoTotem.java` |
| aura, attacking, target | `meteor-client-master/.../combat/KillAura.java` |
| trigger, crits, cooldown | `MeteorPlus-26.3/.../combat/TriggerBot.java` |
| rotation, queue, move-fix | `dino-printer-main/.../utils/RotationManager.java` |
| rotation, smoothing | `Seija-Printer-26.3/.../util/ShadyRotation.java` |
| lava, fire, collision | `Numby-hack-main/.../general/SafeFire.java` |
| arrow, projectile, dodge | `meteor-client-master/.../combat/ArrowDodge.java` |
| knockback, velocity, grim | `MeteorPlus-26.3/.../velocity/modes/GrimSkip.java`, `GrimCancel_v2.java` |
| fall, nofall, bucket | `meteor-client-master/.../movement/NoFall.java` |
| mining, highway, double-mine | `meteor-client-master/.../world/HighwayBuilder.java` |
| pathing, baritone, build | `Baritone-Controller-main/.../BaritoneManager.java` |
| hunt, approach, moveto | `MeteorPlus-26.3/.../combat/Hunt.java` |
| place, canPlace, context | `Seija-Printer-26.3/.../placedata_getter/vanilla_precision_placer/DataGetter.java` |
| place, packet, blacklist | `Seija-Printer-26.3/.../util/BlockUtil.java` |
| team, ally, friendly | `MeteorPlus-26.3/.../combat/Teams.java` |
| villager, trade, enchant book | `meteor-villager-roller-main/.../modules/VillagerRoller.java` |
| disconnect, safety, logout | `MeteorPlus-26.3/.../combat/SafetyNet.java`, `AutoLeave.java`, `AutoLogPlus.java` |

---

## 8. Backlog (unread)

`CrystalAura` body, `XrayBruteforce` (1,137 — possible M4 ore-finding), `VillagerRoller` remainder
(trade-execution side), `Puller` / `AutoPearlStasis` / `BaseSocket`, `AntiTrample`, `BlockUtil` remainder,
`RayTraceUtil`, `AntiBotPlus`, `AntiBedTrap`.

Read order when resumed: `XrayBruteforce` (tier progression), then `VillagerRoller` execution half
(kit pipeline), then the rest.
