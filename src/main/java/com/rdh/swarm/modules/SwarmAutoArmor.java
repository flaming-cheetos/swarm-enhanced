package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmAutoArmor extends SwarmProxyModule {
    private static final byte[] _aas = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _aac = 0x726468;
    private static final long _aan = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Protection { Protection, BlastProtection, FireProtection, ProjectileProtection }

    public final Setting<Protection> preferredProtection = sg.add(new EnumSetting.Builder<Protection>().name("preferred-protection").defaultValue(Protection.Protection).onChanged(v -> pushSetting("preferred-protection", v.name().toLowerCase())).build());
    public final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("swap-delay").defaultValue(1).min(0).sliderMax(5).onChanged(v -> pushSetting("swap-delay", v)).build());
    public final EnchantmentListSetting avoidedEnchantments = sg.add(new EnchantmentListSetting.Builder().name("avoided-enchantments").defaultValue().onChanged(v -> pushSetting("avoided-enchantments", v)).build());
    public final Setting<Boolean> blastLeggings = sg.add(new BoolSetting.Builder().name("blast-prot-leggings").defaultValue(true).onChanged(v -> pushSetting("blast-prot-leggings", v)).build());
    public final Setting<Boolean> antiBreak = sg.add(new BoolSetting.Builder().name("anti-break").defaultValue(false).onChanged(v -> pushSetting("anti-break", v)).build());
    public final Setting<Boolean> ignoreElytra = sg.add(new BoolSetting.Builder().name("ignore-elytra").defaultValue(true).onChanged(v -> pushSetting("ignore-elytra", v)).build());

    public SwarmAutoArmor() {
        super("swarm-auto-armor", "Proxy for auto-armor", "auto-armor");
    }
}
