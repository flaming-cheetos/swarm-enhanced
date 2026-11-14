package com.rdh.swarm;

import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Gate A board-reset probe: /api/task/remove calls TaskQueue.removeById, which
 * must delete a PENDING row rather than silently doing nothing, preserve the
 * other rows, persist the removal, and clear an ACTIVE owner's lease.
 * Plain Java main; no test-framework dependency.
 */
public final class TaskRemovalTest {

    private static final String TASK_A = "66666666-6666-6666-6666-666666666666";
    private static final String TASK_B = "77777777-7777-7777-7777-777777777777";
    private static final String TASK_C = "88888888-8888-8888-8888-888888888888";
    private static final Path CONFIG =
        Path.of("meteor-client", "enhanced-swarm", "task-queue.json");

    private TaskRemovalTest() {}

    public static void main(String[] args) throws Exception {
        pendingRemovalKeepsSiblings();
        removalPersistsAndCanEmptyTheBoard();
        activeRemovalClearsOwnerAndLease();
        missingIdsAreNoOps();
        System.out.println("PASS TaskRemovalTest: pending/active/persist/empty");
    }

    private static void pendingRemovalKeepsSiblings() throws Exception {
        TaskQueue queue = seedQueue();
        check(queue.size() == 3, "seed did not load three tasks");

        check(queue.removeById(uuid(TASK_A)), "PENDING row was not removed");
        check(queue.size() == 2, "removing one row changed the queue by more than one");
        check(find(queue, TASK_A) == null, "removed PENDING row is still present");
        check(find(queue, TASK_B) != null, "sibling B was removed");
        check(find(queue, TASK_C) != null, "sibling C was removed");
    }

    private static void removalPersistsAndCanEmptyTheBoard() throws Exception {
        TaskQueue queue = seedQueue();
        check(queue.removeById(uuid(TASK_A)), "PENDING row was not removed before reload");

        TaskQueue reloaded = new TaskQueue();
        check(reloaded.size() == 2, "removed row came back after queue reload");
        check(find(reloaded, TASK_A) == null, "removed PENDING row persisted on disk");
        check(find(reloaded, TASK_B) != null, "sibling B did not survive reload");
        check(find(reloaded, TASK_C) != null, "sibling C did not survive reload");

        check(reloaded.removeById(uuid(TASK_B)), "second PENDING row was not removed");
        check(reloaded.removeById(uuid(TASK_C)), "last PENDING row was not removed");
        check(reloaded.size() == 0, "queue did not reach an empty board");
        TaskQueue empty = new TaskQueue();
        check(empty.size() == 0, "empty board did not persist across reload");
    }

    private static void activeRemovalClearsOwnerAndLease() throws Exception {
        TaskQueue queue = seedQueue();
        BotTask claimed = queue.claimNextForBot(uuid(TASK_B), "jamal", 1_000L, 5_000L);
        check(claimed != null, "could not claim the task for active-removal test");
        check(queue.hasActiveTask("jamal"), "claimed task is not ACTIVE");

        check(queue.removeById(uuid(TASK_B)), "ACTIVE row was not removed");
        check(!queue.hasActiveTask("jamal"), "removed ACTIVE row still blocks the worker");
        check(find(queue, TASK_B) == null, "removed ACTIVE row is still present");
        check(queue.size() == 2, "active removal dropped an unrelated row");

        TaskQueue reloaded = new TaskQueue();
        check(find(reloaded, TASK_B) == null, "removed ACTIVE row persisted on disk");
        check(reloaded.size() == 2, "active removal did not persist cleanly");
    }

    private static void missingIdsAreNoOps() throws Exception {
        TaskQueue queue = seedQueue();
        check(!queue.removeById(uuid("99999999-9999-9999-9999-999999999999")),
            "removing an unknown id reported success");
        check(!queue.removeById(null), "removing null reported success");
        check(queue.size() == 3, "a missing id changed the queue");
        TaskQueue reloaded = new TaskQueue();
        check(reloaded.size() == 3, "a missing id changed the persisted queue");
    }

    private static TaskQueue seedQueue() throws Exception {
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
        return new TaskQueue();
    }

    private static BotTask find(TaskQueue queue, String id) {
        return queue.getAllTasks().stream()
            .filter(task -> task.getId().toString().equals(id))
            .findFirst()
            .orElse(null);
    }

    private static UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
