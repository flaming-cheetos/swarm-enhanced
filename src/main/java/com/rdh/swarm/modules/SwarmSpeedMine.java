package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.world.level.block.Block;

import meteordevelopment.meteorclient.settings.*;

public class SwarmSpeedMine extends SwarmProxyModule {
    private static final byte[] _sms = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _smc = 0x726468;
    private static final long _smn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode { Normal, Haste, Damage }
    public enum ListMode { Whitelist, Blacklist }

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Damage).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final Setting<List<Block>> blocks = sg.add(new BlockListSetting.Builder().name("blocks").filter(block -> true).visible(() -> mode.get() != Mode.Haste).onChanged(v -> pushSetting("blocks", v)).build());
    public final Setting<ListMode> blocksFilter = sg.add(new EnumSetting.Builder<ListMode>().name("blocks-filter").defaultValue(ListMode.Blacklist).visible(() -> mode.get() != Mode.Haste).onChanged(v -> pushSetting("blocks-filter", v.name().toLowerCase())).build());
    public final Setting<Double> modifier = sg.add(new DoubleSetting.Builder().name("modifier").defaultValue(1.4).visible(() -> mode.get() == Mode.Normal).min(0).onChanged(v -> pushSetting("modifier", v)).build());
    public final Setting<Integer> hasteAmplifier = sg.add(new IntSetting.Builder().name("haste-amplifier").defaultValue(2).min(1).visible(() -> mode.get() == Mode.Haste).onChanged(v -> pushSetting("haste-amplifier", v)).build());
    public final Setting<Boolean> instamine = sg.add(new BoolSetting.Builder().name("instamine").defaultValue(true).visible(() -> mode.get() == Mode.Damage).onChanged(v -> pushSetting("instamine", v)).build());
    public final Setting<Boolean> grimBypass = sg.add(new BoolSetting.Builder().name("grim-bypass").defaultValue(false).visible(() -> mode.get() == Mode.Damage).onChanged(v -> pushSetting("grim-bypass", v)).build());

    public SwarmSpeedMine() { super("swarm-speed-mine", "Proxy for speed-mine", "speed-mine"); }
}
