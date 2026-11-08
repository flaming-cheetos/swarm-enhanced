package com.rdh.swarm.web;

import com.rdh.swarm.BotStatus;
import com.rdh.swarm.EnhancedSwarm;
import com.rdh.swarm.EnhancedSwarmHost;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.RecipeBook;
import com.rdh.swarm.task.TaskQueue;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Web panel API contract: every endpoint must do only what its path and UI
 * label document. If a control cannot perform its advertised action, disable
 * it and send no request; never attach an unrelated game command to an
 * endpoint just to make the control appear to work.
 */
public class SwarmWebServer {

    private static final String MOD_VERSION;
    private static final String BUILD_COMMIT;

    static {
        Properties buildInfo = new Properties();
        try (InputStream in = SwarmWebServer.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) buildInfo.load(in);
        } catch (Exception ignored) {}
        MOD_VERSION = buildInfo.getProperty("version", "unknown");
        BUILD_COMMIT = buildInfo.getProperty("commit", "unknown");
    }

    private final String bindAddress;
    private final int port;
    private final String token;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private final ChannelGroup wsClients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<String> eventLog = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_ENTRIES = 500;

    // Signature
    private static final byte[] _ws0 = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _ws1 = 0x726468;

    public SwarmWebServer(String bindAddress, int port, String token) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.token = token == null ? "" : token;
    }

    /** Loopback is always allowed; remote clients must present the configured token header. */
    public boolean isAuthorized(Channel channel, String suppliedToken) {
        try {
            if (channel.remoteAddress() instanceof InetSocketAddress address
                && address.getAddress() != null
                && address.getAddress().isLoopbackAddress()) return true;
            if (token.isEmpty()) return false;
            return suppliedToken != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new WebSocketServerCompressionHandler());
                        p.addLast(new SwarmWebHandler(SwarmWebServer.this));
                    }
                });

            serverChannel = bootstrap.bind(bindAddress, port).sync().channel();
            ChatUtils.infoPrefix("Swarm", "Web panel started on http://%s:%d", bindAddress, port);

            // Push state to WebSocket clients every 2 seconds
            scheduler.scheduleAtFixedRate(this::broadcastState, 2, 2, TimeUnit.SECONDS);

        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Web panel failed to start: " + e.getMessage());
            stop();
        }
    }

    public void stop() {
        scheduler.shutdown();
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        ChatUtils.infoPrefix("Swarm", "Web panel stopped.");
    }

    public void addClient(Channel ch) {
        wsClients.add(ch);
        // Send current state immediately on connect
        broadcastTo(ch, buildStateJson());
        // Send log history
        broadcastTo(ch, buildLogJson());
    }

    public void removeClient(Channel ch) {
        wsClients.remove(ch);
    }

    public void log(String level, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = String.format(
            "{\"type\":\"log\",\"time\":\"%s\",\"level\":\"%s\",\"msg\":\"%s\"}",
            time, level, message.replace("\"", "'")
        );
        eventLog.add(entry);
        if (eventLog.size() > MAX_LOG_ENTRIES) eventLog.remove(0);
        broadcast(entry);
    }

    public void logInfo(String message) { log("INFO", message); }
    public void logWarn(String message) { log("WARN", message); }
    public void logError(String message) { log("ERROR", message); }

    private void broadcast(String json) {
        TextWebSocketFrame frame = new TextWebSocketFrame(json);
        wsClients.writeAndFlush(frame);
    }

    private void broadcastTo(Channel ch, String json) {
        ch.writeAndFlush(new TextWebSocketFrame(json));
    }

    private void broadcastState() {
        if (wsClients.isEmpty()) return;
        broadcast(buildStateJson());
    }

    public String buildStateJson() {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod == null || !mod.isHost() || mod.host == null) {
            return "{\"type\":\"state\",\"modVersion\":\"" + esc(MOD_VERSION)
                + "\",\"buildCommit\":\"" + esc(BUILD_COMMIT)
                + "\",\"hostName\":\"\",\"connected\":0,\"bots\":[],\"tasks\":[]}";
        }

        EnhancedSwarmHost host = mod.host;
        StringBuilder sb = new StringBuilder();
        String hostName = "";
        try { hostName = net.minecraft.client.Minecraft.getInstance().getUser().getName(); } catch (Exception ignored) {}
        sb.append("{\"type\":\"state\",\"modVersion\":\"").append(esc(MOD_VERSION));
        sb.append("\",\"buildCommit\":\"").append(esc(BUILD_COMMIT)).append("\",");
        sb.append("\"hostName\":\"").append(esc(hostName)).append("\",");
        sb.append("\"connected\":").append(host.getConnectionCount()).append(",");

        // Bots
        sb.append("\"bots\":[");
        String[] names = host.getWorkerNames();
        for (int i = 0; i < names.length; i++) {
            String workerName = names[i];
            BotStatus status = host.getBotStatus(workerName);
            String nickname = host.getTaskQueue() != null ?
                host.getTaskQueue().getNickname(workerName) : workerName;

            sb.append("{");
            sb.append("\"username\":\"").append(esc(workerName)).append("\",");
            sb.append("\"nickname\":\"").append(esc(nickname)).append("\",");

            if (status != null) {
                sb.append("\"online\":").append(status.isOnline()).append(",");
                sb.append("\"health\":").append(String.format("%.1f", status.health)).append(",");
                sb.append("\"food\":").append(status.food).append(",");
                sb.append("\"x\":").append((int)status.x).append(",");
                sb.append("\"y\":").append((int)status.y).append(",");
                sb.append("\"z\":").append((int)status.z).append(",");
                sb.append("\"task\":\"").append(esc(status.taskDescription != null ? status.taskDescription : "Idle")).append("\",");
                sb.append("\"progress\":\"").append(esc(status.getProgressString())).append("\",");
                sb.append("\"eta\":\"").append(esc(status.eta != null ? status.eta : "")).append("\",");
                sb.append("\"tier\":\"").append(esc(status.tier != null ? status.tier : "Wood")).append("\",");
                sb.append("\"state\":\"").append(esc(status.state != null ? status.state : "IDLE")).append("\"");
            } else {
                sb.append("\"online\":false,");
                sb.append("\"health\":0,\"food\":0,\"x\":0,\"y\":0,\"z\":0,");
                sb.append("\"task\":\"Offline\",\"progress\":\"\",\"eta\":\"\",");
                sb.append("\"tier\":\"Wood\",\"state\":\"OFFLINE\"");
            }
            sb.append("}");
            if (i < names.length - 1) sb.append(",");
        }
        sb.append("],");

        // Tasks
        sb.append("\"tasks\":[");
        if (host.getTaskQueue() != null) {
            List<BotTask> tasks = host.getTaskQueue().getAllTasks();
            for (int i = 0; i < tasks.size(); i++) {
                BotTask task = tasks.get(i);
                sb.append("{");
                sb.append("\"id\":\"").append(task.getId()).append("\",");
                sb.append("\"type\":").append(task.getType().name()).append("\",");
                sb.append("\"item\":\"").append(esc(task.getItemName())).append("\",");
                sb.append("\"quantity\":").append(task.getTargetQuantity()).append(",");
                sb.append("\"progress\":\"").append(esc(task.getProgressString())).append("\",");
                sb.append("\"status\":\"").append(task.getStatus().name()).append("\",");
                sb.append("\"bot\":\"").append(esc(task.getAssignedBot() != null ? task.getAssignedBot() : "any")).append("\"");
                sb.append("}");
                if (i < tasks.size() - 1) sb.append(",");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildLogJson() {
        StringBuilder sb = new StringBuilder("{\"type\":\"log_history\",\"logs\":[");
        for (int i = 0; i < eventLog.size(); i++) {
            sb.append(eventLog.get(i));
            if (i < eventLog.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    public ChannelGroup getWsClients() { return wsClients; }

    public void handleApiCommand(String path, String body) {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod == null || !mod.isHost() || mod.host == null) return;
        EnhancedSwarmHost host = mod.host;

        try {
            switch (path) {
                case "/api/task/add" -> {
                    String item = extractJson(body, "item");
                    int qty = 64;
                    try { qty = Integer.parseInt(extractJson(body, "qty")); } catch (Exception ignored) {}
                    String bot = extractJson(body, "bot");
                    String typeStr = extractJson(body, "type");
                    if (item == null) item = "";
                    boolean itemless = item.isEmpty() || item.equalsIgnoreCase("none");

                    net.minecraft.world.item.Item mcItem = null;
                    if (!itemless) {
                        try { mcItem = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", item)); } catch (Exception ignored) {}
                    }
                    BotTask.Type taskType;
                    if (typeStr == null || typeStr.isEmpty() || typeStr.equalsIgnoreCase("auto")) {
                        boolean hasRecipe = false;
                        try {
                            if (mcItem != null) hasRecipe = RecipeBook.hasRecipe(mcItem);
                            else hasRecipe = RecipeBook.hasRecipe(item);
                        } catch (Exception ignored) {}
                        taskType = hasRecipe ? BotTask.Type.CRAFT : BotTask.Type.GATHER;
                    } else {
                        taskType = BotTask.Type.valueOf(typeStr.toUpperCase());
                    }
                    BotTask task = new BotTask(taskType, mcItem, qty);
                    // Clear any stale completed/failed tasks before adding a new one
                    try { if (host.getTaskQueue() != null) host.getTaskQueue().clearCompleted(); } catch (Exception ignored) {}
                    host.getTaskQueue().addTask(task);

                    // If a specific bot was requested, try to resolve nickname
                    String targetBot = bot;
                    if (bot != null && !bot.isEmpty() && !bot.equalsIgnoreCase("any")) {
                        try {
                            java.util.Map<String,String> nmap = host.getTaskQueue().getAllNicknames();
                            if (nmap != null) {
                                for (var entry : nmap.entrySet()) {
                                    if (entry.getValue().equalsIgnoreCase(bot)) {
                                        targetBot = entry.getKey();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    } else {
                        targetBot = null;
                    }

                    // Let the host decide whether to dispatch now (ensures single active task)
                    try { host.dispatchNextTask(targetBot == null ? (host.getWorkerNames().length > 0 ? host.getWorkerNames()[0] : "") : targetBot); } catch (Exception ignored) {}

                    logInfo("Task added: " + (itemless ? taskType.name() : item) + " x" + qty + " → " + (bot != null ? bot : "any"));
                }
                case "/api/task/clear_done" -> {
                    if (host.getTaskQueue() != null) host.getTaskQueue().clearCompleted();
                    logInfo("Cleared completed tasks");
                }
                case "/api/task/remove" -> {
                    String id = extractJson(body, "id");
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(id);
                        host.getTaskQueue().removeById(uuid);
                        logInfo("Task removed: " + id);
                    } catch (Exception e) {
                        logWarn("Invalid task id: " + id);
                    }
                }
                case "/api/task/pause" -> {
                    String bot = extractJson(body, "bot");
                    host.sendTo(bot, "swarm:" + bot + " task pause");
                    logInfo("Paused: " + bot);
                }
                case "/api/task/resume" -> {
                    String bot = extractJson(body, "bot");
                    host.sendTo(bot, "swarm:" + bot + " task resume");
                    logInfo("Resumed: " + bot);
                }
                case "/api/action/follow" -> {
                    String myName = "";
                    try { myName = net.minecraft.client.Minecraft.getInstance().getUser().getName(); } catch (Exception ignored) {}
                    host.sendAll("swarm:all follow player " + myName);
                    logInfo("Follow me sent to all bots");
                }
                case "/api/action/stop" -> {
                    host.sendAll("swarm:all stop");
                    logInfo("Stop sent to all bots");
                }
                case "/api/action/deposit" -> {
                    host.sendAll("swarm:all depositall");
                    logInfo("Deposit all sent to all bots");
                }
                case "/api/action/deposit_ores" -> {
                    host.sendAll("swarm:all deposit ores");
                    logInfo("Deposit ores sent to all bots");
                }
                case "/api/action/deposit_shulkers" -> {
                    host.sendAll("swarm:all deposit shulkers");
                    logInfo("Deposit shulkers sent to all bots");
                }
                case "/api/action/pvp_on" -> {
                    String[] modules = {"crystal-aura","kill-aura","auto-totem","surround","auto-trap","auto-web","auto-exp"};
                    for (String m : modules) host.sendAll("swarm:all exec .toggle " + m + " on");
                    logInfo("PVP mode ENABLED");
                }
                case "/api/action/pvp_off" -> {
                    String[] modules = {"crystal-aura","kill-aura","auto-totem","surround","auto-trap","auto-web","auto-exp"};
                    for (String m : modules) host.sendAll("swarm:all exec .toggle " + m + " off");
                    logInfo("PVP mode DISABLED");
                }
                case "/api/action/scan_chests" -> {
                    logWarn("Scan Chests is not implemented: container reader unavailable; no command sent");
                }
                case "/api/action/check_fuel" -> {
                    logWarn("Check Fuel is not implemented: container reader unavailable; no command sent");
                }
                case "/api/command" -> {
                    String target = extractJson(body, "target");
                    String cmd = extractJson(body, "cmd");
                    // Send a real exec verb to each worker. "swarm:all <cmd>" makes the
                    // worker parse "all" as the verb instead of the intended command.
                    if (target.equalsIgnoreCase("all")) {
                        for (String workerName : host.getWorkerNames()) {
                            host.sendTo(workerName, "swarm:" + workerName + " exec " + cmd);
                        }
                    } else {
                        host.sendTo(target, "swarm:" + target + " exec " + cmd);
                    }
                    logInfo("Command sent to " + target + ": " + cmd);
                }
                case "/api/nickname" -> {
                    String username = extractJson(body, "username");
                    String nick = extractJson(body, "nickname");
                    if (host.getTaskQueue() != null) {
                        host.getTaskQueue().setNickname(username, nick);
                        logInfo("Nickname set: " + username + " → " + nick);
                    }
                }
            }
        } catch (Exception e) {
            logError("API error on " + path + ": " + e.getMessage());
        }

        // Push updated state after any command
        broadcastState();
    }

    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        idx += search.length();
        if (json.charAt(idx) == '"') {
            int end = json.indexOf('"', idx + 1);
            return json.substring(idx + 1, end);
        } else {
            int end = idx;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(idx, end).trim();
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
