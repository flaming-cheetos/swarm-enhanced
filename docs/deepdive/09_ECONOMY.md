# 09 — Economy: Deposit, Storage, Waypoints

## DepositConfig (296) — categories + per-server chest registry
File: `meteor-client/enhanced-swarm/deposit-chests.json`.
Servers are keyed by `serverAddress.toLowerCase().trim()`; singleplayer = `singleplayer_<level>`.
RAW category init (lines 28–91):
```java
CATEGORIES.put("ores", Set.of(
    "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
    "minecraft:iron_ingot", "minecraft:coal", "minecraft:lapis_lazuli",
    "minecraft:redstone", "minecraft:copper_ingot", "minecraft:ancient_debris",
    "minecraft:netherite_ingot", "minecraft:netherite_scrap",
    "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
    "minecraft:quartz", "minecraft:amethyst_shard",
    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
    "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
    "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
    "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
    "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
    "minecraft:gold_nugget", "minecraft:iron_nugget"));
CATEGORIES.put("shulkers", Set.of("__CONTAINS:shulker_box"));
CATEGORIES.put("empty_shulkers", Set.of("__CONTAINS:shulker_box"));
CATEGORIES.put("tools", Set.of("__CONTAINS:pickaxe","__CONTAINS:axe","__CONTAINS:shovel","__CONTAINS:hoe","__CONTAINS:sword"));
CATEGORIES.put("armor", Set.of("__CONTAINS:helmet","__CONTAINS:chestplate","__CONTAINS:leggings","__CONTAINS:boots"));
CATEGORIES.put("building", Set.of("minecraft:cobblestone","minecraft:stone","minecraft:dirt", ... 21 more));
CATEGORIES.put("valuables", Set.of("minecraft:diamond","minecraft:emerald","minecraft:netherite_ingot",
    "minecraft:enchanted_golden_apple","minecraft:elytra","minecraft:totem_of_undying","minecraft:beacon",
    "minecraft:nether_star","minecraft:heart_of_the_sea","minecraft:dragon_egg","minecraft:dragon_breath",
    "minecraft:diamond_block","minecraft:emerald_block","minecraft:netherite_block","minecraft:gold_block"));
CATEGORIES.put("food", Set.of("minecraft:cooked_beef", ... "minecraft:suspicious_stew"));
```
`matchesCategory` (94): `"all"` → true; else look up set; `__CONTAINS:x` → `itemId.contains(x)`; otherwise exact equality.
`getCategoryNames()` returns keys. Chest/base API: `addChest/setChest/removeChest(cat[,index])/getChest/hasChest/getChests`,
`setBase/getBase/hasBase/removeBase`. JSON shape (Gson pretty): `{ "<server>": { "base":[x,y,z], "chests": { "<cat>": [[x,y,z],...] } } }` (inferred from the map types; no example file exists in the repo). The `shulkers`/`empty_shulkers` matchers are identical — "empty" is not actually distinguished.

## StorageIndex (203)
File: `meteor-client/enhanced-swarm/storage-index.json`.
`enum ChestStatus { FRESH, STALE, UNREACHABLE, MISSING }`.
`static class ChestRecord { public int x,y,z; public String category; public Map<String,Long> counts; public long lastScan;
public ChestStatus status = STALE; public int usedSlots,totalSlots; public double fullness; }`.
```
applyChestResult(server,worker,category,x,y,z,rec)   // MERGES counts, then save()
categoryTotals(server,category) · getChests(server,category) · getTotal(server,itemId) · getAllItems(server)
save() · load() · normalizeId(String) · debugPrint(server)   // all synchronized
```
JSON shape: `{ "<server>": { "<category>": [ {x,y,z,category,counts:{id:count},lastScan,status,usedSlots,totalSlots,fullness} ] } }` (Gson of public fields).

## WorkerConfig (51) / BotStatus / WorkerStatus
- WorkerConfig: per-worker module/setting state mirror used by `/api/...` + GUI; no file of its own (in-memory).
- BotStatus: transient (12-field BOT_STATUS), includes `tier`, no file.
- WorkerStatus: transient STATUS frame, no file.

## WaypointManager (289)
Per-server directory: `<mc run>/enhanced-swarm/waypoints/<sanitized server>/waypoints.json`; JSON = `Map<String,SwarmWaypoint>`
(TypeToken), lowercased names. `sanitizeServerName` → `[^a-zA-Z0-9._-]` → `_`.
```
getCurrentServer() · load(server) · save(server) · ensureLoaded()
addWaypoint(name,category,x,y,z,isNether) · addPortalWaypoint(name,x,y,z,isNether)
removeWaypoint(name) · getWaypoint(name) · getWaypointsByCategory(cat) · getFirstPortal()
getAllWaypoints() · findWaypointForResource(resource, inNether)   // returns first match (comment: "for now")
static getCategoryForResource(resource) · static isNetherCategory(cat)
```
`RESOURCE_CATEGORIES` maps ore ids → `cave` / `nether_cave` / `wood` etc. Nether waypoints store a separate triplet inside
`SwarmWaypoint` (`setCoords(..., isNether)`), so a portal carries both overworld and nether positions.

## End-to-end deposit/withdraw flow (file that sends each step)
1. `/eswarm deposit set ores 100 64 -20` → host stores in `DepositConfig`, `save()`.
2. Host pushes on connect/REQUEST_CONFIG/web push: `swarm:<w> deposit set ores 100 64 -20` (**Connection:97**).
3. `deposit` / `depositall` / web action → `swarm:<w> depositall` (**Host sendTo**, caller-prefixed).
4. Worker `performCategoryDeposit` walks configured chests, filters `KEEP_IDS`/`KEEP_PREFIXES`, opens chests, moves items (**Worker 677–916**).
5. `scan` → worker scans chest slots, sends `SCAN_CHEST_RESULT:<cat>:<x>:<y>:<z>|<json>` (**Worker 1341**).
6. Host parses it → `StorageIndex.applyChestResult` → `storage-index.json` (**Connection 325–361**).
7. Worker needs an item: emits `WITHDRAW_REQUEST:<item>:<need>:<cat>` (**Connection 406 branch**).
8. Host picks freshest chest from index (rescan if `lastScan` older than 5 min) and sends
   `withdraw <cat> <x> <y> <z> <item> <take>` (**Connection 469**, bare prefix).
9. Worker travels, opens, moves, replies `WITHDRAW_RESULT:<item>:<x>:<y>:<z>:<taken>` (**Worker 1332**); host logs it.
10. `CHEST_FULL:<cat>` (**Worker 519/698**) tells the host that category storage is saturated.

## Gaps
- No item-name normalization on the host side beyond a simple `minecraft:` prefix fix.
- Storage index trusts worker-reported counts forever (no TTL expiry; stale only affects withdraw chest choice).
- `empty_shulkers` cannot work as written; `shulkers` and `empty_shulkers` are the same matcher.
