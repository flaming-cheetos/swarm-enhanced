package com.rdh.swarm.task;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Food inventory accounting and the raw-food cooking map. */
public final class FoodManager {
    private FoodManager() {}

    /** Raw food -> cooked result. Kelp and potato have non-obvious results. */
    public static final Map<Item, Item> COOKING = Map.ofEntries(
        Map.entry(Items.BEEF, Items.COOKED_BEEF),
        Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
        Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
        Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
        Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
        Map.entry(Items.COD, Items.COOKED_COD),
        Map.entry(Items.SALMON, Items.COOKED_SALMON),
        Map.entry(Items.POTATO, Items.BAKED_POTATO),
        Map.entry(Items.KELP, Items.DRIED_KELP)
    );

    /** Raw foods that are safe to eat uncooked when no cooked food is available. */
    public static final Set<Item> SAFE_RAW = Set.of(
        Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.RABBIT, Items.COD, Items.SALMON
    );

    private static final Map<Item, Item> COOKED_TO_RAW = new LinkedHashMap<>();
    static {
        for (Map.Entry<Item, Item> entry : COOKING.entrySet()) COOKED_TO_RAW.put(entry.getValue(), entry.getKey());
    }

    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }

    public static int countFood() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isFood(stack)) count += stack.getCount();
        }
        return count;
    }

    public static int cookedCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFood(stack)) continue;
            Item item = stack.getItem();
            if (COOKED_TO_RAW.containsKey(item)
                || item == Items.BREAD || item == Items.BAKED_POTATO || item == Items.DRIED_KELP
                || item == Items.GOLDEN_APPLE || item == Items.GOLDEN_CARROT
                || item == Items.COOKED_COD || item == Items.COOKED_SALMON) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static int countRaw(Item item) {
        if (mc.player == null || item == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    public static boolean isCookable(Item item) {
        return item != null && COOKING.containsKey(item);
    }

    public static Item cookedResult(Item item) {
        return item == null ? null : COOKING.get(item);
    }
}
