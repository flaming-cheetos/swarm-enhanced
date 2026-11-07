package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmAutoExp extends SwarmProxyModule {
    private static final byte[] _aes = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _aec = 0x726468;
    private static final long _aen = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum ExpMode {
        Armor,
        Hands,
        Both
    }

    public final Setting<ExpMode> mode = sg.add(new EnumSetting.Builder<ExpMode>().name("mode").defaultValue(ExpMode.Both).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> replenish = sg.add(new BoolSetting.Builder().name("replenish").defaultValue(true).onChanged(v -> pushSetting("replenish", v)).build());
    public final Setting<Boolean> onlyGround = sg.add(new BoolSetting.Builder().name("only-on-ground").defaultValue(false).onChanged(v -> pushSetting("only-on-ground", v)).build());
    public final Setting<Integer> slot = sg.add(new IntSetting.Builder().name("exp-slot").defaultValue(6).range(1,9).onChanged(v -> pushSetting("exp-slot", v)).build());
    public final Setting<Integer> minThreshold = sg.add(new IntSetting.Builder().name("min-threshold").defaultValue(30).range(1,100).onChanged(v -> pushSetting("min-threshold", v)).build());
    public final Setting<Integer> maxThreshold = sg.add(new IntSetting.Builder().name("max-threshold").defaultValue(80).range(1,100).onChanged(v -> pushSetting("max-threshold", v)).build());

    public SwarmAutoExp() {
        super("swarm-auto-exp", "Proxy for auto-exp", "auto-exp");
    }
}
