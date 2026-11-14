package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Disengage. Combat gives ground a few steps at a time; this module breaks contact
 * entirely when the maths is lost — low health, or several hostiles at once — runs
 * with the shield up, and hands control back once the threat is out of range.
 *
 * It is deliberately separate from SwarmCombat: reassurance retries, escape quits.
 */
public class SwarmFlee extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> healthThreshold = sg.add(new DoubleSetting.Builder()
        .name("health-threshold").description("Flee at or below this health.")
        .defaultValue(6.0).min(0).sliderRange(0, 20).build());

    public final Setting<Integer> dangerCount = sg.add(new IntSetting.Builder()
        .name("danger-count").description("Also flee when this many hostiles are close. 0 disables.")
        .defaultValue(3).min(0).sliderRange(0, 8).build());

    public final Setting<Double> threatRange = sg.add(new DoubleSetting.Builder()
        .name("threat-range").description("Hostiles inside this radius count as danger.")
        .defaultValue(8.0).min(2).sliderRange(2, 24).build());

    public final Setting<Integer> holdTicks = sg.add(new IntSetting.Builder()
        .name("hold-ticks").description("Minimum ticks to keep running once triggered.")
        .defaultValue(60).min(0).sliderRange(0, 400).build());

    public final Setting<Boolean> shield = sg.add(new BoolSetting.Builder()
        .name("shield").description("Keep the offhand shield up while running.")
        .defaultValue(true).build());

    public final Setting<Boolean> jumpObstacles = sg.add(new BoolSetting.Builder()
        .name("jump").description("Jump when something blocks the escape.")
        .defaultValue(true).build());

    public final Setting<Boolean> includePlayers = sg.add(new BoolSetting.Builder()
        .name("players").description("Treat players as danger too.")
        .defaultValue(true).build());

    private int fleeUntil;
    private boolean fleeing;

    public SwarmFlee() {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, "swarm-flee",
            "Break contact when the fight is lost, shield up");
    }

    public boolean isFleeing() {
        return isActive() && fleeing;
    }

    @Override
    public void onActivate() {
        fleeing = false;
        fleeUntil = 0;
    }

    @Override
    public void onDeactivate() {
        stop();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
            stop();
            return;
        }
        if (isEating()) {
            // Eating cannot be interrupted halfway without wasting the food.
            return;
        }

        LivingEntity threat = nearestThreat();
        int hostiles = countHostiles();
        boolean danger = threat != null
            && (mc.player.getHealth() <= healthThreshold.get()
                || (dangerCount.get() > 0 && hostiles >= dangerCount.get()));

        if (danger) {
            fleeUntil = Math.max(fleeUntil, mc.player.tickCount + holdTicks.get());
        }

        if (threat == null || mc.player.tickCount > fleeUntil) {
            fleeing = false;
            stop();
            return;
        }

        fleeing = true;
        // Run along the line away from the threat, so distance grows even when the
        // bot is cornered and has to slide along a wall.
        double dx = mc.player.getX() - threat.getX();
        double dz = mc.player.getZ() - threat.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0.01) {
            mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        }

        setKey(mc.options.keyUp, true);
        setKey(mc.options.keyDown, false);
        mc.player.setSprinting(true);

        if (jumpObstacles.get() && mc.player.horizontalCollision) {
            mc.player.jumpFromGround();
        }

        if (shield.get() && hasShield()) setKey(mc.options.keyUse, true);
    }

    private void stop() {
        setKey(mc.options.keyUp, false);
        setKey(mc.options.keyUse, false);
        if (mc.player != null) mc.player.setSprinting(false);
    }

    private void setKey(net.minecraft.client.KeyMapping key, boolean down) {
        if (key != null && key.isDown() != down) key.setDown(down);
    }

    private boolean hasShield() {
        return mc.player != null
            && (mc.player.getOffhandItem().is(Items.SHIELD) || mc.player.getMainHandItem().is(Items.SHIELD));
    }

    private boolean isEating() {
        ItemStack using = mc.player.getUseItem();
        return !using.isEmpty() && using.has(DataComponents.FOOD);
    }

    private LivingEntity nearestThreat() {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : threats()) {
            double distance = mc.player.distanceTo(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private int countHostiles() {
        return threats().size();
    }

    private java.util.List<LivingEntity> threats() {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        double radius = threatRange.get();
        AABB box = mc.player.getBoundingBox().inflate(radius);
        for (Entity entity : mc.level.getEntities(mc.player, box)) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living instanceof Monster) {
                if (mc.player.distanceTo(living) <= radius) out.add(living);
            } else if (living instanceof Player player && includePlayers.get()) {
                if (player == mc.player || player.isCreative() || player.isSpectator()) continue;
                if (mc.player.distanceTo(player) <= radius) out.add(player);
            }
        }
        return out;
    }
}
