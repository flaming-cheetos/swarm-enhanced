# Roadmap

Updated 14 November 2026. This is the order of work and the honest status. If this file disagrees with the code, the
code wins — then fix this file.

## The north star

Start the bots on **iron gear**. Walk away for **one hour**. Come back to: every task finished, chests sorted, at
least one bot on **diamond**, every bot fed, nobody dead with gear on the floor, and nothing asking a question.

That is the product test. Not "can it mine" — can it run a shift without you.

**Passing it means:** 0 manual interventions · every task in a terminal state (`COMPLETE`, or `FAILED` with a
reason) · the input chest empty · no deposit leftovers · at least one bot at diamond · every bot holding food or
above hunger 12 · at most one death per bot, each with a logged cause · no task `RUNNING` for 10 minutes without
progress · no WARN line repeating more than five times.

## Two gates

- **Gate A — Survival 30.** One bot, 30 minutes, no new features. Proves the loop: tasks complete, food works,
  deposits land, nothing hangs.
- **Gate B — The Iron Hour.** The full hour, plus tier progression from iron to diamond.
- **Gate A2 — The Two-Bot Shift.** Two bots, 90 minutes, no task or chest fighting.
  rdh's stated definition of done (`2026-11-13`): "one or 2 bots FULLY surviving for an
  hour or an hour and a half". Task ownership must be settled before this gate, not
  after — the leases that landed on 2026-11-13 are the foundation.

One bot first. A fleet multiplies whatever one bot already is, good or bad.

## Status legend

`DONE` verified live · `CODE ONLY` written, never watched working · `PARTIAL` some of it works · `BROKEN` works
against you · `ABSENT` not written

## Milestones

| # | What | Lane | Status |
|---|---|---|---|
| M0 | Foundation: 26.3 port, modules, task queue, panel, mining, halt, deposit | both | `DONE` (live) |
| M1 | Survival loop: fuel, cooking, completion, pause, staging | bot | `PARTIAL` — outcome-completion gate, deferred completion, deposit ledger and multi-load smelt are `DONE` live (2026-11-13); fuel insertion/cooking, food and pickup remain `CODE ONLY`; the fuel reader fields are test-covered by `h5FuelTest` |
| M2 | Storage intelligence: chest counts, reserved slots, sorter | both | `PARTIAL` — chest scan + index + keep-filter verified live 2026-11-13; gather/ensure task modes live 2026-11-14 (additive vs measured shortfall, category scope). Still missing: ensure rules + upkeep scheduler, named chest zones (categories are item-class filters, not places), reserved slots, low-stock warnings, sorter |
| M3 | Bulk jobs: shulkers, counting, multi-trip | bot | `ABSENT` |
| M4 | Tier progression: iron → diamond | bot | `ABSENT` |
| M5 | Base utilities: gather zones, furnace registry, self-rebuild | bot | `PARTIAL` |
| M6 | Blueprints: `.litematic` / `.schem` | bot | `ABSENT` |
| M7 | Bodyguard | bot | `ABSENT` |
| M8 | Anchor / respawn bots — the hardest, and the most client-bound | bot | `ABSENT` |
| M9 | Fleet: several bots, no two going for the same chest | bot | `PARTIAL` — task claims, leases, renew and requeue are `DONE` live 2026-11-13 and h5-tested; 60-second lease-grace re-attach is h5-tested (`LeaseGraceTest`) but not separately observed live; chest/furnace deconfliction `ABSENT` (gate A2 depends on it) |
| M10 | In-game chatbot — independent, buildable any time | bot | `ABSENT` |
| M11 | Hostile-server hardening: pacing, packet rates, reconnects | bot | `ABSENT` |
| M12 | Threat and evasion — priority sits **above** M4 | bot | `PARTIAL` — SafeFire proven at the collision layer and Shield proven in a staged 1v1, both 2026-11-14 (see `30_...` and `31_GUARD_HUNT_SHIELD_2026-11-14.md`); Flee is `CODE ONLY` after its repath fix; arrow dodge and anti-knockback still undecided |
| M13 | Stash and travel: ender chest, stash registry, nether | bot | `ABSENT` |
| W | Panel v2 at `/v2` — static today; React rewrite planned | panel | `CODE ONLY` — opened on desktop and phone against fake players; no bot in game yet |

## Reference corpus integration (added 2026-11-14)

Source of truth for every claim below: `docs/deepdive/29_REFERENCE_CORPUS.md` — 11 addons, 1,401 files
inventoried, ~40 read in depth. Cards are cited by ID (`C1`–`C14`), and every card names the file it came
from. Nothing in this section is invented.

**Rule:** a card enters a milestone only when that milestone needs it. Good ideas do not get built for
being good.

### Blocking Gate A — build before the 30-minute clock

| card | what | why it blocks | exit criterion |
|---|---|---|---|
| C1 SafeFire | `CollisionShapeEvent` makes fire/lava solid client-side | the most likely way a 30-minute shift ends early: the bot cannot see lava sitting inside its path | **`DONE` mechanism 2026-11-14 (`SwarmSafeFire`)** — collision probe: full cube on lava with the module on, empty with it off; corridor refused. Evidence in `30_TEST_RUN_2026-11-14.md` |

One module, ~40 lines, no interaction with the task loop. That is the entire Gate A delta in code.

### Gate A insurance — promoted by the first dry run, not by theory

| card | promoted when |
|---|---|
| C2 rotation move-fix | mining rotates and drifts — wasted breaks and rubberbanding |
| C5 flee | the shift takes damage it cannot answer |
| C8 NoFall config | the shift paths near a ledge; `Packet` is already set, confirm it appears in `bot_state` |
| C3 shield companion | a death is logged with a mob cause |

If a clean dry run needs none of these, they wait. Gate A is a 30-minute observation, not a feature sprint.

### Mapped to milestones — not Gate A

| card | milestone | note |
|---|---|---|
| C9 input wrapper + pause hooks | M0/M1 tooling | small; fixes stuck keys after a crash. Adopt when movement is next touched |
| C9 double-mine | M3 bulk jobs | ~2x gather throughput; measure it, never claim it |
| C10 Baritone API instead of chat | M5 base utilities | removes command-parse and "did it land" ambiguity |
| C11 placement pre-validation | M6 blueprints | `FakePlacementContext.canPlace()` before touching the world |
| C12 fleet ally check | M9 / Gate A2 | bots must never target each other; friend-list path is reliable, scoreboard is not |
| C13 villager roller | V7 | enchant pipeline; its observed-state machine also models our task executor |
| C3/C5/C6/C7 combat set | M12 threat | shield, flee, arrow dodge, anti-knockback |

### Not being built

`CrystalAura`, `BaseMacro`/6Bees chat macros, packet-only placement, any second aura implementation, and
wholesale HighwayBuilder paving/blockade. Reasons in `29_REFERENCE_CORPUS.md` §4.

## Phase 0 — the dev loop (current work, added 2026-11-13)

Evidence: the 2026-11-13 iron session spent ~70% of its wall clock waiting — 6 live
runs (~54 min), 3 client restarts (~15 min), scans and polling (~10 min), against
~25 min of real code. A live run costs 9 minutes because 78 ingots is 6.5 minutes of
furnace time and because every observation needs a Minecraft client.

Phase 0 builds the loop that makes every later phase cheaper, and ships before any
new gameplay feature. Full contract: `docs/MCP_SERVER_SPEC.md`.

- **Time compression:** measured `/tick rate` ladder on the Pi 5 test server (RCON `192.168.4.57:25575`), two blast furnaces (2× smelt speed) and teleport instead of walking. 78 ingots: 6.5 min today → ~3.2 min at 20 TPS → ~65 s at 60 TPS → ~33 s at 120 TPS. The rate that held (`mspt <= 45`) is recorded in every verdict.
- **Eyes as data:** world probe (blocks, containers, furnace state, entities) plus a `screenshot` tool that reads the client's own PNG — the AI cannot see the game window, so world state must be readable.
- **Decision visibility:** `bot_state` (plan, guards, inventory) and a decision-trace ring buffer, replacing chat-line grepping.
- **Fault injection:** a short chest delivery, a refused chest, an unreachable furnace — so guards like the 2026-11-13 withdraw top-up can be proven in 30 seconds instead of an unproven 9-minute gamble.
- **Verdicts, not logs:** JSONL events in the mod + an assertion engine, so a run is PASS/FAIL with the first mismatch named.
- **Client control:** launch/stop/restart by PID, wait for join, toggle the brain — the manual parts of tonight, automated.
- **MCP server:** Streamable HTTP on loopback (`127.0.0.1:8790/mcp`) so a new chat opens with the tools already connected.

Definition of done for Phase 0: `run_scenario iron78` passes in under 3 minutes with
an assertion verdict, and `run_scenario topup-fault` proves the short-delivery top-up
path in under a minute.

## What is happening now

**Updated 2026-11-14:** the task model is honest and the arguments autofill. Gate A is the
next thing: one module blocks it (C1 SafeFire, see the corpus section above), and nothing else
does — the rest is the run itself.

1. **Task modes are live.** `gather` is additive, `ensure` measures at enqueue and completes only
   against a fresh reading taken after its own deposit, or fails `ensure-undergather`. Stock can no
   longer close a queued task: `completeFromStock` was deleted, not disabled. Proven live
   2026-11-14 on `0.1.0+206fe95` (`verified — base holds 61 of 60`).
2. **Tab completion is live.** Every argument that can be known is suggested from base stock, the
   live queue, the chest categories and the player's position. Owner-verified in game 2026-11-14.
3. **Gate A.** Not run. It needs a seeded board, a 30-minute clock, and every `COMPLETE`
   reconciled by opening the chest.
4. **Eating is wired, never watched.** `SwarmAutoEat` plus Meteor's `AutoEat` under `ModuleGuard`;
   `maybeInterruptForFood` hunts when food reaches zero. `CODE ONLY`.
5. **The reference work is finished and mapped.** 11 addons, 1,401 files, ~40 read in depth; every usable
   finding now has a milestone, a Gate A decision, or a recorded rejection. See `29_REFERENCE_CORPUS.md`.

### Tonight's queue (2026-11-14, planned with the operator before bed)

Phase 0 is close enough that Gate A is the next real proof, so the order is:

| # | Work | Exit criterion |
|---|---|---|
| 0 | Deploy the staged jar, relaunch both clients, prove `world_container open=true` reads a furnace's coal | **DONE 04:24** - `coal x42` at `-549 91 606` |
| 1 | Five cheap tools: `log_tail`, `task_watch`, `chest_delta`, `server_restart`, `panic` | each answers once, live |
| 2 | **Fix the 690 vs 768 gap** - the storage index undercounts the 601/602 double chest by exactly one 78-ingot run | **DONE 04:26** - index 832 = 768 + 64, double chest counted once; 690 was a pre-deposit snapshot. Built `/api/debug/storage` to see it |
| 3a | **A3/A4/A5 base registry** - locations as data, id-addressed reads, survives restart | **DONE 2026-11-13 04:33** - 12 entries, `loadedFromDisk=true` after a real stop/start, iron 832 = 768 + 64, `bf1` coal x42 |
| 2b | **B1 operator tools** `log_tail`, `task_watch`, `chest_delta`, `server_restart`, `panic` | **DONE 2026-11-13 05:05** - all five answer live; chest_delta caught a real +5 iron deposit; panic halts dispatch and resumes |
| 2c | **Deposit loop proven end to end** - gather -> deposit -> ledger -> complete | **DONE 2026-11-13 05:41** - 32 cobblestone gathered, deposited and ledgered (1646 -> 1678), task COMPLETE 32/32 evidence=verified-empty |
| 3 | Surface the fuel reader (`G2: cook check`) in `bot_state` as `fuels[]` + `fuelTicks` | **DONE 2026-11-14** - `h5FuelTest` proves known coal 67,200 ticks, empty 0, and unresolved fuel excluded from usable ticks; live bot read still not observed |
| 3b | Food loop and live preflight | `swarm_preflight` passes every check on a fresh bot |
| 4 | **Gate A**: one bot, 30 minutes, deposits verified by opening chests, not by chat | verdict PASS with every deposit diffed against the world |

Gate A at 60 TPS needs the RCON password on this machine (`tools/mcp/.rcon-password`);
without it the gate runs at 20 TPS, which is slower but still valid.

Accepted work is above. Unaccepted ideas sit in `docs/IDEAS.md` and are
reviewed, never merged.

## Order after Gate A

Threat and stash (M12, M13, plus `local` vs `anarchy` profiles) → storage and bulk (M2, M3) → fleet (M9) →
blueprints, bodyguard, anchor (M6, M7, M8) → hardening (M11) → then live. The chatbot (M10) can be built any
time; it touches nothing else.

Every default flips once threat and stash land, which is why they come before any real server time.

## Non-goals

- The outbound `/tpr` kit-run loop. Dropped on purpose.
- The mob farm. Unwanted — the code can stay, the settings get renamed.
- The 37 proxy PvP modules stay disabled.
- Porting to a headless runtime this month. Later, and only for the parts that aren't client-bound.
- Writing our own block placement engine to replace Baritone's.

## Red lines

- **The panel is never exposed to the internet.** Loopback is not a trust boundary: token on every request
  including the socket, `Origin` allow-listed, no wildcard CORS, no tunnel. It shows coordinates, and a leaked
  base is unrecoverable.
- **Coordinates never leave the client** — not in chat, not on the panel, not in a screenshot.
- **No movement exploits.** Flight, speed, blink and timer can cost the account.
- **Chat never obeys chat.** The bot answers the server; it takes no orders from it.
- **Storage is configured, not assumed.** A deposit target is a coordinate the operator set. No storage rule exists that the deposit path does not actually follow.

## How you'll know it is working

The logs. Every claim has a line behind it: if it says it deposited 99, a chest has 99 more in it. A verdict
without a log line is a hypothesis, and `NOT OBSERVED` is a valid answer.


## v2 backlog — deferred, not cut

rdh, 2026-11-13: "most of this is still what i want in the END once the main core is
battle tested and fully done to the spec." These stay in the plan, ordered behind
Gate B. They are not rejected and not deleted.

| # | What | Why it waits |
|---|---|---|
| V1 | M6 blueprints (`.litematic` / `.schem`) | needs a stable, trusted task/paste path first |
| V2 | M7 bodyguard | depends on threat model (M12) and fleet comms |
| V3 | M8 anchor / respawn bots | hardest and most client-bound; needs restart survival proven (M1 persistence) |
| V4 | M10 in-game chatbot | touches nothing else — can be pulled forward any time it is wanted |
| V5 | M11 hostile-server hardening (pacing, packet rates, reconnects) | only matters once it runs on a real server for real hours |
| V6 | W panel v2 React rewrite | current panel covers the need; revisit after fleet view (Phase 2) |
| V7 | villager-roller enchant pipeline (`enchant:<id>:<level>` task) | **kept on purpose** — it is how any enchantment on any kit becomes farmable instead of luck. Needs a stationary villager station + emerald supply first; see `docs/deepdive/29_REFERENCE_CORPUS.md` C13 |

## Revision history

- `2026-11-13` — Phase 0 added; M1/M2/M9 re-scored from live evidence; Gate A2 added;
  v2 backlog section created from rdh's "defer, do not cut" call.
- `2026-11-14` — reference corpus mapped into the plan: exactly one blocking Gate A item (C1 SafeFire),
  an insurance tier promoted by the dry run, and a milestone home for every remaining card.

## Gate A status, 2026-11-14

The 2026-11-13 failure was an accounting failure, and it is closed at the source. A queued task
cannot complete on base stock any more (`completeFromStock` deleted, zero callers), and an `ensure`
completes only after a reading taken *after* its own deposit. Both failure modes were caught live
before the fix landed: measuring before the deposit reported 46/50 on work that had succeeded, and
trusting a cache older than the move reported 51/50 and failed it anyway.

What must be true before the 30 minutes start:

1. **Board empty.** `/api/task/remove` must clear a PENDING row — **DONE 2026-11-14.**
   `TaskRemovalTest` proves the queue core (PENDING removal, sibling preservation, reload
   persistence, ACTIVE cleanup, empty board). `HostHttpRemoveTest` now starts a real
   `EnhancedSwarmHost` on loopback and sends a tokenized POST to the real endpoint: a wrong
   token is rejected with 401, the target row is gone, both siblings survive, and a fresh
   `TaskQueue` reload still has exactly two rows. The production `Modules.get()` lookup is
   game-only: a plain-JVM attempt to register `EnhancedSwarm` fails while initializing
   `MeteorClient` (`invoked too early?`), so the probe binds the same host through
   `SwarmWebServer.bindHostForProbe`. The route after host resolution is unchanged; exact
   bootstrap evidence is quoted in `MCP_SERVER_SPEC.md` §5.3.
2. **A reading under 30 s old.** Scan the chests first; `ensure` refuses anything older.
3. **Food on hand and `SwarmAutoEat` on.** Eating has never been watched live.
4. **The shift.** One bot, 30 minutes, then open every deposit chest and diff it against the task's
   claimed quantity. A `COMPLETE 3/3` with 2 in the chest is the failure this gate exists to catch.

**HTTP API evidence (2026-11-14).** `HostHttpApiTest` runs under `h5HostApiTest`/`check` and
starts a real `EnhancedSwarmHost` plus Netty `SwarmWebServer` on loopback with a real persisted
`TaskQueue`. It drives panel/assets, header/query/cookie auth, foreign-Origin rejection, `/ws`
state + log frames, task add/remove/cancel/clear/pause/resume, every action route, chest
add/remove, command/nickname, and every implemented `/api/debug/*` POST route. The three GET
design sketches in `MCP_SERVER_SPEC.md` §6.1–6.3 are **blocked**: the probe asserts 404 and
quotes the exact output in §6.6; they are not claimed as live routes. The external Node MCP
transport is covered separately by `tools/mcp/selftest.mjs` (20 passed, 0 failed) because
Gradle is forbidden to invoke Node.
