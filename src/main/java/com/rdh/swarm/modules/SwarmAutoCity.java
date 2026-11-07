package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;

public class SwarmAutoCity extends SwarmProxyModule {
    private static final byte[] _acs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _acc = 0x726468;
    private static final long _acn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(5.5).min(0).sliderMax(7).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Double> breakRange = sg.add(new DoubleSetting.Builder().name("break-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("break-range", v)).build());
    public enum SwitchMode { Normal, Silent }
    public final Setting<SwitchMode> switchMode = sg.add(new EnumSetting.Builder<SwitchMode>().name("switch-mode").defaultValue(SwitchMode.Normal).onChanged(v -> pushSetting("switch-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> support = sg.add(new BoolSetting.Builder().name("support").defaultValue(true).onChanged(v -> pushSetting("support", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());
    public final Setting<Boolean> chatInfo = sg.add(new BoolSetting.Builder().name("chat-info").defaultValue(true).onChanged(v -> pushSetting("chat-info", v)).build());

    public final Setting<Boolean> swingHand = sg.add(new BoolSetting.Builder().name("swing-hand").defaultValue(false).onChanged(v -> pushSetting("swing-hand", v)).build());
    public final Setting<Boolean> renderBlock = sg.add(new BoolSetting.Builder().name("render-block").defaultValue(true).onChanged(v -> pushSetting("render-block", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).onChanged(v -> pushSetting("shape-mode", v.name())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(225,0,0,75)).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(225,0,0,255)).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmAutoCity() {
        super("swarm-auto-city", "Proxy for auto-city", "auto-city");
    }
}
