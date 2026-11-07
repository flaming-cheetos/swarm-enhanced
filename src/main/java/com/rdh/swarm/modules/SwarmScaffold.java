package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.world.level.block.Block;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class SwarmScaffold extends SwarmProxyModule {
    private static final byte[] _scs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _scc = 0x726468;
    private static final long _scn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public final Setting<List<Block>> blocks = sg.add(new BlockListSetting.Builder().name("blocks").onChanged(v -> pushSetting("blocks", v)).build());
    public final Setting<ListMode> blocksFilter = sg.add(new EnumSetting.Builder<ListMode>().name("blocks-filter").defaultValue(ListMode.Blacklist).onChanged(v -> pushSetting("blocks-filter", v.name())).build());

    public final Setting<Boolean> fastTower = sg.add(new BoolSetting.Builder().name("fast-tower").defaultValue(false).onChanged(v -> pushSetting("fast-tower", v)).build());
    public final Setting<Double> towerSpeed = sg.add(new DoubleSetting.Builder().name("tower-speed").defaultValue(0.5).min(0).sliderMax(1).visible(fastTower::get).onChanged(v -> pushSetting("tower-speed", v)).build());
    public final Setting<Boolean> whileMoving = sg.add(new BoolSetting.Builder().name("while-moving").defaultValue(false).visible(fastTower::get).onChanged(v -> pushSetting("while-moving", v)).build());

    public final Setting<Boolean> onlyOnClick = sg.add(new BoolSetting.Builder().name("only-on-click").defaultValue(false).onChanged(v -> pushSetting("only-on-click", v)).build());
    public final Setting<Boolean> renderSwing = sg.add(new BoolSetting.Builder().name("swing").defaultValue(false).onChanged(v -> pushSetting("swing", v)).build());
    public final Setting<Boolean> autoSwitch = sg.add(new BoolSetting.Builder().name("auto-switch").defaultValue(true).onChanged(v -> pushSetting("auto-switch", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());
    public final Setting<Boolean> airPlace = sg.add(new BoolSetting.Builder().name("air-place").defaultValue(false).onChanged(v -> pushSetting("air-place", v)).build());

    public final Setting<Double> aheadDistance = sg.add(new DoubleSetting.Builder().name("ahead-distance").defaultValue(0).min(0).sliderMax(1).visible(() -> !airPlace.get()).onChanged(v -> pushSetting("ahead-distance", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("closest-block-range").defaultValue(4).min(0).sliderMax(8).visible(() -> !airPlace.get()).onChanged(v -> pushSetting("closest-block-range", v)).build());
    public final Setting<Double> radius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(0).min(0).max(6).visible(airPlace::get).onChanged(v -> pushSetting("radius", v)).build());
    public final Setting<Integer> blocksPerTick = sg.add(new IntSetting.Builder().name("blocks-per-tick").defaultValue(3).min(1).visible(airPlace::get).onChanged(v -> pushSetting("blocks-per-tick", v)).build());

    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).visible(render::get).onChanged(v -> pushSetting("shape-mode", v.name())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(197,137,232,10)).visible(render::get).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(197,137,232)).visible(render::get).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmScaffold() {
        super("swarm-scaffold", "Proxy for scaffold", "scaffold");
    }
}
