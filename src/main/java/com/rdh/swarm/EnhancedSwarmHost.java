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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

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
    private Integer depositChestX = null;
    private Integer depositChestY = null;
    private Integer depositChestZ = null;
    // Optional explicit base anchor (x,y,z) set by host
    private Integer baseAnchorX = null;
    private Integer baseAnchorY = null;
    private Integer baseAnchorZ = null;
    private final DepositConfig depositConfig = new DepositConfig();
    private final StorageIndex storageIndex = new StorageIndex();
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

    public EnhancedSwarmHost(String bindAddress, int port, int webPort, String token) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.token = token == null ? "" : token;
        try {
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(bindAddress, port));
        } catch (IOException e) {
            socket = null;
            ChatUtils.errorPrefix("Swarm", "Couldn't bind the swarm server to %s:%s.", bindAddress, port);
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
                    ChatUtils.infoPrefix("Swarm", "Loaded base anchor for %s: %d %d %d", server, b[0], b[1], b[2]);
                }
            } catch (Exception ignored) {}
            try {
                webServer = new SwarmWebServer(bindAddress, webPort, token);
                webServer.start();
            } catch (Throwable e) {
                ChatUtils.errorPrefix("Swarm", "Web panel failed to start on %s:%s: %s", bindAddress, webPort, e.getMessage());
            }
            leaseMaintenance.scheduleWithFixedDelay(() -> {
                try {
                    if (running) runLeaseMaintenance(taskQueue, System.currentTimeMillis());
                } catch (Throwable ignored) {}
            }, LEASE_SWEEP_MS, LEASE_SWEEP_MS, TimeUnit.MILLISECONDS);
        }
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
            ChatUtils.infoPrefix("Swarm", "CHEST: add %s %d %d %d %s", dimension, x, y, z, category);
            ChatUtils.infoPrefix("Swarm", "Linked %s chest at %d,%d,%d (server: %s)", category, x, y, z, server);
            pushDepositConfigAll();
        } else {
            ChatUtils.infoPrefix("Swarm", "CHEST: duplicate ignored %s %d %d %d %s", dimension, x, y, z, category);
        }
    }

    public void setBaseAnchor(int x, int y, int z) {
        baseAnchorX = x;
        baseAnchorY = y;
        baseAnchorZ = z;

        String server = getCurrentServer();
        try { depositConfig.setBase(server, x, y, z); } catch (Exception ignored) {}

        ChatUtils.infoPrefix("Swarm", "Linked base anchor at %d,%d,%d (server: %s)", x, y, z, server);
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
        ChatUtils.infoPrefix("Swarm", "Listening for incoming connections on %s:%s.", bindAddress, socket.getLocalPort());
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
                ChatUtils.errorPrefix("Swarm", "Error making a connection to worker.");
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

            if (!found) ChatUtils.warningPrefix("Swarm", "Worker not found: %s", workerName);
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
        for (EnhancedSwarmConnection c : connections) {
            if (c == null || c.isInterrupted() || c.socket == null || c.socket.isClosed()) {
                if (c != null && c.workerName != null) taskQueue.requeueActiveForBot(c.workerName);
            }
        }
        connections.removeIf(c -> c == null || c.isInterrupted() || c.socket == null || c.socket.isClosed());
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

        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ChatUtils.infoPrefix("Swarm", "Server closed on port %s.", socket == null ? "?" : socket.getLocalPort());
        } catch (Exception ignored) {}

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

    public StorageIndex getStorageIndex() { return storageIndex; }

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

    public void pushDepositConfigAll() {
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

    /** The host's sole dispatch/claim path, also used by the H5 probes. */
    static BotTask claimNextTaskForDispatch(TaskQueue queue, String botName, long now, long ttlMs) {
        if (queue == null || botName == null || botName.isEmpty()) return null;
        return queue.claimNextForBot(botName, now, ttlMs);
    }

    /** The maintenance tick body; kept static so the expiry path is testable. */
    static int runLeaseMaintenance(TaskQueue queue, long now) {
        return queue == null ? 0 : queue.expireLeases(now);
    }

    /**
     * Dispatch the next pending task to a bot, but ONLY if that
     * bot doesn't already have a live leased task.
     */
    public void dispatchNextTask(String botName) {
        if (botName == null || botName.isEmpty()) return;
        try {
            // A halted bot accepts no planner work until an explicit resume.
            BotStatus st = botStatuses.get(botName);
            if (st != null && st.halted) return;

            BotTask next = claimNextTaskForDispatch(taskQueue, botName,
                System.currentTimeMillis(), LEASE_TTL_MS);
            if (next == null) return;

            String payload = String.format("task assign %s|%s|%s|%d|%s",
                next.getId().toString(), next.getType().name(), next.getItemName(), next.getTargetQuantity(), next.getAssignedBot() == null ? "" : next.getAssignedBot());
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
        String msg = String.format("swarm:%s task assign %s|%s|%s|%d|%s", workerName, claimed.getId().toString(), claimed.getType().name(), itemName, claimed.getTargetQuantity(), claimed.getAssignedBot() == null ? "" : claimed.getAssignedBot());
        sendTo(workerName, msg);
    }

    void putBotStatus(BotStatus status) {
        if (status == null || status.workerName == null) return;
        botStatuses.put(status.workerName, status);
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
}
