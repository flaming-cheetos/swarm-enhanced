package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * H5 protocol compatibility matrix. Expected outcome for every cell:
 *
 * <pre>
 * host / worker                         progress       completion     failure / cancel
 * new host + new worker (token)         accepted       accepted       accepted
 * new host + old worker (no token)      accepted while accepted while  accepted while
 *                                       owner is ACTIVE owner is ACTIVE owner is ACTIVE
 * old host + new worker (no lease)      accepted       accepted       accepted
 * missing token, active legacy worker   accepted       accepted       accepted
 * stale token after reconnect/restart   rejected       rejected       rejected
 * </pre>
 *
 * "Old host" is represented by an ACTIVE row with no fencing token; the new
 * worker omits the token because the assignment had none. A stale token is one
 * that belonged to a previous attempt after requeue/recovery.
 */
public final class ProtocolMatrixTest {
    private static final String TASK_ID = "66666666-6666-6666-6666-666666666666";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private ProtocolMatrixTest() {}

    public static void main(String[] args) throws Exception {
        newHostNewWorkerRoundTrip();
        newHostOldWorkerFallback();
        oldHostNewWorkerLegacyShape();
        missingTokenFromActiveLegacyWorker();
        staleTokenAfterRequeue();
        hostRestartRecovery();
        terminalMatrix();
        progressDirections();
        System.out.println("PASS ProtocolMatrixTest: old/new matrix covered");
    }

    private static void newHostNewWorkerRoundTrip() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = claim(queue, "jamal", 1000L);
        String token = task.getLeaseToken();
        check(!token.isEmpty(), "new host did not issue a token");
        check(events(queue).contains("TASK: claimed " + task.getId() + " by jamal attempt=1"),
            "claim anchor did not fire");

        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", token, 1, 1500L, 5000L),
            "new host/new worker progress was rejected");
        check(events(queue).contains("TASK: lease renewed " + task.getId() + " attempt=1"),
            "renewal anchor did not fire");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", token, BotTask.Status.COMPLETE, "completed"),
            "new host/new worker completion was rejected");
        check(task.getStatus() == BotTask.Status.COMPLETE, "new/new task did not complete");
    }

    private static void newHostOldWorkerFallback() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = claim(queue, "jamal", 1000L);
        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", "", 1, 1500L, 5000L),
            "old worker progress was rejected while ACTIVE");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", "", BotTask.Status.COMPLETE, "legacy-complete"),
            "old worker completion was rejected while ACTIVE");
        check(task.getStatus() == BotTask.Status.COMPLETE, "legacy task did not complete");
    }

    private static void oldHostNewWorkerLegacyShape() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = claim(queue, "jamal", 1000L);
        // An old host has no token field; the new worker sees an assignment
        // without a token and emits the legacy no-token report shape.
        task.setLeaseToken("");
        task.setLeaseExpiresAt(6000L);
        task.setLastHeartbeatAt(1000L);
        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", "", 1, 1500L, 5000L),
            "old-host/new-worker progress was rejected");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", "", BotTask.Status.COMPLETE, "legacy-host"),
            "old-host/new-worker completion was rejected");
        check(task.getStatus() == BotTask.Status.COMPLETE, "legacy-host task did not complete");
    }

    private static void missingTokenFromActiveLegacyWorker() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = claim(queue, "jamal", 1000L);
        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", "", 1, 1500L, 5000L),
            "missing-token progress was rejected for the active owner");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", "", BotTask.Status.CANCELLED, "legacy-cancel"),
            "missing-token cancellation was rejected for the active owner");
        check(task.getStatus() == BotTask.Status.CANCELLED, "legacy cancel did not apply");
    }

    private static void staleTokenAfterRequeue() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask old = claim(queue, "jamal", 1000L);
        String staleToken = old.getLeaseToken();
        check(EnhancedSwarmHost.prepareWorkerReconnect(queue, "jamal") == 1,
            "reconnect did not requeue");
        check(old.getStatus() == BotTask.Status.PENDING,
            "reconnect did not return the row to PENDING");
        List<String> reconnectEvents = events(queue);
        check(reconnectEvents.contains("TASK: requeued " + old.getId() + " reason=worker-disconnect"),
            "worker-disconnect requeue anchor did not fire");
        check(reconnectEvents.contains("Requeued 1 active task(s) from jamal"),
            "reconnect summary anchor did not fire");

        check(!EnhancedSwarmHost.recordWorkerProgress(
            queue, old.getId(), "jamal", staleToken, 2, 2000L, 5000L),
            "stale progress after reconnect was accepted");
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, old.getId(), "jamal", staleToken, BotTask.Status.COMPLETE, "stale"),
            "stale completion after reconnect was accepted");
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, old.getId(), "jamal", staleToken, BotTask.Status.FAILED, "stale"),
            "stale failure after reconnect was accepted");
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, old.getId(), "jamal", staleToken, BotTask.Status.CANCELLED, "stale"),
            "stale cancellation after reconnect was accepted");
        check(old.getStatus() == BotTask.Status.PENDING,
            "stale reports mutated the requeued row");

        BotTask fresh = claim(queue, "jamal", 2000L);
        check(fresh != null && !fresh.getLeaseToken().equals(staleToken),
            "reconnect did not issue a fresh token");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, fresh.getId(), "jamal", fresh.getLeaseToken(), BotTask.Status.COMPLETE, "fresh"),
            "fresh lease could not complete after reconnect");
    }

    private static void hostRestartRecovery() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask old = claim(queue, "jamal", 1000L);
        String staleToken = old.getLeaseToken();
        check(queue.recoverActiveAfterHostRestart() == 1,
            "host restart did not recover the ACTIVE row");
        check(old.getStatus() == BotTask.Status.PENDING,
            "host restart did not return the row to PENDING");
        check("host-restart".equals(old.getStatusReason()),
            "host restart did not record the reason");
        check(queue.getLastRecoveryCount() == 1,
            "host restart did not expose the recovery count");
        List<String> restartEvents = events(queue);
        check(restartEvents.contains("TASK: requeued " + old.getId() + " reason=host-restart"),
            "host-restart requeue anchor did not fire");
        check(restartEvents.contains("Host restart recovery: requeued 1 active task(s)"),
            "host restart summary anchor did not fire");
        check(!EnhancedSwarmHost.finishTaskFromWorker(
            queue, old.getId(), "jamal", staleToken, BotTask.Status.COMPLETE, "stale"),
            "stale completion after host restart was accepted");
        BotTask fresh = claim(queue, "jamal", 3000L);
        check(fresh != null, "recovered row could not be reclaimed");
        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, fresh.getId(), "jamal", fresh.getLeaseToken(), BotTask.Status.COMPLETE, "fresh"),
            "recovered row could not complete under the fresh lease");
    }

    private static void terminalMatrix() throws Exception {
        for (BotTask.Status terminal : new BotTask.Status[]{
            BotTask.Status.COMPLETE, BotTask.Status.FAILED, BotTask.Status.CANCELLED}) {
            TaskQueue newNew = freshQueue();
            BotTask nn = claim(newNew, "jamal", 1000L);
            check(EnhancedSwarmHost.finishTaskFromWorker(
                newNew, nn.getId(), "jamal", nn.getLeaseToken(), terminal, "new-new"),
                "new/new " + terminal + " was rejected");

            TaskQueue newOld = freshQueue();
            BotTask no = claim(newOld, "jamal", 1000L);
            check(EnhancedSwarmHost.finishTaskFromWorker(
                newOld, no.getId(), "jamal", "", terminal, "new-old"),
                "new/old " + terminal + " was rejected");

            TaskQueue oldNew = freshQueue();
            BotTask on = claim(oldNew, "jamal", 1000L);
            on.setLeaseToken("");
            on.setLeaseExpiresAt(6000L);
            check(EnhancedSwarmHost.finishTaskFromWorker(
                oldNew, on.getId(), "jamal", "", terminal, "old-new"),
                "old/new " + terminal + " was rejected");
            check(on.getStatus() == terminal, "old/new " + terminal + " did not apply");
        }
    }

    private static void progressDirections() throws Exception {
        TaskQueue newNew = freshQueue();
        BotTask nn = claim(newNew, "jamal", 1000L);
        check(EnhancedSwarmHost.recordWorkerProgress(
            newNew, nn.getId(), "jamal", nn.getLeaseToken(), 1, 1500L, 5000L),
            "new/new progress was rejected");

        TaskQueue oldNew = freshQueue();
        BotTask on = claim(oldNew, "jamal", 1000L);
        on.setLeaseToken("");
        on.setLeaseExpiresAt(6000L);
        check(EnhancedSwarmHost.recordWorkerProgress(
            oldNew, on.getId(), "jamal", "", 1, 1500L, 5000L),
            "old/new progress was rejected");
    }

    private static BotTask claim(TaskQueue queue, String bot, long now) {
        BotTask task = EnhancedSwarmHost.claimNextTaskForDispatch(queue, bot, now, 5000L);
        check(task != null, "claim failed for " + bot + " at " + now);
        return task;
    }

    private static List<String> events(TaskQueue queue) {
        return queue.drainEvents();
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
                  "targetQuantity": 2,
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
