# 03 — EnhancedSwarmConnection (589 lines)

One instance per accepted socket, `extends Thread`, holds `messageQueue` drained by its own run loop.

## run() step by step
1. `ChatUtils.infoPrefix("Swarm","New worker connected on %s.", getIp(...))` and `webServer.logInfo(...)`.
2. **Broken line 47** (see below) inside `try { } catch (Exception ignored) {}` — currently a compile error.
3. `out = new DataOutputStream(socket.getOutputStream()); in = new DataInputStream(...)`.
4. `socket.setSoTimeout(2000)` → enables the read-timeout poll loop.
5. First frame: `in.readUTF()`; if it starts with `worker:` take `substring(7).trim()` as name, else use raw string.
   `SocketTimeoutException` → continue unnamed; other `IOException` → disconnect.
6. Queue `swarm:sync-settings`; async thread sleeps 500 ms then pushes this server's chests
   (`swarm:<w> deposit set ...`), base (`swarm:<w> base set ...`), and waypoints
   (`swarm:<w> waypoint set <name> <cat> <owx> <owy> <owz> <nx> <ny> <nz>`).
7. Loop: read with 2 s timeout; on each iteration drain `messageQueue` with `out.writeUTF(msg)`; on timeout just loop.
8. On disconnect: interrupt, close, remove from host list.

## Inbound branches (all `read.startsWith(...)`)
| prefix | example | host reaction |
|---|---|---|
| `REQUEST_CONFIG:` | `REQUEST_CONFIG:Steve` | logs, re-pushes deposit/base/waypoint config to that name |
| `STATUS` | `STATUS:Steve:10.5:64.0:-3.2:20.0:18:gathering` | `WorkerStatus.deserialize` (8 parts), `putBotStatus` |
| `TASK_PROGRESS:` | `TASK_PROGRESS:Steve:<uuid>:0:64:PENDING:` | finds task by id, updates collected/status/eta, assigns bot |
| `TASK_COMPLETE:` | `TASK_COMPLETE:<uuid>:<qty>` | marks COMPLETE, saves queue |
| `TASK_FAILED:` | `TASK_FAILED:<uuid>:<reason>` | marks FAILED, saves queue |
| `BOT_STATUS:` | `BOT_STATUS:Steve:MINING:iron 12/64:12:64::20.0:18:10:64:0:IRON` | 12-field BotStatus parse incl. tier |
| `CHEST_FULL:` | `CHEST_FULL:ores` | logs; triggers deposit bookkeeping |
| `SCAN_CHEST_RESULT:` | `SCAN_CHEST_RESULT:ores:100:64:-20|{"minecraft:iron_ingot":32}` | `storageIndex.applyChestResult(...)` |
| `SCAN_COMPLETE:` | `SCAN_COMPLETE:all` | logs completion |
| `SETTINGS:` | `SETTINGS:Steve:B:followRange=4;...|M:...|S:...` | parses `B:`/`M:`/`S:` entries into `WorkerConfig` |
| `STORAGE_QUERY_REQUEST:` | `STORAGE_QUERY_REQUEST:minecraft:iron_ingot:64:...` | answers `STORAGE_QUERY_RESPONSE:` from index totals |
| `WITHDRAW_REQUEST:` | `WITHDRAW_REQUEST:minecraft:iron_ingot:64:ores` | picks chest from StorageIndex; sends `withdraw <cat> <x> <y> <z> <item> <take>`; `RESCAN_REQUESTED:<cat>` if stale |
| `WITHDRAW_RESULT:` | `WITHDRAW_RESULT:minecraft:iron_ingot:100:64:-20:32` | logs result |

## Outbound raw lines
```java
messageQueue.add("swarm:sync-settings");
messageQueue.add(String.format("swarm:%s deposit set %s %d %d %d", wname, entry.getKey(), pos[0], pos[1], pos[2]));
messageQueue.add(String.format("swarm:%s base set %d %d %d", wname, base[0], base[1], base[2]));
String msg = String.format("swarm:%s waypoint set %s %s %s %s %s %s %s %s", wname, name, cat, owx, owY, owz, nx, ny, nz);
String cmd = String.format("withdraw %s %d %d %d %s %d", category, r.x, r.y, r.z, itemId, take);   // bare
out.writeUTF("RESCAN_REQUESTED:" + category);
String.format("STORAGE_QUERY_RESPONSE:%s:%d...", ...)
```
`getIp(String)` maps `127.0.0.1` → `localhost` for display; `getConnection()` is `addr:port`.

## Line 47 (compile blocker)
```java
host.getWebServer().logInfo("dY"- " + (workerName == null ? getConnection() : workerName) + " connected");
```
A `-` between two string literals; Java has no string subtraction. Looks like a mangled edit of `"dY" + "- "`.
Not build-verified (Gradle deliberately not run), but this cannot compile as written.

## Other smells
- 48 × `catch (Exception ignored)`, including the entire status/task parse path — bad frames vanish silently.
- Timeout 2000 ms doubles as liveness detection, but a slow worker mid-write can be dropped.
- Debug `System.out.println` blocks in WITHDRAW (`=== WITHDRAW DEBUG ===`, available totals, `[WITHDRAW SEND]`).
- `parseBaritoneSettings`/`parseModuleSettings` are **no-ops** (settings push removed) — `SETTINGS:` only updates counters.
- No version/handshake token, no auth: first UTF frame is trusted as the worker's identity.
