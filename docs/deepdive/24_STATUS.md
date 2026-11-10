# 24 — Status Snapshot (read-only)

Repo: `C:\Projects\swarm\swarm-improved`
Snapshot taken before testing. No edits, no build, no commit.

## A. WHERE AM I

- Branch: `feat/n-swarm`
- HEAD: `fd0ce6e Document the G1-N mega-run results and limitations.`
- Previous: `f2d1fd8 Add swarm roles that gate automatic BotBrain work.` / `344dd7f Add a Baritone schematic builder task and state.`
- `git status --porcelain`:
  - `?? DEEPDIVE/23_AUDIT.md`
- Modified tracked `.java` / `.kts`: **NONE**
- Untracked `*.log`: **NONE**

### Tags

| Tag | Commit |
|---|---|
| done-g1 | f7c2abe |
| done-g2 | 647581e |
| done-h | 104efa9 |
| done-i | 70eb2a9 |
| done-k | 56fafd7 |
| done-l | b026dde |
| done-m | 344dd7f |
| done-n | f2d1fd8 |
| done-w | 157ad28 |

### Branches

Local: `feat/g1-survival`, `feat/g2-food`, `feat/h-grinder`, `feat/i-netherite`, `feat/k-logistics`, `feat/l-shulkers`, `feat/m-builder`, `feat/n-swarm` (current), `master`, `port-26.3`
Remote: `origin/HEAD -> origin/master`, `origin/copilot/add-enhanced-swarm-addon`, `origin/master`

### Jar

- Exists: `C:\Projects\swarm\swarm-improved\build\libs\enhanced-swarm-0.1.0.jar`
- Size: 428,534 bytes
- Last write: 2026-11-07 19:24:21 -0600
- Newest `.java` under `src`: 2026-11-07 19:24:19 (`BotBrain.java`) — jar is 2 seconds newer.
- Verdict: **FRESH** (not stale by timestamp)

## B. FEATURE PROBE

| Feature | Verdict | Evidence |
|---|---|---|
| Class `ModuleGuard` | EXISTS | `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\modules\ModuleGuard.java:36`; called at `...\modules\BotBrain.java:561-563,585,644-646` |
| Class `GearAudit` | ABSENT | no file or class under `...\swarm-improved\src` |
| Class `GrindPlanner` | ABSENT | no file or class under `...\swarm-improved\src` |
| Class `FoodManager` | EXISTS | `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\task\FoodManager.java:15`; used at `...\modules\BotBrain.java:2690,2712,2877,3496` |
| Class `ContainerHelper` | ABSENT | not found under `...\swarm-improved\src` |
| Class `ShulkerHelper` | ABSENT | not found under `...\swarm-improved\src` |
| Class `MaterialList` | ABSENT | not found under `...\swarm-improved\src` |
| `BotTask.Type` values | EXISTS | `...\task\BotTask.java:11` — `GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS, HUNT_FOOD, GRIND, SMITH, SHULKER, BUILD` |
| Setting `manageModules` | EXISTS | `...\modules\BotBrain.java:83-88` — Boolean `manage-modules`, default `true` |
| Setting `fightMobs` | EXISTS | `...\modules\BotBrain.java:90-95` — Boolean `fight-mobs`, default `true` |
| Setting `foodLow` | EXISTS | `...\modules\BotBrain.java:97-103` — Integer `food-low`, default `6` |
| Setting `foodTarget` | EXISTS | `...\modules\BotBrain.java:105-111` — Integer `food-target`, default `20` |
| Setting `huntRange` | EXISTS | `...\modules\BotBrain.java:113-119` — Integer `hunt-range`, default `96` |
| Setting `eatRawFallback` | EXISTS | `...\modules\BotBrain.java:121-127` — Boolean `eat-raw-fallback`, default `true` |
| Setting `grindTarget` | ABSENT | no `grindTarget` / `grind-target` under `...\swarm-improved\src` |
| Setting `junkDrop` | ABSENT | no `junkDrop` under `...\swarm-improved\src` |
| Setting `autoSort` | ABSENT | no `autoSort` under `...\swarm-improved\src` |
| Setting `sleepAtNight` | ABSENT | no `sleepAtNight` under `...\swarm-improved\src` |
| Settings containing `grind` | EXISTS | `auto-grind` Boolean false (`BotBrain.java:128-133`), `grind-x`/`grind-y`/`grind-z` Integer 0 (`:135-157`), `grind-range` Integer 16 (`:159-165`), `grind-duration` Integer 300 (`:167-173`), `grind-collect` Boolean true (`:175-180`) |
| Settings containing `food` | EXISTS | `food-low` Integer 6 (`BotBrain.java:97-103`), `food-target` Integer 20 (`:105-111`) |
| `handleIdle` default → GATHERING | NO | `...\modules\BotBrain.java:792` `default -> failUnhandledTask(step)`; GATHER/CRAFT/SMELT gate at `:749`; GATHERING set only at `:767,775,790` |
| `case EQUIP` handler | ABSENT | only enum `...\task\BotTask.java:11`, rejection `...\modules\BotBrain.java:749`, default fail `:792`; `grep "case EQUIP"` empty |
| Recipe `flint_and_steel` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `torch` | EXISTS | `...\task\RecipeBook.java:292` |
| Recipe `shield` | EXISTS | `...\task\RecipeBook.java:170` |
| Recipe `bucket` | EXISTS | `...\task\RecipeBook.java:175` |
| Recipe `bow` | EXISTS | `...\task\RecipeBook.java:297` |
| Recipe `bed` | EXISTS | `...\task\RecipeBook.java:326` (`Items.BED.white()`) |
| Recipe `furnace` | EXISTS | `...\task\RecipeBook.java:112` |
| Recipe `smithing_table` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `barrel` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `blast_furnace` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `enchanting_table` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `anvil` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `hopper` | EXISTS | `...\task\RecipeBook.java:338` |
| Recipe `chest` | EXISTS | `...\task\RecipeBook.java:118` |
| Recipe `crossbow` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `shears` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `fishing_rod` | ABSENT | not in `...\task\RecipeBook.java` |
| Recipe `arrow` | EXISTS | `...\task\RecipeBook.java:303` |
| Command `/eswarm grind` | ABSENT | no `grind` literal in `...\commands\SwarmCommand.java` |
| Command `/eswarm get` | ABSENT | only reflection `getMethod("get")` at `...\commands\SwarmCommand.java:101`; no command literal |
| Command `/eswarm sort` | ABSENT | no `sort` literal in `...\commands\SwarmCommand.java` |
| Command `/eswarm build` | ABSENT | no `build` literal in `...\commands\SwarmCommand.java` |
| Command `/eswarm bulk` | ABSENT | no `bulk` literal in `...\commands\SwarmCommand.java` |
| Command `/eswarm deposit` | EXISTS | `...\commands\SwarmCommand.java:270` |
| Command `/eswarm waypoint` | EXISTS | `...\commands\SwarmCommand.java:495` |
| Command `/eswarm task` | EXISTS | `...\commands\SwarmCommand.java:171` |
| Directory `src\main\resources\web\` | ABSENT | directory does not exist; UI is inline `...\web\SwarmWebPanel.java:7` |
| `/api/registry/items` | ABSENT | no match under `...\swarm-improved\src\main\java` |
| `/api/registry/blocks` | ABSENT | no match under `...\swarm-improved\src\main\java` |
| `/api/storage` | ABSENT | no match under `...\swarm-improved\src\main\java` |
| `/api/waypoints` | ABSENT | no match under `...\swarm-improved\src\main\java` |
| `/api/grind/status` | ABSENT | no match under `...\swarm-improved\src\main\java` |
| Actual web endpoints | EXISTS | `...\web\SwarmWebServer.java:245-375` — task add/clear_done/remove/pause/resume, action follow/stop/deposit/deposit_ores/deposit_shulkers/pvp_on/pvp_off/scan_chests/check_fuel, command, nickname |
| `candidates.get(0)` | EXISTS | `...\modules\BotBrain.java:1526` — `int[] pos = candidates.get(0);` |
| `scan_chests` payload | EXISTS (wrong action) | `...\web\SwarmWebServer.java:354` — sends `swarm:all deposit list` |
| `check_fuel` payload | EXISTS (wrong action) | `...\web\SwarmWebServer.java:358` — sends `swarm:all exec #stop` |
| `LEASE_REQUEST` | ABSENT | no match under `...\swarm-improved\src` |
| `DeathTracker.recordDeath` callers | EXISTS (1 caller) | `...\modules\BotBrain.java:612`; definition `...\task\DeathTracker.java:36,40` |
| Classes extending `SwarmProxyModule` | 38 | 38 files match `extends SwarmProxyModule` under `...\src\main\java` |
| Those actually registered | 38 of 38 | all 38 have `Modules.get().add(new ...)` in `...\EnhancedSwarmAddon.java:26-64`; 44 total module adds (38 proxies + EnhancedSwarm, Spam, AutoTPA, AirCrystal, PearlChase, BotBrain) |

## SAFE TO TEST NOW (exists and wired end to end)

- **Boot** — jar `build\libs\enhanced-swarm-0.1.0.jar` exists and is fresh; `EnhancedSwarm` is added at `...\EnhancedSwarmAddon.java:25`; `/eswarm list` is registered at `...\commands\SwarmCommand.java:142`.
- **G1 module guard** — `ModuleGuard` is instantiated at `...\modules\BotBrain.java:382`, activated/deactivated at `:561-563,585`, ticked at `:644-646`; guard set includes AutoEat/AutoArmor/AutoTotem/AutoTool/AutoReplenish/NoFall/AntiHunger/AutoRespawn/KillAura (`...\modules\ModuleGuard.java:39-74`).
- **G1 baritone stop + death record** — `onDeactivate` sends `#stop` and cancels pathing at `...\modules\BotBrain.java:591-594`; death is recorded at `...\modules\BotBrain.java:612`.
- **G2 food hunt + cooking** — auto-hunt below `food-low` at `...\modules\BotBrain.java:2872-2885`; post-hunt cooking queue with fuel/raw checks at `:2907-2931`; smelting states at `:2486-2561`.
- **H grinder via settings only** — `auto-grind` + `grind-x/y/z` trigger `GRIND` at `...\modules\BotBrain.java:3492-3504`; execution at `:3341-3440`. There is no `/eswarm grind` command and no tier argument.

Note: the guard log line is `ModuleGuard: Activated %d modules (...)` (`...\modules\ModuleGuard.java:142`), not `ModuleGuard: enabled ...`.

## WILL FAIL IF TESTED (absent or stub with no dispatch)

- `/eswarm grind all diamond` — command absent; no tier/gear model (`GearAudit`, `GrindPlanner`, `grindTarget` all absent).
- Any `EQUIP` task — enum only, no `case EQUIP`; rejected at `...\modules\BotBrain.java:749` and default-failed at `:792`.
- Requests for `flint_and_steel`, `smithing_table`, `barrel`, `blast_furnace`, `enchanting_table`, `anvil`, `crossbow`, `shears`, `fishing_rod` — no recipe entry.
- `/api/registry/items`, `/api/registry/blocks`, `/api/storage`, `/api/waypoints`, `/api/grind/status` — no handlers.
- `/eswarm get`, `/eswarm sort`, `/eswarm build`, `/eswarm bulk` — not registered; `ContainerHelper`, `FETCH`, `SORT`, `sort-rules.json` absent.
- Server-side task leasing/claiming — `LEASE_REQUEST` absent; `TaskQueue` has only requeue/active checks (`...\task\TaskQueue.java:224,246`).
- `scan_chests` / `check_fuel` buttons — they send `deposit list` and `exec #stop`, so they do not scan chests or check fuel.
- Shulker fill and `BULK_GATHER` — place/empty only (`...\modules\BotBrain.java:3026,3053`); no fill path.
- Build material planning — `beginBuild` has no material list (`...\modules\BotBrain.java:2936-2965`).
- Nether portal building and obsidian mining — no portal builder; obsidian appears only in tier/id checks.
