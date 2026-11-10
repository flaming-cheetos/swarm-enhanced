# 19 — MEGA RUN SUMMARY (G1 → N)

- Repository: `C:\Projects\swarm\swarm-improved`
- Base: `port-26.3` @ `04f358c` (`ignore build logs`)
- Final branch: `feat/n-swarm`
- Build tool: `.\gradlew.bat build` with `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2`
- Final build: **BUILD SUCCESSFUL** (`compileJava` and `jar` ran; only pre-existing `BlockStateBase.isSolid()` deprecation warnings)
- Phase notes: `DEEPDIVE/20_PHASE_NOTES.md`
- No merge into `port-26.3`, no force-push, no history rewrite. The 37 proxy modules and the Rust/Horizon bridge were not touched.

## Phase table

| Phase | Branch | Tag | Commit | Status |
|---|---|---|---|---|
| G1 — survival | `feat/g1-survival` | `done-g1` | `f7c2abe` | DONE |
| G2 — food | `feat/g2-food` | `done-g2` | `647581e` | DONE |
| H — grinder | `feat/h-grinder` | `done-h` | `104efa9` | DONE |
| W — web | `feat/w-web` | `done-w` | `157ad28` | DONE |
| I — netherite | `feat/i-netherite` | `done-i` | `70eb2a9` | PARTIAL |
| K — logistics | `feat/k-logistics` | `done-k` | `56fafd7` | DONE |
| L — shulkers | `feat/l-shulkers` | `done-l` | `b026dde` | PARTIAL |
| M — builder | `feat/m-builder` | `done-m` | `344dd7f` | DONE (thin) |
| N — swarm | `feat/n-swarm` | `done-n` | `f2d1fd8` | DONE (local roles) |

## Delivered per phase

- **G1:** `ModuleGuard` snapshots/restores Meteor survival modules, applies settings through `Setting.parse`, re-enables guarded modules, suppresses `KillAura` during food hunts, and freezes timers while eating/Baritone-paused. BotBrain records one death position/dimension per death, refuses cross-dimension recovery, fails unsupported task types with one WARN, and stops Baritone on deactivation.
- **G2:** `FoodManager` counts, `COOKING`/`SAFE_RAW` maps, `HUNT_FOOD`/`HUNTING` with Baritone follow, attack and drop collection, auto-hunt low-water mark, zero-food interruption with resume, direct cooking tasks on the existing furnace states, and AutoEat raw-food fallback.
- **H:** `GRIND`/`GRINDING` sessions at configured coordinates, hostile targeting, drops, progress, auto-start, and a web task option.
- **W:** item-less `HUNT_FOOD`/`GRIND` tasks from the dashboard, null-safe `/api/task/add`, type labels, and a worker-side null-item NPE fix.
- **I:** `SmithingManager` slot handling and a `SMITH`/`SMITHING` flow for diamond→netherite when template, ingot, base item, and a smithing table are all available.
- **K:** `TaskQueue.requeueActiveForBot` plus host cleanup/shutdown hooks so a dead worker’s ACTIVE task returns to PENDING.
- **L:** `SHULKER`/`SHULKER` emptying flow: place a filled shulker, shift-click 27 slots into inventory, break it, collect the drop, with per-phase timeouts.
- **M:** `BUILD`/`BUILDING` using verified `IBuilderProcess.build(String, File, Vec3i)` and `isActive()`, with file/origin settings and a 30-minute timeout.
- **N:** `SwarmRole { GENERAL, MINER, GRINDER, FOOD }` and `swarm-role` gating for automatic food, grinder, smithing, shulker, and builder work.

## Limitations

- No in-game test was possible for any phase; verification is compile/build plus `javap` checks against the 26.3 merged jar and Meteor/Baritone jars.
- **I is PARTIAL:** execution only. Ancient-debris mining, netherite-ingot crafting, smithing-table crafting, and bastion template acquisition are not implemented.
- **L is PARTIAL:** emptying only. Filling/placing items into shulkers, colored-shulker block detection, and tool-optimized breaking are not implemented.
- **M is thin:** no schematic material planning, rotation, selection, or open-Litematica support.
- **N is local-only:** roles are per-bot settings; the host does not assign or report them, and `MINER` only suppresses auto tasks.
- AutoEat’s `blacklist` setting name and several Meteor setting parses are runtime assumptions; failures log one WARN and degrade softly.
- W had no browser/WebSocket test.

## Manual test checklist

1. Activate BotBrain with `manage-modules` on; confirm guard modules enable and restore on deactivate.
2. Set `food-low` above current food; confirm auto-hunt, kill, drop pickup, and cooking when fuel exists.
3. Configure `grind-x/y/z` and `auto-grind`; confirm travel, attacks, drops, and completion.
4. Queue `HUNT_FOOD` and `GRIND` from the web panel with an empty item field.
5. Give template + netherite ingot + diamond gear and a nearby smithing table; confirm a netherite result.
6. Disconnect a bot with an ACTIVE task; confirm the host requeues it to PENDING.
7. Carry a filled shulker with `auto-empty-shulkers`; confirm place/open/empty/break/collect.
8. Set `build-file` to a valid `.schem`/`.litematic` and `auto-build`; confirm Baritone starts and completes.
9. Set `swarm-role` to FOOD/GRINDER/MINER on separate bots; confirm only the matching auto tasks start.
