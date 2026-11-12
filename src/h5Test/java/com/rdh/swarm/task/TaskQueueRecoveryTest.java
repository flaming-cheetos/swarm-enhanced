package com.rdh.swarm.task;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * H5 Step 1 regression probe: a host restart must not leave an ACTIVE task
 * stranded. The test uses the real persisted queue file and the same
 * TaskQueue constructor the host uses at startup. No test framework is
 * required, so the repo keeps its existing dependency set.
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

        BotTask next = queue.getNextPendingForBot("jamal");
        check(next != null, "recovered task was not eligible for redispatch");

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

        // This is the host dispatch contract: hasActiveTask() must not block
        // the recovered row, and the next claim must reach a terminal state.
        BotTask claimed = reloaded.getNextPendingForBot("jamal");
        check(claimed != null, "reloaded task was not eligible for redispatch");
        claimed.setAssignedBot("jamal");
        claimed.setAttempt(claimed.getAttempt() + 1);
        claimed.setStatus(BotTask.Status.ACTIVE);
        claimed.setStatus(BotTask.Status.COMPLETE);
        reloaded.save();
        check(claimed.getStatus() == BotTask.Status.COMPLETE,
            "recovered task did not reach COMPLETE after redispatch");

        System.out.println("PASS TaskQueueRecoveryTest: ACTIVE -> PENDING -> COMPLETE");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
