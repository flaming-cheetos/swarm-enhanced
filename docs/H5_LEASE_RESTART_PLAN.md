# H5 Lease/Restart Plan

Status: **PLAN — not implemented; no H5 live result claimed**
Date: 2026-11-12
Build under test: `0.1.0+e62b2f9`
Harness status: H1 three consecutive live runs PASS under
`test-runs/h1-3x-e62b2f9/run1..run3`.

## 1. Problem

H5 is: restart the host while a task is `ACTIVE`; no task may remain
stranded, and any surviving task must become `PENDING` or explicitly
`FAILED`.

Current code has no task lease and no startup recovery:

- `BotTask` stores only `status` and `assignedBot`; it has no owner token,
  lease deadline, heartbeat, attempt number, or host epoch.
  (`src/main/java/com/rdh/swarm/task/BotTask.java`)
- `TaskQueue.save()` and `load()` persist the task status and assigned bot,
  but no lease or ownership generation.
  (`src/main/java/com/rdh/swarm/task/TaskQueue.java`)
- `TaskQueue.load()` adds non-terminal tasks, including `ACTIVE`, and
  `clearStale()` only removes terminal rows.
- `EnhancedSwarmHost.dispatchNextTask()` returns early when
  `taskQueue.hasActiveTask(botName)` is true, so a restored `ACTIVE` row can
  never be dispatched again.
  (`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`)
- `cleanDead()` and `disconnect()` call `requeueActiveForBot()` on a known
  disconnected worker, but a host crash never runs that path.
- Completion, failure, and cancellation messages identify only the task ID;
  there is no fencing token to reject a late message from a worker that was
  assigned the task before a restart.
  (`src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java`)

The H5 stub already models the intended contract: restart marks the worker
offline, requeues the `ACTIVE` row to `PENDING`, and recovery brings the
worker back online. That is contract evidence only, not product evidence.
(`tools/harness/scenarios/h5.json`, `tools/harness/panel-stub.mjs`)

## 2. Desired invariants

1. A task in `ACTIVE` always has a live lease owner, a lease token, and a
   future expiry.
2. On host startup, no restored task is left `ACTIVE`. Restored active work
   becomes `PENDING` with an explicit `requeued`/`host-restart` reason, or
   becomes `FAILED` with a concrete reason if it cannot be requeued.
3. A worker may renew or finish a task only with the lease token issued for
   its current assignment.
4. Expiry or reconnect requeues the task once, preserving `collected` and
   incrementing an attempt counter; it never silently resets progress.
5. A stale completion from an old assignment cannot complete the task after
   it has been requeued and claimed by a new assignment.
6. Every recovery transition is visible in the panel log and the host
   evidence window.
7. H5 remains deterministic in the stub suite and has a documented live
   gate.

## 3. Data model

Add a small lease value to `BotTask`:

```text
leaseOwner      String   worker name
leaseToken      UUID     fencing token issued on claim
leaseExpiresAt  long     epoch millis; 0 when PENDING
lastHeartbeatAt long     epoch millis
attempt         int      incremented on each claim/requeue
```

Keep `status` and `assignedBot` for compatibility, but treat them as views of
the lease lifecycle:

```text
PENDING --claim--> ACTIVE --complete--> COMPLETE
                         --fail-------> FAILED
                         --cancel-----> CANCELLED
                         --expire-----> PENDING (attempt++ + reason)
                         --host start-> PENDING (attempt++ + reason)
```

Persist lease fields in `task-queue.json`. Older files without these fields
load with an empty lease and `attempt = 0`.

Lease defaults:

- TTL: **30 seconds** (the fixture’s H1 gather has taken 11–20 seconds).
- Renewal: existing `TASK_PROGRESS` heartbeat, at most once per 5 seconds.
- Startup recovery runs before the first dispatch, after the queue loads and
  before a worker can connect.

## 4. Queue API

All lease operations are synchronized inside `TaskQueue`:

```text
claimNextForBot(botName, now, ttlMs) -> ClaimedTask(task, token, attempt)
  Only PENDING tasks eligible for botName. Sets ACTIVE, owner, token,
  expiry, attempt++, saves, logs.

renewLease(taskId, token, now, ttlMs) -> boolean
  Only if task ACTIVE and token matches. Extends expiry and heartbeat.

expireLeases(now) -> requeued count
  ACTIVE && now >= expiry -> PENDING, owner/token cleared, expiry 0,
  attempt retained, reason "lease-expired", save, log.

recoverActiveAfterHostRestart(now) -> requeued count
  Called once by the host after load. Every restored ACTIVE task becomes
  PENDING, owner/token cleared, attempt++, reason "host-restart", save, log.

finishIfLeaseMatches(taskId, token, terminalStatus, reason) -> boolean
  COMPLETE/FAILED/CANCELLED only when token matches. Stale tokens are
  rejected and logged; they never mutate the task.

requeueForWorkerDisconnect(botName, now) -> count
  ACTIVE rows owned by botName become PENDING, reason "worker-disconnect".
```

`hasActiveTask()` remains useful for dispatch but must not prevent startup
recovery. Recovery runs before any connection is accepted or dispatched.

## 5. Host changes

1. After constructing `TaskQueue`, call
   `recoverActiveAfterHostRestart(System.currentTimeMillis())` before the
   host starts accepting workers.
2. Replace the dispatch body with `claimNextForBot()` and send the lease
   token/attempt in the assignment.
3. On `TASK_PROGRESS`, renew the lease if the token matches.
4. On `TASK_COMPLETE`, `TASK_FAILED`, and `TASK_CANCELLED`, call the
   lease-checked finish method; only then update queue and panel logs.
5. On worker disconnect and `cleanDead()`, requeue by owner and token.
6. Add a host-side periodic tick (or existing maintenance point) that calls
   `expireLeases()`, logs `TASK: lease expired ...`, and dispatches pending
   work.
7. On a worker reconnect after host restart, do not assume the old worker
   state is valid. Send a cancel/reset for any stale assignment, then claim
   the requeued task with a fresh token.

## 6. Protocol compatibility

Stage 1 can keep the current assignment shape and add the lease token as an
optional trailing field. New hosts must tolerate old workers; new workers
must tolerate old hosts.

Preferred assignment:

```text
task assign <id>|<type>|<item>|<qty>|<bot>|<leaseToken>|<attempt>
```

Preferred worker responses:

```text
TASK_PROGRESS:<worker>:<taskId>:<leaseToken>:<collected>:<target>:<state>:<eta>
TASK_COMPLETE:<worker>:<taskId>:<leaseToken>:<item>:<qty>:<elapsed>
TASK_FAILED:<worker>:<taskId>:<leaseToken>:<reason>
TASK_CANCELLED:<worker>:<taskId>:<leaseToken>:<reason>
```

Compatibility rule: missing token is accepted only from a worker using the
legacy protocol and only while the host has the task `ACTIVE` under that
worker; the host records a warning. Once a task has been requeued, a legacy
worker’s completion is stale and rejected.

## 7. Panel and logs

Expose lease diagnostics without exposing the token:

```json
{
  "attempt": 2,
  "leaseOwner": "jamal",
  "leaseExpiresAt": 1770000000000,
  "lastHeartbeatAt": 1770000000000
}
```

Required log anchors:

```text
TASK: claimed <id> by <bot> attempt=<n>
TASK: lease renewed <id> attempt=<n>
TASK: lease expired <id> attempt=<n>; requeued
TASK: requeued <id> reason=host-restart
TASK: requeued <id> reason=worker-disconnect
Requeued <n> active task(s) from <bot>
Host restart recovery: requeued <n> active task(s)
TASK: rejected stale completion <id> token=<short>
```

The H5 stub assertion may keep the existing
`Requeued 1 active task(s)` + `Host restart` anchors; if the product uses
`Host restart recovery`, update the stub/spec together rather than weakening
the assertion.

## 8. Failure and edge cases

- **Host restart while worker is still running:** old worker may finish
  physically, but its old token cannot complete the new queue row. New host
  must cancel/reset the old assignment before issuing a new one.
- **Worker disconnect:** requeue once, preserve collected progress, clear
  owner; the next eligible worker claims a new attempt.
- **Lease expiry while worker is alive:** heartbeat should prevent it; if the
  worker is unresponsive, requeue and fence the old completion.
- **Worker never reconnects:** task stays `PENDING`; H5 result can be
  `PENDING` (no stranded ACTIVE) rather than fabricated completion.
- **Old queue file:** migrate missing lease fields; any `ACTIVE` row is
  recovered on startup, never left untouched.
- **Duplicate progress/completion:** token check makes only the current
  attempt authoritative.
- **Collected quantity:** requeue never resets it; completion evidence still
  gates terminal state.

## 9. Implementation sequence

### Step 1 — Startup recovery (smallest H5 fix)

- Add lease/attempt fields and persistence.
- Add `recoverActiveAfterHostRestart()`.
- Call it from `EnhancedSwarmHost` before serving connections.
- Add panel/log visibility.
- Update H5 stub only if product log strings differ.
- Build, stub suite, self-test, stage jar.

### Step 2 — Claim, heartbeat, expiry

- Add `claimNextForBot`, `renewLease`, `expireLeases`.
- Wire `dispatchNextTask` and `TASK_PROGRESS` heartbeat.
- Add lease expiry maintenance tick and panel fields.

### Step 3 — Fencing and reconnect handshake

- Add lease token/attempt to worker assignment and responses.
- Reject stale completion/failure/cancellation.
- Reset stale worker assignment on reconnect.
- Add protocol compatibility tests for old/new combinations.

### Step 4 — H5 live gate

1. Clear terminal tasks; add `oak_log x2` assigned to `jamal`.
2. Wait for `ACTIVE` and record the task id/attempt.
3. Restart the host with the task still `ACTIVE`.
4. After host startup, panel state must contain no `ACTIVE` row; the task is
   `PENDING` with an explicit reason, or `FAILED` with a reason.
5. Reconnect the worker; it claims with a new attempt and completes once.
6. Assert exactly one completion and no duplicate/stranded row.
7. Preserve the failure bundle even if H5 fails.

## 10. Acceptance criteria

- H5 stub suite remains green; self-test remains 14/14.
- No restored `ACTIVE` task survives host startup.
- H5 live produces `PENDING` or explicit `FAILED`, never stranded `ACTIVE`.
- One terminal completion per task; stale attempts cannot complete.
- Panel state and logs name the recovery reason and attempt.
- `gradlew build` green; H1 three-run gate remains green.
- Docs/spec updated with the actual log anchors and live evidence path.

## 11. Source map

- `src/main/java/com/rdh/swarm/task/BotTask.java` — task state fields.
- `src/main/java/com/rdh/swarm/task/TaskQueue.java` — persistence, load,
  `requeueActiveForBot`, `hasActiveTask`.
- `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` — queue ownership,
  `dispatchNextTask`, `cleanDead`, `disconnect`.
- `src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java` —
  `TASK_PROGRESS`, `TASK_COMPLETE`, `TASK_FAILED`, `TASK_CANCELLED`.
- `tools/harness/scenarios/h5.json` — intended stub contract.
- `tools/harness/panel-stub.mjs` — `hostRestart`/requeue simulation.
- `docs/TEST_HARNESS_SPEC.md` — H5 gate and acceptance criteria.

This is a plan, not a claim that H5 is fixed. The next implementation step is
Step 1; H5 live remains unrun until the recovery path exists.
