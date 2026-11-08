package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Smithing-table slot handling for diamond-to-netherite upgrades. */
public final class SmithingManager {
    private SmithingManager() {}

    /** Upgrade order: armor first, then tools. */
    public static final Map<Item, Item> UPGRADES = new LinkedHashMap<>();
    static {
        UPGRADES.put(Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
        UPGRADES.put(Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        UPGRADES.put(Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
        UPGRADES.put(Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
        UPGRADES.put(Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        UPGRADES.put(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
        UPGRADES.put(Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        UPGRADES.put(Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL);
        UPGRADES.put(Items.DIAMOND_HOE, Items.NETHERITE_HOE);
    }

    public static boolean isUpgradeable(Item base) { return base != null && UPGRADES.containsKey(base); }

    public static Item upgradeResult(Item base) { return base == null ? null : UPGRADES.get(base); }

    public static boolean isSmithingOpen() {
        return mc.player != null && mc.player.containerMenu instanceof SmithingMenu;
    }

    public static boolean openSmithingTable(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return false;
        try {
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean ensureTemplate() {
        return insert(SmithingMenu.TEMPLATE_SLOT, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
    }

    public static boolean ensureBase(Item base) {
        return insert(SmithingMenu.BASE_SLOT, base);
    }

    public static boolean ensureIngot() {
        return insert(SmithingMenu.ADDITIONAL_SLOT, Items.NETHERITE_INGOT);
    }

    private static boolean insert(int menuSlot, Item item) {
        if (!isSmithingOpen() || item == null) return false;
        SmithingMenu menu = (SmithingMenu) mc.player.containerMenu;
        ItemStack current = menu.slots.get(menuSlot).getItem();
        if (!current.isEmpty()) return current.getItem() == item;
        int source = findInventorySlot(menu, item);
        if (source == -1) return false;
        InvUtils.move().fromId(source).toId(menuSlot);
        return true;
    }

    public static boolean resultReady() {
        if (!isSmithingOpen()) return false;
        return !((SmithingMenu) mc.player.containerMenu).slots.get(SmithingMenu.RESULT_SLOT).getItem().isEmpty();
    }

    public static boolean takeResult() {
        if (!resultReady()) return false;
        InvUtils.shiftClick().slotId(SmithingMenu.RESULT_SLOT);
        return true;
    }

    private static int findInventorySlot(AbstractContainerMenu menu, Item item) {
        // Slots 0-3 are template/base/additional/result; player inventory starts at 4.
        for (int i = 4; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    public static void close() {
        try {
            if (mc.player != null) mc.setScreenAndShow(null);
        } catch (Exception ignored) {}
    }
}
