# 18 — RECON 26.3 (read-only)

Repo: `C:\Projects\swarm\swarm-improved`; branch `port-26.3`.
No Gradle tasks, builds, or source changes were run for this report. Jars used:

- Meteor: `C:\Users\rdh\.gradle\caches\modules-2\files-2.1\meteordevelopment\meteor-client\26.3-SNAPSHOT\436a0c5423531eccdfd11b7c30d9c74b71a0020b\meteor-client-26.3-SNAPSHOT.jar`
- Baritone: `C:\Users\rdh\.gradle\caches\modules-2\files-2.1\meteordevelopment\baritone\26.3-SNAPSHOT\870981a04658bd53909a7001cd56e9f7143b51d5\baritone-26.3-SNAPSHOT.jar`
- Minecraft 26.3 merged: `C:\Users\rdh\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar`

No `-sources.jar` exists for Meteor or Baritone in the Gradle cache or the project loom cache. Settings below are from `javap -p -c -constants`; type is the field generic, default is the nearest `defaultValue` argument. “not decoded” means the default was present but not safely recoverable from the bytecode slice; it is not a guess.

## Part 1 — Meteor modules available on 26.3

### Existing modules and settings

**`meteordevelopment.meteorclient.systems.modules.player.AutoEat`**
- `blacklist` ItemList = `ENCHANTED_GOLDEN_APPLE, GOLDEN_APPLE, CHORUS_FRUIT, POISONOUS_POTATO, PUFFERFISH, CHICKEN, ROTTEN_FLESH, SPIDER_EYE, SUSPICIOUS_STEW`
- `pause-auras` Bool = `true`
- `pause-baritone` Bool = `true`
- `search-inventory` Bool = `false`
- `food-priority` Enum<`AutoEat.Priority`> = `Saturation`
- `threshold-mode` Enum<`AutoEat.ThresholdMode`> = `Any`
- `health-threshold` Double = `10.0`
- `hunger-threshold` Int = `16`

**`meteordevelopment.meteorclient.systems.modules.combat.AutoArmor`**
- `preferred-protection` Enum<`AutoArmor.Protection`> = `Protection`
- `swap-delay` Int = `1`
- `avoided-enchantments` EnchantmentList = `BINDING_CURSE, FROST_WALKER`
- `blast-prot-leggings` Bool = `true`
- `anti-break` Bool = `false`
- `ignore-elytra` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.combat.AutoTotem`**
- `mode` Enum<`AutoTotem.Mode`> = `Smart`
- `delay` Int = `0`
- `health` Int = `10`
- `elytra` Bool = `true`
- `fall` Bool = `true`
- `explosion` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.combat.Offhand`**
- `item-switch-delay` Int = `0`
- `item` Enum<`Offhand.Item`> = `Crystal`
- `hotbar` Bool = `false`
- `right-gapple` Bool = `false`
- `sword-gapple` Bool = `false`
- `always-gap-on-sword` Bool = `false`
- `always-pot-on-sword` Bool = `false`
- `sword-pot` Bool = `false`
- `min-health` Double = `10.0`
- `elytra` Bool = `false`
- `falling` Bool = `false`
- `explosion` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.combat.KillAura`**
- `attack-when-holding` Enum<`KillAura.AttackItems`> = `Weapons`
- `selected-weapon-types` ItemList = `DIAMOND_SWORD, DIAMOND_AXE, TRIDENT`
- `rotate` Enum<`KillAura.RotationMode`> = `Always`
- `auto-switch` Bool = `false`
- `swap-back` Bool = `false`
- `shield-mode` Enum<`KillAura.ShieldMode`> = `None`
- `only-on-click` Bool = `false`
- `only-on-look` Bool = `false`
- `pause-baritone` Bool = `true`
- `entities` EntityTypeList = `PLAYER`
- `priority` Enum<`SortPriority`> = `ClosestAngle`
- `max-targets` Int = `1`
- `range` Double = `4.5`
- `walls-range` Double = `3.5`
- `passive-mob-age-filter` Enum<`EntityAgeTest`> = `Adult`
- `hostile-mob-age-filter` Enum<`EntityAgeTest`> = `Both`
- `ignore-named` Bool = `false`
- `ignore-passive` Bool = `true`
- `ignore-tamed` Bool = `false`
- `pause-on-lag` Bool = `true`
- `pause-on-use` Bool = `false`
- `pause-on-CA` Bool = `true`
- `TPS-sync` Bool = `true`
- `custom-delay` Bool = `false`
- `hit-delay` Int = `11`
- `switch-delay` Int = `0`

**`meteordevelopment.meteorclient.systems.modules.player.AutoRespawn`**
- No settings. Toggle-only module.

**`meteordevelopment.meteorclient.systems.modules.player.AutoTool`**
- `prefer` Enum<`AutoTool.EnchantPreference`> = `Fortune`
- `silk-touch-for-ender-chest` Bool = `true`
- `fortune-for-ores-and-crops` Bool = `false`
- `anti-break` Bool = `false`
- `anti-break-percentage` Int = `10`
- `switch-back` Bool = `false`
- `switch-delay` Int = `0`
- `list-mode` Enum<`ListMode`> = `Blacklist`
- `whitelist` ItemList = empty
- `blacklist` ItemList = empty

**`meteordevelopment.meteorclient.systems.modules.player.AutoReplenish`**
- `min-count` Int = `8`
- `delay` Int = `1`
- `offhand` Bool = `true`
- `unstackable` Bool = `true`
- `same-enchants` Bool = `true`
- `search-hotbar` Bool = `false`
- `excluded-items` ItemList = empty

**`meteordevelopment.meteorclient.systems.modules.movement.NoFall`**
- `mode` Enum<`NoFall.Mode`> = `Packet`
- `reset-on-disable` Bool = `true`
- `placed-item` Enum<`NoFall.PlacedItem`> = `Bucket`
- `air-place-mode` Enum<`NoFall.PlaceMode`> = `BeforeDeath`
- `rotate` Bool = `true`
- `anchor` Bool = `true`
- `anti-bounce` Bool = `true`
- `pause-on-mace` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.player.AntiHunger`**
- `sprint` Bool = `true`
- `on-ground` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.combat.AutoLog`**
- `health` Int = `6`
- `predict-incoming-damage` Bool = `true`
- `totem-pops` Int = `0`
- `only-trusted` Bool = `false`
- `32K` Bool = `false`
- `smart-toggle` Bool = `false`
- `toggle-off` Bool = `true`
- `toggle-auto-reconnect` Bool = `true`
- `entities` EntityTypeList = `END_CRYSTAL`
- `use-total-count` Bool = `true`
- `combined-entity-threshold` Int = `10`
- `individual-entity-threshold` Int = `2`
- `range` Int = `5`

**`meteordevelopment.meteorclient.systems.modules.player.AutoMend`**
- `blacklist` ItemList = empty
- `force` Bool = `false`
- `auto-disable` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks`**
- `mouse-drag-item-move` Bool = `true`
- `xcarry` Bool = `true`
- `uncap-bundle-scrolling` Bool = `true`
- `frame-input-handling` Bool = `false`
- `sorting-enabled` Bool = `true`
- `sorting-key` Keybind = not decoded
- `sorting-delay` Int = `1`
- `disable-in-creative` Bool = `true`
- `anti-drop-items` ItemList = empty
- `item-frames` Bool = `true`
- `override-bind` Keybind = not decoded
- `auto-drop-items` ItemList = empty
- `exclude-equipped` Bool = `true`
- `exclude-hotbar` Bool = `false`
- `only-full-stacks` Bool = `false`
- `steal-screens` ScreenHandlerList = `GENERIC_9x3, GENERIC_9x6`
- `inventory-buttons` Bool = `true`
- `steal-drop` Bool = `false`
- `drop-backwards` Bool = `false`
- `dump-filter` Enum<`InventoryTweaks.ListMode`> = `None`
- `dump-items` ItemList = empty
- `steal-filter` Enum<`InventoryTweaks.ListMode`> = `None`
- `steal-items` ItemList = empty
- `auto-steal` Bool = `false`
- `auto-dump` Bool = `false`
- `delay` Int = `20`
- `initial-delay` Int = `50`
- `random` Int = `50`

**`meteordevelopment.meteorclient.systems.modules.world.AutoSmelter`** — closest to “AutoSmelt”
- `fuel-items` ItemList = `COAL, CHARCOAL`
- `fuel-items-per-refill` Int = `64`
- `smeltable-items` ItemList = `IRON_ORE, GOLD_ORE, COPPER_ORE, RAW_IRON, RAW_COPPER, RAW_GOLD`
- `disable-when-out-of-items` Bool = `true`
- `auto-close` Bool = `false`

**`meteordevelopment.meteorclient.systems.modules.player.AutoFish`**
- `auto-switch` Bool = `true`
- `anti-break` Bool = `true`
- `auto-cast` Bool = `true`
- `cast-delay` Int = `14`
- `cast-delay-variance` Int = `0`
- `catch-delay` Int = `6`
- `catch-delay-variance` Int = `0`

**`meteordevelopment.meteorclient.systems.modules.world.Nuker`**
- `shape` Enum<`Nuker.Shape`> = `Sphere`
- `mode` Enum<`Nuker.Mode`> = `Flatten`
- `range` Double = `4.0`
- `up` Int = `1`
- `down` Int = `1`
- `left` Int = `1`
- `right` Int = `1`
- `forward` Int = `1`
- `back` Int = `1`
- `walls-range` Double = `4.0`
- `delay` Int = `0`
- `max-blocks-per-tick` Int = `1`
- `sort-mode` Enum<`Nuker.SortMode`> = `Closest`
- `packet-mine` Bool = `false`
- `only-suitable-tools` Bool = `false`
- `interact` Bool = `false`
- `rotate` Bool = `true`
- `list-mode` Enum<`ListMode`> = `Blacklist`
- `blacklist` BlockList = empty
- `whitelist` BlockList = empty
- `select-block-bind` Keybind = not decoded
- `swing` Bool = `true`
- `bounding-box` Bool = `true`
- `nuke-box-mode` Enum<`ShapeMode`> = `Both`
- `side-color` Color = not decoded
- `line-color` Color = not decoded
- `broken-blocks` Bool = `true`
- `nuke-block-mode` Enum<`ShapeMode`> = `Both`
- `side-color` Color = not decoded (second block-color group)
- `line-color` Color = not decoded (second block-color group)

**`meteordevelopment.meteorclient.systems.modules.movement.Scaffold`**
- `blocks` BlockList = empty
- `blocks-filter` Enum<`ListMode`> = `Blacklist`
- `fast-tower` Bool = `false`
- `tower-speed` Double = `0.5`
- `while-moving` Bool = `false`
- `only-on-click` Bool = `false`
- `swing` Bool = `false`
- `auto-switch` Bool = `true`
- `rotate` Bool = `true`
- `air-place` Bool = `false`
- `ahead-distance` Double = not decoded
- `closest-block-range` Double = `4.0`
- `radius` Double = `6.0`
- `blocks-per-tick` Int = `3`
- `render` Bool = `true`
- `shape-mode` Enum<`ShapeMode`> = `Both`
- `side-color` Color = not decoded
- `line-color` Color = not decoded

**`meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly`**
- `mode` Enum<`ElytraFlightModes`> = `Vanilla`
- `auto-take-off` Bool = `false`
- `fall-multiplier` Double = `0.01`
- `horizontal-speed` Double = not decoded
- `vertical-speed` Double = not decoded
- `acceleration` Bool = `false`
- `acceleration-step` Double = `5.0`
- `acceleration-start` Double = `0.1`
- `stop-in-water` Bool = `true`
- `no-unloaded-chunks` Bool = `true`
- `auto-hover` Bool = `false`
- `no-crash` Bool = `false`
- `crash-look-ahead` Int = `5`
- `insta-drop` Bool = `false`
- `pitch40-lower-bounds` Double = `180.0`
- `pitch40-upper-bounds` Double = `220.0`
- `pitch40-rotate-speed-up` Double = `5.45`
- `pitch40-rotate-speed-down` Double = `0.9`
- `auto-jump` Bool = `true`
- `yaw-lock` Enum<`Rotation.LockMode`> = `Smart`
- `yaw` Double = `360.0`
- `pitch-lock` Bool = `true`
- `pitch` Double = `85.0`
- `restart` Bool = `true`
- `restart-delay` Int = `7`
- `sprint-constantly` Bool = `true`
- `manual-takeoff` Bool = `false`
- `elytra-replace` Bool = `false`
- `replace-durability` Int = `2`
- `chest-swap` Enum<`ElytraFly.ChestSwapMode`> = `Never`
- `replenish-fireworks` Bool = `false`
- `replenish-slot` Int = `9`
- `auto-pilot` Bool = `false`
- `use-fireworks` Bool = `false`
- `firework-delay` Double = `8.0`
- `minimum-height` Double = `120.0`

**`meteordevelopment.meteorclient.systems.modules.movement.AutoWalk`**
- `mode` Enum<`AutoWalk.Mode`> = `Smart`
- `simple-direction` Enum<`AutoWalk.Direction`> = `Forwards`
- `disable-on-input` Bool = `false`
- `disable-on-y-change` Bool = `false`
- `no-unloaded-chunks` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.render.StorageESP`**
- `mode` Enum<`StorageESP.Mode`> = `Shader`
- `storage-blocks` StorageBlockList = `STORAGE_BLOCKS`
- `tracers` Bool = `false`
- `shape-mode` Enum<`ShapeMode`> = `Both`
- `fill-opacity` Int = `50`
- `width` Int = `1`
- `glow-multiplier` Double = `3.5`
- `chest` Color = not decoded
- `trapped-chest` Color = not decoded
- `barrel` Color = not decoded
- `shulker` Color = not decoded
- `ender-chest` Color = not decoded
- `other` Color = not decoded
- `fade-distance` Double = `6.0`
- `hide-opened` Bool = `false`
- `opened-color` Color = not decoded

**`meteordevelopment.meteorclient.systems.modules.world.AutoBreed`** — animal-related
- `entities` EntityTypeList = `WOLF, CAT, OCELOT, RABBIT, LLAMA, TURTLE, PANDA, FOX, BEE, STRIDER, HOGLIN`
- `range` Double = `4.5`
- `hand-for-breeding` Enum<`InteractionHand`> = `MAIN_HAND`
- `mob-age-filter` Enum<`EntityAgeTest`> = `Adult`
- `continuous-breeding` Bool = `false`
- `breeding-interval` Int = `6600`

**`meteordevelopment.meteorclient.systems.modules.world.AutoShearer`** — animal-related
- `distance` Double = `5.0`
- `anti-break` Bool = `false`
- `rotate` Bool = `true`

**`meteordevelopment.meteorclient.systems.modules.world.AutoMount`** — animal-related
- `check-saddle` Bool = `false`
- `rotate` Bool = `true`
- `entities` EntityTypeList = empty/not decoded

**`meteordevelopment.meteorclient.systems.modules.combat.BedAura`** — bed module
- `delay` Int = `9`
- `strict-direction` Bool = `false`
- `target-range` Double = `4.0`
- `target-priority` Enum<`SortPriority`> = `LowestHealth`
- `min-damage` Double = `7.0`
- `max-self-damage` Double = `7.0`
- `anti-suicide` Bool = `true`
- `auto-move` Bool = `false`
- `auto-move-slot` Int = `9`
- `auto-switch` Bool = `true`
- `pause-on-eat` Bool = `true`
- `pause-on-drink` Bool = `true`
- `pause-on-mine` Bool = `true`
- `swing` Bool = `true`
- `render` Bool = `true`
- `shape-mode` Enum<`ShapeMode`> = `Both`
- `side-color` Color = not decoded
- `line-color` Color = not decoded

**`meteordevelopment.meteorclient.systems.modules.combat.AntiBed`** — bed-defense module
- `place-string-top` Bool = `false`
- `place-string-middle` Bool = `true`
- `place-string-bottom` Bool = `false`
- `only-in-hole` Bool = `true`

### Modules that do NOT exist on 26.3

- `AutoSmelt` — **not found**. The implemented module is `world.AutoSmelter`.
- `AutoCraft` — **not found**. No auto-crafting-table/crafting-queue Meteor module in this jar.
- `AutoTrade` — **not found**. No villager-trading module in this jar.
- Sleep module — **not found**. The bed-related modules are `combat.BedAura` and `combat.AntiBed`.
- `AutoBreed` itself **does exist**; same for `AutoShearer` and `AutoMount`.

### a) Does AutoEat pause Baritone while eating?

Yes, by default. `AutoEat` has `pause-baritone` Bool default `true`, description “Pause baritone when eating.” In `startEating` bytecode:

```text
115: getfield pauseBaritone
130: invokestatic PathManagers.get
133: invokeinterface IPathManager.isPathing
142: getfield wasBaritone
153: invokestatic PathManagers.get
156: invokeinterface IPathManager.pause
```

`stopEating` resumes only if it was the code that paused pathing:

```text
95: getfield pauseBaritone
111: getfield wasBaritone
119: putfield wasBaritone false
122: invokestatic PathManagers.get
125: invokeinterface IPathManager.resume
```

The pause is one-way guarded by `wasBaritone`; it will not resume a path it did not pause.

### b) Does KillAura fight Baritone pathing?

Yes, optionally and by default. `KillAura` has `pause-baritone` Bool default `true` (description: “Freezes Baritone temporarily until you are finished attacking the entity.”). In `onTick`:

```text
547: invokestatic PathManagers.get
550: invokeinterface IPathManager.isPathing
558: getfield wasPathing
565: invokestatic PathManagers.get
568: invokeinterface IPathManager.pause
573: putfield wasPathing true
```

`stopAttacking` resumes:

```text
14: getfield wasPathing
20: invokestatic PathManagers.get
23: invokeinterface IPathManager.resume
```

Attack behavior itself is not path-aware except for this pause. It rotates via `Rotations.getYaw/getPitch` (bytecode 517-528), then calls `delayCheck()` and attacks targets. `pause-baritone` was found in `AutoEat` and `KillAura`; no other module’s `javap` constant list contained that setting string.

### c) Meteor utilities usable by the addon (public static only)

- `meteordevelopment.meteorclient.utils.player.InvUtils`: `testInMainHand`, `testInOffHand`, `testInHands`, `testInHotbar`, `findEmpty`, `findInHotbar`, `find`, `findFastestTool`, `swap`, `swapBack`, `move`, `click`, `quickSwap`, `shiftClick`, `drop`, `dropOne`, `dropHand`.
- `meteordevelopment.meteorclient.utils.player.PlayerUtils`: `getPlayerColor`, `getHorizontalVelocity`, `centerPlayer`, `canSeeEntity`, `calculateAngle`, `shouldPause`, `isMoving`, `isSprinting`, `isInHole`, `possibleHealthReductions`, `distance`, `distanceTo`, `squaredDistanceTo`, `squaredDistance`, `isWithin`, `distanceToCamera`, `squaredDistanceToCamera`, `isWithinCamera`, `isWithinReach`, `getDimension`, `getGameMode`, `getTotalHealth`, `isAlive`, `getPing`.
- `meteordevelopment.meteorclient.utils.world.BlockUtils`: `init`, `place` (7 overloads), `interact`, `canPlaceBlock`, `canPlace` (2), `getPlaceSide`, `getClosestPlaceSide` (2), `breakBlock`, `canBreak` (2), `canInstaBreak` (2), `calcBlockBreakingDelta2`, `isClickable`, `isValidMobSpawn`, `isValidSpawnBlock`, `getDirection`, `isExposed`, `getBreakDelta`, `mutateAround`.
- `meteordevelopment.meteorclient.utils.player.Rotations`: `init`, `rotate` (5 overloads), `getYaw` (4), `getPitch` (4), `setCamRotation`.
- `meteordevelopment.meteorclient.utils.entity.EntityUtils`: `isAttackable`, `isRideable`, `getTotalHealth`, `getPing`, `getGameMode`, `isAboveWater`, `isInCobweb`, `isInRenderDistance` (4), `getCityBlock`, `getName`, `getColorFromDistance`, `getColorFromHealth`, `intersectsWithEntity`, `getGroup`, `isOnAir`.
- `meteordevelopment.meteorclient.utils.player.InventorySorter`: **no public static methods found**.
- `meteordevelopment.meteorclient.utils.player.SlotUtils`: constants `HOTBAR_START`, `HOTBAR_END`, `MAIN_START`, `MAIN_END`, `ARMOR_START`, `ARMOR_END`, `OFFHAND`; static `indexToId`, `isHotbar`, `isMain`, `isArmor`.
- `meteordevelopment.meteorclient.utils.entity.TargetUtils`: `get`, `getList`, `getPlayerTarget`, `isBadTarget`.

UNKNOWN: exact color/Keybind/ahead-distance/elytra-speed default values were present but not safely decoded from `javap`; no Meteor source jar exists. `AutoMount.entities` default was not decoded. Exact extension mapping inside `DefaultSchematicFormats` is deferred to Part 4.

## Part 2 — How our code touches Meteor modules

### a) `Modules.get()` pattern

The addon uses Meteor’s registry directly; there is no cached module handle in the swarm code. Standard pattern:

```java
EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
```

- `commands/SwarmCommand.java:43,122,143,153,180,198,220,238,248,257,295,312,322,333,351,363,373,386,393,402,415,438,453,466,475,483,504,525,552,573,590,617,636,659`
- `EnhancedSwarmWorker.java:243,364,388,395,402,1013,1026,1376` uses `Modules.get().get(BotBrain.class)`.
- `BotBrain.java:338,526,874,1086,1161,1547,1750,1785` uses `Modules.get().get(EnhancedSwarm.class)`.
- `modules/SwarmProxyModule.java:50` uses it to find the host `EnhancedSwarm`.

There is no `Modules.get().get(SomeMeteorModule.class)` in the addon. The proxy never resolves the remote Meteor module class; it asks the worker to run Meteor’s own `.toggle`/`.settings` command.

### b) Remote module toggle path (RAW)

`SwarmProxyModule` (`modules/SwarmProxyModule.java:41-58`):

```java
protected void pushToggle(boolean on) {
    sendToTarget("exec .toggle " + remoteModuleName + " " + (on ? "on" : "off"));
}

protected void pushSetting(String settingName, Object value) {
    sendToTarget("exec .settings " + remoteModuleName + " " + settingName + " " + value.toString());
}

protected void sendToTarget(String payload) {
    EnhancedSwarm swarm = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
    if (swarm == null || !swarm.isHost() || swarm.host == null) return;
    String target = targetSetting.get();
    if (target == null || target.isEmpty()) target = "All";
    if (target.equalsIgnoreCase("All")) {
        swarm.host.sendAll("swarm:all " + payload);
    } else {
        swarm.host.sendTo(target, "swarm:" + target + " " + payload);
    }
}
```

Worker `handlePayload` (`EnhancedSwarmWorker.java:259-271`):

```java
switch (verb) {
    case "exec":
        try {
            if (args != null && args.startsWith("#")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getConnection() != null) mc.getConnection().sendChat(args);
            } else {
                String cleaned = args;
                if (cleaned != null && cleaned.startsWith(".")) cleaned = cleaned.substring(1);
                Commands.dispatch(cleaned != null ? cleaned : "");
            }
        } catch (Exception ignored) {}
        break;
```

On the worker, `Commands.dispatch("toggle ...")` is Meteor’s own command path, so the module runs in that worker’s process. `exec .settings ...` reaches Meteor’s settings command; this is how a proxy changes a remote setting without direct API access.

Host all-target special case in `SwarmCommand.java:349-357` is for deposit, not module toggles. The web PVP routes send `exec .toggle` to all workers (`SwarmWebServer.java:336-345`).

### c) Do BotBrain/Worker toggle Meteor modules locally?

**No direct toggle calls.** A search of `BotBrain.java`, `EnhancedSwarmWorker.java`, and `EnhancedSwarm.java` for `Modules.get().get(...).toggle` or `.toggle()` returned no matches. Consequences:

- BotBrain does not enable `AutoEat`, `AutoArmor`, `AutoTool`, `KillAura`, etc. Those modules are only reachable through `SwarmProxyModule` / `exec` / the web PVP route.
- The worker’s `exec` handler *can* toggle any Meteor module locally because it dispatches a Meteor command; that is command execution, not a module API call.
- `SwarmProxyModule` itself is a host-side Meteor module whose `onActivate` sends a remote toggle; it does not call the remote class.
- The worker `task` handler calls `BotBrain` methods directly (`setTask`, `cancel`, `pause`, `resume`) but never toggles Meteor modules.

UNKNOWN: Whether every proxy subclass’s `remoteModuleName` is a valid Meteor 26.3 module id was not audited; only the base proxy path was. The exact `exec` permission behavior of Meteor’s command dispatcher on a worker is not verified in-game.

## Part 3 — BotBrain internals

File: `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\modules\BotBrain.java` (3,031 lines).

### a) State enum and main tick dispatch

`BrainState` (`BotBrain.java:79-93`):

```java
public enum BrainState {
    IDLE,
    CHECKING_EQUIPMENT,
    GATHERING,
    WITHDRAWING,
    RETURNING_TO_BASE,
    CRAFTING_PREP,
    CRAFTING,
    SMELTING_DEPOSIT,
    SMELTING_WAIT,
    SMELTING_COLLECT,
    DEPOSITING,
    RECOVERING,
    STUCK
}
```

Main loop: `onTick(TickEvent.Post)` at `BotBrain.java:335-390`.

```java
if (swarmMod == null || swarmMod.mode.get() == com.rdh.swarm.EnhancedSwarm.Mode.Host) return;
if (mc.player == null || mc.level == null) return;
if (now - lastBrainTick < BRAIN_TICK_MS) return;
...
if (paused) return;

if (deathTracker.hasDeath() && deathTracker.canRecover()) {
    handleRecovery();
    return;
} ...
switch (state) {
    case IDLE -> handleIdle();
    case CHECKING_EQUIPMENT -> handleEquipmentCheck();
    case GATHERING -> handleGathering();
    case WITHDRAWING -> handleWithdrawing();
    case RETURNING_TO_BASE -> handleReturningToBase();
    case CRAFTING_PREP -> handleCraftingPrep();
    case CRAFTING -> handleCrafting();
    case SMELTING_DEPOSIT -> handleSmeltingDeposit();
    case SMELTING_WAIT -> handleSmeltingWait();
    case SMELTING_COLLECT -> handleSmeltingCollect();
    case DEPOSITING -> handleDepositing();
    case RECOVERING -> handleRecovery();
    case STUCK -> handleStuck();
}
updateEta();
```

Tick throttle: `BRAIN_TICK_MS = 250` (`BotBrain.java:120`); client-only guard at 338-339; pause guard at 351.

### b) `onActivate` / `onDeactivate` — ModuleGuard hook points

`BotBrain.java:315-333`:

```java
@Override
public void onActivate() {
    state = BrainState.IDLE;
    currentTask = null;
    executionPlan = null;
    planStep = 0;
    stuckCounter = 0;
    deathTracker.load();
    stateEnterTime = System.currentTimeMillis();
    lastBrainTick = System.currentTimeMillis();
    lastItemCheckTime = System.currentTimeMillis();
}

@Override
public void onDeactivate() {
    state = BrainState.IDLE;
    currentTask = null;
    executionPlan = null;
}
```

A `ModuleGuard` can hook the two overrides directly. The important deficiency: `onDeactivate()` does **not** call `cancel()`, does not send `#stop`, does not call `PathManagers.stop()`, and does not toggle the Meteor modules BotBrain may have enabled through proxies. If a guard disables BotBrain, it must explicitly stop Baritone and restore module state; the current deactivation leaves the worker pathing.

### c) Baritone pause/resume/start/stop and cooldown

Baritone is almost entirely driven by chat commands, not API:

- Start mining: `sendBaritoneChat("#mine ...")` in `handleGathering` (many call sites; e.g. `BotBrain.java:578-604`).
- Start walking: `sendBaritoneChat("#goto ...")` in `handleRecovery` (`2318`), `handleReturningToBase`, staging.
- Stop: `cancel()` `3008`, `pause()` `3014`; many `sendBaritoneChat("#stop")` sites.
- `PathManagers.stop()` is called in the worker, not BotBrain: `EnhancedSwarmWorker.java:187,1405,1413`.
- The only `baritone.api` import/use in the swarm tree is `BotBrain.java:2788` (ETA fallback):

```java
try {
    if (baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
        currentEta = "Pathing";
    } else {
        currentEta = "Waiting";
    }
} catch (Exception | NoClassDefFoundError e) {
    currentEta = "Unknown";
}
```

Cooldown helper (`BotBrain.java:2535-2544`):

```java
private boolean canSendBaritone() {
    return System.currentTimeMillis() - lastBaritoneCommand > BARITONE_COMMAND_COOLDOWN;
}

private void sendBaritoneChat(String command) {
    lastBaritoneCommand = System.currentTimeMillis();
    if (mc.getConnection() != null) {
        mc.getConnection().sendChat(command);
    }
}
```

`BARITONE_COMMAND_COOLDOWN` is `3_000` ms in the current source. The cooldown is global to BotBrain; it is not a path-completion callback. `sendBaritoneChat` unconditionally sends through the vanilla chat connection and does not check whether Baritone is present; only the ETA read is guarded by `NoClassDefFoundError`.

Pause/cancel/resume (`BotBrain.java:3003-3020`):

```java
public void cancel() {
    try { sendTaskFailed(currentTask, "cancelled"); } catch (Exception ignored) {}
    this.currentTask = null;
    this.executionPlan = null;
    this.planStep = 0;
    sendBaritoneChat("#stop");
    setState(BrainState.IDLE);
}

public void pause() {
    paused = true;
    sendBaritoneChat("#stop");
}

public void resume() {
    paused = false;
    setState(BrainState.IDLE);
}
```

### d) Death recovery; hunger/health/mob handling

Death check is first in `onTick` (`BotBrain.java:353-364`):

```java
if (deathTracker.hasDeath() && deathTracker.canRecover()) {
    handleRecovery();
    return;
} else if (deathTracker.hasDeath() && !deathTracker.canRecover()) {
    deathTracker.clearDeath();
    if (currentTask != null) {
        try { sendTaskFailed(currentTask, "Bot died"); } catch (Exception ignored) {}
        currentTask = null;
    }
    setState(BrainState.IDLE);
}
```

`handleRecovery` is `BotBrain.java:2305-2330`; it sends `#goto` to the last death position and clears only when within 3 blocks (`distSqr < 9`). It does not select a safe route, does not equip recovery gear, and does not handle the “items despawned” case.

`DeathTracker` (`task/DeathTracker.java:30`) defines `recordDeath(UUID, String, BlockPos)`, but a tree-wide search found **only the definition**; no caller. Therefore `hasDeath()` is always false in the current code, and the whole `RECOVERING` path is dead. `load()` is intentionally a no-op (`DeathTracker.java:48-50`).

Hunger/health/mob handling in BotBrain: **none beyond status reporting**.

- No `FoodData`/`getFoodData` use, no eating call, no `useItem` for food.
- No local `AutoEat` toggle or `Modules.get().get(AutoEat.class)`.
- No mob targeting, hunting, breeding, or cooking logic. `AutoBreed`/`AutoShearer` metadata exists in Meteor but is not invoked by BotBrain.
- `AutoEat` remains a remote proxy/host module only; see Part 1.
- `EnhancedSwarmWorker` updates health/food in `BotStatus` for the web panel, but never acts on them.

### e) All BotBrain settings

From `BotBrain.java:38-77`; one group, `General`:

| Name | Type | Default | Range |
|---|---|---|---|
| `auto-equip` | Bool | `true` | — |
| `crafting-table-range` | Int | `5` | 3–15 |
| `deposit-threshold` | Int | `4` | 1–15 |
| `gather-staging` | Bool | `true` | — |
| `gather-staging-distance` | Int | `160` | 32–512 |

### f) DependencyResolver tier/equipment + BotBrain 440–500 RAW

`DependencyResolver.getCurrentTier()` (`task/DependencyResolver.java:242-250`) only checks the pickaxe:

```java
public static RecipeBook.Tier getCurrentTier() {
    if (mc.player == null) return RecipeBook.Tier.Wood;
    if (CraftingManager.countInInventory(Items.NETHERITE_PICKAXE) > 0) return RecipeBook.Tier.Netherite;
    if (CraftingManager.countInInventory(Items.DIAMOND_PICKAXE) > 0) return RecipeBook.Tier.Diamond;
    if (CraftingManager.countInInventory(Items.IRON_PICKAXE) > 0) return RecipeBook.Tier.Iron;
    if (CraftingManager.countInInventory(Items.STONE_PICKAXE) > 0) return RecipeBook.Tier.Stone;
    return RecipeBook.Tier.Wood;
}
```

`getEquipmentTasks(Tier)` (`DependencyResolver.java:252-283`) returns craft tasks for the tier’s pickaxe/axe/sword/armor; it does not inspect currently worn armor, offhand, or tool durability.

BotBrain equipment path (`BotBrain.java:452-490`):

```java
private void handleEquipmentCheck() {
    RecipeBook.Tier needed;
    BotTask step = getCurrentStep();

    if (step != null) {
        needed = RecipeBook.getRequiredTier(step.getTargetItem());
    } else {
        needed = RecipeBook.getRequiredTier(currentTask.getTargetItem());
    }

    RecipeBook.Tier current = DependencyResolver.getCurrentTier();

    if (current.level >= needed.level) {
        currentTaskDescription = "Equipment OK (" + current.name() + ")";
        if (step != null && step.getType() == BotTask.Type.CRAFT) {
            craftPrepReturnPlanStep = planStep;
            setState(BrainState.CRAFTING_PREP);
        } else {
            setState(BrainState.GATHERING);
        }
        return;
    }

    // Need to upgrade — resolve equipment tasks
    List<BotTask> equipTasks = DependencyResolver.getEquipmentTasks(needed);
    if (!equipTasks.isEmpty()) {
        executionPlan.addAll(0, equipTasks);
        planStep = 0;
        currentTaskDescription = "Upgrading to " + needed.name() + " tier";
        setState(BrainState.IDLE);
    } else {
        if (step != null && step.getType() == BotTask.Type.CRAFT) {
            setState(BrainState.CRAFTING_PREP);
        } else {
            setState(BrainState.GATHERING);
        }
    }
}
```

The check is tier-only. A diamond pickaxe anywhere in inventory satisfies “Diamond”, even if the bot is wearing leather and holding a wooden sword. The resulting `EQUIP` task type exists in `BotTask.Type` but `handleIdle` has no `EQUIP` case (defaults to `GATHERING`).

### g) Armor/offhand/hotbar/container helpers

No BotBrain-specific helper exists for armor slots, offhand, or generic container clicking. The code uses Meteor and task helpers directly:

- `CraftingManager.countInInventory(Item)` — `task/CraftingManager.java:49`.
- `InvUtils.findInHotbar(Predicate)`, `InvUtils.find(...)`, `InvUtils.swap(int, boolean)`, `InvUtils.move().from(...).toHotbar(...)` — `BotBrain.java:1254-1268, 1995-2178`.
- `BOInvUtils.getSelectedSlot()` / slot constants for crafting grid and inventory.
- `ContainerInput.QUICK_MOVE`, `ContainerInput.PICKUP` with `mc.gameMode.handleContainerInput(...)` for chest/furnace/crafting transfers; inline in `BotBrain` (`1597-1735`, `1829-1940`).
- Closing is `mc.setScreenAndShow(null)`.
- No `openContainerAt(pos, action)` helper; open/act/close sequences are repeated in `handleGathering` (crafting table), `handleSmelting*`, `handleDepositing`, and `handleWithdrawing`.
- No armor-slot swap helper; `AutoArmor`/`InvUtils` are not called by BotBrain.

### h) Food: KEEP_IDS and cooking/hunting

`KEEP_IDS` lives in `EnhancedSwarmWorker.java:73-84`, with cooked meats, bread, golden foods, stews, totem, pearls, fireworks, crystals, elytra, and shield. `KEEP_PREFIXES` at 86-89 keeps netherite/diamond/iron/stone/wooden/golden gear; suffixes at 91-92 keep tools/armor. `shouldKeep` at 94-105 explicitly returns false for shulkers.

There is **no cooking or hunting logic** in BotBrain or Worker:

- No furnace use for food; `SmeltingManager.depositOre` accepts only configured ore items.
- No cow/pig/chicken targeting, no `FoodData` reads, no eating.
- `KEEP_IDS` only protects food during deposits; it does not create food.
- `AutoEat` can eat remote food if enabled, but BotBrain never enables it or reacts to hunger.

UNKNOWN: `BARITONE_COMMAND_COOLDOWN`’s declaration line is not quoted above; its value is 3,000 ms in the current source. In-game behavior of `#stop` when Baritone is absent was not tested. No mob/hunger behavior was found, but no exhaustive bytecode audit of Meteor’s `AutoEat` activation state was performed.

## Part 4 — Baritone API on 26.3

Jar: `C:\Users\rdh\.gradle\caches\modules-2\files-2.1\meteordevelopment\baritone\26.3-SNAPSHOT\870981a04658bd53909a7001cd56e9f7143b51d5\baritone-26.3-SNAPSHOT.jar`. No sources jar.

### a) `IBaritone` process getters

`javap -public baritone.api.IBaritone`:

```text
IPathingBehavior getPathingBehavior()
ILookBehavior getLookBehavior()
IFollowProcess getFollowProcess()
IMineProcess getMineProcess()
IBuilderProcess getBuilderProcess()
IExploreProcess getExploreProcess()
IFarmProcess getFarmProcess()
ICustomGoalProcess getCustomGoalProcess()
IGetToBlockProcess getGetToBlockProcess()
IElytraProcess getElytraProcess()
IWorldProvider getWorldProvider()
IPathingControlManager getPathingControlManager()
IInputOverrideHandler getInputOverrideHandler()
IPlayerContext getPlayerContext()
IEventBus getGameEventHandler()
ISelectionManager getSelectionManager()
ICommandManager getCommandManager()
void openClick()
```

No `getAutoEatProcess` or `getInventoryProcess` exists. `IElytraProcess` is present in 26.3.

### b) `IBuilderProcess`

```text
void build(String, ISchematic, Vec3i)
boolean build(String, File, Vec3i)
default boolean build(String, BlockPos)
void buildOpenSchematic()
void buildOpenLitematic(int)
void pause()
boolean isPaused()
void resume()
void clearArea(BlockPos, BlockPos)
List<BlockState> getApproxPlaceable()
Optional<Integer> getMinLayer()
Optional<Integer> getMaxLayer()
```

Inherited from `IBaritoneProcess`: `boolean isActive()`, `PathingCommand onTick(boolean, boolean)`, `boolean isTemporary()`, `void onLostControl()`, `double priority()`, `String displayName()`, `String displayName0()`.

**No `getMissing`, `getMissingMaterials`, `getRequiredMaterials`, or equivalent exists in `IBuilderProcess`.** `getApproxPlaceable()` only returns the blocks the builder thinks it can place now; it is not a material deficit API. Material planning must come from the schematic or from our own scan.

### c) `IMineProcess` and `IFarmProcess`

```text
IMineProcess:
  void mineByName(int, String...)
  void mine(int, BlockOptionalMetaLookup)
  default void mine(BlockOptionalMetaLookup)
  default void mineByName(String...)
  default void mine(int, BlockOptionalMeta...)
  default void mine(BlockOptionalMeta...)
  default void mine(int, Block...)
  default void mine(Block...)
  default void cancel()

IFarmProcess:
  void farm(int, BlockPos)
  default void farm()
  default void farm(int)
```

`mineByName` takes the quantity first, then names; e.g. `mineByName(64, "iron_ore")`. This is the clean API replacement for repeated `#mine` chat.

### d) Settings relevant to building, mining, inventory

Types are the field generics; names are exact:

- Building: `buildInLayers` Bool; `buildOnlySelection` Bool; `buildRepeat` Vec3i; `buildRepeatCount` Int; `buildRepeatSneaky` Bool; `buildIgnoreBlocks` BlockList; `buildSkipBlocks` BlockList; `buildValidSubstitutes` `Map<Block,List<Block>>`; `buildSubstitutes` `Map<Block,List<Block>>`; `buildIgnoreExisting` Bool; `buildIgnoreDirection` Bool; `buildIgnoreProperties` `List<String>`; `okIfAir` BlockList; `schematicOrientationX/Y/Z` Bool; `buildSchematicRotation` Rotation; `buildSchematicMirror` Mirror; `schematicFallbackExtension` String; `builderTickScanRadius` Int.
- Break/place safety: `allowBreak` Bool; `allowPlace` Bool; `allowPlaceInFluidsSource` Bool; `allowPlaceInFluidsFlow` Bool; `allowBreakAnyway` BlockList; `blocksToAvoid` BlockList; `blocksToDisallowBreaking` BlockList; `blocksToAvoidBreaking` BlockList; `avoidBreakingMultiplier` Double; `breakFromAbove` Bool; `breakCorrectBlockPenaltyMultiplier` Double; `placeIncorrectBlockPenaltyMultiplier` Double.
- Inventory: `allowInventory` Bool; `inventoryMoveOnlyIfStationary` Bool; `ticksBetweenInventoryMoves` Int; `acceptableThrowawayItems` ItemList. **No health/eating setting** was found in `baritone.api.Settings`; `doDeathWaypoints` Bool is the closest death setting.
- Mining: `mineGoalUpdateInterval` Int; `mineMaxOreLocationsCount` Int; `allowOnlyExposedOres` Bool; `allowOnlyExposedOresDistance` Int; `mineScanDroppedItems` Bool; `mineDropLoiterDurationMSThanksLouca` Long.
- Farming: `farmUsingSelection` Bool; `farmMaxScanSize` Int.
- Compatibility: `antiCheatCompatibility` Bool.

### e) Registered command classes

`jar tf .../baritone/command/defaults/` shows:

`AxisCommand`, `BlacklistCommand`, `BuildCommand`, `ClickCommand`, `ComeCommand`, `ETACommand`, `ElytraCommand`, `ExecutionControlCommands`, `ExploreCommand`, `ExploreFilterCommand`, `FarmCommand`, `FindCommand`, `FollowCommand`, `ForceCancelCommand`, `GcCommand`, `GoalCommand`, `GotoCommand`, `HelpCommand`, `InvertCommand`, `LitematicaCommand`, `MineCommand`, `PathCommand`, `PickupCommand`, `ProcCommand`, `ReloadAllCommand`, `RenderCommand`, `RepackCommand`, `SaveAllCommand`, `SchematicaCommand`, `SelCommand`, `SetCommand`, `SurfaceCommand`, `ThisWayCommand`, `TunnelCommand`, `VersionCommand`, `WaypointsCommand`, plus `DefaultCommands` and aliases.

Canonical chat names exposed by these classes are the lower-case command names (`build`, `farm`, `mine`, `sel`, `schematica`, `litematica`, `goto`, `follow`, `explore`, `elytra`, `come`, `tunnel`, `surface`, `thisway`, `waypoints`, `find`, `pickup`, `click`, `axis`, `goal`, `path`, `proc`, `set`, `invert`, `render`, `repack`, `saveall`, `reloadall`, `forcecancel`, `gc`, `eta`, `help`, `version`). Aliases exist in `DefaultCommands`; exact alias list was not decoded.

### f) How our code calls Baritone

Only **one** API access exists in the entire swarm source:

```java
// BotBrain.java:2788
if (baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
    currentEta = "Pathing";
} else {
    currentEta = "Waiting";
}
```

Everything else is chat:

- `sendBaritoneChat("#mine ...")` in `BotBrain` gathering paths.
- `sendBaritoneChat("#goto ...")` for return/staging/recovery.
- `sendBaritoneChat("#stop")` in `cancel`, `pause`, deposit transitions, and worker stop paths.
- `PathManagers.stop()` in `EnhancedSwarmWorker.java:187,1405,1413` (Meteor’s Baritone abstraction, not Baritone API).

There are no calls to `getBuilderProcess`, `getMineProcess`, `getFarmProcess`, `getCustomGoalProcess`, or `getElytraProcess` today. The `#mine` chat parser is doing all quantity/reach selection.

### g) Schematic formats

Format classes present in 26.3:

- `baritone.utils.schematic.format.DefaultSchematicFormats` (enum implementing `ISchematicFormat`; methods `isFileType(File)`, `getFileExtensions()`, `values()`, `valueOf(String)`).
- `baritone.utils.schematic.format.defaults.LitematicaSchematic`
- `baritone.utils.schematic.format.defaults.MCEditSchematic`
- `baritone.utils.schematic.format.defaults.SpongeSchematic`
- `baritone.utils.schematic.SchematicSystem`
- `baritone.command.defaults.SchematicaCommand`, `LitematicaCommand`

Yes: Litematica, MCEdit, and Sponge readers exist, so `.litematic` and `.schem`/`.schematic` are supported in principle. The enum’s extension table is inside obfuscated nested classes and was not fully decoded. `buildOpenLitematic(int)` exists specifically for an already-open Litematica placement; `build(String, File, Vec3i)` is the stable path for a file.

UNKNOWN: exact `DefaultSchematicFormats` extension mapping (`.litematic` vs `.schem` vs `.schematic`) was not decoded from the obfuscated enum; the classes and format readers exist. Exact command aliases and all default setting values were not extracted. No in-game call to the builder was made, so `getApproxPlaceable()` behavior and schematic material behavior remain unverified.

## Part 5 — Storage / economy

### a) Public signatures

`StorageIndex` (`StorageIndex.java:13`; `ChestRecord` is a nested class, not a separate file):

```text
enum ChestStatus { FRESH, STALE, UNREACHABLE, MISSING }
static class ChestRecord {
    public int x, y, z;
    public String category;
    public Map<String,Long> counts;
    public long lastScan;
    public ChestStatus status;
    public int usedSlots, totalSlots;
    public double fullness;
}
StorageIndex()
synchronized void applyChestResult(String server, String workerName, String category, int x, int y, int z, ChestRecord rec)
synchronized Map<String,Long> categoryTotals(String server, String category)
synchronized List<ChestRecord> getChests(String server, String category)
synchronized void save()
synchronized void load()
synchronized long getTotal(String server, String itemId)
synchronized Set<String> getAllItems(String server)
static String normalizeId(String id)
synchronized void debugPrint(String server)
```

`DepositConfig`:

```text
static boolean matchesCategory(String category, ItemStack stack)
static boolean isShulkerBox(ItemStack stack)
static boolean matchesCategory(String category, String itemId)
static List<String> getCategoryNames()
Map<String,List<int[]>> getChests(String serverAddress)
void setBase(String, int, int, int)
int[] getBase(String)
boolean hasBase(String)
void removeBase(String)
void addChest(String, String, int, int, int)
void setChest(String, String, int, int, int)
void removeChest(String, String)
void removeChest(String, String, int)
int[] getChest(String, String)
boolean hasChest(String, String)
void save()
void load()
```

`WaypointManager`:

```text
static String getCategoryForResource(String)
static boolean isNetherCategory(String)
String getCurrentServer()
void load(String)
void save(String)
void ensureLoaded()
void addWaypoint(String, String, int, int, int, boolean)
void addPortalWaypoint(String, int, int, int, boolean)
boolean removeWaypoint(String)
SwarmWaypoint getWaypoint(String)
List<SwarmWaypoint> getWaypointsByCategory(String)
SwarmWaypoint getFirstPortal()
Collection<SwarmWaypoint> getAllWaypoints()
SwarmWaypoint findWaypointForResource(String, boolean)
```

`SmeltingManager`: see (f).

### b) Chest/container interaction code

There is **no reusable “open container at pos, run action, close” helper**. Each path repeats the sequence:

1. `findChestStandPos(BlockPos)` and `#goto` the stand position.
2. `mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(...))`.
3. Poll `mc.player.containerMenu != mc.player.inventoryMenu`.
4. Move items with `InvUtils.shiftClick().slotId(si)` or `mc.gameMode.handleContainerInput(..., ContainerInput.QUICK_MOVE/PICKUP, ...)`.
5. Close with `mc.setScreenAndShow(null)`.

Concrete sites:

- Deposit: `EnhancedSwarmWorker.performDepositToChest` at `754`; open at `809-815`; transfer worker at `825+`; retries/chest-full logic in the same method.
- Withdraw: `EnhancedSwarmWorker.handleWithdraw` at `1214`; path/open at `1237-1273`; shift-click transfer at `1285-1299`; excess return at `1301-1329`; close at `1335`; result packet at `1339`.
- BotBrain chest deposit: `handleDepositing` and `performDepositToChest` call path around `BotBrain.java:754+`/`1597-1735`; crafting table open/click around `1829-1940`; furnace paths `handleSmeltingDeposit/Wait/Collect`.
- `SmeltingManager.openFurnace`/`closeFurnace` is the only furnace-specific open/close helper; it is not generic.

The worker has `findChestStandPos` and a menu-provider validation step, but no generic callback helper. Any new “block getter” or sorter should introduce one; otherwise it will copy the third open/close sequence.

### c) `WITHDRAW_REQUEST` end to end

Host handler: `EnhancedSwarmConnection.java:421-495`.

```text
FROM WORKER: WITHDRAW_REQUEST:<itemId>:<needed>:<category>
host.getStorageIndex().getChests(server, category)
for each chest:
  available = r.counts.getOrDefault(itemId, 0)
  take = min(available, remaining)
  send "withdraw <category> <x> <y> <z> <itemId> <take>"
```

Worker receiver: `EnhancedSwarmWorker.handleWithdraw` (`1214-1344`) parses `<category> <x> <y> <z> <itemId> <count>`, paths to the chest, opens it, shift-clicks matching item stacks until `transferred >= needed`, returns excess, closes, and sends `WITHDRAW_RESULT:<itemId>:<x>:<y>:<z>:<transferred>`.

**The current bot does not use this end-to-end path.** Tree search for `WITHDRAW_REQUEST:` finds only:

- the host handler at `EnhancedSwarmConnection.java:421-492`;
- comments in `BotBrain.java:576,775,813` saying “do not send WITHDRAW_REQUEST here; rely on cache”.

Instead, `BotBrain.handleWithdrawing` (`1119-1208`) selects the **first** chest in `worker.depositChests` (category `"all"` if present), builds `category x y z item qty`, and invokes private `handleWithdraw` by reflection (`1189-1195`). It polls `CraftingManager.countInInventory` and times out after 5 minutes. It does not select a chest by actual contents and does not use `StorageIndex` to choose among chests.

Answer: the worker **can** pull a specific item+count from a known chest via `handleWithdraw`, but there is no active storage-index request path today. It cannot reliably pull “from any indexed chest” because the BotBrain path picks the first chest, and the multi-chest host handler has no sender. A failed first chest yields zero items and eventually `STUCK`.

### d) Shulker handling / ender chests

Shulkers are only a **deposit category**, not a logistics system:

- `DepositConfig.CATEGORIES` has `shulkers` and `empty_shulkers`, both `__CONTAINS:shulker_box` (`DepositConfig.java:50-51`).
- `matchesCategory` distinguishes empty vs non-empty via `DataComponents.CONTAINER` and `isEmptyShulker` (`100-136`).
- `isShulkerBox` is used by `shouldKeep` to force shulkers into deposit; `KEEP_IDS` never keeps them.
- The panel has a `Dep. Shulkers` action (`/api/action/deposit_shulkers`) and the GUI has the same button.

**Not found:** opening a shulker, inserting items into a shulker, removing items from a shulker, placing a shulker, breaking a placed shulker, or any ender-chest logic. No `ENDER_CHEST`/`ender_chest` code exists in `com.rdh.swarm`. A shulker is treated as an opaque stack for category routing and deposit only.

### e) Inventory-wide / storage-wide counts

- `CraftingManager.countInInventory(Item)` (`CraftingManager.java:49`) counts the player inventory (main inventory + hotbar; armor/offhand behavior is in its loop).
- `StorageIndex.getTotal(String server, String itemId)` (`145`) totals indexed storage for one item.
- `StorageIndex.categoryTotals(String server, String category)` (`48`) totals all items in a category.
- `StorageIndex.getAllItems(String server)` (`159`) lists indexed item ids.
- `StorageIndex.normalizeId(String)` (`173`) normalizes ids.
- There is no single helper that adds inventory + storage; callers combine `CraftingManager.countInInventory` and `StorageIndex.getTotal` manually.

### f) Furnace handling

`SmeltingManager` public API:

```text
static boolean isFurnaceOpen()
static boolean openFurnace(BlockPos)
static boolean depositOre(Item)
static boolean depositFuel()
static boolean takeResult()
static boolean hasFuel()
static int countFuel()
static void closeFurnace()
static boolean hasInput()
static int getOutputCount()
```

BotBrain states `SMELTING_DEPOSIT`, `SMELTING_WAIT`, `SMELTING_COLLECT` call these helpers. `AutoSmelter` is a separate Meteor module and is not used by `SmeltingManager`; the two do not share state or item configuration.

UNKNOWN: no test verified chest-menu slot layouts or shulker component behavior in 26.3. `ChestRecord` is nested; code that imports a top-level `ChestRecord` would not compile. Exact `handleDeposit` line range was not re-read, though the deposit chest method and transfer path above are verified.

## Part 6 — Names / registries

### a) Current suggestions and validation

`SwarmCommand` (`commands/SwarmCommand.java`):

- `TARGET_SUGGESTIONS` (`42-51`) suggests `all` plus `host.getWorkerNames()`.
- `CATEGORY_SUGGESTIONS` (`53-56`) suggests `DepositConfig.getCategoryNames()`.
- `COMMAND_SUGGESTIONS` (`58+`) suggests registered command names; for `mine` it has a hardcoded block list (`66-71`):

```java
if (first.equalsIgnoreCase("mine")) {
    String[] commonBlocks = {"diamond_ore", "iron_ore", "coal_ore", "oak_log", "stone", "dirt", "gravel", "sand"};
    for (String b : commonBlocks) {
        if (secondPartial.isEmpty() || b.startsWith(secondPartial)) builder.suggest("mine " + b);
    }
    return CompletableFuture.completedFuture(builder.build());
}
```

- The task item argument has **no suggestion provider**:

```java
// SwarmCommand.java:174
RequiredArgumentBuilder<ClientSuggestionProvider, String> itemArg = argument("item", StringArgumentType.word());
```

- Parsing/validation is registry lookup by `Identifier`, and failures are reported as “Invalid item” (`184-189`):

```java
Identifier id = Identifier.tryParse(item);
net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.getValue(id);
BotTask t = new BotTask(BotTask.Type.GATHER, it, qty);
```

- `task add` always creates `GATHER`; it does not infer `CRAFT` or `SMELT`.
- The web item field is free text; `/api/task/add` uses:

```java
BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", item))
```

(`SwarmWebServer.java:250`). It has no namespace input and no registry validation message.

### b) All-item registry / 26.3 names

Verified with `javap` on the merged 26.3 jar:

```text
net.minecraft.core.registries.BuiltInRegistries.ITEM  : DefaultedRegistry<Item>
net.minecraft.core.registries.BuiltInRegistries.BLOCK : DefaultedRegistry<Block>
net.minecraft.resources.Identifier.tryParse(String)
net.minecraft.resources.Identifier.fromNamespaceAndPath(String, String)
net.minecraft.resources.Identifier.parse(String)
```

`BuiltInRegistries.ITEM.keySet()` is the natural all-item source (or `getAllRecipes` for craftables). There is **no existing item registry dump** in the addon. Natural insertion points, if this were being implemented:

1. A `SuggestionProvider` on the existing `itemArg` at `SwarmCommand.java:174`; or
2. A new `GET /api/items` route in `SwarmWebServer`, returning `BuiltInRegistries.ITEM.keySet()` as JSON.

Do not hardcode a second list; the existing `mine` list is only eight blocks and will drift.

### c) Web panel item data

The panel receives no item registry data. The task item input is free text, and the server resolves it with `fromNamespaceAndPath("minecraft", item)`. There is no `<datalist>`, autocomplete array, `/api/items`, or registry-derived option list. Invalid item names create a `BotTask` with `targetItem == null`; `getItemName()` then returns `""`, and `BotBrain` can set `STUCK`/idle depending on the path.

UNKNOWN: `BuiltInRegistries.ITEM.keySet()` was not runtime-tested, and no 26.3 client was launched to confirm item ids. `Identifier.tryParse` behavior for namespaced vs un-namespaced input was verified only by signature, not by executing a client command.

## Part 7 — Web panel

### a) Dispatch, static content, WebSocket state

`SwarmWebHandler` receives Netty messages (`SwarmWebHandler.java:21-28`) and splits HTTP from WebSocket. HTTP auth is first (`30-34`), then the raw routing block (`36-72`):

```java
String uri = req.uri().split("\?")[0];

if (uri.equals("/ws")) {
    WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
        "ws://" + req.headers().get(HttpHeaderNames.HOST) + "/ws", null, true);
    handshaker = factory.newHandshaker(req);
    if (handshaker == null) {
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
    } else {
        handshaker.handshake(ctx.channel(), req).addListener(future -> {
            if (future.isSuccess()) server.addClient(ctx.channel());
        });
    }
    return;
}

if (req.method() == HttpMethod.POST) {
    String body = req.content().toString(CharsetUtil.UTF_8);
    server.handleApiCommand(uri, body);
    sendJson(ctx, "{\"ok\":true}");
    return;
}

if (req.method() == HttpMethod.GET && uri.equals("/")) {
    sendHtml(ctx, SwarmWebPanel.getHtml());
    return;
}
sendResponse(ctx, NOT_FOUND, "text/plain", "Not found");
```

`SwarmWebServer.handleApiCommand` (`237-383`) is a switch on the raw path. Unknown paths fall through silently; every POST still receives HTTP 200 `{"ok":true}` from the handler, even if the body is malformed or the command throws (the server catches and logs). There is no method/content-type check beyond the POST branch.

Static content is **not** served from `src/main/resources`. `GET /` calls `SwarmWebPanel.getHtml()`, which returns a single Java text block containing CSS + markup + JS (`SwarmWebPanel.java:5-550`). All other GETs are 404.

WebSocket:

- Handshake adds the channel to `wsClients`; `addClient` immediately sends state and log history (`SwarmWebServer.java:116-122`).
- A scheduled task broadcasts state every 2 seconds (`99-100`), and every API command calls `broadcastState()` at the end (`381-382`).
- Text frames from the browser are routed back into `handleApiCommand(msg, msg)` (`SwarmWebHandler.java:81-84`), so the first token of a WS text message is treated as a path. There is no separate WS action protocol.
- All clients receive the same state; there is no per-user scoping.

State JSON, keys only (`buildStateJson`, `157-223`):

```json
{"type":"state","connected":1,
 "bots":[{"username":"Worker1","nickname":"Miner","online":true,"health":20.0,"food":20,"x":0,"y":64,"z":0,"task":"Mining iron","progress":"3/64","eta":"2m","tier":"Iron","state":"GATHERING"}],
 "tasks":[{"id":"<uuid>","item":"iron_ore","quantity":64,"progress":"3/64","status":"ACTIVE","bot":"Worker1"}]}
```

Offline bots use `online:false`, zero health/food/coords, `task:"Offline"`, `state:"OFFLINE"` (`193-198`). Empty host returns `{"type":"state","connected":0,"bots":[],"tasks":[]}` (`160`).

### b) POST endpoints and required fields

There are exactly 16 cases in `handleApiCommand`:

| # | Path | Body fields used |
|---|---|---|
| 1 | `/api/task/add` | `item` (string, no namespace), `qty` (int), `bot` (string), optional `type` (`GATHER`/`CRAFT`/`SMELT`/...); invalid type falls through |
| 2 | `/api/task/clear_done` | none |
| 3 | `/api/task/remove` | `id` (UUID string) |
| 4 | `/api/task/pause` | `bot` |
| 5 | `/api/task/resume` | `bot` |
| 6 | `/api/action/follow` | none; follows the host player’s current name |
| 7 | `/api/action/stop` | none |
| 8 | `/api/action/deposit` | none |
| 9 | `/api/action/deposit_ores` | none |
| 10 | `/api/action/deposit_shulkers` | none |
| 11 | `/api/action/pvp_on` | none; sends 7 `exec .toggle ... on` commands |
| 12 | `/api/action/pvp_off` | none; sends the same 7 `off` |
| 13 | `/api/action/scan_chests` | none; **currently sends `deposit list`, not a scan** |
| 14 | `/api/action/check_fuel` | none; **currently sends `exec #stop`, not a fuel check** |
| 15 | `/api/command` | `target`, `cmd`; target `all` fans out per worker |
| 16 | `/api/nickname` | `username`, `nickname` |

PVP module ids are hardcoded: `crystal-aura`, `kill-aura`, `auto-totem`, `surround`, `auto-trap`, `auto-web`, `auto-exp` (`336-345`). `/api/action/scan_chests` and `check_fuel` are misleading and do not do what their names say.

### c) Embedded HTML ranges

`SwarmWebPanel.java`:

- `5-11`: text-block opener/doctype/head start.
- `12-250`: `<style>` CSS.
- `252-337`: static `<body>` markup/buttons/forms.
- `339-547`: `<script>` JS (fetch POSTs, WS connect/reconnect, state render, logs).
- `548-549`: `</body></html>`.
- `550`: text-block close/`getHtml()` return.

There are no external `.css`, `.js`, image, or font resources. All styling is inline; the page is one Java string. Changing markup means editing a Java text block, not a resource file.

### d) Netty capabilities / classpath resources

Pipeline (`SwarmWebServer.java:88-93`):

```java
p.addLast(new HttpServerCodec());
p.addLast(new HttpObjectAggregator(65536));
p.addLast(new WebSocketServerCompressionHandler());
p.addLast(new SwarmWebHandler(SwarmWebServer.this));
```

`HttpObjectAggregator` is present, so a single `FullHttpRequest` is available up to 64 KiB. There is **no** `ChunkedWriteHandler`, no static-file handler, no MIME registry, no range support, and no classpath `ClassLoader.getResource` path. Serving `src/main/resources/web/` would require a new route/handler (plus packaging resources and content types); it is not a drop-in change. Large or streamed assets would also need chunked handling. For the current single 550-line HTML block, 64 KiB is enough, but the page is already close enough to the limit that adding a large JS bundle would be risky.

### e) Per-worker data in the UI

`buildStateJson` exposes exactly:

`username`, `nickname`, `online`, `health`, `food`, `x`, `y`, `z`, `task`, `progress`, `eta`, `tier`, `state`.

Task rows expose `id`, `item`, `quantity`, `progress`, `status`, `bot`. Logs are a separate `{"type":"log","time","level","msg"}` frame plus `log_history`. The UI does not show inventory contents, armor, offhand, storage totals, chest coordinates, module states, or per-worker settings.

UNKNOWN: no browser was opened; HTML/JS rendering and WS reconnect were not runtime-tested. `HttpObjectAggregator` limits and behavior for >64 KiB bodies were not exercised. The WS text-frame path’s practical exploitability was not tested; it is visible in code.

## Part 8 — Task engine

### a) BotTask fields, enums, TaskQueue, assignment

`BotTask` (`task/BotTask.java`):

```java
enum Type { GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS }
enum Status { PENDING, ACTIVE, BLOCKED, COMPLETE, FAILED }

UUID id;
Type type;
int priority;
Map<String,Object> data;
Instant createdAt;
Item targetItem;
Item sourceItem;
int targetQuantity;
int collectedQuantity;
Status status;
String assignedBot;
```

Constructors: `BotTask(UUID, Type, int, Map)` and `BotTask(Type, Item, int)`; the convenience constructor sets priority 0 and a random UUID. `getRemaining()` is `max(0, target - collected)`. `getItemName()` returns the registry path only (no namespace). There is no type-specific state or schema in `data` beyond `item`, `quantity`, `source`.

`TaskQueue` public API (`task/TaskQueue.java`):

```text
TaskQueue()
synchronized void add(BotTask)
synchronized BotTask poll()
synchronized BotTask peek()
synchronized int size()
synchronized void clear()
synchronized boolean removeById(UUID)
synchronized List<BotTask> toList()
String getNickname(String)
void setNickname(String, String)
synchronized List<BotTask> getAllTasks()
synchronized Map<String,String> getAllNicknames()
synchronized void addTask(BotTask)      // alias for add(); saves once
synchronized void save()
synchronized void load()
synchronized void clearStale()
synchronized void clearCompleted()
synchronized BotTask getNextPendingForBot(String)
synchronized boolean hasActiveTask(String)
```

Persistence is JSON at `meteor-client/enhanced-swarm/task-queue.json`. `add()` saves immediately; `addTask()` delegates to `add()` (comment explicitly says do not save twice). `load()` drops `COMPLETE`/`FAILED` tasks and calls `addTask()` per surviving task, so each task is re-saved during load.

Assignment path:

1. Host `dispatchNextTask(String botName)` (`EnhancedSwarmHost.java:360-384`) refuses if `hasActiveTask`, gets `getNextPendingForBot`, assigns the bot if empty/any/all, sets `ACTIVE`, sends:
   `task assign <uuid>|<TYPE>|<item>|<qty>|<bot>`.
2. Worker `handlePayload` parses `task assign` at `EnhancedSwarmWorker.java:331-369`, reconstructs `BotTask` via `Type.valueOf`, resolves `BuiltInRegistries.ITEM`, and calls `BotBrain.setTask(task)`.
3. `BotBrain.setTask` (`2346-2391`) queues if currently depositing, rejects a new task if an `ACTIVE` one exists, sets status `ACTIVE`, resolves dependencies, and stores the plan.
4. `handleIdle` (`394-449`) builds/advances the plan and switches on `step.getType()`. Actual work starts in `GATHERING`, `CRAFTING_PREP`, `SMELTING_DEPOSIT`, etc.

### b) RecipeBook registered outputs

`RecipeBook` has 58 `register(...)` calls from line 39 to 338. Grouped by output:

- Wood: `OAK_PLANKS`, `STICK`, `CRAFTING_TABLE`, `WOODEN_PICKAXE`, `WOODEN_AXE`, `WOODEN_SWORD`, `WOODEN_SHOVEL`, `WOODEN_HOE`.
- Stone/utility: `STONE_PICKAXE`, `STONE_AXE`, `STONE_SWORD`, `STONE_SHOVEL`, `STONE_HOE`, `FURNACE`, `CHEST`.
- Iron: `IRON_PICKAXE`, `IRON_AXE`, `IRON_SWORD`, `IRON_SHOVEL`, `IRON_HOE`, `IRON_HELMET`, `IRON_CHESTPLATE`, `IRON_LEGGINGS`, `IRON_BOOTS`, `SHIELD`, `BUCKET`.
- Diamond: `DIAMOND_PICKAXE`, `DIAMOND_AXE`, `DIAMOND_SWORD`, `DIAMOND_SHOVEL`, `DIAMOND_HOE`, `DIAMOND_HELMET`, `DIAMOND_CHESTPLATE`, `DIAMOND_LEGGINGS`, `DIAMOND_BOOTS`.
- Golden: `GOLDEN_PICKAXE`, `GOLDEN_AXE`, `GOLDEN_SWORD`, `GOLDEN_SHOVEL`, `GOLDEN_HOE`, `GOLDEN_HELMET`, `GOLDEN_CHESTPLATE`, `GOLDEN_LEGGINGS`, `GOLDEN_BOOTS`.
- Leather: `LEATHER_HELMET`, `LEATHER_CHESTPLATE`, `LEATHER_LEGGINGS`, `LEATHER_BOOTS`.
- Utility/food: `TORCH`, `BOW`, `ARROW`, `LADDER`, `OAK_BOAT`, `OAK_DOOR`, `BED.white()`, `BREAD`, `PAPER`, `HOPPER`.

Explicit gap check:

| Requested item/group | Status |
|---|---|
| Netherite gear | **missing** (no netherite recipes) |
| Smithing table | **missing** |
| Enchanting table | **missing** |
| Anvil | **missing** |
| Blast furnace | **missing** |
| Bed | **present**, white bed only (`BED.white()`) |
| Shield | **present** |
| Bucket | **present** |
| Flint and steel | **missing** |
| Bow / arrow | **present** |
| Crossbow | **missing** |
| Fishing rod | **missing** |
| Ender chest | **missing** |
| Shulker box | **missing** |
| Hopper | **present** |
| Chest | **present** |
| Barrel | **missing** |

Other notable absences: `SMITHING_TABLE` means no netherite upgrade path at all; no `ENCHANTING_TABLE` means AutoMend/XP enchanting cannot be produced; no `BLAST_FURNACE` means smelting speed is basic-furnace only; no `BARREL` means storage uses chests only.

### c) Where a new `BotTask.Type` plugs in

`BotTask.Type` is already `GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS`, but only a subset is handled. New type dispatch sites:

- `task/BotTask.java:11` — add the enum constant.
- `modules/BotBrain.java:79-93` — add a `BrainState` if the type needs a new state.
- `modules/BotBrain.java:373-387` — add the state case to the main `onTick` switch.
- `modules/BotBrain.java:435-449` — **the critical switch** in `handleIdle`; `default -> setState(GATHERING)` currently swallows unknown/`EQUIP`/`DEPOSIT`/`SCAN_CHESTS` types.
- `modules/BotBrain.java:493-500` — `handleGathering` has a special `Type.GATHER` branch; other types need their own handlers/methods.
- `modules/BotBrain.java:2346-2391` — `setTask` dependency resolution assumes `targetItem`/`quantity`; a new type without these will produce a null plan or empty step.
- `EnhancedSwarmWorker.java:351-352` — generic `Type.valueOf`; new type works without a worker edit, but `task.getItemName()` must be meaningful.
- `SwarmWebServer.java:258-260` — generic `Type.valueOf`; new type works if the request supplies it.
- `TaskQueue.java:103` and `148` — generic `name()` / `valueOf` persistence; new type works but old JSON cannot contain it.
- `EnhancedSwarmHost.java:379-380` — generic `next.getType().name()` in the assignment payload; no edit needed.
- `commands/SwarmCommand.java:186` — CLI `task add` hardcodes `BotTask.Type.GATHER`; it cannot create a new type without a type argument.
- `task/DependencyResolver.java` — new types should be emitted here if they are prerequisites; currently emits `CRAFT`, `SMELT`, `GATHER`.

`EQUIP` is the biggest existing hole: it is declared and even produced conceptually by equipment planning, but there is no `case EQUIP` and no armor/offhand swap method. `DEPOSIT` and `SCAN_CHESTS` are also declared but not handled by `handleIdle`; they fall through to `GATHERING`. Any new type needs a real branch before the default, not just an enum entry.

UNKNOWN: no compile was run, so the exact compile errors for adding a type are not enumerated; the switch/default sites above are from source inspection. `RecipeBook` output count (58) is the number of `register(Items...)` call sites, not necessarily 58 unique recipes if duplicates exist; the list above is de-duplicated by item.

## FINAL SECTION — SUMMARY

### 1. Meteor modules that exist on 26.3 and I’d keep always-on during BotBrain

Use the exact classes, with these settings:

- `meteordevelopment.meteorclient.systems.modules.player.AutoEat` — `pause-baritone=true`, `pause-auras=false`, `threshold-mode=Any`, `health-threshold=10.0`, `hunger-threshold=16`, `food-priority=Saturation`, `search-inventory=true` (if the worker carries food outside the hotbar).
- `meteordevelopment.meteorclient.systems.modules.combat.AutoArmor` — `preferred-protection=Protection`, `swap-delay=1`, `ignore-elytra=true`, `blast-prot-leggings=true`, `anti-break=true`.
- `meteordevelopment.meteorclient.systems.modules.combat.AutoTotem` — `mode=Smart`, `delay=0`, `health=10`, `elytra=true`, `fall=true`, `explosion=true`.
- `meteordevelopment.meteorclient.systems.modules.player.AutoTool` — `prefer=Fortune`, `silk-touch-for-ender-chest=true`, `anti-break=true`, `switch-back=true`, `switch-delay=0`.
- `meteordevelopment.meteorclient.systems.modules.player.AutoReplenish` — `min-count=8`, `delay=1`, `offhand=true`, `unstackable=true`, `same-enchants=true`, `search-hotbar=false`.
- `meteordevelopment.meteorclient.systems.modules.movement.NoFall` — `mode=Packet`, `reset-on-disable=true`, `air-place-mode=BeforeDeath`, `rotate=true`.
- `meteordevelopment.meteorclient.systems.modules.player.AntiHunger` — `sprint=true`, `on-ground=true`.
- `meteordevelopment.meteorclient.systems.modules.player.AutoRespawn` — toggle only; no settings.

Practical exclusions: do **not** run `Offhand` alongside `AutoTotem` (both claim the offhand), do not run `KillAura` during gathering (it pauses Baritone by default and steals rotations), and do not run `AutoSmelter` while BotBrain owns a furnace (two controllers, one menu). `AutoFish`, `AutoBreed`, and `AutoShearer` are optional task-specific modules, not always-on.

### 2. Baritone features we can call directly instead of building ourselves

- Mining quantity: `IBaritone.getMineProcess().mineByName(int, String...)`; cancel with `IMineProcess.cancel()`.
- Farming: `IBaritone.getFarmProcess().farm()`, `.farm(int)`, `.farm(int, BlockPos)`.
- Schematic building: `IBaritone.getBuilderProcess().build(String, File, Vec3i)`, `.buildOpenSchematic()`, `.buildOpenLitematic(int)`, `.pause()`, `.resume()`, `.clearArea(...)`.
- Movement/exploration: use the registered commands `#goto`, `#follow`, `#explore`, `#thisway`, `#come`, `#elytra`; the corresponding process getters exist (`getCustomGoalProcess`, `getFollowProcess`, `getExploreProcess`, `getElytraProcess`), but their individual method signatures were not audited here.
- Commands for operations: `#mine`, `#farm`, `#build`, `#sel`, `#schematica`, `#litematica`.
- `getApproxPlaceable()` is not a material-deficit API; there is no `getMissing` on `IBuilderProcess`. Compute missing materials ourselves.

### 3. Gaps neither Meteor nor Baritone fills

- Food sourcing: no hunting, cooking, or eating loop in BotBrain; `AutoEat` only consumes food that already exists.
- Equipment validation: tier is pickaxe-only; no armor/offhand slot check, no “wear the best piece” task, and `EQUIP` has no handler.
- Netherite/smithing: no smithing table, netherite recipes, or smithing task.
- Shulker/ender logistics: no fill, empty, place, break, or ender-chest access; shulkers are deposit categories only.
- Generic container helper: chest/crafting/furnace open-act-close is duplicated; no reusable “container at pos” abstraction.
- Storage sorting/block getting: `StorageIndex` indexes but does not sort or fetch; no block-breaker integration with `#mine` beyond chat.
- Item registry UX: no all-item suggestion provider or `/api/items`; CLI/web item entry is free text.
- Death recovery: `DeathTracker.recordDeath` has no caller, so the recovery state is dead.
- Task engine: `DEPOSIT` and `SCAN_CHESTS` are declared but unhandled; new types need real branches.
- Web truthfulness: “scan chests” and “check fuel” do not perform those actions; POST always reports `{"ok":true}`.

### 4. Risks

- **Dead recovery:** `DeathTracker.hasDeath()` is always false because `recordDeath` is never called. The `RECOVERING` code is unreachable.
- **Broken withdraw selection:** BotBrain’s reflection path uses the first deposit chest, not the chest containing the item; the proper multi-chest `WITHDRAW_REQUEST` host handler has no sender. Failures time out to `STUCK`.
- **Misleading web controls:** `/api/action/scan_chests` sends `deposit list`; `/api/action/check_fuel` sends `exec #stop`. `/api/command` and `/api/pvp_*` can execute arbitrary Meteor commands on workers.
- **Unsafe HTTP semantics:** every POST returns 200 `{"ok":true}` regardless of malformed body or command failure; WS text frames are treated as paths. Remote access is protected only by the token header; loopback is always allowed.
- **Deactivation leaves Baritone running:** `BotBrain.onDeactivate()` clears task state but does not `#stop` or `PathManagers.stop()`. A guard must stop pathing explicitly.
- **Module/Baritone conflicts:** `AutoEat.pause-baritone` and `KillAura.pause-baritone` default true; they will pause BotBrain’s path. `AutoTool`/`AutoArmor` can swap items while BotBrain is mid-container or mid-craft.
- **Hardcoded registries:** `KEEP_IDS`, `CATEGORIES`, the eight-block `mine` suggestion list, and RecipeBook’s 58 outputs will drift from 26.3 content. Netherite, smithing, enchanting, anvil, blast furnace, barrel, crossbow, fishing rod, ender chest, shulker box, and flint-and-steel are absent from RecipeBook.
- **No compile/test was run** for this report (recon-only). The only Baritone API call is guarded by `Exception | NoClassDefFoundError`, but chat `#...` calls are not guarded.
- **Web payload headroom:** `HttpObjectAggregator` is 64 KiB and the inline panel is already ~550 lines; a large JS addition may exceed the limit. No static resource handler exists.
- **Task type trap:** `handleIdle`’s `default -> GATHERING` silently routes `EQUIP`, `DEPOSIT`, and `SCAN_CHESTS` to gathering. Adding an enum constant alone is not enough.

### 5. File lengths (current, before any port changes)

Top five files we’d touch:

| File | Lines |
|---|---:|
| `src/main/java/com/rdh/swarm/modules/BotBrain.java` | 3,031 |
| `src/main/java/com/rdh/swarm/EnhancedSwarmWorker.java` | 1,421 |
| `src/main/java/com/rdh/swarm/EnhancedSwarm.java` | 844 |
| `src/main/java/com/rdh/swarm/commands/SwarmCommand.java` | 694 |
| `src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java` | 604 |

Next: `web/SwarmWebPanel.java` 552, `task/RecipeBook.java` 492, `web/SwarmWebServer.java` 404, `task/TaskQueue.java` 233, `task/BotTask.java` 102. Ten-file total above: 8,003 lines.

UNKNOWN: no 26.3 client was launched, no build/Gradle task was run, no web page was rendered, and no Baritone builder/mine/farm call was executed. Defaults that were not safely decoded are marked “not decoded” in Parts 1 and 4.
