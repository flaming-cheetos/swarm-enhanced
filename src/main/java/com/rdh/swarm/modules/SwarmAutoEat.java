package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.item.Item;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAutoEat extends SwarmProxyModule {
    private static final byte[] _eas = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _eac = 0x726468;
    private static final long _ean = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<List<Item>> blacklist = sg.add(new ItemListSetting.Builder().name("blacklist").filter(item -> true).onChanged(v -> pushSetting("blacklist", v)).build());
    public final Setting<Boolean> pauseAuras = sg.add(new BoolSetting.Builder().name("pause-auras").defaultValue(true).onChanged(v -> pushSetting("pause-auras", v)).build());
    public final Setting<Boolean> pauseBaritone = sg.add(new BoolSetting.Builder().name("pause-baritone").defaultValue(true).onChanged(v -> pushSetting("pause-baritone", v)).build());
    public final Setting<Boolean> searchInventory = sg.add(new BoolSetting.Builder().name("search-inventory").defaultValue(false).onChanged(v -> pushSetting("search-inventory", v)).build());
    public final Setting<Priority> prioritise = sg.add(new EnumSetting.Builder<Priority>().name("food-priority").defaultValue(Priority.Saturation).onChanged(v -> pushSetting("food-priority", v.name().toLowerCase())).build());

    public final Setting<ThresholdMode> thresholdMode = sg.add(new EnumSetting.Builder<ThresholdMode>().name("threshold-mode").defaultValue(ThresholdMode.Any).onChanged(v -> pushSetting("threshold-mode", v.name().toLowerCase())).build());
    public final Setting<Double> healthThreshold = sg.add(new DoubleSetting.Builder().name("health-threshold").defaultValue(10).range(1,19).sliderRange(1,19).visible(() -> thresholdMode.get() != ThresholdMode.Hunger).onChanged(v -> pushSetting("health-threshold", v)).build());
    public final Setting<Integer> hungerThreshold = sg.add(new IntSetting.Builder().name("hunger-threshold").defaultValue(16).range(1,19).sliderRange(1,19).visible(() -> thresholdMode.get() != ThresholdMode.Health).onChanged(v -> pushSetting("hunger-threshold", v)).build());

    public SwarmAutoEat() {
        super("swarm-auto-eat", "Proxy for auto-eat", "auto-eat");
    }

    public enum ThresholdMode { Health, Hunger, Any, Both }
    public enum Priority { Combined, Hunger, Saturation }
}
