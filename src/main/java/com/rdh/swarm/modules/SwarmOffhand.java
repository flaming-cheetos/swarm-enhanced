package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmOffhand extends SwarmProxyModule {
    private static final byte[] _ohs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _ohc = 0x726468;
    private static final long _ohn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Item { EGap, Gap, Crystal, Totem, Shield, Potion }

    public final Setting<Integer> delayTicks = sg.add(new IntSetting.Builder().name("item-switch-delay").defaultValue(0).min(0).sliderMax(20).onChanged(v -> pushSetting("item-switch-delay", v)).build());
    public final Setting<Item> preferreditem = sg.add(new EnumSetting.Builder<Item>().name("item").defaultValue(Item.Crystal).onChanged(v -> pushSetting("item", v.name().toLowerCase())).build());
    public final Setting<Boolean> hotbar = sg.add(new BoolSetting.Builder().name("hotbar").defaultValue(false).onChanged(v -> pushSetting("hotbar", v)).build());
    public final Setting<Boolean> rightgapple = sg.add(new BoolSetting.Builder().name("right-gapple").defaultValue(false).onChanged(v -> pushSetting("right-gapple", v)).build());
    public final Setting<Boolean> swordGap = sg.add(new BoolSetting.Builder().name("sword-gapple").defaultValue(false).onChanged(v -> pushSetting("sword-gapple", v)).build());
    public final Setting<Boolean> alwaysSwordGap = sg.add(new BoolSetting.Builder().name("always-gap-on-sword").defaultValue(false).onChanged(v -> pushSetting("always-gap-on-sword", v)).build());
    public final Setting<Boolean> alwaysPot = sg.add(new BoolSetting.Builder().name("always-pot-on-sword").defaultValue(false).onChanged(v -> pushSetting("always-pot-on-sword", v)).build());
    public final Setting<Boolean> potionClick = sg.add(new BoolSetting.Builder().name("sword-pot").defaultValue(false).onChanged(v -> pushSetting("sword-pot", v)).build());

    public final Setting<Double> minHealth = sg.add(new DoubleSetting.Builder().name("min-health").defaultValue(10).range(0,36).sliderRange(0,36).onChanged(v -> pushSetting("min-health", v)).build());
    public final Setting<Boolean> elytra = sg.add(new BoolSetting.Builder().name("elytra").defaultValue(false).onChanged(v -> pushSetting("elytra", v)).build());
    public final Setting<Boolean> falling = sg.add(new BoolSetting.Builder().name("falling").defaultValue(false).onChanged(v -> pushSetting("falling", v)).build());
    public final Setting<Boolean> explosion = sg.add(new BoolSetting.Builder().name("explosion").defaultValue(true).onChanged(v -> pushSetting("explosion", v)).build());

    public SwarmOffhand() {
        super("swarm-offhand", "Proxy for offhand", "offhand");
    }
}
