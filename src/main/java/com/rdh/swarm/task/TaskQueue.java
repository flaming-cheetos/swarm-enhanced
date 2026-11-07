package com.rdh.swarm.task;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class TaskQueue {

    private final Queue<BotTask> queue;
    private final java.util.Map<String, String> nicknames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Path CONFIG_DIR = Path.of("meteor-client", "enhanced-swarm");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("task-queue.json");

    public TaskQueue() {
        this.queue = new PriorityQueue<>(Comparator.comparingInt(BotTask::getPriority).reversed());
        load();
    }

    public synchronized void add(BotTask task) {
        queue.add(task);
        save();
    }

    public synchronized BotTask poll() {
        return queue.poll();
    }

    public synchronized BotTask peek() {
        return queue.peek();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized boolean removeById(UUID id) {
        if (id == null) return false;
        for (BotTask t : new ArrayList<>(queue)) {
            if (t != null && id.equals(t.getId())) {
                queue.remove(t);
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized List<BotTask> toList() {
        return new ArrayList<>(queue);
    }

    // Minimal nickname support - returns the given name by default.
    public String getNickname(String workerName) {
        if (workerName == null) return "";
        return nicknames.getOrDefault(workerName, workerName);
    }

    public void setNickname(String workerName, String nickname) {
        if (workerName == null) return;
        if (nickname == null) nickname = "";
        nicknames.put(workerName, nickname);
        save();
    }

    public synchronized java.util.List<BotTask> getAllTasks() {
        return toList();
    }

    public synchronized java.util.Map<String,String> getAllNicknames() {
        return new java.util.HashMap<>(nicknames);
    }

    public synchronized void addTask(BotTask task) {
        add(task); // add() persists once; do not save twice
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();

            // Tasks
            JsonArray tasks = new JsonArray();
            for (BotTask t : toList()) {
                JsonObject jo = new JsonObject();
                jo.addProperty("id", t.getId().toString());
                jo.addProperty("type", t.getType().name());
                jo.addProperty("priority", t.getPriority());
                jo.addProperty("item", t.getItemName());
                jo.addProperty("targetQuantity", t.getTargetQuantity());
                jo.addProperty("collected", t.getCollectedQuantity());
                try { jo.addProperty("status", t.getStatus().name()); } catch (Exception ignored) { jo.addProperty("status", "PENDING"); }
                jo.addProperty("assignedBot", t.getAssignedBot() == null ? "" : t.getAssignedBot());
                tasks.add(jo);
            }
            root.add("tasks", tasks);

            // Nicknames
            JsonObject nobj = new JsonObject();
            for (var e : nicknames.entrySet()) {
                nobj.addProperty(e.getKey(), e.getValue());
            }
            root.add("nicknames", nobj);

            Files.writeString(CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public synchronized void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) return;
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Load nicknames
            nicknames.clear();
            if (root.has("nicknames") && root.get("nicknames").isJsonObject()) {
                JsonObject nobj = root.getAsJsonObject("nicknames");
                for (var entry : nobj.entrySet()) {
                    nicknames.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            // Load tasks
            queue.clear();
            if (root.has("tasks") && root.get("tasks").isJsonArray()) {
                JsonArray tasks = root.getAsJsonArray("tasks");
                for (JsonElement el : tasks) {
                    try {
                        JsonObject jo = el.getAsJsonObject();
                        UUID id = UUID.fromString(jo.get("id").getAsString());
                        BotTask.Type type = BotTask.Type.valueOf(jo.get("type").getAsString());
                        int priority = jo.has("priority") ? jo.get("priority").getAsInt() : 0;
                        String itemName = jo.has("item") ? jo.get("item").getAsString() : "";
                        int targetQty = jo.has("targetQuantity") ? jo.get("targetQuantity").getAsInt() : 0;
                        int collected = jo.has("collected") ? jo.get("collected").getAsInt() : 0;
                        String statusStr = jo.has("status") ? jo.get("status").getAsString() : "PENDING";
                        String assigned = jo.has("assignedBot") ? jo.get("assignedBot").getAsString() : "";

                        Map<String, Object> data = new HashMap<>();
                        if (itemName != null && !itemName.isEmpty()) {
                            try {
                                Identifier idf = Identifier.tryParse(itemName);
                                Item it = BuiltInRegistries.ITEM.getValue(idf);
                                data.put("item", it);
                            } catch (Exception ignored) {}
                        }
                        data.put("quantity", targetQty);

                        BotTask t = new BotTask(id, type, priority, data);
                        if (collected > 0) t.addCollected(collected);
                        try { t.setStatus(BotTask.Status.valueOf(statusStr)); } catch (Exception ignored) {}
                        if (assigned != null && !assigned.isEmpty()) t.setAssignedBot(assigned);
                        if (t != null &&
                            t.getStatus() != BotTask.Status.COMPLETE &&
                            t.getStatus() != BotTask.Status.FAILED) {
                            addTask(t);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        // Remove any stale completed/failed tasks that might remain and persist the clean state
        try {
            clearStale();
        } catch (Exception ignored) {}
    }

    public synchronized void clearStale() {
        queue.removeIf(t -> t == null || t.getStatus() == BotTask.Status.COMPLETE || t.getStatus() == BotTask.Status.FAILED);
        save();
    }

    // Backwards compatible name requested by web UI code
    public synchronized void clearCompleted() {
        queue.removeIf(t -> t == null || t.getStatus() == BotTask.Status.COMPLETE || t.getStatus() == BotTask.Status.FAILED);
        save();
    }

    /**
     * Returns the next PENDING task for a specific bot.
     * Checks tasks where bot field matches botName,
     * OR bot field is null/empty/"any"/"all".
     * Returns null if no pending tasks exist.
     */
    public synchronized BotTask getNextPendingForBot(String botName) {
        List<BotTask> ordered = toList();
        ordered.sort(Comparator.comparingInt(BotTask::getPriority).reversed());
        for (BotTask task : ordered) {
            if (task.getStatus() == BotTask.Status.PENDING) {
                String assigned = task.getAssignedBot();
                if (assigned == null || assigned.isEmpty()
                    || assigned.equalsIgnoreCase("any")
                    || assigned.equalsIgnoreCase("all")
                    || assigned.equalsIgnoreCase(botName)) {
                    return task;
                }
            }
        }
        return null;
    }

    /**
     * Check if a bot currently has an ACTIVE task.
     */
    public synchronized boolean hasActiveTask(String botName) {
        for (BotTask task : toList()) {
            if (task.getStatus() == BotTask.Status.ACTIVE) {
                String assigned = task.getAssignedBot();
                if (assigned != null && assigned.equalsIgnoreCase(botName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
