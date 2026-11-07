package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmNoSlow extends SwarmProxyModule {
    private static final byte[] _nss = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _nsc = 0x726468;
    private static final long _nsn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum WebMode { Vanilla, Timer, None }

    public final Setting<Boolean> items = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("items").defaultValue(true).onChanged(v -> pushSetting("items", v)).build());
    public final Setting<WebMode> web = sg.add(new EnumSetting.Builder<WebMode>().name("web").defaultValue(WebMode.Vanilla).onChanged(v -> pushSetting("web", v.name())).build());
    public final Setting<Double> webTimer = sg.add(new DoubleSetting.Builder().name("web-timer").defaultValue(10).min(1).onChanged(v -> pushSetting("web-timer", v)).build());
    public final Setting<Boolean> honeyBlock = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("honey-block").defaultValue(true).onChanged(v -> pushSetting("honey-block", v)).build());
    public final Setting<Boolean> soulSand = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("soul-sand").defaultValue(true).onChanged(v -> pushSetting("soul-sand", v)).build());
    public final Setting<Boolean> slimeBlock = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("slime-block").defaultValue(true).onChanged(v -> pushSetting("slime-block", v)).build());
    public final Setting<Boolean> berryBush = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("berry-bush").defaultValue(true).onChanged(v -> pushSetting("berry-bush", v)).build());
    public final Setting<Boolean> airStrict = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("air-strict").defaultValue(false).onChanged(v -> pushSetting("air-strict", v)).build());
    public final Setting<Boolean> fluidDrag = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("fluid-drag").defaultValue(false).onChanged(v -> pushSetting("fluid-drag", v)).build());
    public final Setting<Boolean> sneaking = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("sneaking").defaultValue(false).onChanged(v -> pushSetting("sneaking", v)).build());
    public final Setting<Boolean> hunger = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("hunger").defaultValue(false).onChanged(v -> pushSetting("hunger", v)).build());
    public final Setting<Boolean> slowness = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("slowness").defaultValue(false).onChanged(v -> pushSetting("slowness", v)).build());

    public SwarmNoSlow() {
        super("swarm-no-slow", "Proxy for no-slow", "no-slow");
    }
}
