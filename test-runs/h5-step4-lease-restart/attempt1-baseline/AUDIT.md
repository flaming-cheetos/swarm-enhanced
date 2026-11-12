# H5 Step 4 - Attempt 1/2 - BASELINE FAIL (e62b2f9)

- Attempt: **1 of 2** (pre-H5 baseline)
- Mode: **live**
- Result: **FAIL** (expected baseline behavior; this is the failure the H5 build fixes)
- Build: `0.1.0+e62b2f9`, source commit `e62b2f9`
- Build stamp: `built=2026-11-12T06:12:47.685502200Z`
- Build artifact: `build/libs/enhanced-swarm-0.1.0+e62b2f9.jar`
- Build command: `./gradlew.bat build` (the artifact's build stamp; no rebuild was performed for this attempt)
- Live command: see `commands.txt`
- Timestamps: ACTIVE snapshot before kill **2026-11-12T08:02:58Z**; host panel down **08:03:02Z**; host panel back **08:03:54Z**; post-restart state **08:03:55Z**.

## Task under test

- Task ID: `64e1fd2f-0c39-4976-ad2f-96ed65890880`
- Requested: `oak_log x2`, assigned to `jamal`
- ACTIVE snapshot: `task-queue-active.json` and `active-panel-state.json`
- Baseline queue fields: **no `attempt`, no `lastOwner`, no `statusReason`, no lease fields** (the e62b2f9 build predates H5).
- Restart: host JVMs were terminated while the row was ACTIVE; worker was then stopped so the post-startup state could be captured without a fast reconnect masking it.

## Step 4 assertion verdicts

| # | Assertion | Verdict | Evidence |
|---|---|---|---|
| 1 | Clear terminal tasks; add `oak_log x2` to `jamal` | **PASS** | `active-panel-state.json` task row; queue ACTIVE |
| 2 | Wait for ACTIVE; record task id/attempt | **PASS (id), attempt unavailable** | queue has no attempt field in baseline |
| 3 | Restart host while task is ACTIVE | **PASS** | `task-queue-before-kill.json`; host stopped 08:02:58Z |
| 4 | After startup: no ACTIVE; PENDING/FAILED with reason | **FAIL** | `post-restart-panel-state.json`: ACTIVE, reason empty; `task-queue-post-restart.json`: ACTIVE, no reason/attempt |
| 5 | Reconnect worker; new attempt; complete once | **FAIL** | worker logged `TASK: complete 64e1...` at 08:03:28Z while host was down; host never accepted it; no new attempt; panel bots empty |
| 6 | Exactly one completion; no duplicate/stranded row | **FAIL** | one stranded ACTIVE row; no accepted completion; worker completion is unacknowledged |
| 7 | Preserve failure bundle | **PASS** | this directory (logs, queues, panel states, hashes) |

## Decisive lines

- `host-pre-restart.log:310`: `Initializing Enhanced Swarm 0.1.0+e62b2f9 (built 2026-11-12T06:12:47.685502200Z)`
- `worker-post-reconnect.log:465`: `TASK: complete 64e1fd2f-0c39-4976-ad2f-96ed65890880 evidence=verified-empty`
- `host-post-restart.log:311`: `Initializing Enhanced Swarm 0.1.0+e62b2f9 ...`
- `post-restart-panel-state.json`: task `64e1fd2f...` is `ACTIVE`, progress `0/2`, reason `""`, bots `[]`
- `task-queue-post-restart.json`: task is `ACTIVE`, assigned `jamal`, with no attempt/lease/reason fields.

The worker completing while the host was down is not a pass: the restarted host never accepted or logged a completion, and the task stayed ACTIVE. This is exactly the stranded-row failure H5 exists to catch.

Counted directly from the committed files: `ACTIVE` rows after restart = **1**; accepted `COMPLETE` rows = **0**; worker completion lines = **1** (unacknowledged).

## Raw artifacts

| File | SHA-256 (committed, sanitized logs) |
|---|---|
| `active-panel-state.json` | `2142bb43367fabc465521da5eaa99399923f8adf85b3a9f03e43aaec958092cd` |
| `build-info.properties` | `51621619b6dd1a4a7520ab9463c39bf8b0049518dd9bbbd299d48b640b172efa` |
| `commands.txt` | `9dc32c307e1451713f6444f42491a1fcd876f7dd21d94214ab64b72ae38c99a1` |
| `host-post-reconnect.log` | `b4d7ec7d77004f001a43eba1d0722d6e96fd7541abb09f4c6d7a0590f1a74312` |
| `host-post-restart.log` | `effeba3a54e45077348e861db8e8730c7efdb5bacf9d1a8708d828cf853f15c6` |
| `host-pre-restart.log` | `70cb7c0664f3fbeae680906b670c80f247fcb335c1d56027b4402f16bf9e85e6` |
| `initial-panel-message.json` | `04162eadb192e891320bef534f70865957deb5c961fbed599263a40f77d1ce21` |
| `jar-sha256.txt` | `71078704a451094e9e992d1cdb51186e6e7c2db6200f9d9c2a9fdf2cb6d520b7` |
| `post-reconnect-panel-state.json` | `92d876d61be2a14c97cc6c6c7c9f9c62a7692872972b376ea836c931c87eacb0` |
| `post-restart-panel-state.json` | `bcdfa9ff3782c602eb9eaac31d7e88662bdfda4a8cee40aae07a749cee181439` |
| `raw-original-log-sha256.txt` | `b4eed34a17d49a4e93fbf67547751221a3dea140e34f1bf76a790feab41a84c8` |
| `task-queue-active.json` | `05201a691c32bdde8e5b26428b05bfc49174096002771e840de1a6229bbd2e56` |
| `task-queue-before-kill.json` | `05201a691c32bdde8e5b26428b05bfc49174096002771e840de1a6229bbd2e56` |
| `task-queue-initial.json` | `f486398c396c6c606c7bbf9a462adb80f45b86cdb9e7844ecaf04eae7239af76` |
| `task-queue-post-reconnect.json` | `05201a691c32bdde8e5b26428b05bfc49174096002771e840de1a6229bbd2e56` |
| `task-queue-post-restart.json` | `05201a691c32bdde8e5b26428b05bfc49174096002771e840de1a6229bbd2e56` |
| `worker-post-reconnect.log` | `e422cad6c02e3411ff3e5998ade5800ba3f824ad92ae10abd0466271e1857845` |
| `worker-post-restart.log` | `07b3912c438c32825cd151a642a1c9ed51573dd7be20b25c40c30748c8c97b16` |
| `worker-pre-restart.log` | `97874d73f26c95f36bf3647e1a87cf4f563f409870f4e05dadcad7c1d2a8f129` |

Committed `*.log` copies are byte-identical to the captured files except panel session-token query values were replaced with `<redacted>` after the host was stopped. Exact unredacted originals are kept outside version control under `test-runs/h5-step4-lease-restart/.raw/<attempt>/`; SHA-256 of those originals is in `raw-original-log-sha256.txt`.
