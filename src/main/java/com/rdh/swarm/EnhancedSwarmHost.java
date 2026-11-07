package com.rdh.swarm;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.web.SwarmWebServer;
import com.rdh.swarm.WaypointManager;

public class EnhancedSwarmHost extends Thread {
    private ServerSocket socket;
    private final CopyOnWriteArrayList<EnhancedSwarmConnection> connections = new CopyOnWriteArrayList<>();
    private final Map<String, WorkerConfig> workerConfigs = new ConcurrentHashMap<>();
    private final Map<String, BotStatus> botStatuses = new ConcurrentHashMap<>();
    private final TaskQueue taskQueue = new TaskQueue();
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

    public EnhancedSwarmHost(int port) {
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            socket = null;
            ChatUtils.errorPrefix("Swarm", "Couldn't start a server on port %s.", port);
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
                webServer = new SwarmWebServer(8888);
                webServer.start();
            } catch (Throwable ignored) {}
        }
    }

    public void setDepositChest(String category, int x, int y, int z) {
        if (category == null || category.isEmpty()) category = "all";
        depositChestX = x;
        depositChestY = y;
        depositChestZ = z;

        String server = getCurrentServer();
        // append to stored config rather than replacing
        depositConfig.addChest(server, category, x, y, z);

        ChatUtils.infoPrefix("Swarm", "Linked %s chest at %d,%d,%d (server: %s)", category, x, y, z, server);
        sendAll(String.format("swarm:all deposit set %s %d %d %d", category, x, y, z));
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
        ChatUtils.infoPrefix("Swarm", "Listening for incoming connections on port %s.", socket.getLocalPort());
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
        connections.removeIf(c -> c == null || c.isInterrupted() || c.socket == null || c.socket.isClosed());
    }

    public void disconnect() {
        // stop run loop first
        running = false;
        for (EnhancedSwarmConnection connection : connections) {
            if (connection != null) connection.disconnect();
        }

        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ChatUtils.infoPrefix("Swarm", "Server closed on port %s.", socket == null ? "?" : socket.getLocalPort());
        } catch (Exception ignored) {}

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

    public String getCurrentServer() {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null) return "unknown";
            if (mc.getCurrentServerEntry() != null) {
                String addr = mc.getCurrentServerEntry().address;
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
            if (mc.isIntegratedServerRunning()) return "singleplayer";
        } catch (Exception ignored) {}
        return "unknown";
    }

    public void pushDepositConfig(String workerName) {
        String server = getCurrentServer();
        Map<String, List<int[]>> chests = depositConfig.getChests(server);
        for (var entry : chests.entrySet()) {
            for (int[] pos : entry.getValue()) {
                String msg = String.format("swarm:%s deposit set %s %d %d %d", workerName, entry.getKey(), pos[0], pos[1], pos[2]);
                sendTo(workerName, msg);
            }
        }
        // push base anchor for this server if present
        try {
            int[] b = depositConfig.getBase(server);
            if (b != null && b.length >= 3) sendTo(workerName, String.format("swarm:%s base set %d %d %d", workerName, b[0], b[1], b[2]));
        } catch (Exception ignored) {}
    }

    public void pushDepositConfigAll() {
        String server = getCurrentServer();
        Map<String, List<int[]>> chests = depositConfig.getChests(server);
        for (var entry : chests.entrySet()) {
            for (int[] pos : entry.getValue()) {
                sendAll(String.format("swarm:all deposit set %s %d %d %d", entry.getKey(), pos[0], pos[1], pos[2]));
            }
        }
        // push base anchor to all workers
        try {
            int[] b = depositConfig.getBase(server);
            if (b != null && b.length >= 3) sendAll(String.format("swarm:all base set %d %d %d", b[0], b[1], b[2]));
        } catch (Exception ignored) {}
    }

    public BotStatus getBotStatus(String workerName) {
        if (workerName == null) return null;
        return botStatuses.get(workerName);
    }

    public TaskQueue getTaskQueue() {
        return taskQueue;
    }

    /**
     * Dispatch the next pending task to a bot, but ONLY if that
     * bot doesn't already have an active task.
     */
    public void dispatchNextTask(String botName) {
        if (botName == null || botName.isEmpty()) return;
        try {
            // Don't send if bot already has an active task
            if (taskQueue.hasActiveTask(botName)) return;

            BotTask next = taskQueue.getNextPendingForBot(botName);
            if (next == null) return;

            // Assign the bot name if it was "any"/"all"/empty
            String assigned = next.getAssignedBot();
            if (assigned == null || assigned.isEmpty()
                || assigned.equalsIgnoreCase("any")
                || assigned.equalsIgnoreCase("all")) {
                next.setAssignedBot(botName);
            }

            // Set to ACTIVE and send assignment
            next.setStatus(BotTask.Status.ACTIVE);
            String payload = String.format("task assign %s|%s|%s|%d|%s",
                next.getId().toString(), next.getType().name(), next.getItemName(), next.getTargetQuantity(), next.getAssignedBot() == null ? "" : next.getAssignedBot());
            sendTo(botName, payload);

            taskQueue.save();
        } catch (Exception ignored) {}
    }

    public void assignTask(String workerName, com.rdh.swarm.task.BotTask task) {
        if (workerName == null || task == null) return;
        if (workerName != null && !workerName.isEmpty()) {
            try { task.setAssignedBot(workerName); } catch (Exception ignored) {}
        }
        String itemName = "";
        try { itemName = task.getItemName(); } catch (Exception ignored) {}
        String msg = String.format("swarm:%s task assign %s|%s|%s|%d|%s", workerName, task.getId().toString(), task.getType().name(), itemName, task.getTargetQuantity(), task.getAssignedBot() == null ? "" : task.getAssignedBot());
        sendTo(workerName, msg);
    }

    void putBotStatus(BotStatus status) {
        if (status == null || status.workerName == null) return;
        botStatuses.put(status.workerName, status);
    }
}
