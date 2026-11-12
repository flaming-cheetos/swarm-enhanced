# Enhanced Swarm Test Harness — 0.2.0 Spec

Status: **APPROVED (decisions 1–6, 2026-11-11) — H0/H0b/H1–H6/H8–H12 stub suite verified; live H0/H0b/H1–H3 pass; H4–H6 and H8–H12 are simulated, manual gates pending**
Direction: **menu-aware HarnessBridge; external MCP fork rejected**
Target build: `0.1.0+e62b2f9` (`fix/phantom-task-types`)
Last updated: 2026-11-12

This document is the contract for the harness, not the harness itself. Nothing
here launches Minecraft. The user starts the host and worker; the harness
attaches to them.

## 1. Why this exists

The current loop is `CODE ONLY`. There are no automated tests, and a task can
look finished while the world did not change. Manual testing is slow, and a
video is not something the assistant can reliably inspect.

The harness must make one question cheap to answer:

> Given a known world and a known action, did the mod do what it claimed, and
> if not, what is the first observable difference?

It is a tripwire and a flight recorder, not a gameplay bot.

## 2. Goals

- Run a scenario against a live host + worker without launching either client.
- Turn a run into a small evidence bundle that a human or the assistant can
  diagnose from text plus a few screenshots.
- Assert terminal task state, evidence, reconnects, deposits, and panel state.
- Separate product failures from infrastructure, environment, and known
  limitations.
- Make the 0.2.0 trust fixes measurable instead of anecdotal.
- Keep the harness out of the shipped jar.

## 3. Non-goals

- No automatic Minecraft/Prism launch in 0.2.0.
- No video understanding and no video dependency for pass/fail.
- No anarchy-server testing. Use a copied local world.
- No auto-enchant, kits, fleet AI, or map renderer. Those are 0.3.0+.
- No claim that a green harness proves the mod is fun, visually correct, or
  safe on a real server.

## 3.1 What runs without you vs what needs you

This harness is **not** a zero-input game tester. It is a repeatable runner for
a staged world.

### Runs unattended after setup

- attach to an already-running host and worker;
- read the panel token from the `Web panel:` log line;
- call the panel API and read `/ws` state;
- add, cancel, pause, resume, and clear tasks;
- wait for task state, logs, timeouts, and terminal states;
- run H1 (gather), H2 (cancel), H3 (pause/resume), H9 (panel truth) once the
  world has the required tree and the clients are connected;
- run simulated H4–H6 and H8–H12 contract scenarios with the stubs and no
  Minecraft clients; these are labelled `SIMULATED`, never live results;
- capture logs, state, queue files, screenshots, and the contact sheet;
- repeat a scenario and compare results.

### Needs you in 0.2.0

- launching or restarting Prism/Minecraft;
- loading or copying the test world and joining the LAN world;
- installing the jar and changing module settings;
- placing the tree, chest, furnace, crafting table, and other fixtures;
- filling or emptying inventory for H6;
- opening a crafting table for the H8 menu-mismatch test;
- killing the bot for H10;
- starting a third client for H11;
- disconnecting the worker process for H4;
- judging whether movement, combat, or visuals look right.

### Could be automated later, with explicit permission

- the menu-aware HarnessBridge below, which removes the manual crafting-table
  and menu steps once it is enabled;
- Prism/Minecraft launch and restart for H4/H5;
- a dedicated local server with RCON/datapack reset commands for fixtures,
  inventory, teleport, death, and block placement.

The honest promise: **the harness removes most of the repeated clicking and
log-reading after you stage the world. It does not remove the game from the
loop.**

## 4. Layers

### Layer A — Live rig (must ship first)

Black-box runner against the real host and worker. The dedicated RPi 5
26.3 server is documented in `docs/TEST_SERVER.md`.

It uses:

- panel HTTP API and `/ws` state feed;
- `X-Swarm-Token` or `?token=`;
- `latest.log` from both Prism instances;
- desktop screenshots and contact sheets;
- task queue files for post-mortems.

This catches the failures that matter: false completion, stranded `ACTIVE`,
deposit mismatches, reconnect behavior, stale panel state.

### Layer B — Protocol rig (second)

Fake host and fake workers speaking the same TCP text protocol, with no
Minecraft. It runs in seconds and injects disconnects, bad messages, duplicate
assignments, and restart sequences.

It cannot test Baritone, menus, or block breaking. Do not let a green protocol
rig create false confidence.

### Layer C — Menu-aware HarnessBridge (selected)

A test-only client bridge inside Enhanced Swarm, native to 26.3. It is not a
second player controller and not a port of the external 1.21.11 MCP mod. It
exposes the screens and containers the tests need, plus exact snapshots.

**Read:**

- player/task/halt state, health, food, position, dimension;
- current screen class, title, and widget tree (type, text, bounds, enabled,
  visible);
- current menu class, title, `containerId`, slot list, cursor stack, player
  inventory, and item components;
- framebuffer PNG screenshots.

**Act:**

- click a widget by id or text, or by coordinates;
- type text, press keys, close the current screen;
- open a block by coordinate/face;
- click a menu slot with `PICKUP`, `QUICK_MOVE`, `SWAP`, `THROW`, or
  `PICKUP_ALL`;
- transfer items between two slots and snapshot the result.

It does **not** move the player, mine, fight, craft on its own, or run its own
task logic. BotBrain remains the system under test; task start/cancel still goes
through the panel API. The bridge only supplies eyes and menu hands.

Minimum menu coverage: player inventory, chest, crafting table, furnace,
smithing table, and shulker box. Anvil and enchanting-table coverage arrives
with the 0.3.0 kit work, not in the first bridge.

Read-only endpoints are implemented behind opt-in config. The first guarded
action is also implemented:

```text
GET  /harness/health
GET  /harness/state
GET  /harness/screen
GET  /harness/menu
POST /harness/screenshot
POST /harness/menu/click   (slot-only QUICK_MOVE; requires allowActions=true)

Pending:
POST /harness/screen/click
POST /harness/screen/type
POST /harness/screen/key
POST /harness/screen/close
POST /harness/menu/transfer
POST /harness/block/use
```

`POST /harness/menu/click` accepts exactly `{"slot":N}`. It requires an open
`AbstractContainerScreen`, validates the menu slot index, and calls the native
`handleContainerInput(..., ContainerInput.QUICK_MOVE, ...)` path once. It holds
a single-action lock with the normal client timeout and returns `result`,
`before`, and `after` menu/inventory snapshots. There is no generic action or
command endpoint.

With `allowActions` omitted or `false`, the click route returns HTTP 403
`actions-disabled` without reading or mutating anything. With the guard on, a
second overlapping action gets HTTP 409 `action-in-flight`.

Per-instance config: `config/enhanced-swarm-harness.json` with `enabled`,
`port`, a 16-256 character `token`, and optional `allowActions` (default
`false`). Missing config leaves the bridge off. It binds `127.0.0.1` only and
requires `X-Harness-Token` or `?token=`.

Implementation status: read-only endpoints and the guarded click are in the
jar. Verified live on 2026-11-11: guard-off returns 403 without state change,
a single QUICK_MOVE click moves exactly one stack, and concurrent actions are
rejected with 409 `action-in-flight`.

Runner status: `tools/harness` has the bridge adapter, the panel adapter, and
deterministic stub-only simulation controls. H0, H0b, H1–H6, H8, H9 and
H10–H12 are implemented and stub-verified, including the 403/409 bridge paths,
task cancel/pause/resume, WebSocket state/logs, stale-state detection, and a
deliberate broken field. H1 means gather; H0b means the read-only truth
precondition. H4–H6 and H8–H12 are **simulated contract scenarios**: their
triggers are reproduced by the stubs and their evidence is labelled
`SIMULATED`. H7 is live-only. Live H0/H0b/H1/H2/H3 passed on 2026-11-12
against `0.1.0+e62b2f9`. The live panel state was initially invalid JSON
(`"type":GATHER"`); `SwarmWebServer` now emits `"type":"GATHER"` and the
repaired state parses. H1/H3 then exposed the BotBrain IDLE orphan guard
failing a newly assigned task before `handleIdle()` could plan it; the guard is
removed. The fixture takes about 20 seconds for a gather, so H1/H3 terminal
waits are 60 seconds; the assertions still require `COMPLETE` and the
completion log. H2 passes with terminal `CANCELLED`; its panel log is
`■ jamal task cancelled: cancelled by user`, while the `HALT` anchor appears in
the worker log. Live H4–H12 remain manual gates.

The bridge runs on the client thread and should avoid mixins; it reads the
current screen/menu and calls their real click/key paths. If a vanilla method
cannot be reached without a mixin, stop and document it before adding one.

The harness calls HTTP directly. An MCP adapter can wrap these endpoints later
for interactive assistant use; MCP is not required for batch tests.

**Why not fork the external `mc-agent-mcp`?** It is a player controller for
1.21.11. It has movement, mining, containers, and crafting, but no screenshot
or screen-widget tree, and no task/panel semantics. Porting its 29 tools and
client mixins to 26.3, then adding the menu layer we actually need, is more work
than this focused bridge. The 26.3 Fabric API MCP server is useful for world
setup, but its client endpoint is inspection-only and cannot click menus.

## 5. Proposed architecture

```text
scenario.json
     |
     v
+-------------------+
|  Live runner      |  Node 25, no npm packages
|  (tools/harness)  |
+---------+---------+
          |  HTTP POST (control)
          |  WS /ws (state + logs)
          |  tail latest.log (host + worker)
          |  PowerShell screenshot sampler
          |  HarnessBridge HTTP (menu/screen state)
          v
   test-runs/<runId>/
     summary.md
     result.json
     scenario.json
     events.jsonl
     state.jsonl
     host.log
     bot.log
     screens/
     contact-sheet.png
     video.mp4            (optional, human only)
```

## 6. Proposed stack

| Part | Choice | Reason |
|---|---|---|
| Runner | Node 25.8.0 | Installed; built-in `fetch`, `WebSocket`, `fs`; no npm packages |
| Screenshots | PowerShell 5.1 + existing `capture-game.ps1` | Already works on this desktop; no new runtime |
| Task control | `/api/*` + `/ws` | Existing panel contract |
| Client control | HarnessBridge HTTP | Menus, screens, screenshots; native 26.3 |
| Logs | `latest.log` tail | Existing, no mod changes |
| Delta proof | HarnessBridge | Exact inventory, menu, and screen assertions |
| Protocol rig | Node test fixtures | Same runtime, no Minecraft |
| CI | `node tools/harness/selftest.mjs` | Protocol rig only; no game in CI |

If you prefer PowerShell-only, the runner can be rewritten, but WebSocket and
JSON handling will be more code for no gain. Node is the recommendation.

## 7. Existing interfaces the harness may use

### Panel

Verified in `SwarmWebServer` / `SwarmWebHandler`:

- `GET /v2?token=<TOKEN>` — panel shell.
- `GET /api/items` — item registry cache.
- `WS /ws?token=<TOKEN>` — state every 2 seconds and log events.
- `POST /api/task/add` — `{"item","qty","type","bot"}`.
- `POST /api/task/remove` — `{"id"}`.
- `POST /api/task/cancel` — `{"bot"}`.
- `POST /api/task/pause` — `{"bot"}`.
- `POST /api/task/resume` — `{"bot"}`.
- `POST /api/task/clear_done` — no body.
- `POST /api/action/cancel|stop|resume|deposit|deposit_ores|deposit_shulkers`.
- `POST /api/command` — `{"target","cmd"}`.

Auth accepts `X-Swarm-Token`, `?token=`, or the `swarm_token` cookie.

**Important:** POST endpoints return `{"ok":true}` even when the request is
rejected by validation. The harness must never treat HTTP 200 as success. It
must assert the resulting log/state.

`/api/action/check_fuel` is HTTP 501. `/api/action/scan_chests` logs that the
container reader is unavailable and sends nothing. Those are known limitations,
not harness failures.

### State feed

Each `state` message contains:

- top level: `modVersion`, `buildCommit`, `hostName`, `dimension`, `connected`,
  `bots`, `tasks`;
- per bot: `username`, `nickname`, `online`, `health`, `food`, `x`, `y`, `z`,
  `task`, `progress`, `eta`, `tier`, `state`, `halted`;
- per task: `id`, `type`, `item`, `quantity`, `progress`, `status`, `reason`,
  `bot`.

The feed has no inventory and no per-bot dimension. The HarnessBridge covers
those gaps.

### Logs

Exact paths on this machine:

```text
C:\Users\rdh\AppData\Roaming\PrismLauncher\instances\main\.minecraft\logs\latest.log
C:\Users\rdh\AppData\Roaming\PrismLauncher\instances\bot\.minecraft\logs\latest.log
```

State files (created on first save; the bot copies exist today, the main copy
may not until the host writes a queue):

```text
<gameDir>\meteor-client\enhanced-swarm\task-queue.json
<gameDir>\meteor-client\enhanced-swarm\deposit-chests.json
<gameDir>\meteor-client\enhanced-swarm\storage-index.json
```

Verified log anchors (worker `latest.log` unless noted):

```text
TASK: complete <id> evidence=inv
TASK: complete <id> evidence=verified-empty
TASK: failed <id> reason=<reason>
TASK: retry <id> attempt=1/2 reason=unproven-completion
TASK: orphaned task in IDLE, failing <id>
DROPS: skipped (inventory full)
Requeued <n> active task(s) from <bot>
HALT: paused <id>
HALT: resumed <id>
HALT: cancelled <id>
HALT: cancelled state cleared; ready for a new task
DEPOSIT: start chest=... reason=...
DEPOSIT: complete moved=... stacks=... delta=...
DEPOSIT: refused reason=menu-mismatch
DEPOSIT: failed reason=... after=... steps
CHEST: add ...
CHEST: duplicate ignored ...
CHEST: list replaced n=<count> gen=<generation>
STAGE: origin ...
STAGE: selected ...
STAGE: gave up reason=unreachable after=...
STAGE: goto timeout after=... attempt=...
Death recorded at <x> <y> <z> (<dimension>)
Death was in <dimension>; refusing cross-dimension recovery
New worker connected on ...
Worker disconnected on ip: ...
Worker will attempt reconnect in 5s.
Web panel: http://<host>:<port>/?token=<token>
```

Panel `/ws` logs use host-level messages: `✓ <bot> completed: <item> x<qty>`,
`■ <bot> task cancelled: cancelled by user`, `Paused: <bot>`, and
`resume received for <bot>`. The `HALT` anchors above come from the worker
log; they are not expected in the panel log window.

## 8. Evidence bundle

Every run writes exactly one directory:

```text
test-runs/<UTC timestamp>-<scenarioId>-<slug>/
```

Required files:

| File | Purpose |
|---|---|
| `summary.md` | Human/assistant-readable verdict, first divergence, repro |
| `result.json` | Machine-readable assertions, durations, verdict |
| `scenario.json` | Exact scenario and resolved variables |
| `events.jsonl` | Normalized event stream |
| `state.jsonl` | Panel snapshots with receive time |
| `simulation.json` | Simulation label and simulated-scenario metadata when applicable |
| `host.log` | Host log window, filtered to the run |
| `bot.log` | Bot log window, filtered to the run |
| `task-queue.host.json` | Queue snapshot at failure/end |
| `task-queue.bot.json` | Worker queue snapshot at failure/end |
| `screens/` | Start, active, terminal, and failure frames |
| `contact-sheet.png` | Six-frame strip for quick visual review |

Optional: `video.mp4` for the human. It is never required for a verdict.

### `summary.md` contract

Must answer in one screen:

1. Scenario and build.
2. Verdict: `PASS`, `FAIL`, `KNOWN_FAILURE`, `XPASS`, `INFRA`, `ENV`, `FLAKE`.
3. What was expected.
4. What actually happened.
5. First divergence, with timestamp and source (`host.log`, `bot.log`,
   `events.jsonl`, or state).
6. Evidence links.
7. Exact repro command.
8. Whether a task was left `ACTIVE`.

No token. No anarchy coordinates. Screenshots are local-test-world only.

A stub run writes `simulation.json`, sets `simulation.simulated=true` in
`run.json`, and marks each H4–H6/H8–H12 result with `simulated: true`. `summary.md`
labels those scenarios `[SIMULATED]` and says they are not live client proof.
A live run that selects a simulated scenario fails explicitly; it does not
silently label the stub as live.

## 9. Scenario format

JSON, no YAML dependency. One example:

```json
{
  "id": "H1",
  "title": "Gather oak logs with evidence",
  "priority": "P0",
  "timeoutSec": 300,
  "setup": {
    "requires": ["host", "worker"],
    "clearTasks": true,
    "collectDrops": true
  },
  "steps": [
    {
      "action": "http",
      "path": "/api/task/add",
      "body": {
        "item": "oak_log",
        "qty": 1,
        "type": "GATHER",
        "bot": "${BOT}"
      }
    },
    {
      "waitFor": {
        "event": "task.terminal",
        "timeoutSec": 180
      }
    }
  ],
  "assert": [
    {"taskStatus": "COMPLETE"},
    {"event": "TASK: complete .* evidence=inv"},
    {"noStrandedActive": true},
    {"stateAgeMsMax": 5000}
  ]
}
```

Supported actions:

- `http` — POST to a panel endpoint.
- `panelSimulate` — stub-only deterministic panel trigger.
- `bridgeSimulate` — stub-only deterministic bridge fixture change.
- `panelTruth` — derived freshness/online/claim view of the received `/ws` state.
- `waitFor` — event, task state, or panel state.
- `sleep` — bounded, explicit.
- `screenshot` — label and optional burst.
- `exec` — only when the scenario sets `"allowExec": true`.
- `captureQueue` — copy the queue files.

Supported assertions:

- task terminal state and reason;
- exact/regex event present or absent, including explicit log order;
- exact collection lengths (`lengthEquals`) and minimums (`lengthMin`);
- no task left `ACTIVE`;
- `TASK: complete` evidence value;
- `TASK: retry` then terminal state;
- reconnect and requeue logs;
- deposit complete/refused and `CHEST_DELTA`;
- state age and `online` count;
- optional inventory/chest/menu delta from the HarnessBridge.

Known failures are declared, not silently skipped:

```json
"knownFailure": {
  "reason": "no server level (multiplayer client)",
  "until": "0.3.0"
}
```

Result is `KNOWN_FAILURE`, not `PASS`. If it unexpectedly passes, result is
`XPASS` and the spec needs updating.

## 10. First scenario catalogue

This is the harness's own gate, not the whole game test plan.

| ID | Priority | Scenario | Proves |
|---|---|---|---|
| H0 | P0 | Attach and identify | Host/worker/panel reachable; token works; no task |
| H1 | P0 | Gather `oak_log x1` | Task reaches `COMPLETE` with evidence; panel `completed` log; no stranded row |
| H2 | P0 | Cancel while gathering | Terminal `CANCELLED`; panel `task cancelled`; worker `HALT: cancelled`; no ghost task |
| H3 | P0 | Pause then resume | Panel `Paused`/`resume received`; worker `HALT: paused`/`resumed`; task stays owned; no duplicate |
| H4 | P0 | Worker disconnect/reconnect | Reconnect log; active task requeued or failed with reason |
| H5 | P0 | Host restart with `ACTIVE` task | No stranded `ACTIVE`; requeue or explicit failure |
| H6 | P0 | Full inventory / no drops | `DROPS: skipped (inventory full)`, `TASK: retry`, then `FAILED reason=unproven-completion` |
| H7 | P1 | Unreachable target/staging timeout | `STAGE: gave up` or `no mining progress`; terminal state |
| H8 | P0 | Deposit with wrong menu | `DEPOSIT: refused reason=menu-mismatch`; no false complete |
| H9 | P0 | Panel truth | Cards/tasks update ≤2s; stale/offline not shown as live |
| H10 | P1 | Death and recovery | Death logged; recovery or cross-dim refusal; no silent loss |
| H11 | P1 | Two workers, one task | Exactly one claim; no duplicate completion |
| H12 | P1 | Known limitations | SMELT fuel, BUILD `unproven-completion` reported honestly |

H0b is the runner-only read-only truth precondition. It is not a product
scenario and does not replace H1 (gather).

### Execution status and live gates

`harness:stub` runs all 13 implemented scenarios in one continuous run. The
simulated ones prove the harness contract and the expected evidence shape,
not the product behavior of a real client.

| ID | Stub status | Simulated? | Live gate |
|---|---|---|---|
| H0 | PASS | no | **PASS live** 2026-11-12; `harness:live -- --scenario H0` |
| H0b | PASS | no | **PASS live** 2026-11-12; `harness:live -- --scenario H0b` |
| H1 | PASS | no | **PASS live** 2026-11-12 on e62b2f9; three consecutive passes in `test-runs/h1-3x-e62b2f9/run1..run3`; `harness:live -- --scenario H1` |
| H2 | PASS | no | **PASS live** 2026-11-12 on e62b2f9; `harness:live -- --scenario H2` |
| H3 | PASS | no | **PASS live** 2026-11-12 on e62b2f9; `harness:live -- --scenario H3` |
| H4 | PASS | yes | Stop the worker ≥5s while ACTIVE; confirm reconnect/requeue. No automated live file yet. |
| H5 | PASS | yes | Restart the host with ACTIVE; no stranded ACTIVE. Step 1 startup recovery and ownership/attempt persistence landed 2026-11-12; `./gradlew.bat h5RecoveryTest` is part of `check`. Live H5 still unrun; leases/fencing pending. Plan: `docs/H5_LEASE_RESTART_PLAN.md`. |
| H6 | PASS | yes | Fill inventory and gather; confirm drops skipped, retry, explicit failure. No automated live file yet. |
| H7 | not implemented | n/a | Unreachable target/no-progress live gate. Not in the stub suite. |
| H8 | PASS | yes | Open crafting table and deposit; confirm menu-mismatch refusal. No automated live file yet. |
| H9 | PASS | yes | Browser task/state/log freshness and stale/offline display. No automated live browser file yet. |
| H10 | PASS | yes | Kill the worker; death, cross-dim refusal, explicit task failure. No automated live file yet. |
| H11 | PASS | yes | Third Prism client if available; one claim/completion. Do not fake it live. |
| H12 | PASS | yes | SMELT fuel and BUILD unproven completion; explicit limitation/failure. No automated live file yet. |

The live-capable rows use the canonical command from `tools/harness/README.md`:

```powershell
npm --prefix tools/harness run harness:live -- `
  --base-url http://127.0.0.1:8899 `
  --token-file <harness-token> `
  --panel-url http://127.0.0.1:9999 `
  --panel-token-file <panel-token> `
  --bot <worker> `
  --scenario H0,H0b,H1,H2,H3
```

H4–H6 and H8–H12 have no automated live scenario in 0.2.0; they remain the
user's manual final gate. H7 is live-only and has no stub scenario at all.

H5 Step 1 startup recovery landed on 2026-11-12: `TaskQueue.load()` requeues
persisted `ACTIVE` rows with owner/attempt/reason, and
`TaskQueueRecoveryTest` is part of the Gradle `check` task. The simulated H5
still only asserts the stub contract, and live H5 has not been run; lease
expiry and stale-completion fencing remain open. The implementation plan is
tracked in `docs/H5_LEASE_RESTART_PLAN.md`; do not weaken the assertion.

H11 needs a third Prism instance live. The stub uses a second simulated worker
to prove single-claim bookkeeping; live remains `ENV` if unavailable. Do not
fake a second worker live.

## 11. Debugging without video

The assistant can read text and still images; it cannot watch video. The
harness must therefore produce a diagnosis, not a recording.

Required behavior:

- Capture a state snapshot at every task transition and every 2 seconds.
- Capture a screenshot at start, task active, terminal, and failure.
- On failure, keep the last 200 log lines from both sides around the failure.
- Record the first divergence: expected event vs actual event, with timestamps.
- Build a `contact-sheet.png` from six frames so one image shows the sequence.
- Keep full-size failure frames for detail.
- Never make a verdict depend on a video.

The existing capture script is at:

```text
C:\Projects\minecraft testing\capture-game.ps1
C:\Projects\minecraft testing\CAPTURE-README.md
```

It samples the desktop, not a window, and writes frames plus contact sheets.
The live rig may call it for a burst around a failure. Screenshots are
supplemental evidence; logs and state are the assertion source.

## 12. Failure classification

| Class | Meaning | Verdict |
|---|---|---|
| `INFRA` | Host/worker/panel/token/log unavailable | Not a product result |
| `PRODUCT` | Wrong state, false evidence, stranded task, bad deposit | Bug |
| `KNOWN` | Declared limitation, e.g. SMELT fuel | `KNOWN_FAILURE` |
| `ENV` | Missing world fixture, no tree/chest, third client absent | Skip, fix fixture |
| `FLAKE` | Timing/race; rerun 3x, same result 2/3 | Report as flake |
| `XPASS` | Known failure unexpectedly passes | Investigate/update spec |

No verdict without evidence. “I think it worked” is not a result.

## 13. Safety and privacy

- The harness never launches Minecraft or Prism in 0.2.0.
- It binds to the existing loopback panel only.
- The token is read from the `Web panel:` line and redacted from every artifact.
- It sends no destructive action unless the scenario declares it.
- It uses a copied local test world, never an anarchy base.
- Screenshots may contain coordinates; keep `test-runs/` out of git and do not
  use them on a real base.
- No video is uploaded anywhere; it is local and optional.
- The HarnessBridge is test-only: loopback bind, bearer token, disabled by
  default, no arbitrary commands, no anarchy server.

## 14. 0.2.0 acceptance criteria

The harness is ready when:

0. The all-scenario stub suite is green: H0, H0b, H1–H6, H8, H9 and H10–H12.
   H4–H6/H8–H12 results are labelled `SIMULATED`; this is contract evidence,
   not a live pass.
1. `H0`, `H1`, `H2`, `H3`, `H4`, `H6`, `H8`, `H9` pass against a real host and
   worker.
2. `H5` passes after the lease/restart fix; before that it fails with a clear
   `PRODUCT` verdict, not a timeout.
3. `H1` passes three consecutive runs, not one lucky run.
4. Every failure bundle contains `summary.md`, `events.jsonl`, `state.jsonl`,
   both log windows, and at least one screenshot.
5. Every failed scenario names a first divergence.
6. No run leaves a task `ACTIVE` unless the scenario is specifically proving
   that bug.
7. `gradlew.bat build` stays green; the Node runner is not in the jar. The
   read-only bridge is in the jar but inactive without config.
8. The protocol rig self-test runs without Minecraft.
9. A stranger can read `summary.md` and know what to fix in under a minute.

The 0.2.0 product work after the harness: task leases/restart recovery, job
plans with evidence, inventory ledger/reservations, event stream, panel shell,
host-assigned roles, and persistent deaths. 0.3.0 is kits and enchantments.

## 15. Implementation order

1. **Runner skeleton** — attach, token, logs, state, evidence folder, H0.
2. **H1** — gather happy path; prove the evidence bundle is usable.
3. **H2–H4** — cancel, pause/resume, reconnect.
4. **H5** — restart with `ACTIVE`; file the first real bug.
5. **H6/H8/H9** — full inventory, deposit mismatch, panel truth.
6. **H7/H10/H11/H12** — path, death, two workers, known limitations.
7. **HarnessBridge** — menu/screen snapshots, click/type, screenshots; off by default.
8. **Protocol rig** — fake host/worker, CI self-test.
9. **Scorecard** — one command, one summary, pass/fail counts.

Status: steps 1–6 have a green stub contract suite (H4–H6/H8–H12 simulated).
Live H0, H0b, H1, H2, and H3 pass on `0.1.0+e62b2f9`. The H4–H6/H8–H12 manual
gates remain pending. H7 is live-only and has no stub scenario. Step 9
(scorecard) is not implemented.

Do not start 0.3.0 auto-enchant until this list is green or explicitly waived.

## 16. Decisions confirmed

Confirmed 2026-11-11:

1. **Node 25 + PowerShell 5.1 + existing capture script** are the live rig.
   No npm packages, no new jar dependencies.
2. **`test-runs/` lives in the repo and is git-ignored.**
3. **A menu-aware HarnessBridge is the selected direction**, replacing the
   earlier read-only probe. Test-only, off by default, loopback + token.
4. **Video is optional.** Keyframes and text are the contract. Video is for the
   human, not the assistant.
5. **The 0.2.0 gate is approved:** H0–H6, H8, H9 green; H7/H10/H11/H12 may be
   `ENV` or `KNOWN` with a written reason.
6. **The copied local test-world fixture is approved:** known tree, chest,
   furnace, crafting table; no anarchy base.
7. **The external 1.21.11 MCP fork is rejected.** Its missing screen/menu layer
   and 26.3 port cost are not worth it.
8. **The bridge is test-only and menu-focused.** Read/click/type/screenshot
   only; no movement, no task logic, no arbitrary commands.

The read-only bridge (health, state, screen, menu, screenshot) is implemented.
The runner has the bridge adapter, panel adapter, and stub-only simulation
controls. H0/H0b/H1–H6/H8/H9/H10–H12 are stub-verified; H4–H12 are explicitly
simulated. Live H0, H0b, H1, H2, and H3 pass on `0.1.0+e62b2f9`. The next step
is the manual H4–H6/H8–H12 gates. H7 remains live-only.

## 17. Bottom line on “fully test without my input”

No. A harness cannot fully test this game without input because the client,
world, menus, other players, and visual judgment are outside the panel API.
It can fully run the **staged P0 suite** without input once the host and worker
are running in the fixture world and the bridge is enabled. Cold start, world
setup, process restarts, death, a second worker, and “does this look right”
still need a human or a later process-control/test-server layer. Menu states
are manual only until the bridge lands.
