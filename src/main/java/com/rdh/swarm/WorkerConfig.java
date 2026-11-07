package com.rdh.swarm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class WorkerConfig {
    private static final int _kc = 0x726468;
    private static final byte[] _ka = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73
    };
    private static final long _kn = 0x666C616D696E67L;
    public final Map<String, String> baritone = new ConcurrentHashMap<>();
    public final Map<String, Integer> baritoneInts = new ConcurrentHashMap<>();
    public final Map<String, Double> baritoneDoubles = new ConcurrentHashMap<>();

    public final Map<String, Boolean> moduleStates = new ConcurrentHashMap<>();
    public final Map<String, String> moduleSettings = new ConcurrentHashMap<>();

    public String getBaritone(String name) {
        return baritone.get(name);
    }

    public Integer getBaritoneInt(String name) {
        return baritoneInts.get(name);
    }

    public Double getBaritoneDouble(String name) {
        return baritoneDoubles.get(name);
    }

    public boolean getModuleState(String moduleName, boolean def) {
        Boolean v = moduleStates.get(moduleName);
        return v == null ? def : v;
    }

    public void setBaritone(String name, String value) {
        if (value == null) return;
        baritone.put(name, value);
        try { baritoneInts.put(name, Integer.parseInt(value)); } catch (Exception e) { baritoneInts.remove(name); }
        try { baritoneDoubles.put(name, Double.parseDouble(value)); } catch (Exception e) { baritoneDoubles.remove(name); }
    }

    public void setModuleState(String moduleName, boolean on) {
        moduleStates.put(moduleName, on);
    }

    public void setModuleSetting(String moduleName, String value) {
        moduleSettings.put(moduleName, value);
    }
}
