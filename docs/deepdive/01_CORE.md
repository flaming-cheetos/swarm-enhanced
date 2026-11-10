# 01 — Core Module & Settings

## EnhancedSwarmAddon (101 lines)
- `MeteorAddon` subclass; `CATEGORY = new Category("Enhanced Swarm")`; `LOG`.
- `onInitialize()` registers 44 modules + 2 command instances (`/eswarm`, alias `/swarm`) + HUD presets.
- `onRegisterCategories()` → `Modules.registerCategory(CATEGORY)`.
- `getPackage()` → `com.rdh.swarm`; `getRepo()` → `rdh/enhanced-swarm`.
- Dead watermark fields `_lvc`, `_ax`, `_rv` and dead locals `_g0/_g1/_g2` (never used).

## EnhancedSwarm (819 lines) — every Setting
All in `sgGeneral` (`settings.getDefaultGroup()`); there is no second group.

| name | type | default | notes |
|---|---|---|---|
| `deposit-x` | Int | `Integer.MIN_VALUE` | persisted chest X (host UI) |
| `deposit-y` | Int | `Integer.MIN_VALUE` | persisted chest Y |
| `deposit-z` | Int | `Integer.MIN_VALUE` | persisted chest Z |
| `base-x` | Int | `Integer.MIN_VALUE` | persisted base anchor X |
| `base-y` | Int | `Integer.MIN_VALUE` | persisted base anchor Y |
| `base-z` | Int | `Integer.MIN_VALUE` | persisted base anchor Z |
| `auto-deposit` | Bool | `false` | "Workers auto-deposit when inventory is nearly full" |
| `auto-deposit-threshold` | Int | `2` (0–10) | empty slots left that trigger deposit |
| `mode` | Enum `Mode` | `Host` | `Host` / `Worker` |
| `ip` | String | `"localhost"` | visible only in Worker mode (private setting) |
| `port` | Int | **6969** (1–65535, no slider) | used for both Host bind and Worker connect |
| `auto-reconnect` | Bool | `true` | visible only in Worker mode |
| `pvp-enabled` | Bool | `false` | `visible(() -> false)` — hidden persisted state |
No web-port setting: **8888 is hardcoded** in `EnhancedSwarmHost` ctor.

## Lifecycle
- `EnhancedSwarm()` → `super(CATEGORY, "enhanced-swarm", "...individual targeting, two-way comms, auto-reconnect")`.
- `onActivate()` (694): Host → `new EnhancedSwarmHost(serverPort.get())`, then async 2 s later `pushDepositConfigAll()` and
  (if auto-deposit) `sendAll("swarm:all deposit auto on")` + threshold. Worker → `new EnhancedSwarmWorker(ip, port, autoReconnect)`.
- `onDeactivate()` (715): `host.disconnect(); host = null;` and `worker.shutdown(); worker = null;` (permanent worker kill).
- `close()` (727): GUI Start/Stop only — disconnects host, **never touches the worker** (comment says worker manages itself).
- `onGameLeft` (737): host disconnects on leaving a server; worker intentionally does nothing (TCP survives MC disconnect).
- `onGameJoin` (749): recreates host only if `mode == Host && host == null`.
- `onTick(TickEvent.Post)` (768): `if (isWorker()) worker.tick();` — the only tick driver.
- `getInfoString()` → `Host (n)` / `Worker` / mode name.
- `getWidget()` builds: Start/Stop, target dropdown ("All" + worker names), Sync From Worker, Follow Me, Stop Task,
  Come Here, Deposit Keep, Deposit All, Deposit Chests section (per-server list with X buttons, crosshair set, manual
  x/y/z, auto-deposit checkbox + threshold, Push Config, Dep. Ores/Shulkers/Building/Valuables), raw command box,
  PVP Mode toggle (7 modules), Task Manager (add item/qty, quick buttons "Stack Iron" 64 / "32 Diamonds",
  nickname setter, task list, dispatch next).
- PVP toggle sends `exec .toggle <mod> on|off` for crystal-aura, kill-aura, auto-totem, surround, auto-trap, auto-web, auto-exp.
- `addNumberRow` / `addDoubleRow` are stubs: label "`<name>: (removed)`" (Baritone setting UI was ripped out).

## Core data classes
- **BotStatus (52)** — wire status: `workerName, state, taskDescription, collected, total, eta, health, food, x, y, z, tier`
  (public fields). `serialize()` → `BOT_STATUS:name:state:task:collected:total:eta:health:food:x:y:z:tier` with `:` sanitized in
  text fields; `getProgressString()` → `collected/total`. `deserialize` accepts >11 parts, tier = parts[11].
- **WorkerStatus (58)** — `name,x,y,z,health,food,currentTask,lastUpdate`; `serialize()` →
  `STATUS:%s:%f:%f:%f:%f:%d:%s` (Locale.US); `deserialize` requires 8 parts.
- **WorkerConfig (51)** — per-worker persisted module/setting state (`setModuleState`, etc.), held in host map.
- **BuildInfo (40)** — static watermark only; `z()` assembles `"flamingcheet0s/rdh forever"` from byte/char arrays. Never used
  for anything but log noise; `_hb/_he` variants live in Host/Worker/DepositConfig/WaypointManager/WebServer.
- **SwarmHudPresets (25)** — `init()` registers HUD presets; called inside `try { } catch (Throwable ignored) {}`.
- **SwarmWaypoint (87)** — name, category, overworld + nether coords, `setCoords(x,y,z,isNether)`, `toString()`, Gson-friendly.
- **StorageIndex (203)** — see 09_ECONOMY.
