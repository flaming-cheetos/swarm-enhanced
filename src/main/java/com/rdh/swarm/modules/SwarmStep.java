package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmStep extends SwarmProxyModule {
    private static final byte[] _sts = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _stc = 0x726468;
    private static final long _stn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum ActiveWhen {
        Always,
        Sneaking,
        NotSneaking
    }

    public final Setting<Double> height = sg.add(new DoubleSetting.Builder().name("height").defaultValue(1.25).min(0).onChanged(v -> pushSetting("height", v)).build());
    public final Setting<ActiveWhen> activeWhen = sg.add(new EnumSetting.Builder<ActiveWhen>().name("active-when").defaultValue(ActiveWhen.Always).onChanged(v -> pushSetting("active-when", v.name().toLowerCase())).build());
    public final Setting<Boolean> safeStep = sg.add(new BoolSetting.Builder().name("safe-step").defaultValue(false).onChanged(v -> pushSetting("safe-step", v)).build());
    public final Setting<Integer> stepHealth = sg.add(new IntSetting.Builder().name("step-health").defaultValue(5).range(1,36).sliderRange(1,36).visible(safeStep::get).onChanged(v -> pushSetting("step-health", v)).build());

    public SwarmStep() {
        super("swarm-step", "Proxy for step", "step");
    }
}
