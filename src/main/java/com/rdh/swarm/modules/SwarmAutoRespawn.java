package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAutoRespawn extends SwarmProxyModule {
    private static final byte[] _ars = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x33,0x73};
    private static final int _arc = 0x726468;
    private static final long _arn = 0x6175746FL;
    private final SettingGroup sg = settings.getDefaultGroup();

    public SwarmAutoRespawn() { super("swarm-auto-respawn", "Proxy for auto-respawn", "auto-respawn"); }
}
