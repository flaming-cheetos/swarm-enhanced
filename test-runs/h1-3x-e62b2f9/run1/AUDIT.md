# H1 Live Run 1/3 — PASS

- Run index: **1 of 3**
- Mode: **live** (`simulation.simulated=false`, `LIVE`)
- Started: 2026-11-12T07:03:41.472Z
- Finished: 2026-11-12T07:04:02.284Z
- Harness run ID: `2026-11-12T07-03-41-472Z-live`
- Bridge: http://127.0.0.1:8899
- Panel: http://127.0.0.1:9999
- Scenario: H1 — gather `oak_log` x1 with evidence
- Client build stamp: **`0.1.0+e62b2f9`** (source commit `e62b2f9`)
- Client build evidence: `bot.log:33`: `- enhanced-swarm 0.1.0+e62b2f9`
- Harness command shape (the harness did not persist argv; token-file paths are intentionally omitted):
  `npm --prefix tools/harness run harness:live -- --base-url http://127.0.0.1:8899 --token-file <harness-token-file> --panel-url http://127.0.0.1:9999 --panel-token-file <panel-token-file> --bot jamal --scenario H1 --host-log <host latest.log> --bot-log <bot latest.log>`
- Build command: the artifact records the runtime Gradle build stamp `0.1.0+e62b2f9`; the exact shell command used to build that jar was not captured in the harness artifact.

## Result

**PASS** — H1 task `1f325668-3a7c-49bb-9908-b45bfcbef7dc` (`GATHER`, `oak_log` x1) ended `COMPLETE` with reason `(empty)`; bot `jamal`; in-game `true`.

Assertions:
- PASS `task.status`: task.status matched
- PASS `task.reason`: task.reason matched
- PASS `taskLogs`: taskLogs matched
- PASS `state.inGame`: state.inGame matched

## Decisive lines

- `bot.log:904`: `[01:04:00] [Render thread/INFO]: [System] [CHAT] [Meteor] [BotBrain] TASK: complete 1f325668-3a7c-49bb-9908-b45bfcbef7dc evidence=verified-empty`
- `host.log:446`: `[01:04:00] [Render thread/INFO]: [System] [CHAT] [Meteor] [Swarm] ✓ jamal completed: oak_log x1 in 18s`

## Consecutive order

- Previous: none; this is the first run in the sequence.
- Next: `run2` starts 2.675 seconds after this run finished.
- No other live H1 run is recorded between these timestamps.

## Raw artifact hashes

| File | SHA-256 |
|---|---|
| `bot.log` | `9ae34612a8a6b41a3523dc49013150e99a5bfc23a08a3433343c53fd83305d1a` |
| `events.jsonl` | `ff731c83278a0ed064a9fdcb494f0fbb56af6aeb3852b98832acf7d30b0c433e` |
| `h1-screenshot.png` | `0d0443266683af15cb3b93981ff6bc93ca87e8f94b29c08fa82aea7552a71470` |
| `h1.json` | `2436b584862894888b531b60cdd6e5088a1a0938587f4ababd81fd396634710e` |
| `h1.result.json` | `2436b584862894888b531b60cdd6e5088a1a0938587f4ababd81fd396634710e` |
| `h1.scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `host.log` | `85b629cbe4af9918eec397a4ad00c4b90cdf7f44e7eaa9a39dd3234a66c16094` |
| `result.json` | `ac636d4f99b5157b1ad058363d0d36f458a09ea49d468c4550c483d788287aff` |
| `run.json` | `ac636d4f99b5157b1ad058363d0d36f458a09ea49d468c4550c483d788287aff` |
| `scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `scenarios.json` | `627b1e33c0d9325989606e6715e0cc62ba46e2b0279782b4b035dddfcb6fcd57` |
| `state.jsonl` | `04227b53757b2f89af6dd45f2dcef07e6719260c00dcd947cb8d5f775defb90a` |
| `summary.md` | `21cce34c94cf39bb01c3608db84ee95d01ef2361a3a80c6f42e97d0ab679d641` |
