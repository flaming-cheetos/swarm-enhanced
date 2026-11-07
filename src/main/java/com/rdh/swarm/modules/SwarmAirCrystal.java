package com.rdh.swarm.modules;

import com.rdh.swarm.EnhancedSwarmAddon;
import com.rdh.swarm.utils.BODamageUtils;
import com.rdh.swarm.utils.BOInvUtils;
import com.rdh.swarm.utils.ExtrapolationUtils;
import com.rdh.swarm.utils.OLEPOSSUtils;
import com.rdh.swarm.utils.RotationUtils;
import com.rdh.swarm.utils.TimerList;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwarmAirCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General
    private final Setting<String> targetName = sgGeneral.add(new StringSetting.Builder()
        .name("target")
        .description("Player to combo.")
        .defaultValue("")
        .build()
    );

    private final Setting<Double> minAirHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-air-height")
        .description("How high off ground target must be to trigger combo.")
        .defaultValue(0.5)
        .range(0.1, 5.0)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Max range to attempt combo.")
        .defaultValue(5)
        .range(2, 8)
        .build()
    );

    private final Setting<Integer> extrapolationTicks = sgGeneral.add(new IntSetting.Builder()
        .name("extrapolation")
        .description("Ticks of movement prediction for damage calc.")
        .defaultValue(2)
        .range(0, 10)
        .build()
    );

    private final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Silently swap to obsidian and crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTerrain = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-terrain")
        .description("Ignore terrain in damage calculations.")
        .defaultValue(true)
        .build()
    );

    // Damage
    private final Setting<Double> minDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Minimum damage to target before placing.")
        .defaultValue(4)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum self damage allowed.")
        .defaultValue(8)
        .range(0, 36)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgDamage.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Never place if it would kill you.")
        .defaultValue(true)
        .build()
    );

    // Timing
    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
        .name("place-delay-ms")
        .description("Delay between obsidian and crystal placement.")
        .defaultValue(50)
        .range(0, 500)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Integer> breakDelay = sgTiming.add(new IntSetting.Builder()
        .name("break-delay-ms")
        .description("Delay before breaking the crystal.")
        .defaultValue(50)
        .range(0, 500)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Integer> comboCooldown = sgTiming.add(new IntSetting.Builder()
        .name("combo-cooldown-ms")
        .description("Cooldown between full combos.")
        .defaultValue(500)
        .range(100, 3000)
        .sliderRange(100, 2000)
        .build()
    );

    // State machine
    private enum ComboState {
        WAITING,
        PLACING_OBBY,
        PLACING_CRYSTAL,
        BREAKING_CRYSTAL
    }

    private ComboState state = ComboState.WAITING;
    private BlockPos targetObbyPos = null;
    private long stateStartTime = 0;
    private long lastCombo = 0;

    // Extrapolation map
    private final Map<AbstractClientPlayerEntity, Box> extPos = new HashMap<>();

    // Attack cooldown tracker
    private final TimerList<Integer> attackedList = new TimerList<>();

    // Signature
    private static final byte[] _acs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _acc = 0x726468;
    private static final long _acn = 0x666C616D696E67L;

    public SwarmAirCrystal() {
        super(EnhancedSwarmAddon.CATEGORY, "swarm-air-crystal",
            "Places obsidian and crystals under airborne targets. Use with swarm-pearl-chase.");
    }

    @Override
    public void onActivate() {
        state = ComboState.WAITING;
        targetObbyPos = null;
        extPos.clear();
        attackedList.clear();
        lastCombo = 0;
    }

    @Override
    public void onDeactivate() {
        state = ComboState.WAITING;
        targetObbyPos = null;
        extPos.clear();
        attackedList.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        attackedList.update();

        // Update extrapolation map for all players
        ExtrapolationUtils.extrapolateMap(
            extPos,
            p -> p == mc.player ? 0 : extrapolationTicks.get(),
            p -> 2
        );

        String name = targetName.get().trim();
        if (name.isEmpty()) return;

        PlayerEntity target = findTarget(name);
        if (target == null) {
            state = ComboState.WAITING;
            return;
        }

        double dist = mc.player.distanceTo(target);
        if (dist > range.get()) {
            state = ComboState.WAITING;
            return;
        }

        long now = System.currentTimeMillis();

        switch (state) {
            case WAITING -> {
                if (!isAirborne(target)) break;
                if (now - lastCombo < comboCooldown.get()) break;

                BlockPos below = getAirBlockBelow(target);
                if (below == null) break;
                if (!canPlace(below)) break;

                // Get extrapolated bounding box for accurate damage calc
                Box targetBox = extPos.containsKey((AbstractClientPlayerEntity) target)
                    ? extPos.get((AbstractClientPlayerEntity) target)
                    : target.getBoundingBox();

                // Crystal position (center top of the obby block)
                Vec3d crystalPos = new Vec3d(
                    below.getX() + 0.5,
                    below.getY() + 1,
                    below.getZ() + 0.5
                );

                // Damage checks using BODamageUtils
                double targetDmg = BODamageUtils.crystalDamage(
                    target, targetBox, crystalPos, null, ignoreTerrain.get()
                );
                double selfDmg = BODamageUtils.crystalDamage(
                    mc.player, mc.player.getBoundingBox(), crystalPos, null, ignoreTerrain.get()
                );

                if (targetDmg < minDamage.get()) break;
                if (selfDmg > maxSelfDamage.get()) break;
                if (antiSuicide.get()) {
                    double totalHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                    if (selfDmg >= totalHp) break;
                }

                targetObbyPos = below;
                state = ComboState.PLACING_OBBY;
                stateStartTime = now;
            }

            case PLACING_OBBY -> {
                if (targetObbyPos == null) {
                    state = ComboState.WAITING;
                    break;
                }
                if (placeObby(targetObbyPos)) {
                    state = ComboState.PLACING_CRYSTAL;
                    stateStartTime = now;
                } else {
                    state = ComboState.WAITING;
                    targetObbyPos = null;
                }
            }

            case PLACING_CRYSTAL -> {
                if (now - stateStartTime < placeDelay.get()) break;
                if (targetObbyPos == null) {
                    state = ComboState.WAITING;
                    break;
                }
                if (placeCrystal(targetObbyPos)) {
                    state = ComboState.BREAKING_CRYSTAL;
                    stateStartTime = now;
                } else {
                    state = ComboState.WAITING;
                    targetObbyPos = null;
                }
            }

            case BREAKING_CRYSTAL -> {
                if (now - stateStartTime < breakDelay.get()) break;
                if (targetObbyPos == null) {
                    state = ComboState.WAITING;
                    break;
                }
                breakNearestCrystal(targetObbyPos);
                lastCombo = now;
                state = ComboState.WAITING;
                targetObbyPos = null;
            }
        }
    }

    private PlayerEntity findTarget(String name) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    private boolean isAirborne(PlayerEntity player) {
        if (player.isOnGround()) return false;
        BlockPos below = player.getBlockPos().down();
        double feetY = player.getY();
        double groundY = below.getY() + 1;
        return (feetY - groundY) >= minAirHeight.get();
    }

    private BlockPos getBlockBelow(PlayerEntity player) {
        BlockPos pos = player.getBlockPos().down();
        for (int i = 0; i < 5; i++) {
            if (mc.world.getBlockState(pos).isAir()) return pos;
            pos = pos.down();
        }
        return null;
    }

    // Compatibility wrappers for earlier BlackOut port names
    private BlockPos getAirBlockBelow(PlayerEntity player) {
        return getBlockBelow(player);
    }

    private boolean canPlace(BlockPos pos) {
        return canPlaceObby(pos);
    }

    private boolean canPlaceObby(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return false;
        BlockPos below = pos.down();
        if (mc.world.getBlockState(below).isAir()) return false;
        return true;
    }

    private boolean placeObby(BlockPos pos) {
        int slot = InvUtils.find(Items.OBSIDIAN).slot();
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        if (autoSwap.get()) BOInvUtils.invSwitch(slot);

        try {
            Vec3d hitVec = Vec3d.ofCenter(pos.down()).add(0, 0.5, 0);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos.down(), false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } catch (Exception e) {
            return false;
        }

        if (autoSwap.get()) BOInvUtils.swapBack();
        return true;
    }

    private boolean placeCrystal(BlockPos obbyPos) {
        if (mc.world.getBlockState(obbyPos).getBlock() != Blocks.OBSIDIAN) return false;

        int slot = InvUtils.find(Items.END_CRYSTAL).slot();
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        if (autoSwap.get()) BOInvUtils.invSwitch(slot);

        try {
            Vec3d hitVec = Vec3d.ofCenter(obbyPos).add(0, 0.5, 0);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, obbyPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } catch (Exception e) {
            return false;
        }

        if (autoSwap.get()) BOInvUtils.swapBack();
        return true;
    }

    private void breakCrystal(BlockPos obbyPos) {
        Box searchBox = new Box(obbyPos).expand(2);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, searchBox, e -> true);
        if (crystals.isEmpty()) return;

        EndCrystalEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (EndCrystalEntity crystal : crystals) {
            double d = mc.player.distanceTo(crystal);
            if (d < closestDist) {
                closestDist = d;
                closest = crystal;
            }
        }

        if (closest != null) {
            mc.interactionManager.attackEntity(mc.player, closest);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void breakNearestCrystal(BlockPos obbyPos) {
        breakCrystal(obbyPos);
    }

    @Override
    public String getInfoString() {
        return state == ComboState.WAITING ? null : state.name();
    }
}
