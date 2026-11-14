package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * H5 Step 2 regression probe: leases are assigned, exclusive, renewable only
 * by the current token, expirable back to PENDING, and swept by the host
 * maintenance tick. Plain Java main; no test-framework dependency.
 */
public final class LeaseLifecycleTest {

    private static final String TASK_ID = "22222222-2222-2222-2222-222222222222";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private LeaseLifecycleTest() {}

    public static void main(String[] args) throws Exception {
        claimAssignsFreshLease();
        secondClaimCannotGetLeasedTask();
        renewalExtendsOnlyCurrentLease();
        expiryRequeuesWithReason();
        hostMaintenanceTickExpires();
        System.out.println("PASS LeaseLifecycleTest: claim/renew/expire/tick");
    }

    private static void claimAssignsFreshLease() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask claimed = queue.claimNextForBot("jamal", 1000L, 5000L);

        check(claimed != null, "claim did not return the pending task");
        check(claimed.getStatus() == BotTask.Status.ACTIVE,
            "claim did not mark the task ACTIVE");
        check("jamal".equals(claimed.getAssignedBot()),
            "claim did not assign the worker");
        check(!claimed.getLeaseToken().isEmpty(),
            "claim did not issue a lease token");
        check(claimed.getLeaseExpiresAt() == 6000L,
            "claim expiry was not now + ttl");
        check(claimed.getAttempt() == 1,
            "claim did not increment the attempt");
        check(queue.hasActiveTask("jamal"),
            "host does not see the claimed lease");
    }

    private static void secondClaimCannotGetLeasedTask() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask first = queue.claimNextForBot("jamal", 1000L, 5000L);
        check(first != null, "first claim failed");

        BotTask second = queue.claimNextForBot("jamal", 1001L, 5000L);
        check(second == null, "second claim received an already leased task");

        BotTask other = queue.claimNextForBot("chris", 1001L, 5000L);
        check(other == null, "another worker received an already leased task");
    }

    private static void renewalExtendsOnlyCurrentLease() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask claimed = queue.claimNextForBot("jamal", 1000L, 5000L);
        check(claimed != null, "claim failed for renewal test");
        String currentToken = claimed.getLeaseToken();

        check(queue.renewLease(claimed.getId(), currentToken, 3000L, 5000L),
            "current lease could not be renewed");
        check(claimed.getLeaseExpiresAt() == 8000L,
            "renewal did not extend the expiry");

        check(!queue.renewLease(claimed.getId(), "superseded-token", 4000L, 5000L),
            "a superseded token renewed the lease");
        check(claimed.getLeaseExpiresAt() == 8000L,
            "a superseded token changed the expiry");

        check(queue.renewLeaseForBot(claimed.getId(), "jamal", 4000L, 5000L),
            "host heartbeat could not renew the current owner's lease");
        check(claimed.getLeaseExpiresAt() == 9000L,
            "host heartbeat did not extend the expiry");
        check(!queue.renewLeaseForBot(claimed.getId(), "chris", 5000L, 5000L),
            "a different worker renewed someone else's lease");

        check(queue.expireLeases(9000L) == 1, "lapsed lease did not expire");
        BotTask replacement = queue.claimNextForBot("chris", 9001L, 5000L);
        check(replacement != null, "expired task could not be reclaimed");
        check(!queue.renewLease(claimed.getId(), currentToken, 9500L, 5000L),
            "a stale token renewed a replacement lease");
        check(replacement.getLeaseExpiresAt() == 14001L,
            "a stale token changed the replacement expiry");
    }

    private static void expiryRequeuesWithReason() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask claimed = queue.claimNextForBot("jamal", 1000L, 500L);
        check(claimed != null, "claim failed for expiry test");

        check(queue.expireLeases(1500L) == 1, "lapsed lease was not expired");
        BotTask expired = queue.getAllTasks().get(0);
        check(expired.getStatus() == BotTask.Status.PENDING,
            "expired lease did not return to PENDING");
        check(expired.getAssignedBot() == null || expired.getAssignedBot().isEmpty(),
            "expired lease kept the dead owner");
        check(expired.getLeaseToken().isEmpty(),
            "expired lease kept the dead token");
        check(expired.getLeaseExpiresAt() == 0L,
            "expired lease kept the dead expiry");
        check("lease-expired".equals(expired.getStatusReason()),
            "expired lease did not record the reason");
        check(!queue.hasActiveTask("jamal"),
            "host still sees the expired lease as active");
    }

    private static void hostMaintenanceTickExpires() throws Exception {
        TaskQueue queue = freshQueue();
        BotTask claimed = queue.claimNextForBot("jamal", 1000L, 100L);
        check(claimed != null, "claim failed for host tick test");

        check(EnhancedSwarmHost.runLeaseMaintenance(queue, 1100L) == 1,
            "host maintenance tick did not expire the lapsed lease");
        BotTask expired = queue.getAllTasks().get(0);
        check(expired.getStatus() == BotTask.Status.PENDING,
            "host tick did not return the lease to PENDING");
        check(expired.getAssignedBot() == null || expired.getAssignedBot().isEmpty(),
            "host tick did not clear the owner");
        check("lease-expired".equals(expired.getStatusReason()),
            "host tick did not record the expiry reason");
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
