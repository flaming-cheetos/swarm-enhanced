package com.rdh.swarm.task;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.*;

public class RecipeBook {

    public static class Recipe {
        public final Item output;
        public final int outputCount;
        public final Map<Integer, Item> pattern;
        public final Map<Item, Integer> ingredients;
        public final boolean needsCraftingTable;

        public Recipe(Item output, int outputCount, Map<Integer, Item> pattern, boolean needsCraftingTable) {
            this.output = output;
            this.outputCount = outputCount;
            this.pattern = pattern;
            this.needsCraftingTable = needsCraftingTable;

            this.ingredients = new HashMap<>();
            for (Item item : pattern.values()) {
                ingredients.merge(item, 1, Integer::sum);
            }
        }
    }

    private static final Map<Item, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        // ===== WOOD TIER =====

        // Planks from oak log (any log works, we use oak as default)
        // Single slot recipe, 4 output
        register(Items.OAK_PLANKS, 4, false,
            Map.of(6, Items.OAK_LOG));

        // Sticks (right-aligned)
        register(Items.STICK, 4, false,
            Map.of(6, Items.OAK_PLANKS,
                   9, Items.OAK_PLANKS));

        // Crafting table (right-aligned bottom)
        register(Items.CRAFTING_TABLE, 1, false,
            Map.of(5, Items.OAK_PLANKS, 6, Items.OAK_PLANKS,
                   8, Items.OAK_PLANKS, 9, Items.OAK_PLANKS));

        // Wooden pickaxe
        register(Items.WOODEN_PICKAXE, 1, true,
            Map.of(1, Items.OAK_PLANKS, 2, Items.OAK_PLANKS, 3, Items.OAK_PLANKS,
                   5, Items.STICK,
                   8, Items.STICK));

        // Wooden axe (right-aligned)
        register(Items.WOODEN_AXE, 1, true,
            Map.of(2, Items.OAK_PLANKS, 3, Items.OAK_PLANKS,
                   5, Items.STICK, 6, Items.OAK_PLANKS,
                   8, Items.STICK));

        // Wooden sword (right-aligned)
        register(Items.WOODEN_SWORD, 1, true,
            Map.of(3, Items.OAK_PLANKS,
                   6, Items.OAK_PLANKS,
                   9, Items.STICK));

        // Wooden shovel
        register(Items.WOODEN_SHOVEL, 1, true,
            Map.of(2, Items.OAK_PLANKS,
                   5, Items.STICK,
                   8, Items.STICK));

        // Wooden hoe
        register(Items.WOODEN_HOE, 1, true,
            Map.of(1, Items.OAK_PLANKS, 2, Items.OAK_PLANKS,
                   5, Items.STICK,
                   8, Items.STICK));

        // ===== STONE TIER =====

        register(Items.STONE_PICKAXE, 1, true,
            Map.of(1, Items.COBBLESTONE, 2, Items.COBBLESTONE, 3, Items.COBBLESTONE,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.STONE_AXE, 1, true,
            Map.of(2, Items.COBBLESTONE, 3, Items.COBBLESTONE,
                   5, Items.STICK, 6, Items.COBBLESTONE,
                   8, Items.STICK));

        register(Items.STONE_SWORD, 1, true,
            Map.of(3, Items.COBBLESTONE,
                   6, Items.COBBLESTONE,
                   9, Items.STICK));

        // Stone shovel
        register(Items.STONE_SHOVEL, 1, true,
            Map.of(2, Items.COBBLESTONE,
                   5, Items.STICK,
                   8, Items.STICK));

        // Stone hoe
        register(Items.STONE_HOE, 1, true,
            Map.of(1, Items.COBBLESTONE, 2, Items.COBBLESTONE,
                   5, Items.STICK,
                   8, Items.STICK));

        // Furnace (full grid)
        register(Items.FURNACE, 1, true,
            Map.of(1, Items.COBBLESTONE, 2, Items.COBBLESTONE, 3, Items.COBBLESTONE,
                   4, Items.COBBLESTONE, 6, Items.COBBLESTONE,
                   7, Items.COBBLESTONE, 8, Items.COBBLESTONE, 9, Items.COBBLESTONE));

        // Chest (full grid)
        register(Items.CHEST, 1, true,
            Map.of(1, Items.OAK_PLANKS, 2, Items.OAK_PLANKS, 3, Items.OAK_PLANKS,
                   4, Items.OAK_PLANKS, 6, Items.OAK_PLANKS,
                   7, Items.OAK_PLANKS, 8, Items.OAK_PLANKS, 9, Items.OAK_PLANKS));

        // ===== IRON TIER =====

        register(Items.IRON_PICKAXE, 1, true,
            Map.of(1, Items.IRON_INGOT, 2, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.IRON_AXE, 1, true,
            Map.of(2, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   5, Items.STICK, 6, Items.IRON_INGOT,
                   8, Items.STICK));

        register(Items.IRON_SWORD, 1, true,
            Map.of(3, Items.IRON_INGOT,
                   6, Items.IRON_INGOT,
                   9, Items.STICK));

        // Iron shovel
        register(Items.IRON_SHOVEL, 1, true,
            Map.of(2, Items.IRON_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        // Iron hoe
        register(Items.IRON_HOE, 1, true,
            Map.of(1, Items.IRON_INGOT, 2, Items.IRON_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.IRON_HELMET, 1, true,
            Map.of(1, Items.IRON_INGOT, 2, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   4, Items.IRON_INGOT, 6, Items.IRON_INGOT));

        register(Items.IRON_CHESTPLATE, 1, true,
            Map.of(1, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   4, Items.IRON_INGOT, 5, Items.IRON_INGOT, 6, Items.IRON_INGOT,
                   7, Items.IRON_INGOT, 8, Items.IRON_INGOT, 9, Items.IRON_INGOT));

        register(Items.IRON_LEGGINGS, 1, true,
            Map.of(1, Items.IRON_INGOT, 2, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   4, Items.IRON_INGOT, 6, Items.IRON_INGOT,
                   7, Items.IRON_INGOT, 9, Items.IRON_INGOT));

        register(Items.IRON_BOOTS, 1, true,
            Map.of(4, Items.IRON_INGOT, 6, Items.IRON_INGOT,
                   7, Items.IRON_INGOT, 9, Items.IRON_INGOT));

        register(Items.SHIELD, 1, true,
            Map.of(1, Items.OAK_PLANKS, 2, Items.IRON_INGOT, 3, Items.OAK_PLANKS,
                   4, Items.OAK_PLANKS, 5, Items.OAK_PLANKS, 6, Items.OAK_PLANKS,
                   8, Items.OAK_PLANKS));

        register(Items.BUCKET, 1, true,
            Map.of(4, Items.IRON_INGOT, 6, Items.IRON_INGOT,
                   8, Items.IRON_INGOT));

        // ===== DIAMOND TIER =====

        register(Items.DIAMOND_PICKAXE, 1, true,
            Map.of(1, Items.DIAMOND, 2, Items.DIAMOND, 3, Items.DIAMOND,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.DIAMOND_AXE, 1, true,
            Map.of(2, Items.DIAMOND, 3, Items.DIAMOND,
                   5, Items.STICK, 6, Items.DIAMOND,
                   8, Items.STICK));

        register(Items.DIAMOND_SWORD, 1, true,
            Map.of(3, Items.DIAMOND,
                   6, Items.DIAMOND,
                   9, Items.STICK));

        // Diamond shovel
        register(Items.DIAMOND_SHOVEL, 1, true,
            Map.of(2, Items.DIAMOND,
                   5, Items.STICK,
                   8, Items.STICK));

        // Diamond hoe
        register(Items.DIAMOND_HOE, 1, true,
            Map.of(1, Items.DIAMOND, 2, Items.DIAMOND,
                   5, Items.STICK,
                   8, Items.STICK));

        // Golden tools
        register(Items.GOLDEN_PICKAXE, 1, true,
            Map.of(1, Items.GOLD_INGOT, 2, Items.GOLD_INGOT, 3, Items.GOLD_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.GOLDEN_AXE, 1, true,
            Map.of(2, Items.GOLD_INGOT, 3, Items.GOLD_INGOT,
                   5, Items.STICK, 6, Items.GOLD_INGOT,
                   8, Items.STICK));

        register(Items.GOLDEN_SWORD, 1, true,
            Map.of(3, Items.GOLD_INGOT,
                   6, Items.GOLD_INGOT,
                   9, Items.STICK));

        register(Items.GOLDEN_SHOVEL, 1, true,
            Map.of(2, Items.GOLD_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.GOLDEN_HOE, 1, true,
            Map.of(1, Items.GOLD_INGOT, 2, Items.GOLD_INGOT,
                   5, Items.STICK,
                   8, Items.STICK));

        register(Items.DIAMOND_HELMET, 1, true,
            Map.of(1, Items.DIAMOND, 2, Items.DIAMOND, 3, Items.DIAMOND,
                   4, Items.DIAMOND, 6, Items.DIAMOND));

        register(Items.DIAMOND_CHESTPLATE, 1, true,
            Map.of(1, Items.DIAMOND, 3, Items.DIAMOND,
                   4, Items.DIAMOND, 5, Items.DIAMOND, 6, Items.DIAMOND,
                   7, Items.DIAMOND, 8, Items.DIAMOND, 9, Items.DIAMOND));

        register(Items.DIAMOND_LEGGINGS, 1, true,
            Map.of(1, Items.DIAMOND, 2, Items.DIAMOND, 3, Items.DIAMOND,
                   4, Items.DIAMOND, 6, Items.DIAMOND,
                   7, Items.DIAMOND, 9, Items.DIAMOND));

        register(Items.DIAMOND_BOOTS, 1, true,
            Map.of(4, Items.DIAMOND, 6, Items.DIAMOND,
                   7, Items.DIAMOND, 9, Items.DIAMOND));

        // Golden armor
        register(Items.GOLDEN_HELMET, 1, true,
            Map.of(1, Items.GOLD_INGOT, 2, Items.GOLD_INGOT, 3, Items.GOLD_INGOT,
                   4, Items.GOLD_INGOT, 6, Items.GOLD_INGOT));

        register(Items.GOLDEN_CHESTPLATE, 1, true,
            Map.of(1, Items.GOLD_INGOT, 3, Items.GOLD_INGOT,
                   4, Items.GOLD_INGOT, 5, Items.GOLD_INGOT, 6, Items.GOLD_INGOT,
                   7, Items.GOLD_INGOT, 8, Items.GOLD_INGOT, 9, Items.GOLD_INGOT));

        register(Items.GOLDEN_LEGGINGS, 1, true,
            Map.of(1, Items.GOLD_INGOT, 2, Items.GOLD_INGOT, 3, Items.GOLD_INGOT,
                   4, Items.GOLD_INGOT, 6, Items.GOLD_INGOT,
                   7, Items.GOLD_INGOT, 9, Items.GOLD_INGOT));

        register(Items.GOLDEN_BOOTS, 1, true,
            Map.of(4, Items.GOLD_INGOT, 6, Items.GOLD_INGOT,
                   7, Items.GOLD_INGOT, 9, Items.GOLD_INGOT));

        // Leather armor
        register(Items.LEATHER_HELMET, 1, true,
            Map.of(1, Items.LEATHER, 2, Items.LEATHER, 3, Items.LEATHER,
                   4, Items.LEATHER, 6, Items.LEATHER));

        register(Items.LEATHER_CHESTPLATE, 1, true,
            Map.of(1, Items.LEATHER, 3, Items.LEATHER,
                   4, Items.LEATHER, 5, Items.LEATHER, 6, Items.LEATHER,
                   7, Items.LEATHER, 8, Items.LEATHER, 9, Items.LEATHER));

        register(Items.LEATHER_LEGGINGS, 1, true,
            Map.of(1, Items.LEATHER, 2, Items.LEATHER, 3, Items.LEATHER,
                   4, Items.LEATHER, 6, Items.LEATHER,
                   7, Items.LEATHER, 9, Items.LEATHER));

        register(Items.LEATHER_BOOTS, 1, true,
            Map.of(4, Items.LEATHER, 6, Items.LEATHER,
                   7, Items.LEATHER, 9, Items.LEATHER));

        // ===== UTILITY =====

        register(Items.TORCH, 4, false,
            Map.of(3, Items.COAL,
                   6, Items.STICK));

        // Bow
        register(Items.BOW, 1, true,
            Map.of(2, Items.STICK, 3, Items.STRING,
                   4, Items.STICK, 6, Items.STRING,
                   8, Items.STICK, 9, Items.STRING));

        // Arrows
        register(Items.ARROW, 4, true,
            Map.of(2, Items.FLINT,
                   5, Items.STICK,
                   8, Items.FEATHER));

        // Ladder
        register(Items.LADDER, 3, true,
            Map.of(1, Items.STICK, 3, Items.STICK,
                   4, Items.STICK, 5, Items.STICK, 6, Items.STICK,
                   7, Items.STICK, 9, Items.STICK));

        // Boat
        register(Items.OAK_BOAT, 1, true,
            Map.of(4, Items.OAK_PLANKS, 6, Items.OAK_PLANKS,
                   7, Items.OAK_PLANKS, 8, Items.OAK_PLANKS, 9, Items.OAK_PLANKS));

        // Door
        register(Items.OAK_DOOR, 3, true,
            Map.of(1, Items.OAK_PLANKS, 2, Items.OAK_PLANKS,
                   4, Items.OAK_PLANKS, 5, Items.OAK_PLANKS,
                   7, Items.OAK_PLANKS, 8, Items.OAK_PLANKS));

        // Bed
        register(Items.BED.white(), 1, true,
            Map.of(4, Items.WOOL.white(), 5, Items.WOOL.white(), 6, Items.WOOL.white(),
                   7, Items.OAK_PLANKS, 8, Items.OAK_PLANKS, 9, Items.OAK_PLANKS));

        // Bread
        register(Items.BREAD, 1, true,
            Map.of(7, Items.WHEAT, 8, Items.WHEAT, 9, Items.WHEAT));

        // Paper
        register(Items.PAPER, 3, true,
            Map.of(7, Items.SUGAR_CANE, 8, Items.SUGAR_CANE, 9, Items.SUGAR_CANE));

        register(Items.HOPPER, 1, true,
            Map.of(1, Items.IRON_INGOT, 3, Items.IRON_INGOT,
                   4, Items.IRON_INGOT, 5, Items.CHEST, 6, Items.IRON_INGOT,
                   8, Items.IRON_INGOT));
    }

    private static void register(Item output, int count, boolean needsTable, Map<Integer, Item> pattern) {
        RECIPES.put(output, new Recipe(output, count, pattern, needsTable));
    }

    public static Recipe getRecipe(Item item) {
        return RECIPES.get(item);
    }

    public static boolean hasRecipe(Item item) {
        return RECIPES.containsKey(item);
    }

    /**
     * Convenience overload to check by item name (minecraft id path, e.g. "iron_ingot").
     */
    public static boolean hasRecipe(String itemName) {
        try {
            if (itemName == null || itemName.isEmpty()) return false;
            Identifier id = Identifier.fromNamespaceAndPath("minecraft", itemName);
            Item it = BuiltInRegistries.ITEM.getValue(id);
            if (it == null) return false;
            return hasRecipe(it);
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<Item, Integer> getIngredients(Item item) {
        Recipe r = RECIPES.get(item);
        return r != null ? r.ingredients : Collections.emptyMap();
    }

    public static Collection<Recipe> getAllRecipes() {
        return RECIPES.values();
    }

    public static boolean requires3x3(Item item) {
        Recipe r = RECIPES.get(item);
        if (r == null) return false;
        // 2x2 remappable slots: 5,6,8,9
        Set<Integer> twoByTwo = Set.of(5,6,8,9);
        if (r.pattern.size() > 4) return true;
        for (Integer k : r.pattern.keySet()) {
            if (!twoByTwo.contains(k)) return true;
        }
        return false;
    }

    // ===== TIER DEFINITIONS =====

    public enum Tier {
        WOOD(0, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SWORD),
        STONE(1, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SWORD),
        IRON(2, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SWORD,
             Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
        DIAMOND(3, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SWORD,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
        NETHERITE(4, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SWORD,
                  Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

        public final int level;
        public final Item[] tools;

        Tier(int level, Item... tools) {
            this.level = level;
            this.tools = tools;
        }

        public Tier next() {
            Tier[] values = values();
            int i = ordinal() + 1;
            return i < values.length ? values[i] : this;
        }
    }

    // What pickaxe tier is needed to mine certain blocks
    public static Tier getRequiredTier(Item targetBlock) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetBlock).toString();
        if (id.contains("diamond") || id.contains("emerald") || id.contains("gold") || id.contains("redstone")) return Tier.IRON;
        if (id.contains("iron") || id.contains("lapis") || id.contains("copper")) return Tier.STONE;
        if (id.contains("obsidian") || id.contains("ancient_debris") || id.contains("netherite")) return Tier.DIAMOND;
        return Tier.WOOD;
    }

    // Smelting recipes (ore -> ingot)
    public static final Map<Item, Item> SMELTING = Map.of(
        Items.RAW_IRON, Items.IRON_INGOT,
        Items.RAW_GOLD, Items.GOLD_INGOT,
        Items.RAW_COPPER, Items.COPPER_INGOT,
        Items.IRON_ORE, Items.IRON_INGOT,
        Items.GOLD_ORE, Items.GOLD_INGOT,
        Items.COPPER_ORE, Items.COPPER_INGOT,
        Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP
    );

    public static Item getSmeltResult(Item ore) {
        return SMELTING.get(ore);
    }

    public static Item getOreForIngot(Item ingot) {
        // Prefer RAW_ forms over ORE forms since mining drops raw items
        Item rawForm = null;
        Item oreForm = null;
        for (var entry : SMELTING.entrySet()) {
            if (entry.getValue() == ingot) {
                String keyId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).getPath();
                if (keyId.startsWith("raw_")) {
                    rawForm = entry.getKey();
                } else {
                    oreForm = entry.getKey();
                }
            }
        }
        return rawForm != null ? rawForm : oreForm;
    }

    /**
     * True when this project knows how to obtain the item: a registered block
     * item, a recipe, a smeltable ingot, or a known mining drop. This is a
     * source check, not a claim that the world contains the resource.
     */
    public static boolean hasKnownSource(Item item) {
        if (item == null || item == Items.AIR) return false;
        if (hasRecipe(item)) return true;
        if (getOreForIngot(item) != null) return true;
        // Cooking is a real source: raw food -> cooked food through a smoker or
        // furnace. Without this, maybeQueueCooking's task was rejected by
        // setTask() with "no known source" before it could ever start.
        if (isCookedFood(item)) return true;
        try {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) return false;
            if (BuiltInRegistries.BLOCK.getOptional(id).isPresent()) return true;
            String path = id.getPath();
            return path.endsWith("_ore") || path.endsWith("_log") || path.endsWith("_stem")
                || path.endsWith("_wood") || KNOWN_GATHER_DROPS.contains(path);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isCookedFood(Item item) {
        try {
            return FoodManager.COOKING.containsValue(item);
        } catch (Exception e) {
            return false;
        }
    }

    private static final Set<String> KNOWN_GATHER_DROPS = Set.of(
        "coal", "charcoal", "diamond", "emerald", "lapis_lazuli", "redstone",
        "flint", "raw_iron", "raw_gold", "raw_copper", "quartz", "glowstone_dust"
    );
}
