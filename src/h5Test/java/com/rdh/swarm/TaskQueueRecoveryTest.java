package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * H5 Step 1 regression probe: a host restart must not leave an ACTIVE task
 * stranded. The probe seeds the real persisted queue, constructs the same
 * TaskQueue the host uses, and drives dispatch through EnhancedSwarmHost's
 * own selection/claim methods. No test framework is required, so the repo
 * keeps its existing dependency set.
 */
public final class TaskQueueRecoveryTest {

    private static final String TASK_ID = "11111111-1111-1111-1111-111111111111";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private TaskQueueRecoveryTest() {}

    public static void main(String[] args) throws Exception {
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
                  "status": "ACTIVE",
                  "assignedBot": "jamal",
                  "attempt": 4,
                  "lastOwner": "old-owner",
                  "statusReason": "active"
                }
              ],
              "nicknames": {}
            }
            """.formatted(TASK_ID));

        TaskQueue queue = new TaskQueue();

        // This is the exact host gate: an unrecovered ACTIVE row makes
        // hasActiveTask() block any redispatch and the selection returns null.
        BotTask selected = EnhancedSwarmHost.selectNextTaskForDispatch(queue, "jamal");
        check(selected != null,
            "host dispatch was blocked by a stranded ACTIVE task for jamal");

        BotTask task = queue.getAllTasks().stream()
            .filter(candidate -> candidate.getId().toString().equals(TASK_ID))
            .findFirst()
            .orElseThrow(() -> new AssertionError("persisted task was not loaded"));

        check(task.getStatus() == BotTask.Status.PENDING,
            "orphaned ACTIVE task was not recovered to PENDING");
        check(task.getAssignedBot() == null || task.getAssignedBot().isEmpty(),
            "orphaned task kept its dead owner assignment");
        check("jamal".equals(task.getLastOwner()),
            "recovery did not record the previous owner");
        check(task.getAttempt() == 5,
            "recovery did not increment the persisted attempt counter");
        check("host-restart".equals(task.getStatusReason()),
            "recovery did not record the host-restart reason");
        check(!queue.hasActiveTask("jamal"),
            "host still sees a stranded ACTIVE task for jamal");

        // Recovery must survive the same persisted-queue load the next host
        // performs before redispatching the task.
        TaskQueue reloaded = new TaskQueue();
        BotTask persisted = reloaded.getAllTasks().stream()
            .filter(candidate -> candidate.getId().toString().equals(TASK_ID))
            .findFirst()
            .orElseThrow(() -> new AssertionError("recovered task did not persist"));
        check(persisted.getStatus() == BotTask.Status.PENDING,
            "recovered PENDING status did not persist");
        check(persisted.getAttempt() == 5,
            "attempt counter did not persist across reload");
        check("jamal".equals(persisted.getLastOwner()),
            "previous owner did not persist across reload");
        check("host-restart".equals(persisted.getStatusReason()),
            "recovery reason did not persist across reload");

        // Dispatch and claim through the host's real helpers, then complete
        // the task through the queue.
        BotTask claimed = EnhancedSwarmHost.selectNextTaskForDispatch(reloaded, "jamal");
        check(claimed != null, "reloaded task was not eligible for host dispatch");
        EnhancedSwarmHost.beginDispatch(claimed, "jamal");
        check(claimed.getStatus() == BotTask.Status.ACTIVE,
            "host dispatch did not claim the recovered task");
        check(claimed.getAttempt() == 6,
            "host dispatch did not increment the persisted attempt");
        claimed.setStatus(BotTask.Status.COMPLETE);
        reloaded.save();
        check(claimed.getStatus() == BotTask.Status.COMPLETE,
            "recovered task did not reach COMPLETE after hosted dispatch");

        System.out.println("PASS TaskQueueRecoveryTest: ACTIVE -> PENDING -> COMPLETE");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
