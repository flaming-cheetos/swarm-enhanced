# H5 Step 4 - Attempt 2/2 - PASS (H5 build 8e2eed6)

- Attempt: **2 of 2** (H5 Steps 1-3 build deployed after baseline failure)
- Mode: **live**
- Result: **PASS**
- Build: `0.1.0+8e2eed6`, source commit `8e2eed6`
- Build stamp: `built=2026-11-12T07:45:34.179538700Z`
- Build artifact: `build/libs/enhanced-swarm-0.1.0+8e2eed6.jar`
- Build command: `./gradlew.bat build` (the artifact's build stamp; the jar was selected and deployed for this attempt)
- Provenance: attempt 1 is the `e62b2f9` pre-H5 baseline; `e62b2f9` is not the successful build and is not claimed here.
- Deployment: copied to both instances' `mods/enhanced-swarm-0.1.0.jar`; `javap` confirms `recoverActiveAfterHostRestart`, `claimNextForBot`, and `finishIfLeaseMatches`
- Live commands: `commands.txt`
- Timestamps: host H5 startup **08:06:41Z**; fresh task ACTIVE **08:07:25Z**; host kill **08:07:46Z**; host down **08:07:51Z**; worker down **08:07:53Z**; host back **08:08:39Z**; worker reconnect **08:08:56Z-08:09:33Z**; completion **08:09:45Z**.

## Task under test

- Task ID: `2b7aa4f5-4f32-43df-b163-0fc13a63c60d`
- Requested: `oak_log x2`, assigned to `jamal`
- Before restart: `ACTIVE`, `attempt: 1`, `statusReason: leased`, lease token present, expiry set (`task-queue-active.json` / `task-queue-before-kill.json`)
- After host startup, before worker: `PENDING`, `attempt: 2`, `lastOwner: jamal`, `statusReason: host-restart`, owner/lease cleared (`task-queue-post-restart.json`)
- After worker reconnect: fresh claim; final queue `COMPLETE`, `attempt: 3`, `statusReason: completed`, lease cleared (`task-queue-complete.json`)

## Step 4 assertion verdicts

| # | Assertion | Verdict | Evidence |
|---|---|---|---|
| 1 | Clear terminal tasks; add `oak_log x2` to `jamal` | **PASS** | panel log `Task added: oak_log x2 -> jamal` at 02:07:25; `active-panel-state.json` |
| 2 | Wait for ACTIVE; record task id/attempt | **PASS** | `task-queue-active.json`: `2b7aa4f5...`, `attempt: 1`, `status: ACTIVE`, lease present |
| 3 | Restart host while task is ACTIVE | **PASS** | `task-queue-before-kill.json` ACTIVE; host kill at 08:07:46Z |
| 4 | After startup: no ACTIVE; PENDING/FAILED with reason | **PASS** | `post-restart-panel-state.json`: no ACTIVE, task PENDING, bots `[]`; `task-queue-post-restart.json`: `statusReason: host-restart`, `attempt: 2`, owner/lease cleared |
| 5 | Reconnect worker; claims new attempt; completes once | **PASS** | `host-complete.log:396`: `New worker connected`; final queue `attempt: 3`; host completion line 398 |
| 6 | Exactly one completion; no duplicate/stranded row | **PASS** | exactly one row with task id; worker `TASK: complete ...` count 1; host `jamal completed: oak_log x2` count 1; no `ACTIVE` row remains |
| 7 | Preserve failure/pass bundle | **PASS** | this directory (logs, queues, panel states, hashes) |

**Panel-field caveat:** the live panel serializes `status` and `reason` (failure reason), but does **not** serialize `attempt`, `statusReason`, `lastOwner`, or the lease fields. The explicit recovery reason and attempt are proven by `task-queue-post-restart.json` and the final queue, not by panel JSON. Panel/log lease visibility remains open work; the panel row itself is correctly `PENDING` and non-ACTIVE.

**Progress caveat:** the completed row still reports `progress: 0/2` / `collected: 0`; completion is proven by the accepted `COMPLETE` transition and the two logs. This progress-counter behavior is pre-existing and was also present in H1; it is not a completion duplicate.

## Decisive lines

- `host-h5-startup.log:311`: `Initializing Enhanced Swarm 0.1.0+8e2eed6 (built 2026-11-12T07:45:34.179538700Z)`
- `host-post-restart.log:311`: `Initializing Enhanced Swarm 0.1.0+8e2eed6 ...`
- `host-complete.log:396`: `New worker connected on localhost.` (08:09:33)
- `host-complete.log:398`: `jamal completed: oak_log x2 in 9s` (08:09:45)
- `worker-complete.log:417`: `TASK: complete 2b7aa4f5-4f32-43df-b163-0fc13a63c60d evidence=verified-empty`
- `task-queue-post-restart.json`: `PENDING`, `attempt: 2`, `statusReason: host-restart`, `assignedBot: ""`, `leaseToken: ""`, `leaseExpiresAt: 0`
- `task-queue-complete.json`: `COMPLETE`, `attempt: 3`, `statusReason: completed`, one row for the id

Counted directly from the committed files: worker `TASK: complete` lines = **1**; host completion lines = **1**; rows matching the task id = **1**; final `ACTIVE` rows = **0**.

## Raw artifacts

| File | SHA-256 (committed, sanitized logs) |
|---|---|
| `active-after-reconnect.json` | `be263ae3b867e2c2f87e6fb8d6337f32ce87c1d3072ea448b1d6b25abfd01bc9` |
| `active-panel-state.json` | `944269bcea0988dad0909da321b2c5fd6a8d930f9a8af37c7b98730da4f245d4` |
| `build-info.properties` | `ae9d1eaaf9a765e4ae73ef802037ab86377071242398f6323f44b30f325dce6e` |
| `commands.txt` | `f8ffc963e0c3fadde049447a1433aba42003cce158b549a2a7403c12940b4a75` |
| `completion-panel-state.json` | `78ac8eee91648da2049c7cb9d99ab6245d9f10299a12a265db11cdf3bc62f164` |
| `h5-startup-panel-state.json` | `d822c21307aa0c24cd408b92859f913f450b9f9b75eb1bbc9051ea372443c2bf` |
| `host-active.log` | `5d678ecdf923a374c239ac393dc5d4e47ae6db143a061b2ebe4c0cc43fef3191` |
| `host-complete.log` | `1880613ac3e08cc17abf8285339f94dd76c124b54ac72e79f592686296d1aa1b` |
| `host-h5-startup.log` | `de30e62eee9e2c2979b30a419db54840968c453cb9d158915a542b923082888f` |
| `host-post-restart.log` | `5ac3907fa59ed7f8dfc1edad4376541182ff006b1f9e4da47c0273d57a7e6503` |
| `host-pre-restart.log` | `5d678ecdf923a374c239ac393dc5d4e47ae6db143a061b2ebe4c0cc43fef3191` |
| `host-shutdown.log` | `c7ca15f4b993bb7304def37eb47f682486efce5c50e625431edfad345f89bda3` |
| `jar-sha256.txt` | `4b020d2a0ea3bdbb655272f2ff4214d1d26cd599fd8e9ac113bd58508ae36aa6` |
| `post-restart-panel-state.json` | `ad278d3ada93a67d390ad3134f20eef60442f3bce5aab6adc4b8e0cb8013febd` |
| `raw-original-log-sha256.txt` | `c78e68f9bcf0eb6428fd02ba7ef568a662e391f407aff54530cfea1c991a0c1c` |
| `task-queue-active.json` | `b513e4e15c8462b900eed40546af4e5226a9a48181aab9e4073fabbc774f4def` |
| `task-queue-before-kill.json` | `dad671c8ae70530dec413cf71768490d2f8603a7207a63725f040fe424851d1e` |
| `task-queue-complete.json` | `c30822762abf8fe2e7542dad7689bb4636e3d277f09d0fd9832bb997bd147e82` |
| `task-queue-h5-startup.json` | `ef2ac057aba1078fe5fe68c6df11b80f435fab5463276ffb991d5440716c62a1` |
| `task-queue-post-restart.json` | `679cb3b111852609f1a2e35a9554b35364aa6fd13a46e9f9f3174946ec75e3b7` |
| `worker-active.log` | `0dc4a9f9555a7c9083ac73412201c79132740a46e22ad562763898cb01a025ec` |
| `worker-complete.log` | `da6e548e1a563c6d4700b8b64bf61cc35ee404658df0dceaff0fc445d4b626d5` |
| `worker-pre-restart.log` | `e0256b7ae6ad309602c1c70468f6bb80d4ab02e9f4f520cfb085a3c213601df2` |
| `worker-shutdown.log` | `c612c51215f046ee63f147e9655fa654aea736b651d5445300aa2ceb8cce2c3f` |

Committed `*.log` copies are byte-identical to the captured files except panel session-token query values were replaced with `<redacted>` after the host was stopped. Exact unredacted originals are kept outside version control under `test-runs/h5-step4-lease-restart/.raw/<attempt>/`; SHA-256 of those originals is in `raw-original-log-sha256.txt`.
