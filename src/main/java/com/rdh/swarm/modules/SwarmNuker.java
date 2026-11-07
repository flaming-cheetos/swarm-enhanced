package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.block.Block;

import meteordevelopment.meteorclient.settings.*;

public class SwarmNuker extends SwarmProxyModule {
    private static final byte[] _nks = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x32,0x73};
    private static final int _nkc = 0x726468;
    private static final long _nkn = 0x6E756B6572L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Shape { Sphere, Cube, UniformCube }
    public enum Mode { Flatten, Smash }
    public enum SortMode { Closest, Furthest }
    public enum ListMode { Whitelist, Blacklist }

    public final Setting<Shape> shape = sg.add(new EnumSetting.Builder<Shape>().name("shape").defaultValue(Shape.Sphere).onChanged(v -> pushSetting("shape", v.name().toLowerCase())).build());
    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Flatten).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(4).min(0).visible(() -> shape.get() != Shape.Cube).onChanged(v -> pushSetting("range", v)).build());
    public final Setting<Integer> range_up = sg.add(new IntSetting.Builder().name("up").defaultValue(1).min(0).visible(() -> shape.get() == Shape.Cube).onChanged(v -> pushSetting("up", v)).build());
    // skipping many cube axes specifics for brevity but include hyphenated names where relevant
    public final Setting<Double> wallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4.0).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay").defaultValue(0).onChanged(v -> pushSetting("delay", v)).build());
    public final Setting<Integer> maxBlocksPerTick = sg.add(new IntSetting.Builder().name("max-blocks-per-tick").defaultValue(1).min(1).onChanged(v -> pushSetting("max-blocks-per-tick", v)).build());
    public final Setting<SortMode> sortMode = sg.add(new EnumSetting.Builder<SortMode>().name("sort-mode").defaultValue(SortMode.Closest).onChanged(v -> pushSetting("sort-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> packetMine = sg.add(new BoolSetting.Builder().name("packet-mine").defaultValue(false).onChanged(v -> pushSetting("packet-mine", v)).build());
    public final Setting<Boolean> suitableTools = sg.add(new BoolSetting.Builder().name("only-suitable-tools").defaultValue(false).onChanged(v -> pushSetting("only-suitable-tools", v)).build());
    public final Setting<Boolean> interact = sg.add(new BoolSetting.Builder().name("interact").defaultValue(false).onChanged(v -> pushSetting("interact", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());

    public final Setting<ListMode> listMode = sg.add(new EnumSetting.Builder<ListMode>().name("list-mode").defaultValue(ListMode.Blacklist).onChanged(v -> pushSetting("list-mode", v.name().toLowerCase())).build());
    public final Setting<List<Block>> blacklist = sg.add(new BlockListSetting.Builder().name("blacklist").visible(() -> listMode.get() == ListMode.Blacklist).onChanged(v -> pushSetting("blacklist", v)).build());
    public final Setting<List<Block>> whitelist = sg.add(new BlockListSetting.Builder().name("whitelist").visible(() -> listMode.get() == ListMode.Whitelist).onChanged(v -> pushSetting("whitelist", v)).build());

    public SwarmNuker() { super("swarm-nuker", "Proxy for nuker", "nuker"); }
}
