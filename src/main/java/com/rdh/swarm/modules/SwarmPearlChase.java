package com.rdh.swarm.modules;

import com.rdh.swarm.EnhancedSwarmAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;

public class SwarmPearlChase extends Module {
    private final SettingGroup sgPearl = settings.createGroup("Pearl");
    private final SettingGroup sgWind = settings.createGroup("Wind Charge");

    private final Setting<String> targetName = sgPearl.add(new StringSetting.Builder()
        .name("target")
        .description("Player to chase.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> pearlDistance = sgPearl.add(new IntSetting.Builder()
        .name("pearl-distance")
        .description("Pearl if target is further than this many blocks.")
        .defaultValue(6)
        .range(3, 60)
        .sliderRange(3, 30)
        .build()
    );

    private final Setting<Integer> pearlCooldown = sgPearl.add(new IntSetting.Builder()
        .name("pearl-cooldown-ms")
        .defaultValue(1500)
        .range(500, 10000)
        .sliderRange(500, 5000)
        .build()
    );

    private final Setting<Double> arcMultiplier = sgPearl.add(new DoubleSetting.Builder()
        .name("arc-multiplier")
        .description("Aim compensation for gravity. Higher = more arc for long throws.")
        .defaultValue(0.12)
        .range(0.05, 0.4)
        .sliderRange(0.05, 0.3)
        .build()
    );

    private final Setting<Boolean> useWindCharge = sgWind.add(new BoolSetting.Builder()
        .name("use-wind-charge")
        .description("Throw wind charges at target feet to launch them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> windDistance = sgWind.add(new IntSetting.Builder()
        .name("wind-distance")
        .description("Max distance to throw wind charge.")
        .defaultValue(5)
        .range(2, 10)
        .build()
    );

    private final Setting<Integer> windCooldown = sgWind.add(new IntSetting.Builder()
        .name("wind-cooldown-ms")
        .defaultValue(2000)
        .range(500, 10000)
        .sliderRange(500, 5000)
        .build()
    );

    private long lastPearl = 0;
    private long lastWind = 0;

    private static final byte[] _pc0 = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _pc1 = 0x726468;
    private static final long _pc2 = 0x666C616D696E67L;

    public SwarmPearlChase() {
        super(EnhancedSwarmAddon.CATEGORY, "swarm-pearl-chase",
            "Throws pearls to close gap and wind charges to launch target for crystal combo.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        String name = targetName.get().trim();
        if (name.isEmpty()) return;

        Player target = findTarget(name);
        if (target == null) return;

        double dist = mc.player.distanceTo(target);
        long now = System.currentTimeMillis();

        // Priority 1: Wind charge if close enough
        if (useWindCharge.get() && dist <= windDistance.get()) {
            if (now - lastWind >= windCooldown.get()) {
                if (throwWindCharge(target)) {
                    lastWind = now;
                    return;
                }
            }
        }

        // Priority 2: Pearl if too far
        if (dist >= pearlDistance.get()) {
            if (now - lastPearl >= pearlCooldown.get()) {
                if (throwPearl(target, dist)) {
                    lastPearl = now;
                }
            }
        }
    }

    private Player findTarget(String name) {
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    private boolean throwPearl(Player target, double dist) {
        int slot = findHotbarItem(Items.ENDER_PEARL);
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        aimAt(target, dist, true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

        mc.player.getInventory().setSelectedSlot(prev);
        return true;
    }

    private boolean throwWindCharge(Player target) {
        int slot = findHotbarItem(Items.WIND_CHARGE);
        if (slot == -1) return false;

        int prev = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        aimAtFeet(target);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);

        mc.player.getInventory().setSelectedSlot(prev);
        return true;
    }

    private void aimAt(Player target, double dist, boolean compensate) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy = (target.getY() + 1.0) - (mc.player.getY() + 1.6);
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double extraHeight = compensate ? horizontal * arcMultiplier.get() : 0;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy + extraHeight, horizontal));

        pitch = Math.max(-89, Math.min(89, pitch));

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private void aimAtFeet(Player target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy = target.getY() - (mc.player.getY() + 1.6);
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        pitch = Math.max(-89, Math.min(89, pitch));

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private int findHotbarItem(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    @Override
    public String getInfoString() {
        return targetName.get().isEmpty() ? null : targetName.get();
    }
}
