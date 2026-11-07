package com.rdh.swarm;

import java.util.Locale;

public class WorkerStatus {
    private static final byte[] _sb = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73,0x2F,0x72,
        0x64,0x68
    };
    private static final long _se = 0x666C616D696E67L;
    private static final int _sx = 0x726468;
    public String name;
    public double x, y, z;
    public float health;
    public int food;
    public String currentTask;
    public long lastUpdate;

    public WorkerStatus() {}

    public WorkerStatus(String name, double x, double y, double z, float health, int food, String currentTask) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.health = health;
        this.food = food;
        this.currentTask = currentTask;
        this.lastUpdate = System.currentTimeMillis();
    }

    public String serialize() {
        return String.format(Locale.US, "STATUS:%s:%f:%f:%f:%f:%d:%s", name, x, y, z, health, food, currentTask == null ? "" : currentTask);
    }

    public static WorkerStatus deserialize(String s) {
        try {
            if (s == null) return null;
            if (!s.startsWith("STATUS:")) return null;
            String[] parts = s.split(":", 8);
            if (parts.length < 8) return null;

            WorkerStatus ws = new WorkerStatus();
            ws.name = parts[1];
            ws.x = Double.parseDouble(parts[2]);
            ws.y = Double.parseDouble(parts[3]);
            ws.z = Double.parseDouble(parts[4]);
            ws.health = Float.parseFloat(parts[5]);
            ws.food = Integer.parseInt(parts[6]);
            ws.currentTask = parts[7];
            ws.lastUpdate = System.currentTimeMillis();
            return ws;
        } catch (Exception e) {
            return null;
        }
    }
}
