package com.rdh.swarm.task;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Collects nearby item entities in bounded clusters after a mining or combat
 * work unit. It never waits for despawn and never turns into a general loot
 * walk: at most three clusters, one path each.
 */
public final class DropCollector {
    private static final int MAX_CLUSTERS = 3;
    private static final long BLACKLIST_MS = 30_000L;
    private static final long MAX_PATH_MS = 10_000L;
    private static final double CLUSTER_RADIUS_SQ = 9.0D;
    private static final double VANILLA_PICKUP_SQ = 2.25D;

    private enum Phase { SCAN, PATH, WAIT, DONE }

    private final Supplier<Boolean> enabled;
    private final Supplier<Double> radius;
    private final Supplier<Integer> waitMs;
    private final Runnable afterSweep;

    private boolean active;
    private Phase phase = Phase.DONE;
    private long phaseStarted;
    private long waitUntil;
    private int delayTicks;
    private int pathAttempts;
    private int clusters;
    private int totalSeen;
    private boolean skippedLogged;
    private boolean loggedInventoryFull;
    private boolean wasBaritoneActive;
    private BlockPos firstCluster;
    private final Set<String> collected = new LinkedHashSet<>();
    private final Set<String> clusterIds = new LinkedHashSet<>();
    private final Map<UUID, Long> blacklist = new HashMap<>();
    private List<ItemEntity> cluster = new ArrayList<>();
    private UUID finalSweepTask;
    private UUID finalSweptTask;

    public DropCollector(Supplier<Boolean> enabled, Supplier<Double> radius,
                         Supplier<Integer> waitMs, Runnable afterSweep) {
        this.enabled = enabled;
        this.radius = radius;
        this.waitMs = waitMs;
        this.afterSweep = afterSweep;
    }

    public boolean isActive() {
        return active;
    }

    /** Start one bounded sweep after a block break or kill. */
    public boolean startWorkUnit() {
        if (!Boolean.TRUE.equals(enabled.get()) || active) return false;
        if (inventoryFull()) {
            logInventoryFull();
            return false;
        }
        loggedInventoryFull = false;
        resetUnit();
        active = true;
        phase = Phase.SCAN;
        delayTicks = 2;
        return true;
    }

    /**
     * Called immediately before a task is marked complete. The task remains
     * active until this returns false on a later tick, so the queue cannot
     * report work complete with the last drop still on the ground.
     */
    public boolean startFinalSweep(UUID taskId) {
        if (!Boolean.TRUE.equals(enabled.get())) return false;
        if (taskId == null || taskId.equals(finalSweptTask)) return false;
        if (active) {
            if (finalSweepTask == null) finalSweepTask = taskId;
            return true;
        }
        finalSweepTask = taskId;
        if (!startWorkUnit()) {
            finalSweptTask = taskId;
            finalSweepTask = null;
            return false;
        }
        return true;
    }

    public void resetTaskTracking() {
        finalSweepTask = null;
        finalSweptTask = null;
    }

    /** Abort a sweep without logging a normal completion. */
    public void cancel() {
        if (!active) return;
        cancelPath();
        active = false;
        phase = Phase.DONE;
        cluster.clear();
        clusterIds.clear();
    }

    /** Returns true while the sweep owns this tick and the task loop must wait. */
    public boolean tick() {
        if (!active) return false;
        if (delayTicks > 0) {
            delayTicks--;
            return true;
        }
        if (!Boolean.TRUE.equals(enabled.get())) {
            finish();
            return true;
        }
        if (inventoryFull()) {
            logInventoryFull();
            finish();
            return true;
        }
        loggedInventoryFull = false;

        switch (phase) {
            case SCAN -> scan();
            case PATH -> tickPath();
            case WAIT -> tickWait();
            default -> finish();
        }
        return true;
    }

    private void scan() {
        long now = System.currentTimeMillis();
        List<ItemEntity> items = nearbyItems(now);
        if (items.isEmpty()) {
            finish();
            return;
        }

        Cluster clusterChoice = bestCluster(items);
        if (clusterChoice == null) {
            finish();
            return;
        }
        if (clusters >= MAX_CLUSTERS) {
            finish();
            return;
        }

        cluster = clusterChoice.items;
        clusterIds.clear();
        for (ItemEntity item : cluster) {
            clusterIds.add(itemId(item));
        }
        totalSeen = Math.max(totalSeen, collected.size() + clusterIds.size());
        firstCluster = clusterChoice.center;

        if (nearCluster()) {
            clusters++;
            collectVanished();
            if (clusters >= MAX_CLUSTERS) {
                finish();
            } else {
                delayTicks = 2;
                phase = Phase.SCAN;
            }
            return;
        }

        String unsafe = unsafeReason(clusterChoice.center);
        if (unsafe != null) {
            blacklist(cluster, now);
            logSkipped("unsafe: " + unsafe);
            finish();
            return;
        }

        clusters++;
        if (!startPath(clusterChoice.center)) {
            blacklist(cluster, now);
            logSkipped("unreachable, blacklisted 30s");
            finish();
        }
    }

    private boolean startPath(BlockPos center) {
        long now = System.currentTimeMillis();
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            wasBaritoneActive = baritone.getPathingBehavior().isPathing()
                || baritone.getMineProcess().isActive();
            // Take control away from Baritone's mining process; the task loop
            // will reissue its mining command after this sweep ends.
            baritone.getMineProcess().mine(0, (baritone.api.utils.BlockOptionalMetaLookup) null);
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(center));
            phase = Phase.PATH;
            phaseStarted = now;
            pathAttempts = 0;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void tickPath() {
        long now = System.currentTimeMillis();
        if (clusterGone()) {
            finishCollected();
            return;
        }
        if (nearCluster()) {
            cancelPath();
            phase = Phase.WAIT;
            waitUntil = now + Math.max(500, waitMs.get());
            return;
        }

        boolean pathing;
        try {
            pathing = BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior().isPathing();
        } catch (Throwable t) {
            failUnreachable(now);
            return;
        }

        if (!pathing && now - phaseStarted > 1200) {
            if (pathAttempts >= 1) {
                failUnreachable(now);
                return;
            }
            pathAttempts++;
            phaseStarted = now;
            try {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(firstCluster));
            } catch (Throwable t) {
                failUnreachable(now);
                return;
            }
        }

        if (now - phaseStarted > MAX_PATH_MS) {
            failUnreachable(now);
        }
    }

    private void tickWait() {
        if (clusterGone()) {
            finishCollected();
            return;
        }
        if (System.currentTimeMillis() >= waitUntil) {
            failUnreachable(System.currentTimeMillis());
        }
    }

    private void finishCollected() {
        collectVanished();
        cluster.clear();
        clusterIds.clear();
        if (clusters < MAX_CLUSTERS) {
            delayTicks = 2;
            phase = Phase.SCAN;
        } else {
            finish();
        }
    }

    private void failUnreachable(long now) {
        blacklist(cluster, now);
        logSkipped("unreachable, blacklisted 30s");
        finish();
    }

    private void finish() {
        if (!active) return;
        cancelPath();
        active = false;
        phase = Phase.DONE;
        cluster.clear();
        clusterIds.clear();
        if (finalSweepTask != null) {
            finalSweptTask = finalSweepTask;
            finalSweepTask = null;
        }
        if (!skippedLogged) {
            String r = String.format("%.1f", radius.get());
            if (totalSeen == 0 && collected.isEmpty()) {
                ChatUtils.info("DROPS: 0 items within %s -> none", r);
            } else {
                String where = firstCluster == null ? "none"
                    : String.format("%d,%d,%d", firstCluster.getX(), firstCluster.getY(), firstCluster.getZ());
                String ids = collected.isEmpty() ? "none" : String.join(",", collected);
                ChatUtils.info("DROPS: %d items within %s -> cluster (%s) -> collected %s",
                    totalSeen, r, where, ids);
            }
        }
        if (afterSweep != null) afterSweep.run();
    }

    private void resetUnit() {
        phase = Phase.SCAN;
        phaseStarted = 0;
        waitUntil = 0;
        delayTicks = 0;
        pathAttempts = 0;
        clusters = 0;
        totalSeen = 0;
        skippedLogged = false;
        firstCluster = null;
        collected.clear();
        clusterIds.clear();
        cluster.clear();
    }

    private void cancelPath() {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {}
    }

    private List<ItemEntity> nearbyItems(long now) {
        List<ItemEntity> result = new ArrayList<>();
        if (mc.player == null || mc.level == null) return result;
        double r = Math.max(1.0D, radius.get());
        AABB box = new AABB(mc.player.blockPosition()).inflate(r);
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof ItemEntity)) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!item.isAlive() || item.getItem().isEmpty()) continue;
            Long until = blacklist.get(item.getUUID());
            if (until != null && until > now) continue;
            result.add(item);
        }
        return result;
    }

    private Cluster bestCluster(List<ItemEntity> items) {
        List<Cluster> clusters = new ArrayList<>();
        for (ItemEntity item : items) {
            Cluster match = null;
            for (Cluster candidate : clusters) {
                if (candidate.center.distSqr(item.blockPosition()) <= CLUSTER_RADIUS_SQ) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                clusters.add(new Cluster(item));
            } else {
                match.items.add(item);
            }
        }
        if (clusters.isEmpty()) return null;
        clusters.sort(Comparator.comparingDouble(c -> mc.player.position()
            .distanceToSqr(Vec3.atCenterOf(c.center))));
        return clusters.get(0);
    }

    private boolean nearCluster() {
        return mc.player != null && firstCluster != null
            && mc.player.position().distanceToSqr(Vec3.atCenterOf(firstCluster)) <= VANILLA_PICKUP_SQ;
    }

    private boolean clusterGone() {
        for (ItemEntity item : cluster) {
            if (item.isAlive() && !item.getItem().isEmpty()) return false;
        }
        return true;
    }

    private void collectVanished() {
        if (clusterIds.isEmpty()) return;
        Set<String> remaining = new LinkedHashSet<>();
        for (ItemEntity item : cluster) {
            if (item.isAlive() && !item.getItem().isEmpty()) remaining.add(itemId(item));
        }
        for (String id : clusterIds) {
            if (!remaining.contains(id)) collected.add(id);
        }
    }

    private String unsafeReason(BlockPos center) {
        if (mc.player == null || mc.level == null) return "no world";
        BlockState floor = mc.level.getBlockState(center.below());
        BlockState feet = mc.level.getBlockState(center);
        BlockState head = mc.level.getBlockState(center.above());

        if (dangerous(feet) || dangerous(head) || dangerous(floor)) {
            return "lava/fire";
        }
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()
            || !floor.getFluidState().isEmpty()) {
            return "fluid";
        }
        if (nearbyDanger(center)) {
            return "lava/fire nearby";
        }
        if (floor.isAir() || !floor.isSolid()) {
            return "no floor";
        }
        if (!feet.isAir() && !feet.canBeReplaced()) {
            return "blocked";
        }
        if (!head.isAir() && !head.canBeReplaced()) {
            return "blocked";
        }
        if (Math.abs(center.getY() - mc.player.blockPosition().getY()) > 3) {
            return "drop more than 3 blocks";
        }
        return null;
    }

    private boolean nearbyDanger(BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockState state = mc.level.getBlockState(center.offset(dx, dy, dz));
                    if (dangerous(state) || !state.getFluidState().isEmpty()) return true;
                }
            }
        }
        return false;
    }

    private boolean dangerous(BlockState state) {
        return state.getBlock() == Blocks.LAVA
            || state.getBlock() == Blocks.FIRE
            || state.getBlock() == Blocks.MAGMA_BLOCK
            || state.getBlock() == Blocks.CACTUS;
    }

    private boolean inventoryFull() {
        if (mc.player == null) return true;
        Inventory inventory = mc.player.getInventory();
        int slots = Math.min(36, inventory.getContainerSize());
        for (int i = 0; i < slots; i++) {
            if (inventory.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private void logInventoryFull() {
        if (loggedInventoryFull) return;
        loggedInventoryFull = true;
        skippedLogged = true;
        ChatUtils.info("DROPS: skipped (inventory full)");
    }

    private void logSkipped(String reason) {
        skippedLogged = true;
        ChatUtils.info("DROPS: skipped (%s)", reason);
    }

    private void blacklist(List<ItemEntity> items, long now) {
        for (ItemEntity item : items) {
            blacklist.put(item.getUUID(), now + BLACKLIST_MS);
        }
    }

    private String itemId(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return "unknown";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static final class Cluster {
        final BlockPos center;
        final List<ItemEntity> items = new ArrayList<>();

        Cluster(ItemEntity first) {
            this.center = first.blockPosition();
            this.items.add(first);
        }
    }
}
