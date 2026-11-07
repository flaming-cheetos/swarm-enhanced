package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SmeltingManager {

    public static boolean isFurnaceOpen() {
        return mc.player != null &&
               mc.player.containerMenu instanceof AbstractFurnaceMenu;
    }

    public static boolean openFurnace(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return false;
        try {
            mc.player.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(pos)
            );
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) { return false; }
    }

    // Deposit ore into furnace input slot
    public static boolean depositOre(Item ore) {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        int slot = findItemInInventorySlots(handler, ore);
        if (slot == -1) return false;

        InvUtils.move().fromId(slot).toId(0);
        return true;
    }

    // Deposit fuel into furnace fuel slot
    public static boolean depositFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        // Check if fuel slot already has fuel
        if (!handler.slots.get(1).getItem().isEmpty()) return true;

        // Find coal or charcoal
        int slot = findItemInInventorySlots(handler, Items.COAL);
        if (slot == -1) slot = findItemInInventorySlots(handler, Items.CHARCOAL);
        if (slot == -1) return false;

        InvUtils.move().fromId(slot).toId(1);
        return true;
    }

    // Take result from furnace output slot
    public static boolean takeResult() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        ItemStack result = handler.slots.get(2).getItem();
        if (result.isEmpty()) return false;

        InvUtils.shiftClick().slotId(2);
        return true;
    }

    // Check if furnace has fuel remaining
    public static boolean hasFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        return handler.getLitProgress() > 0 ||
               !handler.slots.get(1).getItem().isEmpty();
    }

    // Count fuel in player inventory
    public static int countFuel() {
        int count = 0;
        if (mc.player == null) return 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.COAL || stack.getItem() == Items.CHARCOAL) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int findItemInInventorySlots(AbstractFurnaceMenu handler, Item item) {
        // Furnace: slots 0-2 are furnace, 3+ are player inventory
        for (int i = 3; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).getItem().getItem() == item &&
                !handler.slots.get(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void closeFurnace() {
        if (mc.player != null) {
            mc.setScreenAndShow(null);
        }
    }

    public static boolean hasInput() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return !handler.slots.get(0).getItem().isEmpty();
    }

    public static int getOutputCount() {
        if (!isFurnaceOpen()) return 0;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return handler.slots.get(2).getItem().getCount();
    }
}
