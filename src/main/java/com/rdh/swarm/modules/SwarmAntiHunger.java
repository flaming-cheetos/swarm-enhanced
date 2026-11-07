package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAntiHunger extends SwarmProxyModule {
    private static final byte[] _ahs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _ahc = 0x726468;
    private static final long _ahn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Boolean> sprint = sg.add(new BoolSetting.Builder().name("sprint").defaultValue(true).onChanged(v -> pushSetting("sprint", v)).build());
    public final Setting<Boolean> onGround = sg.add(new BoolSetting.Builder().name("on-ground").defaultValue(true).onChanged(v -> pushSetting("on-ground", v)).build());

    public SwarmAntiHunger() { super("swarm-anti-hunger", "Proxy for anti-hunger", "anti-hunger"); }
}
