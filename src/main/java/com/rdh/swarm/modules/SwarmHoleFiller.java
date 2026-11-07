package com.rdh.swarm.modules;

import java.util.List;

import net.minecraft.block.Block;

import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.misc.Keybind;

public class SwarmHoleFiller extends SwarmProxyModule {
    private static final byte[] _hfs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _hfc = 0x726468;
    private static final long _hfn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<List<Block>> blocks = sg.add(new BlockListSetting.Builder().name("blocks").defaultValue(
        // keep some defaults similar to reference
        Block.getBlockFromItem(net.minecraft.item.Items.OBSIDIAN),
        Block.getBlockFromItem(net.minecraft.item.Items.CRYING_OBSIDIAN),
        Block.getBlockFromItem(net.minecraft.item.Items.NETHERITE_BLOCK),
        Block.getBlockFromItem(net.minecraft.item.Items.RESPAWN_ANCHOR),
        Block.getBlockFromItem(net.minecraft.item.Items.COBWEB)
    ).onChanged(v -> pushSetting("blocks", v)).build());

    public final Setting<Integer> searchRadius = sg.add(new IntSetting.Builder().name("search-radius").defaultValue(5).min(0).sliderMax(6).onChanged(v -> pushSetting("search-radius", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Double> placeWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<Boolean> doubles = sg.add(new BoolSetting.Builder().name("doubles").defaultValue(true).onChanged(v -> pushSetting("doubles", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(false).onChanged(v -> pushSetting("rotate", v)).build());
    public final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder().name("place-delay").defaultValue(1).min(0).onChanged(v -> pushSetting("place-delay", v)).build());
    public final Setting<Integer> blocksPerTick = sg.add(new IntSetting.Builder().name("blocks-per-tick").defaultValue(3).min(1).onChanged(v -> pushSetting("blocks-per-tick", v)).build());

    public final Setting<Boolean> smart = sg.add(new BoolSetting.Builder().name("smart").defaultValue(true).onChanged(v -> pushSetting("smart", v)).build());
    public final Setting<Keybind> forceFill = sg.add(new KeybindSetting.Builder().name("force-fill").defaultValue(Keybind.none()).onChanged(v -> pushSetting("force-fill", v)).build());
    public final Setting<Boolean> predictMovement = sg.add(new BoolSetting.Builder().name("predict-movement").defaultValue(true).onChanged(v -> pushSetting("predict-movement", v)).build());
    public final Setting<Double> ticksToPredict = sg.add(new DoubleSetting.Builder().name("ticks-to-predict").defaultValue(10).min(1).sliderMax(30).onChanged(v -> pushSetting("ticks-to-predict", v)).build());
    public final Setting<Boolean> ignoreSafe = sg.add(new BoolSetting.Builder().name("ignore-safe").defaultValue(true).onChanged(v -> pushSetting("ignore-safe", v)).build());
    public final Setting<Boolean> onlyMoving = sg.add(new BoolSetting.Builder().name("only-moving").defaultValue(true).onChanged(v -> pushSetting("only-moving", v)).build());
    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(7).min(0).sliderMin(1).sliderMax(10).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Double> feetRange = sg.add(new DoubleSetting.Builder().name("feet-range").defaultValue(1.5).min(0).sliderMax(4).onChanged(v -> pushSetting("feet-range", v)).build());

    public final Setting<Boolean> swing = sg.add(new BoolSetting.Builder().name("swing").defaultValue(true).onChanged(v -> pushSetting("swing", v)).build());
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).onChanged(v -> pushSetting("shape-mode", v.name())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(197,137,232,10)).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(197,137,232)).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmHoleFiller() {
        super("swarm-hole-filler", "Proxy for hole-filler", "hole-filler");
    }
}
