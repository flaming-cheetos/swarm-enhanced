package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * H5 lease-grace regression probe: a worker whose lease lapsed while it was
 * still working may re-attach only with its old token, only inside the grace
 * window. A re-claimed task must fence that old token even inside the window.
 * Plain Java main; no test-framework dependency.
 */
public final class LeaseGraceTest {

    private static final String TASK_ID = "55555555-5555-5555-5555-555555555555";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private LeaseGraceTest() {}

    public static void main(String[] args) throws Exception {
        progressReattachesInsideGrace();
        progressAtGraceBoundaryAccepted();
        progressAfterGraceRejected();
        reclaimedLeaseFencesOldToken();
        completionReattachesInsideGrace();
        System.out.println("PASS LeaseGraceTest: grace re-attach/boundary/fence");
    }

    private static void progressReattachesInsideGrace() throws Exception {
        Claimed claim = expiredClaim();
        long now = 61_000L;
        int lapsedFor = (int) (now - claim.task().getLeaseLapsedAt());
        check(lapsedFor == 59_000, "test setup did not lapse the lease as expected");
        check(lapsedFor <= TaskQueue.LEASE_GRACE_MS, "test setup exceeded the grace window");

        check(claim.queue().recordProgressIfLeaseMatches(
            claim.task().getId(), "jamal", claim.token(), 5, now, 5_000L),
            "late progress inside the grace window was rejected");
        check(claim.task().getStatus() == BotTask.Status.ACTIVE,
            "re-attached task did not return to ACTIVE");
        check("jamal".equals(claim.task().getAssignedBot()),
            "re-attached task lost its original owner");
        check(claim.token().equals(claim.task().getLeaseToken()),
            "re-attached task did not retain the worker token");
        check(claim.task().getCollectedQuantity() == 5,
            "re-attached progress was not applied");
        check(claim.task().getLeaseExpiresAt() == 66_000L,
            "re-attached lease did not receive a fresh expiry");
        check("lease-re-attached".equals(claim.task().getStatusReason()),
            "re-attached lease did not record its reason");
        check(claim.queue().drainEvents().stream()
                .anyMatch(event -> event.contains("lease re-attached")),
            "re-attach was not emitted to the panel event stream");
    }

    private static void progressAtGraceBoundaryAccepted() throws Exception {
        Claimed claim = expiredClaim();
        long now = 62_000L;
        check(now - claim.task().getLeaseLapsedAt() == TaskQueue.LEASE_GRACE_MS,
            "test setup did not reach the exact grace boundary");

        check(claim.queue().recordProgressIfLeaseMatches(
            claim.task().getId(), "jamal", claim.token(), 3, now, 5_000L),
            "progress at the exact grace boundary was rejected");
        check(claim.task().getStatus() == BotTask.Status.ACTIVE,
            "boundary re-attach did not restore ACTIVE");
        check(claim.task().getCollectedQuantity() == 3,
            "boundary re-attach did not apply progress");
        check(claim.task().getLeaseExpiresAt() == 67_000L,
            "boundary re-attach did not extend the lease");
    }

    private static void progressAfterGraceRejected() throws Exception {
        Claimed claim = expiredClaim();
        long now = 62_001L;
        check(now - claim.task().getLeaseLapsedAt() > TaskQueue.LEASE_GRACE_MS,
            "test setup did not exceed the grace window");

        check(!claim.queue().recordProgressIfLeaseMatches(
            claim.task().getId(), "jamal", claim.token(), 9, now, 5_000L),
            "late progress after the grace window was accepted");
        check(claim.task().getStatus() == BotTask.Status.PENDING,
            "after-grace progress changed the requeued status");
        check(claim.task().getCollectedQuantity() == 0,
            "after-grace progress changed the collected count");
        check(claim.task().getLeaseToken().isEmpty(),
            "after-grace progress reattached a dead lease");
        check("lease-expired".equals(claim.task().getStatusReason()),
            "after-grace progress changed the expiry reason");
    }

    private static void reclaimedLeaseFencesOldToken() throws Exception {
        Claimed claim = expiredClaim();
        BotTask reclaimed = claim.queue().claimNextForBot("chris", 3_000L, 5_000L);
        check(reclaimed != null, "another worker could not reclaim the expired task");
        String liveToken = reclaimed.getLeaseToken();
        check(!liveToken.equals(claim.token()), "reclaim reused the lapsed token");

        check(!claim.queue().recordProgressIfLeaseMatches(
            claim.task().getId(), "jamal", claim.token(), 42, 4_000L, 5_000L),
            "old token updated a re-claimed task inside the grace window");
        check("chris".equals(claim.task().getAssignedBot()),
            "old token changed the re-claimed owner");
        check(claim.task().getCollectedQuantity() == 0,
            "old token changed the re-claimed progress");
        check(claim.task().getLeaseExpiresAt() == 8_000L,
            "old token changed the re-claimed expiry");

        check(claim.queue().recordProgressIfLeaseMatches(
            claim.task().getId(), "chris", liveToken, 4, 4_000L, 5_000L),
            "current owner could not update the re-claimed task");
        check(claim.task().getCollectedQuantity() == 4,
            "current owner progress was not applied");
    }

    private static void completionReattachesInsideGrace() throws Exception {
        TaskQueue queue = freshQueue();
        long now = System.currentTimeMillis();
        BotTask task = queue.claimNextForBot("jamal", now - 2_000L, 1_000L);
        check(task != null, "completion-grace claim failed");
        check(queue.expireLeases(now - 500L) == 1,
            "completion-grace lease did not expire");
        String token = task.getLastLeaseToken();
        check(!token.isEmpty(), "completion-grace lost the lapsed token");

        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", token, BotTask.Status.COMPLETE, "late-done"),
            "completion inside the grace window was rejected");
        check(task.getStatus() == BotTask.Status.COMPLETE,
            "grace completion did not reach COMPLETE");
        check("late-done".equals(task.getStatusReason()),
            "grace completion did not preserve its reason");
        check(task.getLeaseToken().isEmpty(),
            "completed task kept a dead lease token");
        check(queue.hasActiveTask("jamal") == false,
            "grace completion left an ACTIVE lease behind");
    }

    private static Claimed expiredClaim() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = queue.claimNextForBot("jamal", 1_000L, 1_000L);
        check(task != null, "grace probe could not claim the task");
        String token = task.getLeaseToken();
        check(!token.isEmpty(), "claim issued no token");
        check(queue.expireLeases(2_000L) == 1, "lease did not expire at its deadline");
        check(task.getStatus() == BotTask.Status.PENDING,
            "expired task did not return to PENDING");
        check("lease-expired".equals(task.getStatusReason()),
            "expired task did not record lease-expired");
        check(token.equals(task.getLastLeaseToken()),
            "expired task did not preserve the lapsed token");
        return new Claimed(queue, task, token);
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
                  "targetQuantity": 10,
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

    private record Claimed(TaskQueue queue, BotTask task, String token) {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
