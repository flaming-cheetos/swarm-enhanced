# Enhanced Swarm — Project Overview (AI Handoff)

- **Snapshot:** 2026-11-14 (originally 2026-11-13)
- **Repository:** `C:\Projects\swarm\swarm-improved`
- **Branch:** `main`
- **Code snapshot:** `HEAD` (the commit containing this overview; verify with `git rev-parse HEAD`)
- **`origin/main`:** `HEAD` after the push
- **Version:** `0.1.0`
- **Working tree:** the runtime source changes in this snapshot are committed. The reference-corpus reorganization and concurrent operator-script edits may remain uncommitted; none are part of runtime behavior.

This document is a project-level companion to [`PANEL_V2_SPEC.md`](PANEL_V2_SPEC.md). The panel spec explains the web panel in depth. This document explains the whole system: what it is, how host and workers fit together, what the task engine actually does, what is proven, and where the documentation and code disagree.

It is written for another AI or developer who has none of the original conversation. It is deliberately blunt about uncertainty. **Source code and recorded test artifacts are the ground truth for behavior. Documentation can be stale.**

---

## 1. The one-paragraph answer

Enhanced Swarm is a **client-side Meteor Client addon for Minecraft 26.3** that lets one Minecraft client act as a **host/coordinator** and other Minecraft clients act as **autonomous workers**. The host exposes a token-protected HTTP panel, accepts worker connections over a custom TCP protocol, persists a task queue, tracks configured storage chests, and assigns work. Each worker runs `BotBrain`, a deterministic Java state machine that uses Meteor modules and Baritone to gather, craft, smelt, hunt food, use a smithing table, empty shulker boxes, build, deposit, withdraw, and recover from problems. It is not a Minecraft server plugin, not a headless bot, and not an LLM agent.

The intended product test is stated in the roadmap: **one bot, iron gear, one hour, every task reaching a terminal state, chests sorted, at least one diamond, the bot fed, no gear lost, and no questions asked.** The repository is not yet documented as having passed that full test.

---

## 2. Identity and scope

| Item | Value |
|---|---|
| Display name | Enhanced Swarm |
| Mod id | `enhanced-swarm` |
| Java package | `com.rdh.swarm` |
| Version | `0.1.0` |
| Minecraft | `26.3` |
| Java | 25 |
| Loader | Fabric Loader `0.19.5` |
| Client | Meteor Client `26.3-SNAPSHOT` |
| Runtime pathing | Meteor's Baritone fork; installed separately |
| Environment | client only |
| License | GPL-3.0 |
| Primary target | anarchy servers |
| SMP support | explicitly none |

The addon registers **44 modules**: `EnhancedSwarm`, `BotBrain`, and 42 concrete `Swarm*` support modules. One additional `SwarmProxyModule` file is a base class, not a separately registered module. `ModuleGuard` is support code, not a Meteor module.

The support modules are mostly PvP, movement, and player-utility modules. They matter because the addon is also a general Meteor module pack, but they are **not** the swarm task engine. The autonomous loop is `EnhancedSwarm` + `EnhancedSwarmWorker` + `BotBrain` + the task/storage classes.

### Who it is for

A player running multiple Minecraft clients/accounts who wants one client to coordinate the others on an anarchy server. It assumes the user can:

- install Meteor Client and the matching Baritone build;
- run more than one graphical Minecraft client;
- configure host/worker mode and a shared token;
- stage a local test world for safe testing;
- read logs when something fails.

It does not assume a cloud account, a database, or a separate daemon.

---

## 3. What it is not

These distinctions matter because the name and the panel can make the project look broader than it is.

| Not this | Why |
|---|---|
| A Minecraft server plugin | The host is a Minecraft **client**, not a server. There is no Paper/Spigot/Fabric server mod. |
| A headless bot | Every participant is a normal graphical Minecraft client running Meteor. |
| An AI/LLM agent | `BotBrain` is a deterministic state machine plus recipe/dependency logic. No model calls, no vision model, no planner service. |
| A web SaaS | The panel is an embedded HTTP server inside the host client. It is not a hosted web app and should not be deployed to Vercel/Cloudflare/Sites. |
| A multi-host cluster | One host owns one queue and one panel. There is no host election, sharding, or cross-host coordination. |
| An encrypted remote-control product | TCP and HTTP are plaintext on the configured interfaces. The shared token is authentication, not transport security. |
| An SMP utility | The README explicitly says anarchy only, no SMP support. |
| A completed, validated release | The branch contains in-flight trust work, stale docs, and an incomplete live-test matrix. |
| A general-purpose Meteor replacement | It bundles support modules, but the project's reason to exist is the swarm coordinator. |

---

## 4. Architecture at a glance

```text
                         +--------------------------------------+
                         | Browser                              |
                         | http://<host>:8888/?token=<token>     |
                         +------------------+-------------------+
                                            |
                                      HTTP / WS
                                            |
+-------------------------------------------v--------------------------------------+
| HOST Minecraft client (Meteor + Enhanced Swarm)                                |
|                                                                                |
|  EnhancedSwarm (mode=Host)                                                     |
|    +-- EnhancedSwarmHost : TCP 6969                                            |
|    |     +-- EnhancedSwarmConnection per worker                                |
|    |     +-- TaskQueue  (persisted, leased, fenced)                            |
|    |     +-- DepositConfig / StorageIndex / WaypointManager                    |
|    |     +-- lease maintenance (30s TTL, 5s sweep)                             |
|    +-- SwarmWebServer : HTTP 8888                                              |
|          +-- legacy page /                                                     |
|          +-- static replacement /v2                                            |
|          +-- REST API and /ws state/log feed                                   |
+-------------------------------------------+------------------------------------+
                                            |
                              custom TCP text protocol
                              Java writeUTF frames
                                            |
              +-----------------------------+-----------------------------+
              |                                                           |
+-------------v----------------+                           +--------------v-------------+
| WORKER Minecraft client      |                           | WORKER Minecraft client     |
| EnhancedSwarm (mode=Worker)  |                           | EnhancedSwarm (mode=Worker) |
| EnhancedSwarmWorker          |                           | EnhancedSwarmWorker         |
|   +-- reconnect loop         |                           |   +-- reconnect loop        |
|   +-- BotBrain               |                           |   +-- BotBrain              |
|   +-- Baritone pathing       |                           |   +-- Baritone pathing      |
|   +-- Meteor survival modules|                           |   +-- Meteor survival modules|
+------------------------------+                           +-----------------------------+
```

All clients join the actual Minecraft server independently. The host-to-worker TCP connection is **not** a Minecraft connection and does not proxy game traffic.

### Host responsibilities

- Bind the worker TCP listener and the web panel, both on the configured bind address.
- Authenticate the worker handshake with the shared token.
- Maintain the task queue and dispatch eligible work.
- Claim tasks with a lease and fencing token.
- Requeue work on reconnect or host restart.
- Own deposit-chest configuration, storage index, base anchor, and waypoints.
- Serve the panel, REST API, and WebSocket state/log feed.
- Send worker commands such as cancel, pause, resume, deposit, scan, and exec.
- Refuse to dispatch to a halted bot until it is resumed.

### Worker responsibilities

- Connect to the host, announce its player name and token, and reconnect every 5 seconds after a disconnect.
- Report status and task progress.
- Accept a task assignment with its lease token.
- Run the task through `BotBrain`.
- Query host storage before gathering when a task needs materials.
- Deposit items into configured chests and report observed item deltas.
- Scan configured containers and report results.
- Send completion, failure, or cancellation with the correct task and lease identity.
- Stop autonomous task work when cancelled, paused, or disconnected.

The host also has a local `deposit self` path for the host player's own inventory. That is not a worker task and does not go through the worker TCP connection.

---

## 5. Transport, identity, and security

### Ports and defaults

| Service | Default | Configurable |
|---|---|---|
| Worker TCP | `6969` | `port` |
| Web panel | `8888` | web port setting |
| Bind address | `127.0.0.1` | `bind-address` |
| Worker host address | `localhost` | `ip` |
| Mode | `Host` | `mode` |

Both listeners use the same configured bind address. LAN use requires deliberately changing `bind-address`; the default is loopback-only. The README says to set `bind-address` for LAN access.

### Framing

The worker protocol is a custom **Java `DataInputStream.readUTF` / `DataOutputStream.writeUTF`** protocol. That means length-prefixed modified UTF-8 strings, not newline-delimited JSON and not a standard WebSocket protocol. The string payloads contain separators such as `:`, `|`, and spaces.

Do not write a client that assumes newline framing without changing both sides.

### Handshake

The worker's first message is:

```text
worker:<playerName>:<token>
```

The host checks the token. An empty host token is accepted only for loopback connections. The panel uses the same shared token value; changing it affects both worker and panel access.

### Authentication limits

- There are no user accounts, roles, or per-worker credentials.
- There is no TLS for TCP and no HTTPS for the panel.
- The token may appear in a panel URL query string and in logs.
- The panel strips the raw task lease token from its JSON feed, but logs may contain a shortened lease token.
- Exposing the bind address to the internet without a VPN/reverse proxy is a bad idea.

### Worker lifecycle

- Worker connects and identifies itself.
- Host registers/replaces the connection by player name.
- Worker sends status roughly every two seconds and task progress while working.
- On disconnect, the worker retries every five seconds.
- On reconnect, the host requeues the worker's active assignment, sends a task reset, and the next dispatch claims a fresh lease.
- A host restart loads the persisted queue, converts persisted `ACTIVE` rows back to `PENDING` with a host-restart reason, and dispatches again after a worker reconnects.

### Protocol message families

Host-to-worker messages include:

```text
task assign <taskId>|<TYPE>|<item>|<quantity>|<bot>|<leaseToken>|<attempt>
swarm:<worker> task reset ...
swarm:<worker> cancel|pause|resume
swarm:<worker> deposit ...
swarm:<worker> scan [category]
swarm:<worker> exec <command>
```

Worker-to-host messages include:

```text
worker:<name>:<token>
STATUS ...
BOT_STATUS ...
TASK_PROGRESS ...
TASK_COMPLETE ...
TASK_FAILED ...
TASK_CANCELLED ...
STORAGE_QUERY_REQUEST ...
STORAGE_QUERY_RESPONSE ...
WITHDRAW_REQUEST ...
WITHDRAW_RESULT ...
WITHDRAW_REFUSED ...
SCAN_CHEST_RESULT ...
SCAN_COMPLETE ...
CHEST_DELTA ...
SETTINGS ...
```

The exact field order matters. The host contains both current and legacy parsing paths for several messages. When changing the protocol, inspect `EnhancedSwarmConnection` and the worker's dispatch code together; do not change one side in isolation.

---

## 6. Tasks, leases, and evidence

### 6.1 Task types

`BotTask.Type` currently declares:

```text
GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS,
HUNT_FOOD, GRIND, SMITH, SHULKER, BUILD
```

`BotTask.isDispatchable()` returns true for everything except `EQUIP`, `DEPOSIT`, and `SCAN_CHESTS`. That does **not** mean every remaining type has a complete queued-task path.

| Type | Intended job | Current caveat |
|---|---|---|
| `GATHER` | Collect a target item, using dependency steps if needed | Strongest path; H1 recorded live |
| `CRAFT` | Craft a dependency or target item | Source path exists; no full live matrix recorded here |
| `SMELT` | Smelt ore/inputs, including fuel and collection phases | Multi-phase implementation; known fuel limitation tracked by H12 |
| `HUNT_FOOD` | Obtain food by hunting | State and auto-hunt exist; no dedicated live scenario recorded |
| `SMITH` | Netherite/smithing-table work | Source path and auto-netherite exist; live status not established |
| `SHULKER` | Place, empty, break, and collect a shulker box | Source path exists; live status not established |
| `BUILD` | Build from a configured build file at coordinates | Source path exists; H12 treats unproven completion as a known limitation |
| `GRIND` | Mob/XP grinding at configured coordinates | `GRINDING` works through auto-grind; `handleIdle()` has no queued `GRIND` branch, so a queued GRIND task is not a verified path |
| `EQUIP` | Equipment operation | Enum-only; not dispatchable |
| `DEPOSIT` | Deposit operation | Enum-only; deposit is driven by command/driver, not a queued task |
| `SCAN_CHESTS` | Storage scan | Enum-only as a task; scan is a worker verb |

Queue statuses are:

```text
PENDING, ACTIVE, BLOCKED, COMPLETE, FAILED, CANCELLED
```

### 6.2 A normal GATHER task, end to end

1. A browser or command adds a task: item, quantity, type, optional bot.
2. Host validates and persists a `BotTask` as `PENDING`.
3. Host dispatches only to a connected, non-halted worker with no live assignment.
4. `TaskQueue.claimNextForBot()` marks it `ACTIVE`, sets owner, attempt, lease token, and expiry.
5. Host sends `task assign ...` including the lease token.
6. Worker hands it to `BotBrain`. `BotBrain` starts in `IDLE`, builds a dependency plan, and moves into equipment check, crafting, smelting, gathering, or a specialized state.
7. Worker sends progress; progress renews the lease only when the lease token matches.
8. Completion is checked against inventory/container evidence, not merely elapsed time.
9. If evidence is missing, the task can be retried with an `unproven-completion` reason and then fail explicitly.
10. Host records `COMPLETE`, `FAILED`, or `CANCELLED`; the panel sees the terminal state and log entry.

### 6.3 Leases and fencing

This is current-branch behavior and is newer than parts of `docs/FEATURES.md`.

- Lease TTL: **30 seconds**.
- Lease maintenance sweep: **5 seconds**.
- A claim assigns a random lease/fencing token.
- The worker periodically sends `TASK_PROGRESS`; that renews the lease only for the matching task and token.
- `TASK_COMPLETE`, `TASK_FAILED`, and `TASK_CANCELLED` are accepted only if the lease matches.
- A stale or superseded report is rejected and logged as a stale report.
- A lapsed lease returns the task to `PENDING` for another attempt.
- A reconnect requeues the previous assignment and issues a new token on the next claim.
- A host restart recovers persisted `ACTIVE` tasks rather than leaving them stranded.

The panel JSON deliberately omits the raw lease token. It exposes lease owner, expiry, heartbeat age, attempt, and related fields.

This design addresses the failure mode where a worker finishes a task after the host has already given it to someone else, or a restarted host leaves a task permanently `ACTIVE`.

### 6.4 Evidence, not optimism

The project's central trust rule is that a task is not complete just because a timer expired or a bot said so.

Recorded log anchors include:

```text
TASK: complete <id> evidence=inv
TASK: complete <id> evidence=verified-empty
TASK: failed <id> reason=<reason>
TASK: retry <id> attempt=1/2 reason=unproven-completion
TASK: rejected stale complete <id> token=<short>
```

Deposit verification is separate: the driver opens the configured chest, checks the menu identity, moves items, and counts observed deltas. A wrong menu can produce:

```text
DEPOSIT: refused reason=menu-mismatch
```

The panel's POST API is not a reliable success signal: several endpoints return HTTP 200 with `{"ok":true}` even when downstream validation rejects the request. Always assert the resulting state/log, not just the HTTP status.

---

## 7. `BotBrain`: the worker's state machine

`BotBrain` is a large Meteor module and the core of worker behavior. It is disabled on host clients: `onTick` returns immediately when the swarm module is in `Host` mode.

### 7.1 States

There are **18** declared states:

```text
IDLE
CHECKING_EQUIPMENT
GATHERING
HUNTING
GRINDING
SMITHING
SHULKER
BUILDING
WITHDRAWING
RETURNING_TO_BASE
CRAFTING_PREP
CRAFTING
SMELTING_DEPOSIT
SMELTING_WAIT
SMELTING_COLLECT
DEPOSITING
RECOVERING
STUCK
```

Grouped by purpose:

| Group | States |
|---|---|
| Acquire/plan | `IDLE`, `CHECKING_EQUIPMENT`, `CRAFTING_PREP`, `CRAFTING` |
| Resource collection | `GATHERING`, `HUNTING`, `GRINDING` |
| Processing | `SMELTING_DEPOSIT`, `SMELTING_WAIT`, `SMELTING_COLLECT`, `SMITHING` |
| Special builds | `SHULKER`, `BUILDING` |
| Logistics | `WITHDRAWING`, `RETURNING_TO_BASE`, `DEPOSITING` |
| Recovery | `RECOVERING`, `STUCK` |

### 7.2 How a task becomes a plan

`DependencyResolver` builds a sequence of `CRAFT`, `SMELT`, and `GATHER` steps for a target item. `RecipeBook` supplies recipe and tier knowledge. `BotBrain` executes the plan step by step, rather than treating every task as a single mining job.

The planner is deterministic. It does not ask an external service what to do. If it lacks a recipe or a handler, it fails the task with a reason such as `no handler for <type>` instead of silently idling.

### 7.3 Logistics and recovery

- `FoodManager` tracks food values and safe raw-food fallback.
- `DeathTracker` records death position/dimension and decides whether recovery is possible.
- Cross-dimension recovery is refused rather than silently attempted.
- `SmithingManager` handles the smithing-table interaction.
- `DepositDriver` handles container opening, menu verification, item movement, and delta counting.
- `StorageIndex` tracks per-server, per-category stock and chest contents.
- `WaypointManager` stores base/waypoint navigation data.
- Stuck detection stops Baritone and enters `STUCK` or fails the task with a reason.

### 7.4 Standalone auto behaviors

`BotBrain` can also act from `IDLE` without a host-assigned task when the corresponding settings are enabled:

- auto-hunt;
- auto-cook;
- auto-grind;
- auto-netherite;
- auto-empty-shulkers;
- auto-build;
- auto-equip;
- drop collection.

This means a worker is not completely inert when the queue is empty. It is still constrained by its module settings and by the host's halt/cancel commands.

### 7.5 Module guard

When `manage-modules` is enabled, `BotBrain` uses `ModuleGuard` to snapshot, configure, and enable Meteor's native survival modules:

```text
AutoEat, AutoArmor, AutoTotem, AutoTool, AutoReplenish,
NoFall, AntiHunger, AutoRespawn, and optionally KillAura
```

`KillAura` is enabled only when `fight-mobs` is on, and its target list is restricted to hostile mob categories. `ModuleGuard` restores the original enabled/disabled state when the brain deactivates. It also adjusts AutoEat's raw-food blacklist when raw-food fallback is enabled.

The guard deliberately uses Meteor's native modules rather than the addon's proxy PvP modules for survival behavior.

---

## 8. Feature inventory and honest status

Status words used here:

- **Source-present** — code exists in this working tree; not necessarily run.
- **Test-covered** — covered by the eleven `h5Test` probes or the harness stubs.
- **Recorded live** — a human-run artifact is cited in the docs; not re-run for this export.
- **Simulated** — harness contract behavior only; no real client proof.
- **Unverified** — no evidence located.

| Area | What exists | Status / caveat |
|---|---|---|
| Host/worker transport | TCP handshake, status, reconnects, commands | Source-present; H0/H0b recorded live per harness spec |
| Task queue | Persisted tasks, statuses, assignment, recoveries, removal | Source-present; `TaskQueueRecoveryTest`, `TaskRemovalTest`, `HostHttpRemoveTest`, `HostHttpApiTest` |
| Leases/fencing | 30s TTL, 5s sweep, token-checked progress/completion, 60s grace re-attach | Source-present; eleven h5 probes overall, including `LeaseGraceTest`; H5 recorded live on an earlier build, not rerun after the latest gap closure |
| Gather | Baritone-driven collection, dependency steps, evidence | H1 recorded live on `e62b2f9`, including three consecutive runs |
| Craft | Recipe/dependency planning, crafting-table path | Source-present; no complete live artifact recorded here |
| Smelt | Deposit/wait/collect phases, fuel handling | Source-present; H12 tracks a known fuel limitation; do not call it fully solved |
| Fuel debug | `fuels[]` + `fuelTicks` in `/api/debug/bot` | Source-present; test-covered by `FuelReaderTest` (known/empty/unresolved); live bot read not observed |
| Hunt food | Hunting state, AutoEat, auto-cook | Source-present; no dedicated live H-scenario |
| Smithing | Smithing-table path, auto-netherite | Source-present; live status not established |
| Shulker | Place/open/empty/break/collect path | Source-present; live status not established |
| Build | Build file + coordinates path | Source-present; H12 records `unproven-completion` as a known limitation |
| Grind | `GRINDING` state and auto-grind | Works via auto-grind; queued `GRIND` has no `handleIdle` branch |
| Deposit | Menu validation, item moves, delta counting | Source-present; H8 is simulated, not a live pass |
| Withdraw | Host storage query and worker withdrawal messages | Source-present; live status not established |
| Scan chests | Worker container scan and category results | Source-present; docs conflict; `/v2` button may still be disabled; treat as unverified live |
| Storage index | Per-server category totals and chest contents | Source-present; depends on successful scans/deposits |
| Self-deposit | Host player's own inventory | Current-branch feature; source-present; no live proof recorded here |
| Panel | `/`, `/v2`, REST, `/ws`, token auth, logs | `/v2` opened in a desktop browser and phone against fake players; no bot has driven it in game |
| PvP/support modules | 42 concrete `Swarm*` modules | Registered; proxy PvP modules are off by default; not part of the task loop |
| Test harness | Node runner, bridge, simulated/live scenarios | H0/H0b/H1–H3 recorded live; H5 recorded live earlier; H4/H6/H8–H12 simulated; H7 not implemented |

### 8.1 What is actually known about the live state

From the repository's own documents, not from a fresh run for this export. These are historical build results, not results for the current HEAD:

- H0 (attach/identify), H0b (read-only truth), H1 (gather), H2 (cancel), and H3 (pause/resume) passed live on `0.1.0+e62b2f9`.
- H1 passed three consecutive times.
- H5 (host restart with an `ACTIVE` task) passed live on `0.1.0+8e2eed6`; the pre-fix baseline failed with a stranded `ACTIVE` row.
- H4, H6, H8, H9, H10, H11, and H12 are **simulated** contract scenarios, not live passes.
- H7 is not implemented.
- The harness spec says the post-H5 lease gap closure is test-covered but the live H5 gate has not been rerun since that closure.
- The panel has been browser-tested only against fake players. No real worker has driven the panel in game.
- No full one-hour acceptance run is recorded as passed.

The original export did **not** run Minecraft, the Gradle build, the probes, the harness, or the panel. A later forced Gradle build on 2026-11-14 passed, and all eleven `h5Test` probes passed, including `FuelReaderTest`, `LeaseGraceTest`, `TaskRemovalTest`, `HostHttpRemoveTest`, and `HostHttpApiTest`; no live Minecraft run was performed. The HTTP probes are plain-JVM proofs against a real `EnhancedSwarmHost`, not a game session; the production `Modules.get()` lookup remains game-only. The external Node MCP transport was separately checked with `node tools/mcp/selftest.mjs` — 24 passed, 0 failed, including the legacy `/sse` + `/messages` handshake.

---

## 9. Commands and panel

### 9.1 Commands

Both `/eswarm` and `/swarm` are registered for compatibility. The command tree includes:

- `cancel`, `stop`, `resume` — halt/resume local or all workers depending on context.
- `exec <command>` — run a command locally or broadcast it when hosting.
- `list` — list connected workers.
- `send <target> <command>` — send a raw worker command; `all` broadcasts.
- `gather <item> <qty>` / `ensure <item> <qty>` / `ensure in <category> <item> <qty>` — queue work
  (additive or target-level). The `in` is a literal branch, not an optional category.
- `task list|remove|clear|cancel|pause|resume` — control the queue. Tasks carry no bot name.
- `deposit set|remove|list|auto|threshold|self|push` — configure deposit chests and behavior.
- `scan [category]` — request a storage scan from all workers.
- `stock [category]` — show storage index totals.
- `base set` — set the base anchor from the crosshair.
- `dumpstorage` — dump storage state.
- `waypoint ...` — waypoint management, including `goto`.
- `portal` — portal-related command path in the current tree.

Use the in-game Brigadier suggestions as the exact authority; the tree has grown organically and some branches are host-only or worker-only.

### 9.2 Panel

- `/` is the legacy page.
- `/v2` is the static replacement described in `PANEL_V2_SPEC.md`.
- Default port: `8888`.
- Token required on every request; accepted as `X-Swarm-Token`, `?token=`, or the `swarm_token` cookie.
- `/ws?token=...` pushes state roughly every two seconds plus log events.
- State includes host dimension, connected bots, tasks, lease fields, statuses, and reasons.
- The panel can add/cancel/pause/resume tasks, trigger deposit actions, scan, send commands, rename bots, and toggle PvP modules.

Important API caveats:

- HTTP 200 does **not** mean the action was accepted.
- `/api/action/check_fuel` is an HTTP 501 stub.
- `/api/action/scan_chests` forwards `swarm:all scan`; the worker scan path exists in source, but the UI/docs are inconsistent and live verification is missing.
- `/api/action/test_give` exists as a test-only fixture path. Do not treat it as a product feature.
- The panel is an embedded Java HTTP server, not a framework app. `/v2` is static HTML/CSS/JS served by that server.

The panel spec contains the full API/state schema, visual system, and React/JavaScript rebuild path. This overview intentionally does not duplicate it.

---

## 10. Persistence and configuration

State lives per Minecraft client under Meteor's config directory. The documented paths are:

```text
<gameDir>\meteor-client\enhanced-swarm\task-queue.json
<gameDir>\meteor-client\enhanced-swarm\deposit-chests.json
<gameDir>\meteor-client\enhanced-swarm\storage-index.json
```

Additional death/waypoint data is kept by the corresponding managers. The queue is the critical artifact for post-mortems: it records task status, owner, attempt, reason, and lease metadata.

There is no central database and no external service. If the host client's game directory is lost, its queue/history is lost with it.

---

## 11. Build, install, and run

### Requirements

- JDK 25
- Minecraft 26.3
- Meteor Client for 26.3
- Meteor's Baritone fork installed as a runtime mod
- Fabric Loader matching the build

### Build

From the repository root, using the project's documented Windows command:

```bash
JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\gradlew.bat build
```

Output goes to `build/libs/`. Install the resulting jar in the client's `mods/` folder next to Meteor.

The Gradle build uses Fabric Loom and resolves Minecraft/Meteor/Baritone as compile-time dependencies. Baritone is compile-only in the build; it must be present at runtime.

### Run

1. Start the host client and join the target world/server.
2. Enable Enhanced Swarm, set `mode` to `Host`, set a token, and start.
3. Confirm the log line containing the panel URL.
4. Start each worker client, set `mode` to `Worker`, set host address/port and the same token, and start.
5. Confirm the worker appears in `list` and in the panel.
6. Add a small task first (for example, gather one log) before trusting a long run.

For LAN access, change `bind-address` deliberately; otherwise both TCP and panel stay on loopback.

### Test commands

The repository contains eleven plain-Java probes under `src/h5Test`:

```text
TaskQueueRecoveryTest
TaskRemovalTest
HostHttpRemoveTest
HostHttpApiTest
LeaseLifecycleTest
FencingReconnectTest
LeaseGraceTest
PanelLeaseJsonTest
ProtocolMatrixTest
ProgressAccountingTest
FuelReaderTest
```

They are part of the Gradle `check` task per the harness spec. The Node harness under `tools/harness` has stub and live modes; see `docs/TEST_HARNESS_SPEC.md` and `tools/harness/README.md`.

---

## 12. Current status: verified, simulated, and merely present

### 12.1 Verification ladder

Use these labels consistently:

| Label | Meaning |
|---|---|
| Source-present | Code exists in this tree. It may not compile, run, or work. |
| Test-covered | A deterministic probe or stub scenario exercises the contract. Not a gameplay proof. |
| Recorded live | A real host/worker run is recorded in `test-runs` or docs. Cite the artifact. |
| Simulated | Harness stub only. Never call it live. |
| Observed | A human watched the behavior in game. Use sparingly and only with evidence. |
| Unverified | No evidence located. Say so. |

### 12.2 What is on `main` now

This document started as a snapshot of `fix/phantom-task-types` at `226b1b3`. Those changes have since landed on `main`; the fuel probe landed as `45467cf`, the lease-grace probe as `272f977`, the task-removal probe as `c54ce66`, and the host HTTP removal proof as `9ec3f9f`. Current `main` includes:

- lease fields, fencing, lease renewal, expiry, and stale-report rejection;
- host-restart recovery of `ACTIVE` tasks;
- self-deposit for the host player;
- test-only `test_give`;
- harness client-control endpoints (`command`, `chat`, `goto`, `stop-path`, `respawn`, and the shape probe);
- fuel `fuels[]` / `fuelTicks` in the bot debug snapshot (`45467cf`), with `FuelReaderTest` coverage for known, empty, and unresolved fuel;
- 60-second lease-grace re-attach and re-claim fencing in `TaskQueue` (`272f977`), with `LeaseGraceTest` coverage;
- PENDING/ACTIVE row removal and empty-board persistence in `TaskQueue` (`c54ce66`), with `TaskRemovalTest` coverage;
- real HTTP `/api/task/remove` proof in `HostHttpRemoveTest` (wrong token 401, one PENDING row removed, siblings preserved, reload persisted), plus the wider `HostHttpApiTest` surface proof (panel/assets, auth, `/ws`, task/action/chest/command/debug routes); three GET debug design sketches remain 404/blocked, with the probe output quoted in `MCP_SERVER_SPEC.md` §6.6; plain-JVM module registration is still blocked by `MeteorClient`'s `invoked too early?`, so the probes bind the host directly;
- `docs/PANEL_V2_SPEC.md`, which is now tracked.

The only uncommitted tree change is the unrelated reference-corpus reorganization. Do not use that churn to infer runtime behavior.

### 12.3 Documentation drift that matters

`docs/FEATURES.md` is useful prose but is not a reliable status table for `main`. Concrete examples found while preparing this export:

- It still describes the system as having no lease/atomic claim and lists task leasing as future work, while the current tree has lease fields, tests, and H5 evidence.
- It says `SCAN_CHESTS` is an enum-only value with no execution path, while the current worker has a `handleScan` implementation and the host endpoint forwards `swarm:all scan`.
- It describes the `/v2` Scan Chests button as disabled; verify the current UI before relying on it.
- Its live/code-only labels predate the newest `main` changes.

`docs/PANEL_V2_SPEC.md` is a design/handoff document, not a running app. It is now tracked in the repository.

### 12.4 Packaging and metadata debt

`src/main/resources/fabric.mod.json` still contains template-era metadata:

- `contact.sources` points to `MeteorDevelopment/meteor-addon-template`;
- the icon is under `assets/template/icon.png`;
- the description is a short legacy line.

`EnhancedSwarmAddon.getRepo()` returns `rdh/enhanced-swarm`, while the project's release/remote identity used elsewhere is different. Do not treat either string as a clean release identity without checking the remote. These are packaging issues, not gameplay behavior.

### 12.5 Known gaps and risks

1. **Docs drift.** `FEATURES.md` is behind `main` on leases and scan. Always cross-check source.
2. **Incomplete live matrix.** H4/H6/H8–H12 are simulated; H7 is missing; H5 was not rerun after the latest closure.
3. **No full acceptance run.** The one-hour, iron-gear, no-questions product test is not recorded as passed.
4. **Panel never driven by a bot.** `/v2` has only fake-player/browser coverage.
5. **Queued `GRIND` gap.** Auto-grind works, but `handleIdle()` has no queued `GRIND` branch. Do not promise it as a queueable task.
6. **Scan uncertainty.** Source, UI, and docs disagree; live behavior is unverified.
7. **Plaintext shared-token security.** No TLS, no accounts, no per-worker authorization.
8. **HTTP 200 is not acceptance.** Panel actions can report success before downstream validation fails.
9. **Dirty reference tree.** The reference-corpus reorg is uncommitted and unrelated to runtime behavior. Do not reset or clean it to "get a clean checkout."
10. **Template metadata.** Release metadata is not production-clean.

---

## 13. Source map

Start here when reading the code:

| Path | Role |
|---|---|
| `src/main/java/com/rdh/swarm/EnhancedSwarm.java` | Meteor module, settings, Host/Worker mode, panel startup, self-deposit |
| `src/main/java/com/rdh/swarm/EnhancedSwarmAddon.java` | Registers 44 modules, both command names, optional harness |
| `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` | TCP listener, connections, queue ownership, leases, storage, deposit config, panel lifecycle |
| `src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java` | Per-worker socket, handshake, message parsing, stale-report rejection, host-side protocol |
| `src/main/java/com/rdh/swarm/EnhancedSwarmWorker.java` | Worker reconnect loop, command dispatch, scanning, deposits, status, task messages |
| `src/main/java/com/rdh/swarm/modules/BotBrain.java` | 18-state worker brain, planning, gathering, crafting, smelting, recovery |
| `src/main/java/com/rdh/swarm/modules/ModuleGuard.java` | Native Meteor survival-module snapshot/configure/restore |
| `src/main/java/com/rdh/swarm/task/BotTask.java` | Task types, statuses, lease/attempt fields |
| `src/main/java/com/rdh/swarm/task/TaskQueue.java` | Queue persistence, claims, leases, recovery, fencing |
| `src/main/java/com/rdh/swarm/task/DependencyResolver.java` | Plan steps for a target item |
| `src/main/java/com/rdh/swarm/task/RecipeBook.java` | Recipe/tier knowledge |
| `src/main/java/com/rdh/swarm/task/SmeltingManager.java` | Furnace/fuel/smelt handling |
| `src/main/java/com/rdh/swarm/task/FoodManager.java` | Food values and raw-food fallback |
| `src/main/java/com/rdh/swarm/DepositDriver.java` | Container opening, menu validation, item movement, delta counting |
| `src/main/java/com/rdh/swarm/DepositConfig.java` | Per-server chest categories and coordinates |
| `src/main/java/com/rdh/swarm/StorageIndex.java` | Per-server stock/chest index |
| `src/main/java/com/rdh/swarm/WaypointManager.java` | Base/waypoint data |
| `src/main/java/com/rdh/swarm/web/SwarmWebServer.java` | Embedded HTTP/WS server, API routes |
| `src/main/java/com/rdh/swarm/web/SwarmWebHandler.java` | Panel request/state handling |
| `src/main/java/com/rdh/swarm/harness/HarnessBridge.java` | Test-only loopback HTTP bridge |
| `src/h5Test/java/com/rdh/swarm/` | Nine lease/recovery/protocol/grace/fuel/removal probes |
| `tools/harness/` | Node runner and live/simulated scenarios |
| `docs/PANEL_V2_SPEC.md` | Deep panel/API/UI handoff |
| `docs/FEATURES.md` | Feature prose; stale in places |
| `docs/ROADMAP.md` | North star, milestones, gates, non-goals |
| `docs/TEST_HARNESS_SPEC.md` | Harness contract and live/simulated status |
| `docs/H5_LEASE_RESTART_PLAN.md` | Lease/restart implementation plan and evidence |
| `AGENTS.md` | Project workflow and evidence rules |
| `CONTRIBUTING.md` | Build/test/branch/panel workflow |

---

## 14. How another AI should work on this

### First moves

1. Read `AGENTS.md` and `CONTRIBUTING.md`.
2. Read this document.
3. Read `docs/PANEL_V2_SPEC.md` if the task touches the panel.
4. Read the actual source files for the behavior in question.
5. Check `git status` and the current `HEAD` before assuming the documentation's snapshot is still current.

### Rules that prevent bad work

- **Do not reset, restore, stash, checkout, or clean the working tree.** It contains an unrelated in-flight reference-corpus reorganization.
- **Do not merge, tag, or push unless asked.**
- **Do not turn a source-present feature into a live claim.** Use the verification ladder.
- **Do not test on an anarchy server.** Use a copied local world.
- **Do not print tokens or anarchy coordinates**, including in generated reports.
- **Do not treat HTTP 200 as accepted.** Assert state and logs.
- **Do not assume `docs/FEATURES.md` is current.** It is not, on leases and scan.
- **Do not add a generic web deployment.** The panel is embedded in the Java host.
- **Do not change one side of the TCP protocol without the other.**

### How to trace a task failure

1. Get the task ID from the panel or `task-queue.json`.
2. Search both `latest.log` files for the task ID and for `TASK:`, `HALT:`, `DEPOSIT:`, `STAGE:`, and `CHEST:` anchors.
3. Check the panel state for status, owner, attempt, lease expiry, and heartbeat age.
4. Map the current `BotBrain` state to the source branch that handles it.
5. Check whether the failure is product behavior, missing setup, a stale lease, or a rejected panel action.
6. Only then read the prose docs.

### Sensible next work, without inventing a roadmap

These follow directly from the gaps above:

- stabilize and commit the in-flight branch in coherent pieces;
- reconcile `docs/FEATURES.md` with lease, scan, and self-deposit behavior;
- rerun the live H5 gate after the closure and record it;
- complete the live H4/H6/H8–H12 gates and implement or explicitly defer H7;
- drive `/v2` with a real bot at least once;
- decide whether queued `GRIND` should be supported or rejected at validation;
- resolve the scan UI/API/doc mismatch;
- clean release metadata and state the canonical repository URL.

Do not implement these merely because they are listed; confirm scope with the maintainer.

---

## 15. Glossary

| Term | Meaning |
|---|---|
| Host | The Meteor client that owns the queue, panel, leases, and storage index. |
| Worker | A Meteor client that connects to a host and runs assigned work. |
| BotBrain | The deterministic worker state machine and task executor. |
| Task | A `BotTask` with type, target, quantity, status, attempt, and lease metadata. |
| Lease | Time-bounded ownership of a task by a worker. |
| Fencing token | The random token that prevents a stale worker from completing a re-leased task. |
| Evidence | The inventory/container check used to justify completion. |
| Deposit driver | The code that opens a chest, validates the menu, moves items, and measures deltas. |
| Storage index | Host-side per-server record of chest contents/category totals. |
| ModuleGuard | Snapshot/configure/restore wrapper around native Meteor survival modules. |
| H0–H12 | The harness scenario catalogue; not all scenarios are live or implemented. |
| `h5Test` | Plain-Java probes for queue recovery/removal, leases, grace re-attach, fencing, panel JSON, protocol matrix, progress accounting, and fuel accounting. |
| Live | A real host/worker run with a recorded artifact. |
| Simulated | A harness stub result; contract evidence only. |
| Observed | A human watched it happen in game. Stronger than "the log says so," but still needs context. |

---

## 16. Bottom line for the next chat

If you remember only five things:

1. **It is a Meteor client addon that coordinates real Minecraft clients, not a server or a hosted service.**
2. **The host owns the queue, panel, storage index, and leases; workers run `BotBrain` and report evidence.**
3. **The transport is plaintext custom TCP with a shared token; the panel is embedded HTTP on port 8888.**
4. **`main` has leases/fencing and self-deposit; parts of `FEATURES.md` still do not reflect them.**
5. **A lot is source-present, some is test-covered, less is live-proven, and the full one-hour acceptance test is not recorded as passed.**

When in doubt, read the source and say what is unverified. That is the project's own standard.
