# 00 — Index, Inventory, Versions

Generated from read-only inspection (no Gradle run). Root: `C:\Projects\swarm\swarm-improved`.

## Totals
- **80 `.java` files**, **13,758 lines** under `src/main/java/com/rdh/swarm`.
- 44 of them are in `modules/`; `BotBrain` alone is 3,038 lines (22% of the codebase).
- Shell note: bash host, all commands run as `powershell.exe -NoProfile -Command ...` with `-LiteralPath`.

## Inventory (lines per file)
```
BotStatus 52            BuildInfo 40            DepositConfig 296        EnhancedSwarm 819
EnhancedSwarmAddon 101  EnhancedSwarmConnection 589  EnhancedSwarmHost 378  EnhancedSwarmWorker 1414
StorageIndex 203        SwarmHudPresets 25      SwarmWaypoint 87         WaypointManager 289
WorkerConfig 51         WorkerStatus 58         commands/SwarmCommand 694
mixin/ChatUtilsMixin 18
task/BaseMap 36         task/BotTask 102        task/CraftingManager 214 task/DeathTracker 85
task/DependencyResolver 285  task/RecipeBook 492  task/SmeltingManager 135  task/TaskQueue 232
timers/TimerList 69
utils/BODamageUtils 33  utils/BOInvUtils 49     utils/EpicInterface 6    utils/ExtrapolationUtils 132
utils/OLEPOSSUtils 101  utils/PlacementHelper 173  utils/RotationUtils 60  utils/TimerList 69
web/SwarmWebHandler 113 web/SwarmWebPanel 552  web/SwarmWebServer 379
modules/BotBrain 3038   modules/SwarmAirCrystal 410   modules/SwarmAnchorAura 46
modules/SwarmAntiHunger 15  modules/SwarmAntiVoid 21   modules/SwarmArrowDodge 29
modules/SwarmAutoArmor 28   modules/SwarmAutoCity 36   modules/SwarmAutoEat 31
modules/SwarmAutoExp 31     modules/SwarmAutoReplenish 26  modules/SwarmAutoRespawn 12
modules/SwarmAutoTool 34    modules/SwarmAutoTotem 29  modules/SwarmAutoTPA 145
modules/SwarmAutoTrap 47    modules/SwarmAutoWeb 36    modules/SwarmBedAura 43
modules/SwarmBowAimbot 27   modules/SwarmChestSwap 18  modules/SwarmCrystalAura 85
modules/SwarmElytraFly 64   modules/SwarmFastUse 21    modules/SwarmFlight 31
modules/SwarmHoleFiller 61  modules/SwarmJesus 30      modules/SwarmKillAura 55
modules/SwarmNoFall 29      modules/SwarmNoRotate 10   modules/SwarmNoSlow 33
modules/SwarmNuker 39       modules/SwarmOffhand 35    modules/SwarmPearlChase 198
modules/SwarmProxyModule 60 modules/SwarmReach 17     modules/SwarmSafeWalk 23
modules/SwarmScaffold 48    modules/SwarmSpam 212      modules/SwarmSpeed 28
modules/SwarmSpeedMine 27   modules/SwarmSprint 25     modules/SwarmStep 25
modules/SwarmSurround 55    modules/SwarmTimer 14
```

## Reference folder (vendored, do not mix with src)
`reference/BlackOut/` (full mod), `reference/meteor 1.21.4/` (Meteor source + `launch/`), `reference/starscript-master/`,
plus loose legacy copies: `Swarm.java`, `SwarmConnection.java`, `SwarmHost.java`, `SwarmWorker.java`, `InventoryTweaks.java`.
The only place `gradle.properties:minecraft_version` is read is `reference/meteor 1.21.4/build.gradle.kts`.

## Versions (source of truth)
`gradle/libs.versions.toml` → minecraft **1.21.11**, yarn **1.21.11+build.3**, fabric-loader **0.18.2**,
loom **1.14-SNAPSHOT**, meteor **1.21.11-SNAPSHOT**, mod-version **0.1.0**.
`gradle.properties` → `minecraft_version=1.21.4` (**dead for root build**), `maven_group=com.rdh.swarm`,
`archives_base_name=enhanced-swarm`, jvmargs `-Xmx2G`, configuration-cache off.
`fabric.mod.json` → id `enhanced-swarm`, entrypoint `com.rdh.swarm.EnhancedSwarmAddon`, `environment: client`,
`depends.minecraft: ["1.21.4"]` (hardcoded skew), java >=21, icon still `assets/template/icon.png`,
sources still the meteor-addon-template URL, `custom._fc0` watermark.
`build.gradle.kts` (94 lines) → deps: `minecraft(libs.minecraft)`, yarn v2 mappings, fabric-loader,
meteor-client; `compileOnly("meteordevelopment:baritone:1.21.10-SNAPSHOT")`; java 21 + release 21;
two absolute MultiMC mods folders copied on `build`; processResources expands `version` + unused `mc_version`.

## Addon registration (onInitialize, RAW order)
EnhancedSwarm, CrystalAura, KillAura, AutoTotem, Surround, AutoTrap, AutoWeb, AutoExp, Spam, AnchorAura,
BedAura, AutoCity, HoleFiller, Offhand, AutoArmor, BowAimbot, ArrowDodge, Speed, Flight, ElytraFly, NoFall,
NoSlow, Step, Scaffold, Sprint, SafeWalk, AntiVoid, AutoTool, AutoEat, AutoReplenish, Reach, FastUse,
AntiHunger, NoRotate, SpeedMine, Timer, Nuker, AutoRespawn, Jesus, ChestSwap, AutoTPA, AirCrystal,
PearlChase, BotBrain. Then `SwarmHudPresets.init()` (swallowed), then
`Commands.add(new SwarmCommand())` + `Commands.add(new SwarmCommand("swarm"))` (`/eswarm` + legacy `/swarm`).
No commented-out `Modules.get().add` lines exist — all 44 are live.

## Document map
01 core module/settings · 02 host · 03 connection · 04 worker · 05 task engine · 06 tiers/kits ·
07 module catalog · 08 survival loop · 09 economy · 10 utils/mixin · 11 commands · 12 web ·
13 protocol · 14 health · 15 data files · 16 roadmap · 29 reference corpus (capability map + retrieval index).
