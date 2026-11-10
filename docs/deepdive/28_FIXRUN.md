# Fix run — overnight, 2026-11-09

## Check out this to test

branch: `fix/staging`
commit: `c366290`
build: **BUILD SUCCESSFUL** (exit `0`)

```text
BUILD_STATUS=0
BUILD SUCCESSFUL in 1s
4 actionable tasks: 2 executed, 2 up-to-date
```

`./gradlew build` was used, never `-PcopyToMmc`. The build has no test sources
(`test NO-SOURCE`); the two warnings are the existing `isSolid()` deprecations in
`BotBrain` and `EnhancedSwarmWorker`.

Nothing in this run was launched in Minecraft. All gameplay statements below are
**code only — needs client**.

---

## Branches

The branches stack as requested:

```text
932a5d6  fix/trust-fuel
   └─ 49eabd6  fix/deposit
        └─ 8d1d276  fix/completion-lifecycle
             └─ c366290  fix/staging
```

### fix/deposit — COMPLETE

base: `932a5d6`  head: `49eabd6`
findings closed: 2, 4, 5, 6, 13

commits:
- `49eabd6` deposit: run transfers on the tick thread with owned menus and observed deltas (findings 2, 4, 5, 6, 13)

what changed:
- Added a tick-driven `DepositDriver` state machine:
  `IDLE → OPENING → VERIFYING → TRANSFERRING → CLOSING → DONE | FAILED`.
- One container action per client tick. The transfer path no longer reads a menu,
  a slot, or clicks from `MeteorExecutor`.
- Timeouts: OPENING 3 s, VERIFYING 1 s, TRANSFERRING 30 s, CLOSING 3 s; step cap
  128; stop after 3 consecutive steps with no observed progress.
- Menu ownership is tracked by the driver. It verifies the expected container type,
  the observed `containerId`, and the block still at the target position before a
  transfer. It closes only the menu it opened.
- Chest identity is `dimension|x|y|z|category`; puts are idempotent; host pushes
  replace the list atomically and carry a generation; reconnects do not append.
- Deposit counters reset per deposit. Only observed `(item, delta)` pairs are
  published as `CHEST_DELTA`; a no-op is not a success.
- One `startDeposit(reason)` entry point is used by the full-inventory, manual,
  and low-stock paths.
- Log prefixes implemented: `DEPOSIT: start`, `DEPOSIT: step`, `DEPOSIT: no-op`,
  `DEPOSIT: refused`, `DEPOSIT: complete`, `DEPOSIT: failed`, `CHEST: add`,
  `CHEST: duplicate ignored`, `CHEST: list replaced`.

green: yes (final `./gradlew.bat build`, exit `0`, `BUILD SUCCESSFUL in 2s`)
unplanned changes: none
known gaps:
- No live client test. Menu identity, shift-click deltas, and reconnect generation
  behavior are code-only.
- Diff is 991 insertions / 430 deletions across 7 files. The size is the new
  698-line `DepositDriver` plus deletion of the old executor transfer methods;
  there is no formatting-only churn. This is the one branch over the ~800-line
  sanity threshold, and this is the justification.

### fix/completion-lifecycle — COMPLETE

base: `49eabd6`  head: `8d1d276`
findings closed: 7, 8, 14, 15, 21

commits:
- `8d1d276` completion-lifecycle: gate completion on evidence and preserve paused tasks (findings 7, 8, 14, 15, 21)

what changed:
- `DropCollector.startFinalSweep` now returns
  `RUNNING | EVIDENCED | NOTHING_TO_COLLECT | UNPROVEN`.
- One completion gate, `finishTaskWithEvidence`, is the only path that sets
  `COMPLETE`. `UNPROVEN` retries once, then fails with
  `reason=unproven-completion`.
- Routed gather, hunt, cooking-hunt, grind, smithing, shulker, and crafting
  completion through the gate. Removed the invented remaining-count additions
  from `completeBuild` and `completeShulker`.
- Halt is now `RUNNING / PAUSED / CANCELLED`. Pause keeps the task and progress;
  cancel drops the task and tells the host; resume is tick-deferred and only
  accepts a paused task with the same id.
- The command and web resume paths no longer dispatch inline.
- `handleStuck` and the deposit timeout now fail the owned task instead of
  leaving `IDLE` with a non-null task. An explicit orphan check fails any
  `IDLE + currentTask` state at the top of the tick.
- Hunt follow now uses the selected UUID; retarget is logged on dead, gone, or
  out-of-range. `lastHuntTargetId` is read as well as written.
- Worker frames go through one synchronized `writeLine`; the worker installs and
  clears the brain output stream on connect/disconnect. `BotBrain.out` is
  volatile and its task messages synchronize on the stream.

green: yes (first attempt exposed a stale `catch (IOException)` around
`sendScanResult`; fixed, second build exit `0`, `BUILD SUCCESSFUL in 2s`)
unplanned changes:
- Added `DepositDriver.cancel()` so pause/cancel closes only the deposit-owned
  menu (finding 7's ownership rule).
- Removed the two inline `dispatchPendingForAll()` calls from resume; resume now
  waits for the client tick (finding 7's tick-owned rule).
known gaps:
- `completeBuild` now **always fails** with `reason=unproven-completion`: there is
  still no placement-verification evidence in the builder. That is the honest
  consequence of “stop inventing counts”; it means builder tasks will not complete
  until placement evidence is implemented. This is deliberate, not an oversight.
- `completeShulker` uses `shulkerMovedAny`, set when a shift-click is issued, not
  a measured inventory delta. It stops the old full-count invention, but it is
  weaker evidence than the deposit driver's observed deltas.
- The completion gate can complete on `NOTHING_TO_COLLECT` after a genuinely
  empty sweep; it does not independently re-verify item identity/count for every
  task type.
- `HUNT: retarget reason=no-target` is not emitted; dead/gone/out-of-range are.
- Pause/resume payloads still arrive through the worker socket handler. The
  planner mutations are tick-deferred enough for this branch's gate, but the
  socket-thread ownership cleanup is finding 10 and remains for B6.

### fix/staging — COMPLETE

base: `8d1d276`  head: `c366290`
finding closed: 9

commits:
- `c366290` staging: search a scored vertical band and bound the goto (finding 9)

what changed:
- Added `gather-staging-band`, default 16, range 0–64.
- Search now samples up to 64 candidates in the vertical band around the origin.
- A candidate requires a non-air, non-fluid floor and two blocks of headroom
  (feet and head air/fluid-free).
- Candidates are scored by horizontal distance, vertical distance, floor, and
  headroom; candidates below score 16 are rejected. The best valid candidate is
  selected.
- `continueToStagingTarget` has a 60-second wall-clock timeout and up to two
  reissues (three total sends), then returns a logged failure.
- Added logs: `STAGE: origin`, `STAGE: selected`, `STAGE: gave up`,
  `STAGE: goto timeout`; per-candidate `STAGE: candidate` and `STAGE: rejected`
  are behind the existing debug flag.

green: yes (exit `0`, `BUILD SUCCESSFUL in 1s`)
unplanned changes:
- `CANCELLED` now clears to `RUNNING` after one client tick, with
  `HALT: cancelled state cleared; ready for a new task`. This was found while
  reviewing the B5 branch; without it, a cancelled worker stayed halted forever
  and could never accept the next task. It lives on the B5 commit because the
  stack was already built that way.
known gaps:
- “Reachable” is a score heuristic, not a pathfinding probe. A candidate can
  score well and still be unreachable by Baritone.
- No `waypoint -> none` branch was removed; it now logs the staged search and
  either selects or gives up with a reason.

### refactor/tick-owned — NOT STARTED

Findings 10, 16, 17 were not started. B3–B5 are complete and green; the
instruction was to stop rather than leave a half-finished B6. No socket-thread
ownership refactor, sweep ownership change, or placement tri-state has been
attempted. The known thread-ownership gap in B4 remains.

---

## Test tomorrow, in this order

Run from `C:\Projects\swarm\swarm-improved`, checked out at `fix/staging`
(`c366290`).

```bash
export JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
export PATH="/c/Program Files/Java/jdk-25.0.2/bin:$PATH"
./gradlew build
```

### 1. Build once

Expected: `BUILD SUCCESSFUL`, exit `0`. Do not pass `-PcopyToMmc`.

### 2. Deposit twice into two different chests

Configure two chests with different identities, fill the inventory, and let the
bot deposit twice.

Expected:
- one `DEPOSIT: start chest=<dim> <x> <y> <z> reason=...` per chest;
- one `DEPOSIT: complete moved=... stacks=... delta=...` per chest;
- no third `start` for the same chest;
- only one `CHEST: add` per key; a repeat is `CHEST: duplicate ignored`;
- reconnect shows `CHEST: list replaced n=<count> gen=<generation>` and does not
  append duplicates.

Grep: `DEPOSIT:|CHEST:`

### 3. Deposit with a crafting table open

Stand at a crafting table, open it, then trigger a deposit through the normal
manual control.

Expected:
- `DEPOSIT: refused reason=menu-mismatch`;
- no transfer;
- the owned menu closes;
- no `DEPOSIT: complete` for that attempt.

Grep: `DEPOSIT: refused`

### 4. Completion with a failed final sweep

Reach a task completion with a full inventory or a sweep that cannot start.

Expected:
- `TASK: retry <id> attempt=1/2 reason=unproven-completion`;
- then either `TASK: complete <id> evidence=...` only if the sweep observed a
  delta, or `TASK: failed <id> reason=unproven-completion`;
- never an immediate `TASK: complete` on the failed sweep.

Grep: `TASK:`

### 5. Pause and resume

Pause while gathering, wait a few seconds, then resume.

Expected:
- `HALT: paused <id>` and no movement while paused;
- progress is retained; the task does not return to the queue;
- `HALT: resumed <id>` uses the same id after the next client tick;
- host status reports `PAUSED` while paused, not a fresh running task.

Grep: `HALT:`

### 6. Cancel

Cancel the current task.

Expected:
- `HALT: cancelled <id>`;
- the task is gone and the host is told;
- on the next tick, `HALT: cancelled state cleared; ready for a new task`;
- a new task can be assigned without restarting the client.

Grep: `HALT:`

### 7. Hunt UUID follow

Have two eligible animals nearby; start a food hunt and attack one.

Expected:
- `HUNT: target=<uuid> acquired` once;
- no `HUNT: retarget` while that animal is alive and in range;
- after it dies: `HUNT: retarget reason=dead`;
- if it leaves range: `HUNT: retarget reason=out-of-range`.

Grep: `HUNT:`

### 8. Staging search

Enable gather staging and start a gather with no usable waypoint.

Expected:
- `STAGE: origin <x> <y> <z> band=16`;
- `STAGE: selected <x> <y> <z> floor=<block>` with Y inside the band;
- with debug enabled, `STAGE: candidate ... score=... floor=... headroom=2`
  and `STAGE: rejected reason=no-floor|no-headroom|unreachable`;
- if Baritone cannot arrive: `STAGE: goto timeout after=60000 attempt=1`, then
  reissues, then `STAGE: gave up reason=unreachable` (or `no-candidate`).

Grep: `STAGE:`

### 9. Reconnect while chest config is live

Disconnect and reconnect one worker without changing the chest list.

Expected:
- `CHEST: list replaced n=<count> gen=<newer generation>`;
- no appended duplicate entries, no extra deposit cycle for the same chest.

Grep: `CHEST:`

---

## What is still unverified

Everything in-game. No client was launched in this run.

Specifically unverified:
- deposit menu identity and `containerId` matching against real vanilla menus;
- observed item deltas from real shift-clicks;
- chest generation ordering across a real reconnect;
- completion verdicts from `DropCollector` under real inventories;
- pause/resume/cancel host acknowledgement ordering;
- hunt UUID following and retarget timing;
- staging candidate reachability and the 60-second goto timeout;
- all log prefixes above in a real client;
- builder completion, which is currently expected to fail with
  `unproven-completion` because placement evidence does not exist yet;
- shulker completion evidence, which is currently only “a shift-click was
  issued,” not a measured inventory delta.

Known gaps not touched, by instruction: findings 10, 12, 16, 17, 19, 23, 24, 25.
