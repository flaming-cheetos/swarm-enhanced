# 06 — Armor Tiers, Kits, TPR, Shulkers

## Where tier logic lives
| file | role |
|---|---|
| `task/RecipeBook.java` | `enum Tier WOOD(0) STONE(1) IRON(2) DIAMOND(3) NETHERITE(4)` with a `tools[]` array per tier; `getRequiredTier(block)`; hardcoded recipes; smelting + fuel maps |
| `task/DependencyResolver.java` | `getCurrentTier()` (highest pickaxe owned), `getEquipmentTasks(targetTier)`, `resolve(item,qty)` |
| `modules/BotBrain.java` | before gathering: `RecipeBook.getRequiredTier(target)` vs `DependencyResolver.getCurrentTier()`, then "Upgrading to X tier" and resolves equipment tasks |
| `BotStatus` / `WorkerStatus` | `tier` field sent to host + web (`"tier":"IRON"`) |
| `SwarmChestSwap` | only *proxy* setting `chestplate: Diamond|Netherite|PreferDiamond|PreferNetherite` (remote Meteor module) |

## What actually works today
- **Craftable chain: WOOD → STONE → IRON → DIAMOND** (tools + armor). Recipes exist for all four, plus leather and
  golden armor as side branches.
- `NETHERITE` exists **only as an enum constant and an inventory check**. There is:
  - no netherite tool/armor recipe,
  - no smithing table item/recipe,
  - no smithing-screen interaction anywhere (`netherite|smithing` hits = enum + DepositConfig categories + `SwarmHoleFiller` block list + `SwarmChestSwap` enum),
  - no ancient-debris gather plan beyond `getRequiredTier` (ANCIENT_DEBRIS→NETHERITE_SCRAP is in `SMELTING`, unused).
- `resolve(NETHERITE_PICKAXE)` finds no recipe → puts `netherite_pickaxe` in `gatherList` → BotBrain would send
  `#mine minecraft:netherite_pickaxe`, which is impossible. So `getEquipmentTasks(NETHERITE)` is a dead end.
- `getCurrentTier()` only inspects the **pickaxe**; armor is not part of tier detection.
- `EQUIP` exists in `BotTask.Type` but **BotBrain's step switch handles only CRAFT / SMELT / GATHER**
  (plus internal DEPOSITING). No equip routine — `BotBrain.autoEquip` setting exists but there is no queue step for it.
- `SCAN_CHESTS` exists in `BotTask.Type` but is never scheduled by resolver/brain; chest scanning is the worker `scan` verb.

## Kits / TPR / shulkers
- **`/kit` does not exist anywhere** in source (the `kit` grep only hit `trackItemCollection`, `BlockItem`, CSS scrollbar).
- **`/tpr`, `/rtp`, `/warp`, `/home`, `/spawn` do not exist**. `SwarmAutoTPA` only answers *incoming* TPA requests:
  regex `(.*) wants to teleport to you`, accept command default **`/tpy {username}`**, 500 ms delay, friendsOnly.
  There is no outgoing teleport request, no random teleport, no kit claim.
- **Shulkers** appear only as deposit categories: `shulkers` and `empty_shulkers` both `"__CONTAINS:shulker_box"`
  (identical matcher — "empty" is not distinguished), plus UI buttons "Dep. Shulkers". No placing/filling/withdrawing
  shulkers, no shulker-based inventory expansion.

## 6b6t kitbot loop implied by the code
Host → `worker` connects → `task assign` → BotBrain resolves recipe → GATHER (Baritone `#mine`) → SMELT (furnace) →
CRAFT (table) → auto-deposit to categorized chests → status to host/web. Kits/TPR are assumed to be done manually or by
an external mod; the swarm itself never claims a kit or teleports to a farm.

## What diamond → netherite would need
1. `RecipeBook`: `SMITHING_TABLE` (4 planks + 2 iron), `NETHERITE_INGOT` (4 scrap + 4 gold, 3×3), and per-piece
   upgrade recipes (template + diamond piece + ingot) — or a dedicated smithing handler since it is not a 3×3 craft.
2. Template acquisition: `NETHERITE_UPGRADE_SMITHING_TEMPLATE` from bastion loot — needs a `GATHER`/loot source or a
   "buy from kit" step.
3. `DependencyResolver`: special-case netherite so it plans scrap+gold rather than the pickaxe itself; add
   `getEquipmentTasks` armor coverage beyond `tools[]`.
4. `BotBrain`: new state `SMITHING` driving the smithing table UI (`SmithingScreenHandler` slots 0/1/2/3).
5. Tier detection: inspect armor slots, not just pickaxe.

## Raw hits (tier/kit/netherite hunt, representative)
```
BotStatus.java:15  public String tier = "";
BotStatus.java:23  // BOT_STATUS:workerName:state:taskDescription:collected:total:eta:health:food:x:y:z:tier
BotStatus.java:46  if (parts.length > 11) bs.tier = parts[11];
DepositConfig.java:33  "minecraft:netherite_ingot", "minecraft:netherite_scrap",
DepositConfig.java:47  CATEGORIES.put("shulkers", Set.of("__CONTAINS:shulker_box"));
DepositConfig.java:48  CATEGORIES.put("empty_shulkers", Set.of("__CONTAINS:shulker_box"));
DepositConfig.java:72  "minecraft:diamond", "minecraft:emerald", "minecraft:netherite_ingot",
DepositConfig.java:73  "minecraft:enchanted_golden_apple", "minecraft:elytra",
DepositConfig.java:74  "minecraft:totem_of_undying", "minecraft:beacon",
EnhancedSwarmWorker.java:81  "minecraft:totem_of_undying", "minecraft:ender_pearl", "minecraft:firework_rocket",
EnhancedSwarmWorker.java:82  "minecraft:end_crystal", "minecraft:elytra", "minecraft:shield"
EnhancedSwarmWorker.java:86  "minecraft:netherite_", "minecraft:diamond_", "minecraft:iron_",
EnhancedSwarmWorker.java:87  "minecraft:stone_", "minecraft:wooden_", "minecraft:golden_"
EnhancedSwarmWorker.java:1389  bs.tier = com.rdh.swarm.task.DependencyResolver.getCurrentTier().name();
RecipeBook.java:401  NETHERITE(4, Items.NETHERITE_PICKAXE, ... NETHERITE_BOOTS);
RecipeBook.java:424  if (id.contains("obsidian") || id.contains("ancient_debris") || id.contains("netherite")) return Tier.DIAMOND;
RecipeBook.java:436  Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP
DependencyResolver.java:242  if (hasInInventory(Items.NETHERITE_PICKAXE)) return RecipeBook.Tier.NETHERITE;
BotBrain.java:42   .description("Automatically upgrade equipment before gathering.")
BotBrain.java:312  "Autonomous bot AI. Gathers, crafts, smelts, deposits, and upgrades equipment."
BotBrain.java:453  RecipeBook.Tier needed;
BotBrain.java:462  RecipeBook.Tier current = DependencyResolver.getCurrentTier();
BotBrain.java:475  // Need to upgrade - resolve equipment tasks
BotBrain.java:481  currentTaskDescription = "Upgrading to " + needed.name() + " tier";
SwarmChestSwap.java:11  public enum Chestplate { Diamond, Netherite, PreferDiamond, PreferNetherite }
SwarmChestSwap.java:13  .defaultValue(Chestplate.PreferNetherite)
SwarmHoleFiller.java:30  Block.getBlockFromItem(Items.NETHERITE_BLOCK)
SwarmWebPanel.java:128  .tier-NETHERITE { background: #2a1a2a; color: #cc88ff; }
```
Full 200-hit dump was captured during inspection; the above are the load-bearing ones (no `/kit`, `/tpr`, smithing,
enchanting, or template handling exists).
