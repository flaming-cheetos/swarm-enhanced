package com.rdh.swarm.modules;

public class SwarmNoRotate extends SwarmProxyModule {
    private static final byte[] _nrs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _nrc = 0x726468;
    private static final long _nrn = 0x666C616D696E67L;
    public SwarmNoRotate() {
        super("swarm-no-rotate", "Proxy for no-rotate", "no-rotate");
    }
}
