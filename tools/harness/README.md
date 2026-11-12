# Enhanced Swarm harness runner

Node 25, no npm dependencies. The runner has two transports:

- **HarnessBridge** — client truth: health, state, screen, menu, screenshot, click.
- **Panel** — control plane: task add/cancel/pause/resume, WebSocket state, logs.

It can run against the contract stubs or a live loopback host/worker.

## Commands

From the repo root:

```powershell
npm --prefix tools/harness run harness:stub
npm --prefix tools/harness run harness:selftest
```

From this directory the equivalent commands are `npm run harness:stub` and
`npm run harness:selftest`.

`harness:stub` starts a loopback bridge stub and a loopback panel stub, then
runs H0, H0b, H1, H2 and H3. It writes one evidence directory under the repo's
`test-runs/`.

The same runner against live clients:

```powershell
npm run harness:live -- `
  --base-url http://127.0.0.1:8899 `
  --token-file C:\path\to\harness-token.txt `
  --panel-url http://127.0.0.1:8888 `
  --panel-token-file C:\path\to\panel-token.txt `
  --bot main `
  --host-log C:\path\to\main\.minecraft\logs\latest.log `
  --bot-log C:\path\to\bot\.minecraft\logs\latest.log `
  --host-queue C:\path\to\main\.minecraft\meteor-client\enhanced-swarm\task-queue.json `
  --bot-queue C:\path\to\bot\.minecraft\meteor-client\enhanced-swarm\task-queue.json
```

The bridge token and panel token are separate. Relative paths resolve from
`tools/harness`; use absolute paths for tokens.

If the bridge is not listening, the command prints an explicit
`SKIP: no client listening` reason and exits 0. A listening bridge with a bad
or missing token fails as an authentication/contract error instead of being
called a skip.

Panel/task scenarios require a panel token. H0/H0b do not.

## Scenario IDs

```text
H0   attach and identify
H0b  attach truth: state, screen, menu, inventory, screenshot
H1   gather oak_log x1 with evidence
H2   cancel while gathering
H3   pause then resume
```

H0b is a runner precondition, not the product catalogue's H1. The product
catalogue's H1 is the gather scenario.

## Transport contract

Bridge:

```text
GET  /harness/health
GET  /harness/state
GET  /harness/screen
GET  /harness/menu
POST /harness/screenshot
POST /harness/menu/click   body: {"slot":N}
```

The bridge stub implements the `403 actions-disabled` and
`409 action-in-flight` paths, plus `409 menu-not-open` and `400 invalid-slot`.

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
```

The panel stub implements the task lifecycle, state broadcasts and log
history. It is not a game simulation; it proves the runner's task/state/log
contract.

## Scenarios

Scenarios are data in `scenarios/*.json`. Each step names an action and an
optional `as` capture name. Assertions use explicit paths:

```json
{"action":"state","as":"state"}
{"path":"state.player.name","exists":true}
```

Task scenarios use `${BOT}` for the live worker name. The stub run supplies
`StubBot`; live runs need `--bot`.

## Evidence

A run writes:

```text
test-runs/<timestamp>-<mode>/
  run.json
  result.json
  scenarios.json
  events.jsonl
  state.jsonl
  summary.md
  h0.json
  h0-screenshot.png
  h0b.json
  h0b-screenshot.png
  h1.json
  h1-screenshot.png
  h0.scenario.json / h0.result.json
  h0b.scenario.json / h0b.result.json
  h1.scenario.json / h1.result.json
  h2.scenario.json / h2.result.json
  h3.scenario.json / h3.result.json
  screens/
  host.log / bot.log              optional live copies, redacted
  task-queue.host.json / task-queue.bot.json  optional live snapshots
```

`events.jsonl` records every executed step. `state.jsonl` records every
bridge/panel state capture. Live log and queue copies are opt-in with the CLI
flags above and are redacted for `token=` values. No token is written to
evidence.

## Self-test

`npm run harness:selftest` runs:

1. the full H0/H0b/H1/H2/H3 stub suite;
2. a corrupted bridge response (`state.player` removed) and asserts H0 fails;
3. a deliberately wrong H0 assertion and asserts the scenario fails;
4. the guard-off `403 actions-disabled` response;
5. concurrent clicks, asserting one `200` and one `409 action-in-flight`;
6. the full stub suite again after the corruption checks.

The self-test exits non-zero if any negative case unexpectedly passes.

## Not yet verified

- Live H0/H0b/H1/H2/H3 against real clients.
- H4 disconnect/reconnect, H5 host restart, H6 full inventory, H8 menu
  mismatch, H9 panel truth, and H10–H12.
- Live log-window capture and contact-sheet generation.
