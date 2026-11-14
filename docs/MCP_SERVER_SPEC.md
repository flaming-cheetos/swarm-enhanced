# Enhanced Swarm MCP Server — Spec 0.1.0

Status: **DRAFT — awaiting approval.** Date: 2026-11-13.
Owner: rdh. Implementer: assistant.
Transport decision: **Streamable HTTP on loopback** (rdh, 2026-11-13).

This is the contract for the MCP server and for the mod-side debug endpoints it
needs. It is written before any code, per the project rule that docs are truth
and a design gets approved before it gets built.

---

## 1. Why this exists

The 2026-11-13 iron session produced one hard number: **~70% of the session was
waiting, not building.**

| Cost | Measured |
|---|---|
| 6 live runs (withdraw → smelt → deposit) | ~54 min wall clock |
| 3 client restarts to load a new jar | ~15 min |
| Chest scans and "did it land" polling | ~10 min |
| Actual code thinking and writing | ~25 min |

Two causes, both fixable without touching gameplay logic:

1. **The world is slow.** 78 ingots is 6.5 minutes of real furnace time. On a
   local test server `/tick rate 200` makes it ~40 seconds.
2. **Every observation needs a Minecraft client.** The planner's decisions can
   only be checked by running the game, so a guard like "short delivery → top
   up" costs a 9-minute run to test, or stays unproven. It stayed unproven.

The MCP server exists so an AI client (Freebuff) can see the world as data,
drive the swarm directly, compress time, inject faults, and get a pass/fail
verdict instead of a chat log to read.

**It is a flight recorder and a set of controls, not a gameplay bot.**

---

## 2. What it is

A separate Node/TypeScript process, **outside the shipped jar**, that:

- speaks MCP over Streamable HTTP to the AI client;
- talks to the mod's existing panel HTTP API (`:8888`) as its control channel;
- talks to a new set of `/api/debug/*` routes that must be added to
  `SwarmWebServer`;
- launches, stops and restarts the two Prism instances when asked;
- runs `gradlew build` / deploys the jar into both instances when asked;
- writes a JSONL event log the assertion engine reads.

```
┌──────────────┐   MCP (Streamable HTTP)   ┌────────────────┐
│ AI client    │ ────────────────────────► │ swarm-mcp      │
│ (Freebuff)   │ ◄──────────────────────── │ 127.0.0.1:8790 │
└──────────────┘                           └───────┬────────┘
                                                   │ HTTP + token
                             ┌─────────────────────┴──────────┐
                             │ mod panel  :8888               │
                             │  /api/*        (exists)        │
                             │  /api/debug/*  (this spec)     │
                             └───────┬────────────────────────┘
                                     │ mod socket
                             ┌───────┴────────┐   ┌──────────────┐
                             │ host (chris)   │   │ worker(jamal)│
                             └────────────────┘   └──────────────┘
```

The server never talks to Minecraft directly. Everything goes through the mod,
so a green result proves the **mod** did it, not the harness.

---

## 3. Transport

Per the MCP specification (Streamable HTTP, introduced 2025-03-26, stateless
revision 2026-07-28):

- **Single endpoint:** `POST http://127.0.0.1:8790/mcp`.
- **JSON-RPC 2.0**, UTF-8. One request per HTTP POST.
- Methods in 0.1.0: `initialize`, `ping`, `tools/list`, `tools/call`.
  (`resources/*`, `prompts/*`, `sampling/*` are out of scope — tools only.)
- Responses are a single JSON object. An SSE stream scoped to the request is
  optional and is **not** used in 0.1.0 (all tools are request/response).
- No sessions. `Mcp-Session-Id` is not issued. Server state lives in the mod,
  not in the transport, so reconnects and client restarts are free.
- **Legacy fallback:** also host `GET /sse` + `POST /messages` (HTTP+SSE, the
  deprecated transport) so an older client still connects. Same tool surface.
- `GET /mcp` returns 405 with `Allow: POST` (not the old SSE stream — the
  deprecated path is `/sse`, kept separate so the probe rules work).

Version negotiation: accept `2025-03-26`, `2025-06-18`, `2025-11-25`,
`2026-07-28` in `initialize` and echo the client's version when supported,
otherwise answer with our highest supported version.

---

## 4. Security

The panel's red lines apply unchanged, and this endpoint is strictly weaker
than the panel is not allowed to be:

- **Bind 127.0.0.1 only.** Refuse to start on `0.0.0.0`; no flag to override.
- **Token on every request.** `Authorization: Bearer <token>`. Token is the
  panel token, read from the mod's log (`Web panel: http://127.0.0.1:8888/?token=…`)
  or from `SWARM_MCP_TOKEN`. Rotating it in one place rotates both.
- **Origin allowlist.** Requests with an `Origin` header not in
  {`http://127.0.0.1:8790`, `null`, absent} are rejected 403. This is the
  DNS-rebinding defence the spec requires.
- **No CORS wildcard.** No `Access-Control-Allow-Origin: *` anywhere.
- **No tunnel, ever.** No ngrok/cloudflared integration, no remote bind.
- **Coordinates stay local.** Tool results may include coordinates (the panel
  already shows them); nothing is sent anywhere but the connected client.
- **Restart scope is the Minecraft server, nothing else** (rdh, 2026-11-13):
  tooling may call `stop.sh` / `start.sh` in `~/minecraft-test` and nothing else on
  the Pi. No reboot, no `systemctl`, no touching `~/homelab`, no other service, no
  package installs, no sudo. The SSH profile is used for reading config and for the
  world tar only. Any command outside `~/minecraft-test` is a bug in the server, not
  a thing to work around.
- **No player-slot or server.properties changes without explicit approval**
  (`max-players=4` stays as it is; host + 2 bots fits).
- **Destructive tools are opt-in** behind `SWARM_MCP_ALLOW_DESTRUCTIVE=1`:
  `world_restore`, `world_snapshot_overwrite`, `client_stop`, `fault_inject`,
  `task_clear_all`. Without the flag they answer
  `isError: true, code: "destructive_disabled"`.
- **Every call is logged** to `tools/mcp/session-<date>.jsonl` (tool, args
  redacted of tokens, result code, duration) so a new chat can inherit the
  trail.

---

## 5. Tool catalog (v0.1 target: 42 tools)

`ex` = backed by an existing panel route. `new` = needs a `/api/debug/*` route
defined in §6.

### 5.1 Observe

| Tool | Args | Returns | Backing |
|---|---|---|---|
| `swarm_state` | — | panel state JSON: version, commit, dimension, connected, bots[], tasks[] | ex `/ws` state |
| `bot_state` | `bot` | full brain: `state`, `halted`, `brainActive`, `plan{step,size,steps[]}`, `task{id,type,item,qty,have,deposited}`, `withdraw{taskId,item,delivered,attempts,inFlight}`, `smelt{baseline,load,oreHeld}`, `pos`, `tick` | new |
| `bot_inventory` | `bot` | 41 slots: `{slot,item,count,durability,maxDurability,reserved}` | new |
| `bot_decision_trace` | `bot`, `n=20` | ring buffer: `{tick,ms,rule,inputs{},decision}` | new |
| `storage_index` | — | `{chest:{x,y,z},counts{},slots,used,indexedAtMs}` | ex StorageIndex |
| `task_list` | — | rows: `{id,type,item,qty,collected,status,owner,leaseExpiresMs,attempt,createdAtMs}` | ex state |
| `swarm_preflight` | `bot?` | ordered PASS/FAIL checks, see §6.4 | new |
| `swarm_health` | — | `{modVersion,buildCommit,uptimeMs,tick,dimension,weather,players[],hostTokenValid}` | new |
| `events_tail` | `n=100`, `filter?` | last n JSONL events, parsed | new |

### 5.2 World (readback — the eyes)

| Tool | Args | Returns | Backing |
|---|---|---|---|
| `world_blocks` | `x,y,z,radius=6` | `[{x,y,z,id}]` (non-air) | new |
| `world_container` | `x,y,z` | `{type,slots:[{slot,item,count,durability}],used,total}` | new (reuses handleScan) |
| `world_containers` | `radius=24` | every container near base, same shape | new |
| `world_entities` | `radius=16` | `[{type,distance,health?,itemCount?}]` | new |
| `world_furnace` | `x,y,z` | `{input,fuel,output,burnTicksRemaining,cookProgress,cookTotal,lit}` | new |
| `world_block_at` | `x,y,z` | single block id + state | new |
| `screenshot` | `instance=bot` | triggers the client's F2 grab, returns `{path,bytes}` — a PNG on disk the AI can open | new |

`screenshot` is the answer to "you can't see the game": the client writes
`screenshots/*.png` and the AI reads the file. Pixels are a last resort; the
probe above is the primary channel.

### 5.3 Control

| Tool | Args | Backing |
|---|---|---|
| `command` | `bot, chat` (raw command passthrough) | ex `/api/command` |
| `task_add` | `item, qty, mode?, scope?` (no bot: the lease assigns) | ex `/api/task/add` |
| `task_cancel` / `task_remove` / `task_pause` / `task_resume` / `task_clear_done` | `id?` | ex |
| `task_clear_all` | — | ex + destructive |
| `bot_stop` | `bot` | ex `/api/action/stop` |
| `bot_follow` | `bot, player` | ex |
| `bot_goto` | `bot, x,y,z` | ex `/api/command` (`#goto`) |
| `bot_tp` | `bot, x,y,z` | new (`/tp` via worker) |
| `deposit` | `bot` | ex |
| `deposit_self` | — | ex |
| `withdraw` | `bot, item, count` | ex `/api/command` |
| `equip` | `bot, item` | new |
| `toggle_module` | `bot, module` (e.g. `bot-brain`) | ex `/api/command` (`.toggle`) |

### 5.4 Fixtures and time

| Tool | Args | Notes |
|---|---|---|
| `scenario_build` | `spec` (§8.2) | one call: clear area, place chests/furnace, stock ore+fuel, set base |
| `give` | `player, item, n` | ex `/api/action/test_give` — labelled test fixture |
| `tick_rate` | `rate` (20–200) | server console via **RCON** (§5.4.1) — the biggest single lever |
| `tick_query` | — | `{tps, mspt, tickCount}` — measure before trusting a rate |
| `tick_freeze` / `tick_step` | `on`, `n` | `/tick freeze`, `/tick step n` via RCON |
| `time_set` / `weather_set` | `value` | determinism |
| `mc_command` | `cmd` | raw server console command via RCON (`/fill`, `/tp`, `/summon`, `/gamerule`) |
| `world_snapshot` | `name` | tar the server world dir on the Pi (destructive) |
| `world_restore` | `name` | restore the tar + restart the server (destructive) |

### 5.4.1 Test server control — the Pi 5, via RCON

The local test world is **not** on the Windows machine. It is a vanilla server on a
Raspberry Pi 5 (`192.168.4.57`, also `100.69.64.16` over tailscale), managed by
`~/minecraft-test/start.sh` (PID file, `nice -n 10`, `-Xmx1280M`, G1GC).

Measured facts from `~/minecraft-test/` (2026-11-13):

| Setting | Value | Why it matters |
|---|---|---|
| `enable-rcon=true`, `rcon.port=25575` | on | the console path; no tmux/screen — `start.sh` execs java into `server.out` |
| `max-players=4` | small | 2 clients + headroom |
| `simulation-distance=3`, `view-distance=5` | tiny | cheap ticks, good for a high rate |
| `level-seed=swarm-test-26.3` | fixed | world is reproducible |
| `pause-when-empty-seconds=0` | off | no accidental freeze |
| server jar | modern vanilla | `/tick rate` exists (1.20.4+) |

**The password never goes in a doc or a config file in the repo.** Read it from
`~/minecraft-test/server.properties` over SSH at start, or from
`SWARM_MC_RCON_PASSWORD`.

**Tick rate is a measured ladder, not a magic 200.** A Pi 5 at 10× ticks is a real
CPU load. The server proxies the console; the harness must:

1. `tick_query` at 20 → record baseline `mspt`.
2. Step `40 → 60 → 100 → 140` and hold each ~15 s.
3. Stop climbing when `mspt > 45` or TPS sags below the requested rate; use the last
   rate that held, and record it in the run's verdict.
4. Always drop back to 20 in teardown (`tick_rate 20`), even on failure.

**Free 2× before touching tick rate:** the base has three blast furnaces (measured
2026-11-13; the table below was computed for two, so treat it as an upper bound), and a blast
furnace smelts at 100 ticks/item instead of 200. Splitting 78 ore across both is
39 each — 3.25 min at 20 TPS, no settings changed.

Honest time math for 78 ingots (two furnaces, real numbers):

| Setup | Wall clock |
|---|---|
| today: 2 regular furnaces, 20 TPS | ~6.5 min |
| 2 blast furnaces, 20 TPS | ~3.25 min |
| 2 blast furnaces, 60 TPS | ~65 s |
| 2 blast furnaces, 120 TPS | ~33 s |

So the earlier "~40 s" claim holds only at high tick rate **and** blast furnaces;
at 60 TPS with regular furnaces it is ~2.2 min. State the measured rate in every
verdict rather than assuming one.

### 5.5 Faults (destructive)

`fault_inject(kind, params)` with kinds:

| Kind | Params | Proves |
|---|---|---|
| `withdraw_short` | `deliver` | top-up path after a short chest delivery |
| `deposit_refuse` | `chest` | spill to the next chest |
| `deposit_no_progress` | `chest` | full-chest refusal handling |
| `furnace_unreachable` | `pos` | SMELT_OPEN re-path |
| `chest_no_target` | `chest` | no-target refusal |
| `lease_kill` | `taskId` | requeue and re-claim |
| `disconnect_bot` | `bot`, `seconds` | reconnect + task resumption |
| `fill_inventory` | `bot`, `item` | no-room deposits |
| `smelt_baseline_reset` | — | completion gate honesty |

Each fault is active until `fault_clear(kind)` or process restart, and every
scenario that uses one is labelled `FAULT` in its verdict.

### 5.6 Verify

| Tool | Args | Returns |
|---|---|---|
| `assert_events` | `spec` (§8.1) | `{pass, failures:[{rule,expected,actual,atMs}]}` |
| `chest_delta` | `before{}, after{}` | `{item:delta}` with arithmetic shown |
| `chest_scan_now` | — | forces a live scan and returns parsed contents |
| `timing_report` | `runId?` | per-phase seconds: connect, withdraw, smelt, deposit, complete |
| `watchdog_status` | — | stuck tasks + why |

### 5.7 Clients

| Tool | Args | Notes |
|---|---|---|
| `client_status` | — | `[{instance,pid,listening,joined,panelToken,logPath}]` |
| `client_launch` | `instance` (`main`/`bot`/`both`) | waits for "joined the game", returns elapsed |
| `client_stop` | `instance` | by PID only, never by name pattern |
| `client_restart` | `instance` | stop → launch → wait → (bot) toggle brain ON |
| `client_log` | `instance`, `n`, `filter` | tail with ANSI stripped |

### 5.8 Dev loop

| Tool | Args | Notes |
|---|---|---|
| `build` | — | gradle build, returns `{exitCode, jar, durationMs, errors[]}` |
| `deploy` | `targets` | copy jar into instances |
| `run_scenario` | `name` or `path` | setup → action → assert → collect verdict → optional restore |
| `run_all` | `dir` | every scenario, one summary table |
| `mcp_self_log` | `n` | this session's own calls |

---

## 6. Mod-side endpoints this needs

All under `/api/debug/`, same token + Origin rules as the panel. All return
`{"ok":true,...}` on success and `{"ok":false,"error":"<code>","detail":"…"}`
on failure.

### 6.1 `GET /api/debug/bot?bot=jamal`

```json
{"ok":true,"bot":"jamal","brainActive":true,"state":"SMELTING_WAIT","halted":false,
 "tick":128442,"pos":[123,64,-88],
 "plan":{"step":1,"size":2,"steps":["GATHER:raw_iron:78","SMELT:iron_ingot:78"]},
 "task":{"id":"859525a4-…","type":"SMELT","item":"minecraft:iron_ingot","qty":78,
         "have":78,"deposited":0,"baseline":0},
 "withdraw":{"taskId":"859525a4-…","item":"minecraft:raw_iron","delivered":70,
             "attempts":1,"inFlight":null,"inFlightAgeMs":0},
 "smelt":{"baseline":0,"load":2,"oreHeld":64,"loads":["14","64"]}}
```

### 6.2 `GET /api/debug/trace?bot=jamal&n=20`

Ring buffer, newest last. The single most useful tool for a state machine:

```json
[{"ms":1736…,"rule":"withdraw.short-delivery-topup",
  "inputs":{"need":78,"have":70,"deficit":8,"storage":81,"delivered":70,"attempts":1},
  "decision":"WITHDRAW 8"}]
```

### 6.3 `GET /api/debug/world/container?x=&y=&z=`

Reuses the worker's existing container read (the same path `handleScan` uses).

### 6.4 `/api/debug/bot` and preflight (as built, 2026-11-13)

Built shape: `POST /api/debug/bot {bot, refresh}`. On `refresh` the host sends
`DEBUG_STATE_REQUEST:<nonce>` to that worker, the worker answers
`DEBUG_STATE:<nonce>|<json>` from `BotBrain.debugStateJson()`, and the host caches it.
The route returns `{ok, bot, ageMs, data:{...}}`, or `{ok:false,error:"no_state_yet"}`
before the first reply. The host never fabricates brain data.

`swarm_preflight` is **composed in the MCP** from `bot_state` + `swarm_state`
(host reachable, worker connected, bot online, brain active, not halted, inventory
clean of junk, index fresh, fuel known, food, health). "Junk" is whatever the mod's
own `shouldKeepStatic` predicate did not classify as keep (`keep` / `deposit` maps in the
snapshot) — the MCP never re-decides that.. It is deliberately a composition, not
a mod endpoint, so check thresholds can change without a rebuild.

Original design sketch (kept for reference):

```json
{"ok":true,"pass":false,"checks":[
 {"name":"host_connected","pass":true},
 {"name":"worker_joined","pass":true},
 {"name":"brain_active","pass":false,"detail":"BotBrain module is not active"},
 {"name":"index_fresh","pass":true,"ageMs":8200},
 {"name":"inventory_empty","pass":false,"detail":{"minecraft:raw_iron":75}},
 {"name":"near_base","pass":true,"distance":6},
 {"name":"furnace_known","pass":true},
 {"name":"fuel_known","pass":false,"detail":"check_fuel unimplemented"},
 {"name":"food_ok","pass":true,"food":18}]}
```

`preflight` is the tool that would have saved three wasted runs tonight.

### 6.5 Event log

The mod writes `logs/swarm-events.jsonl`, one object per line, alongside the
existing chat lines (chat stays for humans, JSONL is for assertions):

```json
{"t":1736…,"tick":128442,"bot":"jamal","ev":"withdraw","item":"minecraft:raw_iron",
 "qty":64,"have":70,"need":78,"src":{"x":-550,"y":91,"z":601}}
{"t":…,"ev":"smelt","phase":"load_done","after":14,"before":0,"delta":14,"load":1,"loads":2}
{"t":…,"ev":"task","phase":"holding_complete","id":"…","have":78,"need":78}
{"t":…,"ev":"deposit","phase":"complete","moved":92,"delta":{"minecraft:iron_ingot":78}}
{"t":…,"ev":"task","phase":"complete","id":"…","evidence":"verified-empty"}
```

Required event names: `task`, `withdraw`, `smelt`, `deposit`, `gate`, `lease`,
`fault`, `watchdog`, `preflight`. Fields are additive-only within a major
version.

---

## 7. Watchdog

The roadmap's own pass condition — "no task `RUNNING` for 10 minutes without
progress" — becomes enforced:

- If a task's `have + deposited` has not changed for `stuckAfterMs` (default
  600 000, or 60 000 in fast mode), the mod emits `ev:"watchdog"` with the full
  `bot_state` + `decision_trace` snapshot.
- `run_scenario` treats a watchdog event as a failure with the snapshot attached.
- No automatic cancel — the point is diagnosis, not hiding the hang.

---

## 8. Scenario and assertion formats

### 8.1 Assertions (`assert_events`)

```json
{"run":"iron78","expect":[
  {"ev":"task","phase":"plan","item":"minecraft:iron_ingot","qty":78,"order":1},
  {"ev":"withdraw","have":78,"order":2},
  {"ev":"smelt","phase":"load_done","delta":78,"order":3},
  {"ev":"gate","phase":"holding_complete","order":4},
  {"ev":"deposit","phase":"complete","delta":{"minecraft:iron_ingot":78},"order":5},
  {"ev":"task","phase":"complete","order":6},
  {"chest_delta":{"minecraft:iron_ingot":78}}
]}
```

Rules: match on any subset of fields; `order` is monotonic (a lower order may
not appear after a higher one); `chest_delta` compares a scan taken at scenario
start with one taken at the end. Verdict:

```json
{"pass":false,"failures":[{"rule":4,"expected":{"phase":"holding_complete",
  "before_order":3},"actual":"absent","atMs":1736…}]}
```

### 8.2 Scenario file (`tools/mcp/scenarios/iron78.json`)

```json
{"name":"iron78","label":"not-fault",
 "setup":[
   {"op":"tick_rate","rate":200},
   {"op":"client_launch","instance":"both"},
   {"op":"toggle_module","bot":"jamal","module":"bot-brain"},
   {"op":"scenario_build","furnace":{"x":-549,"y":91,"z":606},
    "chests":[{"x":-550,"y":91,"z":601,"fill":{"minecraft:raw_iron":78}}],
    "ore":78,"fuel":{"minecraft:coal":64}},
   {"op":"preflight","expect":"pass"}],
 "act":[{"op":"task_add","item":"minecraft:iron_ingot","qty":78,"mode":"gather"}],
 "wait":{"until":"task.complete","timeoutMs":240000},
 "assert":{"ref":"tools/mcp/asserts/iron78.json"},
 "teardown":[{"op":"tick_rate","rate":20}]}
```

At `/tick rate 200` this scenario should finish in **under 3 minutes**. Today
the same run takes ~9.

---

## 9. Config

`tools/mcp/config.json` (untracked if it holds a token):

```json
{"port":8790,"bind":"127.0.0.1","panel":"http://127.0.0.1:8888",
 "token":"<panel token>","instances":{"main":"…\\instances\\main","bot":"…\\instances\\bot"},
 "project":"C:\\Projects\\swarm\\swarm-improved",
 "logs":{"bot":"…\\instances\\bot\\.minecraft\\logs\\latest.log",
         "main":"…\\instances\\main\\.minecraft\\logs\\latest.log"},
 "mcServer":{"host":"192.168.4.57","rconPort":25575,"passwordEnv":"SWARM_MC_RCON_PASSWORD",
             "sshProfile":"pi-lan","dir":"/home/rdh/minecraft-test",
             "startScript":"start.sh","stopScript":"stop.sh","worldDir":"world"},
 "fastMode":{"tickRate":60,"measureFirst":true,"maxMspt":45},
 "destructive":false,"stuckAfterMs":600000}
```

Env overrides: `SWARM_MCP_PORT`, `SWARM_MCP_TOKEN`, `SWARM_MCP_PANEL`,
`SWARM_MCP_ALLOW_DESTRUCTIVE`.

Token discovery order: `SWARM_MCP_TOKEN` → `config.token` → scrape the newest
`Web panel: http://127.0.0.1:8888/?token=…` line from `main`'s `latest.log`.

---

## 10. Connecting Freebuff

1. Start the server: `node tools/mcp/server.mjs` (or `npm start` in `tools/mcp`).
2. It prints `swarm-mcp: http://127.0.0.1:8790/mcp  (token: ••••)`.
3. Freebuff → MCP servers → **add custom remote server** → URL
   `http://127.0.0.1:8790/mcp`, header `Authorization: Bearer <token>`.
4. Tools appear as `remote:<id>` and are discoverable with tool search.
5. If a client only speaks the old transport, point it at `/sse` instead.

---

## 11. Build phases

| Phase | Content | Session cost |
|---|---|---|
| **v0.1** | transport + token + 25 tools: observe (`swarm_state`, `bot_state`, `swarm_preflight`, `task_list`, `swarm_health`, `client_status`), control (`task_*`, `command`, `bot_stop`, `deposit*`, `scan_chests`, `give`), server (`tick_query`, `tick_rate`, `mc_command`), clients (`client_launch`, `client_stop`), dev (`build`, `deploy`) | **DONE 2026-11-13** |
| **v0.2** | world probe (5 tools) + `screenshot` + `scenario_build` + `fault_inject` | 1 session |
| **v0.3** | JSONL events in the mod + `events_tail` + `assert_events` + `run_scenario` + 3 scenario files (iron78, cancel, topup-fault) | 1 session |
| **v0.4** | `bot_decision_trace` + watchdog + `timing_report` + `run_all` | 1 session |

Each phase ships with the mod-side endpoints it needs in the same change, so
the tool is never a stub that lies.

---

## 12. Acceptance (how we know it works)

| Check | Pass |
|---|---|
| Transport | `initialize` + `tools/list` + one `tools/call` from Freebuff against `/mcp` |
| Legacy | same three against `/sse` |
| Security | POST without token → 401; foreign `Origin` → 403; bind on `0.0.0.0` refused |
| Restart scope | the only server-side write is `stop.sh`/`start.sh` in `~/minecraft-test`; a grep of the server's own code shows no other Pi path |
| Fast iron | `run_scenario iron78` → PASS in < 3 min using two blast furnaces at the highest tick rate that held `mspt <= 45` (measured, recorded in the verdict) |
| Evidence | its verdict contains the 6 ordered events and `chest_delta = +78` |
| Fault | `run_scenario topup-fault` → PASS, proving the top-up path in < 60 s |
| Watchdog | a deliberately stuck task produces `ev:"watchdog"` within the window |
| Regression | `run_all` green, and a red scenario fails the build step |

---

## 13. Non-goals

- Not a gameplay bot — it drives the mod, it does not path-find.
- Not in the shipped jar. Harness rule: `tools/` is never bundled.
- No remote/internet exposure, ever.
- No gameplay logic in the harness: if a run is green because the harness did
  the work, the run proves nothing.
- Not a replacement for the existing Node harness; it supersedes its control
  layer and keeps its stub scenarios.
