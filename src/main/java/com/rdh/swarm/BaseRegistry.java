package com.rdh.swarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A3/A4: the one place that knows where the base stores and smelts.
 *
 * Coordinates are data, not literals scattered through observers. Seeded once
 * from measured truth (docs/FEATURES.md, "Base inventory as measured",
 * 2026-11-13) and then persisted to disk, so a restart resolves the same
 * entries without anyone re-seeding by hand.
 *
 * Nothing here guesses: every seeded entry carries the measurement that put it
 * there in {@link Entry#source}.
 */
public class BaseRegistry {

    public static class Entry {
        public String id;
        public String kind;
        public int x, y, z;
        public boolean doubleChest;
        public int[] twin;
        public String category;
        public String source;

        public Entry() {}

        Entry(String id, String kind, int x, int y, int z, String source) {
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.source = source;
        }

        public String posString() { return x + "," + y + "," + z; }

        public boolean isFurnace() {
            return "blast_furnace".equals(kind) || "smoker".equals(kind) || "furnace".equals(kind);
        }

        public boolean isStorage() { return "chest".equals(kind) || "barrel".equals(kind); }
    }

    public static final Path CONFIG_DIR = Path.of("meteor-client", "enhanced-swarm");
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("base-registry.json");

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private String seededAt = "";
    private boolean loadedFromDisk = false;

    public BaseRegistry() {
        load();
        if (entries.isEmpty()) {
            seedDefaults();
            save();
        }
    }

    /** Measured truth, 2026-11-13, by world_container(open=true) and the fuel reader. */
    private void seedDefaults() {
        seededAt = "2026-11-13 measurement";

        Entry main = new Entry("chest-main", "chest", -550, 91, 601,
            "world_container open=true: 32/54 slots, 768 iron_ingot (double chest half)");
        main.doubleChest = true;
        main.twin = new int[]{-550, 91, 602};
        main.category = "all";
        add(main);

        Entry full = new Entry("chest-full", "chest", -549, 91, 598,
            "world_container open=true: 54/54 slots, 64 iron_ingot, cobble/dirt heavy");
        full.category = "all";
        add(full);

        Entry spare1 = new Entry("chest-spare-1", "chest", -549, 91, 601, "world_container open=true: 0/54 slots (empty)");
        spare1.category = "all";
        add(spare1);

        Entry spare2 = new Entry("chest-spare-2", "chest", -549, 91, 602, "world_container open=true: 0/54 slots (empty)");
        spare2.category = "all";
        add(spare2);

        add(new Entry("bf1", "blast_furnace", -549, 91, 606, "world_container open=true: coal x42 in 1/3 slots"));
        add(new Entry("bf2", "blast_furnace", -550, 91, 606, "world_containers: type at this position"));
        add(new Entry("bf3", "blast_furnace", -551, 91, 606, "world_containers: type at this position"));
        add(new Entry("smoker1", "smoker", -549, 91, 605, "world_containers: type at this position"));
        add(new Entry("smoker2", "smoker", -550, 91, 605, "world_containers: type at this position"));
        add(new Entry("smoker3", "smoker", -551, 91, 605, "world_containers: type at this position"));
        add(new Entry("furnace1", "furnace", -549, 91, 607, "world_containers: type at this position"));
        add(new Entry("furnace2", "furnace", -550, 91, 607, "world_containers: type at this position"));
    }

    public synchronized void add(Entry e) {
        if (e == null || e.id == null || e.id.isBlank()) return;
        entries.put(e.id, e);
    }

    public synchronized Entry byId(String id) {
        return id == null ? null : entries.get(id);
    }

    public synchronized boolean remove(String id) {
        if (id == null || entries.remove(id) == null) return false;
        save();
        return true;
    }

    /** The entry at a position, whatever its id, so a chest is never registered twice. */
    public synchronized Entry byPosition(int x, int y, int z) {
        for (Entry e : entries.values()) {
            if (e != null && e.x == x && e.y == y && e.z == z) return e;
        }
        return null;
    }

    public synchronized List<Entry> all() { return new ArrayList<>(entries.values()); }

    public synchronized List<Entry> storage() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.isStorage()) out.add(e);
        return out;
    }

    public synchronized List<Entry> furnaces() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.isFurnace()) out.add(e);
        return out;
    }

    public synchronized int size() { return entries.size(); }

    public synchronized boolean isLoadedFromDisk() { return loadedFromDisk; }

    public synchronized String getSeededAt() { return seededAt; }

    /** Resolve "x,y,z" the way the probe wants it, or null when the id is unknown. */
    public synchronized String posForId(String id) {
        Entry e = byId(id);
        return e == null ? null : e.posString();
    }

    public synchronized String kindForId(String id) {
        Entry e = byId(id);
        return e == null ? null : e.kind;
    }

    public synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.addProperty("file", CONFIG_FILE.toString());
        root.addProperty("loadedFromDisk", loadedFromDisk);
        root.addProperty("seededAt", seededAt);
        root.addProperty("count", entries.size());
        JsonArray arr = new JsonArray();
        for (Entry e : entries.values()) arr.add(toJson(e));
        root.add("entries", arr);
        return root.toString();
    }

    public static JsonObject toJson(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("kind", e.kind);
        o.addProperty("x", e.x);
        o.addProperty("y", e.y);
        o.addProperty("z", e.z);
        o.addProperty("doubleChest", e.doubleChest);
        if (e.twin != null) {
            JsonArray t = new JsonArray();
            for (int v : e.twin) t.add(v);
            o.add("twin", t);
        }
        if (e.category != null) o.addProperty("category", e.category);
        if (e.source != null) o.addProperty("source", e.source);
        return o;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("seededAt", seededAt);
            JsonArray arr = new JsonArray();
            for (Entry e : entries.values()) arr.add(toJson(e));
            root.add("entries", arr);
            Files.writeString(CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {}
    }

    public synchronized void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) return;
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            if (root == null) return;
            if (root.has("seededAt")) seededAt = root.get("seededAt").getAsString();
            if (!root.has("entries")) return;
            for (var el : root.getAsJsonArray("entries")) {
                try {
                    Entry e = new Gson().fromJson(el, Entry.class);
                    if (e != null && e.id != null) entries.put(e.id, e);
                } catch (Exception ignored) {}
            }
            loadedFromDisk = !entries.isEmpty();
        } catch (Exception ignored) {}
    }
}
