package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * H5 Step 3 regression probe: stale workers cannot mutate a live lease, a
 * reconnect requeues and reclaims through the host claim path with a fresh
 * token, and a re-leased task completes exactly once. Plain Java main; no
 * test-framework dependency.
 */
public final class FencingReconnectTest {

    private static final String TASK_ID = "33333333-3333-3333-3333-333333333333";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private FencingReconnectTest() {}

    public static void main(String[] args) throws Exception {
        staleWorkerCannotMutateLiveLease();
        reconnectFreshLease();
        reconnectedLeaseCompletesExactlyOnce();
        legacyTokenFallbackOnlyWhileActiveOwner();
        System.out.println("PASS FencingReconnectTest: stale/reconnect/exactly-once");
    }

    private static void staleWorkerCannotMutateLiveLease() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask first = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 1000L, 5000L);
        check(first != null, "initial claim failed");
        String staleToken = first.getLeaseToken();
        check(!staleToken.isEmpty(), "initial claim had no lease token");

        check(EnhancedSwarmHost.prepareWorkerReconnect(queue, "jamal") == 1,
            "reconnect did not requeue the old assignment");
        BotTask live = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "chris", 3000L, 5000L);
        check(live != null, "new owner could not claim the requeued task");
        String liveToken = live.getLeaseToken();
        check(!liveToken.isEmpty(), "new owner had no lease token");
        check(!liveToken.equals(staleToken), "reconnect reused the stale lease token");

        boolean staleProgress = EnhancedSwarmHost.recordWorkerProgress(
            queue, live.getId(), "jamal", staleToken, 99, 4000L, 5000L);
        check(!staleProgress, "stale worker progress was accepted");
        check(live.getCollectedQuantity() == 0,
            "stale worker progress changed the live collected count");
        check(live.getLeaseExpiresAt() == 8000L,
            "stale worker progress extended the live lease");
        check("chris".equals(live.getAssignedBot()),
            "stale progress changed the live owner");
        check(liveToken.equals(live.getLeaseToken()),
            "stale progress changed the live lease token");
        check(live.getStatus() == BotTask.Status.ACTIVE,
            "stale progress changed the live status");

        boolean staleCompletion = EnhancedSwarmHost.finishTaskFromWorker(
            queue, live.getId(), "jamal", staleToken, BotTask.Status.COMPLETE, "stale");
        check(!staleCompletion, "stale worker completion was accepted");
        check(live.getStatus() == BotTask.Status.ACTIVE,
            "stale completion changed the live task status");
        check("chris".equals(live.getAssignedBot()),
            "stale completion changed the live owner");
        check(liveToken.equals(live.getLeaseToken()),
            "stale completion changed the live lease token");
        check(live.getCollectedQuantity() == 0,
            "stale completion changed the live collected count");

        boolean staleFailure = EnhancedSwarmHost.finishTaskFromWorker(
            queue, live.getId(), "jamal", staleToken, BotTask.Status.FAILED, "stale");
        check(!staleFailure, "stale worker failure was accepted");
        boolean staleCancellation = EnhancedSwarmHost.finishTaskFromWorker(
            queue, live.getId(), "jamal", staleToken, BotTask.Status.CANCELLED, "stale");
        check(!staleCancellation, "stale worker cancellation was accepted");
        check(live.getStatus() == BotTask.Status.ACTIVE,
            "stale terminal report changed the live task status");

        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, live.getId(), "chris", liveToken, BotTask.Status.COMPLETE, "done"),
            "live lease holder could not complete the task");
    }

    private static void reconnectFreshLease() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask original = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 1000L, 5000L);
        check(original != null, "initial claim failed");
        String oldToken = original.getLeaseToken();
        int oldAttempt = original.getAttempt();

        check(EnhancedSwarmHost.prepareWorkerReconnect(queue, "jamal") == 1,
            "reconnect did not requeue the old assignment");
        check(original.getStatus() == BotTask.Status.PENDING,
            "reconnect did not return the task to PENDING");
        check(original.getLeaseToken().isEmpty(),
            "reconnect did not clear the old lease token");
        check("worker-disconnect".equals(original.getStatusReason()),
            "reconnect did not record the worker-disconnect reason");

        BotTask renewed = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 2000L, 5000L);
        check(renewed != null, "reconnect could not reclaim the task");
        check(!renewed.getLeaseToken().isEmpty(), "reclaimed task had no lease token");
        check(!oldToken.equals(renewed.getLeaseToken()),
            "reconnect reused the old lease token");
        check(renewed.getAttempt() == oldAttempt + 2,
            "reconnect did not advance the attempt through requeue and claim");
        check(renewed.getLeaseExpiresAt() == 7000L,
            "reclaimed task did not receive a fresh expiry");
        check(renewed.getStatus() == BotTask.Status.ACTIVE,
            "reclaimed task was not ACTIVE");
        check("jamal".equals(renewed.getAssignedBot()),
            "reclaimed task did not belong to the reconnecting worker");

        // Same worker name, superseded token: only the token equality fence can
        // reject this old report. The owner name alone is not enough.
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, renewed.getId(), "jamal", oldToken, BotTask.Status.COMPLETE, "stale"),
            "superseded token completed the re-leased task");
        check(!EnhancedSwarmHost.recordWorkerProgress(
            queue, renewed.getId(), "jamal", oldToken, 42, 3000L, 5000L),
            "superseded token updated the re-leased task progress");
        check(renewed.getCollectedQuantity() == 0,
            "superseded token changed the re-leased collected count");
        check(renewed.getLeaseExpiresAt() == 7000L,
            "superseded token extended the re-leased expiry");
        check(renewed.getStatus() == BotTask.Status.ACTIVE,
            "superseded token changed the re-leased status");
    }

    private static void reconnectedLeaseCompletesExactlyOnce() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask first = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 1000L, 5000L);
        check(first != null, "initial claim failed");
        String oldToken = first.getLeaseToken();
        check(EnhancedSwarmHost.prepareWorkerReconnect(queue, "jamal") == 1,
            "reconnect did not requeue the task");
        BotTask reclaimed = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 2000L, 5000L);
        check(reclaimed != null, "reconnect could not re-lease the task");
        String token = reclaimed.getLeaseToken();
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, reclaimed.getId(), "jamal", oldToken, BotTask.Status.COMPLETE, "stale"),
            "superseded token completed the re-leased task");
        check(reclaimed.getStatus() == BotTask.Status.ACTIVE,
            "superseded token changed the re-leased task before completion");

        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, reclaimed.getId(), "jamal", token, BotTask.Status.COMPLETE, "done"),
            "re-leased task did not complete");
        boolean duplicate = EnhancedSwarmHost.finishTaskFromWorker(
            queue, reclaimed.getId(), "jamal", token, BotTask.Status.COMPLETE, "done-again");
        check(!duplicate, "duplicate completion was accepted");

        long completed = queue.getAllTasks().stream()
            .filter(task -> task.getStatus() == BotTask.Status.COMPLETE)
            .count();
        check(completed == 1, "task did not complete exactly once");
        check(reclaimed.getCollectedQuantity() == 0,
            "duplicate completion changed the completed task");
    }

    private static void legacyTokenFallbackOnlyWhileActiveOwner() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask first = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 1000L, 5000L);
        check(first != null, "initial claim failed");
        UUID taskId = first.getId();

        check(EnhancedSwarmHost.prepareWorkerReconnect(queue, "jamal") == 1,
            "reconnect did not requeue the legacy task");
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, taskId, "jamal", "", BotTask.Status.COMPLETE, "stale-legacy"),
            "legacy completion was accepted after the task was requeued");
        check(first.getStatus() == BotTask.Status.PENDING,
            "stale legacy completion mutated the requeued task");

        BotTask reclaimed = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 2000L, 5000L);
        check(reclaimed != null, "legacy task could not be reclaimed");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, taskId, "jamal", "", BotTask.Status.COMPLETE, "legacy-done"),
            "legacy completion was rejected while the owner was ACTIVE");
    }

    private static TaskQueue freshQueue() throws Exception {
        Files.createDirectories(CONFIG.getParent());
        Files.writeString(CONFIG, """
            {
              "tasks": [
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
            """.formatted(TASK_ID));
        return new TaskQueue();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
