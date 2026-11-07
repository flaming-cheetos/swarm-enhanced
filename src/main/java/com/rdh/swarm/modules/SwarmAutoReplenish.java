package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.item.Item;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAutoReplenish extends SwarmProxyModule {
    private static final byte[] _ars = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _arc = 0x726468;
    private static final long _arn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Integer> minCount = sg.add(new IntSetting.Builder().name("min-count").defaultValue(8).min(1).sliderRange(1,63).onChanged(v -> pushSetting("min-count", v)).build());
    public final Setting<Integer> tickDelay = sg.add(new IntSetting.Builder().name("delay").defaultValue(1).min(0).onChanged(v -> pushSetting("delay", v)).build());
    public final Setting<Boolean> offhand = sg.add(new BoolSetting.Builder().name("offhand").defaultValue(true).onChanged(v -> pushSetting("offhand", v)).build());
    public final Setting<Boolean> unstackable = sg.add(new BoolSetting.Builder().name("unstackable").defaultValue(true).onChanged(v -> pushSetting("unstackable", v)).build());
    public final Setting<Boolean> sameEnchants = sg.add(new BoolSetting.Builder().name("same-enchants").defaultValue(true).visible(unstackable::get).onChanged(v -> pushSetting("same-enchants", v)).build());
    public final Setting<Boolean> searchHotbar = sg.add(new BoolSetting.Builder().name("search-hotbar").defaultValue(false).onChanged(v -> pushSetting("search-hotbar", v)).build());
    public final Setting<List<Item>> excludedItems = sg.add(new ItemListSetting.Builder().name("excluded-items").onChanged(v -> pushSetting("excluded-items", v)).build());

    public SwarmAutoReplenish() {
        super("swarm-auto-replenish", "Proxy for auto-replenish", "auto-replenish");
    }
}
