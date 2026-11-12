# Enhanced Swarm harness runner

Node 25, no npm dependencies. The runner has two transports:

- **HarnessBridge** — client truth: health, state, screen, menu, screenshot, click.
- **Panel** — control plane: task add/cancel/pause/resume, WebSocket state, logs.

It runs against the contract stubs or a live loopback host/worker. The stubs
also expose deterministic simulation controls for triggers that need a client
restart, a full inventory, a wrong menu, a panel disconnect, a death, or a
second worker. Those controls exist **only in the stubs**.

H1 means gather. H0b is the read-only attach-truth precondition, not a product
scenario. H4-H6 and H8-H12 are simulated contract scenarios; their live
variants remain the final gate until the clients and fixtures are available.

## Commands

From the repo root:

```powershell
npm --prefix tools/harness run harness:stub
npm --prefix tools/harness run harness:selftest
```

From this directory the equivalent commands are `npm run harness:stub` and
`npm run harness:selftest`.

`harness:stub` starts a loopback bridge stub and a loopback panel stub, then
runs all 13 scenarios in one process: H0, H0b, H1-H6, H8, H9, and H10-H12. It
writes one evidence directory under the repo's `test-runs/` and labels the
simulated scenarios in `run.json`, `simulation.json`, and `summary.md`.

### Automated live pass

Only H0/H0b/H1-H3 are automated against real clients. Run:

```powershell
npm run harness:live -- `
  --base-url http://127.0.0.1:8899 `
  --token-file C:\path\to\harness-token.txt `
  --panel-url http://127.0.0.1:9999 `
  --panel-token-file C:\path\to\panel-token.txt `
  --bot main `
  --scenario H0,H0b,H1,H2,H3 `
  --host-log C:\path\to\main\.minecraft\logs\latest.log `
  --bot-log C:\path\to\bot\.minecraft\logs\latest.log `
  --host-queue C:\path\to\main\.minecraft\meteor-client\enhanced-swarm\task-queue.json `
  --bot-queue C:\path\to\bot\.minecraft\meteor-client\enhanced-swarm\task-queue.json
```

Live status 2026-11-12: H0/H0b/H2 PASS at build `44f292f`; H1/H3 are pending a
relaunch with the rebuilt BotBrain. Evidence is under
`test-runs/live-h0-h0b-44f292f`, `test-runs/live-h2-44f292f`, and the failed
H1/H3 attempt under `test-runs/live-h1-h3-44f292f`.

H4-H6 and H8-H12 must **not** be passed to `harness:live` yet: their simulation
controls do not exist on the real bridge/panel. H7 has no stub scenario. Use
the manual live gates in the matrix below. A live run that selects a simulated
scenario fails explicitly instead of pretending to be live.

The bridge token and panel token are separate. Relative paths resolve from
`tools/harness`; use absolute paths for tokens. If the bridge is not
listening, the command prints `SKIP: no client listening` and exits 0. A
listening bridge with a bad token fails as an authentication/contract error.

Panel/task scenarios require a panel token. H0/H0b do not.

## Scenario matrix

| ID | Stub run | Status | Live gate |
|---|---|---|---|
| H0 | PASS | live-capable | `harness:live -- --scenario H0` |
| H0b | PASS | live-capable precondition | `harness:live -- --scenario H0b` |
| H1 | PASS | live-capable; gather | `harness:live -- --scenario H1` |
| H2 | PASS | live-capable | `harness:live -- --scenario H2` |
| H3 | PASS | live-capable | `harness:live -- --scenario H3` |
| H4 | PASS | **SIMULATED** | Stop the worker for ≥5s while a task is ACTIVE; confirm reconnect + requeue/failure. |
| H5 | PASS | **SIMULATED** | Restart the host with an ACTIVE task; no stranded `ACTIVE` row. The product may still expose the restart bug until leases land. |
| H6 | PASS | **SIMULATED** | Fill the worker inventory; confirm `DROPS: skipped (inventory full)`, retry, then `FAILED reason=unproven-completion`. |
| H7 | not implemented | live-only | Make the target unreachable; confirm `STAGE: gave up` or no-mining-progress terminal state. Not part of the stub suite. |
| H8 | PASS | **SIMULATED** | Open a crafting table, attempt a deposit, confirm `DEPOSIT: refused reason=menu-mismatch` and no `DEPOSIT: complete`. |
| H9 | PASS | **SIMULATED** | In the browser, add a task, watch cards/logs update within 2s, then verify stale/offline state is not shown as live. |
| H10 | PASS | **SIMULATED** | Kill the worker; confirm death + cross-dimension refusal and an explicit task failure. |
| H11 | PASS | **SIMULATED** | If a third Prism client is available, submit one task to two workers; exactly one claim/completion. Do not fake the third client live. |
| H12 | PASS | **SIMULATED** | Run SMELT without fuel and BUILD without proof; both must report explicit failure/limitation, never silent success. |

The `harness:live` command in the table is shorthand for the full command
above; run the full command once with the desired `--scenario` IDs. H4-H6 and
H8-H12 have no automated live scenario yet; H7 is live-only.

## Scenario IDs and assertions

```text
H0   attach and identify
H0b  attach truth: state, screen, menu, inventory, screenshot
H1   gather oak_log x1 with evidence
H2   cancel while gathering
H3   pause then resume
H4   worker disconnect/reconnect (simulated)
H5   host restart with ACTIVE task (simulated)
H6   full inventory/unproven completion (simulated)
H8   deposit with wrong menu (simulated)
H9   panel task/state/log truth and stale detection (simulated)
H10  death and recovery (simulated)
H11  two workers, one task (simulated)
H12  known limitations (simulated)
```

Scenarios are data in `scenarios/*.json`. Each step names an action and an
optional `as` capture name. Assertions use explicit paths:

```json
{"action":"state","as":"state"}
{"path":"state.player.name","exists":true}
```

Supported assertions include `equals`, `equalsPath`, `exists`, `lengthMin`,
`lengthEquals`, `contains`, `containsInOrder`, `notContains`, and `type`.

Task scenarios use `${BOT}` for the live worker name. The stub run supplies
`StubBot`; live runs need `--bot`.

Simulated scenarios declare:

```json
"simulated": true,
"simulation": {
  "trigger": "what the stub reproduces",
  "liveGate": "what the user still has to verify against real clients"
}
```

Stub-only steps are `bridgeSimulate` and `panelSimulate`; `panelTruth` reads
the live `/ws` state already received by the adapter. They fail in live mode.

## Transport contract

Bridge:

```text
GET  /harness/health
GET  /harness/state
GET  /harness/screen
GET  /harness/menu
POST /harness/screenshot
POST /harness/menu/click   body: {"slot":N}
POST /harness/simulate     stub only
```

Panel:

```text
WS   /ws?token=<panel-token>
GET  /api/items
POST /api/task/add          {"item","qty","type","bot"}
POST /api/task/clear_done
POST /api/task/remove       {"id"}
POST /api/task/cancel       {"bot"}
POST /api/task/pause        {"bot"}
POST /api/task/resume       {"bot"}
POST /api/action/<name>
POST /api/simulate          stub only
```

The panel stub implements the task lifecycle, task API, `/ws` state and log
history, and deterministic simulation hooks. It is not a game simulation; it
proves the runner's task/state/log contract. The bridge stub implements the
read-only snapshot plus `403 actions-disabled`, `409 action-in-flight`,
`409 menu-not-open`, and `400 invalid-slot` paths.

## Evidence

A run writes:

```text
test-runs/<timestamp>-<mode>/
  run.json
  result.json
  scenarios.json
  simulation.json              when any scenario is simulated
  events.jsonl
  state.jsonl
  summary.md
  h0.json / h0-screenshot.png
  h0b.json / h0b-screenshot.png
  h1.json / h1-screenshot.png
  h4.json ... h12.json
  h8-screenshot.png
  <id>.scenario.json / <id>.result.json for every scenario
  screens/
  host.log / bot.log              optional live copies, redacted
  task-queue.host.json / task-queue.bot.json  optional live snapshots
```

`events.jsonl` records every executed step. `state.jsonl` records every
bridge/panel state capture. `simulation.json` and each result's `simulated`
field label stub-only evidence. Live log and queue copies are opt-in with the
CLI flags above and are redacted for `token=` values. No token is written to
evidence.

## Self-test

`npm run harness:selftest` runs:

1. the full 13-scenario stub suite;
2. a corrupted bridge response (`state.player` removed) and asserts H0 fails;
3. a deliberately wrong H0 assertion and asserts the scenario fails;
4. the guard-off `403 actions-disabled` response;
5. concurrent clicks, asserting one `200` and one `409 action-in-flight`;
6. one normal run plus one wrong-assertion run for each simulated family
   H4, H5, H6, H8, H9, H10, H11, and H12, requiring the specific assertion
   path to fail (including H9 stale detection);
7. the full stub suite again after the corruption checks.

The self-test exits non-zero if any negative case unexpectedly passes.

## Not yet verified

- Live H0/H0b/H1-H3 against real clients.
- Live H4-H6/H8-H12 manual gates above. H7 has no stub scenario.
- Live log-window capture and contact-sheet generation.
- A live H9 browser assertion; the stub proves the API/WebSocket contract,
  not the browser rendering.
