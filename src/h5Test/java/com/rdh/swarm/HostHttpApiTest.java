package com.rdh.swarm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.web.SwarmWebServer;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Gate A HTTP surface probe: one real EnhancedSwarmHost/Netty listener,
 * one real TaskQueue loaded from disk, and an actual request for every
 * mod API route the panel/MCP specs document. The only assertions on a
 * 404 are the three GET-only design sketches in MCP_SERVER_SPEC §6.1-6.3;
 * those are recorded as blocked rather than treated as success.
 */
public final class HostHttpApiTest {

    private static final String TOKEN = "host-http-api-probe-token";
    private static final String TASK_A = "11111111-1111-4111-8111-111111111111";
    private static final String TASK_B = "22222222-2222-4222-8222-222222222222";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    private HostHttpApiTest() {}

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void run() throws Exception {
        seedQueue();
        int tcpPort = freePort();
        int webPort = freePort();
        EnhancedSwarmHost host = null;
        try {
            host = new EnhancedSwarmHost("127.0.0.1", tcpPort, webPort, TOKEN);
            SwarmWebServer web = host.getWebServer();
            if (web == null) throw new IllegalStateException("host did not create its web server");
            web.bindHostForProbe(host);
            MeteorExecutor.init();

            waitForPanel(webPort);
            assetsAndAuth(webPort);
            websocketFeed(webPort);
            taskRoutes(host, webPort);
            actionRoutes(host, webPort);
            chestRoutes(host, webPort);
            debugRoutes(host, webPort);
            documentedMissingRoutes(webPort);
            clearRoute(host, webPort);

            System.out.println("PASS HostHttpApiTest: assets/auth/ws/tasks/actions/chests/debug");
        } finally {
            if (host != null) host.disconnect();
        }
    }

    private static void assetsAndAuth(int port) throws Exception {
        HttpResponse<String> home = get(port, "/", TOKEN);
        check(home.statusCode() == 200, "GET / -> " + home.statusCode());
        check(home.body().contains("<html"), "GET / did not return HTML");
        check(home.headers().firstValue("set-cookie").orElse("").contains("swarm_token="),
            "GET / did not set the panel cookie");

        check(get(port, "/", null).statusCode() == 401, "GET / without token was not 401");
        check(get(port, "/", "wrong-token").statusCode() == 401, "GET / with a wrong token was not 401");
        check(get(port, "/app.js", TOKEN).statusCode() == 200, "GET /app.js failed");
        check(get(port, "/v2", TOKEN).statusCode() == 200, "GET /v2 failed");
        check(get(port, "/v2/", TOKEN).statusCode() == 200, "GET /v2/ failed");
        check(get(port, "/v2/app.css", TOKEN).statusCode() == 200, "GET /v2/app.css failed");
        check(get(port, "/v2/app.js", TOKEN).statusCode() == 200, "GET /v2/app.js failed");

        HttpResponse<String> items = get(port, "/api/items", TOKEN);
        check(items.statusCode() == 200, "GET /api/items -> " + items.statusCode());
        check(json(items).has("items"), "GET /api/items has no items array");
        check(get(port, "/api/items?token=" + TOKEN, null).statusCode() == 200,
            "query-token auth failed");
        check(getWithCookie(port, "/api/items").statusCode() == 200,
            "cookie auth failed");

        HttpResponse<String> foreign = post(port, "/api/task/clear_done", "{}", TOKEN,
            "http://evil.example");
        check(foreign.statusCode() == 403, "foreign Origin was not rejected: " + foreign.statusCode());

        HttpResponse<String> noToken = post(port, "/api/task/clear_done", "{}", null, null);
        check(noToken.statusCode() == 401, "POST without token was not 401");
    }

    private static void websocketFeed(int port) throws Exception {
        FrameListener listener = new FrameListener();
        WebSocket ws = CLIENT.newWebSocketBuilder()
            .header("X-Swarm-Token", TOKEN)
            .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), listener)
            .get(5, TimeUnit.SECONDS);
        try {
            String state = listener.state.get(5, TimeUnit.SECONDS);
            String logs = listener.logs.get(5, TimeUnit.SECONDS);
            check(state.contains("\"type\":\"state\""), "WS did not send panel state");
            check(json(state).getAsJsonArray("tasks").size() == 2,
                "WS state did not contain the seeded queue");
            check(logs.contains("\"type\":\"log_history\""), "WS did not send log history");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "probe").get(5, TimeUnit.SECONDS);
        }
    }

    private static void taskRoutes(EnhancedSwarmHost host, int port) throws Exception {
        TaskQueue queue = host.getTaskQueue();
        expectOk(post(port, "/api/task/add",
            "{\"item\":\"\",\"qty\":1,\"type\":\"GATHER\"}", TOKEN, null));
        check(queue.size() == 3, "task add did not append one row");
        check(new TaskQueue().size() == 3, "task add did not persist");

        UUID added = onlyThird(queue);
        BotTask addedTask = find(queue, added);
        check(addedTask != null, "added task vanished");
        addedTask.setStatus(BotTask.Status.COMPLETE);
        expectOk(post(port, "/api/task/clear_done", "{}", TOKEN, null));
        check(queue.size() == 2, "clear_done did not drop the completed row");
        check(new TaskQueue().size() == 2, "clear_done did not persist");

        expectOk(post(port, "/api/task/add",
            "{\"item\":\"\",\"qty\":1,\"type\":\"GATHER\"}", TOKEN, null));
        UUID removable = onlyThird(queue);
        expectOk(post(port, "/api/task/remove", "{\"id\":\"" + removable + "\"}", TOKEN, null));
        check(queue.size() == 2, "task remove did not drop the added row");
        check(new TaskQueue().size() == 2, "task remove did not persist");

        expectOk(post(port, "/api/task/add",
            "{\"item\":\"\",\"qty\":1,\"type\":\"GATHER\"}", TOKEN, null));
        UUID cancellable = onlyThird(queue);
        expectOk(post(port, "/api/task/cancel", "{\"id\":\"" + cancellable + "\"}", TOKEN, null));
        check(queue.size() == 2, "task cancel did not drop the row");
        check(new TaskQueue().size() == 2, "task cancel did not persist");

        expectOk(post(port, "/api/task/pause", "{\"bot\":\"jamal\"}", TOKEN, null));
        expectOk(post(port, "/api/task/resume", "{\"bot\":\"jamal\"}", TOKEN, null));
        check(queue.size() == 2, "pause/resume changed the queue");
        check(queue.getAllTasks().stream().allMatch(t -> t.getStatus() == BotTask.Status.PENDING),
            "resume leased work to an offline worker");

        expectOk(post(port, "/api/nickname",
            "{\"username\":\"jamal\",\"nickname\":\"J\"}", TOKEN, null));
        check("J".equals(queue.getNickname("jamal")), "nickname not stored in memory");
        check("J".equals(new TaskQueue().getNickname("jamal")), "nickname not persisted");
    }

    private static void actionRoutes(EnhancedSwarmHost host, int port) throws Exception {
        String[] actions = {
            "/api/action/follow",
            "/api/action/cancel",
            "/api/action/stop",
            "/api/action/resume",
            "/api/action/deposit",
            "/api/action/deposit_ores",
            "/api/action/deposit_shulkers",
            "/api/action/pvp_on",
            "/api/action/pvp_off",
            "/api/action/scan_chests",
            "/api/action/deposit_push",
            "/api/action/test_give",
            "/api/action/deposit_self"
        };
        for (String action : actions) {
            HttpResponse<String> response = action.equals("/api/action/test_give")
                ? post(port, action, "{\"item\":\"minecraft:stone\",\"count\":1}", TOKEN, null)
                : post(port, action, "{}", TOKEN, null);
            expectOk(response);
        }
        check(host.getTaskQueue().size() == 2, "action routes changed the queue");

        HttpResponse<String> fuel = post(port, "/api/action/check_fuel", "{}", TOKEN, null);
        check(fuel.statusCode() == 501, "check_fuel -> " + fuel.statusCode());
        check(fuel.body().contains("check_fuel"), "check_fuel body did not name the limitation");
    }

    private static void chestRoutes(EnhancedSwarmHost host, int port) throws Exception {
        expectOk(post(port, "/api/chest/add",
            "{\"x\":10,\"y\":64,\"z\":10,\"category\":\"probe\"}", TOKEN, null));
        JsonObject deposit = expectOk(post(port, "/api/debug/deposit", "{}", TOKEN, null));
        check(deposit.has("chests"), "debug deposit has no chests object");
        check(hasChest(deposit, "probe", 10, 64, 10), "chest add did not reach deposit config");

        expectOk(post(port, "/api/chest/remove",
            "{\"x\":10,\"y\":64,\"z\":10}", TOKEN, null));
        deposit = expectOk(post(port, "/api/debug/deposit", "{}", TOKEN, null));
        check(!hasChest(deposit, "probe", 10, 64, 10), "chest remove left the coordinate behind");
    }

    private static void debugRoutes(EnhancedSwarmHost host, int port) throws Exception {
        Files.createDirectories(Path.of("logs"));
        Files.writeString(Path.of("logs", "latest.log"), "line-one\nline-two\n");

        JsonObject state = json(post(port, "/api/debug/state", "{}", TOKEN, null));
        check("state".equals(state.get("type").getAsString()), "debug state has wrong type");
        check(state.getAsJsonArray("tasks").size() == 2, "debug state lost the queue");

        JsonObject bots = expectOk(post(port, "/api/debug/bot", "{}", TOKEN, null));
        check(bots.getAsJsonObject("bots").isEmpty(), "debug bot listed a worker that is not connected");
        HttpResponse<String> noState = post(port, "/api/debug/bot",
            "{\"bot\":\"jamal\"}", TOKEN, null);
        check(noState.statusCode() == 200, "debug bot with a name failed");
        check("no_state_yet".equals(json(noState).get("error").getAsString()),
            "debug bot did not report the missing worker state");

        JsonObject health = expectOk(post(port, "/api/debug/health", "{}", TOKEN, null));
        check(health.get("isHost").getAsBoolean(), "debug health did not report the bound host");
        check(health.get("pid").getAsLong() > 0, "debug health has no pid");

        JsonObject logs = expectOk(post(port, "/api/debug/logs", "{\"lines\":1}", TOKEN, null));
        check(logs.get("totalLines").getAsInt() == 2, "debug logs did not read the log");
        check(logs.getAsJsonArray("lines").size() == 1, "debug logs did not honor lines=1");

        JsonObject tasks = expectOk(post(port, "/api/debug/tasks", "{}", TOKEN, null));
        check(tasks.get("size").getAsInt() == 2, "debug tasks size mismatch");

        expectOk(post(port, "/api/debug/deposit", "{}", TOKEN, null));
        JsonObject chestMissing = json(post(port, "/api/debug/chest", "{}", TOKEN, null));
        check("bot_and_id_required".equals(chestMissing.get("error").getAsString()),
            "debug chest accepted an empty request");
        JsonObject chestUnknown = json(post(port, "/api/debug/chest",
            "{\"bot\":\"jamal\",\"id\":\"missing\"}", TOKEN, null));
        check("unknown_id".equals(chestUnknown.get("error").getAsString()),
            "debug chest did not reject an unknown id");

        JsonObject registry = expectOk(post(port, "/api/debug/registry", "{}", TOKEN, null));
        check(registry.get("count").getAsInt() > 0, "debug registry is empty");

        JsonObject storage = expectOk(post(port, "/api/debug/storage", "{}", TOKEN, null));
        check(storage.has("totals"), "debug storage has no totals");

        JsonObject worldMissing = json(post(port, "/api/debug/world", "{}", TOKEN, null));
        check("bot_required".equals(worldMissing.get("error").getAsString()),
            "debug world accepted an empty request");
        JsonObject worldWaiting = json(post(port, "/api/debug/world",
            "{\"bot\":\"jamal\"}", TOKEN, null));
        check("waiting".equals(worldWaiting.get("error").getAsString()),
            "debug world did not wait for a worker");

        JsonObject panic = expectOk(post(port, "/api/debug/panic", "{}", TOKEN, null));
        check(panic.get("panicked").getAsBoolean(), "panic did not set the latch");
        check(host.getTaskQueue().size() == 0, "panic did not clear the queue");
        JsonObject resume = expectOk(post(port, "/api/debug/resume", "{}", TOKEN, null));
        check(!resume.get("panicked").getAsBoolean(), "resume did not clear the latch");
    }

    private static void documentedMissingRoutes(int port) throws Exception {
        HttpResponse<String> botGet = get(port, "/api/debug/bot?bot=jamal", TOKEN);
        check(botGet.statusCode() == 404, "GET debug/bot should be the unimplemented GET sketch");
        System.out.println("BLOCKED GET /api/debug/bot?bot=jamal -> " + botGet.statusCode()
            + " (implemented as POST /api/debug/bot)");

        HttpResponse<String> traceGet = get(port, "/api/debug/trace?bot=jamal&n=20", TOKEN);
        check(traceGet.statusCode() == 404, "GET debug/trace should be the unimplemented GET sketch");
        System.out.println("BLOCKED GET /api/debug/trace?bot=jamal&n=20 -> " + traceGet.statusCode()
            + " (no worker decision-trace route exists)");

        HttpResponse<String> containerGet = get(port, "/api/debug/world/container?x=1&y=2&z=3", TOKEN);
        check(containerGet.statusCode() == 404,
            "GET debug/world/container should be the unimplemented GET sketch");
        System.out.println("BLOCKED GET /api/debug/world/container?x=1&y=2&z=3 -> "
            + containerGet.statusCode() + " (use POST /api/debug/world)");
    }

    private static void clearRoute(EnhancedSwarmHost host, int port) throws Exception {
        expectOk(post(port, "/api/task/add",
            "{\"item\":\"\",\"qty\":1,\"type\":\"GATHER\"}", TOKEN, null));
        check(host.getTaskQueue().size() == 1, "could not seed a row before clear");
        expectOk(post(port, "/api/task/clear", "{}", TOKEN, null));
        check(host.getTaskQueue().size() == 0, "task clear left rows behind");
        check(new TaskQueue().size() == 0, "task clear did not persist");
    }

    private static void waitForPanel(int port) throws Exception {
        Exception last = null;
        for (int i = 0; i < 80; i++) {
            try {
                if (get(port, "/", TOKEN).statusCode() == 200) return;
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("host web panel did not become ready: " + last, last);
    }

    private static HttpResponse<String> get(int port, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .GET();
        if (token != null) builder.header("X-Swarm-Token", token);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getWithCookie(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Cookie", "swarm_token=" + TOKEN)
            .GET()
            .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(int port, String path, String body,
                                             String token, String origin) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("X-Swarm-Token", token);
        if (origin != null) builder.header("Origin", origin);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject expectOk(HttpResponse<String> response) {
        check(response.statusCode() == 200,
            "expected 200, got " + response.statusCode() + ": " + response.body());
        JsonObject object = json(response);
        check(object.has("ok") && object.get("ok").getAsBoolean(),
            "expected ok:true, got " + response.body());
        return object;
    }

    private static JsonObject json(HttpResponse<String> response) {
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("not a JSON object: " + response.body(), e);
        }
    }

    private static JsonObject json(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("not a JSON object: " + body, e);
        }
    }

    private static boolean hasChest(JsonObject deposit, String category, int x, int y, int z) {
        JsonObject chests = deposit.getAsJsonObject("chests");
        if (chests == null || !chests.has(category)) return false;
        JsonArray list = chests.getAsJsonArray(category);
        for (var element : list) {
            JsonArray p = element.getAsJsonArray();
            if (p.get(0).getAsInt() == x && p.get(1).getAsInt() == y && p.get(2).getAsInt() == z) {
                return true;
            }
        }
        return false;
    }

    private static UUID onlyThird(TaskQueue queue) {
        return queue.getAllTasks().stream()
            .map(BotTask::getId)
            .filter(id -> !id.toString().equals(TASK_A) && !id.toString().equals(TASK_B))
            .findFirst()
            .orElseThrow(() -> new AssertionError("new task was not queued"));
    }

    private static BotTask find(TaskQueue queue, UUID id) {
        return queue.getAllTasks().stream()
            .filter(task -> task.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    private static void seedQueue() throws Exception {
        Files.createDirectories(CONFIG.getParent());
        Files.writeString(CONFIG, """
            {
              "tasks": [
                {
                  "id": "%s",
                  "type": "GATHER",
                  "priority": 0,
                  "item": "",
                  "targetQuantity": 3,
                  "collected": 0,
                  "status": "PENDING",
                  "assignedBot": ""
                },
                {
                  "id": "%s",
                  "type": "GATHER",
                  "priority": 0,
                  "item": "",
                  "targetQuantity": 2,
                  "collected": 0,
                  "status": "PENDING",
                  "assignedBot": ""
                }
              ],
              "nicknames": {}
            }
            """.formatted(TASK_A, TASK_B));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FrameListener implements WebSocket.Listener {
        final CompletableFuture<String> state = new CompletableFuture<>();
        final CompletableFuture<String> logs = new CompletableFuture<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                if (message.contains("\"type\":\"state\"")) state.complete(message);
                if (message.contains("\"type\":\"log_history\"")) logs.complete(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            state.completeExceptionally(error);
            logs.completeExceptionally(error);
        }
    }
}
