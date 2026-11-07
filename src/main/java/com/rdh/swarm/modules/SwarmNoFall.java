package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class SwarmNoFall extends SwarmProxyModule {
    private static final byte[] _nfs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _nfc = 0x726468;
    private static final long _nfn = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode { Packet, AirPlace, Place }
    public enum PlacedItem { Bucket, PowderSnow, HayBale, Cobweb, SlimeBlock }
    public enum PlaceMode { BeforeDamage, BeforeDeath }

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Packet).onChanged(v -> pushSetting("mode", v.name())).build());
    public final Setting<PlacedItem> placedItem = sg.add(new EnumSetting.Builder<PlacedItem>().name("placed-item").defaultValue(PlacedItem.Bucket).onChanged(v -> pushSetting("placed-item", v.name())).build());
    public final Setting<PlaceMode> airPlaceMode = sg.add(new EnumSetting.Builder<PlaceMode>().name("air-place-mode").defaultValue(PlaceMode.BeforeDeath).onChanged(v -> pushSetting("air-place-mode", v.name())).build());
    public final Setting<Boolean> anchor = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("anchor").defaultValue(true).onChanged(v -> pushSetting("anchor", v)).build());
    public final Setting<Boolean> antiBounce = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("anti-bounce").defaultValue(true).onChanged(v -> pushSetting("anti-bounce", v)).build());
    public final Setting<Boolean> pauseOnMace = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("pause-on-mace").defaultValue(true).onChanged(v -> pushSetting("pause-on-mace", v)).build());

    public SwarmNoFall() {
        super("swarm-no-fall", "Proxy for no-fall", "no-fall");
    }
}
