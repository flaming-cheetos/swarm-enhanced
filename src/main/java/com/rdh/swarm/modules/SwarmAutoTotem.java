package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmAutoTotem extends SwarmProxyModule {
    private static final byte[] _aos = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _aoc = 0x726468;
    private static final long _aon = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum TotemMode {
        Smart,
        Strict
    }

    public final Setting<TotemMode> mode = sg.add(new EnumSetting.Builder<TotemMode>().name("mode").defaultValue(TotemMode.Smart).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final meteordevelopment.meteorclient.settings.IntSetting delay = sg.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("delay").defaultValue(0).min(0).onChanged(v -> pushSetting("delay", v)).build());
    public final meteordevelopment.meteorclient.settings.IntSetting health = sg.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder().name("health").defaultValue(10).range(0,36).onChanged(v -> pushSetting("health", v)).build());
    public final meteordevelopment.meteorclient.settings.BoolSetting elytra = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("elytra").defaultValue(true).onChanged(v -> pushSetting("elytra", v)).build());
    public final meteordevelopment.meteorclient.settings.BoolSetting fall = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("fall").defaultValue(true).onChanged(v -> pushSetting("fall", v)).build());
    public final meteordevelopment.meteorclient.settings.BoolSetting explosion = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("explosion").defaultValue(true).onChanged(v -> pushSetting("explosion", v)).build());

    public SwarmAutoTotem() {
        super("swarm-auto-totem", "Proxy for auto-totem", "auto-totem");
    }
}
