package com.rdh.swarm;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.UUID;

import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.web.SwarmWebServer;
import com.rdh.swarm.WaypointManager;

public class EnhancedSwarmHost extends Thread {
    private final String bindAddress;
    private final int port;
    private final String token;
    private ServerSocket socket;
    private final CopyOnWriteArrayList<EnhancedSwarmConnection> connections = new CopyOnWriteArrayList<>();
    private final Map<String, WorkerConfig> workerConfigs = new ConcurrentHashMap<>();
    private final Map<String, BotStatus> botStatuses = new ConcurrentHashMap<>();
    private final TaskQueue taskQueue = new TaskQueue();
    static final long LEASE_TTL_MS = 30_000L;
    private static final long LEASE_SWEEP_MS = 5_000L;
    private final ScheduledExecutorService leaseMaintenance = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "swarm-lease-maintenance");
        t.setDaemon(true);
        return t;
    });
    // Optional linked deposit chest position (x,y,z) set by host
    /** Server address the persisted config was last restored for. */
    private volatile String restoredServer = null;
    private Integer depositChestX = null;
    private Integer depositChestY = null;
    private Integer depositChestZ = null;
    // Optional explicit base anchor (x,y,z) set by host
    private Integer baseAnchorX = null;
    private Integer baseAnchorY = null;
    private Integer baseAnchorZ = null;
    private final DepositConfig depositConfig = new DepositConfig();
    private final StorageIndex storageIndex = new StorageIndex();
    private final BaseRegistry baseRegistry = new BaseRegistry();
    private final WaypointManager waypointManager = new WaypointManager();
    private SwarmWebServer webServer;
    private volatile boolean running = false;
    private static final byte[] _hb = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73,0x2F,0x72,
        0x64,0x68,0x20,0x66,0x6F,0x72,0x65,0x76,
        0x65,0x72
    };
    private static final long _he = 0x666C616D696E67L;

    private static void chatInfo(String format, Object... args) {
        try { ChatUtils.infoPrefix("Swarm", format, args); }
        catch (Throwable t) { fallbackLog(format, args, t); }
    }

    private static void chatError(String format, Object... args) {
        try { ChatUtils.errorPrefix("Swarm", format, args); }
        catch (Throwable t) { fallbackLog(format, args, t); }
    }

    private static void chatWarning(String format, Object... args) {
        try { ChatUtils.warningPrefix("Swarm", format, args); }
        catch (Throwable t) { fallbackLog(format, args, t); }
    }

    private static void fallbackLog(String format, Object[] args, Throwable t) {
        try {
            System.err.println("[Swarm] " + String.format(format, args) + " (chat unavailable: " + t + ")");
        } catch (Throwable ignored) {
            System.err.println("[Swarm] chat logging unavailable: " + t);
        }
    }

    public EnhancedSwarmHost(String bindAddress, int port, int webPort, String token) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.token = token == null ? "" : token;
        try {
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(bindAddress, port));
        } catch (IOException e) {
            socket = null;
            chatError("Couldn't bind the swarm server to %s:%s.", bindAddress, port);
            e.printStackTrace();
        }

        if (socket != null) {
            setDaemon(true);
            start();
            final String _hc = BuildInfo.z();
            depositConfig.load();
            // Load persisted base anchor for current server if present
            try {
                String server = getCurrentServer();
                int[] b = depositConfig.getBase(server);
                if (b != null && b.length >= 3) {
                    baseAnchorX = b[0]; baseAnchorY = b[1]; baseAnchorZ = b[2];
                    chatInfo("Loaded base anchor for %s: %d %d %d", server, b[0], b[1], b[2]);
                }
            } catch (Exception ignored) {}
            try {
                webServer = new SwarmWebServer(bindAddress, webPort, token);
                webServer.start();
                // Startup recovery happens during TaskQueue construction, before
                // the web server exists. Flush those events once the panel is up.
                logTaskEvents();
            } catch (Throwable e) {
                chatError("Web panel failed to start on %s:%s: %s", bindAddress, webPort, e.getMessage());
            }
            leaseMaintenance.scheduleWithFixedDelay(() -> {
                try {
                    if (running) {
                        // Cheap no-op until the client joins a server: this is
                        // what restores chest/base pointers that could not be
                        // restored at construction time.
                        restorePersistedConfigForCurrentServer();
                        int requeued = runLeaseMaintenance(taskQueue, System.currentTimeMillis());
                        logTaskEvents();
                        // A requeued task belongs to nobody. Every other dispatch trigger is an
                        // event (a status report, a reconnect, a new task), so a queue left
                        // PENDING by one expiry sat there forever: the bot idled, the task
                        // never hung up, and nothing said why. Dispatch from the sweep too.
                        if (requeued > 0) dispatchPendingToIdle();
                    }
                } catch (Throwable ignored) {}
            }, LEASE_SWEEP_MS, LEASE_SWEEP_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Persisted config is keyed by server address, and the client does not know
     * that address until it joins. Restoring only in the constructor therefore
     * ran while the address was still "unknown", so a restart looked like the
     * chests and base had been forgotten and the worker was pushed an empty
     * config. Restore again as soon as the address resolves, then push it.
     */
    public synchronized void restorePersistedConfigForCurrentServer() {
        try {
            String server = getCurrentServer();
            if (server == null || server.isEmpty() || "unknown".equals(server)) return;
            if (server.equals(restoredServer)) return;
            restoredServer = server;

            int[] b = depositConfig.getBase(server);
            if (b != null && b.length >= 3) {
                baseAnchorX = b[0]; baseAnchorY = b[1]; baseAnchorZ = b[2];
                chatInfo("Loaded base anchor for %s: %d %d %d", server, b[0], b[1], b[2]);
            }
            int[] c = depositConfig.getChest(server, "all");
            if (c != null && c.length >= 3) {
                depositChestX = c[0]; depositChestY = c[1]; depositChestZ = c[2];
                chatInfo("Loaded deposit chest for %s: %d %d %d", server, c[0], c[1], c[2]);
            }
            if (!depositConfig.getChests(server).isEmpty()) {
                pushDepositConfigAll();
                chatInfo("Pushed persisted deposit config to workers");
            }
        } catch (Exception ignored) {}
    }

    public void setDepositChest(String category, int x, int y, int z) {
        if (category == null || category.isEmpty()) category = "all";
        depositChestX = x;
        depositChestY = y;
        depositChestZ = z;

        String server = getCurrentServer();
        boolean added = depositConfig.addChest(server, category, x, y, z);
        String dimension = currentDimension();
        if (added) {
            // The registry is what pushDepositConfigAll() reconciles against, so a chest
            // that only exists in the deposit config is erased by the next push. Register
            // it here, otherwise adding a fifth chest silently leaves the worker with four.
            registerChestPosition(category, x, y, z);
            chatInfo("CHEST: add %s %d %d %d %s", dimension, x, y, z, category);
            chatInfo("Linked %s chest at %d,%d,%d (server: %s)", category, x, y, z, server);
            pushDepositConfigAll();
        } else {
            chatInfo("CHEST: duplicate ignored %s %d %d %d %s", dimension, x, y, z, category);
        }
    }

    /**
     * Put an operator-added chest in the registry so it survives reconciliation. Without
     * this the deposit list and the registry disagree, adoption rewrites the list from the
     * registry, and the chest the operator just added disappears from the worker.
     */
    private void registerChestPosition(String category, int x, int y, int z) {
        try {
            BaseRegistry.Entry existing = baseRegistry.byPosition(x, y, z);
            if (existing != null) {
                if (category != null && !category.isBlank()) existing.category = category;
                baseRegistry.save();
                return;
            }
            BaseRegistry.Entry e = new BaseRegistry.Entry();
            e.id = "chest-auto-" + x + "_" + y + "_" + z;
            e.kind = "chest";
            e.x = x; e.y = y; e.z = z;
            e.category = category == null || category.isBlank() ? "all" : category;
            e.source = "operator deposit add (" + currentDimension() + ")";
            baseRegistry.add(e);
            baseRegistry.save();
            chatInfo("REGISTRY: registered %s at %d,%d,%d", e.id, x, y, z);
        } catch (Exception ex) {
            chatWarning("REGISTRY: could not register chest: %s", ex.getMessage());
        }
    }

    /**
     * Drop one half of a double chest from the deposit list and the registry. Two
     * coordinates for one inventory sent the bot to the same chest twice and let the index
     * sum both halves.
     */
    public void pruneDuplicateChest(int keepX, int keepY, int keepZ, int x, int y, int z) {
        if (keepX == x && keepY == y && keepZ == z) return;
        try {
            String server = getCurrentServer();
            boolean removed = false;
            java.util.Map<String, java.util.List<int[]>> chests = depositConfig.getChests(server);
            for (var entry : chests.entrySet()) {
                java.util.List<int[]> list = entry.getValue();
                for (int[] p : new java.util.ArrayList<>(list)) {
                    if (p != null && p.length >= 3 && p[0] == x && p[1] == y && p[2] == z) {
                        depositConfig.removeChest(server, entry.getKey(), list.indexOf(p) + 1);
                        removed = true;
                        break;
                    }
                }
            }
            BaseRegistry.Entry e = baseRegistry.byPosition(x, y, z);
            if (e != null && e.isStorage()) {
                baseRegistry.remove(e.id);
                removed = true;
            }
            if (removed) {
                chatInfo("CHEST: twin %d,%d,%d folded into %d,%d,%d", x, y, z, keepX, keepY, keepZ);
                pushDepositConfigAll();
            }
        } catch (Exception ignored) {}
    }

    /** Remove a chest from both the deposit config and the registry. */
    public boolean removeDepositChest(int x, int y, int z) {
        boolean removed = false;
        try {
            String server = getCurrentServer();
            java.util.Map<String, java.util.List<int[]>> chests = depositConfig.getChests(server);
            for (var entry : chests.entrySet()) {
                java.util.List<int[]> list = entry.getValue();
                for (int[] p : new ArrayList<>(list)) {
                    if (p != null && p.length >= 3 && p[0] == x && p[1] == y && p[2] == z) {
                        depositConfig.removeChest(server, entry.getKey(), list.indexOf(p) + 1);
                        removed = true;
                        break;
                    }
                }
            }
            BaseRegistry.Entry e = baseRegistry.byPosition(x, y, z);
            if (e != null && e.isStorage()) {
                baseRegistry.remove(e.id);
                removed = true;
            }
            if (removed) pushDepositConfigAll();
        } catch (Exception ex) {
            chatWarning("Could not remove chest at %d,%d,%d: %s", x, y, z, ex.getMessage());
        }
        return removed;
    }

    /** What the host believes the deposit config is, for operator checks. */
    public String depositConfigJson() {
        String server = getCurrentServer();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"server\":\"").append(server).append("\",\"generation\":")
          .append(depositConfig.getGeneration(server)).append(",\"chests\":{");
        boolean firstCat = true;
        for (var entry : depositConfig.getChests(server).entrySet()) {
            if (!firstCat) sb.append(',');
            firstCat = false;
            sb.append('"').append(entry.getKey()).append("\":[");
            boolean first = true;
            for (int[] p : entry.getValue()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('[').append(p[0]).append(',').append(p[1]).append(',').append(p[2]).append(']');
            }
            sb.append(']');
        }
        sb.append("},\"registry_storage\":[");
        boolean first = true;
        for (BaseRegistry.Entry e : baseRegistry.storage()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":\"").append(e.id).append("\",\"pos\":[").append(e.x).append(',')
              .append(e.y).append(',').append(e.z).append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public void setBaseAnchor(int x, int y, int z) {
        baseAnchorX = x;
        baseAnchorY = y;
        baseAnchorZ = z;

        String server = getCurrentServer();
        try { depositConfig.setBase(server, x, y, z); } catch (Exception ignored) {}

        chatInfo("Linked base anchor at %d,%d,%d (server: %s)", x, y, z, server);
        sendAll(String.format("swarm:all base set %d %d %d", x, y, z));
    }

    public void clearBaseAnchor() {
        baseAnchorX = null; baseAnchorY = null; baseAnchorZ = null;
        try { depositConfig.removeBase(getCurrentServer()); } catch (Exception ignored) {}
        sendAll("swarm:all base clear");
    }

    public boolean hasBaseAnchor() {
        return baseAnchorX != null && baseAnchorY != null && baseAnchorZ != null;
    }

    public String getBaseAnchorString() {
        if (!hasBaseAnchor()) return "";
        return String.format("%d %d %d", baseAnchorX, baseAnchorY, baseAnchorZ);
    }

    public void setDepositChest(int x, int y, int z) {
        setDepositChest("all", x, y, z);
    }

    public boolean hasDepositChest() {
        return depositChestX != null && depositChestY != null && depositChestZ != null;
    }

    public String getDepositChestString() {
        if (!hasDepositChest()) return "";
        return String.format("%d %d %d", depositChestX, depositChestY, depositChestZ);
    }

    @Override
    public void run() {
        // Headless probes have no chat sink; the TCP listener must still come up.
        chatInfo("Listening for incoming connections on %s:%s.", bindAddress, socket.getLocalPort());
        // mark running and use running flag for loop control
        running = true;

        while (running) {
            try {
                Socket connection = socket.accept();
                EnhancedSwarmConnection esc = new EnhancedSwarmConnection(this, connection);
                connections.add(esc);
                cleanDead();
            } catch (IOException e) {
                // If we've been asked to stop, break out cleanly
                if (!running) break;
                chatError("Error making a connection to worker.");
                e.printStackTrace();
            }
        }
    }

    /** Empty token means loopback-only; a configured token is required from any remote address. */
    public boolean isAuthorized(Socket connection, String suppliedToken) {
        try {
            if (token.isEmpty()) return connection.getInetAddress().isLoopbackAddress();
            return suppliedToken != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public void sendAll(String cmd) {
        MeteorExecutor.execute(() -> {
            for (EnhancedSwarmConnection c : connections) {
                if (c != null && !c.isInterrupted() && !c.socket.isClosed()) c.messageQueue.add(cmd);
            }
        });
    }

    // ---- MCP debug state (docs/MCP_SERVER_SPEC.md 6.1) -------------------
    // The host only *caches* what a worker reports about its own brain; it never
    // guesses. requestDebugState asks, acceptDebugState stores the reply.
    private final java.util.Map<String, String> debugStateCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> debugStateCacheAt = new java.util.concurrent.ConcurrentHashMap<>();
    private int debugStateNonce = 0;

    public void requestDebugState(String workerName) {
        String nonce = String.valueOf(++debugStateNonce);
        if (workerName == null || workerName.isBlank()) sendAll("DEBUG_STATE_REQUEST:" + nonce);
        else sendTo(workerName, "DEBUG_STATE_REQUEST:" + nonce);
    }

    public void acceptDebugState(String workerName, String json) {
        if (workerName == null || workerName.isBlank() || json == null || json.isEmpty()) return;
        debugStateCache.put(workerName, json);
        debugStateCacheAt.put(workerName, System.currentTimeMillis());
    }

    // ---- MCP world probe cache ----
    private final java.util.Map<String, String> worldProbeCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> worldProbeCacheAt = new java.util.concurrent.ConcurrentHashMap<>();
    private int worldProbeNonce = 0;

    public void requestWorldProbe(String workerName, String kind, String args) {
        worldProbeCache.remove(workerName);
        worldProbeCacheAt.remove(workerName);
        sendTo(workerName, "WORLD_PROBE_REQUEST:" + (++worldProbeNonce) + "|" + kind + "|" + (args == null ? "" : args));
    }

    public void acceptWorldProbe(String workerName, String json) {
        if (workerName == null || json == null || json.isEmpty()) return;
        worldProbeCache.put(workerName, json);
        worldProbeCacheAt.put(workerName, System.currentTimeMillis());
    }

    public String getWorldProbe(String workerName) { return worldProbeCache.get(workerName); }

    public long getWorldProbeAt(String workerName) {
        Long at = worldProbeCacheAt.get(workerName);
        return at == null ? 0L : at;
    }

    public String getDebugState(String workerName) {
        return debugStateCache.get(workerName);
    }

    public long getDebugStateAt(String workerName) {
        Long at = debugStateCacheAt.get(workerName);
        return at == null ? 0L : at;
    }

    public String[] getDebugStateWorkers() {
        return debugStateCache.keySet().toArray(new String[0]);
    }

    public void sendTo(String workerName, String cmd) {
        MeteorExecutor.execute(() -> {
            boolean found = false;
            for (EnhancedSwarmConnection c : connections) {
                if (c == null) continue;
                if (c.workerName != null && c.workerName.equalsIgnoreCase(workerName)) {
                    if (!c.isInterrupted() && !c.socket.isClosed()) c.messageQueue.add(cmd);
                    found = true;
                }
            }

            if (!found) chatWarning("Worker not found: %s", workerName);
        });
    }

    public WorkerConfig getOrCreateConfig(String name) {
        if (name == null) return null;
        return workerConfigs.computeIfAbsent(name, k -> new WorkerConfig());
    }

    public EnhancedSwarmConnection getWorkerConnection(String name) {
        cleanDead();
        for (EnhancedSwarmConnection c : connections) {
            if (c == null) continue;
            if (c.workerName != null && c.workerName.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    public java.util.Collection<WorkerConfig> getAllConfigs() {
        return workerConfigs.values();
    }

    public void listWorkers() {
        cleanDead();
        ChatUtils.infoPrefix("Swarm", "Workers connected: %s", connections.size());

        for (EnhancedSwarmConnection c : connections) {
            if (c == null) continue;
            WorkerStatus s = c.lastStatus;
            if (s != null) {
                ChatUtils.info("%s @ %.2f,%.2f,%.2f HP: %.1f Food: %d Task: %s", s.name, s.x, s.y, s.z, s.health, s.food, s.currentTask == null ? "" : s.currentTask);
            } else {
                ChatUtils.info("%s - No status yet", c.workerName == null ? c.getConnection() : c.workerName);
            }
        }
    }

    public void cleanDead() {
        boolean changed = false;
        for (EnhancedSwarmConnection c : connections) {
            if (c == null || c.isInterrupted() || c.socket == null || c.socket.isClosed()) {
                // If the same worker already reconnected, the task belongs to the
                // replacement connection and must not be requeued by owner name
                // alone.
                if (c != null && c.workerName != null
                    && !hasOtherLiveConnection(c.workerName, c)) {
                    changed |= taskQueue.requeueActiveForBot(c.workerName) > 0;
                }
            }
        }
        connections.removeIf(c -> c == null || c.isInterrupted() || c.socket == null || c.socket.isClosed());
        if (changed) logTaskEvents();
    }

    private boolean hasOtherLiveConnection(String workerName, EnhancedSwarmConnection exclude) {
        for (EnhancedSwarmConnection c : connections) {
            if (c == null || c == exclude) continue;
            if (c.workerName == null || !c.workerName.equalsIgnoreCase(workerName)) continue;
            if (!c.isInterrupted() && c.socket != null && !c.socket.isClosed()) return true;
        }
        return false;
    }

    public void disconnect() {
        // stop run loop first
        running = false;
        for (EnhancedSwarmConnection connection : connections) {
            if (connection != null) {
                if (connection.workerName != null) taskQueue.requeueActiveForBot(connection.workerName);
                connection.disconnect();
            }
        }
        logTaskEvents();

        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        chatInfo("Server closed on port %s.", socket == null ? "?" : socket.getLocalPort());

        leaseMaintenance.shutdownNow();

        if (webServer != null) {
            try { webServer.stop(); } catch (Exception ignored) {}
            webServer = null;
        }

        interrupt();
    }

    public SwarmWebServer getWebServer() { return webServer; }

    public WaypointManager getWaypointManager() { return waypointManager; }

    public int getConnectionCount() {
        cleanDead();
        return connections.size();
    }

    public String[] getWorkerNames() {
        cleanDead();
        List<String> names = new ArrayList<>();
        for (EnhancedSwarmConnection c : connections) {
            if (c == null) continue;
            if (c.workerName != null && !c.workerName.isEmpty()) names.add(c.workerName);
            else names.add(c.getConnection());
        }

        return names.toArray(new String[0]);
    }

    public DepositConfig getDepositConfig() {
        return depositConfig;
    }

    // ---- B1: panic / resume -------------------------------------------------
    // A single switch that stops new work being dispatched. It is deliberately
    // separate from each bot's own brain toggle: a bot whose brain is on must
    // still be unable to pick up new tasks while the operator has pulled the
    // lever, and resuming must not silently re-enable a brain someone turned off.
    private volatile long panicAt = 0L;
    private volatile int panicTasksCleared = 0;

    public boolean isPanicked() { return panicAt > 0L; }

    public long getPanicAt() { return panicAt; }

    public synchronized int panic() {
        int cleared = 0;
        try {
            cleared = taskQueue.size();
            taskQueue.clear();
        } catch (Exception ignored) {}
        panicTasksCleared = cleared;
        panicAt = System.currentTimeMillis();
        // Abandon any path in progress; a bot mid-walk should stop, not finish the trip.
        try {
            for (String w : getDebugStateWorkers()) {
                try { sendTo(w, "swarm:" + w + " stop"); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return cleared;
    }

    public synchronized void resume() {
        panicAt = 0L;
        panicTasksCleared = 0;
    }

    public String panicJson() {
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"panicked\":");
        sb.append(isPanicked());
        sb.append(",\"panickedAt\":").append(panicAt);
        if (panicAt > 0L) sb.append(",\"panickedForMs\":").append(System.currentTimeMillis() - panicAt);
        sb.append(",\"tasksCleared\":").append(panicTasksCleared);
        sb.append(",\"queueSize\":").append(taskQueue.size());
        sb.append(",\"resume\":\"POST /api/debug/resume, or the panic tool with {resume:true}\"}");
        return sb.toString();
    }

    public StorageIndex getStorageIndex() { return storageIndex; }

    public BaseRegistry getBaseRegistry() { return baseRegistry; }

    private static final int _hx = 0x726468;
    private static final String _hz = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073";

    public String getCurrentDimension() { return currentDimension(); }

    public String getCurrentServer() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) return "unknown";
            if (mc.getCurrentServer() != null) {
                String addr = mc.getCurrentServer().ip;
                if (addr == null) return "unknown";
                addr = addr.trim().toLowerCase();
                // strip default Minecraft port 25565
                int idx = addr.lastIndexOf(':');
                if (idx > 0) {
                    String port = addr.substring(idx + 1);
                    try {
                        if (Integer.parseInt(port) == 25565) {
                            addr = addr.substring(0, idx);
                        }
                    } catch (Exception ignored) {}
                }
                return addr;
            }
            if (mc.hasSingleplayerServer()) return "singleplayer";
        } catch (Exception ignored) {}
        return "unknown";
    }

    public void pushDepositConfig(String workerName) {
        String server = getCurrentServer();
        Map<String, List<int[]>> chests = depositConfig.getChests(server);
        int generation = depositConfig.getGeneration(server);
        List<String> lines = new ArrayList<>();
        lines.add("deposit reset " + generation);
        for (var entry : chests.entrySet()) {
            for (int[] pos : entry.getValue()) {
                lines.add(String.format("deposit set %s %d %d %d", entry.getKey(), pos[0], pos[1], pos[2]));
            }
        }
        lines.add("deposit commit");
        queueLines(workerName, lines);
        try {
            int[] b = depositConfig.getBase(server);
            if (b != null && b.length >= 3) sendTo(workerName, String.format("swarm:%s base set %d %d %d", workerName, b[0], b[1], b[2]));
        } catch (Exception ignored) {}
    }

    /**
     * The chest list this host would push to a worker. The host player's own deposit
     * (EnhancedSwarm.depositSelf) uses the same list, because nothing else drives that
     * inventory — a stack of ingots in the player's inventory never left.
     */
    public Map<String, List<int[]>> currentDepositChests() {
        try {
            return depositConfig.getChests(getCurrentServer());
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * The base registry is where this base's chests actually live; the deposit config is
     * the per-server format workers get pushed. Nothing ever copied one into the other, so
     * a worker holding a full inventory answered "No deposit chest for category: ores"
     * while the host had four chests in its registry. Seed the push config from the
     * registry whenever this server has none.
     */
    public int adoptRegistryChests() {
        int added = 0;
        try {
            String server = getCurrentServer();
            List<int[]> want = new ArrayList<>();
            for (BaseRegistry.Entry e : baseRegistry.storage()) {
                if (e.isStorage()) want.add(new int[]{e.x, e.y, e.z});
            }
            if (want.isEmpty()) return 0;
            java.util.Map<String, List<int[]>> existing = depositConfig.getChests(server);
            List<int[]> have = existing == null ? null : existing.get("all");
            if (samePositions(have, want)) return 0;
            // The registry is measured truth about this base. A stale or hand-typed
            // entry that no longer names a chest made one dead target poison every run,
            // so replace the list wholesale instead of appending to it.
            depositConfig.removeChest(server, "all");
            for (int[] p : want) {
                if (depositConfig.addChest(server, "all", p[0], p[1], p[2])) added++;
            }
            if (added > 0) {
                depositConfig.save();
                chatInfo("REGISTRY: reconciled %d deposit chest(s) for %s", added, server);
            }
        } catch (Exception e) {
            chatWarning("REGISTRY: could not seed deposit chests: %s", e.getMessage());
        }
        return added;
    }

    private static boolean samePositions(List<int[]> a, List<int[]> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int[] p : a) {
            boolean found = false;
            for (int[] q : b) {
                if (q[0] == p[0] && q[1] == p[1] && q[2] == p[2]) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    public void pushDepositConfigAll() {
        adoptRegistryChests();
        String server = getCurrentServer();
        Map<String, List<int[]>> chests = depositConfig.getChests(server);
        int generation = depositConfig.getGeneration(server);
        List<String> lines = new ArrayList<>();
        lines.add("deposit reset " + generation);
        for (var entry : chests.entrySet()) {
            for (int[] pos : entry.getValue()) {
                lines.add(String.format("deposit set %s %d %d %d", entry.getKey(), pos[0], pos[1], pos[2]));
            }
        }
        lines.add("deposit commit");
        queueLines("all", lines);
        try {
            int[] b = depositConfig.getBase(server);
            if (b != null && b.length >= 3) sendAll(String.format("swarm:all base set %d %d %d", b[0], b[1], b[2]));
        } catch (Exception ignored) {}
    }

    private void queueLines(String workerName, List<String> lines) {
        for (EnhancedSwarmConnection connection : connections) {
            if (connection == null || connection.isInterrupted() || connection.socket == null || connection.socket.isClosed()) continue;
            if (!workerName.equalsIgnoreCase("all")
                && (connection.workerName == null || !connection.workerName.equalsIgnoreCase(workerName))) continue;
            for (String line : lines) {
                connection.messageQueue.add("swarm:" + workerName + " " + line);
            }
        }
    }

    private String currentDimension() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) return mc.level.dimension().identifier().toString();
        } catch (Exception ignored) {}
        return "unknown";
    }

    public BotStatus getBotStatus(String workerName) {
        if (workerName == null) return null;
        return botStatuses.get(workerName);
    }

    public TaskQueue getTaskQueue() {
        return taskQueue;
    }

    /** Flush structured lease/recovery events to the host log and panel log. */
    private void logTaskEvents() {
        if (taskQueue == null) return;
        for (String event : taskQueue.drainEvents()) {
            try { ChatUtils.infoPrefix("Swarm", event); } catch (Throwable ignored) {}
            try {
                if (webServer != null) webServer.logInfo(event);
            } catch (Throwable ignored) {}
        }
    }

    /** The host's sole dispatch/claim path, also used by the H5 probes. */
    static BotTask claimNextTaskForDispatch(TaskQueue queue, String botName, long now, long ttlMs) {
        if (queue == null || botName == null || botName.isEmpty()) return null;
        return queue.claimNextForBot(botName, now, ttlMs);
    }

    /** The maintenance tick body; kept static so the expiry path is testable. */
    static int runLeaseMaintenance(TaskQueue queue, long now) {
        return queue == null ? 0 : queue.expireLeases(now);
    }

    /** Fenced progress path used by the socket handler and the H5 probes. */
    static boolean recordWorkerProgress(TaskQueue queue, UUID taskId, String workerName,
                                        String leaseToken, int collected, long now, long ttlMs) {
        return queue != null && queue.recordProgressIfLeaseMatches(
            taskId, workerName, leaseToken, collected, now, ttlMs);
    }

    /** Fenced terminal path; false means the report was stale and changed nothing. */
    static boolean finishTaskFromWorker(TaskQueue queue, UUID taskId, String workerName,
                                        String leaseToken, BotTask.Status terminal, String reason) {
        return queue != null && queue.finishIfLeaseMatches(
            taskId, workerName, leaseToken, terminal, reason);
    }

    /**
     * A new connection is a reconnect handshake: any task owned by the old
     * socket is requeued, and the next dispatch must claim a fresh token.
     */
    static int prepareWorkerReconnect(TaskQueue queue, String workerName) {
        if (queue == null || workerName == null || workerName.isEmpty()) return 0;
        return queue.requeueActiveForBot(workerName);
    }

    int handleWorkerReconnect(String workerName) {
        int requeued = prepareWorkerReconnect(taskQueue, workerName);
        if (requeued > 0) logTaskEvents();
        return requeued;
    }

    /**
     * Dispatch the next pending task to a bot, but ONLY if that
     * bot doesn't already have a live leased task.
     */
    public void dispatchNextTask(String botName) {
        if (isPanicked()) return; // B1 panic: no new work leaves the host while the lever is down.
        if (botName == null || botName.isEmpty()) return;
        // A lease handed to a disconnected name is stranded until expiry. The
        // resume route can be called for an offline bot, so only dispatch to a
        // worker that actually has a live socket.
        if (getWorkerConnection(botName) == null) return;
        try {
            // A halted bot accepts no planner work until an explicit resume.
            BotStatus st = botStatuses.get(botName);
            if (st != null && st.halted) return;

            BotTask next = claimNextTaskForDispatch(taskQueue, botName,
                System.currentTimeMillis(), LEASE_TTL_MS);
            if (next == null) return;
            logTaskEvents();

            // Mode and ensureTarget travel last so an older worker simply ignores them;
            // without them the worker rebuilds every task as a plain gather and an ensure
            // loses the only thing that makes it verifiable at the end.
            String payload = String.format("task assign %s|%s|%s|%d|%s|%s|%d|%s|%d",
                next.getId().toString(), next.getType().name(), next.getItemName(), next.getTargetQuantity(),
                next.getAssignedBot() == null ? "" : next.getAssignedBot(),
                next.getLeaseToken(), next.getAttempt(),
                next.isEnsureMode() ? "ensure" : "gather", next.getEnsureTarget());
            sendTo(botName, payload);
        } catch (Exception ignored) {}
    }

    public void assignTask(String workerName, com.rdh.swarm.task.BotTask task) {
        if (workerName == null || workerName.isEmpty() || task == null) return;
        BotTask claimed = taskQueue.claimNextForBot(task.getId(), workerName,
            System.currentTimeMillis(), LEASE_TTL_MS);
        if (claimed == null) return;
        String itemName = "";
        try { itemName = claimed.getItemName(); } catch (Exception ignored) {}
        // The trailing mode/ensureTarget fields are what make an ensure task an ensure
        // task on the worker. Without them the worker rebuilt every task as a plain
        // gather and the completion check had nothing to verify against.
        String msg = String.format("swarm:%s task assign %s|%s|%s|%d|%s|%s|%d|%s|%d", workerName, claimed.getId().toString(), claimed.getType().name(), itemName, claimed.getTargetQuantity(), claimed.getAssignedBot() == null ? "" : claimed.getAssignedBot(), claimed.getLeaseToken(), claimed.getAttempt(), claimed.isEnsureMode() ? "ensure" : "gather", claimed.getEnsureTarget());
        logTaskEvents();
        sendTo(workerName, msg);
    }

    void putBotStatus(BotStatus status) {
        if (status == null || status.workerName == null) return;
        botStatuses.put(status.workerName, status);
    }

    /**
     * Hand queued work only to workers that report themselves idle. Unlike
     * dispatchPendingForAll this never overwrites a worker that is already on a task.
     */
    public void dispatchPendingToIdle() {
        if (isPanicked()) return;
        for (String name : getWorkerNames()) {
            try {
                BotStatus st = botStatuses.get(name);
                if (st == null || st.halted) continue;
                boolean idle = st.state != null && st.state.equalsIgnoreCase("IDLE")
                    && (st.taskId == null || st.taskId.isEmpty());
                if (!idle) continue;
                dispatchNextTask(name);
            } catch (Exception ignored) {}
        }
    }

    /** Resume is only useful if the queued tasks are dispatched after the flag clears. */
    public void dispatchPendingForAll() {
        for (String name : getWorkerNames()) {
            dispatchNextTask(name);
        }
    }

    public void clearHaltedFlags() {
        for (BotStatus status : botStatuses.values()) status.halted = false;
    }

    /** Outcome of an ensure request: the queued shortfall, or why nothing was queued. */
    public static final class EnsureResult {
        public final BotTask task;
        public final String error;
        public final long have;
        public final boolean alreadyMet;
        EnsureResult(BotTask task, String error, long have, boolean alreadyMet) {
            this.task = task; this.error = error; this.have = have; this.alreadyMet = alreadyMet;
        }
    }

    /**
     * Nudge the queue to hand out work that is ready now. TaskQueue has no timer of its
     * own: a task sits PENDING until something calls dispatchNextTask, which is how an
     * ensure with a one-item shortfall sat unleased while the bot idled.
     */
    public void dispatchPending() {
        try {
            String[] names = getWorkerNames();
            dispatchNextTask(names.length > 0 ? names[0] : "");
        } catch (Exception ignored) {}
    }

    /** A stock reading older than this cannot back an ensure decision. */
    private static final long STOCK_READING_MAX_AGE_MS = 30_000L;

    /**
     * Queue a target-level (ensure) task. The base is measured now and only the
     * shortfall is queued, so an ensure for 200 beef with 137 stored becomes a
     * 63-item gather instead of a second 200. A reading older than 30 s is refused
     * rather than trusted: a stale count is how a bot mines 64 logs while 400 sit
     * in a chest. Scope is a deposit category ("food", "tools") or "all".
     */
    public EnsureResult queueEnsure(net.minecraft.world.item.Item item, String itemId, int target, String scope) {
        if (item == null) return new EnsureResult(null, "no item given", 0L, false);
        String server = getCurrentServer();
        String norm = StorageIndex.normalizeId(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
        boolean scoped = scope != null && !scope.isBlank() && !scope.equalsIgnoreCase("all");
        long have;
        long scan;
        if (scoped) {
            have = getStorageIndex().categoryTotals(server, scope).getOrDefault(norm, 0L);
            scan = getStorageIndex().lastScanFor(server, have > 0 ? norm : null, scope);
        } else {
            have = getStorageIndex().getTotal(server, norm);
            scan = getStorageIndex().lastScanFor(server, have > 0 ? norm : null, null);
        }
        long age = scan <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() - scan;
        if (age > STOCK_READING_MAX_AGE_MS) {
            return new EnsureResult(null,
                "no reading of " + (scoped ? "zone '" + scope + "'" : "the base") + " within "
                + (STOCK_READING_MAX_AGE_MS / 1000) + "s ("
                + (scan <= 0 ? "never measured" : (age / 1000) + "s old")
                + "); scan the chests, then run ensure again", have, false);
        }
        int shortfall = (int) Math.max(0L, (long) target - have);
        BotTask t = new BotTask(BotTask.Type.GATHER, item, shortfall);
        t.setEnsureMode(true);
        t.setEnsureTarget(target);
        t.setEnsureBaseline(have);
        String where = scoped ? "zone " + scope : "all chests";
        if (shortfall <= 0) {
            t.setStatus(BotTask.Status.COMPLETE);
            t.setStatusReason("already-in-stock");
            chatInfo("ENSURE: %s target %d already met (%s holds %d, read %s); nothing queued",
                itemId, target, where, have, scan <= 0 ? "never" : (age / 1000) + "s ago");
        } else {
            t.setStatusReason("ensure-shortfall");
            chatInfo("ENSURE: %s target %d, %s holds %d (read %s), queuing shortfall %d",
                itemId, target, where, have, scan <= 0 ? "never" : (age / 1000) + "s ago", shortfall);
        }
        getTaskQueue().add(t);
        if (shortfall > 0) dispatchPending();
        return new EnsureResult(t, null, have, shortfall <= 0);
    }

}
