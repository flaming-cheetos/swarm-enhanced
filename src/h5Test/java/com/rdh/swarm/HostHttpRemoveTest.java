package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.web.SwarmWebServer;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Gate A HTTP probe: start the real host web server on loopback, POST a real
 * PENDING task id to /api/task/remove with the configured token, and verify
 * the route removed one row, preserved its siblings, and persisted the change.
 * Plain Java main; no test-framework dependency.
 */
public final class HostHttpRemoveTest {

    private static final String TOKEN = "host-http-remove-probe-token";
    private static final String TASK_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String TASK_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String TASK_C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private HostHttpRemoveTest() {}

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

            waitForPanel(webPort);
            String removeBody = "{\"id\":\"" + TASK_A + "\"}";
            HttpResponse<String> unauthorized = post(webPort, "/api/task/remove", removeBody, "wrong-token");
            check(unauthorized.statusCode() == 401,
                "wrong token was not rejected: HTTP " + unauthorized.statusCode());
            check(host.getTaskQueue().size() == 3,
                "unauthorized request changed the queue");

            HttpResponse<String> response = post(webPort, "/api/task/remove", removeBody, TOKEN);
            check(response.statusCode() == 200,
                "task/remove returned HTTP " + response.statusCode() + ": " + response.body());
            check(response.body().contains("\"ok\":true"),
                "task/remove did not return the panel ok envelope: " + response.body());

            TaskQueue live = host.getTaskQueue();
            check(find(live, TASK_A) == null, "HTTP remove left the PENDING row in memory");
            check(find(live, TASK_B) != null, "HTTP remove dropped sibling B");
            check(find(live, TASK_C) != null, "HTTP remove dropped sibling C");
            check(live.size() == 2, "HTTP remove changed the queue by more than one row");

            host.disconnect();
            host = null;

            TaskQueue reloaded = new TaskQueue();
            check(find(reloaded, TASK_A) == null, "HTTP remove did not persist");
            check(find(reloaded, TASK_B) != null, "sibling B did not persist");
            check(find(reloaded, TASK_C) != null, "sibling C did not persist");
            check(reloaded.size() == 2, "persisted queue has the wrong size after HTTP remove");

            System.out.println("PASS HostHttpRemoveTest: HTTP remove/persist/siblings");
        } finally {
            if (host != null) host.disconnect();
        }
    }

    private static void waitForPanel(int port) throws Exception {
        Exception last = null;
        for (int i = 0; i < 80; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/"))
                    .timeout(Duration.ofSeconds(2))
                    .header("X-Swarm-Token", TOKEN)
                    .GET()
                    .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
                last = new IllegalStateException("panel returned HTTP " + response.statusCode());
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("host web panel did not become ready: " + last, last);
    }

    private static HttpResponse<String> post(int port, String path, String body, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("X-Swarm-Token", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
                },
                {
                  "id": "%s",
                  "type": "GATHER",
                  "priority": 0,
                  "item": "",
                  "targetQuantity": 1,
                  "collected": 0,
                  "status": "PENDING",
                  "assignedBot": ""
                }
              ],
              "nicknames": {}
            }
            """.formatted(TASK_A, TASK_B, TASK_C));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static BotTask find(TaskQueue queue, String id) {
        UUID target = UUID.fromString(id);
        return queue.getAllTasks().stream()
            .filter(task -> task.getId().equals(target))
            .findFirst()
            .orElse(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
