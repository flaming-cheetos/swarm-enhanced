package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Runs away from a threat when health is low.
 *
 * Movement is a Baritone direction goal, not key mashing, so the bot does not
 * jitter between dodge and path. KillAura is left alone on purpose: it only
 * reaches 4.5 blocks, so pathing away from the fight ends the attacks by
 * distance instead of by toggling another module underneath ModuleGuard.
 */
public class SwarmFlee extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> health = sg.add(new DoubleSetting.Builder()
        .name("health")
        .description("Flee when health drops to this value.")
        .defaultValue(8.0)
        .min(1)
        .sliderMax(20)
        .build());

    public final Setting<Double> threatRange = sg.add(new DoubleSetting.Builder()
        .name("threat-range")
        .description("Only flee when a hostile is this close.")
        .defaultValue(8.0)
        .min(2)
        .sliderMax(16)
        .build());

    public final Setting<Double> safeDistance = sg.add(new DoubleSetting.Builder()
        .name("safe-distance")
        .description("Stop fleeing once the threat is this far away.")
        .defaultValue(16.0)
        .min(4)
        .sliderMax(32)
        .build());

    public final Setting<Integer> maxSeconds = sg.add(new IntSetting.Builder()
        .name("max-seconds")
        .description("Give up fleeing after this long and resume the task.")
        .defaultValue(10)
        .min(2)
        .sliderMax(60)
        .build());

    public final Setting<Boolean> players = sg.add(new BoolSetting.Builder()
        .name("players")
        .description("Treat players as threats too.")
        .defaultValue(true)
        .build());

    private boolean fleeing;
    private long startedAt;
    private boolean warnedNoBaritone;
    /** Goal captured before fleeing, restored afterwards so the task path survives. */
    private baritone.api.pathing.goals.Goal savedGoal;
    /** Repath throttle: re-issuing the direction every tick makes Baritone thrash. */
    private float lastYaw;
    private long lastIssueMs;

    public SwarmFlee() {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, "swarm-flee",
            "Paths away from a threat when health is low.");
    }

    @Override
    public void onDeactivate() {
        stopFleeing();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        double hp = mc.player.getHealth();
        Entity threat = nearestThreat();

        if (!fleeing) {
            if (hp <= health.get() && threat != null) startFleeing();
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        boolean recovered = hp > health.get() + 2.0;
        boolean safe = threat == null || mc.player.distanceTo(threat) >= safeDistance.get();
        boolean timeout = elapsedMs > maxSeconds.get() * 1000L;

        if (recovered || safe || timeout || hp <= 0) {
            stopFleeing();
            return;
        }

        // Recompute the escape direction at most once a second, or when the threat
        // has moved to a genuinely different bearing. Every-tick reissue thrashed.
        float yaw = awayYaw(threat);
        long now = System.currentTimeMillis();
        boolean turned = Math.abs(wrapDegrees(yaw - lastYaw)) > 20f;
        if (!turned && now - lastIssueMs < 1000L) return;
        lastYaw = yaw;
        lastIssueMs = now;

        try {
            PathManagers.get().moveInDirection(yaw);
        } catch (Throwable t) {
            if (!warnedNoBaritone) {
                warnedNoBaritone = true;
                warning("Path manager unavailable: %s", t.getMessage());
            }
            stopFleeing();
        }
    }

    private void startFleeing() {
        fleeing = true;
        startedAt = System.currentTimeMillis();
        lastIssueMs = 0;
        savedGoal = currentGoal();
        tuneBaritone();
        info("Fleeing — health %.1f", mc.player.getHealth());
    }

    /** Baritone has no sprint-jump, but these keep it sprinting and sprint-ascending. */
    private void tuneBaritone() {
        try {
            baritone.api.Settings s = baritone.api.BaritoneAPI.getSettings();
            s.allowSprint.value = true;
            s.sprintAscends.value = true;
        } catch (Throwable ignored) {
        }
    }

    private static float wrapDegrees(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private void stopFleeing() {
        if (!fleeing) return;
        fleeing = false;

        // Cancel the flee direction, then hand the task's own goal back to Baritone.
        try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior().cancelEverything();
            if (savedGoal != null) {
                baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getCustomGoalProcess().setGoalAndPath(savedGoal);
            }
        } catch (Throwable ignored) {
        }
        savedGoal = null;
        info("Flee over — resuming");
    }

    private baritone.api.pathing.goals.Goal currentGoal() {
        try {
            return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior().getGoal();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isFleeing() {
        return fleeing;
    }

    private Entity nearestThreat() {
        AABB box = mc.player.getBoundingBox().inflate(threatRange.get());
        List<Entity> nearby = mc.level.getEntities(mc.player, box);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : nearby) {
            if (!isThreat(entity)) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private boolean isThreat(Entity entity) {
        if (entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof Player player) {
            if (!players.get()) return false;
            return Friends.get().shouldAttack(player);
        }
        if (entity instanceof Enemy) return true;
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }

    /** Yaw pointing directly away from the threat. Minecraft yaw 0 is +Z. */
    private float awayYaw(Entity threat) {
        double dx = mc.player.getX() - threat.getX();
        double dz = mc.player.getZ() - threat.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
