# 14 — Dependencies & Health

## Meteor API surface actually used
Imports across `EnhancedSwarm*.java`, `task/*`, `web/*`, `commands/*`:
```
meteordevelopment.meteorclient.addons.{MeteorAddon,GithubRepo}
meteordevelopment.meteorclient.settings.{BoolSetting,IntSetting,DoubleSetting,StringSetting,EnumSetting,
    Setting,SettingGroup,ItemListSetting,BlockListSetting,EntityTypeListSetting,ModuleListSetting,
    EnchantmentListSetting,KeybindSetting,ColorSetting}
meteordevelopment.meteorclient.systems.modules.{Module,Modules,Category}
meteordevelopment.meteorclient.gui.{GuiTheme, widgets.*, widgets.containers.*, widgets.input.*, widgets.pressable.*}
meteordevelopment.meteorclient.utils.network.MeteorExecutor
meteordevelopment.meteorclient.utils.player.{ChatUtils,InvUtils}
meteordevelopment.meteorclient.pathing.PathManagers
meteordevelopment.meteorclient.events.game.{GameJoinedEvent,GameLeftEvent}
meteordevelopment.meteorclient.events.world.TickEvent
meteordevelopment.orbit.EventHandler
net.minecraft.* : client, screen(.slot), item, registry, util.math, util.hit, command.argument, entity.player
```
**No `import baritone.*`** anywhere, yet `BotBrain:2795` calls
`baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()` fully qualified.
`PathManagers` is used for `stop()` (worker disconnect) but pathing is driven by **chat commands** (`#mine`, `#goto`).
`compileOnly` baritone 1.21.10-SNAPSHOT matches that runtime assumption (Meteor ships Baritone).

## TODO/FIXME/XXX — all hits
Only one literal `XXX`, and it is not a TODO:
```
DepositConfig.java:25   // Built-in item categories - "__CONTAINS:xxx" = substring match on item ID
```
Near-misses worth knowing (intentional placeholders):
```
EnhancedSwarm.java:791   // Baritone number setting removed - placeholder row kept for layout
EnhancedSwarm.java:798   // Baritone double setting removed - placeholder row kept for layout
WaypointManager.java:279 // For now, return first matching waypoint
SwarmCommand.java:626    // For now just log - actual push to workers would serialize waypoints
DeathTracker.java:49     // no-op for now (placeholder for persistence)
PlacementHelper.java:164 // For now, call with dummy item ... (log warning)
```
Conclusion: no maintained TODO list — the backlog lives in these comments and in missing protocol verbs.

## `/* */` blocks longer than 5 lines (task/)
```
RecipeBook.java:356   /**  (tier definitions doc)
TaskQueue.java:197    /**  (getNextPendingForBot doc)
TaskQueue.java:218    /**  (hasActiveTask doc)
```
No commented-out code blocks in task/.

## Silent failure map (count of `catch (Exception|Throwable ignored)`)
```
68  EnhancedSwarmWorker     48  EnhancedSwarmConnection   37  BotBrain
33  EnhancedSwarm           13  EnhancedSwarmHost          7  BotStatus
 6  SwarmCommand             6  StorageIndex               5  TaskQueue
 5  SwarmWebServer           4  CraftingManager            2  DepositConfig
 1  SwarmSpam / SwarmAutoTPA / EnhancedSwarmAddon
```
Hot spots: the entire socket parse path, deposit/withdraw, proxy setting pushes, web start.

## Bugs / smells (consolidated)
1. **`EnhancedSwarmConnection.java:47`** — `logInfo("dY"- " + ...)`; string minus string. Compile blocker (not build-verified).
2. **PriorityQueue mis-dispatch** — `TaskQueue.getNextPendingForBot` iterates heap order, so priority is ignored; `poll()` is the only ordered path.
3. **Double save** — `addTask()` → `add()` (saves) → `save()` again; two JSON writes per enqueue.
4. **Bare vs prefixed payloads** — `dispatchNextTask`/`withdraw` omit `swarm:`; works only because the worker strips optionally.
5. **`/api/command target=all`** — sends `swarm:all <cmd>`, worker verb becomes `all` → misrouted.
6. **No auth, all interfaces** — 6969 (plaintext UTF protocol) and 8888 (HTTP/WS) accept anyone.
7. **Silent web failure** — `new SwarmWebServer(8888)` inside `catch (Throwable ignored)`.
8. **Dead sync protocol** — `sync-settings` and `SETTINGS:` remain but both parsers are no-ops.
9. **Duplicate status stream** — every `sendStatus()` emits `STATUS:` and `BOT_STATUS:` (two parsers, two code paths).
10. **Duplicate TimerList** — `utils/TimerList.java` and `timers/TimerList.java` differ (MD5 `2B24…` vs `C9A0…`); divergent copies.
11. **Version skew** — toml MC 1.21.11 / fabric.mod.json depends 1.21.4 / baritone 1.21.10.
12. **Dead `minecraft_version`** in gradle.properties for the root build (only the vendored meteor reference reads it).
13. **Absolute MultiMC paths** in `build.gradle.kts` (`C:/Users/rdh/Desktop/...`) — build fails or writes outside on other machines.
14. **Watermark boilerplate** (`BuildInfo.z()`, `_hb/_he`, `_lvc`, `_da`, `_ws0`, `_wa`, `custom._fc0`) — dead, duplicated ~9×.
15. **Mixed logging** — `System.out.println` debug in connection/worker/deposit/resolver instead of ChatUtils.
16. **`CraftingManager.placeCraftingTable`** uses reflection on `selectedSlot` — fragile across mappings/versions.
17. **`DeathTracker.load()`** is a no-op → death positions die with the process.
