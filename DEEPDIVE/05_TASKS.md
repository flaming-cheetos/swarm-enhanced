# 05 — Task Engine

## BotTask (102)
Purpose: value object for one unit of work, JSON-persistable by TaskQueue.
```
enum Type { GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS }
enum Status { PENDING, ACTIVE, BLOCKED, COMPLETE, FAILED }
fields: UUID id, Type type, int priority, Map<String,Object> data, Instant createdAt,
        Item targetItem, Item sourceItem, int targetQuantity, int collectedQuantity, Status status, String assignedBot
BotTask(UUID,Type,int,Map) · BotTask(Type,Item,int)
getId/getType/getPriority/getData/getCreatedAt/getTargetItem/getSourceItem/getTargetQuantity
getRemaining (max(0, target-collected)) · getCollectedQuantity · addCollected
isComplete (remaining<=0) · getStatus/setStatus · getAssignedBot/setAssignedBot
getProgressString "%d/%d" · getItemName (registry path) · getElapsedString · toString
```

## TaskQueue (232)
Purpose: in-memory priority queue + nicknames, persisted to `meteor-client/enhanced-swarm/task-queue.json`.
```
TaskQueue()                       // PriorityQueue<BotTask> comparator comparingInt(priority).reversed(); load()
add · poll · peek · size · clear · removeById(UUID) · toList
getNickname · setNickname · getAllTasks · getAllNicknames · addTask
save · load · clearStale · clearCompleted
getNextPendingForBot(String) · hasActiveTask(String)
```
Bugs:
1. `getNextPendingForBot` iterates `toList()` (a `PriorityQueue`'s iterator = heap order, **not** sorted), so priority is
   ignored for dispatch; only `poll()` is properly ordered.
2. `add()` calls `save()`, and `addTask()` calls `add()` then `save()` again → two JSON writes per enqueue.
3. `removeById` scans a copy then `queue.remove(t)`; O(n²)-ish and identity-based.
4. `load()` skips COMPLETE/FAILED while reading, then calls `clearStale()` which saves again.
Schema: `{ "tasks":[{id,type,priority,item,targetQuantity,collected,status,assignedBot}], "nicknames":{name:nick} }`.

## BaseMap (36)
Thin `ConcurrentHashMap<K,V>` wrapper (`put/get/remove/containsKey/keySet/values/size/clear`). No callers were found in
the worker/host paths; dead utility.

## DependencyResolver (285)
Purpose: recursive recipe expansion → `ExecutionPlan(steps, gatherList, craftPlan, requiredTier)`.
- BFS over `RecipeBook`, accumulates gross needs, subtracts `CraftingManager.countInInventory`, topologically sorts
  craftables (Kahn), injects stone for furnace (8) and logs for 3×3 (3) when needed, maps cobblestone→stone and flint→gravel.
- Smelting: for ingots, gathers the ore/raw form and adds `SMELT` tasks + coal (`ceil(qty / 8)`).
- `getCurrentTier()` = highest pickaxe in inventory: NETHERITE > DIAMOND > IRON > STONE > WOOD.
- `getEquipmentTasks(Tier target)`: for each tier level above current, resolves every item in `tier.tools` not owned.
- Debug `System.out` dumps the whole plan on every resolve.

## RecipeBook (492)
Purpose: hardcoded crafting table (9-slot pattern map), tier table, smelting map, fuel table.
Registered: planks/sticks/table, wood tools, stone tools, furnace/chest, iron tools+armor+shield+bucket,
diamond tools+armor, golden tools+armor, leather armor, torch/bow/arrow/ladder/boat/door/bed/bread/paper/hopper.
**Not registered:** netherite anything, smithing table, enchanting table, anvil, blast furnace.

RAW tier enum:
```java
public enum Tier {
    WOOD(0, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SWORD),
    STONE(1, STONE_PICKAXE, STONE_AXE, STONE_SWORD),
    IRON(2, IRON_PICKAXE, IRON_AXE, IRON_SWORD, IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS),
    DIAMOND(3, DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SWORD, DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS),
    NETHERITE(4, NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_SWORD, NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS);
    public final int level; public final Item[] tools; public Tier next();
}
```
RAW requirement map:
```java
if (id.contains("diamond") || id.contains("emerald") || id.contains("gold") || id.contains("redstone")) return Tier.IRON;
if (id.contains("iron") || id.contains("lapis") || id.contains("copper")) return Tier.STONE;
if (id.contains("obsidian") || id.contains("ancient_debris") || id.contains("netherite")) return Tier.DIAMOND;
return Tier.WOOD;
```
RAW smelting:
```java
RAW_IRON→IRON_INGOT, RAW_GOLD→GOLD_INGOT, RAW_COPPER→COPPER_INGOT,
IRON_ORE→IRON_INGOT, GOLD_ORE→GOLD_INGOT, COPPER_ORE→COPPER_INGOT, ANCIENT_DEBRIS→NETHERITE_SCRAP
```
RAW fuel: COAL 8, CHARCOAL 8, COAL_BLOCK 80, BLAZE_ROD 12, all logs/planks 1.
`requires3x3`: pattern >4 slots or any slot outside {5,6,8,9}.

## CraftingManager (214)
Screen helpers for BotBrain: `canCraft`, `countInInventory`, `findCraftingTable(range)` (45×21×45 scan),
`isCraftingOpen`, `openCraftingTable(BlockPos)`, `hasIngredientInInventory`, `findIngredientSlot`,
`gridSlotHasItem`, `outputReady`, `closeCrafting`, `placeCraftingTable()` (reflection on `selectedSlot`).
`CraftResult` enum: SUCCESS, NO_RECIPE, MISSING_INGREDIENTS, NO_CRAFTING_TABLE, TABLE_NOT_OPEN, FAILED (unused).

## SmeltingManager (135)
`isFurnaceOpen`, `openFurnace`, `depositOre` (queue slot 0), `depositFuel` (slot 1, coal then charcoal),
`takeResult` (shift-click slot 2), `hasFuel`, `countFuel`, `hasInput`, `getOutputCount`, `closeFurnace`.

## DeathTracker (85)
`DeathRecord(time,cause,pos)` per player UUID; `recordDeath`, `getDeaths`, `getDeathCount`, `clear`,
`hasDeath`, `canRecover` (5 min window), `getDeathPos`, `getTimeRemainingMs`, `clearDeath`.
`load()` is an explicit **no-op placeholder** (comment) → deaths are memory-only.

## How progress strings are built
`BotTask.getProgressString()` → `String.format("%d/%d", collectedQuantity, targetQuantity)`; surfaced in BOT_STATUS,
web JSON, and GUI task rows.
