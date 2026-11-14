package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * The bot's own aura. Meteor's KillAura targets and swings well, but it has no
 * concept of the bot's body: it never raises the shield and never gives ground.
 * A skeleton does not care how fast you swing, it cares whether your shield is up
 * between its arrows, and a bot standing still inside a mob pit dies to arithmetic.
 *
 * So this module only does the parts Meteor cannot: shield between swings, strafe,
 * and back off while still hitting when health drops. Targets are the same ones
 * KillAura would pick (monsters, optionally players).
 */
public class SwarmCombat extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range").description("Attack range.")
        .defaultValue(4.2).min(1).sliderRange(2, 6).build());

    public final Setting<Double> wallsRange = sg.add(new DoubleSetting.Builder()
        .name("walls-range").description("Range through blocks.")
        .defaultValue(3.0).min(0).sliderRange(0, 6).build());

    public final Setting<Double> threatRange = sg.add(new DoubleSetting.Builder()
        .name("threat-range").description("Raise the shield for a hostile inside this radius, even out of attack range.")
        .defaultValue(7.0).min(2).sliderRange(2, 16).build());

    public final Setting<Boolean> shield = sg.add(new BoolSetting.Builder()
        .name("shield").description("Hold the offhand shield up between swings.")
        .defaultValue(true).build());

    public final Setting<Boolean> shieldOnlyThreatened = sg.add(new BoolSetting.Builder()
        .name("shield-only-threatened").description("Only raise the shield when something hostile is near.")
        .defaultValue(true).build());

    public final Setting<Boolean> strafe = sg.add(new BoolSetting.Builder()
        .name("strafe").description("Step sideways while fighting so mobs cannot line up.")
        .defaultValue(true).build());

    public final Setting<Double> retreatHealth = sg.add(new DoubleSetting.Builder()
        .name("retreat-health").description("Below this health, give ground while still attacking. 0 disables.")
        .defaultValue(7.0).min(0).sliderRange(0, 20).build());

    public final Setting<Integer> retreatHoldTicks = sg.add(new IntSetting.Builder()
        .name("retreat-hold").description("Ticks to keep backing off before re-engaging.")
        .defaultValue(30).min(0).sliderRange(0, 200).build());

    public final Setting<Boolean> targetMonsters = sg.add(new BoolSetting.Builder()
        .name("target-monsters").defaultValue(true).build());

    public final Setting<Boolean> targetPlayers = sg.add(new BoolSetting.Builder()
        .name("target-players").defaultValue(true).build());

    private long lastAttackAt;
    private int retreatUntil;
    private int strafeFlip = 1;
    private int tickCounter;

    /** Set by ModuleGuard: during a task the planner owns the body, not the aura. */
    private static boolean suppressed;

    public SwarmCombat() {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, "swarm-combat",
            "Own aura: shield between swings, strafe, give ground when hurt");
    }

    public static void setSuppressed(boolean value) {
        // The guard disables the module, which runs onDeactivate -> release().
        suppressed = value;
    }

    @Override
    public void onActivate() {
        retreatUntil = 0;
    }

    @Override
    public void onDeactivate() {
        release();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (suppressed || mc.player.isSpectator()) {
            release();
            return;
        }
        tickCounter++;

        // Eating is a two-second window where a shield is down and a hit is free.
        // Let AutoEat own the body instead of fighting it for the use key.
        if (isEating()) {
            release();
            return;
        }

        LivingEntity target = findTarget();
        LivingEntity threat = findThreat();

        if (target == null && threat == null) {
            release();
            return;
        }

        if (target != null) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
            if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f
                && mc.player.distanceTo(target) <= range.get()) {
                mc.gameMode.attack(mc.player, target);
                lastAttackAt = System.currentTimeMillis();
            }
        }

        // Shield stays up through the swing: blocking does not cancel an attack,
        // but dropping it between attacks is what lets the second arrow land.
        boolean wantShield = shield.get() && hasShield()
            && (!shieldOnlyThreatened.get() || threat != null || target != null);
        setKey(mc.options.keyUse, wantShield);

        boolean hurt = retreatHealth.get() > 0 && mc.player.getHealth() <= retreatHealth.get();
        if (hurt && threat != null) retreatUntil = tickCounter + retreatHoldTicks.get();

        if (tickCounter < retreatUntil) {
            // Back off while facing the target: the swing still lands, the mob's
            // reach does not. This is the whole point of the retreat.
            setKey(mc.options.keyUp, false);
            setKey(mc.options.keyDown, true);
            mc.player.setSprinting(true);
        } else if (strafe.get() && target != null) {
            if (tickCounter % 20 == 0) strafeFlip = -strafeFlip;
            setKey(mc.options.keyLeft, strafeFlip > 0);
            setKey(mc.options.keyRight, strafeFlip < 0);
            setKey(mc.options.keyDown, false);
        } else {
            setKey(mc.options.keyDown, false);
        }
    }

    private void release() {
        setKey(mc.options.keyUse, false);
        setKey(mc.options.keyDown, false);
        setKey(mc.options.keyLeft, false);
        setKey(mc.options.keyRight, false);
    }

    private void setKey(net.minecraft.client.KeyMapping key, boolean down) {
        if (key != null && key.isDown() != down) key.setDown(down);
    }

    private boolean hasShield() {
        if (mc.player == null) return false;
        return mc.player.getOffhandItem().is(Items.SHIELD)
            || mc.player.getMainHandItem().is(Items.SHIELD);
    }

    private boolean isEating() {
        ItemStack using = mc.player.getUseItem();
        return !using.isEmpty() && using.has(DataComponents.FOOD);
    }

    private boolean attackable(LivingEntity entity) {
        if (!entity.isAlive() || entity == mc.player) return false;
        if (entity instanceof Player player) {
            return targetPlayers.get() && !player.isCreative() && !player.isSpectator();
        }
        if (entity instanceof Monster) return targetMonsters.get();
        return false;
    }

    private LivingEntity findTarget() {
        return nearest(this::attackable, range.get());
    }

    /** Anything hostile nearby, in or out of reach — the shield cares, the sword may not. */
    private LivingEntity findThreat() {
        return nearest(e -> e instanceof Monster && e.isAlive(), threatRange.get());
    }

    private LivingEntity nearest(java.util.function.Predicate<LivingEntity> filter, double maxRange) {
        if (mc.level == null || mc.player == null) return null;
        AABB box = mc.player.getBoundingBox().inflate(maxRange + 1.0);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!filter.test(living)) continue;
            double distance = mc.player.distanceTo(living);
            if (distance > maxRange || distance >= bestDistance) continue;
            if (!mc.player.hasLineOfSight(living) && distance > wallsRange.get()) continue;
            best = living;
            bestDistance = distance;
        }
        return best;
    }
}
