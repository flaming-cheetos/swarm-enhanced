package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // Find a crafting table in the world near the player
    public static BlockPos findCraftingTable(int range) {
        if (mc.player == null || mc.world == null) return null;
        BlockPos playerPos = mc.player.getBlockPos();

        // Search in a 45x21x45 volume (y limited to +/-10)
        for (int x = -range; x <= range; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE) {
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
               mc.player.currentScreenHandler instanceof CraftingScreenHandler;
    }

    // Open a crafting table at the given position
    public static boolean openCraftingTable(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return false;

        try {
            mc.player.lookAt(
                net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                Vec3d.ofCenter(pos)
            );
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // NOTE: Tick-driven crafting helpers. The actual per-tick placement is
    // driven from BotBrain; do not perform blocking actions here.

    public static boolean hasIngredientInInventory(
            Item item, net.minecraft.screen.ScreenHandler handler,
            int skipSlots) {
        if (handler == null) return false;
        for (net.minecraft.screen.slot.Slot slot : handler.slots) {
            try {
                if (slot.id < skipSlots) continue;
                net.minecraft.item.ItemStack s = slot.getStack();
                if (!s.isEmpty() && s.getItem() == item) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static int findIngredientSlot(
            Item item, net.minecraft.screen.ScreenHandler handler,
            int skipSlots) {
        if (handler == null) return -1;
        for (net.minecraft.screen.slot.Slot slot : handler.slots) {
            try {
                if (slot.id < skipSlots) continue;
                net.minecraft.item.ItemStack s = slot.getStack();
                if (!s.isEmpty() && s.getItem() == item) return slot.id;
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public static boolean gridSlotHasItem(
            Item item, net.minecraft.screen.ScreenHandler handler,
            int gridSlot) {
        if (handler == null) return false;
        try {
            net.minecraft.item.ItemStack stack = handler.getSlot(gridSlot).getStack();
            return !stack.isEmpty() && stack.getItem() == item;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean outputReady(
            net.minecraft.screen.ScreenHandler handler) {
        if (handler == null) return false;
        try {
            return !handler.getSlot(0).getStack().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // (old helper removed - new doCraft handles player vs table handlers)

    // Close the crafting screen
    public static void closeCrafting() {
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        }
    }

    // Place a crafting table from inventory
    public static boolean placeCraftingTable() {
        if (mc.player == null || mc.world == null) return false;

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.CRAFTING_TABLE) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        try {
            java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            f.setAccessible(true);
            f.setInt(mc.player.getInventory(), slot);
        } catch (Exception e) {
            return false;
        }

        // Look down and place
        BlockPos placePos = mc.player.getBlockPos().add(1, 0, 0);
        try {
            BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(placePos.down()).add(0, 0.5, 0),
                Direction.UP, placePos.down(), false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
                f.setAccessible(true);
                f.setInt(mc.player.getInventory(), prev);
            } catch (Exception ignored) {
            }
            return false;
        }

        try {
            java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            f.setAccessible(true);
            f.setInt(mc.player.getInventory(), prev);
        } catch (Exception ignored) {
        }
        return true;
    }
}
