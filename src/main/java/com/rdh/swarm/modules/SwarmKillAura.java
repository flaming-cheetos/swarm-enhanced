package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.List;

public class SwarmKillAura extends SwarmProxyModule {
    private static final byte[] _kis = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _kic = 0x726468;
    private static final long _kin = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum RotationMode {Always, OnHit, None}
    public enum ShieldMode {Ignore, Break, None}
    public enum AttackItems {Weapons, All}
    public enum EntityAge {Baby, Adult, Both}

    // General
    public final Setting<RotationMode> rotation = sg.add(new EnumSetting.Builder<RotationMode>().name("rotate").defaultValue(RotationMode.Always).onChanged(v -> pushSetting("rotate", v.name().toLowerCase())).build());
    public final Setting<Boolean> autoSwitch = sg.add(new BoolSetting.Builder().name("auto-switch").defaultValue(false).onChanged(v -> pushSetting("auto-switch", v)).build());
    public final Setting<Boolean> swapBack = sg.add(new BoolSetting.Builder().name("swap-back").defaultValue(false).visible(autoSwitch::get).onChanged(v -> pushSetting("swap-back", v)).build());
    public final Setting<ShieldMode> shieldMode = sg.add(new EnumSetting.Builder<ShieldMode>().name("shield-mode").defaultValue(ShieldMode.None).onChanged(v -> pushSetting("shield-mode", v.name().toLowerCase())).build());
    public final Setting<Boolean> onlyOnClick = sg.add(new BoolSetting.Builder().name("only-on-click").defaultValue(false).onChanged(v -> pushSetting("only-on-click", v)).build());
    public final Setting<Boolean> onlyOnLook = sg.add(new BoolSetting.Builder().name("only-on-look").defaultValue(false).onChanged(v -> pushSetting("only-on-look", v)).build());
    public final Setting<Boolean> pauseBaritone = sg.add(new BoolSetting.Builder().name("pause-baritone").defaultValue(true).onChanged(v -> pushSetting("pause-baritone", v)).build());

    // Targeting
    public final EntityTypeListSetting entities = sg.add(new EntityTypeListSetting.Builder().name("entities").onlyAttackable().onChanged(v -> pushSetting("entities", v)).build());
    public final Setting<SortPriority> priority = sg.add(new EnumSetting.Builder<SortPriority>().name("priority").defaultValue(SortPriority.ClosestAngle).onChanged(v -> pushSetting("priority", v.name().toLowerCase())).build());
    public final Setting<Integer> maxTargets = sg.add(new IntSetting.Builder().name("max-targets").defaultValue(1).min(1).sliderRange(1,5).visible(() -> !onlyOnLook.get()).onChanged(v -> pushSetting("max-targets", v)).build());
    public final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(4.5).min(0).sliderMax(6).onChanged(v -> pushSetting("range", v)).build());
    public final Setting<Double> wallsRange = sg.add(new DoubleSetting.Builder().name("walls-range").defaultValue(3.5).min(0).sliderMax(6).onChanged(v -> pushSetting("walls-range", v)).build());
    public final Setting<EntityAge> passiveMobAgeFilter = sg.add(new EnumSetting.Builder<EntityAge>().name("passive-mob-age-filter").defaultValue(EntityAge.Adult).onChanged(v -> pushSetting("passive-mob-age-filter", v.name().toLowerCase())).build());
    public final Setting<EntityAge> hostileMobAgeFilter = sg.add(new EnumSetting.Builder<EntityAge>().name("hostile-mob-age-filter").defaultValue(EntityAge.Both).onChanged(v -> pushSetting("hostile-mob-age-filter", v.name().toLowerCase())).build());
    public final Setting<Boolean> ignoreNamed = sg.add(new BoolSetting.Builder().name("ignore-named").defaultValue(false).onChanged(v -> pushSetting("ignore-named", v)).build());
    public final Setting<Boolean> ignorePassive = sg.add(new BoolSetting.Builder().name("ignore-passive").defaultValue(true).onChanged(v -> pushSetting("ignore-passive", v)).build());
    public final Setting<Boolean> ignoreTamed = sg.add(new BoolSetting.Builder().name("ignore-tamed").defaultValue(false).onChanged(v -> pushSetting("ignore-tamed", v)).build());

    // Timing
    public final Setting<Boolean> pauseOnLag = sg.add(new BoolSetting.Builder().name("pause-on-lag").defaultValue(true).onChanged(v -> pushSetting("pause-on-lag", v)).build());
    public final Setting<Boolean> pauseOnUse = sg.add(new BoolSetting.Builder().name("pause-on-use").defaultValue(false).onChanged(v -> pushSetting("pause-on-use", v)).build());
    public final Setting<Boolean> pauseOnCA = sg.add(new BoolSetting.Builder().name("pause-on-CA").defaultValue(true).onChanged(v -> pushSetting("pause-on-CA", v)).build());
    public final Setting<Boolean> tpsSync = sg.add(new BoolSetting.Builder().name("TPS-sync").defaultValue(true).onChanged(v -> pushSetting("TPS-sync", v)).build());
    public final Setting<Boolean> customDelay = sg.add(new BoolSetting.Builder().name("custom-delay").defaultValue(false).onChanged(v -> pushSetting("custom-delay", v)).build());
    public final Setting<Integer> hitDelay = sg.add(new IntSetting.Builder().name("hit-delay").defaultValue(11).min(0).sliderMax(60).visible(customDelay::get).onChanged(v -> pushSetting("hit-delay", v)).build());
    public final Setting<Integer> switchDelay = sg.add(new IntSetting.Builder().name("switch-delay").defaultValue(0).min(0).sliderMax(10).onChanged(v -> pushSetting("switch-delay", v)).build());

    public SwarmKillAura() {
        super("swarm-kill-aura", "Proxy for kill-aura", "kill-aura");
    }
}
