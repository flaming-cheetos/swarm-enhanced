package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmJesus extends SwarmProxyModule {
    private static final byte[] _jss = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x34,0x73};
    private static final int _jsc = 0x726468;
    private static final long _jsn = 0x6A65737573L;
    private final SettingGroup sg = settings.createGroup("General");

    public final Setting<Boolean> powderSnow = sg.add(new BoolSetting.Builder().name("powder-snow").defaultValue(true).onChanged(v -> pushSetting("powder-snow", v)).build());
    public final Setting<Boolean> ncpBypass = sg.add(new BoolSetting.Builder().name("ncp-bypass").defaultValue(false).onChanged(v -> pushSetting("ncp-bypass", v)).build());
    public final Setting<Boolean> slowDown = sg.add(new BoolSetting.Builder().name("slow-down").defaultValue(false).visible(ncpBypass::get).onChanged(v -> pushSetting("slow-down", v)).build());

    public final Setting<Mode> waterMode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Solid).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> dipIfBurning = sg.add(new BoolSetting.Builder().name("dip-if-burning").defaultValue(true).visible(() -> waterMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-if-burning", v)).build());
    public final Setting<Boolean> dipOnSneakWater = sg.add(new BoolSetting.Builder().name("dip-on-sneak").defaultValue(true).visible(() -> waterMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-on-sneak", v)).build());
    public final Setting<Boolean> dipOnFallWater = sg.add(new BoolSetting.Builder().name("dip-on-fall").defaultValue(true).visible(() -> waterMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-on-fall", v)).build());
    public final Setting<Integer> dipFallHeightWater = sg.add(new IntSetting.Builder().name("dip-fall-height").defaultValue(4).range(1,255).sliderRange(3,20).visible(() -> waterMode.get() == Mode.Solid && dipOnFallWater.get()).onChanged(v -> pushSetting("dip-fall-height", v)).build());

    public final Setting<Mode> lavaMode = sg.add(new EnumSetting.Builder<Mode>().name("lava-mode").defaultValue(Mode.Solid).onChanged(v -> pushSetting("lava-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> dipIfFireResistant = sg.add(new BoolSetting.Builder().name("dip-if-resistant").defaultValue(true).visible(() -> lavaMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-if-resistant", v)).build());
    public final Setting<Boolean> dipOnSneakLava = sg.add(new BoolSetting.Builder().name("dip-on-sneak").defaultValue(true).visible(() -> lavaMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-on-sneak", v)).build());
    public final Setting<Boolean> dipOnFallLava = sg.add(new BoolSetting.Builder().name("dip-on-fall").defaultValue(true).visible(() -> lavaMode.get() == Mode.Solid).onChanged(v -> pushSetting("dip-on-fall", v)).build());
    public final Setting<Integer> dipFallHeightLava = sg.add(new IntSetting.Builder().name("dip-fall-height-lava").defaultValue(4).range(1,255).sliderRange(3,20).visible(() -> lavaMode.get() == Mode.Solid && dipOnFallLava.get()).onChanged(v -> pushSetting("dip-fall-height-lava", v)).build());

    public SwarmJesus() { super("swarm-jesus", "Proxy for jesus", "jesus"); }

    public enum Mode { Solid, Ignore }
}
