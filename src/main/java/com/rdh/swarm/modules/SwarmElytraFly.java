package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;

public class SwarmElytraFly extends SwarmProxyModule {
    private static final byte[] _els = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _elc = 0x726468;
    private static final long _eln = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<ElytraFlightModes> flightMode = sg.add(new EnumSetting.Builder<ElytraFlightModes>().name("mode").defaultValue(ElytraFlightModes.Vanilla).onChanged(v -> pushSetting("mode", v.name())).build());
    public final Setting<Boolean> autoTakeOff = sg.add(new BoolSetting.Builder().name("auto-take-off").defaultValue(false).onChanged(v -> pushSetting("auto-take-off", v)).build());
    public final Setting<Double> fallMultiplier = sg.add(new DoubleSetting.Builder().name("fall-multiplier").defaultValue(0.01).min(0).onChanged(v -> pushSetting("fall-multiplier", v)).build());
    public final Setting<Double> horizontalSpeed = sg.add(new DoubleSetting.Builder().name("horizontal-speed").defaultValue(1).min(0).onChanged(v -> pushSetting("horizontal-speed", v)).build());
    public final Setting<Double> verticalSpeed = sg.add(new DoubleSetting.Builder().name("vertical-speed").defaultValue(1).min(0).onChanged(v -> pushSetting("vertical-speed", v)).build());
    public final Setting<Boolean> acceleration = sg.add(new BoolSetting.Builder().name("acceleration").defaultValue(false).onChanged(v -> pushSetting("acceleration", v)).build());
    public final Setting<Double> accelerationStep = sg.add(new DoubleSetting.Builder().name("acceleration-step").min(0.1).max(5).defaultValue(1).onChanged(v -> pushSetting("acceleration-step", v)).build());
    public final Setting<Double> accelerationMin = sg.add(new DoubleSetting.Builder().name("acceleration-start").min(0.1).defaultValue(0).onChanged(v -> pushSetting("acceleration-start", v)).build());
    public final Setting<Boolean> stopInWater = sg.add(new BoolSetting.Builder().name("stop-in-water").defaultValue(true).onChanged(v -> pushSetting("stop-in-water", v)).build());
    public final Setting<Boolean> dontGoIntoUnloadedChunks = sg.add(new BoolSetting.Builder().name("no-unloaded-chunks").defaultValue(true).onChanged(v -> pushSetting("no-unloaded-chunks", v)).build());
    public final Setting<Boolean> autoHover = sg.add(new BoolSetting.Builder().name("auto-hover").defaultValue(false).onChanged(v -> pushSetting("auto-hover", v)).build());
    public final Setting<Boolean> noCrash = sg.add(new BoolSetting.Builder().name("no-crash").defaultValue(false).onChanged(v -> pushSetting("no-crash", v)).build());
    public final Setting<Integer> crashLookAhead = sg.add(new IntSetting.Builder().name("crash-look-ahead").defaultValue(5).min(1).sliderRange(1,15).onChanged(v -> pushSetting("crash-look-ahead", v)).build());

    public final Setting<Boolean> instaDrop = sg.add(new BoolSetting.Builder().name("insta-drop").defaultValue(false).onChanged(v -> pushSetting("insta-drop", v)).build());
    public final Setting<Double> pitch40lowerBounds = sg.add(new DoubleSetting.Builder().name("pitch40-lower-bounds").defaultValue(180).min(-128).sliderMax(360).onChanged(v -> pushSetting("pitch40-lower-bounds", v)).build());
    public final Setting<Double> pitch40upperBounds = sg.add(new DoubleSetting.Builder().name("pitch40-upper-bounds").defaultValue(220).min(-128).sliderMax(360).onChanged(v -> pushSetting("pitch40-upper-bounds", v)).build());
    public final Setting<Double> pitch40rotationSpeedUp = sg.add(new DoubleSetting.Builder().name("pitch40-rotate-speed-up").defaultValue(5.45).min(1).sliderMax(20).onChanged(v -> pushSetting("pitch40-rotate-speed-up", v)).build());
    public final Setting<Double> pitch40rotationSpeedDown = sg.add(new DoubleSetting.Builder().name("pitch40-rotate-speed-down").defaultValue(0.90).min(0.5).sliderMax(2).onChanged(v -> pushSetting("pitch40-rotate-speed-down", v)).build());

    public final Setting<Boolean> autoJump = sg.add(new BoolSetting.Builder().name("auto-jump").defaultValue(true).onChanged(v -> pushSetting("auto-jump", v)).build());
    public final Setting<Rotation.LockMode> yawLockMode = sg.add(new EnumSetting.Builder<Rotation.LockMode>().name("yaw-lock").defaultValue(Rotation.LockMode.Smart).onChanged(v -> pushSetting("yaw-lock", v.name())).build());
    public final Setting<Double> yaw = sg.add(new DoubleSetting.Builder().name("yaw").defaultValue(0).range(0,360).sliderRange(0,360).onChanged(v -> pushSetting("yaw", v)).build());
    public final Setting<Boolean> lockPitch = sg.add(new BoolSetting.Builder().name("pitch-lock").defaultValue(true).onChanged(v -> pushSetting("pitch-lock", v)).build());
    public final Setting<Double> pitch = sg.add(new DoubleSetting.Builder().name("pitch").defaultValue(85).range(0,90).sliderRange(0,90).onChanged(v -> pushSetting("pitch", v)).build());

    public final Setting<Boolean> restart = sg.add(new BoolSetting.Builder().name("restart").defaultValue(true).onChanged(v -> pushSetting("restart", v)).build());
    public final Setting<Integer> restartDelay = sg.add(new IntSetting.Builder().name("restart-delay").defaultValue(7).min(0).sliderRange(0,20).onChanged(v -> pushSetting("restart-delay", v)).build());
    public final Setting<Boolean> sprint = sg.add(new BoolSetting.Builder().name("sprint-constantly").defaultValue(true).onChanged(v -> pushSetting("sprint-constantly", v)).build());
    public final Setting<Boolean> manualTakeoff = sg.add(new BoolSetting.Builder().name("manual-takeoff").defaultValue(false).onChanged(v -> pushSetting("manual-takeoff", v)).build());

    public final Setting<Boolean> replace = sg.add(new BoolSetting.Builder().name("elytra-replace").defaultValue(false).onChanged(v -> pushSetting("elytra-replace", v)).build());
    public final Setting<Integer> replaceDurability = sg.add(new IntSetting.Builder().name("replace-durability").defaultValue(2).range(1, 432).onChanged(v -> pushSetting("replace-durability", v)).build());
    public final Setting<ChestSwapMode> chestSwap = sg.add(new EnumSetting.Builder<ChestSwapMode>().name("chest-swap").defaultValue(ChestSwapMode.Never).onChanged(v -> pushSetting("chest-swap", v.name())).build());
    public final Setting<Boolean> autoReplenish = sg.add(new BoolSetting.Builder().name("replenish-fireworks").defaultValue(false).onChanged(v -> pushSetting("replenish-fireworks", v)).build());
    public final Setting<Integer> replenishSlot = sg.add(new IntSetting.Builder().name("replenish-slot").defaultValue(9).range(1,9).sliderRange(1,9).onChanged(v -> pushSetting("replenish-slot", v)).build());
    public final Setting<Boolean> autoPilot = sg.add(new BoolSetting.Builder().name("auto-pilot").defaultValue(false).onChanged(v -> pushSetting("auto-pilot", v)).build());
    public final Setting<Boolean> useFireworks = sg.add(new BoolSetting.Builder().name("use-fireworks").defaultValue(false).onChanged(v -> pushSetting("use-fireworks", v)).build());
    public final Setting<Double> autoPilotFireworkDelay = sg.add(new DoubleSetting.Builder().name("firework-delay").defaultValue(8).min(1).sliderMax(20).onChanged(v -> pushSetting("firework-delay", v)).build());
    public final Setting<Double> autoPilotMinimumHeight = sg.add(new DoubleSetting.Builder().name("minimum-height").defaultValue(120).min(-128).sliderMax(260).onChanged(v -> pushSetting("minimum-height", v)).build());

    public SwarmElytraFly() {
        super("swarm-elytra-fly", "Proxy for elytra-fly", "elytra-fly");
    }

    public enum ChestSwapMode { Always, Never, WaitForGround }
}
