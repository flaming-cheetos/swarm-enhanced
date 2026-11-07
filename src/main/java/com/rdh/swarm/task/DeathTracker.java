package com.rdh.swarm.task;

import net.minecraft.util.math.BlockPos;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DeathTracker {

    public static class DeathRecord {
        public final Instant time;
        public final String cause;
        public final BlockPos pos;

        public DeathRecord(Instant time, String cause, BlockPos pos) {
            this.time = time;
            this.cause = cause;
            this.pos = pos;
        }
    }

    private final Map<UUID, List<DeathRecord>> deaths = new ConcurrentHashMap<>();

    public void recordDeath(UUID player, String cause, BlockPos pos) {
        deaths.computeIfAbsent(player, k -> Collections.synchronizedList(new ArrayList<>()))
              .add(new DeathRecord(Instant.now(), cause, pos));
    }

    public List<DeathRecord> getDeaths(UUID player) {
        return deaths.getOrDefault(player, List.of());
    }

    public int getDeathCount(UUID player) {
        return deaths.getOrDefault(player, List.of()).size();
    }

    public void clear(UUID player) {
        deaths.remove(player);
    }

    // Convenience methods used by BotBrain
    public void load() {
        // no-op for now (placeholder for persistence)
    }

    public boolean hasDeath() {
        if (mc.player == null) return false;
        return getDeathCount(mc.player.getUuid()) > 0;
    }

    public boolean canRecover() {
        if (mc.player == null) return false;
        List<DeathRecord> list = getDeaths(mc.player.getUuid());
        if (list.isEmpty()) return false;
        Instant then = list.get(list.size()-1).time;
        return Instant.now().isBefore(then.plusSeconds(300));
    }

    public BlockPos getDeathPos() {
        if (mc.player == null) return new BlockPos(0,0,0);
        List<DeathRecord> list = getDeaths(mc.player.getUuid());
        if (list.isEmpty()) return new BlockPos(0,0,0);
        return list.get(list.size()-1).pos;
    }

    public long getTimeRemainingMs() {
        if (mc.player == null) return 0;
        List<DeathRecord> list = getDeaths(mc.player.getUuid());
        if (list.isEmpty()) return 0;
        Instant then = list.get(list.size()-1).time;
        long remaining = then.plusSeconds(300).toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }

    public void clearDeath() {
        if (mc.player == null) return;
        clear(mc.player.getUuid());
    }
}
