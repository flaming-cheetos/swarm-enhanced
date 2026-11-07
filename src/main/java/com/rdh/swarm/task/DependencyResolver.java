package com.rdh.swarm.task;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DependencyResolver {

    public static class ExecutionPlan {
        public final List<BotTask> steps;
        public final Map<Item, Integer> gatherList; // raw materials to gather
        public final List<BotTask> craftPlan; // ordered craft steps (dependencies first)
        public final RecipeBook.Tier requiredTier;

        public ExecutionPlan(List<BotTask> steps, Map<Item, Integer> gatherList, List<BotTask> craftPlan, RecipeBook.Tier requiredTier) {
            this.steps = steps;
            this.gatherList = gatherList;
            this.craftPlan = craftPlan;
            this.requiredTier = requiredTier;
        }
    }

    public static ExecutionPlan resolve(Item targetItem, int quantity) {
        // totalNeeded accumulates gross needs (no inventory subtraction)
        Map<Item, Integer> totalNeeded = new HashMap<>();
        Map<Item, Integer> craftsAccounted = new HashMap<>();

        // seed
        totalNeeded.put(targetItem, quantity);
        ArrayDeque<Item> q = new ArrayDeque<>();
        q.add(targetItem);

        // propagate requirements down the recipe graph until fixed point
        boolean repeat;
        boolean needs3x3 = false;
        do {
            repeat = false;
            while (!q.isEmpty()) {
                Item item = q.poll();
                int needed = totalNeeded.getOrDefault(item, 0);
                if (needed <= 0) continue;

                if (RecipeBook.hasRecipe(item)) {
                    RecipeBook.Recipe recipe = RecipeBook.getRecipe(item);
                    int output = Math.max(1, recipe.outputCount);
                    int craftsNeeded = (int) Math.ceil((double) needed / output);
                    int prevCrafts = craftsAccounted.getOrDefault(item, 0);
                    if (craftsNeeded > prevCrafts) {
                        int delta = craftsNeeded - prevCrafts;
                        craftsAccounted.put(item, craftsNeeded);
                        for (var e : recipe.ingredients.entrySet()) {
                            Item ing = e.getKey();
                            int per = e.getValue();
                            int add = per * delta;
                            int prev = totalNeeded.getOrDefault(ing, 0);
                            totalNeeded.put(ing, prev + add);
                            q.add(ing);
                        }
                    }
                } else {
                    // no recipe -> raw material, nothing to propagate
                }
            }

// Detect if any craftable item requires a 3x3 table; if so, we will ensure an extra log is gathered (fallback for crafting table)
        for (Item it : craftsAccounted.keySet()) {
            if (RecipeBook.requires3x3(it)) { needs3x3 = true; break; }
        }

        // If we need a 3x3 table, we also need 4 planks for the table itself.
        // Make sure the plank craft count includes that, since the table is not in the craft plan.
        if (needs3x3) {
            int prevPlanks = totalNeeded.getOrDefault(Items.OAK_PLANKS, 0);
            totalNeeded.put(Items.OAK_PLANKS, prevPlanks + 4);

            RecipeBook.Recipe planksRecipe = RecipeBook.getRecipe(Items.OAK_PLANKS);
            if (planksRecipe != null) {
                int output = Math.max(1, planksRecipe.outputCount);
                int craftsForPlanks = (int) Math.ceil((double) (prevPlanks + 4) / output);
                int prevCrafts = craftsAccounted.getOrDefault(Items.OAK_PLANKS, 0);
                if (craftsForPlanks > prevCrafts) {
                    craftsAccounted.put(Items.OAK_PLANKS, craftsForPlanks);
                }
            }
        }

        // Note: We do NOT add a crafting_table craft step here. Table placement is handled by the BotBrain prep flow.
        } while (repeat);

        // Subtract inventory from totals to get final needs
        Map<Item, Integer> finalNeeded = new HashMap<>();
        for (var entry : totalNeeded.entrySet()) {
            Item it = entry.getKey();
            int gross = entry.getValue();
            int have = CraftingManager.countInInventory(it);
            int net = Math.max(0, gross - have);
            if (net > 0) finalNeeded.put(it, net);
        }

        // Build craft plan (dependencies first) using topological sort
        List<BotTask> craftPlan = new ArrayList<>();
        // craftsAccounted currently holds crafts needed for items (from propagation)
        // Ensure we compute craftsNeeded for finalNeeded items as well
        Map<Item, Integer> craftsNeeded = new HashMap<>();
        for (var entry : craftsAccounted.entrySet()) craftsNeeded.put(entry.getKey(), entry.getValue());
        // Also ensure any craftable item present in finalNeeded has crafts computed
        for (var entry : finalNeeded.entrySet()) {
            Item it = entry.getKey();
            if (RecipeBook.hasRecipe(it) && !craftsNeeded.containsKey(it)) {
                RecipeBook.Recipe recipe = RecipeBook.getRecipe(it);
                int crafts = (int) Math.ceil((double) entry.getValue() / Math.max(1, recipe.outputCount));
                craftsNeeded.put(it, crafts);
            }
        }

        // Build dependency graph between craftable items
        Map<Item, Set<Item>> deps = new HashMap<>();
        Map<Item, Integer> indegree = new HashMap<>();
        for (Item node : craftsNeeded.keySet()) {
            deps.put(node, new HashSet<>());
            indegree.put(node, 0);
        }
        for (Item node : craftsNeeded.keySet()) {
            RecipeBook.Recipe recipe = RecipeBook.getRecipe(node);
            if (recipe == null) continue;
            for (var ing : recipe.ingredients.keySet()) {
                Item ingItem = ing;
                if (craftsNeeded.containsKey(ingItem)) {
                    // ingItem -> node
                    deps.get(ingItem).add(node);
                    indegree.put(node, indegree.getOrDefault(node, 0) + 1);
                }
            }
        }

        // Kahn's algorithm
        ArrayDeque<Item> queue = new ArrayDeque<>();
        for (var e : indegree.entrySet()) if (e.getValue() == 0) queue.add(e.getKey());
        List<Item> topo = new ArrayList<>();
        while (!queue.isEmpty()) {
            Item n = queue.poll();
            topo.add(n);
            for (Item m : deps.getOrDefault(n, Collections.emptySet())) {
                indegree.put(m, indegree.get(m) - 1);
                if (indegree.get(m) == 0) queue.add(m);
            }
        }

        // If there are cycles (shouldn't), just append remaining
        for (Item k : craftsNeeded.keySet()) if (!topo.contains(k)) topo.add(k);

        // Build craftPlan in topo order
        for (Item it : topo) {
            int count = craftsNeeded.getOrDefault(it, 0);
            for (int i = 0; i < count; i++) craftPlan.add(new BotTask(BotTask.Type.CRAFT, it, 1));
        }

        // Build gather list: items with positive finalNeeded and no recipe (or ore for smelt)
        Map<Item, Integer> gatherList = new HashMap<>();
        List<BotTask> smeltPlan = new ArrayList<>();
        for (var entry : finalNeeded.entrySet()) {
            Item it = entry.getKey();
            int qty = entry.getValue();
            if (RecipeBook.hasRecipe(it)) continue; // crafted, not gathered
            // If this item is an ingot that must be smelted, gather ore instead and add SMELT step
            Item ore = RecipeBook.getOreForIngot(it);
            if (ore != null) {
                // gather ore instead of ingot
                int prev = gatherList.getOrDefault(ore, 0);
                gatherList.put(ore, prev + qty);
                // add fuel requirement: ceil(qty / fuelPower)
                int coalNeeded = (int) Math.ceil((double) qty / Math.max(1, RecipeBook.getFuelPower(Items.COAL)));
                if (coalNeeded > 0) gatherList.merge(Items.COAL, coalNeeded, Integer::sum);
                // create SMELT task (output = it, source = ore)
                java.util.Map<String, Object> data = java.util.Map.of("item", it, "quantity", Integer.valueOf(qty), "source", ore);
                smeltPlan.add(new BotTask(java.util.UUID.randomUUID(), BotTask.Type.SMELT, 0, data));
            } else {
                gatherList.put(it, gatherList.getOrDefault(it, 0) + qty);
            }
        }

        // If we have any smelt steps, ensure we gather at least 8 stone so a furnace can be crafted/placed
        if (!smeltPlan.isEmpty()) {
            int haveStone = gatherList.getOrDefault(Items.STONE, 0);
            if (haveStone < 8) gatherList.put(Items.STONE, 8);
        }

        // If any craft step needs a 3x3 table, ensure we have enough logs to
        // craft a table plus still have a buffer of planks for the recipe.
        // 3 logs -> 12 planks (4 for table, 8 buffer).
        if (needs3x3) {
            int prev = gatherList.getOrDefault(Items.OAK_LOG, 0);
            gatherList.put(Items.OAK_LOG, prev + 3);
        }

        // Convert gather aliases where the desired item is produced by mining a different block
        // Cobblestone drops from stone — mine stone instead
        if (gatherList.containsKey(Items.COBBLESTONE)) {
            int amount = gatherList.remove(Items.COBBLESTONE);
            gatherList.merge(Items.STONE, amount, Integer::sum);
        }

        // Flint drops from gravel — mine gravel instead
        if (gatherList.containsKey(Items.FLINT)) {
            int amount = gatherList.remove(Items.FLINT);
            gatherList.merge(Items.GRAVEL, amount, Integer::sum);
        }

        // Debug output
        System.out.println("=== RESOLVER OUTPUT ===");
        System.out.println("Target: " + targetItem);
        System.out.println("needs3x3: " + needs3x3);
        for (var entry : gatherList.entrySet()) {
            System.out.println("  GATHER: " + Registries.ITEM.getId(entry.getKey()) + " = " + entry.getValue());
        }
        for (int i = 0; i < craftPlan.size(); i++) {
            BotTask s = craftPlan.get(i);
            System.out.println("  CRAFT[" + i + "]: " + s.getItemName() + " x" + s.getTargetQuantity());
        }
        System.out.println("=======================");

        // Debug gather map after alias replacements
        System.out.println("GATHER AFTER ALIAS: " + gatherList);

        // Build legacy steps list for compatibility: gather tasks first, then smeltPlan, then craftPlan (dependencies first)
        List<BotTask> steps = new ArrayList<>();
        for (var entry : gatherList.entrySet()) steps.add(new BotTask(BotTask.Type.GATHER, entry.getKey(), entry.getValue()));
        // Add smelt steps after gathers and before crafts
        steps.addAll(smeltPlan);
        steps.addAll(craftPlan);

        return new ExecutionPlan(steps, gatherList, craftPlan, RecipeBook.getRequiredTier(targetItem));
    }

    public static RecipeBook.Tier getCurrentTier() {
        if (mc.player == null) return RecipeBook.Tier.WOOD;

        if (hasInInventory(Items.NETHERITE_PICKAXE)) return RecipeBook.Tier.NETHERITE;
        if (hasInInventory(Items.DIAMOND_PICKAXE)) return RecipeBook.Tier.DIAMOND;
        if (hasInInventory(Items.IRON_PICKAXE)) return RecipeBook.Tier.IRON;
        if (hasInInventory(Items.STONE_PICKAXE)) return RecipeBook.Tier.STONE;
        return RecipeBook.Tier.WOOD;
    }

    public static List<BotTask> getEquipmentTasks(RecipeBook.Tier targetTier) {
        List<BotTask> tasks = new ArrayList<>();
        RecipeBook.Tier current = getCurrentTier();

        RecipeBook.Tier[] tiers = RecipeBook.Tier.values();
        for (int i = current.level + 1; i <= targetTier.level && i < tiers.length; i++) {
            RecipeBook.Tier tier = tiers[i];
            for (Item tool : tier.tools) {
                if (!hasInInventory(tool)) {
                    ExecutionPlan plan = resolve(tool, 1);
                    tasks.addAll(plan.steps);
                }
            }
        }

        return tasks;
    }

    public static boolean hasInInventory(Item item) {
        return CraftingManager.countInInventory(item) > 0;
    }

    private static boolean isMineable(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        return id.contains("ore") || id.contains("stone") || id.contains("cobblestone") ||
               id.contains("coal") || id.contains("diamond") || id.contains("emerald") ||
               id.contains("lapis") || id.contains("redstone") || id.contains("copper") ||
               id.contains("gold") || id.contains("iron") || id.contains("ancient_debris") ||
               id.contains("gravel") || id.contains("sand") || id.contains("dirt") ||
               id.contains("clay") || id.contains("obsidian") || id.contains("netherrack");
    }

    private static boolean isLog(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        return id.contains("log") || id.contains("wood");
    }
}
