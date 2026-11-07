package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SmeltingManager {

    public static boolean isFurnaceOpen() {
        return mc.player != null &&
               mc.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler;
    }

    public static boolean openFurnace(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return false;
        try {
            mc.player.lookAt(
                net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                Vec3d.ofCenter(pos)
            );
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) { return false; }
    }

    // Deposit ore into furnace input slot
    public static boolean depositOre(Item ore) {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;

        int slot = findItemInInventorySlots(handler, ore);
        if (slot == -1) return false;

        InvUtils.move().fromId(slot).toId(0);
        return true;
    }

    // Deposit fuel into furnace fuel slot
    public static boolean depositFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;

        // Check if fuel slot already has fuel
        if (!handler.slots.get(1).getStack().isEmpty()) return true;

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
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;

        ItemStack result = handler.slots.get(2).getStack();
        if (result.isEmpty()) return false;

        InvUtils.shiftClick().slotId(2);
        return true;
    }

    // Check if furnace has fuel remaining
    public static boolean hasFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;

        return handler.getFuelProgress() > 0 ||
               !handler.slots.get(1).getStack().isEmpty();
    }

    // Count fuel in player inventory
    public static int countFuel() {
        int count = 0;
        if (mc.player == null) return 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.COAL || stack.getItem() == Items.CHARCOAL) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int findItemInInventorySlots(AbstractFurnaceScreenHandler handler, Item item) {
        // Furnace: slots 0-2 are furnace, 3+ are player inventory
        for (int i = 3; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).getStack().getItem() == item &&
                !handler.slots.get(i).getStack().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void closeFurnace() {
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
    }

    public static boolean hasInput() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;
        return !handler.slots.get(0).getStack().isEmpty();
    }

    public static int getOutputCount() {
        if (!isFurnaceOpen()) return 0;
        AbstractFurnaceScreenHandler handler =
            (AbstractFurnaceScreenHandler) mc.player.currentScreenHandler;
        return handler.slots.get(2).getStack().getCount();
    }
}
