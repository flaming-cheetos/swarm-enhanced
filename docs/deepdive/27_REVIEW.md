# Swarm-Enhanced — Mass Review

**Repo:** `C:\Projects\swarm\swarm-improved`
**Scope:** whole addon, 85 Java files, 17,439 lines.
**Mode:** findings only. No source changed, renamed, reformatted or tidied.
**Branch:** `fix/mining` @ `cc231a3`. No `review/mass` branch was created.
**Build:** `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2`, `.\gradlew.bat build --console=plain` — `BUILD SUCCESSFUL in 12s`, exit 0, no compiler warnings. `test NO-SOURCE`.
**Method:** `ponytail-review` order (correct, safe, holds under load, tested, fast, lean). Code read first, then `javap` against the 26.3 Minecraft/Meteor/Baritone jars before any API claim. Everything below is **code only** unless marked `UNVERIFIED — needs client`.

---

## 1. What this addon does

Enhanced Swarm is a Meteor Client addon that runs one host client and any number of worker clients. The host queues `BotTask`s, pushes chest/waypoint configuration, and exposes an HTTP/WebSocket panel; workers run `BotBrain`, which drives Baritone and vanilla container interactions to gather, craft, smelt, hunt, build, deposit and return to base. It also ships a large set of combat/movement proxy modules and an `eswarm` command tree.

---

## 2. The map

### 2.1 Every Java file, by size

```text
 4922 src/main/java/com/rdh/swarm/modules/BotBrain.java
 1535 src/main/java/com/rdh/swarm/EnhancedSwarmWorker.java
  844 src/main/java/com/rdh/swarm/EnhancedSwarm.java
  773 src/main/java/com/rdh/swarm/commands/SwarmCommand.java
  629 src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java
  576 src/main/java/com/rdh/swarm/web/SwarmWebPanel.java
  528 src/main/java/com/rdh/swarm/task/DropCollector.java
  501 src/main/java/com/rdh/swarm/task/RecipeBook.java
  464 src/main/java/com/rdh/swarm/web/SwarmWebServer.java
  425 src/main/java/com/rdh/swarm/EnhancedSwarmHost.java
  410 src/main/java/com/rdh/swarm/modules/SwarmAirCrystal.java
  342 src/main/java/com/rdh/swarm/DepositConfig.java
  292 src/main/java/com/rdh/swarm/WaypointManager.java
  289 src/main/java/com/rdh/swarm/task/DependencyResolver.java
  269 src/main/java/com/rdh/swarm/modules/ModuleGuard.java
  261 src/main/java/com/rdh/swarm/task/TaskQueue.java
  212 src/main/java/com/rdh/swarm/modules/SwarmSpam.java
  208 src/main/java/com/rdh/swarm/task/CraftingManager.java
  205 src/main/java/com/rdh/swarm/task/SmeltingManager.java
  203 src/main/java/com/rdh/swarm/StorageIndex.java
  198 src/main/java/com/rdh/swarm/modules/SwarmPearlChase.java
  173 src/main/java/com/rdh/swarm/utils/PlacementHelper.java
  145 src/main/java/com/rdh/swarm/modules/SwarmAutoTPA.java
  143 src/main/java/com/rdh/swarm/ids/RegistryDumpWriter.java
  132 src/main/java/com/rdh/swarm/utils/ExtrapolationUtils.java
  121 src/main/java/com/rdh/swarm/commands/IdsCommand.java
  118 src/main/java/com/rdh/swarm/web/SwarmWebHandler.java
  110 src/main/java/com/rdh/swarm/utils/OLEPOSSUtils.java
  105 src/main/java/com/rdh/swarm/task/BotTask.java
  104 src/main/java/com/rdh/swarm/task/SmithingManager.java
  102 src/main/java/com/rdh/swarm/task/DeathTracker.java
  101 src/main/java/com/rdh/swarm/EnhancedSwarmAddon.java
   89 src/main/java/com/rdh/swarm/task/FoodManager.java
   87 src/main/java/com/rdh/swarm/SwarmWaypoint.java
   85 src/main/java/com/rdh/swarm/modules/SwarmCrystalAura.java
   69 src/main/java/com/rdh/swarm/timers/TimerList.java
   64 src/main/java/com/rdh/swarm/modules/SwarmElytraFly.java
   61 src/main/java/com/rdh/swarm/modules/SwarmHoleFiller.java
   60 src/main/java/com/rdh/swarm/utils/RotationUtils.java
   60 src/main/java/com/rdh/swarm/modules/SwarmProxyModule.java
   58 src/main/java/com/rdh/swarm/WorkerStatus.java
   55 src/main/java/com/rdh/swarm/modules/SwarmSurround.java
   55 src/main/java/com/rdh/swarm/modules/SwarmKillAura.java
   54 src/main/java/com/rdh/swarm/BotStatus.java
   51 src/main/java/com/rdh/swarm/WorkerConfig.java
   49 src/main/java/com/rdh/swarm/utils/BOInvUtils.java
   48 src/main/java/com/rdh/swarm/modules/SwarmScaffold.java
   47 src/main/java/com/rdh/swarm/modules/SwarmAutoTrap.java
   46 src/main/java/com/rdh/swarm/modules/SwarmAnchorAura.java
   43 src/main/java/com/rdh/swarm/modules/SwarmBedAura.java
   40 src/main/java/com/rdh/swarm/BuildInfo.java
   39 src/main/java/com/rdh/swarm/modules/SwarmNuker.java
   36 src/main/java/com/rdh/swarm/task/BaseMap.java
   36 src/main/java/com/rdh/swarm/modules/SwarmAutoWeb.java
   36 src/main/java/com/rdh/swarm/modules/SwarmAutoCity.java
   35 src/main/java/com/rdh/swarm/modules/SwarmOffhand.java
   34 src/main/java/com/rdh/swarm/modules/SwarmAutoTool.java
   33 src/main/java/com/rdh/swarm/utils/BODamageUtils.java
   33 src/main/java/com/rdh/swarm/modules/SwarmNoSlow.java
   31 src/main/java/com/rdh/swarm/modules/SwarmFlight.java
   31 src/main/java/com/rdh/swarm/modules/SwarmAutoExp.java
   31 src/main/java/com/rdh/swarm/modules/SwarmAutoEat.java
   30 src/main/java/com/rdh/swarm/modules/SwarmJesus.java
   29 src/main/java/com/rdh/swarm/modules/SwarmNoFall.java
   29 src/main/java/com/rdh/swarm/modules/SwarmAutoTotem.java
   29 src/main/java/com/rdh/swarm/modules/SwarmArrowDodge.java
   28 src/main/java/com/rdh/swarm/modules/SwarmSpeed.java
   28 src/main/java/com/rdh/swarm/modules/SwarmAutoArmor.java
   27 src/main/java/com/rdh/swarm/modules/SwarmSpeedMine.java
   27 src/main/java/com/rdh/swarm/modules/SwarmBowAimbot.java
   26 src/main/java/com/rdh/swarm/modules/SwarmAutoReplenish.java
   25 src/main/java/com/rdh/swarm/modules/SwarmStep.java
   25 src/main/java/com/rdh/swarm/modules/SwarmSprint.java
   25 src/main/java/com/rdh/swarm/SwarmHudPresets.java
   23 src/main/java/com/rdh/swarm/modules/SwarmSafeWalk.java
   21 src/main/java/com/rdh/swarm/modules/SwarmFastUse.java
   21 src/main/java/com/rdh/swarm/modules/SwarmAntiVoid.java
   18 src/main/java/com/rdh/swarm/modules/SwarmChestSwap.java
   18 src/main/java/com/rdh/swarm/mixin/ChatUtilsMixin.java
   17 src/main/java/com/rdh/swarm/modules/SwarmReach.java
   15 src/main/java/com/rdh/swarm/modules/SwarmAntiHunger.java
   14 src/main/java/com/rdh/swarm/modules/SwarmTimer.java
   12 src/main/java/com/rdh/swarm/modules/SwarmAutoRespawn.java
   10 src/main/java/com/rdh/swarm/modules/SwarmNoRotate.java
    6 src/main/java/com/rdh/swarm/utils/EpicInterface.java
```

### 2.2 Entry points

- `EnhancedSwarmAddon.onInitialize` — `EnhancedSwarmAddon.java:19`; registers modules at `25-67`, HUD presets at `69`, commands `eswarm` and `swarm` at `75-76`, category at `81`.
- `EnhancedSwarm.onActivate` — `EnhancedSwarm.java:719`; host construction `721`, background config push `723-732`; worker construction `735`.
- `EnhancedSwarm.onDeactivate` — `740-749`; disconnect/null host and permanently shutdown/null worker.
- `EnhancedSwarm.onTick` — `793-795`; only calls `worker.tick()`.
- `BotBrain.onTick` — `BotBrain.java:712`, registered with `@EventHandler` at `711`; 250 ms brain tick at `335,720-722`.
- `EnhancedSwarmHost` — constructor/`ServerSocket` bind `53-68`; `EnhancedSwarmConnection` thread; `SwarmWebServer.start()` `81-82`.
- `SwarmCommand` — constructor `SwarmCommand.java:36`; command tree builds in the class, registered by the addon at `75-76`.
- `SwarmWebServer.start` — `SwarmWebServer.java:96`; Netty bind at `115`; 2-second state scheduler at `119`.

### 2.3 Tick path

`TickEvent.Post` → `BotBrain.onTick:712`

1. Host-mode guard `714-715`; null world guard `717`; `trackDeath()` `718`.
2. Wall-clock throttle, 250 ms: `720-722`; `tickCounter++` `723`; module guard `724-727`.
3. Halt gate: `728-735` — releases keys, logs every 10 s, returns.
4. Food checks: `736-737`; trace log every 20 ticks `738-740`; pause `742`.
5. Drop collector: `743` — **returns early while a sweep is active**.
6. Module timer freeze: `745-749`; death recovery `751-762`.
7. Standing cooking check every 20 ticks in IDLE/HUNTING: `764-767`.
8. Full-inventory deposit transition: `769-774` — sets `DEPOSITING` and sends `#stop`; **does not call `triggerDeposit()`**.
9. State switch `776-795`; ETA `797`.

Commands and state changes reachable from that path:

- staging `#goto` — `BotBrain.java:4740` (one send, guarded by `gatherStagingCommandSent`); mine `#mine` `1048`; `#stop` `773,1043`; recovery `#goto` `2808`; grind `#goto` `3759,3770`; hunt `#goto` `2917`; `#explore` `2929`.
- deposit `#goto` — `EnhancedSwarmWorker.java:825` (deposit executor), `1230,1350` (scan/withdraw executor).
- `setTask` `BotBrain.java:4047`; `setState` `4059`; `cancel` `4842`; `resume` `4905`; completion `handleIdle:813`, `handleGathering:971`.

### 2.4 Thread map

| Thread | What it runs | Cross-thread state |
|---|---|---|
| Client/render tick | `BotBrain.onTick`, all `handle*` state handlers, `SwarmCommand`, most `EnhancedSwarm` settings; `Minecraft.execute` callbacks queued by deposit open/close (`Worker:857,956`). | Reads `BotBrain.state/currentTask/out`; writes Baritone, inventory and module state. |
| `EnhancedSwarmWorker` socket thread | `run` loop `170-215`; `handlePayload` `217-455`; `sendStatus` `1466`; `publishChestSnapshot` `988`. | Calls `Commands.dispatch` `289`, `brain.setTask` `406`, `brain.cancel` `298/426`, `brain.resume` `312/440`; reads `brain.getState()` in `sendStatus`; writes `out` `1475` without a lock. |
| `EnhancedSwarmConnection` host reader thread | `run` reads worker frames; `messageQueue` `20`; status deserialize and storage result handling. | `messageQueue` unbounded; feeds `StorageIndex`; updates `BotStatus`. |
| `MeteorExecutor` tasks | deposit `549,585,873`; scan/withdraw `1161`; multiple `Thread.sleep` loops `839,853,892,947,968,981`. | Reads `mc.player`, `mc.player.containerMenu`, `handler.slots`; calls `InvUtils.shiftClick()` `944`; writes volatile `lastDeposit*` `98-101`. |
| Netty event loop | `SwarmWebHandler.handleHttp` `30-69`, WebSocket frame handler `81-85`; POST calls `handleApiCommand` `60`. | Calls `host.sendTo`/`dispatchNextTask`; reads host/task/bot status; no Origin check. |
| `SwarmWebServer` scheduler | `broadcastState` every 2 s `119`; `buildStateJson` `176`. | Reads `Modules`, `Minecraft.getInstance()`, task queue and `BotStatus` off the client thread. |

The important crossings:

- `BotBrain.state` `310` and `currentTask` `311` are plain fields; `BotBrain.onTick` reads them on the render thread while `EnhancedSwarmWorker` assigns tasks, cancels and resumes on the socket thread (`Worker:298,312,406,426,440`).
- `BotBrain.out` `539` is plain; set by the worker thread at `Worker:405`, read/written by render-thread send methods (`BotBrain:596`). `Worker.out` `41` is also plain, written at `172` and read by `sendStatus` `1475`; only some writes are synchronized (`publishChestSnapshot` `1015`).
- `BotBrain.halted/paused` are volatile (`506-507`); `Worker.isDepositing` and `lastDeposit*` are volatile (`57,98-101`), so visibility is handled there — the defect is lifetime/reset and cumulative meaning, not missing `volatile`.
- `StorageIndex` is synchronized (`39,48,145`); `TaskQueue` methods are synchronized; `DepositConfig` uses concurrent maps plus synchronized lists; `EnhancedSwarmConnection.messageQueue` is a `ConcurrentLinkedQueue` with no bound (`20`).
- `EnhancedSwarm.host/worker` are public plain fields (`151-152`); `onActivate` reads `host` after a 2 s `MeteorExecutor` sleep (`723-731`) while `onDeactivate` can null it (`740-749`). The null checks narrow the window but do not establish visibility.

### 2.5 Churn hot-spots

Most commits (source files only):

```text
26 BotBrain.java
10 SwarmWebServer.java
10 EnhancedSwarmWorker.java
 9 SwarmWebPanel.java
 9 BotTask.java
 7 SmeltingManager.java
 6 TaskQueue.java
 6 EnhancedSwarmHost.java
 5 SwarmCommand.java
 4 RecipeBook.java
```

Most changed lines (`git log --numstat`, source only):

```text
5654 BotBrain.java
1801 EnhancedSwarmWorker.java
 897 SwarmCommand.java
 878 EnhancedSwarm.java
 639 EnhancedSwarmConnection.java
 638 SwarmWebPanel.java
 575 RecipeBook.java
 530 DropCollector.java
 524 SwarmWebServer.java
 496 SwarmAirCrystal.java
```

The review concentrated on the first list, plus `DropCollector`, `DepositConfig`, `StorageIndex`, `PlacementHelper` and the embedded panel because those are directly implicated by the invariants.

### 2.6 Notes contradicted by the code

- `FuelValues` and `level.fuelValues()` do not exist in this tree. Fuel is read through `DataComponents.COOKING_FUEL` (`SmeltingManager:116`, `RecipeBook:464`).
- Staging is not implemented in `WaypointManager`. The floor test is `BotBrain.java:4708` and uses `candidate.below()` — the “pos vs pos.below” note is wrong; the fixed Y is the real problem.
- The web HTML is embedded in `SwarmWebPanel.java` (`getHtml`), not `src/main/resources/web/`.
- The host storage index is `com.rdh.swarm.StorageIndex`, not a `task` package class.
- `BotBrain` uses a 250 ms wall-clock throttle (`BRAIN_TICK_MS=250`), not one brain tick per game tick.

---

## 3. Invariant table

| # | Invariant | Enforced? | Code / break |
|---|---|---|---|
| 1 | Exactly one code path breaks blocks, and it is Baritone. | **Yes (within this addon)** | No `startDestroyBlock`/`destroyBlock`/`continueDestroyBlock` calls in the addon; all block breaking is Baritone `#mine` (`BotBrain:1038`) or the drop collector’s Baritone mine process (`DropCollector:236`). Third-party modules are outside this claim. |
| 2 | At most one Baritone path/process is active, and it is cancelled before a new one starts. | **No** | No central “cancel then start” guard. `BotBrain` reissues `#goto` in recovery/grind/hunt without `isPathing()` (`2807,3757,2916`); `DropCollector.startPath` calls `mine(0,null)` + `cancelEverything()` (`236-238`) but no other path does. |
| 3 | A command already in flight is never issued again in the same or next tick. | **No** | `canSendBaritone()` is only a 3 s wall-clock cooldown (`4332-4334`); it does not know whether a path exists. Direct `sendChat` bypasses it (`Worker:825,1230,1350`). `GATHER` logs before the guard (`1040` vs `1048`). |
| 4 | A drop sweep never starts during a break, and never cancels a path it did not start. | **Partly / No** | First half is checked by `canStartDropSweep` (`4313-4325`) for `isDestroying` and `isPathing`, but not `mineProcess.isActive()`. `startPath` always cancels and ignores `wasBaritoneActive` (`228-238`). |
| 5 | At most one chest screen is open, it belongs to the target chest, and it is always closed — including on exception and cancel. | **No** | “Opened” is any non-inventory `containerMenu` with nonzero container slots (`Worker:879-889`); no block-pos/menu identity check. `screenOpen` is only logged (`796-803`). Close happens only after a successful transfer (`956`); `cancel()` closes any non-inventory menu (`BotBrain:4876-4879`). |
| 6 | No task is marked complete without an observed inventory or storage change. | **No** | `completeBuild` adds the full remaining quantity with no observed block (`3365-3376`); `completeShulker` does the same (`3503-3514`); `handleIdle` completes after a failed final sweep (`813-819`); `handleGathering` does the same (`971-978`). |
| 7 | A task in a non-terminal state always has a live owner: a plan step, a path, or a pending action. | **No** | `handleStuck` sets `IDLE` but leaves `currentTask` (`3905-3910`); `handleDepositing` timeout sets `IDLE` and leaves it (`2780-2784`); `TaskQueue.hasActiveTask` still sees it. |
| 8 | No path can leave no task and a raised halt flag unless asked. | **Yes** | Only `cancel()` raises `halted` and it immediately clears `currentTask` (`4842-4857`); `resume()` clears the flag (`4905-4910`). This is the requested halt state, not an accidental one. |
| 9 | Every value read from a component/registry is distinguished from “absent.” | **No** | Fuel returns `0` both for “not fuel” and “resolution failed” (`SmeltingManager:119-126`); `lastDepositFailure` uses `""` for unknown (`Worker:101,975`); `getOptional(...).orElse(null)` in task load (`TaskQueue:163`); `getBase`/`findHuntTarget` return `null` without a distinct reason. |
| 10 | Every loop that waits on the world has a bound and a log when it hits. | **No** | `continueToStagingTarget` has no timeout or failure log; it sends `#goto` once and returns false every tick if the path never arrives (`4739-4755`). Recovery reissues until the death timer (`2807-2809`). Deposit loops are time-bounded but not all failure paths log. |
| 11 | Inventory indices are checked against the actual container size before use. | **No** | Deposit assumes the player inventory is the last 36 slots: `playerStart = handler.slots.size() - 36` (`Worker:903`). A menu with fewer than 36 total slots yields a negative start and every `getSlot` throws into an ignored catch; standard chests happen to work. `SmeltingManager.hasFuel` assumes slot 1 exists (`112`). |
| 12 | Item identity uses the 26.3 component-aware comparison, not item type alone. | **No** | `DepositConfig.matchesCategory` reduces most stacks to registry item id (`100-114`); `StorageIndex` keys by item id (`39-45`); `RecipeBook` uses `Item` (`463-470`). Enchanted/potion/component variants collapse to one identity. |
| 13 | Counts and stacks are not conflated: one slot of 64 is one slot and 64 items. | **No** | `SmeltingManager.countFuel` adds `burnTicks(stack)` once per stack and never multiplies by `stack.getCount()` (`142-149`). Deposit logs `deposited` as stacks while `moved` is an item count (`Worker:944-958`). |
| 14 | Every `catch` either recovers and says so, or propagates; none swallow. | **No** | 302 occurrences of `catch (Exception ignored)` / `catch (Throwable ignored)`, including 83 in `BotBrain`, 74 in `Worker`, 51 in `Connection`; `StorageIndex` save/load swallow (`141`); `SmeltingManager` converts every fuel exception to zero (`121-125`). |
| 15 | Every registry has a bound and a removal path. | **No** | `TaskQueue` has no max (`33-36`); `DropCollector.blacklist` is a never-pruned `HashMap` (`65,507-510`); `PlacementHelper` static maps are not removed on failure (`23-24,97-124`); `DepositConfig` is append-only (`196-200`); `StorageIndex` has no removal. |
| 16 | No state survives a world change, respawn or module disable that should reset. | **No** | `EnhancedSwarm.onDeactivate` only disconnects host/worker (`740-749`); `BotBrain` fields, `DropCollector.blacklist`, `PlacementHelper` static maps and `TaskQueue` survive. `PlacementHelper` keys are global block positions, not dimension-qualified. |

---

## 4. Known defects — confirm, refute, or find the real cause

| # | Symptom | Verdict | Evidence |
|---|---|---|---|
| 1 | `= 0 ticks` for coal and planks; cooking always `skip: no fuel` | **CONFIRMED** | `SmeltingManager.isFuel` requires `stack.has(DataComponents.COOKING_FUEL)` (`115-117`); `burnTicks` then calls `stack.get(DataComponents.COOKING_FUEL).burnTime().get(null, 0)` (`119-126`). `javap` confirms `CookingFuel.burnTime()` returns `ResolvableInt`; `ResolvableInt.get(LootContext,int)` on a reference calls `LootContext.getResolver()`, so `null` is not a valid resolver. The NPE is caught and converted to `0`. `RecipeBook.getFuelPower` does the same (`467-473`). `countFuel` adds the resulting zero once per stack (`142-149`). Code-level cause confirmed; the exact component present on coal/planks is runtime data. |
| 2 | Every random staging point at the bot’s own Y, `floor=false`, hundreds of blocks apart | **DIFFERENT CAUSE** | The floor test is correct in shape: `candidate.below()` is checked for non-air and no fluid (`BotBrain:4708-4709`); the note’s “pos vs pos.below” is refuted. The real cause is `targetY = stagingOrigin.getY()` (`4703`) plus random XZ 100–300 blocks away (`4699-4703`) and no vertical search. In uneven terrain the fixed-Y candidate usually has air below. After five candidates, or two outer attempts, it gives up and mines locally (`4691-4695,4719-4723`); it does not keep trying hundreds of points. |
| 3 | `GATHER: … | dispatch` 10× in 2 s; `#goto` every 3 s; deposit re-entered | **CONFIRMED** | `GATHER` is logged before `canSendBaritone` (`BotBrain:1040` vs `1048`), so every brain tick during cooldown logs. `handleRecovery` reissues `#goto` every 3 s while recovering (`2807-2809`); grinding does the same (`3757-3759,3768-3771`). The full-inventory transition sets `DEPOSITING` and sends `#stop` (`770-774`) but never calls `worker.triggerDeposit()`; `handleDepositing` then aborts because `isDepositing` is false (`2760-2764`). |
| 4 | One chest → 3 deposit cycles, 2 no-ops, stale `screenOpen=true`, second chest record never opened | **CONFIRMED** | The chest list is append-only and duplicated by config pushes/reconnects (`DepositConfig:196-200`, `Host:89-100,340-347`, `Worker:472,484`, `Connection:112`). `performDepositToChest` tries three times per entry (`805-809`). It accepts any open container as the target (`879-889`), so after the first chest leaves a menu open the next entry “opens” the first chest again and never reaches the second. `screenOpen` is captured once and only printed (`796-803`). |
| 5 | Full inventory: never completes, keeps trying to collect | **CONFIRMED** | `DropCollector.startFinalSweep` calls `startWorkUnit`; a full inventory makes that return false, and the code records `finalSweptTask` and returns false (`116-129`, `98-100`). `handleIdle` then treats false as “no sweep needed” and marks the task complete (`BotBrain:813-819`); `handleGathering` has the same shape (`971-978`). The task can be completed with drops still on the ground, and `finalSweptTask` prevents a later retry. |
| 6 | Walks away from an animal mid-kill | **CONFIRMED** | `handleHunting` calls `findHuntTarget()` every tick and overwrites `lastHuntTargetEntity` (`2876-2879`); `findHuntTarget` chooses the nearest eligible entity (`2934-2946`). Baritone follow is started with `this::isHuntTarget` (`2883`), which matches any eligible animal (`2949-2954`), not the current target’s UUID. A closer animal retargets the follow path and the attack target can switch mid-kill. |
| 7 | `storage=132` → `storage=44` in one task | **CONFIRMED (mechanism); exact sequence UNVERIFIED** | `StorageIndex.applyChestResult` stores a separate record per category, so one chest can exist under `all` and `ores` (`39-45`); `getTotal` sums every category (`145-155`). `BotBrain.mergeLocalStorageCounts` adds a full chest snapshot as if it were a delta (`545-550`); `publishChestSnapshot` feeds the whole chest count after a deposit (`Worker:1015-1024`). `receiveStorageQueryResponse` replaces the cache with no request id/version (`566-571`), so an older response can overwrite a newer cache. The exact 132→44 arithmetic needs client logs. |
| 8 | `HALT: bot idle, task … abandoned`, then resume idles | **CONFIRMED** | `cancel()` sets the task `CANCELLED`, clears `currentTask`, and raises `halted` (`4842-4857`); `resume()` only clears flags and sets `IDLE` (`4905-4910`). The host’s `dispatchNextTask` looks only for `PENDING` tasks (`EnhancedSwarmHost:368-396`, `TaskQueue:207-222`), and the resume command sends `resume` then immediately dispatches (`SwarmCommand:686-692`; web path `SwarmWebServer:357-363`) without waiting for the worker to process the cancel. The cancelled task is never revived. |
| 9 | `Added deposit chest` 3× for one chest | **CONFIRMED** | `DepositConfig.addChest` always appends (`196-200`); `Host.setDepositChest` calls it and broadcasts (`89-100`); `pushDepositConfigAll` re-sends every stored entry (`340-347`); the worker appends each `deposit set` (`472,484`); each connection also pushes the stored entries (`Connection:112`). There is no key by dimension/block position and no replace/clear. |
| 10 | `[RETURN DEBUG]`, `[STAGING DEBUG]` in player chat | **CONFIRMED** | Unconditional chat: `[STAGING DEBUG]` at `BotBrain:946`, `[RETURN DEBUG]` every 20 ticks at `1501`; `[QUERY_START]`/`[QUERY_RESPONSE]` are unconditional `System.out` at `571,577`. The staging random-point and rejection messages also log every attempt (`4714-4717`). |

---

## 5. Findings

### Must fix

**1. The web panel’s “loopback means trusted” model lets any local web page execute commands.**
What it is: `isAuthorized` returns true for every loopback address before checking the token (`SwarmWebServer.java:82-91`). The HTTP handler accepts every POST path (`SwarmWebHandler.java:58-61`), sends `Access-Control-Allow-Origin: *` (`112-115`), and ignores `Origin`. `/api/command` forwards the body to `exec` (`SwarmWebServer.java:414-425`); the worker passes it to `Commands.dispatch` (and server chat) on its socket thread (`EnhancedSwarmWorker.java:281-289`). The WebSocket path is also loopback-authorized and passes raw text into `handleApiCommand` (`SwarmWebHandler.java:81-84`). A page at `https://anything.example` can open `ws://127.0.0.1:8888/ws` or POST to `http://127.0.0.1:8888/api/command`; no token, Origin, CSRF or content-type check stops it.
Concrete case: a malicious page runs `fetch('http://127.0.0.1:8888/api/command',{method:'POST',headers:{'Content-Type':'text/plain'},body:'{"target":"all","cmd":"say owned"}'})`. The request is loopback, so authorized; CORS is irrelevant for the write; the command is dispatched to every worker.
Smallest fix: require the configured token on every endpoint including loopback and WebSocket, reject unknown `Origin`, remove `ACAO: *`, and generate a random token when the setting is empty. Do not treat source address as authentication.
If skipped: any local page or local process can drive the bot, including arbitrary client/server commands as the worker.

**2. Container transfer mutates Minecraft state on the `MeteorExecutor` thread, not the render thread.**
What it is: `performDepositToChest` runs inside `MeteorExecutor.execute` (`EnhancedSwarmWorker.java:873-964`) and directly reads `mc.player.containerMenu`, `handler.slots`, `Slot.getItem()`, and calls `InvUtils.shiftClick().slotId(i)` (`944`). The screen is opened with `mc.execute` (`857-864`), but the actual transfer loop is not scheduled on the client thread; it polls `mc.player.containerMenu` from the executor.
Concrete case: a game tick closes or replaces the menu while the executor is between `handler.getSlot(i)` and `shiftClick`; `handler` is now stale, the click targets a different container/inventory, or `mc.gameMode` is used concurrently. At minimum it is a data race on live UI state; at worst it moves items in the wrong menu or throws into the outer catch, leaving the screen open.
Smallest fix: one tick-driven container state machine; only read `containerMenu`/slots and issue `InvUtils` clicks from the client thread, with the executor used only for waiting/notification.
If skipped: delayed deposits, ghost items, wrong-container clicks, inconsistent inventory counts, and thread crashes that are hard to reproduce.

**3. Fuel resolution can never return a positive burn time for a referenced `CookingFuel`.**
What it is: `burnTicks` calls `burnTime().get(null, 0)` (`SmeltingManager.java:119-126`); `RecipeBook.getFuelPower` repeats it (`467-473`). `javap` shows `CookingFuel.burnTime()` is a `ResolvableInt`; the reference implementation resolves through `LootContext.getResolver()`. Passing `null` throws, and the catch returns zero. The `has(DataComponents.COOKING_FUEL)` gate at `116` also rejects stacks whose fuel is supplied by the item’s default component rather than the stack patch.
Concrete case: coal or planks report `= 0 ticks`; `countFuel()` returns zero (`142-149`); the cook decision logs `skip: no fuel` and never inserts fuel, even with a full inventory.
Smallest fix: resolve `CookingFuel` through a real `LootContext`/server `RecipeManager` (or use the furnace’s own burn-time query), and return a distinct “unknown” result instead of `0` on failure.
If skipped: smelting is permanently disabled for the exact fuels the feature exists to use.

**4. Deposit chests are append-only; config pushes and reconnects multiply the same chest.**
What it is: `DepositConfig.addChest` always `.add(new int[]{x,y,z})` (`DepositConfig.java:196-200`); `Host.setDepositChest` appends and broadcasts (`89-100`); `pushDepositConfigAll` re-sends every stored entry (`340-347`); `EnhancedSwarmWorker.handleDeposit` appends again (`472,484`); `EnhancedSwarmConnection` also pushes the stored list on connect (`112`). There is no dimension/position key, no dedupe, no replace, and no worker-side clear on reconnect.
Concrete case: link chest A, reconnect twice: the host list and worker list each hold three A entries. `performCategoryDeposit` iterates all three, producing three deposit cycles and two no-ops; `Added deposit chest` appears three times.
Smallest fix: canonical key `(dimension, x, y, z, category)` in a set; `add` becomes idempotent `put`; push replaces the worker’s map instead of appending; include a config generation/version.
If skipped: every reconnect increases work and the same chest is opened repeatedly; combined with finding 5, later chests are never reached.

**5. Any open container is accepted as the target chest, and the screen is not closed on failure.**
What it is: `opened` becomes true when `containerMenu != inventoryMenu` and the menu has at least one non-player slot (`EnhancedSwarmWorker.java:879-889`). It never checks that the menu belongs to `chestX/Y/Z`, that it is a chest type, or that the container id matches the interaction. `screenOpen` is captured once and only logged (`796-803`). Close is scheduled only after a successful transfer (`956`); the `!opened` return (`895-899`) and the outer catch (`961-963`) leave whatever menu is open.
Concrete case: a crafting table or furnace is open when deposit starts; the code accepts its 9/3 container slots, computes `playerStart = slots.size()-36`, and shift-clicks player items into the crafting grid/furnace. With a previous chest still open, the next chest record never opens and the same chest is deposited into again.
Smallest fix: interact with the target block, wait for a menu whose type and block position match, record its `containerId`, and close that id in `finally` on every exit path.
If skipped: items can be moved into the wrong container, the second chest is never opened, and the player is left in a screen that blocks movement/commands.

**6. A deposit can report success with zero items moved, and the success flags are cumulative.**
What it is: `InvUtils.shiftClick().slotId(i)` has no result check; `deposited++` happens unconditionally (`EnhancedSwarmWorker.java:944-946`). The return is `depositedCount[0] > 0 ? 1 : 0` (`971-973`), so one failed click is “deposited.” `lastDepositMoved += moved` and `lastDepositSucceeded = true` are never reset at the start of a new deposit (`954`); `lastDepositFailure` is also not cleared. `BotBrain.handleDepositing` reads those fields (`2760-2764`), and `publishChestSnapshot` adds the full chest count whenever `lastDepositMoved > 0` (`1021-1024`).
Concrete case: a full/blocked chest or a category filter rejects every slot; `deposited` is still >0, `moved` is 0, the category loop sees `result == 1`, and `anyDeposited` becomes true. The next deposit sees the old `lastDepositSucceeded=true` and reports “Deposit complete” without a transfer.
Smallest fix: reset all four fields at the start of each deposit; increment `deposited` only when the click succeeds or the slot actually changes; return a distinct `NO_OP`; require an observed inventory delta for success.
If skipped: no-op deposits look successful, the inventory stays full, and the storage cache is inflated by the whole chest.

**7. Halt/resume destroys the active task and resumes before the worker has processed the cancel.**
What it is: `BotBrain.cancel()` marks the current task `CANCELLED`, sends the cancel, clears `currentTask`, and raises `halted` (`4842-4857`). `resume()` only clears flags and enters `IDLE` (`4905-4910`). The host dispatches only `PENDING` (`EnhancedSwarmHost:368-396`; `TaskQueue:207-222`), but the resume command sends `resume` and immediately calls `dispatchNextTask` (`SwarmCommand:686-692`; web `SwarmWebServer:357-363`). The task is already `CANCELLED` (and `clearStale` removes it), so there is nothing to dispatch.
Concrete case: user runs `halt`, then `resume`; the bot stays idle, the task is gone, and the host log says the queued tasks were kept because it counted only `PENDING`.
Smallest fix: model halt as `PAUSED`/requeue of the same task, or make resume wait for a worker ack and re-create/requeue the task; do not dispatch from the command thread.
If skipped: halt is destructive and resume is a no-op for the task that was running.

**8. Completion is asserted, not observed; a failed final sweep still completes the task.**
What it is: `DropCollector.startFinalSweep` calls `startWorkUnit`; when the inventory is full it records `finalSweptTask` and returns false (`DropCollector.java:116-129`, `98-100`). `handleIdle` treats false as “no sweep” and sends `TASK_COMPLETE` (`BotBrain:813-819`); `handleGathering` does the same (`971-978`). `completeBuild` adds the full remaining quantity and completes (`3365-3376`); `completeShulker` does the same (`3503-3514`).
Concrete case: a full inventory prevents the final sweep; the task is marked complete, drops remain on the ground, and `finalSweptTask` blocks a later retry. A build task can complete with no block observed.
Smallest fix: a single completion gate that requires an observed inventory/storage delta (or an explicit “nothing to collect” observation), and returns a failed/retry state when the sweep cannot start. Do not increment `collected` from `getRemaining()`.
If skipped: false success, lost drops, and queued follow-up tasks that assume materials exist.

**9. Staging uses a fixed Y and has no failure timeout; a blocked path stalls the task forever.**
What it is: random staging sets `targetY = stagingOrigin.getY()` and samples XZ 100–300 blocks away (`BotBrain.java:4698-4718`); it never searches vertically. The floor test at `candidate.below()` is correct, but in uneven terrain the fixed-Y candidate is usually air/water. After five candidates, or two outer attempts, it gives up and mines locally (`4691-4695,4719-4723`). `continueToStagingTarget` sends `#goto` once (`4739-4742`), sets `gatherStagingCommandSent`, and returns false every tick until the player is within 20 blocks (`4744-4755`); there is no timeout, no retry, and no failure result.
Concrete case: `#goto` fails because the point is unloaded or unreachable; the brain keeps returning false, never issues another command, and never logs that staging is stuck. If a point is accepted, the fixed Y can put the bot inside a cliff or on a treetop.
Smallest fix: sample Y in a vertical band, require a solid floor plus two air blocks, and add a path timeout/retry counter that returns a real failure (and logs) instead of false forever.
If skipped: staging either rejects every point or deadlocks the gather task; the known `floor=false` log is explained.

**10. Planner and game state are mutated from the socket thread.**
What it is: `EnhancedSwarmWorker.handlePayload` runs on the worker socket thread (`run:181-198`) and calls `Commands.dispatch` (`289`), `brain.setTask` (`406`), `brain.cancel` (`298,426`) and `brain.resume` (`312,440`). Those methods touch `BotBrain.state/currentTask`, Baritone (`cancel:4866-4870`), `mc.player.containerMenu` (`4876-4879`) and module state. `BotBrain.state`/`currentTask` are plain fields (`310-311`) and `onTick` reads them on the client thread (`712`).
Concrete case: a `task assign` arrives while `onTick` is inside `handleGathering`; `setTask` resets the plan and state from the socket thread, then the render thread continues with the old local branches. `exec` can dispatch a Meteor command while the tick is using the same module/container state.
Smallest fix: carry commands as immutable messages and drain them at the top of the client tick (`Minecraft.execute` or a tick queue); all planner/Baritone/container mutation stays on the render thread. Send state snapshots for status.
If skipped: non-deterministic task state, stale plans, Baritone/container races, and crashes that only appear under network load.

**11. The embedded panel has stored XSS through nickname, task reason and log text.**
What it is: `SwarmWebPanel` builds HTML with `innerHTML` and interpolates `bot.nickname` (`423-447`), `t.reason` (`468`) and `msg.msg` (`489`) without HTML/attribute escaping. The server-side `esc` only escapes backslash, double quote and newline for JSON (`SwarmWebServer.java:460-462`); it is not HTML escaping. Nicknames are accepted from `/api/nickname` and log messages can contain arbitrary command text.
Concrete case: set a nickname to `"><img src=x onerror="fetch('/api/command',{method:'POST',body:'{\"target\":\"all\",\"cmd\":\"say x\"}'})">`; the panel renders it and executes the handler in the panel origin. A log line containing `<svg onload=...>` does the same.
Smallest fix: construct DOM nodes and assign `textContent`; use `setAttribute` for values; add a strict CSP; validate/limit nicknames.
If skipped: any worker/nickname or log producer can run script in the operator’s authenticated panel and drive the control API.

### Should fix

**12. Storage totals double-count category aliases, and the cache has no response identity.**
What it is: `StorageIndex.applyChestResult` stores one `ChestRecord` per category (`StorageIndex.java:39-45`), so one physical chest can be counted under `all` and under `ores`; `getTotal` sums every category (`145-155`). `BotBrain.mergeLocalStorageCounts` adds a full chest snapshot (`545-550`), `publishChestSnapshot` sends the whole chest after a deposit (`Worker:1015-1024`), and `receiveStorageQueryResponse` replaces the cache without a request id or sequence (`566-571`).
Concrete case: chest A has 44 cobblestone and is registered in both `all` and `ores`; `getTotal` reports 88. After a deposit, the full chest count is added to the already-populated cache; a late response from an older query then replaces the newer value. This is a plausible route from 132 to 44 without any item movement.
Smallest fix: key records by dimension+block position, not category; compute category totals by selecting the chest set, not summing duplicate records; add a monotonic request id and reject stale responses; merge per-item deltas, never full snapshots.
If skipped: storage planning and withdrawal decisions use double-counted or stale values; the operator cannot trust the number.

**13. The full-inventory deposit transition sets `DEPOSITING` without starting a deposit.**
What it is: `onTick` detects `shouldDeposit()` and `setState(DEPOSITING)` (`BotBrain.java:769-774`), but there is no `worker.triggerDeposit()` in that branch. `handleDepositing` immediately checks `!worker.isDepositing` and aborts to `IDLE` (`2760-2764`). The only normal trigger is the completion branch in `handleGathering` (`989-995`), which is a different path.
Concrete case: inventory fills while mining; brain enters `DEPOSITING`, sends `#stop`, then on the next tick logs `DEPOSIT: aborted — transfer did not complete` and returns to idle. The task remains, and the same transition can repeat.
Smallest fix: make `DEPOSITING` enter only through a single `startDeposit(reason)` method that sets a pending flag, calls `triggerDeposit`, and waits for `isDepositing`; do not use `setState` as the trigger.
If skipped: full-inventory handling does not deposit and generates repeated abort logs.

**14. The hunt follow predicate retargets any animal, not the chosen target.**
What it is: `handleHunting` selects the nearest target each tick (`BotBrain:2876-2879`) and starts Baritone follow with `this::isHuntTarget` (`2883`). `isHuntTarget` accepts any eligible mob (`2949-2954`), and `findHuntTarget` picks the nearest (`2934-2946`). `lastHuntTargetId` is stored but not used in the predicate.
Concrete case: the bot is killing a cow; a chicken enters the follow box; Baritone follows the chicken while the attack code targets the cow (or switches to the chicken next tick). The bot walks away mid-kill.
Smallest fix: follow a predicate that matches `lastHuntTargetId`/UUID, and do not call `findHuntTarget` while the current target is alive; clear the target only on death/out-of-range.
If skipped: kills are interrupted, drops are left behind, and food hunting oscillates.

**15. Stuck and deposit-timeout paths leave an active task with no owner.**
What it is: `handleStuck` sets `IDLE` after 20 cycles but does not clear or fail `currentTask` (`BotBrain:3905-3910`); `handleDepositing` timeout does the same (`2780-2784`). `TaskQueue.hasActiveTask` still reports the task active, so the host refuses to dispatch another one.
Concrete case: a path gets stuck; after 20 cycles the brain goes idle, the task remains `ACTIVE`, and the bot never receives a replacement. The same happens after a 60 s deposit timeout.
Smallest fix: every transition out of a task-bearing state must either fail/requeue the task or set a pending retry; never leave `currentTask` non-null in `IDLE`.
If skipped: the queue stalls with an ownerless active task.

**16. The drop collector cancels an active Baritone mine process it did not start.**
What it is: `canStartDropSweep` checks `mc.gameMode.isDestroying()` and `pathing.isPathing()` (`BotBrain:4313-4325`), but not `mineProcess.isActive()`. `startPath` records `wasBaritoneActive` and then unconditionally calls `getMineProcess().mine(0, null)` and `cancelEverything()` (`DropCollector:228-238`). `wasBaritoneActive` is never read afterward.
Concrete case: `#mine` is between blocks, so pathing is false and `isDestroying` is false; a sweep starts, clears the mine process, and the gather task loses its mining command. The comment says the task loop will reissue, but `currentlyMining`/`mineCommandSent` are only reset in `onSweepFinished`, and the reissue path depends on the state handler reaching it.
Smallest fix: include `mineProcess.isActive()` in the guard; remember whether this collector started the path/process and only cancel what it owns; restore the prior goal/process explicitly.
If skipped: drop collection interrupts mining and causes command churn.

**17. `PlacementHelper.placeBlockSafely` returns the callback result before the callback can run.**
What it is: `Rotations.rotate(yaw, pitch, 50, callback)` queues a rotation; the callback is invoked later by the rotation system. The method immediately returns `result[0]`, which is still false (`PlacementHelper.java:139-158`). Callers at `BotBrain:1898,2550,3422` treat false as placement failure. The static `attempts`/`lastAttempt` maps are only removed on success (`145-146`); failures leave entries.
Concrete case: a crafting table is placed successfully on the next rotation tick, but `placeBlockSafely` returns false; the caller retries, increments `attempts`, and may abort or place a second block. Over a long session the static maps retain every failed position.
Smallest fix: make placement a tick-driven pending action with a completion callback; return a tri-state (`PENDING`/`PLACED`/`FAILED`), and remove both maps on terminal failure.
If skipped: false failures, duplicate placements, and a slow memory leak.

**18. Web log JSON is not actually escaped; one backslash or newline breaks the panel stream.**
What it is: `SwarmWebServer.log` interpolates the message after replacing only double quotes with single quotes (`147-155`). Backslashes, newlines, carriage returns and other control characters are left raw. `buildLogJson` concatenates the stored strings (`253-260`). The panel parses each WebSocket frame with `JSON.parse` and writes `msg.msg` into `innerHTML` (`SwarmWebPanel:489`).
Concrete case: a command contains `C:\users\name`; the JSON contains `\u` and `JSON.parse` throws, so the log entry is dropped or the panel handler errors. A newline in an exception message produces invalid JSON. A `<svg onload=...>` payload becomes XSS as in finding 11.
Smallest fix: use a real JSON writer/encoder for every server message; do not hand-build JSON; keep HTML escaping separate.
If skipped: unreliable log/state delivery and another XSS vector.

**19. The task queue and the per-connection message queue have no bound.**
What it is: `TaskQueue` is a `PriorityQueue` with no maximum (`TaskQueue.java:23,33-36`); `/api/task/add` can add without limit; `save()` runs on every add. `EnhancedSwarmConnection.messageQueue` is a `ConcurrentLinkedQueue` (`20`) filled by host pushes and reconnects with no cap.
Concrete case: a script or a stuck UI adds thousands of tasks; memory grows, every add rewrites the whole JSON file, and the host becomes unresponsive. A worker that stops reading lets the connection queue grow without backpressure.
Smallest fix: a configured maximum with rejection/logging, batched persistence, and a bounded outbound queue with disconnect-on-overflow.
If skipped: memory/disk growth and an unresponsive host under a simple loop.

**20. `ChatUtilsMixin` is a required mixin that suppresses almost nothing.**
What it is: the mixin injects `ChatUtils.info(String, Object[])` and cancels only when `message.equals("BotBrain")` and `args` is null/empty (`ChatUtilsMixin.java:10-16`). The addon calls `ChatUtils.infoPrefix("BotBrain", format, args)` throughout (for example `BotBrain:739,967,4714`), so the message is not exactly `"BotBrain"` and the mixin does not cancel those lines. `swarm.mixins.json` marks the mixin `required: true`.
Concrete case: the intended spam suppression does nothing, while a Meteor signature change or a missing `ChatUtils.info` overload makes the required mixin fail at load and prevents the client from starting.
Smallest fix: remove the mixin and fix the logging at the call sites, or target the actual `infoPrefix` path with a verified signature and a non-required fallback.
If skipped: debug spam remains and the addon carries a hard startup-failure surface for no benefit.

**21. `BotBrain.out` is a stale, unsynchronized stream across disconnect and status writes.**
What it is: `BotBrain.out` is plain (`539`) and set at `Worker:405`; the worker clears its own `out` in `finally` (`207`) but never clears `brain.out`. `BotBrain` synchronizes on `out` for task/status writes (`596`), while `sendStatus` writes `Worker.out` without a lock (`1475`). On reconnect, a new `out` is only installed when a task is assigned (`405`).
Concrete case: the socket drops; a render-thread completion write still targets the closed stream and is swallowed; the host misses the completion. On a fast reconnect, a status frame and a task-progress frame can interleave because one writer does not take the same lock.
Smallest fix: one connection object with a single synchronized `send(String)` method; clear it on disconnect and install the new one immediately on connect; never expose the raw stream to `BotBrain`.
If skipped: lost task messages and corrupted frames at exactly the moment the network is unstable.

### Nice to have

**22. Debug logging is unconditional and includes `System.out` plus chat.**
What it is: `[QUERY_RESPONSE]` and `[QUERY_START]` use `System.out` (`BotBrain:571,577`); `[STAGING DEBUG]` (`946`) and `[RETURN DEBUG]` (`1501`) go to player chat every 20 ticks; staging logs every rejected random point (`4714-4717`).
Concrete case: a normal task fills the operator’s chat and the client log; `[RETURN DEBUG]` appears every 5 s while returning.
Smallest fix: route through a debug setting/logger with rate limiting; keep one summary line per state transition.
If skipped: noisy chat, larger logs, and real warnings buried.

**23. The drop blacklist never removes expired UUIDs.**
What it is: `DropCollector.blacklist` is a `HashMap<UUID,Long>` (`65`); `blacklist()` adds entries with a timeout (`507-510`); `nearbyItems` checks the timeout (`375-376`) but never removes the key. `resetUnit` does not clear it.
Concrete case: hours of play with many item entities leave thousands of dead UUIDs; memory grows and every lookup carries the stale entries.
Smallest fix: prune expired entries on each sweep or use a bounded expiring cache.
If skipped: slow memory growth over long sessions.

**24. `PlacementHelper` and `StorageIndex` have no removal path.**
What it is: `PlacementHelper.lastAttempt`/`attempts` are static maps (`23-24`) removed only on success (`145-146`); `StorageIndex` has `applyChestResult` and `save` but no remove/clear for a chest (`39-45`). `DepositConfig` can remove entries, but `StorageIndex` cannot.
Concrete case: a failed placement leaves a permanent `BlockPos` entry; a removed/relocated chest remains in storage totals and the JSON file.
Smallest fix: remove on terminal failure/world unload; add `removeChest`/`clearServer` to `StorageIndex` and call it when the config changes.
If skipped: stale data and unbounded state across sessions.

**25. Task loading writes the entire queue once per task.**
What it is: `TaskQueue.load` reads all tasks and calls `addTask` for each (`150-179`); `add` calls `save()` (`33-36`), which serializes the whole queue. Loading N tasks performs N full writes (`170-188`).
Concrete case: a queue with hundreds of tasks makes startup O(N²) in serialized bytes and can stall the client thread that constructs the queue.
Smallest fix: load into the in-memory queue and save once after the loop.
If skipped: slow startup and unnecessary disk churn.

**26. The web API exposes a “check fuel” action that cannot do anything.**
What it is: `/api/action/check_fuel` logs “not implemented: container reader unavailable” and returns (`SwarmWebServer:411-413`). The panel can still present it as an action; the server always reports success at the HTTP layer (`SwarmWebHandler:58-61`).
Concrete case: an operator clicks Check Fuel; the UI gets `{"ok":true}` and no fuel information, with only a warning in the log.
Smallest fix: remove the endpoint/button until a real reader exists, or return a non-2xx status and a structured “unavailable” result.
If skipped: false affordance and no diagnostic value.

---

## 6. Concurrency, web and mixins

### Concurrency

- **Render ↔ worker socket:** `state`, `currentTask`, `out` are plain (`BotBrain:310-311,539`); the worker assigns/cancels/resumes (`Worker:298,312,406,426,440`) while the tick reads them. `halted`/`paused` and the deposit result fields are volatile, but that only fixes visibility, not atomicity: a task can be replaced between `handleGathering`'s local reads and its `currentTask` use.
- **Render ↔ MeteorExecutor:** deposit transfer (`Worker:873-964`) and scan/withdraw (`1161`) call `mc.player`, `containerMenu`, `InvUtils` and `handler.slots` from a pool thread. `mc.execute` is used only to open/close the screen, not for the transfer.
- **Render ↔ Netty/scheduler:** `SwarmWebServer` calls `buildStateJson` every 2 s from its scheduler (`119`), reading `Modules`, the task queue and `BotStatus`; `handleApiCommand` runs on a Netty event-loop thread (`WebHandler:30-61`). `host.sendTo` is mostly queue-based, but `dispatchNextTask` mutates the shared queue and `BotStatus.halted` directly.
- **Host reader ↔ host/worker:** `EnhancedSwarmConnection` runs its own thread; `StorageIndex` is synchronized, `TaskQueue` methods are synchronized, `DepositConfig` uses concurrent maps/synchronized lists. `messageQueue` is concurrent but unbounded.
- **Shared collections:** `BotBrain` uses plain `HashMap`/`ArrayList` fields for plans and caches; `lastStorageCache` is volatile and copied on replace, but the replace has no request ordering. `DropCollector.blacklist` is a plain `HashMap` used from the tick; `PlacementHelper` maps are concurrent.
- **Lifecycle:** `EnhancedSwarm.host/worker` are plain public fields (`151-152`); `onActivate` schedules a 2 s `MeteorExecutor` callback that reads `host` (`723-731`); `onDeactivate` can null it (`740-749`). The null check is not a happens-before edge. `BotBrain.out` is never cleared by the worker's `finally` (`Worker:203-207`).

### Web

- Bind is configurable (`bindAddress` default `127.0.0.1`, `EnhancedSwarm:126-131`); web port default 8888 (`134-142`).
- Auth is loopback-or-token (`SwarmWebServer:82-91`), not token-on-every-request. `Access-Control-Allow-Origin: *` is set on every response (`WebHandler:112-115`). No `Origin`, `Referer`, `Sec-Fetch-Site` or content-type check exists.
- `handleHttp` accepts every POST URI and calls `handleApiCommand` (`WebHandler:58-60`); the command switch has no allowlist for `/api/command` (`SwarmWebServer:414-425`). The worker's `exec` path calls `Commands.dispatch` and then sends the same string to server chat (`Worker:281-289`).
- WebSocket upgrade is also behind `isAuthorized`; text frames are passed as both path and body (`WebHandler:81-84`), so a text frame equal to `/api/task/add` is parsed as a body of `/api/task/add` rather than JSON. There is no per-message token.
- Embedded HTML is hand-built. `innerHTML` interpolation of nickname/task reason/log is the stored-XSS surface (finding 11); `log` does not JSON-escape backslashes/control characters (finding 18); `esc` only handles `\`, `"` and `\n` (`SwarmWebServer:460-462`).
- `buildStateJson` escapes strings for JSON, but not for HTML; the panel then injects the decoded strings into HTML. The two contexts need different encoders.
- `SwarmWebHandler.exceptionCaught` closes the channel without logging (`94-96`); protocol errors disappear.

### Mixins

- Only `ChatUtilsMixin` (`swarm.mixins.json`), `required: true`, `defaultRequire: 1`, Java 25. It injects `ChatUtils.info(String, Object[])` at HEAD, cancellable (`ChatUtilsMixin:10-16`).
- Failure mode: a changed Meteor signature or a missing method prevents the mixin from applying; because it is required, the client fails at startup rather than degrading. The condition only suppresses exactly `"BotBrain"` with no args, which the addon does not use; the real `infoPrefix` calls have a formatted message/args and are not suppressed.
- No other mixin touches world, tick, inventory or network state. No mixin can load before Meteor's `ChatUtils` is available beyond the normal mixin bootstrap.

---

## 7. Systematic sweep

**Numbers and units.** `BRAIN_TICK_MS=250` (`BotBrain:335`) and `BARITONE_COMMAND_COOLDOWN=3000` (`500-501`); `System.currentTimeMillis` drives both, so game pause/world time does not stop the cooldowns. Tick counters are used for `%20`, `%40`, `%100` checks (`737-738,964,970`), mixing 4 Hz wall time with tick counts. Staging uses `distToOrigin >= gatherStagingDistance` and random distance 100–300 (`4685-4703`); arrival is `dist < 20` (`4749`). Hunt calls `distanceToSqr` but names it `distance`, then compares `<= 9.0`, i.e. 3 blocks (`2890-2896`). Deposit arrival is `distSqr <= 4.0`, i.e. 2 blocks (`Worker:836`), while path timeout is 60 s and open timeout is 10 s (`806-807`). There is no tick-based fallback for either.
**Absence treated as a value.** `getFuelPower`/`burnTicks` return 0 for “not fuel” and “resolution failed” (`SmeltingManager:119-126`, `RecipeBook:467-473`); `lastDepositFailure` uses `""` (`Worker:101,975`); `TaskQueue.load` uses `getOptional(...).orElse(null)` (`163`); `getBase`/`findHuntTarget` return null with no reason (`DepositConfig`, `BotBrain:2934-2946`); `lastStorageCache` starts empty and is replaced wholesale (`542,566-571`); `stagingTarget` null means both “not selected” and “give up” (`4734-4736`).
**Silent failure.** 302 `catch (Exception ignored)`/`catch (Throwable ignored)` sites: 83 in `BotBrain`, 74 in `Worker`, 51 in `Connection`, 33 in `EnhancedSwarm`, 12 in `Host`, 9 in `WebServer`. `StorageIndex` save/load swallows (`141`); `SmeltingManager` maps every fuel exception to 0 (`121-125`); `SwarmWebHandler.exceptionCaught` closes without logging (`94-96`); `BotBrain` catches transfer/task failures and often only logs a generic line.
**Off-by-one and bounds.** Player inventory loops `0..35` are correct (`shouldDeposit:4185-4188`, `SmeltingManager:145-149`). Deposit assumes the last 36 menu slots are the player: `playerStart = handler.slots.size() - 36` (`Worker:903`); a custom/short menu yields a negative start and ignored exceptions. `SmeltingManager.hasFuel` assumes slot 1 exists (`112`). `TaskQueue` priority order uses a reversed comparator (`29`), and `getNextPendingForBot` copies/sorts the whole list (`207-221`). No off-by-one found in the 3×3 crafting grid loops inspected, but those loops assume the standard 46-slot crafting menu.
**Overflow / zero division.** No reachable division by zero found in the reviewed paths; distances use `sqrt`/`distSqr` without division. `lastDepositMoved` is an `int` accumulated for the session (`Worker:100,954`); overflow needs ~2.1 billion items, so not a practical bug. `count`/`total` are `int` and bounded by 54×64 for a large chest; storage totals are `long` (`StorageIndex:145-155`). JS health percentages divide by 20, not by a variable (`SwarmWebPanel:418-419`).
**Lifecycle leaks.** Screens are the main one: deposit does not close on every failure (finding 5); `cancel()` closes any non-inventory menu (`BotBrain:4876-4879`). `PlacementHelper` maps persist (`23-24`); `DropCollector.blacklist` persists (`65`); `StorageIndex` has no remove (`39-45`); `TaskQueue` has no max (`23,33-36`); `BotBrain.out` is stale after disconnect (`539`). Baritone is cancelled in `cancel`/drop paths, but not consistently before a new `#goto` (`2807,3757,2916`).
**Identity and equality.** Storage/deposit keys are item registry ids, not component-aware stacks (`DepositConfig:100-114`, `StorageIndex:39-45`); `int[]` chest positions have no `equals`/`hashCode`, so lists are the only identity; `BlockPos` keys are dimension-blind (`PlacementHelper:23-24`, `DropCollector` cluster keys); `BotTask` uses a UUID, which is fine. No `==` on boxed numbers or strings found in the reviewed task/deposit paths.
**Reentrancy.** No direct handler calls itself. The dangerous reentry is cross-thread: `Worker.handlePayload` calls `setTask/cancel/resume` while the tick may be inside a handler (finding 10); `/api/task/resume` calls `dispatchNextTask` immediately after sending `resume` (finding 7); `DropCollector.tick` can call `onSweepFinished` and reset mining flags while `handleGathering` is mid-decision (`BotBrain:743,4327-4330`).
**Unbounded loops.** Deposit loops are time-bounded (`829-830,845-847,877,966-969`) but wait on wall-clock and `Thread.sleep`. Staging has no loop but no failure bound (`4739-4755`); recovery reissues until the death timer (`2807-2809`); `TaskQueue`/`messageQueue` grow without bound. No unbounded `while` on world state was found outside those paths.
**Ordering.** `onTick` sets `DEPOSITING` before a trigger exists (finding 13); `lastDepositSucceeded` is read before it is reset for the next deposit (finding 6); `receiveStorageQueryResponse` applies a response before checking any request identity (finding 12); `setState` resets staging/crafting fields before the new action has succeeded (`BotBrain:4059-4115`).
**Duplication that must change together.** Container open/act/close exists independently in deposit (`Worker:857-964`), crafting (`CraftingManager`), smelting (`SmeltingManager`) and smithing (`SmithingManager`); only deposit was traced in depth. Fuel arithmetic is duplicated in `SmeltingManager:119-126` and `RecipeBook:467-473`; distance/arrival math is duplicated across gathering, deposit, grind and hunt.
**Dead weight.** `ChatUtilsMixin` is effectively inert (finding 20); `PlacementHelper.placeBlockSafely(BlockPos)` is a legacy overload that logs and delegates (`161-171`); `DropCollector.wasBaritoneActive` is written and never read (`228-238`); `/api/action/check_fuel` is a no-op endpoint (finding 26); `SwarmProxyModule` and many short `Swarm*` modules are thin wrappers, not independently reviewed at line level. No deletions were made.
**Coverage register.** Core state/network/web: `BotBrain`, `EnhancedSwarm`, `EnhancedSwarmAddon`, `EnhancedSwarmHost`, `EnhancedSwarmConnection`, `EnhancedSwarmWorker`, `SwarmWebServer`, `SwarmWebHandler`, `SwarmWebPanel`, `BotStatus`, `WorkerStatus`, `BuildInfo`, `WorkerConfig`. Task layer: `DropCollector`, `RecipeBook`, `SmeltingManager`, `CraftingManager`, `SmithingManager`, `TaskQueue`, `BotTask`, `DependencyResolver`, `DeathTracker`, `FoodManager`, `BaseMap`, `StorageIndex`. Config/mapping: `DepositConfig`, `WaypointManager`, `SwarmWaypoint`, `ModuleGuard`, `SwarmHudPresets`, `TimerList`. Commands/ids: `SwarmCommand`, `IdsCommand`, `RegistryDumpWriter`. Utilities: `PlacementHelper`, `RotationUtils`, `ExtrapolationUtils`, `OLEPOSSUtils`, `BOInvUtils`, `BODamageUtils`, `EpicInterface`. Modules: `SwarmAirCrystal` and 44 short `Swarm*`/proxy modules; all were opened, but combat/movement behavior is outside the state-machine findings unless it feeds the same block/container/tick path. No additional unique defect was proven in the short wrappers.

---

## 8. Root-cause clusters

**Cluster A — the web control plane has no real trust boundary or output encoding.** Findings 1, 11, 18, 26. The same design decision (“loopback is trusted; hand-built HTML/JSON is fine”) creates unauthenticated command execution, stored XSS, malformed JSON and a fake action. Fixing token-on-every-request, Origin checks and a single encoder closes the security cluster.

**Cluster B — deposit has no owned state machine.** Findings 2, 4, 5, 6, 13, 16, 21. An append-only chest list, an off-thread transfer, an unowned menu, cumulative success flags, a `DEPOSITING` state without a trigger, and a collector that cancels a mine process all share one missing concept: a per-chest action with a stable identity, one owner, one observed result, and a guaranteed close. This is the largest correctness cluster.

**Cluster C — planner state is shared mutable state across threads.** Findings 7, 10, 15, 21. `setTask`, `cancel`, `resume`, task sends and stage transitions happen on the socket thread; the tick reads and writes the same fields. The ownerless-task paths (15) and the cancelled-task resume (7) are symptoms of the same missing serialization point.

**Cluster D — completion and storage are reported, not measured.** Findings 8, 12. `TASK_COMPLETE` can be sent after a failed sweep, and storage totals can be assembled from category aliases/full snapshots. The shared cause is that no single component owns “what changed” and exposes it as an observed delta.

**Cluster E — absence and identity are collapsed.** Findings 3, 12, 14. Fuel `0` means both “not fuel” and “resolution failed”; a chest is keyed by category as well as position; a hunt target is keyed by “any eligible mob.” The code cannot distinguish “unknown” from “none,” so it makes a decision on the wrong value.

**Cluster F — registries have no bound or removal path.** Findings 19, 23, 24, 25. Task queue, connection queue, blacklist, placement maps and storage index all grow. The single change is an explicit lifecycle/limit for each registry, not a different data structure.

**The single change that would close the most:** make the client tick the only writer, and model every game/container/network action as a tick-owned request with an explicit `PENDING/RUNNING/SUCCEEDED/FAILED` result and a canonical identity (`dimension+blockPos`, task UUID, target UUID). That closes or prevents findings 2, 5, 6, 7, 10, 13, 15, 16, 17 and 21, and removes the conditions that make 4, 8 and 12 possible.

---

## 9. Needs live test

These cannot be settled by reading alone. Each is `UNVERIFIED — needs client`.

- **Storage `132 → 44`:** log every `StorageIndex.applyChestResult` with category, chest position and counts; log `lastStorageCache` before/after `mergeLocalStorageCounts` and `receiveStorageQueryResponse` with a request id. Check whether the same chest is present under two categories, and whether a late response replaced a newer cache. Settles finding 12 and known #7.
- **Fuel `= 0 ticks`:** log `stack.has(COOKING_FUEL)`, the component value, `burnTime()` class and the resolver used; compare coal, planks and a lava bucket. Settles whether the `has` gate or the null resolver is the first failure. Settles finding 3/known #1.
- **Staging:** log each candidate’s base Y, `below()` block state/fluid, `candidate` and `above()` air, and the Baritone path result; then test an unreachable point. Settles finding 9/known #2.
- **Deposit cycles:** log the chest list size and each entry at deposit start, the menu class/`containerId`/target position after opening, and whether each `shiftClick` changed the slot. This settles findings 4, 5, 6/known #4/#9.
- **Screen ownership:** deliberately open a crafting table, start a deposit, and force an open failure; verify which menu is closed and whether items moved. Settles finding 5.
- **Completion with full inventory:** fill the inventory, leave a drop on the ground, and observe `TASK_COMPLETE` vs a sweep retry. Settles finding 8/known #5.
- **Halt/resume:** halt during an active task, inspect the task status on host and worker, then resume; check whether the same task is revived or a new one is dispatched. Settles finding 7/known #8.
- **Hunt retarget:** spawn two animals, damage the farther one, and log `lastHuntTargetId` plus Baritone’s followed entity. Settles finding 14/known #6.
- **Placement return value:** log the callback time and the method return time for `placeBlockSafely`; verify whether a block appears after a false return. Settles finding 17.
- **Web auth:** from a second origin, attempt a no-token POST/WS to the loopback panel and inspect `Origin` handling; do not test against a production server. Settles finding 1.
- **Thread checks:** add temporary thread-name logs around `handlePayload`, `performDepositToChest`, `sendStatus` and `handleGathering`; run a deposit while a task arrives. Settles finding 10/21.

---

## 10. Verdict

**Verdict:** not ready for unattended use. The tree compiles, but the build only proves type correctness. The review found eleven Must-fix items: an unauthenticated local web control plane, stored XSS, off-thread container mutation, broken fuel resolution, append-only chest identity, wrong-container/never-closed deposits, no-op deposits reported as success, destructive halt/resume, completion without evidence, fixed-Y staging with no timeout, and cross-thread planner mutation. Several of these are independent causes of the known live symptoms; the known list is a floor, not the whole set.

**Lean:** `-250 lines possible` (duplicate fuel helpers, the legacy placement overload, dead debug blocks, the inert mixin, the no-op check-fuel path). This is an estimate, not a measured deletion; no source was changed.

**Not checked:** live Minecraft/Meteor/Baritone runtime behavior, server protocol interoperability, every third-party combat/movement module at line level, 26.3 registry data not exercised by `javap`, and test coverage — `test NO-SOURCE`.
