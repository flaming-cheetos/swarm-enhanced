package com.rdh.swarm.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PlacementHelper {
    private static final Map<BlockPos, Long> lastAttempt = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> attempts = new ConcurrentHashMap<>();
    private static final long MIN_DELAY_MS = 300; // minimum delay between attempts
    private static final int MAX_ATTEMPTS = 6; // before fallback search

public static boolean placeBlockSafely(BlockPos target, net.minecraft.world.item.Item requiredItem) {
        if (mc.player == null || mc.level == null || target == null) return false;

        long now = System.currentTimeMillis();
        long last = lastAttempt.getOrDefault(target, 0L);
        if (now - last < MIN_DELAY_MS) return false;

        // quick occupancy/reach checks
        var state = mc.level.getBlockState(target);
        boolean replaceable = state.isAir() || mc.level.getBlockState(target).getCollisionShape(mc.level, target).isEmpty();
        if (!replaceable) {
            ChatUtils.infoPrefix("BotBrain", "Invalid placement: block already occupied at %d %d %d", target.getX(), target.getY(), target.getZ());
            return false;
        }

        // Find support block and placement face (same logic)
        BlockPos support = null;
        Direction face = null;

        BlockPos below = target.below();
        if (below != null) {
            var bs = mc.level.getBlockState(below);
            boolean solidBelow = !bs.isAir() && !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty();
            if (solidBelow) {
                support = below;
                face = Direction.UP;
            }
        }

        if (support == null) {
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                BlockPos adj = target.relative(d);
                var bs = mc.level.getBlockState(adj);
                boolean solid = !bs.isAir() && !mc.level.getBlockState(adj).getCollisionShape(mc.level, adj).isEmpty();
                if (solid) {
                    support = adj;
                    face = d.getOpposite();
                    break;
                }
            }
        }

        if (support == null) {
            ChatUtils.infoPrefix("BotBrain", "Invalid placement: no support block at %d %d %d — searching fallback", target.getX(), target.getY(), target.getZ());
            for (int dx = -2; dx <= 2 && support == null; dx++) {
                for (int dy = -1; dy <= 1 && support == null; dy++) {
                    for (int dz = -2; dz <= 2 && support == null; dz++) {
                        BlockPos cand = target.offset(dx, dy, dz);
                        if (cand.equals(target)) continue;
                        var cs = mc.level.getBlockState(cand);
                        boolean candReplaceable = cs.isAir() || mc.level.getBlockState(cand).getCollisionShape(mc.level, cand).isEmpty();
                        if (!candReplaceable) continue;
                        BlockPos candBelow = cand.below();
                        var bbs = mc.level.getBlockState(candBelow);
                        boolean solid = !bbs.isAir() && !mc.level.getBlockState(candBelow).getCollisionShape(mc.level, candBelow).isEmpty();
                        if (solid) { support = candBelow; face = Direction.UP; target = cand; break; }
                        for (Direction d : Direction.values()) {
                            if (d == Direction.UP) continue;
                            BlockPos adj = cand.relative(d);
                            var as = mc.level.getBlockState(adj);
                            boolean s = !as.isAir() && !mc.level.getBlockState(adj).getCollisionShape(mc.level, adj).isEmpty();
                            if (s) { support = adj; face = d.getOpposite(); target = cand; break; }
                        }
                    }
                }
            }
            if (support == null) {
                ChatUtils.infoPrefix("BotBrain", "Invalid placement: no fallback found near %d %d %d", target.getX(), target.getY(), target.getZ());
                lastAttempt.put(target, now);
                attempts.put(target, attempts.getOrDefault(target, 0) + 1);
                return false;
            }
        }

        // Find EXACT required item in hotbar
        FindItemResult hot = InvUtils.findInHotbar(requiredItem);
        if (!hot.found()) {
            ChatUtils.infoPrefix("BotBrain", "Required item %s not in hotbar for placement at %d %d %d", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), target.getX(), target.getY(), target.getZ());
            return false;
        }

        // Swap to hotbar slot
        InvUtils.swap(hot.slot(), true);

        // Verify main hand
        if (mc.player.getMainHandItem().getItem() != requiredItem) {
            ChatUtils.infoPrefix("BotBrain", "Failed to select %s in main hand for placement at %d %d %d", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), target.getX(), target.getY(), target.getZ());
            return false;
        }

        // Enforce attempt counts
        int cnt = attempts.getOrDefault(target, 0) + 1;
        attempts.put(target, cnt);
        lastAttempt.put(target, now);
        if (cnt > MAX_ATTEMPTS) {
            ChatUtils.infoPrefix("BotBrain", "Placement failed (attempt %d) — triggering extended fallback for %d %d %d", cnt, target.getX(), target.getY(), target.getZ());
        }

        // Build hit result
        Vec3 hitVec = Vec3.atCenterOf(support).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(hitVec, face != null ? face : Direction.UP, support, false);

        // Rotate and place
        double yaw = Rotations.getYaw(hitVec);
        double pitch = Rotations.getPitch(hitVec);
        ChatUtils.infoPrefix("BotBrain", "Rotating to place %s at %d %d %d", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), target.getX(), target.getY(), target.getZ());

        final BlockPos placePos = target;
        final FindItemResult itemHot = hot;

        final boolean[] result = {false};
        Rotations.rotate(yaw, pitch, 50, () -> {
            try {
                boolean placed = BlockUtils.place(placePos, itemHot, true, 50, true);
                if (placed) {
                    ChatUtils.infoPrefix("BotBrain", "Placed %s at %d %d %d", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), placePos.getX(), placePos.getY(), placePos.getZ());
                    attempts.remove(placePos);
                    lastAttempt.remove(placePos);
                    result[0] = true;
                } else {
                    ChatUtils.infoPrefix("BotBrain", "BlockUtils.place failed for %s at %d %d %d", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), placePos.getX(), placePos.getY(), placePos.getZ());
                    result[0] = false;
                }
            } catch (Throwable t) {
                ChatUtils.infoPrefix("BotBrain", "Placement exception for %s: %s", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requiredItem), t.getMessage());
                result[0] = false;
            }
        });

        return result[0];
    }

    public static boolean placeBlockSafely(BlockPos target) {
        // Legacy method - use generic BlockItem search for compatibility
        // Delegate to old logic or new with null check if needed
        // For now, call with dummy item to avoid breaking callers, but log warning
        ChatUtils.warningPrefix("BotBrain", "Legacy placeBlockSafely called - prefer exact Item overload");
        // Fallback: use first BlockItem (existing behavior)
        FindItemResult hot = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof net.minecraft.world.item.BlockItem);
        if (!hot.found()) return false;
        net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(hot.slot());
        net.minecraft.world.item.Item item = stack.getItem();
        return placeBlockSafely(target, item);
    }
}
