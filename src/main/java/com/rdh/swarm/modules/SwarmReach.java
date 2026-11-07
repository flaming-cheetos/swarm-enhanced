package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmReach extends SwarmProxyModule {
    private static final byte[] _res = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _rec = 0x726468;
    private static final long _ren = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> blockReach = sg.add(new DoubleSetting.Builder().name("extra-block-reach").sliderMax(1).onChanged(v -> pushSetting("extra-block-reach", v)).build());
    public final Setting<Double> entityReach = sg.add(new DoubleSetting.Builder().name("extra-entity-reach").sliderMax(1).onChanged(v -> pushSetting("extra-entity-reach", v)).build());

    public SwarmReach() {
        super("swarm-reach", "Proxy for reach", "reach");
    }
}
