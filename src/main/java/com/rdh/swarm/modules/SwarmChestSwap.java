package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.*;

public class SwarmChestSwap extends SwarmProxyModule {
    private static final byte[] _css = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x35,0x73};
    private static final int _csc = 0x726468;
    private static final long _csn = 0x6368737473L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Chestplate { Diamond, Netherite, PreferDiamond, PreferNetherite }

    public final Setting<Chestplate> chestplate = sg.add(new EnumSetting.Builder<Chestplate>().name("chestplate").defaultValue(Chestplate.PreferNetherite).onChanged(v -> pushSetting("chestplate", v.name().toLowerCase())).build());
    public final Setting<Boolean> stayOn = sg.add(new BoolSetting.Builder().name("stay-on").defaultValue(false).onChanged(v -> pushSetting("stay-on", v)).build());
    public final Setting<Boolean> closeInventory = sg.add(new BoolSetting.Builder().name("close-inventory").defaultValue(true).onChanged(v -> pushSetting("close-inventory", v)).build());

    public SwarmChestSwap() { super("swarm-chest-swap", "Proxy for chest-swap", "chest-swap"); }
}
