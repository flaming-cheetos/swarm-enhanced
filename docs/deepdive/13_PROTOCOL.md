# 13 — Protocol Dictionary

Transport: TCP, `DataInputStream`/`DataOutputStream` **`writeUTF`/`readUTF`** (2-byte length + modified UTF-8),
plaintext, no auth, no version token. Host listens on `serverPort` (default **6969**, all interfaces);
connection read timeout **2000 ms**. Web panel: HTTP/WS on **8888**.

Handshake: worker → `worker:<name>` (first frame), then `REQUEST_CONFIG:<name>`.

## Host → Worker
| payload (example) | sent by | worker behaviour |
|---|---|---|
| `swarm:sync-settings` | Connection:79 | `handleSyncSettings()` — **no-op** |
| `swarm:Steve deposit set ores 100 64 -20` | Connection:97, Host:298/304 | stores chest for category |
| `swarm:Steve base set 100 64 -20` | Connection:108, Host:304 | stores base anchor |
| `swarm:Steve waypoint set base ores 100 64 -20 12 70 -3` | Connection:129 | adds overworld+nether waypoint |
| `task assign <uuid>\|GATHER\|iron_ingot\|64\|Steve` | **Host:355 (bare)** | parses, builds BotTask, hands to BotBrain |
| `swarm:Steve task assign <uuid>\|GATHER\|iron_ingot\|64\|Steve` | **Host:370** | same (prefix optional) |
| `withdraw ores 100 64 -20 minecraft:iron_ingot 32` | **Connection:469 (bare)** | travels to chest and pulls items |
| `RESCAN_REQUESTED:ores` | Connection:478 | rescans category chests |
| `STORAGE_QUERY_RESPONSE:minecraft:iron_ingot:128:` | Connection:381 | forwarded to BotBrain cache |
| `swarm:all <cmd>` / `swarm:Steve <cmd>` | web/commands/GUI | verb dispatch (see 04) |

## Worker → Host
| payload (example) | sent by | host behaviour |
|---|---|---|
| `worker:Steve` | Worker:150 | sets workerName (strips 7 chars) |
| `REQUEST_CONFIG:Steve` | Worker:152 | re-push deposit/base/waypoints |
| `STATUS:Steve:10.5:64.0:-3.2:20.0:18:gathering` | Worker:1355 | `WorkerStatus.deserialize` (8 parts) |
| `BOT_STATUS:Steve:GATHERING:iron 12/64:12:64::20.0:18:10:64:0:IRON` | Worker:1391 | 12-field parse incl. tier |
| `TASK_PROGRESS:Steve:<uuid>:0:64:PENDING:` | Worker:370 | update task by id |
| `TASK_COMPLETE:<uuid>:<n>` | BotBrain/Worker | mark COMPLETE, save |
| `TASK_FAILED:<uuid>:<reason>` | BotBrain/Worker | mark FAILED, save |
| `CHEST_FULL:ores` | Worker:519/698 | log + deposit bookkeeping |
| `SCAN_CHEST_RESULT:ores:100:64:-20\|{"minecraft:iron_ingot":32}` | Worker:1341 | `StorageIndex.applyChestResult` |
| `SCAN_COMPLETE:all` | Worker:1202 | log |
| `SETTINGS:Steve:B:...\|M:...\|S:...` | Worker (removed sync) | parse counters only; B/M/S handlers are no-ops |
| `STORAGE_QUERY_REQUEST:minecraft:iron_ingot:64:` | BotBrain | reply from StorageIndex |
| `WITHDRAW_REQUEST:minecraft:iron_ingot:64:ores` | BotBrain | choose chest, send `withdraw` |
| `WITHDRAW_RESULT:minecraft:iron_ingot:100:64:-20:32` | Worker:1332 | log result |

## Worker verb set (after optional `swarm:` strip)
`exec`, `stop`, `sync-settings`, `say`, `where`, `help`, `deposit`, `waypoint`, `scan`, `withdraw`, `base`,
`depositall`, `task {assign|cancel|pause|resume}`, default → Baritone keyword pass-through (`#goto`, `#mine`, `#stop`).

## Prefix rules (easy to get wrong)
- `Host.sendAll/sendTo` add **nothing**; every caller must include `swarm:`.
- `dispatchNextTask` and `withdraw` omit it; the worker tolerates both by stripping `swarm:` when present.
- `swarm:all` is a *pseudo-worker name*, not a broadcast verb: the worker's parsed verb becomes the first token after it,
  which is why `/api/command` with `target=all` fails (see 12_WEB §bug 1).

## Security
Both ports bind all interfaces, plaintext, no authentication/authorization. Anyone who can reach 6969 can impersonate a
worker (`worker:<name>`) or push `exec`-style payloads; anyone reaching 8888 can POST commands. Read timeouts are the only
liveness guard.
