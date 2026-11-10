# Enhanced Swarm — feature status

This is the current description of what the addon does. It was rebuilt from the
deep-dive notes and checked against `src/` on `main` at `12dd6e6`. The notes
predate the fix stack; where they disagree, the code is the authority.

Two statuses only:

- **OBSERVED** — a person watched it work in game.
- **CODE ONLY** — it compiles and the source path exists, but nobody has watched
  it work in game.

No gameplay was launched for this document. Every gameplay row below is
`CODE ONLY`. The `/v2` panel is the one browser exception: it was opened on a
desktop browser and a phone, against fake players. Layout, tabs and the task
queue render. No bot has driven it in game yet. That is still not in-game
observation, so the row stays `CODE ONLY`.

## Mining and gathering

**What it does.** A host dispatches a `GATHER` task to a worker. `BotBrain`
accepts `GATHER`, `CRAFT` and `SMELT` in its idle state, builds a plan for
crafting/smelting through `DependencyResolver`, and enters `handleGathering`.
The dedicated gather path maps an item to a block with `MINE_FOR` and
`MINE_FOR_2` (for example `cobblestone -> stone`, `diamond -> diamond_ore`,
`raw_iron -> iron_ore`), then sends `#mine minecraft:<block>` through Baritone.
`handleGathering` watches inventory counts and stops Baritone when the quantity
is met. `armBreakWatch`/`tickBreakConfirmation` watch for the block becoming air
and for nearby server-side drops. `DropCollector` sweeps the drops separately.

**How you start it.** `/eswarm task add <item> <quantity> [bot]`, the
`/v2` Tasks tab, or `POST /api/task/add`. `type=auto` chooses `CRAFT` when
`RecipeBook.hasRecipe` knows the item, otherwise `GATHER`.

**What it needs.** A host, a connected worker with `BotBrain` active, Baritone
chat commands, a task whose item has a known source, and a usable tool when the
fallback path decides the target requires one. `gather-staging` moves the bot
away from the base before mining; `gather-staging-distance` and
`gather-staging-band` control that search. `collect-drops` must be enabled for
the final pickup sweep to produce completion evidence.

**What it breaks it.** Unknown items and items without a known source are
failed rather than queued (`/api/task/add` and `SwarmCommand` both check
`DependencyResolver.hasKnownSource`). The dedicated `GATHER` branch does not
perform the fallback path's `requiresTool` check, so a tool-required target can
be sent to `#mine` without a usable tool. That branch also has no inventory-progress deadline: it re-sends only when the block
changes and otherwise waits on inventory, so a mine that never produces items
can sit there. It does fail an air transition that cannot be confirmed within
two seconds (`unconfirmed break`), but a block that never breaks is only cleared
from the break watch and retried. The fallback mining path is different: a
tool-required target with no usable tool sets `STUCK`, and no inventory progress
for 30 seconds stops Baritone and fails the task with `no mining progress`
without reissuing the command. Staging candidates
are scored by floor and headroom, not proven reachable, and a failed goto is
bounded to 60 seconds with two reissues. The `MINE_FOR` alias list is small; a
block without an alias is mined by its item id.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/modules/BotBrain.java`
(`handleIdle`, `handleGathering`, `MINE_FOR`, `MINE_FOR_2`, `armBreakWatch`,
`tickBreakConfirmation`, `ensureStagedAwayFromBase`),
`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` (`dispatchNextTask`),
`src/main/java/com/rdh/swarm/task/DependencyResolver.java` (`resolve`,
`hasKnownSource`), `src/main/java/com/rdh/swarm/task/RecipeBook.java`
(`hasRecipe`).

## The task queue

**What it does.** `TaskQueue` is a `PriorityQueue` of `BotTask` records, with
`PENDING`, `ACTIVE`, `BLOCKED`, `COMPLETE`, `FAILED` and `CANCELLED` statuses.
`getNextPendingForBot` copies the queue and sorts by priority before choosing a
task, so priority dispatch is honored there. `EnhancedSwarmHost.dispatchNextTask`
allows one active task per bot, assigns an unassigned/`any`/`all` task to the
chosen bot, sets it `ACTIVE`, and sends the assignment. `cleanDead` calls
`requeueActiveForBot` when a worker disappears. `BotBrain` implements pause,
resume and cancel through its halt state; the queue keeps paused work and drops
a cancelled task. `/api/task/add` no longer calls `clearCompleted`; only
`/api/task/clear_done` prunes finished rows.

**How you start it.** `/eswarm task add`, `/eswarm task list`,
`/eswarm task cancel`, `pause`/`resume`, or the same controls in `/v2` and the
old panel. The queue is created by `EnhancedSwarmHost` and reloaded at host
construction.

**What it needs.** A host process, a writable
`meteor-client/enhanced-swarm/task-queue.json`, a connected worker for dispatch,
and an item with a known source for `GATHER`/`CRAFT` work. A bot with an active
task is not given another one.

**What it breaks it.** There is no lease, owner token or atomic claim; the only
recovery is `requeueActiveForBot` when a connection is cleaned up. `load()`
restores `ACTIVE` rows as `ACTIVE`, and the host constructor does not requeue all
active rows; after a host restart a task can stay assigned to a worker that no
longer owns it, while `dispatchNextTask` sees `hasActiveTask` and refuses to give
it out. The enum also declares `EQUIP`, `DEPOSIT` and `SCAN_CHESTS`, but
`BotBrain.handleIdle` has no execution branch for those types; a task created
with one of them reaches `failUnhandledTask`. Priority is honored by
`getNextPendingForBot`, not by the raw `PriorityQueue` iterator.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/TaskQueue.java`
(`add`, `addTask`, `save`, `load`, `clearCompleted`, `getNextPendingForBot`,
`requeueActiveForBot`, `hasActiveTask`), `src/main/java/com/rdh/swarm/task/BotTask.java`
(`Type`, `Status`), `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`
(`dispatchNextTask`, `cleanDead`), `src/main/java/com/rdh/swarm/modules/BotBrain.java`
(`handleIdle`, `cancel`, `pause`, `resume`).

## Deposits and chests

**What it does.** `DepositConfig` keeps per-server, per-category lists of chest
positions and a base anchor, with a monotonically increasing generation.
`/eswarm deposit set` records a chest and `pushDepositConfigAll` sends a
`deposit reset <generation>`, one `deposit set` per chest, and
`deposit commit`; the worker replaces its list atomically and logs
`CHEST: list replaced`. `DepositDriver` is the tick-owned transfer path:
`IDLE -> OPENING -> VERIFYING -> TRANSFERRING -> CLOSING -> DONE/FAILED`, one
container action per tick. It checks the container type, the block still at the
target, the observed `containerId`, and counts an observed inventory delta before
publishing `CHEST_DELTA`; a no-op is not a success. `StorageIndex` stores chest
counts and categories. `scan` walks configured chests, reads slots, and sends
`SCAN_CHEST_RESULT`. `WITHDRAW_REQUEST` is handled by the host connection and
`handleWithdraw` on the worker.

**How you start it.** `/eswarm deposit set [category] [x y z]`,
`/eswarm deposit push`, `/eswarm deposit auto on|off`,
`/eswarm deposit threshold <n>`, `/eswarm scan [category]`, or the panel's
deposit actions. `auto-deposit` in `EnhancedSwarm` and `BotBrain.shouldDeposit`
trigger the same `startDeposit` entry point when inventory is full or nearly
full; `/eswarm deposit` remains the manual path.

**What it needs.** At least one chest configured for the current server, a safe
standing position, a container block that is still there when the menu opens, an
empty player menu before opening, and items that match the category. Withdraw
also needs a `StorageIndex` entry and a worker that can path to the chest.

**What it breaks it.** No chest gives `DEPOSIT: refused reason=no-target`. An
already-open menu, a changed block, a different container id or a mismatched menu
gives `menu-mismatch`; a failed transfer gives `timeout`, `no-progress`,
`step-cap` or `click-failed`. The panel's `/api/action/scan_chests` does not scan:
`SwarmWebServer` logs that the container reader is unavailable and sends no
command. The `scan` worker path is synchronous: it can wait up to 60 seconds for
a goto and 10 seconds for a menu, so it blocks that worker thread. Storage
counts are only what the last scan or observed delta reported; there is no TTL
or periodic refresh. The host's `WITHDRAW_REQUEST` handler and the worker's
`handleWithdraw` path are separate from the deposit driver; a withdraw that never
lands leaves the worker in `WITHDRAWING` until its timeout.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/DepositConfig.java`
(`save`, `load`, `addChest`, `getGeneration`, `matchesCategory`),
`src/main/java/com/rdh/swarm/DepositDriver.java` (`request`, `tick`,
`tickOpening`, `tickVerifying`, `tickTransferring`, `afterClose`),
`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` (`setDepositChest`,
`pushDepositConfigAll`), `src/main/java/com/rdh/swarm/EnhancedSwarmWorker.java`
(`handleDeposit`, `startDeposit`, `handleScan`, `handleWithdraw`),
`src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java`
(`WITHDRAW_REQUEST` branch), `src/main/java/com/rdh/swarm/StorageIndex.java`
(`applyChestResult`, `applyChestDelta`, `save`, `load`),
`src/main/java/com/rdh/swarm/modules/BotBrain.java` (`handleWithdrawing`,
`startDeposit`).

## Fuel

**What it does.** `SmeltingManager` resolves a fuel through the vanilla
`DataComponents.COOKING_FUEL` component and `ResolvableInt.getFromItem(...,
CookingFuel::burnTime, ...)`. It counts fuel in the player inventory, deposits
fuel into furnace slot 1, checks whether the furnace is lit or has fuel, and
takes the result. `BotBrain` runs the furnace states: scan for a furnace (or
place/craft one), walk to it, open it, insert fuel and ore, wait, refuel if it
runs dry, collect, and verify the result. `maybeQueueCooking` can queue cooking
for raw food when a furnace and fuel are nearby.

**How you start it.** Queue a `SMELT` task, queue a `CRAFT` task whose plan
contains a smelt step, or enable `auto-cook` and let `maybeQueueCooking` create
the cooking task. The panel's Quick Action `Check Fuel` is not a fuel check:
`SwarmWebHandler` answers `/api/action/check_fuel` with HTTP 501 and
`check_fuel unavailable: container reader not implemented`.

**What it needs.** A furnace (existing, in inventory, or craftable from eight
cobblestone), a fuel stack whose burn time resolves, the ore/raw food in the
inventory, and a client that can reach a `ServerLevel` for the loot context.
That last requirement is the catch: `SmeltingManager.serverLevel()` uses
`Minecraft.getInstance().getSingleplayerServer()`. On a normal multiplayer
client that is null, so `fuelValue` returns `UNRESOLVABLE` with
`no server level (multiplayer client)` and cooking is blocked.

**What it breaks it.** No furnace and fewer than eight cobblestone leaves the
state at `STUCK`. No fuel produces `FUEL: none` or `FUEL: ... unresolvable` and
stops the deposit. An input that never empties times out after ten minutes with
`SMELT: wait timed out after 10 minutes`. A result that is below the requested
quantity leaves the bot `STUCK` rather than claiming completion. The
`check_fuel` endpoint is a 501 stub, so it cannot tell you whether fuel exists.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/SmeltingManager.java`
(`fuelValue`, `buildFuelContext`, `serverLevel`, `depositFuel`, `hasFuel`,
`countFuel`, `fuelStatus`), `src/main/java/com/rdh/swarm/modules/BotBrain.java`
(`handleSmeltingDeposit`, `handleSmeltingWait`, `maybeQueueCooking`,
`hasCookFuel`), `src/main/java/com/rdh/swarm/web/SwarmWebHandler.java`
(`/api/action/check_fuel`).

## Item pickup

**What it does.** `DropCollector` is a bounded sweep, not a general item vacuum.
It is enabled by `collect-drops`, uses `pickup-radius` and `pickup-wait-ms`,
scans `ItemEntity` clusters near the player, cancels Baritone mining, paths to a
cluster, waits for vanilla pickup, and blacklists unsafe or unreachable clusters
for 30 seconds. `BotBrain` ticks the collector before its own state machine.
Before a task is marked complete, `finishTaskWithEvidence` calls
`startFinalSweep`; only `EVIDENCED` or `NOTHING_TO_COLLECT` allow completion.
`UNPROVEN` gets one retry and then fails the task with
`reason=unproven-completion`.

**How you start it.** It runs automatically while `collect-drops` is true, after
a block break or kill that the brain observes, and as the final sweep for a task.
There is no separate pickup command.

**What it needs.** `collect-drops` enabled, Baritone pathing, item entities in
range, a safe non-fluid floor with headroom, and inventory space. The final sweep
also needs the task to have a valid id and the collector to be idle.

**What it breaks it.** With `collect-drops` off, `startFinalSweep` returns
`UNPROVEN` and the task fails after one retry. A full inventory logs
`DROPS: inventory full` and skips the sweep. Unsafe clusters are blacklisted with
`unsafe: ...`; unreachable clusters are blacklisted for 30 seconds. The final
sweep only proves that at least one item was collected, not that the task's
specific item count was observed. `completeShulker` uses `shulkerMovedAny`, set
when a shift-click is issued, rather than a measured inventory delta.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/DropCollector.java`
(`startWorkUnit`, `startFinalSweep`, `tick`, `scan`, `startPath`, `finish`),
`src/main/java/com/rdh/swarm/modules/BotBrain.java` (`onTick`,
`finishTaskWithEvidence`, `completeShulker`).

## The panel (`/v2` and the old `/`)

**What it does.** `SwarmWebServer` binds the configured `bind-address` and
`web-port` (default `127.0.0.1:8888`) and broadcasts state every two seconds.
Every HTTP and WebSocket request must carry the shared token in `X-Swarm-Token`,
`?token=`, or the `swarm_token` cookie; the comparison uses
`MessageDigest.isEqual`. A request with an `Origin` header is accepted only when
it is exactly `http://<Host>`; the panel sets `HttpOnly; SameSite=Strict`, a CSP,
and other security headers. `SwarmWebHandler` serves `/` and `/app.js` from the
inline v1 `SwarmWebPanel`/`SwarmWebPanelScript`, `/v2` and `/v2/app.css`/`app.js`
from `web/v2`, `/api/items` from the registry cache, the POST API, and `/ws`.
`/v2` adds tabs, a status strip, keyed task/bot updates, the log controls and the
phone layout. The old `/` is still the v1 inline page and is not being redesigned.

**How you start it.** Enable the host module; the chat log prints
`Web panel: http://<host>:<port>/?token=<token>`. Open `/` or `/v2` in a browser.
The WebSocket is `ws://<host>/ws`.

**What it needs.** A running host, a reachable bind address and port, the shared
token (or the generated session token), and a same-origin browser request.

**What it breaks it.** A missing or wrong token is 401; a foreign `Origin` is
403. `/api/action/check_fuel` is 501, and `/api/action/scan_chests` logs that the
container reader is unavailable and sends nothing. `/api/items` returns every
registry id, including `minecraft:air`. There is no TLS, and the token is placed
in the query string and printed to chat. Exposing the panel beyond loopback is a
red line. The panel was not opened against a real Minecraft host in this pass, so
the Java server's live auth/WS behavior is not part of the browser evidence.

Status: **CODE ONLY**. `/v2` has been opened on a desktop browser and a phone,
against fake players. Layout, tabs and the task queue all render. No bot has
driven it in game yet. The old `/` was not opened in this pass. Sources:
`src/main/java/com/rdh/swarm/web/SwarmWebServer.java` (`isAuthorized`,
`isOriginAllowed`, `buildStateJson`, `handleApiCommand`),
`src/main/java/com/rdh/swarm/web/SwarmWebHandler.java` (`handleHttp`,
`handleWebSocket`), `src/main/java/com/rdh/swarm/web/SwarmWebPanel.java`,
`src/main/java/com/rdh/swarm/web/SwarmWebPanelV2.java`,
`src/main/resources/web/v2/index.html`, `src/main/resources/web/v2/app.js`,
`src/main/resources/web/v2/app.css`.

## Commands and keybinds

**What it does.** `EnhancedSwarmAddon` registers `/eswarm` and the alias
`/swarm`. `SwarmCommand.build` defines `cancel`/`stop`/`resume`, `exec`, `list`,
`send`, `task add|cancel|pause|resume|list`, `deposit set|remove|list|auto|
threshold|push`, `scan`, `stock`, `base set|clear|show|push`, `dumpstorage`,
`waypoint add|portal|list|remove|goto|push`, and attaches `IdsCommand`. Most
commands require `mod.isHost()`; `exec` falls back to local dispatch when the
client is not hosting. There is no addon-wide keybind. The only keybind setting
in the source is `SwarmHoleFiller.forceFill`; ordinary Meteor module keybinds
still toggle modules.

**How you start it.** Type `/eswarm <command>` (or `/swarm <command>`) in chat.
Use Meteor's module keybind system for module toggles; `force-fill` is the one
addon setting that is explicitly a keybind.

**What it needs.** A host for host-only commands. `exec` needs either a host
connection or a local `BotBrain`/Meteor command dispatcher. `send` needs a known
worker name, `all`, or a nickname that resolves to a worker. Commands that create
tasks need a known item source.

**What it breaks it.** A command run on a worker with no host reports
`Not hosting`. `task add` rejects unknown items and items with no known source.
`waypoint push` only prints a message; it does not serialize or send waypoints,
despite being in the tree. `waypoint goto` sends one `exec #goto` string and does
not confirm arrival. There is no `grind`, `get`, `sort`, or `build` command.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/EnhancedSwarmAddon.java`
(`Commands.add(new SwarmCommand())`, `Commands.add(new SwarmCommand("swarm"))`),
`src/main/java/com/rdh/swarm/commands/SwarmCommand.java` (`build`, `haltAll`,
`resumeAll`, `setDepositFromCrosshair`), `src/main/java/com/rdh/swarm/modules/SwarmHoleFiller.java`
(`forceFill`), `src/main/java/com/rdh/swarm/EnhancedSwarm.java` (`onActivate`,
`onDeactivate`).

## Persistence across restart

**What it does.** `TaskQueue` writes
`meteor-client/enhanced-swarm/task-queue.json` on add, remove, nickname,
requeue and clear, and loads it in its constructor. `DepositConfig` writes
`meteor-client/enhanced-swarm/deposit-chests.json` with per-server chest lists,
base anchors and generations; the host loads it when the TCP server starts.
`StorageIndex` writes `meteor-client/enhanced-swarm/storage-index.json` and loads
it in its constructor. `WaypointManager` writes
`<gameDirectory>/enhanced-swarm/waypoints/<server>/waypoints.json` lazily, keyed
by the sanitized server address. Meteor persists the module settings, including
`auto-deposit`, the deposit/base coordinate settings and other module settings.
`DeathTracker` does not persist: `DeathTracker.load()` is an intentional no-op,
so death positions and dimensions are memory-only. `WorkerConfig` has no file.

**How you start it.** No command: `EnhancedSwarmHost` constructs `TaskQueue`,
`DepositConfig` and `StorageIndex`, and `EnhancedSwarm` constructs the host on
module activation or game join. `WaypointManager` loads on first use.

**What it needs.** A writable game directory, the same server address for
per-server keys, and the same Meteor config directory. A restart does not
preserve a worker's in-memory `WorkerConfig` or `DeathTracker` records.

**What it breaks it.** `TaskQueue.load()` skips `COMPLETE`, `FAILED` and
`CANCELLED`, but keeps `ACTIVE`; the host does not requeue all active tasks on
startup, so a restart can strand a task as described above. `StorageIndex` trusts
the last reported counts and has no expiry. `DeathTracker` loses the record on
restart. `TaskQueue.load()` re-resolves the item id; an unknown id loads with a
null item. `WaypointManager` keys by the sanitized server address, so a changed
address is a different waypoint file.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/TaskQueue.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/DepositConfig.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/StorageIndex.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/WaypointManager.java`
(`getWaypointsFile`, `load`, `save`, `ensureLoaded`),
`src/main/java/com/rdh/swarm/task/DeathTracker.java` (`load`),
`src/main/java/com/rdh/swarm/WorkerConfig.java`, `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`
(constructor).

## Status table

| Feature | Status | Where it lives |
|---|---|---|
| Mining and gathering | `CODE ONLY` | `BotBrain.handleGathering`; `EnhancedSwarmHost.dispatchNextTask`; `TaskQueue` |
| Task queue | `CODE ONLY` | `TaskQueue`; `EnhancedSwarmHost.dispatchNextTask`; `BotBrain` halt state |
| Deposits and chests | `CODE ONLY` | `DepositDriver`; `DepositConfig`; `StorageIndex`; `EnhancedSwarmWorker.handleScan`/`handleWithdraw` |
| Fuel | `CODE ONLY` | `SmeltingManager`; `BotBrain.handleSmeltingDeposit`/`handleSmeltingWait` |
| Item pickup | `CODE ONLY` | `DropCollector`; `BotBrain.finishTaskWithEvidence` |
| Panel (`/v2`, old `/`) | `CODE ONLY` | `SwarmWebServer`; `SwarmWebHandler`; `SwarmWebPanelV2`; `web/v2/*` |
| Commands and keybinds | `CODE ONLY` | `SwarmCommand`; `EnhancedSwarmAddon`; `SwarmHoleFiller.forceFill` |
| Persistence across restart | `CODE ONLY` | `TaskQueue`, `DepositConfig`, `StorageIndex`, `WaypointManager`; `DeathTracker` is memory-only |

None of these rows is `OBSERVED`. The `/v2` row is the only one with browser
evidence, and that evidence is fake players, not a Minecraft client.

## Described but not in the code

Short list, not a roadmap:

- **Tier grinder.** `GearAudit`, `GrindPlanner`, `grindTarget` and
  `/eswarm grind <worker> <tier>` are not present. The only grinder is the
  mob-camp `GRIND` flow in `BotBrain.beginGrind`/`handleGrinding` with
  `auto-grind` and `grind-x/y/z`; it has no tier argument.
- **Logistics commands and types.** `/eswarm get`, `/eswarm sort`,
  `ContainerHelper`, `FETCH`, `SORT`, `sort-rules.json` and `BULK_GATHER` are
  not present. Withdraw exists as `handleWithdraw`/`WITHDRAW_REQUEST`, not as a
  general fetch/sort system.
- **Task types with no handler.** `BotTask.Type.EQUIP`, `DEPOSIT` and
  `SCAN_CHESTS` are enum values only. `BotBrain.handleIdle` does not dispatch
  them; deposits happen in the `DEPOSITING` state and scans happen through the
  `scan` verb.
- **Building and shulker gaps.** There is no shulker **fill** path (only
  `BotBrain.handleShulker` emptying), no `ShulkerHelper`, no `MaterialList`, no
  schematic material planning, and no nether-portal builder. `BUILD` starts
  Baritone but `completeBuild` deliberately fails with
  `unproven-completion` because placement evidence is absent.
- **Registry/storage HTTP endpoints.** `/api/registry/items`,
  `/api/registry/blocks`, `/api/storage`, `/api/waypoints` and
  `/api/grind/status` are not registered. The actual item endpoint is
  `/api/items`; `/api/action/scan_chests` is a logged no-op and
  `/api/action/check_fuel` is a 501.
- **Task leasing.** `LEASE_REQUEST`, a lease table and atomic task claiming are
  not present. `TaskQueue` has `requeueActiveForBot` and `hasActiveTask` only.
- **Death persistence.** `DeathTracker.load()` is a no-op; there is no
  `deaths.json` writer.
- **Waypoint push.** `/eswarm waypoint push` exists in the tree but only logs
  that workers will query the host; it does not serialize or send waypoints.
