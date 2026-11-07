package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.ColorSetting;

public class SwarmAnchorAura extends SwarmProxyModule {
    private static final byte[] _ans = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _anc = 0x726468;
    private static final long _ann = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(10).min(0).sliderMax(16).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Double> minDamage = sg.add(new DoubleSetting.Builder().name("min-damage").defaultValue(7).min(0).sliderMax(36).onChanged(v -> pushSetting("min-damage", v)).build());
    public final Setting<Double> maxSelfDamage = sg.add(new DoubleSetting.Builder().name("max-self-damage").defaultValue(7).min(0).sliderMax(36).onChanged(v -> pushSetting("max-self-damage", v)).build());
    public final Setting<Boolean> antiSuicide = sg.add(new BoolSetting.Builder().name("anti-suicide").defaultValue(true).onChanged(v -> pushSetting("anti-suicide", v)).build());
    public final Setting<Boolean> swapBack = sg.add(new BoolSetting.Builder().name("swap-back").defaultValue(true).onChanged(v -> pushSetting("swap-back", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());

    public final Setting<Boolean> place = sg.add(new BoolSetting.Builder().name("place").defaultValue(true).onChanged(v -> pushSetting("place", v)).build());
    public final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder().name("place-delay").defaultValue(5).range(0,10).onChanged(v -> pushSetting("place-delay", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Double> placeWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<Boolean> airPlace = sg.add(new BoolSetting.Builder().name("air-place").defaultValue(true).onChanged(v -> pushSetting("air-place", v)).build());

    public final Setting<Integer> chargeDelay = sg.add(new IntSetting.Builder().name("charge-delay").defaultValue(1).range(0,10).onChanged(v -> pushSetting("charge-delay", v)).build());
    public final Setting<Integer> breakDelay = sg.add(new IntSetting.Builder().name("break-delay").defaultValue(1).range(0,10).onChanged(v -> pushSetting("break-delay", v)).build());
    public final Setting<Double> breakRange = sg.add(new DoubleSetting.Builder().name("break-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("break-range", v)).build());
    public final Setting<Double> breakWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());

    public final Setting<Boolean> swing = sg.add(new BoolSetting.Builder().name("swing").defaultValue(true).onChanged(v -> pushSetting("swing", v)).build());
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).onChanged(v -> pushSetting("shape-mode", v.name())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(15,255,211,41)).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(15,255,211)).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmAnchorAura() {
        super("swarm-anchor-aura", "Proxy for anchor-aura", "anchor-aura");
    }
}
