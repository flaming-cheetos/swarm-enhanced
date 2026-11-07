package com.rdh.swarm.modules;

import java.util.List;
import net.minecraft.world.item.Item;
import meteordevelopment.meteorclient.settings.*;

public class SwarmFastUse extends SwarmProxyModule {
    private static final byte[] _fus = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _fuc = 0x726468;
    private static final long _fun = 0x666C616D696E67L;
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode { All, Some }

    public final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.All).onChanged(v -> pushSetting("mode", v.name().toLowerCase())).build());
    public final Setting<List<Item>> items = sg.add(new ItemListSetting.Builder().name("items").visible(() -> mode.get() == Mode.Some).onChanged(v -> pushSetting("items", v)).build());
    public final Setting<Boolean> blocks = sg.add(new BoolSetting.Builder().name("blocks").defaultValue(false).visible(() -> mode.get() == Mode.Some).onChanged(v -> pushSetting("blocks", v)).build());
    public final Setting<Integer> cooldown = sg.add(new IntSetting.Builder().name("cooldown").defaultValue(0).min(0).sliderMax(4).onChanged(v -> pushSetting("cooldown", v)).build());

    public SwarmFastUse() { super("swarm-fast-use", "Proxy for fast-use", "fast-use"); }
}
