package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class SwarmBedAura extends SwarmProxyModule {
    private static final byte[] _bas = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _bac = 0x726468;
    private static final long _ban = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay").defaultValue(9).min(0).sliderMax(20).onChanged(v -> pushSetting("delay", v)).build());
    public final Setting<Boolean> strictDirection = sg.add(new BoolSetting.Builder().name("strict-direction").defaultValue(false).onChanged(v -> pushSetting("strict-direction", v)).build());

    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(4).min(0).sliderMax(5).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Double> minDamage = sg.add(new DoubleSetting.Builder().name("min-damage").defaultValue(7).min(0).sliderMax(36).onChanged(v -> pushSetting("min-damage", v)).build());
    public final Setting<Double> maxSelfDamage = sg.add(new DoubleSetting.Builder().name("max-self-damage").defaultValue(7).min(0).sliderMax(36).onChanged(v -> pushSetting("max-self-damage", v)).build());
    public final Setting<Boolean> antiSuicide = sg.add(new BoolSetting.Builder().name("anti-suicide").defaultValue(true).onChanged(v -> pushSetting("anti-suicide", v)).build());

    public final Setting<Boolean> autoMove = sg.add(new BoolSetting.Builder().name("auto-move").defaultValue(false).onChanged(v -> pushSetting("auto-move", v)).build());
    public final Setting<Integer> autoMoveSlot = sg.add(new IntSetting.Builder().name("auto-move-slot").defaultValue(9).range(1,9).sliderRange(1,9).onChanged(v -> pushSetting("auto-move-slot", v)).build());
    public final Setting<Boolean> autoSwitch = sg.add(new BoolSetting.Builder().name("auto-switch").defaultValue(true).onChanged(v -> pushSetting("auto-switch", v)).build());

    public final Setting<Boolean> pauseOnEat = sg.add(new BoolSetting.Builder().name("pause-on-eat").defaultValue(true).onChanged(v -> pushSetting("pause-on-eat", v)).build());
    public final Setting<Boolean> pauseOnDrink = sg.add(new BoolSetting.Builder().name("pause-on-drink").defaultValue(true).onChanged(v -> pushSetting("pause-on-drink", v)).build());
    public final Setting<Boolean> pauseOnMine = sg.add(new BoolSetting.Builder().name("pause-on-mine").defaultValue(true).onChanged(v -> pushSetting("pause-on-mine", v)).build());

    public final Setting<Boolean> swing = sg.add(new BoolSetting.Builder().name("swing").defaultValue(true).onChanged(v -> pushSetting("swing", v)).build());
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<ShapeMode> shapeMode = sg.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).onChanged(v -> pushSetting("shape-mode", v.name())).build());
    public final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(15,255,211,75)).onChanged(v -> pushSetting("side-color", v)).build());
    public final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(15,255,211)).onChanged(v -> pushSetting("line-color", v)).build());

    public SwarmBedAura() {
        super("swarm-bed-aura", "Proxy for bed-aura", "bed-aura");
    }
}
