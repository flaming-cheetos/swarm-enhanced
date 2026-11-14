# H1 Live Run 3/3 — PASS

- Run index: **3 of 3**
- Mode: **live** (`simulation.simulated=false`, `LIVE`)
- Started: 2026-11-12T07:04:24.891Z
- Finished: 2026-11-12T07:04:42.242Z
- Harness run ID: `2026-11-12T07-04-24-891Z-live`
- Bridge: http://127.0.0.1:8899
- Panel: http://127.0.0.1:9999
- Scenario: H1 — gather `oak_log` x1 with evidence
- Client build stamp: **`0.1.0+e62b2f9`** (source commit `e62b2f9`)
- Client build evidence: `bot.log:33`: `- enhanced-swarm 0.1.0+e62b2f9`
- Harness command shape (the harness did not persist argv; token-file paths are intentionally omitted):
  `npm --prefix tools/harness run harness:live -- --base-url http://127.0.0.1:8899 --token-file <harness-token-file> --panel-url http://127.0.0.1:9999 --panel-token-file <panel-token-file> --bot jamal --scenario H1 --host-log <host latest.log> --bot-log <bot latest.log>`
- Build command: the artifact records the runtime Gradle build stamp `0.1.0+e62b2f9`; the exact shell command used to build that jar was not captured in the harness artifact.

## Result

**PASS** — H1 task `09be0a4c-bb05-4df8-bf94-baf8c6b45223` (`GATHER`, `oak_log` x1) ended `COMPLETE` with reason `(empty)`; bot `jamal`; in-game `true`.

Assertions:
- PASS `task.status`: task.status matched
- PASS `task.reason`: task.reason matched
- PASS `taskLogs`: taskLogs matched
- PASS `state.inGame`: state.inGame matched

## Decisive lines

- `bot.log:1016`: `[01:04:42] [Render thread/INFO]: [System] [CHAT] [Meteor] [BotBrain] TASK: complete 09be0a4c-bb05-4df8-bf94-baf8c6b45223 evidence=verified-empty`
- `host.log:448`: `[01:04:42] [Render thread/INFO]: [System] [CHAT] [Meteor] [Swarm] ✓ jamal completed: oak_log x1 in 15s`

## Consecutive order

- Previous: `run2`
- Next: none; this is the third run in the sequence.
- No other live H1 run is recorded between these timestamps.

## Raw artifact hashes

| File | SHA-256 |
|---|---|
| `bot.log` | `3e4c71585d1219f4980387dfddf6803854d68f7283ea9980f2dbda872c1f5bc2` |
| `events.jsonl` | `6cbb1a32359124b754fe172411edadc529e5076f718ff4ecb7ed2e56fc2e43c0` |
| `h1-screenshot.png` | `c26d824f67b680a6b316b9ebc90a61f99d2f520b16d448005e2d45fa2b46ac67` |
| `h1.json` | `7271167dc59440716d6acc432881e055bd8938d5c7207162ec2105a161913c90` |
| `h1.result.json` | `7271167dc59440716d6acc432881e055bd8938d5c7207162ec2105a161913c90` |
| `h1.scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `host.log` | `89bf3cc21fc37380df75950c8a5eca42be9f1147262577498c11c63461d8e620` |
| `result.json` | `e72594b49525c654c525e302e80788460d90de1ddab20ef11879a682485a83ef` |
| `run.json` | `e72594b49525c654c525e302e80788460d90de1ddab20ef11879a682485a83ef` |
| `scenario.json` | `ead9024b9c9108b9d463abc9e85277e54efc8a56bfcd115c617579845dc743f8` |
| `scenarios.json` | `627b1e33c0d9325989606e6715e0cc62ba46e2b0279782b4b035dddfcb6fcd57` |
| `state.jsonl` | `e9e2ba06ab71ec42f9833bd13280b8235cacac01a8c81e58f55be7355267df8f` |
| `summary.md` | `33741bae9536b98b7711272575d7d26e1a63d78d82bde9c0d834e2c9accbce29` |
