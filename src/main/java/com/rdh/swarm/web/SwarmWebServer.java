package com.rdh.swarm.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
    private final boolean generatedToken;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private final ChannelGroup wsClients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<String> eventLog = new CopyOnWriteArrayList<>();
    private volatile String itemsJson;
    private static final int MAX_LOG_ENTRIES = 500;
    private static final Gson GSON = new Gson();

    // Signature
    private static final byte[] _ws0 = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _ws1 = 0x726468;

    public SwarmWebServer(String bindAddress, int port, String token) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.generatedToken = token == null || token.isBlank();
        this.token = generatedToken ? generateToken() : token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Every request, including loopback and the WebSocket handshake, must carry
     * the configured token in the header, query string, or session cookie.
     */
    public boolean isAuthorized(FullHttpRequest request) {
        String supplied = tokenFromHeader(request);
        if (supplied == null) supplied = tokenFromQuery(request);
        if (supplied == null) supplied = tokenFromCookie(request);
        if (supplied == null) return false;
        return MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8));
    }

    /** Reject a browser request from any origin other than this panel. */
    public boolean isOriginAllowed(FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null) return true;
        String host = request.headers().get(HttpHeaderNames.HOST);
        return host != null && origin.equalsIgnoreCase("http://" + host);
    }

    private String tokenFromHeader(FullHttpRequest request) {
        String value = request.headers().get("X-Swarm-Token");
        return value == null || value.isBlank() ? null : value;
    }

    private String tokenFromQuery(FullHttpRequest request) {
        try {
            List<String> values = new QueryStringDecoder(request.uri()).parameters().get("token");
            return values == null || values.isEmpty() || values.get(0).isBlank() ? null : values.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private String tokenFromCookie(FullHttpRequest request) {
        String header = request.headers().get(HttpHeaderNames.COOKIE);
        if (header == null) return null;
        try {
            for (var cookie : ServerCookieDecoder.LAX.decode(header)) {
                if (cookie.name().equals("swarm_token")) return cookie.value();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String sessionCookie() {
        return "swarm_token=" + token + "; Path=/; HttpOnly; SameSite=Strict";
    }

    /** The token is only exposed to the panel classes that serve it. */
    String token() {
        return token;
    }

    private String panelHost() {
        if (bindAddress == null || bindAddress.isBlank()
            || bindAddress.equals("0.0.0.0") || bindAddress.equals("::")) return "127.0.0.1";
        return bindAddress;
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
            ChatUtils.infoPrefix("Swarm", "Web panel: http://%s:%d/?token=%s", panelHost(), port, token);
            if (generatedToken) ChatUtils.infoPrefix("Swarm", "Generated web token (shown once).");

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
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "log");
        entry.addProperty("time", time);
        entry.addProperty("level", level);
        entry.addProperty("msg", message);
        String json = GSON.toJson(entry);
        eventLog.add(json);
        if (eventLog.size() > MAX_LOG_ENTRIES) eventLog.remove(0);
        broadcast(json);
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
                + "\",\"hostName\":\"\",\"dimension\":\"\",\"connected\":0,\"bots\":[],\"tasks\":[]}";
        }

        EnhancedSwarmHost host = mod.host;
        StringBuilder sb = new StringBuilder();
        String hostName = "";
        try { hostName = net.minecraft.client.Minecraft.getInstance().getUser().getName(); } catch (Exception ignored) {}
        String dimension = "";
        try { dimension = host.getCurrentDimension(); } catch (Exception ignored) {}
        sb.append("{\"type\":\"state\",\"modVersion\":\"").append(esc(MOD_VERSION));
        sb.append("\",\"buildCommit\":\"").append(esc(BUILD_COMMIT)).append("\",");
        sb.append("\"hostName\":\"").append(esc(hostName)).append("\",");
        sb.append("\"dimension\":\"").append(esc(dimension)).append("\",");
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
                sb.append("\"state\":\"").append(esc(status.state != null ? status.state : "IDLE")).append("\",");
                sb.append("\"halted\":").append(status.halted);
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
            long now = System.currentTimeMillis();
            for (int i = 0; i < tasks.size(); i++) {
                sb.append(buildTaskJson(tasks.get(i), now));
                if (i < tasks.size() - 1) sb.append(",");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Serialize one task without its fencing token. Lease diagnostics are
     * intentionally owner/expiry/age only; exposing the token would let a
     * panel client forge a stale completion.
     */
    public static String buildTaskJson(BotTask task, long now) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(task.getId()).append("\",");
        sb.append("\"type\":\"").append(task.getType().name()).append("\",");
        sb.append("\"item\":\"").append(esc(task.getItemName())).append("\",");
        sb.append("\"quantity\":").append(task.getTargetQuantity()).append(",");
        sb.append("\"progress\":\"").append(esc(task.getProgressString())).append("\",");
        sb.append("\"status\":\"").append(task.getStatus().name()).append("\",");
        sb.append("\"reason\":\"").append(esc(task.getFailureReason() == null ? "" : task.getFailureReason())).append("\",");
        sb.append("\"bot\":\"").append(esc(task.getAssignedBot() != null ? task.getAssignedBot() : "any")).append("\",");
        sb.append("\"attempt\":").append(task.getAttempt()).append(",");
        sb.append("\"statusReason\":\"").append(esc(task.getStatusReason())).append("\",");
        sb.append("\"lastOwner\":\"").append(esc(task.getLastOwner())).append("\",");
        sb.append("\"leaseOwner\":\"").append(esc(task.getAssignedBot() != null ? task.getAssignedBot() : "")).append("\",");
        long expiresAt = task.getLeaseExpiresAt();
        long heartbeatAt = task.getLastHeartbeatAt();
        sb.append("\"leaseExpiresAt\":").append(expiresAt).append(",");
        sb.append("\"leaseExpiresInMs\":").append(expiresAt <= 0 ? 0 : Math.max(0L, expiresAt - now)).append(",");
        sb.append("\"lastHeartbeatAt\":").append(heartbeatAt).append(",");
        sb.append("\"heartbeatAgeMs\":").append(heartbeatAt <= 0 ? 0 : Math.max(0L, now - heartbeatAt)).append(",");
        sb.append("\"leaseActive\":").append(task.getStatus() == BotTask.Status.ACTIVE && expiresAt > now);
        sb.append("}");
        return sb.toString();
    }

    private String buildLogJson() {
        JsonArray logs = new JsonArray();
        for (String entry : eventLog) {
            try {
                logs.add(JsonParser.parseString(entry));
            } catch (Exception ignored) {}
        }
        JsonObject root = new JsonObject();
        root.addProperty("type", "log_history");
        root.add("logs", logs);
        return GSON.toJson(root);
    }

    public ChannelGroup getWsClients() { return wsClients; }

    /** Registry ids for the phone item autocomplete. Built once, then cached. */
    public String buildItemsJson() {
        String cached = itemsJson;
        if (cached != null) return cached;
        JsonArray items = new JsonArray();
        try {
            BuiltInRegistries.ITEM.stream()
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .sorted()
                .forEach(id -> items.add(id));
        } catch (Exception ignored) {}
        JsonObject root = new JsonObject();
        root.add("items", items);
        cached = GSON.toJson(root);
        itemsJson = cached;
        return cached;
    }

    /**
     * Debug read surface for the MCP server (docs/MCP_SERVER_SPEC.md section 6).
     * Returns null for paths this method does not own, so the caller falls back to
     * the fire-and-forget command switch. POST-only, same token/Origin rules.
     */
    public String handleDebugApi(String path, String body) {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        try {
            if ("/api/debug/state".equals(path)) {
                return buildStateJson();
            }
            if ("/api/debug/bot".equals(path)) {
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                String bot = extractJson(body, "bot");
                if (bot == null || bot.isBlank()) {
                    StringBuilder sb = new StringBuilder("{\"ok\":true,\"bots\":{");
                    boolean first = true;
                    for (String name : mod.host.getDebugStateWorkers()) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('"').append(esc(name)).append("\":").append(mod.host.getDebugState(name));
                    }
                    return sb.append("}}").toString();
                }
                boolean refresh = "true".equalsIgnoreCase(String.valueOf(extractJson(body, "refresh")));
                if (refresh) mod.host.requestDebugState(bot);
                String json = mod.host.getDebugState(bot);
                if (json == null) {
                    return "{\"ok\":false,\"error\":\"no_state_yet\",\"bot\":\"" + esc(bot)
                        + "\",\"detail\":\"no debug reply cached for this worker yet\"}";
                }
                long age = System.currentTimeMillis() - mod.host.getDebugStateAt(bot);
                return "{\"ok\":true,\"bot\":\"" + esc(bot) + "\",\"ageMs\":" + age + ",\"data\":" + json + "}";
            }
            if ("/api/debug/health".equals(path)) {
                // Reachability + identity: the jar path proves which build answered, so a
                // stale process cannot be mistaken for the one just started.
                String jar = "";
                try {
                    jar = EnhancedSwarm.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                } catch (Exception ignored) {}
                return "{\"ok\":true,\"pid\":" + ProcessHandle.current().pid()
                    + ",\"uptimeMs\":" + java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()
                    + ",\"isHost\":" + (mod != null && mod.isHost())
                    + ",\"jar\":\"" + esc(jar) + "\"}";
            }

            if ("/api/debug/logs".equals(path)) {
                // B1 log_tail: the real tail of this instance's log, filtered, with timestamps.
                int want = 80;
                try {
                    String n = extractJson(body, "lines");
                    if (n != null) want = Math.max(1, Math.min(500, Integer.parseInt(n.trim())));
                } catch (Exception ignored) {}
                String filter = extractJson(body, "filter");
                java.nio.file.Path logFile = java.nio.file.Path.of("logs", "latest.log");
                if (!java.nio.file.Files.exists(logFile)) {
                    return "{\"ok\":false,\"error\":\"no_log\",\"path\":\"" + esc(logFile.toAbsolutePath().toString()) + "\"}";
                }
                java.util.List<String> all = java.nio.file.Files.readAllLines(logFile);
                java.util.List<String> kept = new java.util.ArrayList<>();
                for (String line : all) {
                    if (filter != null && !filter.isBlank() && !line.contains(filter)) continue;
                    kept.add(line);
                }
                int from = Math.max(0, kept.size() - want);
                StringBuilder sb = new StringBuilder("{\"ok\":true,\"path\":\"")
                    .append(esc(logFile.toAbsolutePath().toString()))
                    .append("\",\"totalLines\":").append(all.size())
                    .append(",\"matched\":").append(kept.size())
                    .append(",\"lines\":[");
                for (int i = from; i < kept.size(); i++) {
                    if (i > from) sb.append(',');
                    sb.append('"').append(esc(kept.get(i))).append('"');
                }
                return sb.append("]}").toString();
            }

            if ("/api/debug/tasks".equals(path)) {
                // B1 task_watch: the live queue, straight out of the TaskQueue in the tree.
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                var q = mod.host.getTaskQueue();
                var gson = new com.google.gson.Gson();
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(buildStateJson()).getAsJsonObject();
                com.google.gson.JsonArray tasks = root.has("tasks") ? root.getAsJsonArray("tasks") : new com.google.gson.JsonArray();
                return "{\"ok\":true,\"size\":" + q.size() + ",\"panicked\":" + mod.host.isPanicked()
                    + ",\"tasks\":" + gson.toJson(tasks) + "}";
            }

            if ("/api/debug/deposit".equals(path)) {
                // What the host believes the deposit config is, and which storage entries
                // the registry holds: the two must agree or a push will erase the difference.
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                return mod.host.depositConfigJson();
            }

            if ("/api/debug/chest".equals(path)) {
                // B1 chest_delta backend: read a registry chest by id, synchronously.
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                String bot = extractJson(body, "bot");
                String cid = extractJson(body, "id");
                if (bot == null || bot.isBlank() || cid == null || cid.isBlank()) {
                    return "{\"ok\":false,\"error\":\"bot_and_id_required\"}";
                }
                var entry = mod.host.getBaseRegistry().byId(cid);
                if (entry == null) {
                    return "{\"ok\":false,\"error\":\"unknown_id\",\"id\":\"" + esc(cid) + "\"}";
                }
                mod.host.requestWorldProbe(bot, "container_open", entry.posString());
                long deadline = System.currentTimeMillis() + 40000L;
                while (System.currentTimeMillis() < deadline) {
                    String json = mod.host.getWorldProbe(bot);
                    if (json != null && System.currentTimeMillis() - mod.host.getWorldProbeAt(bot) < 35000L) {
                        return "{\"ok\":true,\"id\":\"" + esc(cid) + "\",\"pos\":\"" + esc(entry.posString())
                            + "\",\"serverMs\":" + (40000L - (deadline - System.currentTimeMillis()))
                            + ",\"data\":" + json + "}";
                    }
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                return "{\"ok\":false,\"error\":\"probe_timeout\",\"id\":\"" + esc(cid) + "\"}";
            }

            if ("/api/debug/panic".equals(path) || "/api/debug/resume".equals(path)) {
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                if ("/api/debug/resume".equals(path)) mod.host.resume();
                else mod.host.panic();
                return mod.host.panicJson();
            }
            if ("/api/debug/registry".equals(path)) {
                // A3: storage and furnace locations as data, readable by any observer.
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                return mod.host.getBaseRegistry().toJson();
            }
            if ("/api/debug/storage".equals(path)) {
                // A2: the index was invisible, which made "index says 690, world says 768"
                // unanswerable. Totals + per-chest breakdown, read straight from StorageIndex.
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                String server = mod.host.getCurrentServer();
                var idx = mod.host.getStorageIndex();
                StringBuilder sb = new StringBuilder("{\"ok\":true,\"server\":\"" + esc(server) + "\",\"totals\":{");
                var items = idx.getAllItems(server);
                boolean first = true;
                for (String it : items) {
                    long n = idx.getTotal(server, it);
                    if (n <= 0L) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(esc(it)).append("\":").append(n);
                }
                sb.append("},\"breakdown\":{");
                first = true;
                for (String it : items) {
                    long n = idx.getTotal(server, it);
                    if (n <= 0L) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(esc(it)).append("\":\"").append(esc(idx.getBreakdown(server, it))).append('"');
                }
                sb.append("},\"registryChests\":[");
                boolean firstChest = true;
                for (var ce : mod.host.getBaseRegistry().storage()) {
                    if (!firstChest) sb.append(',');
                    firstChest = false;
                    sb.append("{\"id\":\"").append(esc(ce.id)).append("\",\"pos\":\"")
                      .append(esc(ce.posString())).append("\",\"double\":").append(ce.doubleChest).append('}');
                }
                int furnaceCount = mod.host.getBaseRegistry().furnaces().size();
                sb.append("],\"registryFurnaces\":").append(furnaceCount);
                return sb.append('}').toString();
            }
            if ("/api/debug/world".equals(path)) {
                if (mod == null || !mod.isHost() || mod.host == null) {
                    return "{\"ok\":false,\"error\":\"not_host\"}";
                }
                String bot = extractJson(body, "bot");
                if (bot == null || bot.isBlank()) {
                    return "{\"ok\":false,\"error\":\"bot_required\"}";
                }
                String kind = extractJson(body, "kind");
                String args = extractJson(body, "args");
                // A4: an id resolves through the registry, so the furnace's position and
                // kind come from data rather than from whatever the caller passed.
                String regId = extractJson(body, "id");
                if (regId != null && !regId.isBlank()) {
                    var entry = mod.host.getBaseRegistry().byId(regId);
                    if (entry == null) {
                        return "{\"ok\":false,\"error\":\"unknown_id\",\"id\":\"" + esc(regId) + "\"}";
                    }
                    // Registry-driven reads default to opening the container: that is the
                    // only way contents are real, and it is what the id exists for.
                    boolean wantOpen = !"false".equalsIgnoreCase(String.valueOf(extractJson(body, "open")));
                    if (kind == null || kind.isBlank()) kind = wantOpen ? "container_open" : "container";
                    args = entry.posString();
                }
                boolean ask = kind != null && !kind.isBlank();
                if (ask) mod.host.requestWorldProbe(bot, kind.trim(), args == null ? "" : args.trim());
                String json = mod.host.getWorldProbe(bot);
                if (json == null) {
                    return "{\"ok\":false,\"error\":\"waiting\",\"bot\":\"" + esc(bot) + "\"}";
                }
                long age = System.currentTimeMillis() - mod.host.getWorldProbeAt(bot);
                if (!ask && age > 15000L) {
                    return "{\"ok\":false,\"error\":\"stale\",\"ageMs\":" + age + "}";
                }
                return "{\"ok\":true,\"bot\":\"" + esc(bot) + "\",\"ageMs\":" + age + ",\"data\":" + json + "}";
            }
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"debug_failed\",\"detail\":\"" + esc(String.valueOf(e.getMessage())) + "\"}";
        }
        return null;
    }

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
                        try {
                            Identifier id = Identifier.tryParse(item.contains(":") ? item : "minecraft:" + item);
                            if (id != null) mcItem = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                        } catch (Exception ignored) {}
                        if (mcItem == null || mcItem == net.minecraft.world.item.Items.AIR) {
                            logWarn("unknown item '" + item + "', not queued");
                            return;
                        }
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
                        try {
                            taskType = BotTask.Type.valueOf(typeStr.toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            logWarn("unknown task type '" + typeStr + "', not queued");
                            return;
                        }
                        if (!taskType.isDispatchable()) {
                            logWarn("task type " + taskType + " has no handler, not queued");
                            return;
                        }
                    }
                    // Something that must be produced by smelting (ingot from ore, cooked
                    // food from raw) is a SMELT job, not a GATHER job. As GATHER it tried to
                    // mine the finished item and could report 1/1 COMPLETE without smelting.
                    net.minecraft.world.item.Item smeltSource = null;
                    if (mcItem != null && taskType == BotTask.Type.GATHER) {
                        try { smeltSource = com.rdh.swarm.task.RecipeBook.getOreForIngot(mcItem); } catch (Exception ignored) {}
                        if (smeltSource == null) {
                            for (var e : com.rdh.swarm.task.FoodManager.COOKING.entrySet()) {
                                if (e.getValue() == mcItem) { smeltSource = e.getKey(); break; }
                            }
                        }
                    }
                    BotTask task;
                    if (smeltSource != null) {
                        java.util.Map<String, Object> smeltData = new java.util.HashMap<>();
                        smeltData.put("item", mcItem);
                        smeltData.put("quantity", qty);
                        smeltData.put("source", smeltSource);
                        task = new BotTask(java.util.UUID.randomUUID(), BotTask.Type.SMELT, 0, smeltData);
                    } else {
                        task = new BotTask(taskType, mcItem, qty);
                    }
                    if (!itemless && !com.rdh.swarm.task.DependencyResolver.hasKnownSource(mcItem)) {
                        task.setStatus(BotTask.Status.FAILED);
                        task.setFailureReason("no known source");
                        host.getTaskQueue().addTask(task);
                        logWarn("TASK: " + task.getId() + " failed — no known source");
                        return;
                    }
                    // Adding a task must not prune finished history; /api/task/clear_done owns that.
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
                case "/api/task/clear" -> {
                    // A shift needs a known starting board; before this there was no way to
                    // drop a PENDING row, so leftovers rode into the next run.
                    int dropped = host.getTaskQueue() != null ? host.getTaskQueue().clearAll() : 0;
                    logInfo("Cleared whole board: " + dropped + " row(s)");
                }
                case "/api/task/remove" -> {
                    String id = extractJson(body, "id");
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(id);
                        boolean removed = host.getTaskQueue() != null && host.getTaskQueue().removeById(uuid);
                        if (removed) logInfo("Task removed: " + id);
                        else logWarn("Task not on the board (nothing removed): " + id);
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
                    BotStatus status = host.getBotStatus(bot);
                    if (status != null) status.halted = false;
                    host.sendTo(bot, "swarm:" + bot + " resume");
                    host.dispatchNextTask(bot);
                    logInfo("resume received for " + bot);
                }
                case "/api/task/cancel" -> {
                    String id = extractJson(body, "id");
                    String bot = extractJson(body, "bot");
                    String target = bot;
                    if (id != null && !id.isBlank() && host.getTaskQueue() != null) {
                        try {
                            // Cancel the row first so the scheduler cannot re-dispatch
                            // it, then tell the owning worker to stop.
                            String owner = host.getTaskQueue().cancelById(java.util.UUID.fromString(id));
                            if (owner != null && !owner.isBlank()) target = owner;
                            logInfo("Task cancelled: " + id);
                        } catch (Exception e) {
                            logWarn("Invalid task id: " + id);
                        }
                    }
                    if (target != null && !target.isBlank()) {
                        host.sendTo(target, "swarm:" + target + " cancel");
                        logInfo("cancel sent to " + target);
                    }
                }
                case "/api/action/follow" -> {
                    String myName = "";
                    try { myName = net.minecraft.client.Minecraft.getInstance().getUser().getName(); } catch (Exception ignored) {}
                    host.sendAll("swarm:all follow player " + myName);
                    logInfo("Follow me sent to all bots");
                }
                case "/api/action/cancel", "/api/action/stop" -> {
                    host.sendAll("swarm:all cancel");
                    logInfo("cancel received — sent to all bots");
                }
                case "/api/action/resume" -> {
                    host.clearHaltedFlags();
                    host.sendAll("swarm:all resume");
                    logInfo("resume received — sent to all bots");
                }
                case "/api/action/deposit" -> {
                    host.sendAll("swarm:all depositall");
                    logInfo("Deposit all sent to all bots");
                }
                case "/api/action/test_give" -> {
                    // Test fixture for the player-side deposit path: the host player has no
                    // task and no worker, so there was no way to put a known stack in their
                    // inventory to deposit. Hands a vanilla /give to the client thread; the
                    // server still decides whether it is allowed. Not a survival feature.
                    var giveMod = meteordevelopment.meteorclient.systems.modules.Modules.get()
                        .get(com.rdh.swarm.EnhancedSwarm.class);
                    String giveItem = extractJson(body, "item");
                    int giveCount = 1;
                    try { giveCount = Integer.parseInt(extractJson(body, "count")); } catch (Exception ignored) {}
                    if (giveMod == null || giveItem == null || giveItem.isBlank()) {
                        logWarn("Test fixture: swarm module or item missing");
                    } else {
                        final String gi = giveItem;
                        final int gc = giveCount;
                        net.minecraft.client.Minecraft.getInstance()
                            .execute(() -> giveMod.testGive(gi, gc));
                        logInfo("Test fixture: requested " + gi + " x" + gc + " via /give");
                    }
                }
                case "/api/action/deposit_self" -> {
                    // The host player's own inventory: no worker owns it, so this runs
                    // the deposit driver locally instead of relaying a command. It has to
                    // be handed to the client thread — chat output and the driver both
                    // expect it, and from a web thread the whole call dies silently.
                    var swarmMod = meteordevelopment.meteorclient.systems.modules.Modules.get()
                        .get(com.rdh.swarm.EnhancedSwarm.class);
                    if (swarmMod == null) {
                        logWarn("Self-deposit: swarm module not found");
                    } else {
                        net.minecraft.client.Minecraft.getInstance().execute(() -> {
                            boolean started = swarmMod.depositSelf("api");
                            logInfo(started ? "Self-deposit started (host inventory)" : "Self-deposit refused (see game log)");
                        });
                    }
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
                    // Scanning is a worker verb ("swarm:all scan" -> handleScan), not an
                    // exec chat command. This endpoint used to be a stub, so the panel
                    // could never populate the storage index that withdraw depends on.
                    host.sendAll("swarm:all scan");
                    logInfo("Scan Chests: requested a storage scan from all workers");
                }
                case "/api/action/deposit_push" -> {
                    // Re-push the chest list to every worker after the registry changed or a
                    // worker joined without receiving the connect-time push.
                    int seeded = host.adoptRegistryChests();
                    host.pushDepositConfigAll();
                    logInfo("Deposit Push: chest list sent to all workers (seeded " + seeded + " from registry)");
                }
                case "/api/chest/add" -> {
                    // One operator action: config + registry + push. Adding only to the
                    // config left adoption to erase the chest on the next push.
                    String xs = extractJson(body, "x");
                    String ys = extractJson(body, "y");
                    String zs = extractJson(body, "z");
                    String cat = extractJson(body, "category");
                    try {
                        int x = Integer.parseInt(xs.trim());
                        int y = Integer.parseInt(ys.trim());
                        int z = Integer.parseInt(zs.trim());
                        host.setDepositChest(cat == null || cat.isBlank() ? "all" : cat, x, y, z);
                        logInfo("Chest added: " + x + "," + y + "," + z + " category=" + cat + " -> " + host.depositConfigJson());
                    } catch (Exception e) {
                        logWarn("Chest add needs x,y,z ints (category optional)");
                    }
                }
                case "/api/chest/remove" -> {
                    String xs = extractJson(body, "x");
                    String ys = extractJson(body, "y");
                    String zs = extractJson(body, "z");
                    try {
                        int x = Integer.parseInt(xs.trim());
                        int y = Integer.parseInt(ys.trim());
                        int z = Integer.parseInt(zs.trim());
                        boolean removed = host.removeDepositChest(x, y, z);
                        logInfo("Chest remove " + x + "," + y + "," + z + " -> " + removed + " " + host.depositConfigJson());
                    } catch (Exception e) {
                        logWarn("Chest remove needs x,y,z ints");
                    }
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
        if (json == null || json.isBlank()) return "";
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return "";
            JsonElement value = root.getAsJsonObject().get(key);
            if (value == null || value.isJsonNull()) return "";
            return value.getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
