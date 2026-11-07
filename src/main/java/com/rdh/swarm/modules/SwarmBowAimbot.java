package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.utils.entity.SortPriority;

public class SwarmBowAimbot extends SwarmProxyModule {
    private static final byte[] _bos = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _boc = 0x726468;
    private static final long _bon = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(20).range(0,100).sliderMax(100).onChanged(v -> pushSetting("range", v)).build());
    public final EntityTypeListSetting entities = sg.add(new EntityTypeListSetting.Builder().name("entities").onlyAttackable().onChanged(v -> pushSetting("entities", v)).build());
    public final Setting<SortPriority> priority = sg.add(new EnumSetting.Builder<SortPriority>().name("priority").defaultValue(SortPriority.LowestHealth).onChanged(v -> pushSetting("priority", v.name())).build());
    public final Setting<Boolean> babies = sg.add(new BoolSetting.Builder().name("babies").defaultValue(true).onChanged(v -> pushSetting("babies", v)).build());
    public final Setting<Boolean> nametagged = sg.add(new BoolSetting.Builder().name("nametagged").defaultValue(false).onChanged(v -> pushSetting("nametagged", v)).build());
    public final Setting<Boolean> pauseOnCombat = sg.add(new BoolSetting.Builder().name("pause-on-combat").defaultValue(false).onChanged(v -> pushSetting("pause-on-combat", v)).build());

    public SwarmBowAimbot() {
        super("swarm-bow-aimbot", "Proxy for bow-aimbot", "bow-aimbot");
    }
}
