package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmFlight extends SwarmProxyModule {
    private static final byte[] _fls = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _flc = 0x726468;
    private static final long _fln = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Abilities).onChanged(v -> pushSetting("mode", v.name())).build());
    public final Setting<Double> speed = sg.add(new DoubleSetting.Builder().name("speed").defaultValue(0.1).min(0.0).onChanged(v -> pushSetting("speed", v)).build());
    public final Setting<Boolean> verticalSpeedMatch = sg.add(new BoolSetting.Builder().name("vertical-speed-match").defaultValue(false).onChanged(v -> pushSetting("vertical-speed-match", v)).build());
    public final Setting<Boolean> noSneak = sg.add(new BoolSetting.Builder().name("no-sneak").defaultValue(false).onChanged(v -> pushSetting("no-sneak", v)).build());

    public final Setting<AntiKickMode> antiKickMode = sg.add(new EnumSetting.Builder<AntiKickMode>().name("mode").defaultValue(AntiKickMode.Packet).onChanged(v -> pushSetting("anti-kick-mode", v.name())).build());
    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay").defaultValue(20).min(1).sliderMax(200).onChanged(v -> pushSetting("delay", v)).build());
    public final Setting<Integer> offTime = sg.add(new IntSetting.Builder().name("off-time").defaultValue(1).min(1).sliderRange(1,20).onChanged(v -> pushSetting("off-time", v)).build());

    public SwarmFlight() {
        super("swarm-flight", "Proxy for flight", "flight");
    }

    public enum Mode { Abilities, Velocity }
    public enum AntiKickMode { Normal, Packet, None }
}
