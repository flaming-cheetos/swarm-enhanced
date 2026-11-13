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
        /** Set when the world reports this block as one half of a double chest. */
        public boolean doubleChest = false;
        public int[] twin = null;
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
                        o.addProperty("doubleChest", chest.doubleChest);
                        if (chest.twin != null && chest.twin.length >= 3) {
                            JsonArray t = new JsonArray();
                            t.add(chest.twin[0]); t.add(chest.twin[1]); t.add(chest.twin[2]);
                            o.add("twin", t);
                        }
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
                            r.doubleChest = o.has("doubleChest") && o.get("doubleChest").getAsBoolean();
                            if (o.has("twin") && o.getAsJsonArray("twin").size() >= 3) {
                                var tw = o.getAsJsonArray("twin");
                                r.twin = new int[]{tw.get(0).getAsInt(), tw.get(1).getAsInt(), tw.get(2).getAsInt()};
                            }
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
    /**
     * Replace a chest's contents with what was actually read out of it. This is the only
     * way a chest enters the index: the old additive path took a published delta and added
     * it to whatever the index already believed, so one re-published delta was counted
     * twice and the index drifted above the chests (1591 indexed in a container holding
     * 724). A measurement can be re-taken and stays true; an accumulator cannot.
     */
    public synchronized void setChestContents(String server, String category, int x, int y, int z,
                                              Map<String, Long> counts,
                                              int usedSlots, int totalSlots, long lastScan) {
        setChestContents(server, category, x, y, z, counts, usedSlots, totalSlots, lastScan, null);
    }

    /**
     * @param twin the other half of a double chest, when the world says this block is one.
     *             A double chest is one inventory at two coordinates, so it must hold one
     *             record; storing both halves doubled every total for that container.
     */
    public synchronized void setChestContents(String server, String category, int x, int y, int z,
                                              Map<String, Long> counts,
                                              int usedSlots, int totalSlots, long lastScan,
                                              int[] twin) {
        server = normalize(server);
        category = normalize(category);
        Map<String, Map<String, ChestRecord>> serverMap =
            index.computeIfAbsent(server, k -> new ConcurrentHashMap<>());
        String key = chestKey(x, y, z);
        // Keep whatever category this chest already belongs to, so a measurement taken from
        // an operator probe does not reshuffle the index. Then drop any other copy of the
        // same coordinates: one chest is one container, and summing two categories is how
        // a single chest got counted twice.
        for (var cEntry : serverMap.entrySet()) {
            if (cEntry.getValue().containsKey(key)) { category = cEntry.getKey(); break; }
        }
        for (var cEntry : serverMap.entrySet()) {
            if (!cEntry.getKey().equals(category)) cEntry.getValue().remove(key);
        }
        // Collapse double chests onto one record. If this reading is a known twin, update
        // the record that already represents the container; if it names a twin, drop the
        // twin's own record so the two halves never add up twice.
        if (twin != null && twin.length >= 3) {
            // A twin reading always consumes the other half's record and then writes this
            // one: exactly one record per double chest, at whichever half was last opened.
            // Folding into an existing record instead left the reading's own record behind
            // and the halves kept summing.
            String twinKey = chestKey(twin[0], twin[1], twin[2]);
            for (var cEntry : serverMap.entrySet()) {
                cEntry.getValue().remove(twinKey);
            }
        }
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                if (r.twin != null && r.twin.length >= 3 && r.twin[0] == x && r.twin[1] == y && r.twin[2] == z) {
                    r.counts.clear();
                    if (counts != null) {
                        for (Map.Entry<String, Long> e2 : counts.entrySet()) {
                            if (e2.getKey() != null && e2.getValue() != null && e2.getValue() > 0L) {
                                r.counts.put(e2.getKey(), e2.getValue());
                            }
                        }
                    }
                    r.usedSlots = usedSlots;
                    r.totalSlots = totalSlots;
                    r.lastScan = lastScan > 0 ? lastScan : System.currentTimeMillis();
                    r.status = ChestStatus.FRESH;
                    for (var c2 : serverMap.entrySet()) c2.getValue().remove(key);
                    save();
                    return;
                }
            }
        }
        ChestRecord rec = new ChestRecord();
        rec.x = x; rec.y = y; rec.z = z; rec.category = category;
        if (counts != null) {
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                Long v = e.getValue();
                if (e.getKey() != null && v != null && v > 0L) rec.counts.put(e.getKey(), v);
            }
        }
        rec.usedSlots = usedSlots;
        rec.totalSlots = totalSlots;
        rec.fullness = totalSlots > 0 ? (double) usedSlots / (double) totalSlots : 0.0;
        rec.lastScan = lastScan > 0 ? lastScan : System.currentTimeMillis();
        rec.status = ChestStatus.FRESH;
        if (twin != null && twin.length >= 3) {
            rec.doubleChest = true;
            rec.twin = new int[]{twin[0], twin[1], twin[2]};
        }
        serverMap.computeIfAbsent(category, k -> new ConcurrentHashMap<>()).put(key, rec);
        save();
    }

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

    /**
     * Apply a confirmed withdrawal: the chest at (x,y,z) loses {@code n} of the item.
     * Without this the index kept reporting the pre-withdrawal figure, so a query
     * after a successful transfer still said the ore was in the chest.
     */
    public synchronized void debit(String server, int x, int y, int z, String itemId, long n) {
        if (n <= 0) return;
        String norm = normalizeId(itemId);
        server = normalize(server);
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return;
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                if (r.x != x || r.y != y || r.z != z) continue;
                long have = r.counts.getOrDefault(norm, 0L);
                long left = Math.max(0L, have - n);
                if (left == 0L) r.counts.remove(norm); else r.counts.put(norm, left);
            }
        }
    }

    /**
     * Per-chest breakdown for an item, so a total is attributable to a container:
     * {@code x,y,z=count;x,y,z=count}. Only chests that actually hold some are listed,
     * which is what makes an aggregate number checkable against real chest contents.
     */
    /**
     * Drop the item from one chest. Used when a withdrawal from it moved nothing:
     * the count in that chest was stale, and leaving it made every later query say
     * the ore was there, so the same empty chest was requested again and again.
     */
    public synchronized void clear(String server, int x, int y, int z, String itemId) {
        String norm = normalizeId(itemId);
        server = normalize(server);
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return;
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                if (r.x != x || r.y != y || r.z != z) continue;
                r.counts.remove(norm);
            }
        }
    }

    public synchronized String getBreakdown(String server, String itemId) {
        String norm = normalizeId(itemId);
        server = normalize(server);
        StringBuilder sb = new StringBuilder();
        Map<String, Map<String, ChestRecord>> serverMap = index.get(server);
        if (serverMap == null) return "";
        for (var cEntry : serverMap.entrySet()) {
            for (ChestRecord r : cEntry.getValue().values()) {
                long n = 0L;
                try { n = r.counts.getOrDefault(norm, 0L); } catch (Exception ignored) {}
                if (n <= 0L) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append(r.x).append(',').append(r.y).append(',').append(r.z).append('=').append(n);
            }
        }
        return sb.toString();
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
