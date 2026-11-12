package com.rdh.swarm.task;

import com.google.gson.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
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
import net.minecraft.world.item.Items;

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
                jo.addProperty("attempt", t.getAttempt());
                jo.addProperty("lastOwner", t.getLastOwner());
                jo.addProperty("statusReason", t.getStatusReason());
                jo.addProperty("leaseToken", t.getLeaseToken());
                jo.addProperty("leaseExpiresAt", t.getLeaseExpiresAt());
                if (t.getFailureReason() != null) jo.addProperty("failureReason", t.getFailureReason());
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
                        int attempt = jo.has("attempt") ? jo.get("attempt").getAsInt() : 0;
                        String lastOwner = jo.has("lastOwner") ? jo.get("lastOwner").getAsString() : "";
                        String statusReason = jo.has("statusReason") ? jo.get("statusReason").getAsString() : "";
                        String leaseToken = jo.has("leaseToken") ? jo.get("leaseToken").getAsString() : "";
                        long leaseExpiresAt = jo.has("leaseExpiresAt") ? jo.get("leaseExpiresAt").getAsLong() : 0L;

                        Map<String, Object> data = new HashMap<>();
                        if (itemName != null && !itemName.isEmpty()) {
                            try {
                                Identifier idf = Identifier.tryParse(itemName);
                                Item it = BuiltInRegistries.ITEM.getOptional(idf).orElse(null);
                                if (it != null && it != Items.AIR) data.put("item", it);
                            } catch (Exception ignored) {}
                        }
                        data.put("quantity", targetQty);

                        BotTask t = new BotTask(id, type, priority, data);
                        if (collected > 0) t.addCollected(collected);
                        try { t.setStatus(BotTask.Status.valueOf(statusStr)); } catch (Exception ignored) {}
                        if (assigned != null && !assigned.isEmpty()) t.setAssignedBot(assigned);
                        t.setAttempt(attempt);
                        t.setLastOwner(lastOwner);
                        t.setStatusReason(statusReason);
                        t.setLeaseToken(leaseToken);
                        t.setLeaseExpiresAt(leaseExpiresAt);
                        if (jo.has("failureReason")) t.setFailureReason(jo.get("failureReason").getAsString());
                        if (t != null &&
                            t.getStatus() != BotTask.Status.COMPLETE &&
                            t.getStatus() != BotTask.Status.FAILED &&
                            t.getStatus() != BotTask.Status.CANCELLED) {
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
        // A host restart has no live connection to prove an ACTIVE owner still
        // exists. Recover those rows before the first dispatch can see them.
        try {
            recoverActiveAfterHostRestart();
        } catch (Exception ignored) {}
    }

    public synchronized void clearStale() {
        queue.removeIf(t -> t == null || t.getStatus() == BotTask.Status.COMPLETE || t.getStatus() == BotTask.Status.FAILED || t.getStatus() == BotTask.Status.CANCELLED);
        save();
    }

    // Backwards compatible name requested by web UI code
    public synchronized void clearCompleted() {
        queue.removeIf(t -> t == null || t.getStatus() == BotTask.Status.COMPLETE || t.getStatus() == BotTask.Status.FAILED || t.getStatus() == BotTask.Status.CANCELLED);
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
     * The single lease-granting path. Selection, ownership, token, expiry,
     * attempt, status, and persistence happen under one synchronized claim so
     * two workers cannot receive the same task.
     */
    public synchronized BotTask claimNextForBot(String botName, long now, long ttlMs) {
        return claimNextForBot(null, botName, now, ttlMs);
    }

    /** Targeted claim for manual/command assignment; same lease core as dispatch. */
    public synchronized BotTask claimNextForBot(UUID targetTaskId, String botName, long now, long ttlMs) {
        if (botName == null || botName.isEmpty() || ttlMs <= 0) return null;
        if (hasActiveTask(botName)) return null;

        List<BotTask> ordered = toList();
        ordered.sort(Comparator.comparingInt(BotTask::getPriority).reversed());
        for (BotTask task : ordered) {
            if (task == null || task.getStatus() != BotTask.Status.PENDING) continue;
            if (targetTaskId != null && !targetTaskId.equals(task.getId())) continue;
            String assigned = task.getAssignedBot();
            if (assigned != null && !assigned.isEmpty()
                && !assigned.equalsIgnoreCase("any")
                && !assigned.equalsIgnoreCase("all")
                && !assigned.equalsIgnoreCase(botName)) {
                continue;
            }
            if (!task.getLeaseToken().isEmpty() && task.getLeaseExpiresAt() > now) {
                // A live lease on a PENDING row is inconsistent (for example,
                // a racing requeue). Never hand it to a second owner.
                continue;
            }

            long expiry = ttlMs > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMs;
            task.setAssignedBot(botName);
            task.setLeaseToken(UUID.randomUUID().toString());
            task.setLeaseExpiresAt(expiry);
            task.setAttempt(task.getAttempt() + 1);
            task.setStatusReason("leased");
            task.setStatus(BotTask.Status.ACTIVE);
            save();
            return task;
        }
        return null;
    }

    /** Extend only a live, current-token lease. */
    public synchronized boolean renewLease(UUID taskId, String token, long now, long ttlMs) {
        if (taskId == null || token == null || token.isEmpty() || ttlMs <= 0) return false;
        BotTask task = findById(taskId);
        if (task == null || task.getStatus() != BotTask.Status.ACTIVE) return false;
        if (task.getLeaseToken().isEmpty() || !task.getLeaseToken().equals(token)) return false;
        if (task.getLeaseExpiresAt() <= now) return false;

        long expiry = ttlMs > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMs;
        task.setLeaseExpiresAt(expiry);
        save();
        return true;
    }

    /** Renew by the worker that currently owns the lease; used by TASK_PROGRESS. */
    public synchronized boolean renewLeaseForBot(UUID taskId, String botName, long now, long ttlMs) {
        if (taskId == null || botName == null || botName.isEmpty() || ttlMs <= 0) return false;
        BotTask task = findById(taskId);
        if (task == null || task.getStatus() != BotTask.Status.ACTIVE) return false;
        String assigned = task.getAssignedBot();
        if (assigned == null || !assigned.equalsIgnoreCase(botName)) return false;
        return renewLease(taskId, task.getLeaseToken(), now, ttlMs);
    }

    /** Return every ACTIVE row whose lease is absent or lapsed to PENDING. */
    public synchronized int expireLeases(long now) {
        int expired = 0;
        for (BotTask task : queue) {
            if (task == null || task.getStatus() != BotTask.Status.ACTIVE) continue;
            if (!task.getLeaseToken().isEmpty() && task.getLeaseExpiresAt() > now) continue;

            task.setLastOwner(task.getAssignedBot());
            task.setAssignedBot("");
            task.setLeaseToken("");
            task.setLeaseExpiresAt(0L);
            task.setAttempt(task.getAttempt() + 1);
            task.setStatus(BotTask.Status.PENDING);
            task.setStatusReason("lease-expired");
            expired++;
        }
        if (expired > 0) save();
        return expired;
    }

    private BotTask findById(UUID id) {
        for (BotTask task : queue) {
            if (task != null && id.equals(task.getId())) return task;
        }
        return null;
    }

    /**
     * Recover tasks that were ACTIVE when the host died. There is no live
     * owner to trust at startup, so the task is requeued instead of remaining
     * permanently blocked by hasActiveTask().
     */
    public synchronized int recoverActiveAfterHostRestart() {
        int requeued = 0;
        for (BotTask task : queue) {
            if (task == null || task.getStatus() != BotTask.Status.ACTIVE) continue;
            task.setLastOwner(task.getAssignedBot());
            task.setAssignedBot("");
            task.setLeaseToken("");
            task.setLeaseExpiresAt(0L);
            task.setAttempt(task.getAttempt() + 1);
            task.setStatus(BotTask.Status.PENDING);
            task.setStatusReason("host-restart");
            requeued++;
        }
        if (requeued > 0) save();
        return requeued;
    }

    /**
     * Return ACTIVE tasks assigned to a dead/removed bot to PENDING so another worker can take them.
     * Returns the number of tasks requeued.
     */
    public synchronized int requeueActiveForBot(String botName) {
        if (botName == null || botName.isEmpty()) return 0;
        int requeued = 0;
        for (BotTask task : queue) {
            if (task == null || task.getStatus() != BotTask.Status.ACTIVE) continue;
            String assigned = task.getAssignedBot();
            if (assigned != null && assigned.equalsIgnoreCase(botName)) {
                task.setLastOwner(assigned);
                task.setStatus(BotTask.Status.PENDING);
                task.setAssignedBot("");
                task.setLeaseToken("");
                task.setLeaseExpiresAt(0L);
                task.setAttempt(task.getAttempt() + 1);
                task.setStatusReason("worker-disconnect");
                requeued++;
            }
        }
        if (requeued > 0) {
            save();
            ChatUtils.warningPrefix("TaskQueue", "Requeued %d active task(s) from %s", requeued, botName);
        }
        return requeued;
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
