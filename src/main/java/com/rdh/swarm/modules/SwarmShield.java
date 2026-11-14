package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

/**
 * Raises a shield while KillAura fights.
 *
 * Three constraints shape this on purpose:
 * - It requires KillAura. TriggerBot returns early on isBlocking(), so a
 *   trigger-style attacker + shield is a bot that never swings.
 * - It yields the offhand the moment AutoTotem locks; a shield must never
 *   outrank a totem at lethal health.
 * - It drops the shield while walking or fleeing, because blocking caps
 *   movement at 20%.
 */
public class SwarmShield extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range")
        .description("Block only when the aura target is this close.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build());

    public final Setting<Double> minHealth = sg.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Below this health the offhand belongs to the totem, not the shield.")
        .defaultValue(7.0)
        .min(1)
        .sliderMax(20)
        .build());

    public final Setting<Boolean> equipOffhand = sg.add(new BoolSetting.Builder()
        .name("equip-offhand")
        .description("Move a shield into the offhand when one is in the inventory.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> releaseWhenMoving = sg.add(new BoolSetting.Builder()
        .name("release-when-moving")
        .description("Drop the shield while a movement key is held. Blocking caps speed at 20%.")
        .defaultValue(true)
        .build());

    public final Setting<Integer> swapDelay = sg.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks between offhand equip attempts.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build());

    private boolean holdingUse;
    private int swapCooldown;
    private boolean warnedNoShield;

    public SwarmShield() {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, "swarm-shield",
            "Blocks with a shield while KillAura fights; yields to the totem.");
    }

    @Override
    public void onDeactivate() {
        release();
        holdingUse = false;
        swapCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (swapCooldown > 0) swapCooldown--;

        if (!shouldBlock()) {
            release();
            return;
        }

        if (equipOffhand.get() && !hasShieldOffhand()) {
            if (!tryEquip()) {
                release();
                return;
            }
        }

        if (!hasShieldOffhand()) {
            release();
            return;
        }

        if (!holdingUse) {
            mc.options.keyUse.setDown(true);
            holdingUse = true;
        }
    }

    private boolean shouldBlock() {
        if (mc.player == null) return false;
        if (mc.player.getHealth() <= minHealth.get()) return false;
        // isUsingItem() is also true while the shield itself is raised — only food/potions block us.
        if (mc.player.isUsingItem() && !mc.player.getUseItem().is(Items.SHIELD)) return false;
        if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) return false; // right-click would place, not block
        if (isTotemLocked()) return false;                          // totem outranks the shield
        if (isFleeing()) return false;                              // flee owns movement and hands
        if (releaseWhenMoving.get() && isMoving()) return false;    // 20% speed penalty

        KillAura aura = Modules.get().get(KillAura.class);
        if (aura == null || !aura.isActive()) return false;         // KillAura only, never TriggerBot

        Entity target = aura.getTarget();
        return target != null && mc.player.distanceTo(target) <= range.get();
    }

    private boolean isTotemLocked() {
        AutoTotem totem = Modules.get().get(AutoTotem.class);
        return totem != null && totem.isLocked();
    }

    private boolean isFleeing() {
        SwarmFlee flee = Modules.get().get(SwarmFlee.class);
        return flee != null && flee.isActive() && flee.isFleeing();
    }

    private boolean isMoving() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    private boolean hasShieldOffhand() {
        return mc.player != null && mc.player.getOffhandItem().is(Items.SHIELD);
    }

    private boolean tryEquip() {
        if (swapCooldown > 0) return false;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return false;

        FindItemResult shield = InvUtils.find(stack -> stack.is(Items.SHIELD));
        if (!shield.found()) {
            if (!warnedNoShield) {
                warnedNoShield = true;
                warning("No shield in inventory; shield module idle.");
            }
            return false;
        }

        InvUtils.move().from(shield.slot()).toOffhand();
        swapCooldown = swapDelay.get();
        return true;
    }

    private void release() {
        if (holdingUse) {
            mc.options.keyUse.setDown(false);
            holdingUse = false;
        }
    }

    /** True while this module is holding the use key for blocking. */
    public boolean isBlocking() {
        return holdingUse;
    }
}
