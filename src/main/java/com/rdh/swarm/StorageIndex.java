package com.rdh.swarm;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host-owned storage index populated by worker scans.
 * Persisted per-server to meteor-client/enhanced-swarm/storage-index.json
 */
public class StorageIndex {
    private static final Path CONFIG_DIR = Path.of("meteor-client", "enhanced-swarm");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("storage-index.json");

    public enum ChestStatus { FRESH, STALE, UNREACHABLE, MISSING }

    public static class ChestRecord {
        public int x, y, z;
        public String category;
        public Map<String, Long> counts = new LinkedHashMap<>();
        public long lastScan = 0;
        public ChestStatus status = ChestStatus.STALE;
        public int usedSlots = 0;
        public int totalSlots = 0;
        public double fullness = 0.0;
    }

    // server -> category -> chestKey -> ChestRecord
    private final Map<String, Map<String, Map<String, ChestRecord>>> index = new ConcurrentHashMap<>();

    public StorageIndex() { load(); }

    private String normalize(String s) { return s == null ? "unknown" : s.toLowerCase().trim(); }

    private String chestKey(int x, int y, int z) { return x + ":" + y + ":" + z; }

    public synchronized void applyChestResult(String server, String workerName, String category, int x, int y, int z, ChestRecord rec) {
        server = normalize(server);
        category = normalize(category);
        index.computeIfAbsent(server, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(category, k -> new ConcurrentHashMap<>())
            .put(chestKey(x,y,z), rec);
        save();
    }

    /** Merge only the observed (item, delta) pairs from one deposit. */
    public synchronized void applyChestDelta(String server, String category, int x, int y, int z,
                                             Map<String, Long> delta, long lastScan) {
        if (delta == null || delta.isEmpty()) return;
        server = normalize(server);
        category = normalize(category);
        Map<String, ChestRecord> chests = index
            .computeIfAbsent(server, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(category, k -> new ConcurrentHashMap<>());
        ChestRecord rec = chests.get(chestKey(x, y, z));
        if (rec == null) {
            rec = new ChestRecord();
            rec.x = x; rec.y = y; rec.z = z; rec.category = category;
            chests.put(chestKey(x, y, z), rec);
        }
        for (Map.Entry<String, Long> entry : delta.entrySet()) {
            long value = entry.getValue() == null ? 0L : entry.getValue();
            rec.counts.merge(entry.getKey(), value, (oldValue, add) -> Math.max(0L, oldValue + add));
        }
        rec.lastScan = lastScan > 0 ? lastScan : System.currentTimeMillis();
        rec.status = ChestStatus.FRESH;
        save();
    }

    public synchronized Map<String, Long> categoryTotals(String server, String category) {
        Map<String, Long> totals = new LinkedHashMap<>();
        server = normalize(server);
        category = normalize(category);
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return totals;
        Map<String, ChestRecord> chests = serverMap.get(category);
        if (chests == null) return totals;
        for (ChestRecord r : chests.values()) {
            for (Map.Entry<String, Long> e : r.counts.entrySet()) totals.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return totals;
    }

    public synchronized List<ChestRecord> getChests(String server, String category) {
        server = normalize(server); category = normalize(category);
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return Collections.emptyList();
        Map<String, ChestRecord> chests = serverMap.get(category);
        if (chests == null) return Collections.emptyList();
        return new ArrayList<>(chests.values());
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            for (var sEntry : index.entrySet()) {
                JsonObject serverObj = new JsonObject();
                for (var cEntry : sEntry.getValue().entrySet()) {
                    JsonArray arr = new JsonArray();
                    for (var chest : cEntry.getValue().values()) {
                        JsonObject o = new JsonObject();
                        o.addProperty("x", chest.x);
                        o.addProperty("y", chest.y);
                        o.addProperty("z", chest.z);
                        o.addProperty("category", chest.category);
                        o.addProperty("lastScan", chest.lastScan);
                        o.addProperty("status", chest.status.name());
                        o.addProperty("usedSlots", chest.usedSlots);
                        o.addProperty("totalSlots", chest.totalSlots);
                        o.addProperty("fullness", chest.fullness);
                        JsonObject counts = new JsonObject();
                        for (var e : chest.counts.entrySet()) counts.addProperty(e.getKey(), e.getValue());
                        o.add("counts", counts);
                        arr.add(o);
                    }
                    serverObj.add(cEntry.getKey(), arr);
                }
                root.add(sEntry.getKey(), serverObj);
            }
            Files.writeString(CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {}
    }

    public synchronized void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) return;
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            index.clear();
            for (var sEntry : root.entrySet()) {
                String server = sEntry.getKey();
                JsonObject serverObj = sEntry.getValue().getAsJsonObject();
                Map<String, Map<String, ChestRecord>> serverMap = new ConcurrentHashMap<>();
                for (var cEntry : serverObj.entrySet()) {
                    String cat = cEntry.getKey();
                    JsonArray arr = cEntry.getValue().getAsJsonArray();
                    Map<String, ChestRecord> chestMap = new ConcurrentHashMap<>();
                    for (JsonElement el : arr) {
                        try {
                            JsonObject o = el.getAsJsonObject();
                            ChestRecord r = new ChestRecord();
                            r.x = o.get("x").getAsInt(); r.y = o.get("y").getAsInt(); r.z = o.get("z").getAsInt();
                            r.category = o.has("category") ? o.get("category").getAsString() : cat;
                            r.lastScan = o.has("lastScan") ? o.get("lastScan").getAsLong() : 0;
                            r.status = ChestStatus.valueOf(o.has("status") ? o.get("status").getAsString() : "STALE");
                            r.usedSlots = o.has("usedSlots") ? o.get("usedSlots").getAsInt() : 0;
                            r.totalSlots = o.has("totalSlots") ? o.get("totalSlots").getAsInt() : 0;
                            r.fullness = o.has("fullness") ? o.get("fullness").getAsDouble() : 0.0;
                            if (o.has("counts")) {
                                JsonObject counts = o.getAsJsonObject("counts");
                                for (var ce : counts.entrySet()) {
                                    try { r.counts.put(ce.getKey(), counts.get(ce.getKey()).getAsLong()); } catch (Exception ignored) {}
                                }
                            }
                            chestMap.put(r.x+":"+r.y+":"+r.z, r);
                        } catch (Exception ignored) {}
                    }
                    serverMap.put(cat, chestMap);
                }
                index.put(server, serverMap);
            }
        } catch (Exception ignored) {}
    }

    // Return total count of an item across all categories/chests for a server
    public synchronized long getTotal(String server, String itemId) {
        server = normalize(server);
        long total = 0L;
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return 0L;
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                try { total += r.counts.getOrDefault(itemId, 0L); } catch (Exception ignored) {}
            }
        }
        return total;
    }

    // Return set of all item keys present in the index for a server
    public synchronized java.util.Set<String> getAllItems(String server) {
        server = normalize(server);
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return keys;
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                try { keys.addAll(r.counts.keySet()); } catch (Exception ignored) {}
            }
        }
        return keys;
    }

    // Optional safety helper: normalize simple ids to minecraft:prefix
    public static String normalizeId(String id) {
        if (id == null) return null;
        return id.contains(":") ? id : ("minecraft:" + id);
    }

    public synchronized void debugPrint(String server) {
        server = normalize(server);
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) {
            System.out.println("[StorageIndex] No data for server " + server);
            return;
        }

        for (var catEntry : serverMap.entrySet()) {
            String category = catEntry.getKey();
            for (var chestEntry : catEntry.getValue().values()) {
                ChestRecord r = chestEntry;
                System.out.println(String.format(
                    "[StorageIndex] [%s] (%d,%d,%d) status=%s slots=%d/%d items=%s",
                    category,
                    r.x, r.y, r.z,
                    r.status,
                    r.usedSlots,
                    r.totalSlots,
                    r.counts.toString()
                ));
            }
        }
    }

}
