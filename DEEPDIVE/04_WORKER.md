# 04 — EnhancedSwarmWorker (1,414 lines)

`extends Thread`; owns the socket to the host, deposit chest map, and the verb interpreter.
No `@EventHandler` methods — the module ticks it (`EnhancedSwarm.onTick → worker.tick()`).

## Chunk map
- **1–200** — fields (`host,port,name,out,in,currentTask,resumeTaskAfterDeposit,shouldReconnect`), keep-filter sets
  (`KEEP_IDS` food/totems/elytra/shield, `KEEP_PREFIXES` netherite_/diamond_/iron_/stone_/wooden_/golden_,
  `TOOL_SUFFIXES`, `ARMOR_SUFFIXES`), ctor (name = session username), `run()` connect + handshake.
  `run()`: `while (shouldReconnect)` → `socket = new Socket(host, port)`, streams, `out.writeUTF("worker:" + name)`,
  `out.writeUTF("REQUEST_CONFIG:" + name)`, then read loop; `in.available() > 0 → in.readUTF() → handlePayload`.
  `sendStatus()` called periodically from this loop.
- **200–400** — protocol guard: if payload starts `swarm:` strip 6 chars; if residual starts `worker:` ignore;
  `STORAGE_QUERY_RESPONSE:` is intercepted and forwarded to `BotBrain.receiveStorageQueryResponse`;
  verb dispatch switch; `exec` (raw `#` chat via network handler, else `Commands.dispatch` after stripping `.`),
  `stop` (chat `#stop`), `sync-settings`, `say`, `where`, `help`, `deposit`, `waypoint`, `scan`, `withdraw`,
  `base`, `depositall`, `task` (assign/cancel/pause/resume). `task assign` parses
  `<uuid>|<Type>|<item>|<qty>|<bot>`, builds `BotTask`, calls `BotBrain.setOutputStream(out)` + `brain.setTask(task)`,
  replies `TASK_PROGRESS:<name>:<uuid>:0:<qty>:PENDING:`.
- **400–600** — `handleDeposit(args)`: `set <cat> <x> <y> <z>`, `auto on|off`, `threshold N`, `list`, `remove <cat>`;
  `handleDepositAll`, `handleBase` (`set x y z` / `clear` / `show`), waypoint parsing start.
- **600–800** — `handleWaypoint` (overworld/nether variants, remove, list, show), `getOrCreateWaypointManager()`,
  `performCategoryDeposit()` (walk configured chests, match category, deposit), `performDepositToChest(...)`.
- **800–1000** — keep-filter deposit logic (slot loop, `shouldKeep`, `hasAnyDepositableItems`), chest stand-position
  search, `resumeTask()`, `tick()` auto-deposit gate (empty slots <= threshold, 30 s cooldown).
- **1000–1200** — `tick()` body (auto-deposit trigger), `handleSyncSettings` (**no-op since baritone sync removal**),
  `handleDefault` (Baritone verbs pass-through with 3 s cooldown), `handleScan` (find chests in radius, read slots,
  build JSON counts).
- **1200–1414** — `handleWithdraw` (goto chest, open, `InvUtils.move` transfers, `WITHDRAW_RESULT`), `sendScanResult`,
  `sendStatus` (STATUS + BOT_STATUS), `disconnect`/`shutdown`, `getConnection`.

## Verb switch (lines 255–399)
`exec`, `stop`, `sync-settings`, `say`, `where`, `help`, `deposit`, `waypoint`, `scan`, `withdraw`, `base`,
`depositall`, `task` (sub: `assign`, `cancel`, `pause`, `resume`), default → `handleDefault` → Baritone verbs.
`baritoneVerbs` whitelist calls `mc.getNetworkHandler().sendChatMessage(cmd)` behind `canSendBaritone()`
(3,000 ms cooldown, `BARITONE_COMMAND_COOLDOWN`).

## Handler signatures
```
run() · tick()
handlePayload(String) · handleDeposit(String) · handleDepositAll(String) · handleBase(String)
handleWaypoint(String) · handleScan(String) · handleWithdraw(String) · handleSyncSettings()
handleDefault(String,String) · performCategoryDeposit() · performDepositToChest(int,int,int,String,boolean)
resumeTask() · findChestStandPos(BlockPos) · hasAnyDepositableItems() · shouldKeep(ItemStack)
sendScanResult(String,int,int,int,JsonObject) · sendStatus()
triggerDeposit() · getOrCreateWaypointManager() · disconnect() · shutdown() · getConnection()
```

## Task types
The worker never executes `BotTask.Type` itself; it forwards to `BotBrain` (GATHER/CRAFT/SMELT handled; DEPOSIT is a
brain state; EQUIP and SCAN_CHESTS have no brain handler — scans are driven by the `scan` verb instead).

## Reconnect
`while (shouldReconnect)`; `disconnect()` closes the socket, stops PathManagers, interrupts, but leaves
`shouldReconnect = true` → reconnects. `shutdown()` sets it false → permanent stop. On `GameLeft` the worker does
nothing (module comment: TCP is independent of the Minecraft server).

## Status
`sendStatus()` serializes `WorkerStatus` (`STATUS:...`) **and** a `BotStatus` (`BOT_STATUS:...`) with
`tier = DependencyResolver.getCurrentTier().name()`, health/food/coords from the player, task description from
BotBrain (`getState()`, `getTaskDescription()`), collected/total hardcoded 0 (comment: getters may not exist).

## Notes
- 68 × `catch (Exception ignored)` — highest in the repo.
- `handleSyncSettings` is a no-op; module/baritone settings sync was removed but protocol calls remain.
- `performCategoryDeposit` uses hardcoded KEEP sets, so "deposit" is really "deposit everything except gear/food/totems".
- `System.out.println("DEPOSIT DEBUG: ...")` prints slot id/item/playerStart/totalSlots every deposit pass.
