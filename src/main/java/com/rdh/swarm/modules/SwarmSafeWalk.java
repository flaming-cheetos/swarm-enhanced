package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmSafeWalk extends SwarmProxyModule {
    private static final byte[] _sws = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _swc = 0x726468;
    private static final long _swn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Integer> fallDistance = sg.add(new IntSetting.Builder().name("minimum-fall-distance").defaultValue(1).min(1).onChanged(v -> pushSetting("minimum-fall-distance", v)).build());
    public final Setting<Boolean> sneak = sg.add(new BoolSetting.Builder().name("sneak").defaultValue(false).onChanged(v -> pushSetting("sneak", v)).build());
    public final Setting<Boolean> safeSneak = sg.add(new BoolSetting.Builder().name("safe-sneak").defaultValue(true).visible(sneak::get).onChanged(v -> pushSetting("safe-sneak", v)).build());
    public final Setting<Boolean> sneakSprint = sg.add(new BoolSetting.Builder().name("sneak-on-sprint").defaultValue(true).visible(sneak::get).onChanged(v -> pushSetting("sneak-on-sprint", v)).build());
    public final Setting<Double> edgeDistance = sg.add(new DoubleSetting.Builder().name("edge-distance").defaultValue(0.30).sliderRange(0.00, 0.30).decimalPlaces(2).visible(sneak::get).onChanged(v -> pushSetting("edge-distance", v)).build());

    public final Setting<Boolean> renderEdgeDistance = sg.add(new BoolSetting.Builder().name("render").defaultValue(false).visible(sneak::get).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<Boolean> renderPlayerBox = sg.add(new BoolSetting.Builder().name("render-player-box").defaultValue(false).visible(() -> sneak.get() && renderEdgeDistance.get()).onChanged(v -> pushSetting("render-player-box", v)).build());

    public SwarmSafeWalk() {
        super("swarm-safe-walk", "Proxy for safe-walk", "safe-walk");
    }
}
