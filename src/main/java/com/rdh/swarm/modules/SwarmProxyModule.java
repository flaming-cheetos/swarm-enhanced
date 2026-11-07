package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Settings;
import com.rdh.swarm.EnhancedSwarm;
import meteordevelopment.meteorclient.systems.modules.Modules;

public abstract class SwarmProxyModule extends Module {
    private static final byte[] _pms = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x36,0x73};
    private static final int _pmc = 0x726468;
    private static final long _pmn = 0x70726F7879L;
    protected final String remoteModuleName;

    private final SettingGroup sgTarget = settings.createGroup("Target");

    public final Setting<String> targetSetting = sgTarget.add(new StringSetting.Builder()
        .name("target")
        .description("Worker target: All or a specific worker name")
        .defaultValue("All")
        .build()
    );

    public SwarmProxyModule(String name, String description, String remoteModuleName) {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, name, description);
        this.remoteModuleName = remoteModuleName;
    }

    @Override
    public void onActivate() {
        pushToggle(true);
    }

    @Override
    public void onDeactivate() {
        pushToggle(false);
    }

    protected void pushToggle(boolean on) {
        sendToTarget("exec .toggle " + remoteModuleName + " " + (on ? "on" : "off"));
    }

    protected void pushSetting(String settingName, Object value) {
        sendToTarget("exec .settings " + remoteModuleName + " " + settingName + " " + value.toString());
    }

    protected void sendToTarget(String payload) {
        EnhancedSwarm swarm = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
        if (swarm == null || !swarm.isHost() || swarm.host == null) return;
        String target = targetSetting.get();
        if (target == null || target.isEmpty()) target = "All";
        if (target.equalsIgnoreCase("All")) {
            swarm.host.sendAll("swarm:all " + payload);
        } else {
            swarm.host.sendTo(target, "swarm:" + target + " " + payload);
        }
    }
}
