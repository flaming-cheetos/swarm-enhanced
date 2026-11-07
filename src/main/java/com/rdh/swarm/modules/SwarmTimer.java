package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmTimer extends SwarmProxyModule {
    private static final byte[] _tms = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x31,0x73};
    private static final int _tmc = 0x726468;
    private static final long _tmn = 0x74696D6572L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> multiplier = sg.add(new DoubleSetting.Builder().name("multiplier").defaultValue(1).min(0.1).sliderMin(0.1).onChanged(v -> pushSetting("multiplier", v)).build());

    public SwarmTimer() { super("swarm-timer", "Proxy for timer", "timer"); }
}
