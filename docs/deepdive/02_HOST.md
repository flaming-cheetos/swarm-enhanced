# 02 — EnhancedSwarmHost (378 lines)

`public class EnhancedSwarmHost extends Thread`. Owns TCP server, all worker state, task queue, deposit config,
storage index, waypoint manager, and the web server.

## Fields (lines 21–45)
`ServerSocket socket`, `CopyOnWriteArrayList<EnhancedSwarmConnection> connections`,
`ConcurrentHashMap<String,WorkerConfig> workerConfigs`, `ConcurrentHashMap<String,BotStatus> botStatuses`,
`TaskQueue taskQueue`, `DepositConfig depositConfig`, `StorageIndex storageIndex`, `WaypointManager waypointManager`,
`SwarmWebServer webServer`, `volatile boolean running`, optional `depositChestX/Y/Z`, `baseAnchorX/Y/Z`,
watermark `_hb`/`_he`.

## Constructor (47–75)
```java
public EnhancedSwarmHost(int port) {
    try { socket = new ServerSocket(port); }            // binds 0.0.0.0, default backlog, NO auth, NO SO_REUSEADDR
    catch (IOException e) { socket = null; ChatUtils.errorPrefix("Swarm", "Couldn't start a server on port %s.", port); }
    if (socket != null) {
        setDaemon(true); start();
        depositConfig.load();
        // loads persisted base anchor for current server (depositConfig.getBase(server))
        webServer = new SwarmWebServer(8888); webServer.start();   // inside try { } catch (Throwable ignored) {}
    }
}
```
Port-in-use ⇒ `socket == null` and the object is inert, but the web panel still starts (and silently dies if 8888 is taken).

## Accept loop (132–150)
`run()` loops `Socket connection = socket.accept();` → `new EnhancedSwarmConnection(this, connection)` (starts its own thread),
catches `IOException` and breaks. No rate limit, no IP allowlist, no handshake timeout at accept level.

## Sending (152–173) — **no prefix added**
```java
public void sendAll(String cmd) { ... for (c : connections) c.messageQueue.add(cmd); }
public void sendTo(String workerName, String cmd) { ... if (c.workerName.equalsIgnoreCase(workerName)) c.messageQueue.add(cmd);
                                                    if (!found) ChatUtils.warningPrefix("Swarm", "Worker not found: %s", workerName); }
```
Callers must include `swarm:` themselves; two paths forget (see 03/13).

## Task dispatch — two payload formats
```java
// dispatchNextTask (336-361)
String payload = String.format("task assign %s|%s|%s|%d|%s",
    next.getId().toString(), next.getType().name(), next.getItemName(), next.getTargetQuantity(), next.getAssignedBot() == null ? "" : next.getAssignedBot());
sendTo(botName, payload);                       // bare, no "swarm:"

// assignTask (363-372)
String msg = String.format("swarm:%s task assign %s|%s|%s|%d|%s", workerName, task.getId().toString(), task.getType().name(), itemName, task.getTargetQuantity(), task.getAssignedBot() == null ? "" : task.getAssignedBot());
sendTo(workerName, msg);
```
`dispatchNextTask` refuses if `taskQueue.hasActiveTask(bot)`, otherwise takes `getNextPendingForBot`, rewrites
`any/all/empty` assignee to the caller, sets `ACTIVE`, sends, `taskQueue.save()` — all inside `catch (Exception ignored) {}`.

## Public API
```
EnhancedSwarmHost(int)
setDepositChest(String category,int,int,int) · setDepositChest(int,int,int)
setBaseAnchor(int,int,int) · clearBaseAnchor() · hasBaseAnchor() · getBaseAnchorString()
hasDepositChest() · getDepositChestString()
run() · sendAll(String) · sendTo(String,String)
getOrCreateConfig(String) · getWorkerConnection(String) · getAllConfigs() · listWorkers() · cleanDead()
disconnect() · getWebServer() · getWaypointManager() · getConnectionCount() · getWorkerNames()
getDepositConfig() · getStorageIndex() · getCurrentServer()
pushDepositConfig(String) · pushDepositConfigAll()
getBotStatus(String) · getTaskQueue() · dispatchNextTask(String) · assignTask(String,BotTask)
putBotStatus(BotStatus)                      // package-private, called from connections
```
`getCurrentServer()` normalizes `mc.getCurrentServerEntry().address` (lowercase/trim); singleplayer maps to
`singleplayer_<level>`.

## Wiring
`pushDepositConfig(worker)` sends `swarm:<w> deposit set <cat> <x> <y> <z>` per chest + `swarm:<w> base set <x> <y> <z>`.
`disconnect()` closes socket, interrupts, clears running, nulls webServer. `cleanDead()` prunes closed connections.

## UX strings (ChatUtils)
- `"Listening for incoming connections on port %s."` / `"Server closed on port %s."`
- `"Couldn't start a server on port %s."` / `"Error making a connection to worker."`
- `"Workers connected: %s"` / `"Worker not found: %s"` (warning)
- `"Linked %s chest at %d,%d,%d (server: %s)"` / `"Linked base anchor at %d,%d,%d (server: %s)"`
- `"Loaded base anchor for %s: %d %d %d"` / `"%s @ %.2f,%.2f,%.2f HP: %.1f Food: %d Task: %s"` / `"%s - No status yet"`

## Smells
- 13 × `catch (Exception ignored)`; ctor swallows web failure with `catch (Throwable ignored)`.
- `sendAll`/`sendTo` run through `MeteorExecutor`; ordering within a worker is still FIFO via its messageQueue.
- `dispatchNextTask` swallows all exceptions → silent "nothing happened" when a task fails to serialize.
- Prints `System.out` debug in connection/worker paths, not just ChatUtils.
