package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmAntiVoid extends SwarmProxyModule {
    private static final byte[] _avs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _avc = 0x726468;
    private static final long _avn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode {
        Flight,
        Jump
    }

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Jump).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());

    public SwarmAntiVoid() {
        super("swarm-anti-void", "Proxy for anti-void", "anti-void");
    }
}
