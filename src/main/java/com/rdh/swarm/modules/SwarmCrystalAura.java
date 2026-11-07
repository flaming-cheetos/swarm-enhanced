package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.List;

public class SwarmCrystalAura extends SwarmProxyModule {
    private static final byte[] _crs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _crc = 0x726468;
    private static final long _crn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    // General
    public final Setting<Double> targetRange = sg.add(new DoubleSetting.Builder().name("target-range").defaultValue(10).min(0).sliderMax(16).onChanged(v -> pushSetting("target-range", v)).build());
    public final Setting<Boolean> predictMovement = sg.add(new BoolSetting.Builder().name("predict-movement").defaultValue(false).onChanged(v -> pushSetting("predict-movement", v)).build());
    public final Setting<Double> minDamage = sg.add(new DoubleSetting.Builder().name("min-damage").defaultValue(6.0).min(0).onChanged(v -> pushSetting("min-damage", v)).build());
    public final Setting<Double> maxDamage = sg.add(new DoubleSetting.Builder().name("max-damage").defaultValue(6.0).min(0).range(0,36).sliderMax(36).onChanged(v -> pushSetting("max-damage", v)).build());
    public final Setting<Boolean> antiSuicide = sg.add(new BoolSetting.Builder().name("anti-suicide").defaultValue(true).onChanged(v -> pushSetting("anti-suicide", v)).build());
    public final Setting<Boolean> ignoreNakeds = sg.add(new BoolSetting.Builder().name("ignore-nakeds").defaultValue(false).onChanged(v -> pushSetting("ignore-nakeds", v)).build());
    public final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder().name("rotate").defaultValue(true).onChanged(v -> pushSetting("rotate", v)).build());

    // Yaw steps
    public enum YawStepMode {Break, Place}
    public final Setting<YawStepMode> yawStepMode = sg.add(new EnumSetting.Builder<YawStepMode>().name("yaw-steps-mode").defaultValue(YawStepMode.Break).visible(rotate::get).onChanged(v -> pushSetting("yaw-steps-mode", v.name().toLowerCase())).build());
    public final Setting<Double> yawSteps = sg.add(new DoubleSetting.Builder().name("yaw-steps").defaultValue(180).range(1,180).visible(rotate::get).onChanged(v -> pushSetting("yaw-steps", v)).build());

    public final EntityTypeListSetting entities = sg.add(new EntityTypeListSetting.Builder().name("entities").onlyAttackable().onChanged(v -> pushSetting("entities", v)).build());

    // Switch
    public enum AutoSwitchMode {Normal, None}
    public final Setting<AutoSwitchMode> autoSwitch = sg.add(new EnumSetting.Builder<AutoSwitchMode>().name("auto-switch").defaultValue(AutoSwitchMode.Normal).onChanged(v -> pushSetting("auto-switch", v.name().toLowerCase())).build());
    public final Setting<Integer> switchDelay = sg.add(new IntSetting.Builder().name("switch-delay").defaultValue(0).min(0).onChanged(v -> pushSetting("switch-delay", v)).build());
    public final Setting<Boolean> noGapSwitch = sg.add(new BoolSetting.Builder().name("no-gap-switch").defaultValue(true).visible(() -> autoSwitch.get() == AutoSwitchMode.Normal).onChanged(v -> pushSetting("no-gap-switch", v)).build());
    public final Setting<Boolean> noBowSwitch = sg.add(new BoolSetting.Builder().name("no-bow-switch").defaultValue(true).onChanged(v -> pushSetting("no-bow-switch", v)).build());
    public final Setting<Boolean> antiWeakness = sg.add(new BoolSetting.Builder().name("anti-weakness").defaultValue(true).onChanged(v -> pushSetting("anti-weakness", v)).build());

    // Place
    public final Setting<Boolean> doPlace = sg.add(new BoolSetting.Builder().name("place").defaultValue(true).onChanged(v -> pushSetting("place", v)).build());
    public final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder().name("place-delay").defaultValue(0).min(0).sliderMax(20).onChanged(v -> pushSetting("place-delay", v)).build());
    public final Setting<Double> placeRange = sg.add(new DoubleSetting.Builder().name("place-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("place-range", v)).build());
    public final Setting<Double> placeWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<Boolean> placement112 = sg.add(new BoolSetting.Builder().name("1.12-placement").defaultValue(false).onChanged(v -> pushSetting("1.12-placement", v)).build());
    public enum SupportMode {Disabled, Always}
    public final Setting<SupportMode> support = sg.add(new EnumSetting.Builder<SupportMode>().name("support").defaultValue(SupportMode.Disabled).onChanged(v -> pushSetting("support", v.name().toLowerCase())).build());
    public final Setting<Integer> supportDelay = sg.add(new IntSetting.Builder().name("support-delay").defaultValue(1).min(0).visible(() -> support.get() != SupportMode.Disabled).onChanged(v -> pushSetting("support-delay", v)).build());

    // Face place
    public final Setting<Boolean> facePlace = sg.add(new BoolSetting.Builder().name("face-place").defaultValue(true).onChanged(v -> pushSetting("face-place", v)).build());
    public final Setting<Double> facePlaceHealth = sg.add(new DoubleSetting.Builder().name("face-place-health").defaultValue(8).min(1).sliderMin(1).sliderMax(36).visible(facePlace::get).onChanged(v -> pushSetting("face-place-health", v)).build());

    // Break
    public final Setting<Boolean> doBreak = sg.add(new BoolSetting.Builder().name("break").defaultValue(true).onChanged(v -> pushSetting("break", v)).build());
    public final Setting<Integer> breakDelay = sg.add(new IntSetting.Builder().name("break-delay").defaultValue(0).min(0).sliderMax(20).onChanged(v -> pushSetting("break-delay", v)).build());
    public final Setting<Boolean> smartDelay = sg.add(new BoolSetting.Builder().name("smart-delay").defaultValue(false).onChanged(v -> pushSetting("smart-delay", v)).build());
    public final Setting<Double> breakRange = sg.add(new DoubleSetting.Builder().name("break-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("break-range", v)).build());
    public final Setting<Double> breakWallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<Boolean> onlyOwn = sg.add(new BoolSetting.Builder().name("only-own").defaultValue(false).onChanged(v -> pushSetting("only-own", v)).build());
    public final Setting<Integer> breakAttempts = sg.add(new IntSetting.Builder().name("break-attempts").defaultValue(2).sliderMin(1).sliderMax(5).onChanged(v -> pushSetting("break-attempts", v)).build());
    public final Setting<Integer> ticksExisted = sg.add(new IntSetting.Builder().name("ticks-existed").defaultValue(0).min(0).onChanged(v -> pushSetting("ticks-existed", v)).build());
    public final Setting<Integer> attackFrequency = sg.add(new IntSetting.Builder().name("attack-frequency").defaultValue(25).min(1).sliderRange(1,30).onChanged(v -> pushSetting("attack-frequency", v)).build());
    public final Setting<Boolean> fastBreak = sg.add(new BoolSetting.Builder().name("fast-break").defaultValue(true).onChanged(v -> pushSetting("fast-break", v)).build());

    // Pause
    public enum PauseMode {None, Place, Break}
    public final Setting<PauseMode> pauseOnUse = sg.add(new EnumSetting.Builder<PauseMode>().name("pause-on-use").defaultValue(PauseMode.Place).onChanged(v -> pushSetting("pause-on-use", v.name().toLowerCase())).build());
    public final Setting<Boolean> pauseOnLag = sg.add(new BoolSetting.Builder().name("pause-on-lag").defaultValue(true).onChanged(v -> pushSetting("pause-on-lag", v)).build());
    public final ModuleListSetting pauseModules = sg.add(new ModuleListSetting.Builder().name("pause-modules").onChanged(v -> pushSetting("pause-modules", v)).build());
    public final Setting<Double> pauseHealth = sg.add(new DoubleSetting.Builder().name("pause-health").defaultValue(5).range(0,36).sliderRange(0,36).onChanged(v -> pushSetting("pause-health", v)).build());

    // Render (reduced)
    public enum SwingMode {Both, Main, Off}
    public final Setting<SwingMode> swingMode = sg.add(new EnumSetting.Builder<SwingMode>().name("swing-mode").defaultValue(SwingMode.Both).onChanged(v -> pushSetting("swing-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> render = sg.add(new BoolSetting.Builder().name("render").defaultValue(true).onChanged(v -> pushSetting("render", v)).build());
    public final Setting<Boolean> renderPlace = sg.add(new BoolSetting.Builder().name("render-place").defaultValue(true).visible(() -> render.get()).onChanged(v -> pushSetting("render-place", v)).build());
    public final Setting<Integer> placeRenderTime = sg.add(new IntSetting.Builder().name("place-time").defaultValue(10).min(0).sliderMax(20).visible(() -> render.get() && renderPlace.get()).onChanged(v -> pushSetting("place-time", v)).build());

    public SwarmCrystalAura() {
        super("swarm-crystal-aura", "Proxy for crystal-aura", "crystal-aura");
    }
}
