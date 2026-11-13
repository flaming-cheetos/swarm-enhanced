package com.rdh.swarm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.task.BotTask;

public class EnhancedSwarmConnection extends Thread {
    public final Socket socket;
    private final EnhancedSwarmHost host;
    public final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    public String workerName;
    public WorkerStatus lastStatus;
    private static final byte[] _cb = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73,0x2F,0x72,
        0x64,0x68
    };
    private static final String _co = new StringBuilder()
        .append("flaming").append("cheet").append("0s")
        .append("/").append("rdh").append(" forever").toString();
    private DataOutputStream out;
    private DataInputStream in;
    private static final int _cx = 0x726468;
    private static final long _cn = 0x666C616D696E67L;

    private static UUID parseTaskId(String value) {
        try { return UUID.fromString(value); } catch (Exception ignored) { return null; }
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        try { Integer.parseInt(value); return true; } catch (Exception ignored) { return false; }
    }

    private static boolean looksLikeUuid(String value) {
        return parseTaskId(value) != null;
    }

    private void logRejectedReport(String kind, String taskId, String token) {
        String shortToken = token == null || token.isEmpty()
            ? "legacy" : token.substring(0, Math.min(8, token.length()));
        ChatUtils.warningPrefix("Swarm", "TASK: rejected stale %s %s token=%s", kind, taskId, shortToken);
        try {
            if (host != null && host.getWebServer() != null) {
                host.getWebServer().logWarn("TASK: rejected stale " + kind + " " + taskId + " token=" + shortToken);
            }
        } catch (Exception ignored) {}
    }

    public EnhancedSwarmConnection(EnhancedSwarmHost host, Socket socket) {
        this.host = host;
        this.socket = socket;
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "New worker connected on %s.", getIp(socket.getInetAddress().getHostAddress()));
        try {
            if (host != null && host.getWebServer() != null) host.getWebServer().logInfo("🔗 " + (workerName == null ? getConnection() : workerName) + " connected");
        } catch (Exception ignored) {}

        try {
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // configure a read timeout so we can detect disconnects and also poll
            try {
                socket.setSoTimeout(2000);
            } catch (Exception ignored) {}

            // First message must be worker:<name>:<token>. Empty host token only permits loopback.
            String suppliedToken = "";
            boolean authenticated = false;
            try {
                String raw = in.readUTF();
                if (raw != null && raw.startsWith("worker:")) {
                    String rest = raw.substring(7).trim();
                    int sep = rest.indexOf(':');
                    if (sep >= 0) {
                        workerName = rest.substring(0, sep).trim();
                        suppliedToken = rest.substring(sep + 1);
                    } else {
                        workerName = rest;
                    }
                    authenticated = workerName != null && !workerName.isEmpty()
                        && (host == null || host.isAuthorized(socket, suppliedToken));
                }
            } catch (java.net.SocketTimeoutException ignored) {
                authenticated = false;
            } catch (IOException e) {
                ChatUtils.infoPrefix("Swarm", "Error reading initial name from %s.", getIp(socket.getInetAddress().getHostAddress()));
                disconnect();
                return;
            }

            if (!authenticated) {
                ChatUtils.warningPrefix("Swarm", "Rejected unauthorized worker from %s.", getIp(socket.getInetAddress().getHostAddress()));
                disconnect();
                return;
            }

            // Reconnect handshake: fence the old assignment, then tell the
            // worker to drop its local copy before the next claim is dispatched.
            try {
                if (workerName != null && host != null) {
                    int requeued = host.handleWorkerReconnect(workerName);
                    if (requeued > 0) {
                        try { if (host.getWebServer() != null) host.getWebServer().logWarn("Requeued " + requeued + " active task(s) on reconnect for " + workerName); } catch (Exception ignored) {}
                    }
                    messageQueue.add("swarm:" + workerName + " task reset");
                }
            } catch (Exception ignored) {}

            // Request a sync from the worker once we have a name (non-blocking)
            try {
                if (workerName != null && host != null) {
                    // Send sync request to the worker
                    messageQueue.add("swarm:sync-settings");

                    // Push deposit chest config after a short delay to allow worker-side setup
                    final String wname = workerName;
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        try {
                            if (host != null && wname != null && !wname.isEmpty()) {
                                // Push chest coords directly to this connection's queue to avoid race with sendTo()
                                    try {
                                    var depositConfig = host.getDepositConfig();
                                    String server = host.getCurrentServer();
                                    java.util.Map<String, java.util.List<int[]>> chests = depositConfig.getChests(server);

                                    int pushed = 0;
                                    if (chests != null) {
                                        messageQueue.add(String.format("swarm:%s deposit reset %d", wname, depositConfig.getGeneration(server)));
                                        for (var entry : chests.entrySet()) {
                                            for (int[] pos : entry.getValue()) {
                                                messageQueue.add(String.format("swarm:%s deposit set %s %d %d %d", wname, entry.getKey(), pos[0], pos[1], pos[2]));
                                                pushed++;
                                            }
                                        }
                                        messageQueue.add(String.format("swarm:%s deposit commit", wname));
                                        ChatUtils.infoPrefix("Swarm", "Auto-pushed %d chest(s) to %s for server %s", pushed, wname, server);
                                        try { if (host.getWebServer() != null) host.getWebServer().logInfo("Pushed deposit config to " + wname); } catch (Exception ignored) {}
                                    }
                                    // push base anchor if present
                                    try {
                                        int[] base = depositConfig.getBase(server);
                                        if (base != null && base.length >= 3) {
                                            messageQueue.add(String.format("swarm:%s base set %d %d %d", wname, base[0], base[1], base[2]));
                                            ChatUtils.infoPrefix("Swarm", "Auto-pushed base anchor to %s for server %s", wname, server);
                                        }
                                    } catch (Exception ignored) {}
                                    // push waypoints for this server
                                    try {
                                        var wm = host.getWaypointManager();
                                        if (wm != null) {
                                            var wps = wm.getAllWaypoints();
                                            int pushedWp = 0;
                                            if (wps != null) {
                                                for (SwarmWaypoint wp : wps) {
                                                    try {
                                                        String name = wp.name == null ? "" : wp.name;
                                                        String cat = wp.category == null ? "" : wp.category;
                                                        String owx = wp.overworldX == null ? "null" : String.valueOf(wp.overworldX);
                                                        String owY = wp.overworldY == null ? "null" : String.valueOf(wp.overworldY);
                                                        String owz = wp.overworldZ == null ? "null" : String.valueOf(wp.overworldZ);
                                                        String nx = wp.netherX == null ? "null" : String.valueOf(wp.netherX);
                                                        String ny = wp.netherY == null ? "null" : String.valueOf(wp.netherY);
                                                        String nz = wp.netherZ == null ? "null" : String.valueOf(wp.netherZ);
                                                        String msg = String.format("swarm:%s waypoint set %s %s %s %s %s %s %s %s", wname, name, cat, owx, owY, owz, nx, ny, nz);
                                                        messageQueue.add(msg);
                                                        pushedWp++;
                                                    } catch (Exception ignored) {}
                                                }
                                            }
                                            if (pushedWp > 0) {
                                                ChatUtils.infoPrefix("Swarm", "Auto-pushed %d waypoint(s) to %s for server %s", pushedWp, wname, server);
                                                try { if (host.getWebServer() != null) host.getWebServer().logInfo("Pushed waypoints to " + wname); } catch (Exception ignored) {}
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                } catch (Exception ignored) {}

                                // Push auto-deposit settings via direct queue injection
                                try {
                                    var modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
                                    com.rdh.swarm.EnhancedSwarm mod = modules.get(com.rdh.swarm.EnhancedSwarm.class);
                                    if (mod != null) {
                                        try {
                                            if (mod.autoDepositSetting.get()) messageQueue.add("swarm:" + wname + " deposit auto on");
                                        } catch (Exception ignored) {}
                                        try { messageQueue.add("swarm:" + wname + " deposit threshold " + mod.autoDepositThreshold.get()); } catch (Exception ignored) {}
                                    }
                                } catch (Throwable ignored) {}
                            }

                            // After initial setup, attempt to dispatch next task for this worker
                            try {
                                if (host != null && wname != null && !wname.isEmpty()) {
                                    host.dispatchNextTask(wname);
                                }
                            } catch (Exception ignored) {}
                        } catch (Exception ignored) {}
                    }).start();
                }
            } catch (Exception ignored) {}

            while (!isInterrupted()) {
                // Send any queued messages
                String outMsg;
                while ((outMsg = messageQueue.poll()) != null) {
                    try {
                        out.writeUTF(outMsg);
                        out.flush();
                    } catch (Exception e) {
                        ChatUtils.errorPrefix("Swarm", "Send failed to %s", workerName == null ? getConnection() : workerName);
                        disconnect();
                        return;
                    }
                }

                // Read incoming; readUTF will block until data or timeout
                try {
                    String read = null;
                    try {
                        read = in.readUTF();
                    } catch (java.net.SocketTimeoutException ste) {
                        // timeout - no data this cycle
                    }

                        if (read != null) {
                            // Allow workers to explicitly request config push
                            if (read.startsWith("REQUEST_CONFIG:")) {
                                try {
                                    String[] parts = read.split(":", 2);
                                    if (parts.length >= 2 && host != null) {
                                        String reqName = parts[1].trim();
                                        if (!reqName.isEmpty()) {
                                            host.pushDepositConfig(reqName);
                                            try { if (host.getWebServer() != null) host.getWebServer().logInfo("REQUEST_CONFIG from " + reqName); } catch (Exception ignored) {}
                                        }
                                    }
                                } catch (Exception ignored) {}
                                continue;
                            }
                        
                            
                        if (read.startsWith("STATUS")) {
                            WorkerStatus ws = WorkerStatus.deserialize(read);
                            if (ws != null) {
                                lastStatus = ws;
                                workerName = ws.name;
                            }
                        } else if (read.startsWith("TASK_PROGRESS:")) {
                            try {
                                String[] parts = read.split(":", 8);
                                if (parts.length >= 5) {
                                    String wName = parts[1];
                                    String taskId = parts[2];
                                    // New protocol puts the token before collected; legacy
                                    // workers start with the numeric collected count.
                                    boolean hasToken = !isInteger(parts[3]);
                                    int base = hasToken ? 4 : 3;
                                    String leaseToken = hasToken ? parts[3] : "";
                                    int collected = 0;
                                    int total = 0;
                                    try { collected = Integer.parseInt(parts[base]); } catch (Exception ignored) {}
                                    try { total = Integer.parseInt(parts[base + 1]); } catch (Exception ignored) {}
                                    String taskState = parts.length > base + 2 ? parts[base + 2] : "";
                                    String eta = parts.length > base + 3 ? parts[base + 3] : "";

                                    if (host != null) {
                                        TaskQueue tq = host.getTaskQueue();
                                        UUID tid = parseTaskId(taskId);
                                        boolean accepted = tq != null && tid != null
                                            && EnhancedSwarmHost.recordWorkerProgress(tq, tid, wName, leaseToken,
                                                collected, System.currentTimeMillis(), EnhancedSwarmHost.LEASE_TTL_MS);
                                        if (!accepted) {
                                            logRejectedReport("progress", taskId, leaseToken);
                                        } else {
                                            BotStatus bs = host.getBotStatus(wName);
                                            if (bs != null) {
                                                bs.collected = collected;
                                                bs.total = total;
                                                bs.taskId = taskId;
                                                bs.state = taskState;
                                                bs.eta = eta;
                                                bs.lastUpdate = System.currentTimeMillis();
                                                bs.online = true;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("TASK_COMPLETE:")) {
                            try {
                                // New: TASK_COMPLETE:<worker>:<id>:<token>:<item>:<qty>:<elapsed>
                                // Legacy: TASK_COMPLETE:<worker>:<id>:<item>:<qty>:<elapsed>
                                String[] parts = read.split(":", 7);
                                if (parts.length >= 6) {
                                    String w = parts[1];
                                    String tid = parts[2];
                                    boolean hasToken = parts.length >= 7 || (parts.length == 6 && looksLikeUuid(parts[3]));
                                    String leaseToken = hasToken ? parts[3] : "";
                                    int base = hasToken ? 4 : 3;
                                    String item = parts[base];
                                    String qty = parts[base + 1];
                                    String elapsed = parts[base + 2];
                                    UUID taskId = parseTaskId(tid);
                                    boolean accepted = host != null && taskId != null
                                        && EnhancedSwarmHost.finishTaskFromWorker(host.getTaskQueue(), taskId, w,
                                            leaseToken, BotTask.Status.COMPLETE, "completed");
                                    if (!accepted) {
                                        logRejectedReport("completion", tid, leaseToken);
                                    } else {
                                        ChatUtils.infoPrefix("Swarm", "✓ %s completed: %s x%s in %s", w, item, qty, elapsed);
                                        try { if (host.getWebServer() != null) host.getWebServer().logInfo("✓ " + w + " completed: " + item + " x" + qty); } catch (Exception ignored) {}
                                        try { host.dispatchNextTask(w); } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed parsing TASK_COMPLETE: %s", e.getMessage()); }
                        } else if (read.startsWith("TASK_FAILED:")) {
                            try {
                                // New: TASK_FAILED:<worker>:<id>:<token>:<reason>
                                // Legacy: TASK_FAILED:<worker>:<id>:<reason>
                                String[] parts = read.split(":", 5);
                                if (parts.length >= 4) {
                                    String w = parts[1];
                                    String tid = parts[2];
                                    boolean hasToken = parts.length >= 5 || (parts.length == 4 && looksLikeUuid(parts[3]));
                                    String leaseToken = hasToken ? parts[3] : "";
                                    String reason = hasToken && parts.length >= 5 ? parts[4] : "";
                                    if (!hasToken) reason = parts[3];
                                    UUID taskId = parseTaskId(tid);
                                    boolean accepted = host != null && taskId != null
                                        && EnhancedSwarmHost.finishTaskFromWorker(host.getTaskQueue(), taskId, w,
                                            leaseToken, BotTask.Status.FAILED, reason);
                                    if (!accepted) {
                                        logRejectedReport("failure", tid, leaseToken);
                                    } else {
                                        ChatUtils.warningPrefix("Swarm", "✗ %s task failed: %s", w, reason);
                                        try { if (host.getWebServer() != null) host.getWebServer().logWarn("✗ " + w + " task failed: " + reason); } catch (Exception ignored) {}
                                        try { host.dispatchNextTask(w); } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed parsing TASK_FAILED: %s", e.getMessage()); }
                        } else if (read.startsWith("TASK_CANCELLED:")) {
                            try {
                                // New: TASK_CANCELLED:<worker>:<id>:<token>:<reason>
                                // Legacy: TASK_CANCELLED:<worker>:<id>:<reason>
                                String[] parts = read.split(":", 5);
                                if (parts.length >= 4) {
                                    String w = parts[1];
                                    String tid = parts[2];
                                    boolean hasToken = parts.length >= 5 || (parts.length == 4 && looksLikeUuid(parts[3]));
                                    String leaseToken = hasToken ? parts[3] : "";
                                    String reason = hasToken && parts.length >= 5 ? parts[4] : "";
                                    if (!hasToken) reason = parts[3];
                                    UUID taskId = parseTaskId(tid);
                                    boolean accepted = host != null && taskId != null
                                        && EnhancedSwarmHost.finishTaskFromWorker(host.getTaskQueue(), taskId, w,
                                            leaseToken, BotTask.Status.CANCELLED, reason);
                                    if (!accepted) {
                                        logRejectedReport("cancellation", tid, leaseToken);
                                    } else {
                                        ChatUtils.infoPrefix("Swarm", "■ %s task cancelled: %s", w, reason);
                                        try { if (host.getWebServer() != null) host.getWebServer().logInfo("■ " + w + " task cancelled: " + reason); } catch (Exception ignored) {}
                                        try { host.dispatchNextTask(w); } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed parsing TASK_CANCELLED: %s", e.getMessage()); }
                        } else if (read.startsWith("BOT_STATUS:")) {
                            try {
                                BotStatus bs = BotStatus.deserialize(read);
                                if (bs != null && host != null) host.putBotStatus(bs);
                            } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed parsing BOT_STATUS: %s", e.getMessage()); }
                        } else if (read.startsWith("CHEST_FULL:")) {
                            try {
                                String[] parts = read.split(":", 2);
                                String cat = parts.length > 1 ? parts[1] : "";
                                ChatUtils.warningPrefix("Swarm", "Worker reported chest full for category: %s", cat);
                                try { if (host != null && host.getWebServer() != null) host.getWebServer().logWarn("Worker reported chest full: " + cat); } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("CHEST_DELTA:")) {
                            try {
                                String body = read.substring("CHEST_DELTA:".length());
                                int sep = body.indexOf('|');
                                if (sep > 0) {
                                    String header = body.substring(0, sep);
                                    String[] parts = header.split(":");
                                    if (parts.length >= 4 && host != null) {
                                        String category = parts[0];
                                        int x = Integer.parseInt(parts[1]);
                                        int y = Integer.parseInt(parts[2]);
                                        int z = Integer.parseInt(parts[3]);
                                        JsonObject o = JsonParser.parseString(body.substring(sep + 1)).getAsJsonObject();
                                        Map<String, Long> delta = new LinkedHashMap<>();
                                        if (o.has("counts")) {
                                            for (var entry : o.getAsJsonObject("counts").entrySet()) {
                                                try { delta.put(entry.getKey(), entry.getValue().getAsLong()); } catch (Exception ignored) {}
                                            }
                                        }
                                        long when = o.has("lastScan") ? o.get("lastScan").getAsLong() : System.currentTimeMillis();
                                        host.getStorageIndex().applyChestDelta(host.getCurrentServer(), category, x, y, z, delta, when);
                                    }
                                }
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("SCAN_CHEST_RESULT:")) {
                            try {
                                // Format: SCAN_CHEST_RESULT:category:x:y:z|<json>
                                String body = read.substring("SCAN_CHEST_RESULT:".length());
                                int sep = body.indexOf('|');
                                if (sep > 0) {
                                    String header = body.substring(0, sep);
                                    String json = body.substring(sep + 1);
                                    String[] parts = header.split(":");
                                    if (parts.length >= 4 && host != null) {
                                        String category = parts[0];
                                        int x = Integer.parseInt(parts[1]);
                                        int y = Integer.parseInt(parts[2]);
                                        int z = Integer.parseInt(parts[3]);
                                        // parse json into ChestRecord
                                        try {
                                            StorageIndex.ChestRecord rec = new StorageIndex.ChestRecord();
                                            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                                            if (o.has("counts")) {
                                                JsonObject counts = o.getAsJsonObject("counts");
                                                for (var e : counts.entrySet()) {
                                                    try { rec.counts.put(e.getKey(), counts.get(e.getKey()).getAsLong()); } catch (Exception ignored) {}
                                                }
                                            }
                                            rec.x = x; rec.y = y; rec.z = z;
                                            rec.category = category;
                                            rec.lastScan = o.has("lastScan") ? o.get("lastScan").getAsLong() : System.currentTimeMillis();
                                            rec.usedSlots = o.has("usedSlots") ? o.get("usedSlots").getAsInt() : 0;
                                            rec.totalSlots = o.has("totalSlots") ? o.get("totalSlots").getAsInt() : 0;
                                            rec.fullness = o.has("fullness") ? o.get("fullness").getAsDouble() : 0.0;
                                            try { rec.status = StorageIndex.ChestStatus.valueOf(o.has("status") ? o.get("status").getAsString() : "FRESH"); } catch (Exception ignored) { rec.status = StorageIndex.ChestStatus.FRESH; }
                                            host.getStorageIndex().applyChestResult(host.getCurrentServer(), workerName == null ? "unknown" : workerName, category, x, y, z, rec);
                                            ChatUtils.infoPrefix("Swarm", "Scan result from %s: %s@%d,%d,%d", workerName, category, x, y, z);
                                            try { host.getStorageIndex().debugPrint(host.getCurrentServer()); } catch (Exception ignored) {}
                                        } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed parsing SCAN_CHEST_RESULT: %s", e.getMessage()); }
                                    }
                                }
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("WITHDRAW_RESULT:")) {
                            // <itemId...>:x:y:z:moved — the id carries its own ':', so read the
                            // last four tokens and join the rest back into the id.
                            try {
                                String body = read.substring("WITHDRAW_RESULT:".length());
                                String[] f = body.split(":");
                                if (f.length >= 5) {
                                    int moved = Integer.parseInt(f[f.length - 1].trim());
                                    int z = Integer.parseInt(f[f.length - 2].trim());
                                    int y = Integer.parseInt(f[f.length - 3].trim());
                                    int x = Integer.parseInt(f[f.length - 4].trim());
                                    String itemId = String.join(":", java.util.Arrays.copyOfRange(f, 0, f.length - 4)).trim();
                                    String norm = com.rdh.swarm.StorageIndex.normalizeId(itemId);
                                    String server = host.getCurrentServer();
                                    long before = host.getStorageIndex().getTotal(server, norm);
                                    host.getStorageIndex().debit(server, x, y, z, norm, moved);
                                    if (moved <= 0) {
                                        // The chest gave us nothing, so its recorded count was
                                        // stale. Without this the phantom stayed and every
                                        // later query sent the bot back to an empty chest.
                                        host.getStorageIndex().clear(server, x, y, z, norm);
                                        System.out.println(String.format(
                                            "[WITHDRAW STALE] item=%s chest=%d,%d,%d moved=0, cleared its index entry",
                                            norm, x, y, z));
                                    }
                                    long after = host.getStorageIndex().getTotal(server, norm);
                                    System.out.println(String.format(
                                        "[WITHDRAW DONE] item=%s chest=%d,%d,%d moved=%d index %d -> %d",
                                        norm, x, y, z, moved, before, after));
                                    ChatUtils.infoPrefix("Swarm", "Withdraw confirmed: %d %s from %d,%d,%d (index now %d)",
                                        moved, norm, x, y, z, after);
                                }
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("WITHDRAW_REFUSED:")) {
                            // The chest could not be opened (another menu was up). Nothing
                            // moved, so the index is untouched, but the refusal is logged so
                            // a send with no matching [WITHDRAW DONE] is explainable.
                            System.out.println("[WITHDRAW REFUSED] " + read.substring("WITHDRAW_REFUSED:".length()));
                        } else if (read.startsWith("WORLD_PROBE:")) {
                            try {
                                String body = read.substring("WORLD_PROBE:".length());
                                int bar = body.indexOf('|');
                                String json = bar >= 0 ? body.substring(bar + 1) : body;
                                host.acceptWorldProbe(workerName, json);
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("DEBUG_STATE:")) {
                            try {
                                String body = read.substring("DEBUG_STATE:".length());
                                int bar = body.indexOf('|');
                                String nonce = bar >= 0 ? body.substring(0, bar) : "";
                                String json = bar >= 0 ? body.substring(bar + 1) : body;
                                host.acceptDebugState(workerName, json);
                                if (host.getWebServer() != null) {
                                    host.getWebServer().logInfo("Debug state from " + workerName
                                        + " (nonce " + nonce + ", " + json.length() + " chars)");
                                }
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("SCAN_COMPLETE:")) {
                            try {
                                String body = read.substring("SCAN_COMPLETE:".length()).trim();
                                String category = body.isEmpty() ? "all" : body;
                                ChatUtils.infoPrefix("Swarm", "Scan complete from %s for category %s", workerName == null ? "?" : workerName, category);
                                try { if (host != null && host.getWebServer() != null) host.getWebServer().logInfo("Scan complete from " + workerName + " category " + category); } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("SETTINGS:")) {
                            // legacy
                            parseSettings(read);
                        } else if (read.startsWith("STORAGE_QUERY_REQUEST:")) {
                            try {
                                // Format: STORAGE_QUERY_REQUEST:<item1>:<need1>:<item2>:<need2>:...
                                String body = read.substring("STORAGE_QUERY_REQUEST:".length());
                                if (body == null || body.isEmpty()) continue;
                                String[] parts = body.split(":");
                                String server = host.getCurrentServer();
                                StringBuilder resp = new StringBuilder();
                                resp.append("STORAGE_QUERY_RESPONSE:");
                                boolean first = true;
                                int i = 0;
                                while (i < parts.length) {
                                    String itemId = null;
                                    if (parts[i].equals("minecraft") && i + 2 < parts.length) {
                                        itemId = "minecraft:" + parts[i + 1];
                                        i += 3;
                                    } else if (i + 1 < parts.length) {
                                        itemId = parts[i];
                                        i += 2;
                                    } else {
                                        i++;
                                    }
                                    if (itemId == null) continue;
                                    try {
                                        String norm = com.rdh.swarm.StorageIndex.normalizeId(itemId);
                                        long total = host.getStorageIndex().getTotal(server, norm);
                                        String breakdown = host.getStorageIndex().getBreakdown(server, norm);
                                        if (!first) resp.append("#");
                                        first = false;
                                        // id:total:x,y,z=count;... — the trailing field names the
                                        // containers the total came from, so the planner's answer is
                                        // attributable to a specific chest instead of a bare number.
                                        resp.append(itemId).append(":").append(total).append(":").append(breakdown);
                                    } catch (Exception ignored) {}
                                }
                                // Visibility for the storage-query path: what was asked,
                                // which server key was used and what came back.
                                System.out.println(String.format(
                                    "[STORAGE QUERY] server=%s req=%s -> %s",
                                    server, body, resp.toString()));
                                try { if (out != null) { out.writeUTF(resp.toString()); out.flush(); } } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                        } else if (read.startsWith("WITHDRAW_REQUEST:")) {
                            try {
                                // FROM WORKER: WITHDRAW_REQUEST:<itemId>|<needed>|<category>
                                // '|' separates fields because the id is namespace-qualified:
                                // the old "id:needed" form split on ':' truncated
                                // "minecraft:raw_iron" to "minecraft", so the host looked up
                                // minecraft:minecraft, found no chest and dispatched nothing.
                                String body = read.substring("WITHDRAW_REQUEST:".length());
                                String[] f = body.split("\\|", 3);
                                String itemId = null;
                                int needed = 0;
                                String category = "all";
                                if (f.length >= 2) {
                                    itemId = f[0].trim();
                                    try { needed = Integer.parseInt(f[1].trim()); } catch (Exception ignored) {}
                                    if (f.length > 2 && !f[2].trim().isEmpty()) category = f[2].trim();
                                } else {
                                    // legacy colon form; the id itself may contain ':'
                                    String[] c = body.split(":");
                                    int numIdx = c.length - 1;
                                    for (int k = c.length - 1; k >= 1; k--) {
                                        try { Integer.parseInt(c[k].trim()); numIdx = k; break; } catch (Exception ignored) {}
                                    }
                                    itemId = String.join(":", java.util.Arrays.copyOfRange(c, 0, numIdx)).trim();
                                    try { needed = Integer.parseInt(c[numIdx].trim()); } catch (Exception ignored) {}
                                    if (numIdx + 1 < c.length) category = c[numIdx + 1].trim();
                                }
                                // Normalize simple ids (e.g. "iron_ingot" -> "minecraft:iron_ingot")
                                if (itemId != null && !itemId.isEmpty() && !itemId.contains(":")) {
                                    itemId = "minecraft:" + itemId;
                                }
                                if (itemId != null && !itemId.isEmpty() && host != null) {
                                    String server = host.getCurrentServer();
                                    System.out.println(String.format("[WITHDRAW] req item=%s need=%d category=%s index_total=%d",
                                        itemId, needed, category, host.getStorageIndex().getTotal(server, itemId)));

                                    List<StorageIndex.ChestRecord> chests = host.getStorageIndex().getChests(server, category);
                                    long now = System.currentTimeMillis();
                                    final long RESCAN_THRESHOLD = 5 * 60 * 1000; // 5 minutes
                                    boolean requestedRescan = false;
                                    int remaining = needed;
                                    for (StorageIndex.ChestRecord r : chests) {
                                        if (remaining <= 0) break;
                                        try {
                                            System.out.println(String.format(
                                                "[WITHDRAW DEBUG] chest (%d,%d,%d) has %d of %s (need %d)",
                                                r.x, r.y, r.z,
                                                r.counts.getOrDefault(itemId, 0L),
                                                itemId,
                                                remaining
                                            ));
                                        } catch (Exception ignored) {}
                                        long available = r.counts.getOrDefault(itemId, 0L);
                                        if (available <= 0) {
                                            // stale or zero — request rescan if old
                                            if (r.lastScan <= 0 || now - r.lastScan > RESCAN_THRESHOLD) {
                                                requestedRescan = true;
                                            }
                                            continue;
                                        }
                                        int take = (int)Math.min(available, remaining);
                                        try {
                                            System.out.println(String.format(
                                                "[WITHDRAW SEND] -> (%d,%d,%d) taking %d of %s",
                                                r.x, r.y, r.z,
                                                take,
                                                itemId
                                            ));
                                        } catch (Exception ignored) {}
                                        // send withdraw command to this worker (use this.connection's out)
                                        try {
                                            if (out != null) {
                                                String cmd = String.format("withdraw %s %d %d %d %s %d", category, r.x, r.y, r.z, itemId, take);
                                                out.writeUTF(cmd);
                                                out.flush();
                                            }
                                        } catch (Exception ignored) {}
                                        remaining -= take;
                                    }
                                    if (needed > remaining) {
                                        System.out.println(String.format("[WITHDRAW] dispatched %d/%d %s from index chests",
                                            needed - remaining, needed, itemId));
                                    } else {
                                        System.out.println(String.format("[WITHDRAW] no index chest holds %s (considered=%d) — nothing dispatched",
                                            itemId, chests.size()));
                                    }
                                    if (requestedRescan) {
                                        try { host.sendAll("swarm:all scan " + category); } catch (Exception ignored) {}
                                        try { if (out != null) { out.writeUTF("RESCAN_REQUESTED:" + category); out.flush(); } } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (IOException e) {
                    ChatUtils.infoPrefix("Swarm", "Connection lost with %s.", getIp(socket.getInetAddress().getHostAddress()));
                    disconnect();
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }

            try { out.close(); } catch (IOException ignored) {}
            try { in.close(); } catch (IOException ignored) {}
        } catch (IOException e) {
            ChatUtils.infoPrefix("Swarm", "Error creating a connection with %s on port %s.", getIp(socket.getInetAddress().getHostAddress()), socket.getPort());
            e.printStackTrace();
            disconnect();
        }
    }

    private void parseBaritoneSettings(String data) {
        // Baritone settings push removed - no-op
    }

    private void parseModuleStates(String data) {
        // Module state sync removed - no-op
    }

    private void parseModuleSettings(String data) {
        // Module settings sync removed - no-op
    }

    private void parseSettings(String payload) {
        try {
            // Payload form: SETTINGS:workerName:entry|entry|...
            String[] parts = payload.split(":", 3);
            if (parts.length < 3) return;
            String worker = parts[1];
            String body = parts[2];
            WorkerConfig cfg = host != null ? host.getOrCreateConfig(worker) : null;
            if (cfg == null) return;

            String[] entries = body.split("\\|");
            int bcount = 0, mcount = 0, scount = 0;
            for (String e : entries) {
                if (e == null || e.isEmpty()) continue;
                if (e.startsWith("B:")) {
                    String kv = e.substring(2);
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        String k = kv.substring(0, eq);
                        String v = kv.substring(eq + 1);
                        cfg.setBaritone(k, v);
                        bcount++;
                    }
                } else if (e.startsWith("M:")) {
                    String kv = e.substring(2);
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        String k = kv.substring(0, eq);
                        String v = kv.substring(eq + 1);
                        boolean on = v.equals("1") || v.equalsIgnoreCase("true");
                        cfg.setModuleState(k, on);
                        mcount++;
                    }
                } else if (e.startsWith("S:")) {
                    String kv = e.substring(2);
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        String k = kv.substring(0, eq);
                        String v = kv.substring(eq + 1);
                        cfg.setModuleSetting(k, v);
                        scount++;
                    }
                }
            }

            ChatUtils.infoPrefix("Swarm", "Parsed SETTINGS from %s (B:%d M:%d S:%d)", worker, bcount, mcount, scount);
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Failed to parse SETTINGS payload: %s", payload);
        }
    }

    public void disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ChatUtils.infoPrefix("Swarm", "Worker disconnected on ip: %s.", socket.getInetAddress().getHostAddress());
        try { if (host != null && host.getWebServer() != null) host.getWebServer().logWarn("⚡ " + (workerName == null ? getConnection() : workerName) + " disconnected"); } catch (Exception ignored) {}

        interrupt();
    }

    public String getConnection() {
        return getIp(socket.getInetAddress().getHostAddress()) + ":" + socket.getPort();
    }

    private String getIp(String ip) {
        return ip.equals("127.0.0.1") ? "localhost" : ip;
    }
}
