package com.rdh.swarm.harness;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.rdh.swarm.BuildInfo;
import com.rdh.swarm.EnhancedSwarmAddon;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only, loopback HTTP bridge. It is inactive unless
 * config/enhanced-swarm-harness.json exists and is valid.
 */
public final class HarnessBridge {
    private static final Gson GSON = new Gson();
    private static final int CLIENT_TIMEOUT_SECONDS = 5;
    private static final int SCREENSHOT_TIMEOUT_SECONDS = 8;
    private static final long MAX_REQUEST_BODY = 16 * 1024;
    private static final int ACTION_SETTLE_TIMEOUT_MS = 2500;
    private static final long ACTION_POLL_MS = 50;

    private static volatile HarnessBridge instance;

    private final HarnessConfig config;
    private final byte[] tokenBytes;
    private final AtomicBoolean actionInFlight = new AtomicBoolean();
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean stopped;

    private HarnessBridge(HarnessConfig config) {
        this.config = config;
        this.tokenBytes = config.token.getBytes(StandardCharsets.UTF_8);
    }

    public static synchronized void startIfConfigured() {
        if (instance != null) return;

        Path configPath;
        try {
            configPath = FabricLoader.getInstance().getConfigDir().resolve(HarnessConfig.FILE_NAME);
        } catch (Throwable t) {
            EnhancedSwarmAddon.LOG.warn("Harness bridge config directory unavailable: {}", t.getMessage());
            return;
        }
        if (!Files.isRegularFile(configPath)) return;

        HarnessConfig config;
        try {
            config = HarnessConfig.load(configPath);
        } catch (Throwable t) {
            EnhancedSwarmAddon.LOG.warn("Harness bridge config rejected: {}", t.getMessage());
            return;
        }
        if (!config.enabled) return;

        HarnessBridge bridge = new HarnessBridge(config);
        try {
            bridge.start();
            instance = bridge;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(bridge::stop, "enhanced-swarm-harness-shutdown"));
            } catch (Throwable ignored) {
            }
            EnhancedSwarmAddon.LOG.info(
                "Harness bridge listening on http://127.0.0.1:{} ({})",
                config.port,
                config.allowActions ? "actions enabled" : "read-only"
            );
        } catch (Throwable t) {
            bridge.stop();
            EnhancedSwarmAddon.LOG.error("Harness bridge failed to start: {}", t.getMessage());
        }
    }

    private void start() throws IOException {
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "enhanced-swarm-harness-http");
            thread.setDaemon(true);
            return thread;
        });
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.port), 0);
        server.createContext("/harness", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isLoopback(exchange)) {
                sendError(exchange, 403, "remote-not-allowed", "harness bridge is loopback-only");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "unauthorized", "missing or invalid harness token");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equals(method)) {
                switch (path) {
                    case "/harness/health" -> sendJson(exchange, 200, health());
                    case "/harness/state" -> sendJson(exchange, 200, client(() -> HarnessSnapshots.state(Minecraft.getInstance())));
                    case "/harness/screen" -> sendJson(exchange, 200, client(() -> HarnessSnapshots.screen(Minecraft.getInstance())));
                    case "/harness/menu" -> sendJson(exchange, 200, client(() -> HarnessSnapshots.menu(Minecraft.getInstance())));
                    default -> sendError(exchange, 404, "not-found", "unknown harness endpoint");
                }
                return;
            }

            if ("POST".equals(method) && "/harness/menu/click".equals(path)) {
                sendMenuClick(exchange);
                return;
            }

            if ("POST".equals(method) && "/harness/screenshot".equals(path)) {
                String length = exchange.getRequestHeaders().getFirst("Content-Length");
                if (length != null) {
                    try {
                        if (Long.parseLong(length) > MAX_REQUEST_BODY) {
                            sendError(exchange, 413, "body-too-large", "request body is too large");
                            return;
                        }
                    } catch (NumberFormatException ignored) {
                        sendError(exchange, 400, "bad-length", "invalid Content-Length");
                        return;
                    }
                }
                sendScreenshot(exchange);
                return;
            }

            sendError(exchange, 405, "method-not-allowed", "unsupported method for harness endpoint");
        } catch (BridgeException e) {
            sendError(exchange, e.status, e.code, e.getMessage());
        } catch (Throwable t) {
            EnhancedSwarmAddon.LOG.error("Harness bridge request failed", t);
            sendError(exchange, 500, "internal-error", "harness bridge failed");
        } finally {
            exchange.close();
        }
    }

    private JsonObject health() {
        return client(() -> {
            JsonObject root = HarnessSnapshots.health(Minecraft.getInstance());
            root.addProperty("version", BuildInfo.stamp());
            return root;
        });
    }

    private void sendMenuClick(HttpExchange exchange) throws IOException {
        if (!config.allowActions) {
            throw new BridgeException(403, "actions-disabled", "actions are disabled; set allowActions=true in the harness config");
        }
        if (!actionInFlight.compareAndSet(false, true)) {
            throw new BridgeException(409, "action-in-flight", "another harness action is already in flight");
        }

        try {
            JsonObject body = readJsonBody(exchange);
            int slot = parseSlot(body);
            sendJson(exchange, 200, clickMenu(slot));
        } finally {
            actionInFlight.set(false);
        }
    }

    private JsonObject clickMenu(int slot) {
        JsonObject before = client(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                throw new BridgeException(409, "not-in-game", "no player/world is loaded");
            }
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
                throw new BridgeException(409, "menu-not-open", "no container menu is open");
            }
            if (mc.gameMode == null) {
                throw new BridgeException(503, "game-mode-unavailable", "client game mode is unavailable");
            }

            AbstractContainerMenu menu = mc.player.containerMenu;
            if (slot < 0 || slot >= menu.slots.size()) {
                throw new BridgeException(400, "invalid-slot", "slot is outside the open menu");
            }

            JsonObject snapshot = HarnessSnapshots.snapshot(mc);
            mc.gameMode.handleContainerInput(
                menu.containerId,
                slot,
                0,
                ContainerInput.QUICK_MOVE,
                mc.player
            );
            return snapshot;
        });

        JsonObject after = waitForMenuUpdate(before, slot);
        boolean changed = !slotItemSignature(before, slot).equals(slotItemSignature(after, slot));

        JsonObject root = HarnessSnapshots.ok();
        JsonObject result = new JsonObject();
        result.addProperty("action", "menu-click");
        result.addProperty("slot", slot);
        result.addProperty("input", "QUICK_MOVE");
        result.addProperty("button", 0);
        result.addProperty("changed", changed);

        JsonObject beforeMenu = before.getAsJsonObject("menu");
        if (beforeMenu != null) {
            if (beforeMenu.has("containerId")) result.addProperty("containerId", beforeMenu.get("containerId").getAsInt());
            if (beforeMenu.has("stateId")) result.addProperty("stateId", beforeMenu.get("stateId").getAsInt());
        }

        root.add("result", result);
        root.add("before", before);
        root.add("after", after);
        return root;
    }

    private JsonObject waitForMenuUpdate(JsonObject before, int slot) {
        String original = slotItemSignature(before, slot);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ACTION_SETTLE_TIMEOUT_MS);
        JsonObject latest = before;

        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(ACTION_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            latest = client(() -> HarnessSnapshots.snapshot(Minecraft.getInstance()));
            if (!original.equals(slotItemSignature(latest, slot))) return latest;
        }
        return latest;
    }

    private static String slotItemSignature(JsonObject snapshot, int slot) {
        if (snapshot == null) return "";
        JsonObject menu = snapshot.getAsJsonObject("menu");
        if (menu == null || !menu.has("slots")) return "";

        JsonElement slotsElement = menu.get("slots");
        if (!slotsElement.isJsonArray()) return "";
        JsonArray slots = slotsElement.getAsJsonArray();
        if (slot < 0 || slot >= slots.size()) return "";

        JsonElement item = slots.get(slot).getAsJsonObject().get("item");
        return item == null || item.isJsonNull() ? "<empty>" : item.toString();
    }

    private void sendScreenshot(HttpExchange exchange) throws IOException {
        byte[] png;
        try {
            png = screenshot();
        } catch (BridgeException e) {
            sendError(exchange, e.status, e.code, e.getMessage());
            return;
        } catch (Throwable t) {
            EnhancedSwarmAddon.LOG.error("Harness screenshot failed", t);
            sendError(exchange, 500, "screenshot-failed", "screenshot failed");
            return;
        }

        exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"harness.png\"");
        sendBytes(exchange, 200, png, "image/png");
    }

    private byte[] screenshot() {
        CompletableFuture<byte[]> capture = new CompletableFuture<>();

        client(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                throw new BridgeException(409, "not-in-game", "no player/world is loaded");
            }
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            if (target == null) {
                throw new BridgeException(503, "no-render-target", "main render target is unavailable");
            }

            Screenshot.takeScreenshot(target, image -> {
                Path temp = null;
                try {
                    temp = Files.createTempFile("enhanced-swarm-harness-", ".png");
                    image.writeToFile(temp.toFile());
                    capture.complete(Files.readAllBytes(temp));
                } catch (Throwable t) {
                    capture.completeExceptionally(t);
                } finally {
                    if (temp != null) {
                        try {
                            Files.deleteIfExists(temp);
                        } catch (IOException ignored) {
                        }
                    }
                    try {
                        image.close();
                    } catch (Throwable ignored) {
                    }
                }
            });
            return null;
        });

        try {
            return capture.get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BridgeException(503, "screenshot-timeout", "screenshot did not complete in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BridgeException(503, "screenshot-interrupted", "screenshot was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BridgeException be) throw be;
            throw new BridgeException(500, "screenshot-failed", "screenshot failed");
        }
    }

    private static <T> T client(Callable<T> callable) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            throw new BridgeException(503, "client-unavailable", "Minecraft client is not available");
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            mc.execute(() -> {
                try {
                    result.complete(callable.call());
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            throw new BridgeException(503, "client-busy", "could not schedule work on the client thread");
        }

        try {
            return result.get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BridgeException(503, "client-thread-timeout", "client thread did not answer in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BridgeException(503, "client-thread-interrupted", "client thread request was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BridgeException be) throw be;
            throw new BridgeException(500, "client-error", "client thread request failed");
        }
    }

    private boolean isLoopback(HttpExchange exchange) {
        try {
            InetAddress address = exchange.getRemoteAddress().getAddress();
            return address != null && address.isLoopbackAddress();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("X-Harness-Token");
        if (supplied == null) supplied = queryToken(exchange.getRequestURI().getRawQuery());
        if (supplied == null) {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                supplied = authorization.substring("Bearer ".length()).trim();
            }
        }
        if (supplied == null) return false;
        return MessageDigest.isEqual(tokenBytes, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static String queryToken(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;
            if (!"token".equals(pair.substring(0, separator))) continue;
            try {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static JsonObject readJsonBody(HttpExchange exchange) throws IOException {
        int limit = (int) MAX_REQUEST_BODY + 1;
        byte[] bytes = exchange.getRequestBody().readNBytes(limit);
        if (bytes.length > MAX_REQUEST_BODY) {
            throw new BridgeException(413, "body-too-large", "request body is too large");
        }
        if (bytes.length == 0) {
            throw new BridgeException(400, "empty-body", "request body must be a JSON object");
        }

        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new BridgeException(400, "invalid-request", "request body must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (BridgeException e) {
            throw e;
        } catch (Throwable t) {
            throw new BridgeException(400, "invalid-json", "request body is not valid JSON");
        }
    }

    private static int parseSlot(JsonObject body) {
        if (body.size() != 1 || !body.has("slot")) {
            throw new BridgeException(400, "invalid-request", "request body must contain only the slot field");
        }

        JsonElement slotElement = body.get("slot");
        if (slotElement == null || !slotElement.isJsonPrimitive() || !slotElement.getAsJsonPrimitive().isNumber()) {
            throw new BridgeException(400, "invalid-slot", "slot must be an integer");
        }

        double raw = slotElement.getAsDouble();
        int slot = (int) raw;
        if (raw != slot) {
            throw new BridgeException(400, "invalid-slot", "slot must be an integer");
        }
        return slot;
    }

    private void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        sendBytes(exchange, status, GSON.toJson(body).getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void sendError(HttpExchange exchange, int status, String code, String message) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("ok", false);
            body.addProperty("code", code);
            body.addProperty("error", message);
            sendJson(exchange, status, body);
        } catch (Throwable ignored) {
        }
    }

    private void sendBytes(HttpExchange exchange, int status, byte[] body) throws IOException {
        sendBytes(exchange, status, body, "application/octet-stream");
    }

    private void sendBytes(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static final class BridgeException extends RuntimeException {
        final int status;
        final String code;

        BridgeException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
