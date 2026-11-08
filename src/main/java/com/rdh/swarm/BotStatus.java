package com.rdh.swarm;

public class BotStatus {
    public String workerName = "";
    public String nickname = "";
    public String state = "";
    public String taskDescription = "";
    public String taskId = "";
    public int collected = 0;
    public int total = 0;
    public String eta = "";
    public float health = 0f;
    public int food = 0;
    public double x = 0, y = 0, z = 0;
    public String tier = "";
    public boolean halted = false;
    public long lastUpdate = 0;
    public boolean online = false;

    public String getProgressString() { return String.format("%d/%d", collected, total); }
    public boolean isOnline() { return (System.currentTimeMillis() - lastUpdate) < 5000; }

    public String serialize() {
        // BOT_STATUS:workerName:state:taskDescription:collected:total:eta:health:food:x:y:z:tier:halted
        return String.format("BOT_STATUS:%s:%s:%s:%d:%d:%s:%.1f:%d:%.2f:%.2f:%.2f:%s:%b",
            workerName, state, taskDescription == null ? "" : taskDescription.replace(':', ' '), collected, total, eta == null ? "" : eta.replace(':',' '), health, food, x, y, z, tier == null ? "" : tier, halted);
    }

    public static BotStatus deserialize(String s) {
        try {
            if (s == null) return null;
            if (!s.startsWith("BOT_STATUS:")) return null;
            String body = s.substring(11);
            String[] parts = body.split(":", 13);
            BotStatus bs = new BotStatus();
            if (parts.length > 0) bs.workerName = parts[0];
            if (parts.length > 1) bs.state = parts[1];
            if (parts.length > 2) bs.taskDescription = parts[2];
            if (parts.length > 3) try { bs.collected = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
            if (parts.length > 4) try { bs.total = Integer.parseInt(parts[4]); } catch (Exception ignored) {}
            if (parts.length > 5) bs.eta = parts[5];
            if (parts.length > 6) try { bs.health = Float.parseFloat(parts[6]); } catch (Exception ignored) {}
            if (parts.length > 7) try { bs.food = Integer.parseInt(parts[7]); } catch (Exception ignored) {}
            if (parts.length > 8) try { bs.x = Double.parseDouble(parts[8]); } catch (Exception ignored) {}
            if (parts.length > 9) try { bs.y = Double.parseDouble(parts[9]); } catch (Exception ignored) {}
            if (parts.length > 10) try { bs.z = Double.parseDouble(parts[10]); } catch (Exception ignored) {}
            if (parts.length > 11) bs.tier = parts[11];
            if (parts.length > 12) try { bs.halted = Boolean.parseBoolean(parts[12]); } catch (Exception ignored) {}
            bs.lastUpdate = System.currentTimeMillis();
            bs.online = true;
            return bs;
        } catch (Exception e) { return null; }
    }
}
