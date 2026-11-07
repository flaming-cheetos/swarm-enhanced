package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class CraftingManager {

    public enum CraftResult {
        SUCCESS,
        NO_RECIPE,
        MISSING_INGREDIENTS,
        NO_CRAFTING_TABLE,
        TABLE_NOT_OPEN,
        FAILED
    }

    // Check if player has enough materials to craft
    public static boolean canCraft(Item target) {
        RecipeBook.Recipe recipe = RecipeBook.getRecipe(target);
        if (recipe == null) return false;

        Map<Item, Integer> needed = recipe.ingredients;
        for (var entry : needed.entrySet()) {
            int have = countInInventory(entry.getKey());
            if (have < entry.getValue()) return false;
        }
        return true;
    }

    // Count how many of an item are in player inventory
    public static int countInInventory(Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        // Fallback approach: iterate all inventory slots
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // Find a crafting table in the world near the player
    public static BlockPos findCraftingTable(int range) {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();

        // Search in a 45x21x45 volume (y limited to +/-10)
        for (int x = -range; x <= range; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    // Check if crafting screen is currently open
    public static boolean isCraftingOpen() {
        return mc.player != null &&
               mc.player.containerMenu instanceof CraftingMenu;
    }

    // Open a crafting table at the given position
    public static boolean openCraftingTable(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return false;

        try {
            mc.player.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(pos)
            );
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // NOTE: Tick-driven crafting helpers. The actual per-tick placement is
    // driven from BotBrain; do not perform blocking actions here.

    public static boolean hasIngredientInInventory(
            Item item, net.minecraft.world.inventory.AbstractContainerMenu handler,
            int skipSlots) {
        if (handler == null) return false;
        for (net.minecraft.world.inventory.Slot slot : handler.slots) {
            try {
                if (slot.index < skipSlots) continue;
                net.minecraft.world.item.ItemStack s = slot.getItem();
                if (!s.isEmpty() && s.getItem() == item) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static int findIngredientSlot(
            Item item, net.minecraft.world.inventory.AbstractContainerMenu handler,
            int skipSlots) {
        if (handler == null) return -1;
        for (net.minecraft.world.inventory.Slot slot : handler.slots) {
            try {
                if (slot.index < skipSlots) continue;
                net.minecraft.world.item.ItemStack s = slot.getItem();
                if (!s.isEmpty() && s.getItem() == item) return slot.index;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public static boolean gridSlotHasItem(
            Item item, net.minecraft.world.inventory.AbstractContainerMenu handler,
            int gridSlot) {
        if (handler == null) return false;
        try {
            net.minecraft.world.item.ItemStack stack = handler.getSlot(gridSlot).getItem();
            return !stack.isEmpty() && stack.getItem() == item;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean outputReady(
            net.minecraft.world.inventory.AbstractContainerMenu handler) {
        if (handler == null) return false;
        try {
            return !handler.getSlot(0).getItem().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // (old helper removed - new doCraft handles player vs table handlers)

    // Close the crafting screen
    public static void closeCrafting() {
        if (mc.player != null) {
            mc.setScreenAndShow(null);
        }
    }

    // Place a crafting table from inventory
    public static boolean placeCraftingTable() {
        if (mc.player == null || mc.level == null) return false;

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.CRAFTING_TABLE) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        try {
            mc.player.getInventory().setSelectedSlot(slot);
        } catch (Exception e) {
            return false;
        }

        // Look down and place
        BlockPos placePos = mc.player.blockPosition().offset(1, 0, 0);
        try {
            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(placePos.below()).add(0, 0.5, 0),
                Direction.UP, placePos.below(), false
            );
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        } catch (Exception e) {
            try {
                mc.player.getInventory().setSelectedSlot(prev);
            } catch (Exception ignored) {
            }
            return false;
        }

        try {
            mc.player.getInventory().setSelectedSlot(prev);
        } catch (Exception ignored) {
        }
        return true;
    }
}
