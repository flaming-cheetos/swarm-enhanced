# 20 — Phase implementation notes

## G1 — ModuleGuard + survival foundation

Branch: `feat/g1-survival` (from `port-26.3` at `04f358c`).

### What changed

- Added `com.rdh.swarm.modules.ModuleGuard`:
  - Snapshots the guarded module states, enables the guarded survival set, applies settings through `Setting.parse(String)`, re-enables guarded modules every 20 ticks, and restores only the snapshotted modules on deactivate.
  - Guarded: `AutoEat`, `AutoArmor`, `AutoTotem`, `AutoTool`, `AutoReplenish`, `NoFall`, `AntiHunger`, `AutoRespawn`, and optionally `KillAura`.
  - Do not guard `Offhand` or `AutoSmelter`.
  - `KillAura` is configured with `pause-baritone=true`, `ignore-passive=true`, and an `EntityTypeListSetting` containing all `MobCategory.MONSTER` types; players and passive mobs are not included.
  - `isEating()` uses `AutoEat.eating` with a `Player.isUsingItem()` fallback. `isBaritonePaused()` also treats an active `KillAura.attacking` as paused.
- Added BotBrain settings:
  - `manage-modules` Bool, default `true`.
  - `fight-mobs` Bool, default `true`.
- BotBrain lifecycle:
  - `onActivate()` activates the guard (respecting `fight-mobs`) and resets death-capture fields.
  - Tick calls `ModuleGuard.tick()` every 20 ticks.
  - Tick freezes and resets the stuck/state timers while eating or while the guard reports a Baritone pause.
  - `onDeactivate()` restores the guard, sends the chat `#stop`, and calls Baritone `IPathingBehavior.cancelEverything()` in a `try/catch (Exception | NoClassDefFoundError)`.
- `handleIdle` now fails unsupported main task types with one WARN and `TASK_FAILED`, instead of silently entering `GATHERING`; unknown dependency-step types use the same failure path.
- Death capture:
  - Tracks the last alive block position and dimension identifier every callback.
  - Records exactly one death per death event via the new `DeathTracker.recordDeath(UUID, String, BlockPos, String)` overload.
  - `DeathTracker` now stores the dimension and exposes `getDeathDimension()`.
  - `handleRecovery` refuses cross-dimension recovery rather than sending an Overworld `#goto` for a Nether death.

### Public signatures

```text
com.rdh.swarm.modules.ModuleGuard
  void activate(boolean fightMobs)
  void tick(int tickCounter)
  void deactivate()
  boolean isEating()
  boolean isBaritonePaused()
  boolean shouldFreezeTimers()

com.rdh.swarm.task.DeathTracker
  void recordDeath(UUID player, String cause, BlockPos pos)
  void recordDeath(UUID player, String cause, BlockPos pos, String dimension)
  String getDeathDimension()
```

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `manage-modules` | Bool | `true` |
| BotBrain | `fight-mobs` | Bool | `true` |

### Commands / endpoints

None added in G1.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- The build emitted only two pre-existing `BlockStateBase.isSolid()` deprecation warnings.

### Known issues / not verified

- No in-game activation test was possible. `Setting.parse` behavior for every enum/entity setting is a runtime assumption; parse failures produce one WARN and leave the setting unchanged.
- `KillAura`'s pause flag is private; `isBaritonePaused()` approximates it with `KillAura.attacking` and `AutoEat.eating`, so a paused path outside those states may still age timers.
- Death recovery still only navigates to the last death position; it does not guarantee item pickup or safe routing.
- The guard re-enables modules every 20 ticks, so it will also override a manual disable while BotBrain is active by design.
## G2 — Food hunting, cooking, and raw-food fallback

### Summary

- Added `com.rdh.swarm.task.FoodManager`:
  - `countFood()` counts every inventory stack with a `DataComponents.FOOD` component.
  - `cookedCount()` counts cooked/safe-prepared food; `countRaw(Item)` counts a specific raw item.
  - `COOKING` maps raw food to its cooked result (beef, porkchop, mutton, chicken, rabbit, cod, salmon, potato, kelp).
  - `SAFE_RAW` is the subset AutoEat may consume uncooked: beef, porkchop, mutton, rabbit, cod, salmon. Raw chicken and raw potato are intentionally excluded.
- Added `BotTask.Type.HUNT_FOOD` and `BrainState.HUNTING`.
- BotBrain food settings:
  - `food-low` (6): idle auto-hunt water mark.
  - `food-target` (20): hunt stop count and default HUNT_FOOD quantity.
  - `hunt-range` (96): animal search radius.
  - `eat-raw-fallback` (true): allow safe raw food in AutoEat.
- Hunting loop:
  - Targets adult, unnamed, unleashed cows, pigs, sheep, chickens, and rabbits; tamed animals are skipped via `TamableAnimal.isTame()`.
  - Uses Baritone `IFollowProcess.follow(Predicate<Entity>)` while a target exists; cancels it when no target remains or the hunt ends.
  - Attacks within 3 blocks using `Rotations` plus `GameMode.attack` and the 26.3 swing call `swing(InteractionHand, SwingAnimation.DEFAULT, true)`.
  - Collects nearby item drops, explores up to 3 times when no animal is visible, times out after 5 minutes, and reports progress every 10 seconds.
- Automatic food management:
  - `handleIdle` starts HUNT_FOOD when food is below `food-low` (120 s cooldown).
  - Every 40 ticks, a task in GATHERING/IDLE is interrupted when food reaches zero; the interrupted task is stored in `pendingTask` and resumed when the hunt ends.
  - A successful hunt queues a direct cooking task when at least two safe raw items and fuel are available (5 min cooldown).
- Direct cooking task:
  - A `SMELT` task whose target is `FoodManager.cookedResult(source)` bypasses `DependencyResolver` and drives the existing SMELTING_DEPOSIT/SMELTING_WAIT states via `directSmelt`.
  - `getCurrentStep()` returns the main task while `directSmelt`, and completion marks the task fully collected so `handleIdle` sends `TASK_COMPLETE`.
- `ModuleGuard`:
  - `setKillAuraSuppressed(true)` disables KillAura during a hunt; leaving HUNTING restores it through the normal guard tick.
  - `setRawFoodFallback(boolean)` edits AutoEat's `blacklist` `ItemListSetting` for `FoodManager.SAFE_RAW`; a missing setting logs one WARN and leaves behavior unchanged.
- `EnhancedSwarmWorker.KEEP_IDS` now also keeps raw meats/fish, potato, kelp, flint-and-steel, coal/charcoal, and oak planks.
- `SmeltingManager` exposes `COOKING`, `isCookable`, `cookedResult`, and `depositFood`.

### Public signatures

```text
com.rdh.swarm.task.FoodManager
  static final Map<Item, Item> COOKING
  static final Set<Item> SAFE_RAW
  static boolean isFood(ItemStack)
  static int countFood()
  static int cookedCount()
  static int countRaw(Item)
  static boolean isCookable(Item)
  static Item cookedResult(Item)

com.rdh.swarm.modules.ModuleGuard
  void setKillAuraSuppressed(boolean)
  void setRawFoodFallback(boolean)
```

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `food-low` | Int | `6` |
| BotBrain | `food-target` | Int | `20` |
| BotBrain | `hunt-range` | Int | `96` |
| BotBrain | `eat-raw-fallback` | Bool | `true` |

### Commands / endpoints

None added in G2. Food hunts can be requested with a `HUNT_FOOD` task through the existing task queue.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**; `compileJava` and `jar` both ran.
- Verified against the 26.3 merged jar: `EntityTypes.COW/PIG/SHEEP/CHICKEN/RABBIT`, `TamableAnimal.isTame()`, `LivingEntity.swing(InteractionHand, SwingAnimation, boolean)`, and `SwingAnimation.DEFAULT`.

### Known issues / not verified

- No in-game hunt, kill, cooking, or AutoEat fallback test was possible.
- AutoEat's setting name `blacklist` is assumed; the guard fails soft with one WARN if Meteor renames it.
- Hunting only covers five overworld animals; no nether or fish hunting.
- A direct cooking task still requires the normal furnace scan/placement path and will end in STUCK if no furnace or cobblestone is available.
- Raw chicken and potato remain blacklisted because of their hunger/poison effects.
## H — Mob grinder

### Summary

- Added `BotTask.Type.GRIND` and `BrainState.GRINDING`.
- BotBrain grinder settings:
  - `auto-grind` (false): start a session from IDLE when coordinates are configured and food is at least 2.
  - `grind-x` / `grind-y` / `grind-z` (0): standing position; `0 0 0` means unset.
  - `grind-range` (16): hostile search radius, 8–48.
  - `grind-duration` (300 s): session length, 30–3600.
  - `grind-collect` (true): walk to nearby drops.
- Grinder loop:
  - Resolves the position from task data `x`/`y`/`z` when present, otherwise from the settings.
  - Walks to the spot with `#goto` when farther than 5 blocks.
  - Collects item entities within 12 blocks using rate-limited `#goto`.
  - Attacks the nearest `MobCategory.MONSTER` within `grind-range` with `Rotations`, `GameMode.attack`, and the verified 26.3 `swing(InteractionHand, SwingAnimation.DEFAULT, true)` call.
  - Skips tamed and named mobs; walks toward targets between 3 and `grind-range` blocks.
  - Reports progress every 10 seconds; completes at `grind-duration` or when `shouldDeposit()` reports a full inventory.
  - Missing coordinates fail once with `no grinder position configured` and return to IDLE.
- A GRIND task can be queued through the existing task protocol (`Type.valueOf`), and the web task dropdown now offers `GRIND`.
- Task quantity is the session duration in seconds; collected progress is elapsed seconds, so the host progress bar advances and a completed session sends `TASK_COMPLETE`.

### Public signatures

```text
com.rdh.swarm.task.BotTask.Type
  GRIND

com.rdh.swarm.modules.BotBrain.BrainState
  GRINDING
```

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `auto-grind` | Bool | `false` |
| BotBrain | `grind-x` | Int | `0` |
| BotBrain | `grind-y` | Int | `0` |
| BotBrain | `grind-z` | Int | `0` |
| BotBrain | `grind-range` | Int | `16` |
| BotBrain | `grind-duration` | Int | `300` |
| BotBrain | `grind-collect` | Bool | `true` |

### Commands / endpoints

- No new command. Queue a `GRIND` task or set `auto-grind` plus coordinates.
- Web task type dropdown includes `GRIND`.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- Verified against the 26.3 merged jar: `EntityType.getCategory()`, `MobCategory.MONSTER`, and `net.minecraft.world.entity.monster.Monster`.

### Known issues / not verified

- No in-game grinder test was possible; kill credit is approximated by attack count in logs.
- The grinder does not build or repair a farm; it expects an existing kill chamber or spawn area at the configured position.
- Creeper explosions and other environmental damage are not avoided beyond Baritone pathing.
- Inventory-full completion returns to IDLE; the existing deposit logic decides whether to deposit afterward.
## W — Web dashboard task types

### Summary

- Dashboard task creation now supports item-less task types:
  - Added `HUNT_FOOD` and kept `GRIND` in the task-type dropdown.
  - `addTask()` requires an item only for item/task types that need one; `HUNT_FOOD` and `GRIND` submit with an empty item.
  - Task quantity is interpreted by type: food target for `HUNT_FOOD`, duration seconds for `GRIND`.
- `/api/task/add` is now null-safe:
  - Missing `qty` falls back to 64 instead of throwing.
  - Empty/`none` item skips registry lookup and creates a task with a null item.
  - Log label uses the task type when no item is present.
- Worker task assignment no longer uses `Map.of` with a possibly null item; a mutable map stores `item` only as a nullable value, fixing the NPE that would have rejected every item-less task.
- `/api/state` task objects now include `type`, and the task list renders `item || type`, so HUNT_FOOD/GRIND entries are readable.
- Existing food bars, state, task description, progress, ETA, coordinates, pause/resume, and the event log were already present and were left unchanged.

### Public signatures

```text
POST /api/task/add
  item: string (empty for HUNT_FOOD/GRIND)
  qty: int (optional, default 64)
  type: string (optional; AUTO infers CRAFT/GATHER)
  bot: string (optional)
```

### Settings

None added in W.

### Commands / endpoints

- `POST /api/task/add` accepts item-less `HUNT_FOOD` and `GRIND` tasks.
- `/api/state` task entries include `type`.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.

### Known issues / not verified

- No browser or live WebSocket test was possible.
- `SMELT` is still not offered in the dropdown because it needs a raw/result item pair.
- The dashboard cannot set grinder coordinates; use BotBrain settings for `auto-grind` or a task-data extension.
## I — Netherite smithing

### Summary

- Added `com.rdh.swarm.task.SmithingManager`:
  - `UPGRADES` maps all nine diamond gear pieces to their netherite results, armor first.
  - Slot helpers use the verified 26.3 constants `SmithingMenu.TEMPLATE_SLOT` (0), `BASE_SLOT` (1), `ADDITIONAL_SLOT` (2), and `RESULT_SLOT` (3).
  - `ensureTemplate`, `ensureBase`, and `ensureIngot` move items from player inventory slots (4+) into the correct smithing slots with `InvUtils.move()`.
  - `resultReady()` and `takeResult()` use `InvUtils.shiftClick()` on slot 3; `close()` restores the game screen.
- Added `BotTask.Type.SMITH` and `BrainState.SMITHING`.
- BotBrain smithing settings:
  - `auto-netherite` (true): start one smithing task when a template, ingot, and upgradeable diamond piece are all in inventory.
  - `smithing-range` (32): smithing-table search radius.
- SMITH flow: scan for a smithing table, walk within interaction range, open it, fill template/base/ingot, take the result, verify the netherite item, then complete the task. Each phase has tick timeouts and fails with one WARN.
- The smithing task bypasses `DependencyResolver`; the targeted item is the diamond base and completion marks the task fully collected.
- The web task dropdown offers `SMITH`.

### Public signatures

```text
com.rdh.swarm.task.SmithingManager
  static final Map<Item, Item> UPGRADES
  static boolean isUpgradeable(Item)
  static Item upgradeResult(Item)
  static boolean isSmithingOpen()
  static boolean openSmithingTable(BlockPos)
  static boolean ensureTemplate()
  static boolean ensureBase(Item)
  static boolean ensureIngot()
  static boolean resultReady()
  static boolean takeResult()
  static void close()
```

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `auto-netherite` | Bool | `true` |
| BotBrain | `smithing-range` | Int | `32` |

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- Verified against the 26.3 merged jar: `SmithingMenu` slot constants, `Blocks.SMITHING_TABLE`, all diamond gear items, `Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE`, `Items.NETHERITE_INGOT`, and Meteor `InvUtils.move()/shiftClick()`.

### Known issues / not verified

- **PARTIAL:** acquisition is not implemented. The bot does not mine ancient debris, craft netherite ingots, craft a smithing table, or loot a bastion for the upgrade template.
- No in-game smithing-table test was possible; slot indices and result timing are verified only by API signatures.
- Only inventory gear is upgraded. Worn armor is not unequipped first, although `AutoArmor` will equip the result afterward.
- Automatic smithing runs once per 30 seconds and requires all materials to already be present.
## K — Logistics: task recovery

### Summary

- Added `TaskQueue.requeueActiveForBot(String botName)`:
  - Finds ACTIVE tasks assigned to the named worker.
  - Resets them to PENDING and clears the assignment so any worker can claim them.
  - Persists the queue and logs one WARN when at least one task is requeued.
- `EnhancedSwarmHost.cleanDead()` now requeues active tasks for every connection that is interrupted, closed, or has no socket before removing it.
- `EnhancedSwarmHost.disconnect()` requeues active tasks for each connected worker before closing it.
- Existing storage-aware withdrawal is unchanged: `handleGathering` already compares inventory against the storage cache and drives `WITHDRAWING` when storage can satisfy missing materials.

### Public signatures

```text
com.rdh.swarm.task.TaskQueue
  int requeueActiveForBot(String botName)
```

### Settings

None added in K.

### Commands / endpoints

None. Requeueing is automatic on disconnect and host shutdown.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.

### Known issues / not verified

- A bot that stays connected but stops reporting is not requeued; only socket-closed/interrupted connections trigger recovery.
- No cross-bot item transfer or courier task was added.
- Requeued tasks restart from planning; partial `collected` progress is preserved, but BotBrain re-resolves the dependency plan.
## L — Shulker emptying

### Summary

- Added `BotTask.Type.SHULKER` and `BrainState.SHULKER`.
- BotBrain setting `auto-empty-shulkers` (false) starts an emptying task from IDLE when a filled shulker is carried.
- Flow: find a filled shulker via `DepositConfig.isShulkerBox` and a non-empty `DataComponents.CONTAINER`; place it with `PlacementHelper`; open it with `useItemOn` and a verified `ShulkerBoxMenu`; shift-click each of the 27 container slots into inventory; close; destroy the block; walk to the drop.
- Every phase has a tick timeout and fails with one WARN, closing the screen and returning to IDLE.

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `auto-empty-shulkers` | Bool | `false` |

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- Verified 26.3 APIs: `ShulkerBoxMenu`, `Blocks.SHULKER_BOX`, `MultiPlayerGameMode.destroyBlock(BlockPos)`, `ItemContainerContents.size()`, `DataComponents.CONTAINER`.

### Known issues / not verified

- No in-game test. Breaking uses `destroyBlock` every tick; survival break time and tool choice are not optimized.
- Filling/placing items into shulkers is not implemented; this phase only empties.
- Dyed shulkers are supported by the item check, but placement/break detection treats any non-air block at the spot as placed.
- Contents are moved only when the player inventory has room; a full inventory can time out.
## M — Baritone builder

### Summary

- Added `BotTask.Type.BUILD` and `BrainState.BUILDING`.
- Settings: `auto-build` (false), `build-file` (path), `build-x/y/z` (schematic origin).
- `beginBuild` resolves the path from task data `file` or `build-file`, verifies the file exists, and calls the verified `IBuilderProcess.build(String, File, Vec3i)`.
- `handleBuilding` polls `IBuilderProcess.isActive()` and completes when the process stops; a 30-minute timeout fails the task.
- Missing file/process logs one WARN and returns to IDLE.
- The web task dropdown offers `BUILD`.

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `auto-build` | Bool | `false` |
| BotBrain | `build-file` | String | `""` |
| BotBrain | `build-x` / `build-y` / `build-z` | Int | `0` |

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- Verified `IBuilderProcess.build(String, File, Vec3i)` and inherited `isActive()`.

### Known issues / not verified

- No material planning; Baritone decides placeability.
- Only file-based schematics are supported; open Litematica placement is not.
- Origin is a raw coordinate setting; no selection or rotation support.
- No in-game build test.
