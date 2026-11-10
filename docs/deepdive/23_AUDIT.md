# 23 — Read-Only Audit (feat/n-swarm)

Audit target: `C:\Projects\swarm\swarm-improved`, branch `feat/n-swarm` @ `fd0ce6e`.
Read-only: no edits, no build, no commits. Evidence uses absolute paths.

## H (tier grinder)

### 1. GearAudit / GrindPlanner / grindTarget (WOOD..NETHERITE) — NO (not found)

- No `GearAudit` class and no `GrindPlanner` class anywhere under `src` (`find src -iname "*gear*" -o -iname "*grind*"` returns nothing).
- No `grindTarget` or `grind-target` setting. The only grinder settings are `auto-grind` at `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\modules\BotBrain.java:129` and `grind-duration` at `...\BotBrain.java:168` (plus `grind-x/y/z`, `grind-range`, `grind-collect`).
- There is no tier enum or WOOD..NETHERITE parameter in the grinder path.

### 2. `/eswarm grind <worker> <tier>` — NO (not found)

- The command tree has `exec` (`...\commands\SwarmCommand.java:120`), `list` (142), `send` (150), `task` (171), `deposit` (270), `scan` (385), `stock` (401), and `base` (430).
- No `grind` literal or `tier` argument exists in `SwarmCommand.java`; grep for `grind` in `commands` and `EnhancedSwarm.java` returns no matches.

### 3. EQUIP real handler — NO

- `EQUIP` exists only as an enum constant: `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\task\BotTask.java:11`.
- `BotBrain` rejects any main task that is not GATHER/CRAFT/SMELT at `...\modules\BotBrain.java:749`; there is no `case EQUIP`.
- The step switch has only CRAFT/SMELT/GATHER and falls through to `failUnhandledTask` at `...\BotBrain.java:792`.

### 4. RecipeBook entries — PARTIAL (4 of 7)

Present:
- Shield: `...\task\RecipeBook.java:170` (`register(Items.SHIELD, ...)`).
- Bucket: `...\RecipeBook.java:175`.
- Bow: `...\RecipeBook.java:297`.
- Bed (white): `...\RecipeBook.java:326`.

Not found in `RecipeBook.java`:
- `flint_and_steel`
- `smithing_table`
- `barrel`

### 5. What the thing called "grinder" does — one sentence

It sends the bot to configured `grind-x/y/z`, attacks nearby `MobCategory.MONSTER` mobs with `GameMode.attack`/`swing`, collects drops, and completes on `grind-duration` or a full inventory (`...\BotBrain.java:3341`, target filter at `...\BotBrain.java:3439`).

## W (web)

### 6. `src/main/resources/web/` with index.html/app.js/style.css; sidebar; hash routing — NO

- The directory does not exist; `find src/main/resources -type f` lists only mixins, `fabric.mod.json`, and `assets/` metadata.
- The UI is inline Java HTML in `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\web\SwarmWebPanel.java:7` (`<html lang="en">`), with a card `layout` div at `...\SwarmWebPanel.java:264`.
- No `sidebar`, `location.hash`, or `hashchange` matches exist in `src/main/resources` or `src/main/java/com/rdh/swarm/web`.

### 7. `/api/registry/items` and `/api/registry/blocks` — NO (not found)

- No `api/registry`, `registry/items`, or `registry/blocks` string exists anywhere in `src/main/java`.

### 8. What `/api/action/scan_chests` and `/api/action/check_fuel` actually send

- `scan_chests` sends `swarm:all deposit list` and logs "Chest scan triggered": `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\web\SwarmWebServer.java:353-355`.
- `check_fuel` sends `swarm:all exec #stop` and logs "Fuel check triggered": `...\SwarmWebServer.java:357-359`.
- `check_fuel` does not check fuel; it only stops Baritone on every worker.

## K (logistics)

### 9. ContainerHelper / FETCH / `/eswarm get` / SORT / sort-rules.json — NO (all not found)

- No `ContainerHelper` class, no `FETCH` task type (`BotTask.java:11` lists GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS, HUNT_FOOD, GRIND, SMITH, SHULKER, BUILD), and no `SORT` or `sort-rules` string.
- The only `get` hit is reflection inside `SwarmCommand.java:101` (`cls.getMethod("get")`), unrelated to a `/eswarm get` command. The command tree has no `get` literal.

### 10. Withdraw still chooses the first deposit chest — YES

- `int[] pos = candidates.get(0);` at `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\modules\BotBrain.java:1526` (after the empty check at 1520-1524).

## I / L / M / N

### 11. I: obsidian, portal building, ancient_debris mining, netherite_scrap smelting — PARTIAL

- Netherite scrap smelting map exists: `Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP` at `C:\Projects\swarm\swarm-improved\src\main\java\com\rdh\swarm\task\RecipeBook.java:436`.
- Ancient debris appears as a mining alias `"ancient_debris", "ancient_debris"` at `...\modules\BotBrain.java:370`, and as a tool-requiring id at `...\BotBrain.java:1583`.
- Obsidian appears only in tier/id checks (`...\task\RecipeBook.java:424`, `...\modules\BotBrain.java:1575`); there is no obsidian-specific mining plan.
- There is no portal-building code. Portal references are travel/return logic only (`...\modules\BotBrain.java:326-333`, `1287-1304`).
- No smelting execution was found that specifically selects netherite scrap; the map entry exists, but no task path was found that targets it.

### 12. L: place a shulker and fill it; BULK_GATHER — PARTIAL

- It places and empties a shulker: `autoEmptyShulkers` at `...\modules\BotBrain.java:197`, `SHULKER_PLACE` at 426/3026, `SHULKER_EMPTY` at 428/3053.
- No fill path was found: grep for `fill.*shulker`, `shulker.*fill`, and `insert.*shulker` returns only the "empty" description/error text.
- `BULK_GATHER` is not found anywhere.

### 13. M: BUILD uses Baritone IBuilderProcess; material list — PARTIAL

- Yes to Baritone: `getBuilderProcess().build("swarm-build", file, new Vec3i(...))` at `...\modules\BotBrain.java:2946-2947` and `getBuilderProcess().isActive()` at 2960.
- No material list: `beginBuild` (`...\BotBrain.java:2936-2955`) resolves only the file path and origin; no required-materials computation or deficit check exists for BUILD.

### 14. N: lease table or task claiming — NO

- No `lease` or `claim` code (grep finds only unrelated "labeled" GUI helpers in `EnhancedSwarm.java:814/821`).
- `TaskQueue` has `requeueActiveForBot` (`...\task\TaskQueue.java:224`) and `hasActiveTask` (246), but no lease, owner token, or atomic claim.

## Summary

| # | Question | Answer |
|---|---|---|
| 1 | GearAudit / GrindPlanner / grindTarget | NO — not found |
| 2 | `/eswarm grind <worker> <tier>` | NO — not found |
| 3 | EQUIP real handler | NO |
| 4 | RecipeBook: shield/bucket/flint_and_steel/smithing_table/bow/bed/barrel | PARTIAL — 4 of 7 |
| 5 | What "grinder" does | Mob-camp attacker/drop collector at fixed coords |
| 6 | Resources web dir/sidebar/hash routing | NO |
| 7 | `/api/registry/items` + blocks | NO — not found |
| 8 | scan_chests / check_fuel payloads | `deposit list` / `exec #stop` |
| 9 | ContainerHelper / FETCH / get / SORT | NO — not found |
| 10 | Withdraw first chest | YES |
| 11 | Netherite acquisition pieces | PARTIAL |
| 12 | Shulker place + fill / BULK_GATHER | PARTIAL — place/empty only, no fill/BULK |
| 13 | BUILD IBuilderProcess + material list | PARTIAL — builder yes, materials no |
| 14 | Lease table / task claiming | NO |
