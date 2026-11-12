# H1 Live Run 2/3 — PASS

- Run index: **2 of 3**
- Mode: **live** (`simulation.simulated=false`, `LIVE`)
- Started: 2026-11-12T07:04:04.959Z
- Finished: 2026-11-12T07:04:22.246Z
- Harness run ID: `2026-11-12T07-04-04-959Z-live`
- Bridge: http://127.0.0.1:8899
- Panel: http://127.0.0.1:9999
- Scenario: H1 — gather `oak_log` x1 with evidence
- Client build stamp: **`0.1.0+e62b2f9`** (source commit `e62b2f9`)
- Client build evidence: `bot.log:33`: `- enhanced-swarm 0.1.0+e62b2f9`
- Harness command shape (the harness did not persist argv; token-file paths are intentionally omitted):
  `npm --prefix tools/harness run harness:live -- --base-url http://127.0.0.1:8899 --token-file <harness-token-file> --panel-url http://127.0.0.1:9999 --panel-token-file <panel-token-file> --bot jamal --scenario H1 --host-log <host latest.log> --bot-log <bot latest.log>`
- Build command: the artifact records the runtime Gradle build stamp `0.1.0+e62b2f9`; the exact shell command used to build that jar was not captured in the harness artifact.

## Result

**PASS** — H1 task `321cdc64-2f23-4a40-9521-89e830476e4c` (`GATHER`, `oak_log` x1) ended `COMPLETE` with reason `(empty)`; bot `jamal`; in-game `true`.

Assertions:
- PASS `task.status`: task.status matched
- PASS `task.reason`: task.reason matched
- PASS `taskLogs`: taskLogs matched
- PASS `state.inGame`: state.inGame matched

## Decisive lines

- `bot.log:955`: `[01:04:21] [Render thread/INFO]: [System] [CHAT] [Meteor] [BotBrain] TASK: complete 321cdc64-2f23-4a40-9521-89e830476e4c evidence=verified-empty`
- `host.log:447`: `[01:04:21] [Render thread/INFO]: [System] [CHAT] [Meteor] [Swarm] ✓ jamal completed: oak_log x1 in 15s`

## Consecutive order

- Previous: `run1`
- Next: `run3` starts 2.645 seconds after this run finished.
- No other live H1 run is recorded between these timestamps.

## Raw artifact hashes

| File | SHA-256 |
|---|---|
| `bot.log` | `ef7c8abdd5e14c9fdfaea53e7d2fe6d197bf07e68fc2ea878f3da9b5f80403b3` |
| `events.jsonl` | `a0e2562d97b4131bfc750f87b02193410b0f072f1eb263d493765723845a9978` |
| `h1-screenshot.png` | `2125608fd280bfea6f93b32b35b083f1756f0cd78e3edeae68b35191fe2bb370` |
| `h1.json` | `68521117bdc2b481a5a22843fe3a3a8d349d4433cd05a48f1c1d3786aab58fcd` |
| `h1.result.json` | `68521117bdc2b481a5a22843fe3a3a8d349d4433cd05a48f1c1d3786aab58fcd` |
| `h1.scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `host.log` | `7ef3a08c571e3fd55835306581e8d214c3762bf3a39ce82f8ccc896bfd561d80` |
| `result.json` | `acccc7c18854507806cd6004d5c0b9ef220398c19bdd5298890e967801a7ed20` |
| `run.json` | `acccc7c18854507806cd6004d5c0b9ef220398c19bdd5298890e967801a7ed20` |
| `scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `scenarios.json` | `627b1e33c0d9325989606e6715e0cc62ba46e2b0279782b4b035dddfcb6fcd57` |
| `state.jsonl` | `c3c2f882fab3daba057bb554def47b32e67431bbd94c4ae1675f107b70179998` |
| `summary.md` | `1d90bd9143c49d58c7ef05731b7fcbd2271bd79c7828e1693e861a87d04c57d4` |
