package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SmeltingManager {

    /** Raw food -> cooked result; the existing furnace states consume the raw input. */
    public static final java.util.Map<Item, Item> COOKING = java.util.Map.copyOf(FoodManager.COOKING);

    public static boolean isCookable(Item item) {
        return item != null && COOKING.containsKey(item);
    }

    public static Item cookedResult(Item item) {
        return item == null ? null : COOKING.get(item);
    }

    public static boolean depositFood(Item raw) {
        return isCookable(raw) && depositOre(raw);
    }

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
        if (isFuel(handler.slots.get(1).getItem())) {
            ItemStack existing = handler.slots.get(1).getItem();
            ChatUtils.infoPrefix("Smelting", "FUEL: %s x%d = %d ticks in furnace",
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(existing.getItem()), existing.getCount(), burnTicks(existing));
            return true;
        }

        int slot = findFuelInInventorySlots(handler);
        if (slot == -1) {
            ChatUtils.infoPrefix("Smelting", "FUEL: none — no valid fuel in inventory or furnace");
            return false;
        }
        ItemStack fuel = handler.slots.get(slot).getItem();
        ChatUtils.infoPrefix("Smelting", "FUEL: %s x%d = %d ticks available",
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(fuel.getItem()), fuel.getCount(), burnTicks(fuel));
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

        return handler.getLitProgress() > 0 || isFuel(handler.slots.get(1).getItem());
    }

    private static boolean isFuel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.COOKING_FUEL);
    }

    private static int burnTicks(ItemStack stack) {
        if (!isFuel(stack)) return 0;
        try {
            return stack.get(DataComponents.COOKING_FUEL).burnTime().get(null, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Fuel already sitting in a furnace, readable without opening the screen. */
    public static ItemStack fuelStackAt(BlockPos pos) {
        if (pos == null || mc.level == null) return ItemStack.EMPTY;
        if (mc.level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(1);
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasFuelAt(BlockPos pos) {
        return isFuel(fuelStackAt(pos));
    }

    // Count fuel in player inventory
    public static int countFuel() {
        int count = 0;
        if (mc.player == null) return 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isFuel(stack)) {
                count += burnTicks(stack);
            }
        }
        return count;
    }

    /** Human-readable fuel inventory for the cook decision log. */
    public static String inventoryFuelSummary() {
        if (mc.player == null) return "none";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFuel(stack)) continue;
            if (out.length() > 0) out.append(',');
            out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
               .append(" x").append(stack.getCount()).append(" = ").append(burnTicks(stack)).append(" ticks");
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static int findFuelInInventorySlots(AbstractFurnaceMenu handler) {
        for (int i = 3; i < handler.slots.size(); i++) {
            if (isFuel(handler.slots.get(i).getItem())) return i;
        }
        return -1;
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
