# H5 Lease/Restart Plan

Status: **PLAN — Steps 1–2 implemented; Steps 3–4 open; no H5 live result claimed**
Date: 2026-11-12
Build under test: `0.1.0+e62b2f9`
Harness status: H1 three consecutive live runs PASS under
`test-runs/h1-3x-e62b2f9/run1..run3`.

## Step 1 status — DONE (2026-11-12)

Startup recovery is implemented and covered by
`com.rdh.swarm.TaskQueueRecoveryTest`:

- `BotTask` persists `attempt`, `lastOwner`, and `statusReason`.
- `TaskQueue.load()` converts every persisted `ACTIVE` row to `PENDING`,
  clears the dead owner, records the previous owner and `host-restart`
  reason, increments the attempt, and saves the recovered queue.
- `requeueActiveForBot()` records the same ownership/attempt data for a
  known worker disconnect.
- `EnhancedSwarmHost.dispatchNextTask()` increments the attempt when it
  claims work, so the next dispatch is not blocked by `hasActiveTask()`.
- The Step 1 probe drives dispatch through
  `EnhancedSwarmHost.claimNextTaskForDispatch()` and the same
  `claimNextForBot()` path the live host uses, rather than reimplementing
  dispatch in the test.

Evidence:

```text
./gradlew.bat h5RecoveryTest
PASS TaskQueueRecoveryTest: ACTIVE -> PENDING -> COMPLETE

./gradlew.bat build
BUILD SUCCESSFUL
```

The original queue-level probe failed before Step 1:

```text
AssertionError: orphaned ACTIVE task was not recovered to PENDING
  at TaskQueueRecoveryTest.check(TaskQueueRecoveryTest.java:46)
```

The strengthened host-path probe was then run with the load-time recovery
hook temporarily disabled; it failed on the exact stranded-host gate:

```text
AssertionError: host dispatch was blocked by a stranded ACTIVE task for jamal
  at TaskQueueRecoveryTest.check(TaskQueueRecoveryTest.java:107)
  at TaskQueueRecoveryTest.main(TaskQueueRecoveryTest.java:52)
```

Restoring `TaskQueue.java` byte-for-byte made the same probe pass.

## Step 2 status — DONE (2026-11-12)

Lease lifecycle is implemented:

- `BotTask` persists `leaseToken` and `leaseExpiresAt`.
- `TaskQueue.claimNextForBot()` is the single lease-granting path, including
  the targeted overload used by manual `assignTask()`; it sets owner, token,
  expiry, attempt, `ACTIVE`, and saves atomically, and refuses a task held by
  another live lease.
- `renewLease()` extends only a live, current-token lease;
  `renewLeaseForBot()` is the `TASK_PROGRESS` owner check.
- `expireLeases()` returns lapsed leases to `PENDING`, clears owner/token/
  expiry, increments the attempt, and records `lease-expired`.
- `EnhancedSwarmHost.dispatchNextTask()` uses the claim path, `TASK_PROGRESS`
  renews through `renewLeaseForBot()`, and the host schedules
  `runLeaseMaintenance()` every 5 seconds while running.

Evidence:

```text
./gradlew.bat h5LeaseTest
PASS LeaseLifecycleTest: claim/renew/expire/tick

./gradlew.bat h5RecoveryTest
PASS TaskQueueRecoveryTest: ACTIVE -> PENDING -> COMPLETE
```

The lease test's red state removed only the token-equality check:

```text
AssertionError: a superseded token renewed the lease
  at LeaseLifecycleTest.check(LeaseLifecycleTest.java:154)
  at LeaseLifecycleTest.renewalExtendsOnlyCurrentLease(LeaseLifecycleTest.java:73)
```

Step 2 panel/log fields and the H5 live gate remain open. Steps 3–4
(fencing/reconnect and live H5) are still open.

## 1. Problem

H5 is: restart the host while a task is `ACTIVE`; no task may remain
stranded, and any surviving task must become `PENDING` or explicitly
`FAILED`.

Before Step 1, the code had no task lease and no startup recovery:

- Before Step 1, `BotTask` stored only `status` and `assignedBot`; it had no
  owner token, lease deadline, heartbeat, attempt number, or host epoch.
  (`src/main/java/com/rdh/swarm/task/BotTask.java`)
- `TaskQueue.save()` and `load()` now persist the status, assigned bot,
  `attempt`, `lastOwner`, and `statusReason`; lease tokens and expiry are
  still future work.
  (`src/main/java/com/rdh/swarm/task/TaskQueue.java`)
- Before Step 1, `TaskQueue.load()` added non-terminal tasks, including
  `ACTIVE`, and `clearStale()` only removed terminal rows. Step 1 now
  recovers `ACTIVE` rows at load time.
- `EnhancedSwarmHost.dispatchNextTask()` returns early when
  `taskQueue.hasActiveTask(botName)` is true. Recovery now clears the stale
  owner before a reconnect reaches this check.
  (`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`)
- `cleanDead()` and `disconnect()` call `requeueActiveForBot()` on a known
  disconnected worker; Step 1 now records ownership/attempt/reason there too.
  A host crash reaches the load-time recovery path instead.
- Completion, failure, and cancellation messages identify only the task ID;
  there is no fencing token to reject a late message from a worker that was
  assigned the task before a restart.
  (`src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java`)

The H5 stub already models the intended contract: restart marks the worker
offline, requeues the `ACTIVE` row to `PENDING`, and recovery brings the
worker back online. That is contract evidence only, not product evidence.
(`tools/harness/scenarios/h5.json`, `tools/harness/panel-stub.mjs`)

Step 1 implements the persisted queue side of that contract. The live H5
restart remains unrun until the later steps and the manual gate.

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

Steps 1–2 have landed `attempt`, `lastOwner`, `statusReason`, `leaseToken`,
and `leaseExpiresAt` on `BotTask` and in `task-queue.json`. The token/attempt
protocol and completion fencing below remain future Steps 3–4; do not assume
they exist yet.

Relevant lease fields:

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
claimNextForBot(botName, now, ttlMs) -> BotTask
  Step 2: single lease-granting path. Only PENDING eligible tasks. Sets
  ACTIVE, owner, token, expiry, attempt++, saves. A targeted overload serves
  manual assignTask().

renewLease(taskId, token, now, ttlMs) -> boolean
  Step 2: only if ACTIVE, unexpired, and token matches; extends expiry.
  renewLeaseForBot() adds the TASK_PROGRESS owner check.

expireLeases(now) -> requeued count
  Step 2: ACTIVE && now >= expiry -> PENDING, owner/token cleared, expiry 0,
  attempt++, reason "lease-expired", save.

recoverActiveAfterHostRestart() -> requeued count
  Step 1: called at the end of TaskQueue.load(). Every restored ACTIVE task
  becomes PENDING, owner cleared, attempt++, reason "host-restart", save.
  Future lease fields/tokens are not part of this Step 1 signature.

finishIfLeaseMatches(taskId, token, terminalStatus, reason) -> boolean
  COMPLETE/FAILED/CANCELLED only when token matches. Stale tokens are
  rejected and logged; they never mutate the task.

requeueForWorkerDisconnect(botName, now) -> count
  ACTIVE rows owned by botName become PENDING, reason "worker-disconnect".
```

`hasActiveTask()` remains useful for dispatch but must not prevent startup
recovery. Recovery runs before any connection is accepted or dispatched.

## 5. Host changes

1. Step 1: `TaskQueue.load()` calls `recoverActiveAfterHostRestart()`; the
   host field initializer constructs the queue before accepting workers.
2. Step 2: `dispatchNextTask()` uses `claimNextForBot()`; manual `assignTask()`
   uses its targeted overload. The assignment payload still omits the lease
   token until Step 3.
3. Step 2: `TASK_PROGRESS` renews through `renewLeaseForBot()`; the host runs
   `runLeaseMaintenance()` on a 5-second scheduled tick while running.
4. Step 3: on `TASK_COMPLETE`, `TASK_FAILED`, and `TASK_CANCELLED`, call the
   lease-checked finish method with the worker-returned token; only then
   update queue and panel logs.
5. Step 3: on worker disconnect/reconnect, requeue by owner/token, cancel any
   stale assignment, and issue a fresh token.
6. Future: add panel/log fields for the lease and recovery reason.
7. Future: add stale-completion rejection and the reconnect handshake.

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

### Step 1 — Startup recovery (smallest H5 fix) — DONE 2026-11-12

- [x] Add `attempt`, `lastOwner`, and `statusReason` fields and persistence.
- [x] Add `recoverActiveAfterHostRestart()` and call it at the end of
  `TaskQueue.load()` before a worker can dispatch.
- [x] Make `requeueActiveForBot()` preserve the same ownership/attempt data.
- [x] Increment the attempt when `EnhancedSwarmHost.dispatchNextTask()` claims
  work, so the recovered row is eligible.
- [x] Add `TaskQueueRecoveryTest` through the real host dispatch gate,
  runnable with `./gradlew.bat h5RecoveryTest`; `build` depends on it through
  `check`.
- [x] Verify the probe fails before the recovery hook and passes after it.
- [x] Run stub suite, self-test, and full build. H1 evidence remains unchanged.
- [ ] Panel/log visibility (deliberately left to a later step).
- [ ] Stage a new jar for live H5 (not requested in this Step 1 pass).

### Step 2 — Claim, heartbeat, expiry — DONE 2026-11-12

- [x] Add `claimNextForBot`, `renewLease`, `renewLeaseForBot`, and
  `expireLeases`; persist token/expiry.
- [x] Route `dispatchNextTask` and manual `assignTask` through the claim path.
- [x] Wire `TASK_PROGRESS` heartbeat renewal.
- [x] Add the host maintenance tick (`runLeaseMaintenance`).
- [x] Add `LeaseLifecycleTest` for claim exclusivity, renewal, expiry, and
  tick-driven expiry; `h5LeaseTest` is part of `check`.
- [x] Verify the lease test fails when the token-equality check is removed and
  passes after restoring it.
- [ ] Panel/log lease fields (left open).
- [ ] Stage a new jar for live H5 (not requested in this pass).

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
  `claimNextTaskForDispatch`, `runLeaseMaintenance`, `dispatchNextTask`,
  `cleanDead`, `disconnect`.
- `src/h5Test/java/com/rdh/swarm/LeaseLifecycleTest.java` — claim, renewal,
  expiry, and host-maintenance-tick regression probe.
- `src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java` —
  `TASK_PROGRESS`, `TASK_COMPLETE`, `TASK_FAILED`, `TASK_CANCELLED`.
- `tools/harness/scenarios/h5.json` — intended stub contract.
- `tools/harness/panel-stub.mjs` — `hostRestart`/requeue simulation.
- `docs/TEST_HARNESS_SPEC.md` — H5 gate and acceptance criteria.

Steps 1–2 are implemented and tested. Steps 3–4 (token/attempt protocol,
stale-completion fencing, reconnect handshake, panel/log visibility, and the
live H5 gate) remain open; no H5 live result is claimed.
