package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * H5 progress regression probe: progress is applied through the real fenced
 * host path, clamped to the requested quantity, and survives completion.
 */
public final class ProgressAccountingTest {
    private static final String TASK_ID = "55555555-5555-5555-5555-555555555555";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private ProgressAccountingTest() {}

    public static void main(String[] args) throws Exception {
        TaskQueue queue = freshQueue();
        BotTask task = EnhancedSwarmHost.claimNextTaskForDispatch(queue, "jamal", 1000L, 5000L);
        check(task != null, "initial claim failed");
        String token = task.getLeaseToken();

        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", token, 1, 1500L, 5000L),
            "first progress was rejected");
        check(task.getCollectedQuantity() == 1, "first progress was not applied");

        check(EnhancedSwarmHost.recordWorkerProgress(
            queue, task.getId(), "jamal", token, 4, 2000L, 5000L),
            "over-target progress was rejected");
        check(task.getCollectedQuantity() == 2,
            "progress was not clamped to the target quantity");
        check("2/2".equals(task.getProgressString()),
            "progress string did not report the clamped value");

        check(EnhancedSwarmHost.finishTaskFromWorker(
            queue, task.getId(), "jamal", token, BotTask.Status.COMPLETE, "completed"),
            "completion was rejected");
        check(task.getStatus() == BotTask.Status.COMPLETE, "task did not complete");
        check(task.getCollectedQuantity() == 2,
            "completion lost the recorded collected quantity");
        check("2/2".equals(task.getProgressString()),
            "completed row did not report 2/2");
        check("completed".equals(task.getStatusReason()),
            "completed row did not record the terminal reason");
        check(!queue.hasActiveTask("jamal"), "completed row remained ACTIVE");

        System.out.println("PASS ProgressAccountingTest: 2/2 complete and clamped");
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
