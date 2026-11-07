package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import net.minecraft.world.level.block.Block;
import java.util.List;

public class SwarmAutoTrap extends SwarmProxyModule {
    private static final byte[] _tps = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _tpc = 0x726468;
    private static final long _tpn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum TopMode {Full, None}
    public enum BottomMode {Platform, None}

    public final BlockListSetting blocks = sg.add(new BlockListSetting.Builder().name("whitelist").onChanged(v -> pushSetting("whitelist", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Double> placeWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<SortPriority> priority = sg.add(new EnumSetting.Builder<SortPriority>().name("target-priority").defaultValue(SortPriority.LowestHealth).onChanged(v -> pushSetting("target-priority", v.name().toLowerCase())).build());
    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(3).min(0).sliderMax(10).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("place-delay").defaultValue(1).onChanged(v -> pushSetting("place-delay", v)).build());
    public final Setting<Integer> blocksPerTick = sg.add(new IntSetting.Builder().name("blocks-per-tick").defaultValue(1).min(1).onChanged(v -> pushSetting("blocks-per-tick", v)).build());

    public final Setting<TopMode> topPlacement = sg.add(new EnumSetting.Builder<TopMode>().name("top-blocks").defaultValue(TopMode.Full).onChanged(v -> pushSetting("top-blocks", v.name().toLowerCase())).build());
    public final Setting<BottomMode> bottomPlacement = sg.add(new EnumSetting.Builder<BottomMode>().name("bottom-blocks").defaultValue(BottomMode.Platform).onChanged(v -> pushSetting("bottom-blocks", v.name().toLowerCase())).build());
    public final Setting<Boolean> selfToggle = sg.add(new BoolSetting.Builder().name("self-toggle").defaultValue(true).onChanged(v -> pushSetting("self-toggle", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());

    // Render
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).visible(render::get).onChanged(v -> pushSetting("shape-mode", v.name().toLowerCase())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(197,137,232,10)).visible(() -> render.get() && shapeMode.get().sides()).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(197,137,232)).visible(() -> render.get() && shapeMode.get().lines()).onChanged(v -> pushSetting("line-color", v)).build());
    public final Setting<SettingColor> nextSideColor = sg.add(new ColorSetting.Builder().name("next-side-color").defaultValue(new SettingColor(227,196,245,10)).visible(() -> render.get() && shapeMode.get().sides()).onChanged(v -> pushSetting("next-side-color", v)).build());
    public final Setting<SettingColor> nextLineColor = sg.add(new ColorSetting.Builder().name("next-line-color").defaultValue(new SettingColor(5,139,221)).visible(() -> render.get() && shapeMode.get().lines()).onChanged(v -> pushSetting("next-line-color", v)).build());

    public SwarmAutoTrap() {
        super("swarm-auto-trap", "Proxy for auto-trap", "auto-trap");
    }
}
