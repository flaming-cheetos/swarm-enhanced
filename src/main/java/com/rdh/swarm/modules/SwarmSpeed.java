package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.speed.SpeedModes;

public class SwarmSpeed extends SwarmProxyModule {
    private static final byte[] _sps = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _spc = 0x726468;
    private static final long _spn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<SpeedModes> speedMode = sg.add(new EnumSetting.Builder<SpeedModes>().name("mode").defaultValue(SpeedModes.Vanilla).onChanged(v -> pushSetting("mode", v.name())).build());
    public final Setting<Double> vanillaSpeed = sg.add(new DoubleSetting.Builder().name("vanilla-speed").defaultValue(5.6).min(0).sliderMax(20).onChanged(v -> pushSetting("vanilla-speed", v)).build());
    public final Setting<Double> ncpSpeed = sg.add(new DoubleSetting.Builder().name("strafe-speed").defaultValue(1.6).min(0).sliderMax(3).onChanged(v -> pushSetting("strafe-speed", v)).build());
    public final Setting<Boolean> ncpSpeedLimit = sg.add(new BoolSetting.Builder().name("speed-limit").defaultValue(false).onChanged(v -> pushSetting("speed-limit", v)).build());
    public final Setting<Double> timer = sg.add(new DoubleSetting.Builder().name("timer").defaultValue(1).min(0.01).sliderMin(0.01).sliderMax(10).onChanged(v -> pushSetting("timer", v)).build());
    public final Setting<Boolean> inLiquids = sg.add(new BoolSetting.Builder().name("in-liquids").defaultValue(false).onChanged(v -> pushSetting("in-liquids", v)).build());
    public final Setting<Boolean> whenSneaking = sg.add(new BoolSetting.Builder().name("when-sneaking").defaultValue(false).onChanged(v -> pushSetting("when-sneaking", v)).build());
    public final Setting<Boolean> vanillaOnGround = sg.add(new BoolSetting.Builder().name("only-on-ground").defaultValue(false).onChanged(v -> pushSetting("only-on-ground", v)).build());

    public SwarmSpeed() {
        super("swarm-speed", "Proxy for speed", "speed");
    }
}
