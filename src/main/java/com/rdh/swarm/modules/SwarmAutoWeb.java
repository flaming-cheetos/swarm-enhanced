package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.List;

public class SwarmAutoWeb extends SwarmProxyModule {
    private static final byte[] _aws = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _awc = 0x726468;
    private static final long _awn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Double> placeWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<SortPriority> priority = sg.add(new EnumSetting.Builder<SortPriority>().name("target-priority").defaultValue(SortPriority.LowestDistance).onChanged(v -> pushSetting("target-priority", v.name().toLowerCase())).build());
    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(10).min(0).sliderMax(30).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Boolean> predictMovement = sg.add(new BoolSetting.Builder().name("predict-movement").defaultValue(true).onChanged(v -> pushSetting("predict-movement", v)).build());
    public final Setting<Double> ticksToPredict = sg.add(new DoubleSetting.Builder().name("ticks-to-predict").defaultValue(10).min(1).sliderMax(30).visible(predictMovement::get).onChanged(v -> pushSetting("ticks-to-predict", v)).build());
    public final Setting<Boolean> doubles = sg.add(new BoolSetting.Builder().name("doubles").defaultValue(false).onChanged(v -> pushSetting("doubles", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());

    // Render
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).visible(render::get).onChanged(v -> pushSetting("shape-mode", v.name().toLowerCase())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(239,231,244,31)).visible(() -> render.get() && shapeMode.get().sides()).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255,255,255)).visible(() -> render.get() && shapeMode.get().lines()).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmAutoWeb() {
        super("swarm-auto-web", "Proxy for auto-web", "auto-web");
    }
}
