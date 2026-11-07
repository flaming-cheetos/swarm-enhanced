package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmArrowDodge extends SwarmProxyModule {
    private static final byte[] _ads = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _adc = 0x726468;
    private static final long _adn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum MoveType { Velocity, Packet }

    public final Setting<MoveType> moveType = sg.add(new EnumSetting.Builder<MoveType>().name("move-type").defaultValue(MoveType.Velocity).onChanged(v -> pushSetting("move-type", v.name().toLowerCase())).build());
    public final Setting<Double> moveSpeed = sg.add(new DoubleSetting.Builder().name("move-speed").defaultValue(1).min(0.01).sliderRange(0.01,5).onChanged(v -> pushSetting("move-speed", v)).build());
    public final Setting<Double> distanceCheck = sg.add(new DoubleSetting.Builder().name("distance-check").defaultValue(1).min(0.01).sliderRange(0.01,5).onChanged(v -> pushSetting("distance-check", v)).build());
    public final Setting<Boolean> groundCheck = sg.add(new BoolSetting.Builder().name("ground-check").defaultValue(true).onChanged(v -> pushSetting("ground-check", v)).build());
    public final Setting<Boolean> allProjectiles = sg.add(new BoolSetting.Builder().name("all-projectiles").defaultValue(false).onChanged(v -> pushSetting("all-projectiles", v)).build());
    public final Setting<Boolean> ignoreOwn = sg.add(new BoolSetting.Builder().name("ignore-own").defaultValue(true).onChanged(v -> pushSetting("ignore-own", v)).build());
    public final Setting<Integer> simulationSteps = sg.add(new IntSetting.Builder().name("simulation-steps").defaultValue(500).sliderMax(5000).onChanged(v -> pushSetting("simulation-steps", v)).build());

    public SwarmArrowDodge() {
        super("swarm-arrow-dodge", "Proxy for arrow-dodge", "arrow-dodge");
    }
}
