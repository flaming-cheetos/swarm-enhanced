package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmSprint extends SwarmProxyModule {
    private static final byte[] _srs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _src = 0x726468;
    private static final long _srn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("sprint-mode").defaultValue(Mode.Strict).onChanged(v -> pushSetting("sprint-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> keepSprint = sg.add(new BoolSetting.Builder().name("keep-sprint").defaultValue(false).onChanged(v -> pushSetting("keep-sprint", v)).build());
    public final Setting<Boolean> unsprintOnHit = sg.add(new BoolSetting.Builder().name("unsprint-on-hit").defaultValue(false).onChanged(v -> pushSetting("unsprint-on-hit", v)).build());
    public final Setting<Boolean> unsprintInWater = sg.add(new BoolSetting.Builder().name("unsprint-in-water").defaultValue(true).visible(() -> mode.get() == Mode.Rage).onChanged(v -> pushSetting("unsprint-in-water", v)).build());
    public final Setting<Boolean> permaSprint = sg.add(new BoolSetting.Builder().name("sprint-while-stationary").defaultValue(false).visible(() -> mode.get() == Mode.Rage).onChanged(v -> pushSetting("sprint-while-stationary", v)).build());

    public SwarmSprint() {
        super("swarm-sprint", "Proxy for sprint", "sprint");
    }
}
