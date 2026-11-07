package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import net.minecraft.world.level.block.Block;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

public class SwarmSurround extends SwarmProxyModule {
    private static final byte[] _sus = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _suc = 0x726468;
    private static final long _sun = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    // General
    public final BlockListSetting blocks = sg.add(new BlockListSetting.Builder().name("blocks").onChanged(v -> pushSetting("blocks", v)).build());
    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay").defaultValue(0).min(0).onChanged(v -> pushSetting("delay", v)).build());
    public final Setting<Integer> blocksPerTick = sg.add(new IntSetting.Builder().name("blocks-per-tick").defaultValue(1).min(1).onChanged(v -> pushSetting("blocks-per-tick", v)).build());

    public enum Center {Incomplete, Instant}
    public final Setting<Center> center = sg.add(new EnumSetting.Builder<Center>().name("center").defaultValue(Center.Incomplete).onChanged(v -> pushSetting("center", v.name().toLowerCase())).build());

    public final Setting<Boolean> doubleHeight = sg.add(new BoolSetting.Builder().name("double-height").defaultValue(false).onChanged(v -> pushSetting("double-height", v)).build());
    public final Setting<Boolean> onlyOnGround = sg.add(new BoolSetting.Builder().name("only-on-ground").defaultValue(true).onChanged(v -> pushSetting("only-on-ground", v)).build());
    public final Setting<Boolean> airPlace = sg.add(new BoolSetting.Builder().name("air-place").defaultValue(true).onChanged(v -> pushSetting("air-place", v)).build());
    public final Setting<Boolean> toggleModules = sg.add(new BoolSetting.Builder().name("toggle-modules").defaultValue(false).onChanged(v -> pushSetting("toggle-modules", v)).build());
    public final Setting<Boolean> toggleBack = sg.add(new BoolSetting.Builder().name("toggle-back-on").defaultValue(false).visible(toggleModules::get).onChanged(v -> pushSetting("toggle-back-on", v)).build());
    public final ModuleListSetting modules = sg.add(new ModuleListSetting.Builder().name("modules").visible(toggleModules::get).onChanged(v -> pushSetting("modules", v)).build());

    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());
    public final Setting<Boolean> protect = sg.add(new BoolSetting.Builder().name("protect").defaultValue(true).onChanged(v -> pushSetting("protect", v)).build());

    // Render
    public final Setting<Boolean> swing = sg.add(new BoolSetting.Builder().name("swing").defaultValue(true).onChanged(v -> pushSetting("swing", v)).build());
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<Boolean> renderBelow = sg.add(new BoolSetting.Builder().name("below").defaultValue(false).onChanged(v -> pushSetting("below", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).onChanged(v -> pushSetting("shape-mode", v.name().toLowerCase())).visible(() -> render.get()).build());
    public final Setting<SettingColor> safeSideColor = sg.add(new ColorSetting.Builder().name("safe-side-color").defaultValue(new SettingColor(13,255,0,0)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines).onChanged(v -> pushSetting("safe-side-color", v)).build());
    public final Setting<SettingColor> safeLineColor = sg.add(new ColorSetting.Builder().name("safe-line-color").defaultValue(new SettingColor(13,255,0,0)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides).onChanged(v -> pushSetting("safe-line-color", v)).build());
    public final Setting<SettingColor> normalSideColor = sg.add(new ColorSetting.Builder().name("normal-side-color").defaultValue(new SettingColor(0,255,238,12)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines).onChanged(v -> pushSetting("normal-side-color", v)).build());
    public final Setting<SettingColor> normalLineColor = sg.add(new ColorSetting.Builder().name("normal-line-color").defaultValue(new SettingColor(0,255,238,100)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides).onChanged(v -> pushSetting("normal-line-color", v)).build());
    public final Setting<SettingColor> unsafeSideColor = sg.add(new ColorSetting.Builder().name("unsafe-side-color").defaultValue(new SettingColor(204,0,0,12)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines).onChanged(v -> pushSetting("unsafe-side-color", v)).build());
    public final Setting<SettingColor> unsafeLineColor = sg.add(new ColorSetting.Builder().name("unsafe-line-color").defaultValue(new SettingColor(204,0,0,100)).visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides).onChanged(v -> pushSetting("unsafe-line-color", v)).build());

    public SwarmSurround() {
        super("swarm-surround", "Proxy for surround", "surround");
    }
}
