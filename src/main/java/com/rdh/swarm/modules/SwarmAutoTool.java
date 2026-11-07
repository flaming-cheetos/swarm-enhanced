package com.rdh.swarm.modules;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.item.Item;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAutoTool extends SwarmProxyModule {
    private static final byte[] _tls = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _tlc = 0x726468;
    private static final long _tln = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum EnchantPreference { None, Fortune, SilkTouch }
    public enum ListMode { Whitelist, Blacklist }

    public final Setting<EnchantPreference> prefer = sg.add(new EnumSetting.Builder<EnchantPreference>().name("prefer").defaultValue(EnchantPreference.Fortune).onChanged(v -> pushSetting("prefer", v.name().toLowerCase())).build());
    public final Setting<Boolean> silkTouchForEnderChest = sg.add(new BoolSetting.Builder().name("silk-touch-for-ender-chest").defaultValue(true).onChanged(v -> pushSetting("silk-touch-for-ender-chest", v)).build());
    public final Setting<Boolean> fortuneForOresCrops = sg.add(new BoolSetting.Builder().name("fortune-for-ores-and-crops").defaultValue(false).onChanged(v -> pushSetting("fortune-for-ores-and-crops", v)).build());
    public final Setting<Boolean> antiBreak = sg.add(new BoolSetting.Builder().name("anti-break").defaultValue(false).onChanged(v -> pushSetting("anti-break", v)).build());
    public final Setting<Integer> breakDurability = sg.add(new IntSetting.Builder().name("anti-break-percentage").defaultValue(10).range(1,100).sliderRange(1,100).visible(antiBreak::get).onChanged(v -> pushSetting("anti-break-percentage", v)).build());
    public final Setting<Boolean> switchBack = sg.add(new BoolSetting.Builder().name("switch-back").defaultValue(false).onChanged(v -> pushSetting("switch-back", v)).build());
    public final Setting<Integer> switchDelay = sg.add(new IntSetting.Builder().name("switch-delay").defaultValue(0).onChanged(v -> pushSetting("switch-delay", v)).build());

    public final Setting<ListMode> listMode = sg.add(new EnumSetting.Builder<ListMode>().name("list-mode").defaultValue(ListMode.Blacklist).onChanged(v -> pushSetting("list-mode", v.name().toLowerCase())).build());
    public final Setting<List<Item>> whitelist = sg.add(new ItemListSetting.Builder().name("whitelist").filter(item -> true).visible(() -> listMode.get() == ListMode.Whitelist).onChanged(v -> pushSetting("whitelist", v)).build());
    public final Setting<List<Item>> blacklist = sg.add(new ItemListSetting.Builder().name("blacklist").filter(item -> true).visible(() -> listMode.get() == ListMode.Blacklist).onChanged(v -> pushSetting("blacklist", v)).build());

    public SwarmAutoTool() {
        super("swarm-auto-tool", "Proxy for auto-tool", "auto-tool");
    }
}
