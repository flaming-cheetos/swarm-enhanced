# Enhanced Swarm MCP server

Drives the swarm mod, the Pi test server and the two local clients over MCP
(Streamable HTTP, single `POST /mcp` endpoint). Zero dependencies, Node >= 25.

Contract: `docs/MCP_SERVER_SPEC.md`. This is v0.1 — the dev-loop core.

## Start

```bash
cd tools/mcp
cp config.example.json config.json     # optional; edit paths if they differ
node server.mjs
```

It prints its URL and a masked token. The token is read from
`SWARM_MCP_TOKEN`, then `config.json`, then the newest
`Web panel: http://127.0.0.1:8888/?token=…` line in the host's `latest.log`.

## Connect Freebuff

MCP servers → **add custom remote server** → URL `http://127.0.0.1:8790/mcp`,
header `Authorization: Bearer <token>` (or `X-Swarm-Token: <token>`).
A client that only speaks the old transport can point at `http://127.0.0.1:8790/sse`.

## Env

| Var | Purpose |
|---|---|
| `SWARM_MCP_TOKEN` | panel token; overrides config and log scraping |
| `SWARM_MC_RCON_PASSWORD` | enables `tick_query`, `tick_rate`, `mc_command` (from `~/minecraft-test/server.properties` on the Pi — never commit it) |
| `SWARM_MCP_ALLOW_DESTRUCTIVE=1` | enables `client_stop`, arbitrary console commands |
| `SWARM_MCP_PORT` / `SWARM_MCP_PANEL` | overrides |

## Tools (v0.1, 29)

**Observe** — `swarm_state`, `bot_state`, `swarm_preflight`, `base_registry`, `world_container`, `world_containers`, `world_blocks`, `task_list`, `swarm_health`, `client_status`

`base_registry` is the single source of truth for where the base stores and smelts: ids, coordinates, double-chest twins, and the measurement each entry came from. `world_container` accepts an `id` so a known container is addressed by name, never by remembered coordinates.

The `world_*` tools ask the worker to read blocks and container blocks and report what is really there, so a claim in a log can be checked against the world. Read-only: nothing is clicked, opened or moved.

`bot_state` asks the worker over the mod socket (`DEBUG_STATE_REQUEST` / `DEBUG_STATE`) for its own brain: plan, current task, withdraw guard, smelt baseline, live inventory, storage-cache age and base anchor. The host only caches what the worker reports — it never guesses. `swarm_preflight` composes those facts into pass/fail readiness checks.
**Control** — `task_add`, `task_cancel`, `task_pause`, `task_resume`, `task_remove`,
`task_clear_done`, `command`, `bot_stop`, `deposit`, `deposit_self`, `scan_chests`, `give`
**Server (RCON)** — `tick_query`, `tick_rate`, `mc_command` (safe-list: tick/fill/setblock/
summon/tp/give/time/weather/gamerule/data/list/seed/say/clear/kill)
**Clients** — `client_launch`, `client_stop`
**Dev** — `build`, `deploy`

## Safety

- Binds `127.0.0.1` only; refuses any other bind.
- Token required on every request; foreign `Origin` → 403.
- `mc_command` refuses anything outside the safe list unless destructive mode is on.
- `client_stop` kills by the PID listening on that instance's panel port, never by name.
- `client_launch` does not toggle the bot brain — it comes up OFF. Toggle with
  `command{target:"jamal",cmd:".toggle bot-brain"}`.
- Server-side writes are limited to the Minecraft server in `~/minecraft-test`; nothing
  else on the Pi is touched.

## Self-test

```bash
npm run selftest
```

Starts the server on a scratch port and checks: health, 401 without token, 403 on
foreign Origin, initialize/version, tools/list, unknown-tool error, destructive gate,
console safe-list, ping, 405 on GET. No Minecraft needed.

## Not in v0.1 yet

`bot_decision_trace`, the world probe, `fault_inject`, `screenshot`, `assert_events`,
`run_scenario`, `events_tail`, watchdog, `timing_report` — see the
spec's §5 and §11 for the v0.2–v0.4 phases. Those need worker-side debug plumbing
(a new socket message type), so they are the next session, not a stub now.
