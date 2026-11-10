# 12 — Web Panel (Netty, port 8888)

`SwarmWebServer` (379) + `SwarmWebHandler` (113) + `SwarmWebPanel` (552, one Java text block of HTML/CSS/JS).

## Server
- `new SwarmWebServer(8888)` created in `EnhancedSwarmHost` ctor (line 71) inside `try { } catch (Throwable ignored) {}`.
- `start()`: `NioEventLoopGroup(1)` + `(2)`, `ServerBootstrap ... .bind(port)` — **binds 0.0.0.0, no auth, no TLS**.
- Pipeline: `HttpServerCodec`, `HttpObjectAggregator(65536)`, `WebSocketServerCompressionHandler`, `SwarmWebHandler`.
- `scheduler.scheduleAtFixedRate(this::broadcastState, 2, 2, SECONDS)` → WS push every 2 s.
- Chat says `http://localhost:<port>` but the bind is all interfaces.

## Routes
| method | path | JSON in | action |
|---|---|---|---|
| GET | `/` | — | serves `SwarmWebPanel.getHtml()` |
| POST | `/api/task/add` | `{item, quantity, bot}` | builds `BotTask(GATHER)`, `queue.addTask`, optional `dispatchNextTask` |
| POST | `/api/task/clear_done` | `{}` | `taskQueue.clearCompleted()` |
| POST | `/api/task/remove` | `{id}` | `removeById(UUID)` |
| POST | `/api/task/pause` | `{bot}` | `sendTo(bot, "swarm:"+bot+" task pause")` |
| POST | `/api/task/resume` | `{bot}` | `sendTo(bot, "swarm:"+bot+" task resume")` |
| POST | `/api/action/follow` | `{}` | follow host player |
| POST | `/api/action/stop` | `{}` | `stop` to target |
| POST | `/api/action/deposit` | `{}` | `deposit` |
| POST | `/api/action/deposit_ores` | `{}` | `deposit ores` |
| POST | `/api/action/deposit_shulkers` | `{}` | `deposit shulkers` |
| POST | `/api/action/pvp_on` | `{}` | `.toggle` 7 combat modules on |
| POST | `/api/action/pvp_off` | `{}` | same, off |
| POST | `/api/action/scan_chests` | `{}` | `scan all` |
| POST | `/api/action/check_fuel` | `{}` | fuel check command |
| POST | `/api/command` | `{target, cmd}` | `swarm:all <cmd>` or `swarm:<target> <cmd>` |
| POST | `/api/nickname` | `{username, nickname}` | `taskQueue.setNickname` |
| WS | `/ws` | — | receives state pushes; inbound frames also routed to `handleApiCommand` |

## State JSON (every 2 s)
```json
{"type":"state","connected":2,
 "bots":[{"username":"Steve","nickname":"Miner1","online":true,"health":20.0,"food":18,
          "x":10,"y":64,"z":-3,"task":"gathering iron","progress":"12/64","eta":"",
          "tier":"IRON","state":"GATHERING"}],
 "tasks":[{"id":"<uuid>","item":"iron_ingot","quantity":64,"progress":"12/64","status":"ACTIVE","bot":"Steve"}],
 "log":["..."]}
```
Offline bots are still emitted with `online:false`, zeros, `tier:"Wood"`, `state:"OFFLINE"`.

## Panel UI
Single self-contained page: dark theme (`#0d0d0d`), header, bot cards with health/food/tier badge
(`.tier-NETHERITE` styling exists), task table (add item/qty, per-task remove/pause/resume), Quick Actions
(follow/stop/deposit/ores/shulkers/pvp/scan/fuel), command box, nickname field, filterable event log.
JS: `new WebSocket('ws://' + location.host + '/ws')`, `post(path,payload)` via `fetch`, re-render on state frames.

## Bugs / risks
1. **`/api/command` with `target:"all"` sends `swarm:all <cmd>`** — the worker strips `swarm:` and sees verb `all`,
   which matches no case, so broadcast commands are swallowed/misrouted. Single-target works (first token is the name).
2. Unauthenticated POSTs on 0.0.0.0 — any LAN host can run `.toggle`, `exec`, `depositall`.
3. `catch (Throwable ignored)` around start (host) and `catch (Exception)` inside `start()` → a port clash silently
   disables the panel.
4. `handleWebSocket` forwards **any** inbound frame to `handleApiCommand(msg, msg)` (comment says unused) — an odd
   backdoor that treats raw WS text as a path.
5. JSON is hand-concatenated (`sb.append`) with a minimal `esc()`; malformed worker names could break frames.
6. 5 × `catch (Exception ignored)` in the web server.
