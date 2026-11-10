# Web panel inventory — pre-Web v2

Repo: `C:\Projects\swarm\swarm-improved`
Branch / HEAD: `fix/staging` / `8d1d276`
Date: 2026-11-09
Scope: read-only inventory. No source file was changed (`git diff --stat` is empty; only
untracked `DEEPDIVE/` notes exist).

B1 is landed and was the baseline:

- `5d67aa5` — Require a real token for every web panel request.
- `b64c137` — Render panel data with DOM text and encode logs with Gson.

How this was verified: the two Java text blocks were extracted verbatim and served by a
mock backend that reproduces B1's token → cookie → WS contract. The real HTML/CSS/JS ran in
Chromium at 1280 and 900 wide. Every request-bearing control was exercised once; the three
client-only log controls were not clicked. 13 POSTs were captured with their exact bodies. The nickname re-render was tested over a live 2 s state push. Claims that
come only from reading code are marked `[code]`; claims observed in the browser are marked
`[verified]`.

---

## Assumptions for the fill-in block

The blanks were not filled, so these defaults were used and the work continued:

| Field | Assumption | Why |
|---|---|---|
| I USE THE PANEL FOR | watching tasks, then reading the log | Tasks are the only actionable live data; storage is not exposed. |
| MOST IMPORTANT VIEW | tasks | The task queue is the only control surface with real state. |
| LEAST USED | workers ("registry" does not exist) | There is no registry view; the closest thing is the Workers card. |
| MISSING TODAY | storage view, waypoints, warnings — all three | Confirmed absent from `buildStateJson`. |
| MUST KEEP | live log stream | It is the only place failures surface today. |
| SCREEN | one ~1920 monitor | Design for 1280–1920; 900 is a secondary check, not a target. |

---

## 0. Corrections to the Stage 1 brief

These matter before anyone writes Web v2 from the old notes:

1. **The HTML is not all in `SwarmWebPanel.java`.** `SwarmWebPanel.getHtml()` holds the HTML
   and the inline `<style>`; the JavaScript lives in a second class,
   `SwarmWebPanelScript.getScript()`, served as `/app.js` (`SwarmWebPanel.java:352`). There is
   no `src/main/resources/web/` — confirmed again.
2. **There is no SSE log stream any more.** B1 replaced the transport with a WebSocket at
   `/ws`; logs are serialized with Gson, not hand-escaped (`SwarmWebServer.java:206-216`).
3. **`check_fuel` does not return 200 or log "not implemented".** It is special-cased before
   the POST branch and returns **501 Not Implemented** with
   `{"ok":false,"error":"check_fuel unavailable: container reader not implemented"}`
   (`SwarmWebHandler.java:44-48`). It writes no log line.
4. **The port is not 9999 and not provably 6968.** The code default is **8888**, configurable
   through `web-port` (`EnhancedSwarm.java:134-142`). The repo docs also say 8888. No log in
   this workspace contains `6968` or a Swarm web-panel line; see UNKNOWN.
5. **There are no Overview / Tasks / Log views.** It is one page with cards; the three names
   describe regions, not tabs.
6. **The CORS block is gone.** `SwarmWebHandler.java:37` now calls `isOriginAllowed`, which is
   a request-origin check, not CORS. No `Access-Control-Allow-*` header is emitted.
7. **`SwarmWebPanel` is 357 lines, not 552.** `DEEPDIVE/12_WEB.md` is stale on size, auth,
   `scan all`, and `check_fuel`.
8. **B1 removed every `innerHTML` sink.** No `innerHTML`, `outerHTML`, or `insertAdjacentHTML`
   remains in the panel; rendering uses `createElement` / `textContent` / `createTextNode`.

---

## 1. Extraction

Extracted mechanically, without re-indentation or reformatting. Java text blocks normalize
line endings to `\n`, so the files are LF.

| Output | Source | Java content lines | Bytes | SHA-256 (first 16) |
|---|---|---|---|---|
| `DEEPDIVE/panel/current/index.html` | `SwarmWebPanel.java` | 6–354 | 10,572 | `747a4bfa646d8453` |
| `DEEPDIVE/panel/current/app.js` | `SwarmWebPanelScript.java` | 9–349 | 11,047 | `e12ccc5d9470e7c3` |

- The HTML text block opens with `return """` at `SwarmWebPanel.java:5` and closes at line 355.
- The JS text block opens at `SwarmWebPanelScript.java:8` and closes at line 350.
- CSS was **not** split into `style.css`. It is inline in the source `<style>` block
  (`SwarmWebPanel.java:12-254`) and splitting it would not be mechanically faithful. The HTML
  still points at `/app.js`, exactly as the Java serves it.
- `index.html` + `app.js` are the complete page. There are no images, fonts, or other assets.

A capture harness lives in `DEEPDIVE/panel/current/_capture/`. It is not source and not a
substitute for the Java server; it reproduces the auth contract so the page can be rendered.

---

## 2. Control table

Payloads below are the exact bodies observed in `_capture/requests.jsonl` unless marked
`[code]`. All requests carried the session cookie.

### Always-visible controls

| # | Label as written | Element | Trigger | Request | Exact payload | Server handler | What it really does |
|---|---|---|---|---|---|---|---|
| 1 | `+ Add` | `<button id="addTaskButton">` (`Panel:298`) | click → `addTask()` (`Script:266`) | `POST /api/task/add` | `{"item":"diamond","qty":7,"bot":"Bob","type":"CRAFT"}` `[verified]` | `SwarmWebServer.java:336-400` | Validates the item against the registry, picks `CRAFT` when a recipe exists and `GATHER` otherwise for `AUTO`, **silently deletes all COMPLETE/FAILED/CANCELLED tasks**, adds the new task, resolves a nickname to a username, then dispatches to the named bot or to `getWorkerNames()[0]`. An unknown item queues nothing and logs a warning, but the input is still cleared client-side. |
| 2 | `Clear Done/Failed` | `<button id="clearDoneButton">` (`Panel:301`) | click → `post('/api/task/clear_done')` (`Script:319`) | `POST /api/task/clear_done` | `{}` `[verified]` | `SwarmWebServer.java:402-405` | `TaskQueue.clearCompleted()` removes **COMPLETE, FAILED and CANCELLED** and persists. Label omits CANCELLED. |
| 3 | `👥 Follow Me` | `<button data-action="follow">` (`Panel:309`) | click → `action('follow')` (`Script:292`) | `POST /api/action/follow` | `{}` `[verified]` | `SwarmWebServer.java:434-439` | `sendAll("swarm:all follow player " + hostPlayerName)`. The worker's default branch turns `follow <name>` into chat `#follow player <name>` (`EnhancedSwarmWorker.java:921-947`). It moves only if the client's `#`/Baritone integration is present. |
| 4 | `🛑 Stop All` | `<button data-action="cancel">` (`Panel:310`) | click | `POST /api/action/cancel` | `{}` `[verified]` | `SwarmWebServer.java:440-443` | `sendAll("swarm:all cancel")`; every worker cancels its BotBrain task. No confirmation. |
| 5 | `▶ Resume All` | `<button data-action="resume">` (`Panel:311`) | click | `POST /api/action/resume` | `{}` `[verified]` | `SwarmWebServer.java:444-448` | Clears every `BotStatus.halted` flag locally, then `sendAll("swarm:all resume")`. |
| 6 | `📦 Deposit All` | `<button data-action="deposit">` (`Panel:312`) | click | `POST /api/action/deposit` | `{}` `[verified]` | `SwarmWebServer.java:449-452` | `sendAll("swarm:all depositall")`. No confirmation, no target chest preview. |
| 7 | `⛏ Dep. Ores` | `<button data-action="deposit_ores">` (`Panel:313`) | click | `POST /api/action/deposit_ores` | `{}` `[verified]` | `SwarmWebServer.java:453-456` | `sendAll("swarm:all deposit ores")`. |
| 8 | `📫 Dep. Shulkers` | `<button data-action="deposit_shulkers">` (`Panel:314`) | click | `POST /api/action/deposit_shulkers` | `{}` `[verified]` | `SwarmWebServer.java:457-460` | `sendAll("swarm:all deposit shulkers")`. |
| 9 | `🔍 Scan Chests (unavailable)` | `<button disabled>` (`Panel:315`) | none | none | none | `SwarmWebServer.java:471-473` exists but nothing calls it | Sends no request; the handler only logs `"Scan Chests is not implemented: container reader unavailable; no command sent"`. This is honest now. |
| 10 | `🔥 Check Fuel (unavailable)` | `<button disabled>` (`Panel:316`) | none | none | none | `SwarmWebHandler.java:44-48` | Sends no request. If called directly it returns **501** with an error object and logs nothing. |
| 11 | `⚔ PVP ON` | `<button data-action="pvp_on">` (`Panel:317`) | click | `POST /api/action/pvp_on` | `{}` `[verified]` | `SwarmWebServer.java:461-465` | Seven separate `sendAll("swarm:all exec .toggle <module> on")` calls for `crystal-aura`, `kill-aura`, `auto-totem`, `surround`, `auto-trap`, `auto-web`, `auto-exp`. No check that any module exists. |
| 12 | `🕊 PVP OFF` | `<button data-action="pvp_off">` (`Panel:318`) | click | `POST /api/action/pvp_off` | `{}` `[verified]` | `SwarmWebServer.java:466-470` | The same seven messages with `off`. |
| 13 | `Send` | `<button id="sendButton">` (`Panel:327`) | click or Enter in `cmdInput` → `sendCmd()` (`Script:301`) | `POST /api/command` | `{"target":"all","cmd":"say \"hi\" \\ test"}` `[verified]` | `SwarmWebServer.java:474-487` | `all` is sent per worker as `swarm:<worker> exec <cmd>`; a named target gets one message. A `#`-prefixed command is sent to chat by the worker, not dispatched as a command (`Worker:336-345`). The naive body parser truncates at the first quote — this example reaches the worker as `say \`. |
| 14 | `Clear` (log) | `<button id="clearLogsButton">` (`Panel:338`) | click → `clearLogs()` (`Script:248`) | none | none | client only | Empties the DOM and the client array. Server history is untouched and returns on reconnect. |
| 15 | `Auto-scroll` | `<input type="checkbox" id="autoScroll">` (`Panel:340`) | change | none | none | client only | Scrolls `logEntries` to the bottom after each appended line. |
| 16 | Log filter | `<input id="logFilter">` (`Panel:336`) | input → `filterLogs()` (`Script:244`) | none | none | client only | Re-renders all buffered lines with a case-insensitive substring filter. Filtering does not affect what is stored. |

### Controls rendered per bot

| Label as written | Element | Trigger | Request | Exact payload | Handler | What it really does |
|---|---|---|---|---|---|---|
| `⏹ Stop` / `▶ Resume` | per-card `<button>` (`Script:142-150`) | click | `POST /api/task/cancel` or `/api/task/resume` | `{"bot":"Miner01"}` `[verified]` | `SwarmWebServer.java:421-433` | Cancel sends `swarm:<bot> cancel`. Resume sets `halted=false` locally, sends `swarm:<bot> resume`, then dispatches the next task. Local flag flips before the worker confirms. |
| Nickname field | `<input class="nick-input">` (`Script:110-117`) | `blur` (not Enter) | `POST /api/nickname` | `{"username":"Miner01","nickname":"Renamed"}` `[verified]` | `SwarmWebServer.java:488-494` | Stores the nickname in `TaskQueue`, persists `task-queue.json`, logs `"Nickname set: <user> → <nick>"`. No validation, no length cap, duplicates allowed. |

### Controls rendered per task

| Label as written | Element | Trigger | Request | Exact payload | Handler | What it really does |
|---|---|---|---|---|---|---|
| `✕` | per-row `<button>` (`Script:201-205`) | click | `POST /api/task/remove` | `{"id":"0f7c5ae2-1111-4a11-9a11-111111111111"}` `[verified]` | `SwarmWebServer.java:406-415` | `TaskQueue.removeById(UUID)`, persists. Invalid UUID only logs a warning. |

---

## 3. Inputs (no request until a submit control)

| Control | Line | Default / options | Notes |
|---|---|---|---|
| `item (e.g. cobblestone)` | `Panel:284` | empty | Free text; server resolves `namespace:path` or `minecraft:` prefix. Unknown item logs a warning and returns; the input is cleared anyway. |
| `qty` | `Panel:285` | `64` | `<input type="number">` with **no min/max/step**. `0` and negatives are accepted; negative targets make `getRemaining()` return 0 immediately. |
| Task type | `Panel:286-294` | `Auto`, `Craft`, `Gather`, `Hunt food`, `Grind`, `Smith`, `Build` | Server accepts any `BotTask.Type`; panel omits `SMELT`, `EQUIP`, `DEPOSIT`, `SCAN_CHESTS`, `SHULKER`. `HUNT_FOOD`/`GRIND` deliberately allow an empty item. |
| Task bot | `Panel:295-297` | `Any bot` + nicknames | Option **value is the nickname**, label is the nickname. Server resolves nickname → username by scanning `getAllNicknames()`. Duplicate nicknames resolve to the first match. |
| Command target | `Panel:323-325` | `All` + nicknames | Option **value is the username**, unlike the task bot select. Two value spaces in one card. |
| Command input | `Panel:326` | empty | `title` says `exec <command from the box>`; `#` inputs are actually chat. |

---

## 4. Endpoint table

Auth runs first on every request, including the WS upgrade.

| Method | Path | Auth | Handler | Response | What it does |
|---|---|---|---|---|---|
| any | any | token in `X-Swarm-Token`, `?token=`, or `swarm_token` cookie (`Server:101-110`) | `Handler:33` | `401 text/plain "Unauthorized"` | Missing/invalid token stops here. |
| any | any | after auth | `Handler:37` | `403 "Origin not allowed"` | If `Origin` is present it must equal `http://` + `Host` exactly. No `https` form. |
| GET | `/` | token | `Handler:78-81` | `200 text/html` + `Set-Cookie: swarm_token=<token>; Path=/; HttpOnly; SameSite=Strict` + CSP | Serves the page and seeds the cookie that makes `/app.js` and `/ws` work. |
| GET | `/app.js` | cookie/token | `Handler:83-86` | `200 application/javascript` | Serves the external script. |
| GET | `/ws` | cookie/token on the handshake | `Handler:51-67` → `Server:194-200` | `101 Switching Protocols` | On success sends a state frame immediately, then a `log_history` frame. Then the 2 s scheduler pushes state. |
| POST | **any path** | token + origin | `Handler:70-75` | `200 {"ok":true}` **unconditionally** | Calls `handleApiCommand(uri, body)` and returns `ok:true`. Unknown paths, a null host, and caught exceptions all still return 200 ok. |
| POST | `/api/action/check_fuel` | token + origin | `Handler:44-48` | `501` + `{"ok":false,"error":"check_fuel unavailable: container reader not implemented"}` | Special-cased before the generic POST branch. No log line. |
| GET | anything else | token | `Handler:88-89` | `404 text/plain "Not found"` | Includes `/favicon.ico`. |
| WS | `/ws` text frame | cookie at handshake | `Handler:92-102` | no response | Any text frame is passed as **both** path and body to `handleApiCommand(msg, msg)`. |

### Endpoint paths that exist but have no panel control

| Path | Behaviour |
|---|---|
| `/api/task/pause` (`Server:416-420`) | Sends `swarm:<bot> task pause`. No UI calls it. |
| `/api/action/stop` (`Server:440-443`) | Alias of `/api/action/cancel`. No UI calls it. |
| `/api/action/scan_chests` (`Server:471-473`) | Logs a warning, sends nothing, returns 200 ok. No UI calls it. |
| `/api/action/check_fuel` | 501; no UI calls it. |

---

## 5. Auth and transport, in order

1. **Token sources**: header `X-Swarm-Token`, then query `token`, then cookie `swarm_token`
   (`Server:101-110`). The comparison is `MessageDigest.isEqual`, constant-time.
2. **Origin**: checked after auth (`Handler:37`). Missing `Origin` is allowed; a present one
   must be exactly `http://<Host>`. There is no CORS header and no method allowlist.
3. **Cookie**: set only on `GET /` (`Handler:118-123`), `HttpOnly; SameSite=Strict`, no
   `Secure`, no `Max-Age`. It is a session cookie.
4. **WebSocket**: the browser sends the cookie on the same-origin handshake. This was verified:
   `{"event":"ws_open","has_cookie":true,"origin":"http://127.0.0.1:8791"}`. The panel itself
   passes no token (`Script:24`), so the cookie is the entire handshake.
5. **Token exposure**: the full URL including `?token=` is printed to Minecraft chat
   (`Server:174`) and remains in browser history. Responses set `Referrer-Policy: no-referrer`.
6. **CSP** (`Handler:116-123`): `default-src 'none'; script-src 'self'; style-src 'self'
   'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws://<Host>; base-uri 'none';
   form-action 'none'; frame-ancestors 'none'`. `unsafe-inline` for styles is required by the
   inline `<style>` block. The WS source is built from the raw `Host` header.

---

## 6. Render loop

- `SwarmWebServer.start()` binds and starts
  `scheduler.scheduleAtFixedRate(this::broadcastState, 2, 2, SECONDS)` (`Server:154-180`).
- `addClient()` sends `buildStateJson()` and `buildLogJson()` immediately on connect
  (`Server:194-200`), so a new tab does not wait 2 s for first paint.
- `buildStateJson()` (`Server:237-312`) reads **live** host state on the scheduler thread:
  `getConnectionCount()`, `getWorkerNames()`, `getBotStatus()`, `TaskQueue.getAllTasks()`,
  `getNickname()`. It is not a cached snapshot.
- Both `getConnectionCount()` and `getWorkerNames()` call `cleanDead()`
  (`Host:277-292`), which mutates the connection list and can requeue active tasks for dead
  bots. A read from the render loop therefore has side effects.
- `host` is a plain non-volatile field written on the Minecraft thread and read from the
  scheduler/Netty threads; there is no synchronization or snapshot handoff.
- `Minecraft.getInstance().getUser().getName()` is called from the scheduler thread
  (`Server:246`) to fill `hostName`.
- The client rebuilds **everything** on every state frame: `renderBots()` (`Script:75-169`)
  recreates every card, nickname input and both `<select>`s; `renderTasks()`
  (`Script:171-209`) recreates every row. There is no keyed diff and no focus preservation.
- Consequence, verified: focus a nickname input, type, wait 2.6 s → the node is replaced,
  `document.activeElement` becomes `BODY`, and the value reverts to the server value.
- Logs: server keeps 500 `eventLog` strings and broadcasts each new one immediately; the
  client keeps 500 and appends. `log_history` re-renders the buffer. `buildLogJson()`
  re-parses every stored JSON string on each client connect (`Server:314-327`).
- Task order comes from `PriorityQueue.toList()` (`TaskQueue:93-95`), which returns **heap
  order**, not priority or creation order. Rows reshuffle as tasks are added/removed.
- Client `post()` (`Script:253-264`) awaits `fetch`, ignores `response.ok` and the body, and
  only `console.error`s on a network throw. There is no error surface in the page.
- WebSocket reconnect is a fixed 3 s retry (`Script:33-40`). It has no backoff, no attempt
  counter, and no distinction between "server down" and "401 handshake".

---

## 7. Lies

Each item is a mismatch between what the panel says and what the code does.

### L1 — “N bots online” counts connections, not online bots
`Panel:264` renders `state.connected`; `Server:252` fills it from
`host.getConnectionCount()` → `connections.size()` (`Host:277-280`). A connected socket that
has never sent a status still counts. Verified: the header said **“3 bots online”** while the
third card was `OFFLINE` with `❤ 0`.

### L2 — Every POST succeeds, even when nothing happened
`Handler:70-75` answers `{"ok":true}` for every path. `handleApiCommand` returns early when
the mod is not host (`Server:331-333`), logs “API error” on an exception (`Server:497-499`),
and ignores unknown paths entirely. The panel never reads the response. Symptom: a click can
do nothing and the UI still looks successful.

### L3 — `+ Add` silently deletes completed, failed and cancelled tasks
`Server:376` calls `clearCompleted()` before adding. `TaskQueue.clearCompleted()` removes
COMPLETE, FAILED **and CANCELLED** (`TaskQueue:196-199`). The button's tooltip says only
“Queue a task (/api/task/add)”. Symptom: history disappears on add.

### L4 — `Clear Done/Failed` also clears cancelled tasks
Same `clearCompleted()` (`Server:403`). The label and tooltip name only COMPLETE and FAILED.

### L5 — Unknown items are rejected, but the input is cleared as if accepted
`Server:351-354` logs `"unknown item '<x>', not queued"` and returns. `Script:276-277` posts,
then immediately clears the field. The only trace is a WARN line in the log. HTTP still 200 ok.

### L6 — “Any bot” means “the first connected worker”
`Server:398` passes `getWorkerNames()[0]` when the target is any/empty. `dispatchNextTask`
returns early if that bot is halted or already active (`Host:398-410`) and does not try the
next worker. Symptom: a task sits PENDING with an idle second bot.

### L7 — The task quantity is not validated
`Panel:285` has no `min`; `Script:268` accepts `parseInt(...) || 64`; `Server:339` accepts any
integer. A negative quantity makes `getRemaining()` return 0 (`BotTask:65`), so the task is
immediately “complete” while its status can still be PENDING.

### L8 — Commands containing `"` are truncated in transit
`extractJson` (`Server:505-518`) finds the next `"` after the key and does not understand
escapes. Verified body `{"target":"all","cmd":"say \"hi\" \\ test"}` extracts `say \`. The
panel then logs the full typed string as “Command sent”, which is not what was sent.

### L9 — The command tooltip is wrong for `#`
`Panel:326` says “Send wraps it in exec”. The worker's `exec` case sends `#`-prefixed args
straight to chat (`Worker:336-345`), so `#goto …` is chat, not a dispatched command.

### L10 — “Follow Me” silently depends on Baritone / `#` chat
`Server:434-439` sends a protocol message; `Worker:921-947` turns it into `#follow player
<name>` in chat. If the client has no `#`/Baritone handler the bots do not move, but the panel
logs `"Follow me sent to all bots"`.

### L11 — Resume flips the card before the worker confirms
`Server:421-427` sets `status.halted = false` locally, then sends. If the worker is gone,
`sendTo` only writes a chat warning; the card already reads “Stop” and looks running.

### L12 — BLOCKED and CANCELLED have no badge styling
`BotTask.Status` includes `BLOCKED` and `CANCELLED` (`BotTask:13`), and `buildStateJson`
emits them. CSS defines only ACTIVE/PENDING/COMPLETE/FAILED (`Panel:148-151`). Verified in
the screenshots: both render as unstyled text inside the status span.

### L13 — The worker name is printed twice
`Script:101-117` appends a static nickname span and then an input whose value is the same
nickname. Verified: “Iron Goblin · IRON · Iron Goblin”.

### L14 — The log “Clear” looks permanent but is not
`Script:248-251` clears the client array and DOM only. On reconnect, `log_history` restores
the server's 500-line buffer (`Server:199`).

### L15 — The footer commit is the build's commit, which can be older than the running source
`build/resources/main/build-info.properties` contains `commit=c366290305bf`, while HEAD is
`8d1d276`. The footer is truthful about the artifact, not about the checkout. If an older jar
is installed, the panel advertises an older commit.

### L16 — Task order is not a defined order
`TaskQueue.getAllTasks()` → `toList()` → `new ArrayList<>(queue)` on a `PriorityQueue`
(`TaskQueue:71-73,93-95`). Iteration is heap order; rows can reshuffle between frames.

### L17 — `Check Fuel` / `Scan Chests` are honest, but the brief is wrong about them
They are disabled, send no request, and say “unavailable”. Their tooltips are accurate. The
server still exposes both paths; `check_fuel` returns 501, `scan_chests` returns 200 after a
warning.

---

## 8. Holes

Ordered by what would hurt Web v2 first.

### H1 — The panel has no token entry point; the cookie is the whole session
The only way in is the chat URL with `?token=`. `GET /` sets the cookie; `/app.js` and `/ws`
then rely on it. If the cookie is cleared, blocked, or the browser is different, the page
returns a plain-text 401 and there is no form to recover. The WebSocket has no token parameter
(`Script:24`), only the cookie. Verified cookie-on-handshake works today; that is a browser
behaviour, not something the panel manages.

### H2 — A failed WS handshake is indistinguishable from a dropped server
`Script:33-40` retries every 3 s forever. A 401 handshake fires `onclose`, the header says
“Reconnecting…”, and the page never tells the user the token is the problem.

### H3 — The WebSocket is a second command surface with no body contract
`Handler:92-102` passes any text frame as `(path, body)` to `handleApiCommand`. It is
authenticated at handshake, but it bypasses the POST path shape and the per-request origin
check. A cookie-bearing WS client can call any `/api/*` case with a malformed body.

### H4 — Every POST path is accepted
`Handler:70-75` has no route allowlist. A typo in a future control returns 200 ok and does
nothing. There is no 404/405 for APIs.

### H5 — The JSON body parser is not a JSON parser
`extractJson` (`Server:505-518`) cannot handle escaped quotes or backslashes, and it scans the
raw string rather than a parsed tree. Any control whose value can contain `"` or `\` — command,
nickname, item — can be truncated or misread.

### H6 — Locale can invalidate the entire state frame
`Server:269` uses `String.format("%.1f", status.health)` with the default locale. On a
comma-decimal JVM this emits `20,0`, which is invalid JSON. `JSON.parse` throws, `Script:55`
logs “Bad panel message”, and the page silently freezes on the last good state. `[code]`

### H7 — `esc()` does not cover all JSON control characters
`Server:520-523` escapes `\`, `"` and `\n` only. A raw `\r`, `\t`, or other control character
in a task reason, task description, nickname, or item name can produce invalid JSON.

### H8 — The token is exposed in chat and browser history
`Server:174` prints the full `?token=` URL. Anyone who sees a screenshot or stream gets the
token for the session. There is no rotation, no per-tab token, and no “copy URL” button that
would avoid the chat log.

### H9 — The cookie is not `Secure`, and the bind address is configurable
`Server:144-146` sets no `Secure` and no `Max-Age`. On loopback that is normal; if
`bind-address` is set to `0.0.0.0`, the token and all commands cross the LAN in cleartext HTTP.

### H10 — Origin checking only understands `http://`
`Server:112-117` compares `origin` to `http://` + Host. An HTTPS reverse proxy in front of the
panel would make every mutation 403, because the browser sends `https://…`. Not a problem on
loopback today; a blocker for any remote-access story.

### H11 — CSP interpolates the raw `Host` header
`Handler:116-123` builds `connect-src 'self' ws://<Host>` from the request header. A crafted
Host value could inject CSP directives or a different WS origin. The request must already be
authenticated, so this is low severity on loopback, but it should not be string concatenation.

### H12 — The WS handshake URL is hardcoded to `ws://`
`Handler:53` builds the accepted URL with `ws://` regardless of scheme. On an HTTPS page the
browser would use `wss://` (`Script:23`), which this server cannot serve without TLS.

### H13 — `buildStateJson` touches Minecraft and the filesystem off-thread
It calls `Minecraft.getInstance().getUser()` (`Server:246`) and `getWorkerNames()` →
`cleanDead()` → `taskQueue.requeueActiveForBot()` → `save()` (`Host:277-292`). The scheduler
and Netty threads are not the client thread; a task requeue can trigger disk I/O from a state
push.

### H14 — API work runs on the Netty event loop
`Handler:72` calls `handleApiCommand` inline. That method does registry lookups, `TaskQueue.save()`
file I/O, and `MeteorExecutor.execute` calls (`Server:329-503`). One slow disk stalls every
panel response and state push on that loop.

### H15 — No backpressure on WS broadcasts
`broadcast()` writes to every channel unconditionally (`Server:225-227`). A slow or stalled
client can grow its outbound buffer; there is no `isWritable` check and no drop policy.

### H16 — Destructive controls have no confirmation or undo
Stop All, Deposit All, PVP ON/OFF, Clear Done/Failed and task remove all fire on one click.
Given L2 (everything returns ok), a misclick is silent and irreversible from the panel.

### H17 — No staleness indicator anywhere
The header dot reflects the WebSocket, not data freshness. A locale parse failure (H6), a
stalled scheduler, or a host with no status all leave a green “Connected” dot and a page that
looks live.

### H18 — No error channel from server to page
`log()` is the only feedback path, and it is a scrolling stream. There is no toast, no
per-control result, no HTTP status display. `post()` ignores status and body (`Script:253-264`).

### H19 — `/api/action/scan_chests` returns success for a no-op
`Server:471-473` only logs. A caller cannot distinguish “not implemented” from “done” by the
response. The UI hides it, but the API is still there.

### H20 — Memory-only log buffer
`eventLog` is an in-memory `CopyOnWriteArrayList` capped at 500 (`Server:206-216`). A mod
restart loses all history; a browser reload recovers only what the current process still has.

---

## 9. What is missing

- **Storage view.** `EnhancedSwarmHost` holds a `StorageIndex` (`Host:41`) and exposes it
  (`Host:298`), but `buildStateJson` never reads it. There is no total, no per-item count, no
  chest count, and no “last scan” in the state JSON.
- **Waypoints.** `WaypointManager` is instantiated (`Host:42`) and exposed (`Host:275`), but no
  endpoint or state field serializes it. The panel has no waypoint list, add, or goto.
- **Warnings.** `logWarn` exists and the log colours WARN orange, but there is no count, badge,
  banner, or “needs attention” surface. A warning scrolls away.
- **Deposit configuration.** `DepositConfig`, linked chests and the base anchor are host state
  (`Host:40`, `Host:294`) with no panel representation.
- **Task detail.** No task age, priority, assigned-at, or failure history; only the current
  reason string.
- **Any confirmation / undo** for destructive actions.
- **A token entry form** and a visible auth failure state.
- **A registry view** — it does not exist, despite the fill-in block naming it.

---

## 10. Capture

Screenshots are in `DEEPDIVE/panel/current/shots/`, captured from the extracted page served by
the B1-faithful mock. The top-left “Freebuff” chip is the client's preview chrome, not the
page.

| File | Width × height | Region |
|---|---|---|
| `overview-1280.png` | 1280×720 | Header, Workers, Task Queue, Quick Actions (3-column layout) |
| `tasks-1280.png` | 1280×720 | Tasks focused; bot cards and actions visible |
| `log-1280.png` | 1280×720 | Live Log with all six fixture lines and the footer |
| `overview-900.png` | 900×720 | 2-column layout; Quick Actions pushed below the fold |
| `tasks-900.png` | 900×720 | Tasks at 900, with the action card starting below |
| `log-900.png` | 900×720 | Log at 900 with the collapsed multi-line entry |

Measured weight of a cold load:

- `index.html`: **10,572 bytes** (HTML + inline CSS).
- `app.js`: **11,047 bytes**.
- Total embedded assets: **21,619 bytes** uncompressed, no images or fonts.
- Requests: **2 HTTP GETs** (`/`, `/app.js`) plus **1 WebSocket upgrade** (`/ws`). Chromium
  made no `/favicon.ico` request in this run; the handler would 404 it. `[verified]`
- Server response headers add CSP, `Set-Cookie`, `Cache-Control: no-store`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.

Harness caveat: the game state and logs in the screenshots are fixtures, not a live client.
The page, CSS, JS, control wiring, request bodies, focus-loss behaviour and WS cookie
behaviour are real. The Java server itself was not launched.

---

## 11. Condition report

The panel is a working single-page console, not a dashboard. B1 fixed the two things that
made it dangerous — the token is now mandatory on every request and the XSS sinks are gone —
and it left a page that is honest about what it cannot do (`Scan Chests` and `Check Fuel` are
disabled and say so). What remains is a 2-second full-page repaint with no error channel, no
diffing, and no staleness signal: every state frame destroys the DOM, so typing a nickname
loses focus and reverts, and any parse failure freezes the page behind a green “Connected”
dot. The information architecture is thin — tasks and a log are real, but storage,
waypoints, deposit configuration and warnings exist on the host and are never serialized, so
the panel cannot answer “what do I have?” or “what is wrong?”. Several labels still promise
more than the code delivers: `+ Add` silently deletes history, `Clear Done/Failed` also
removes cancelled tasks, “Any bot” means the first worker, and commands containing quotes are
truncated before they reach a bot. It is usable for watching a run on one monitor; it is not
yet trustworthy as an operations console.

---

## 12. UNKNOWN

- **Port 6968 / 9999.** No file in this repo contains `9999` or a web-panel log line; this
  workspace's `logs/latest.log` has no Swarm entry. The code default is **8888**
  (`EnhancedSwarm.java:134-142`) and the TCP swarm default is **6969**. If a log showed 6968,
  it was a `web-port` setting in that instance, not the default. Marked UNKNOWN rather than
  guessed.
- **Whether every task type reports progress.** `TASK_PROGRESS` is parsed and applied for
  tasks that emit it (`EnhancedSwarmConnection.java:232-267`), but the UI only exposes
  GATHER/CRAFT/HUNT_FOOD/GRIND/SMITH/BUILD; the worker/BotBrain coverage for each type was not
  audited end to end.
- **Whether `#follow player <name>` works in this client.** Depends on the Meteor/Baritone
  chat integration; the panel only sends the protocol message.
- **Real-client rendering.** No Minecraft client was launched, so the screenshots are the real
  page with a mocked backend. A live client could differ in fonts/emoji and in the actual
  fixture values.
- **Comma-decimal locale behaviour.** H6 is derived from `String.format` semantics; it was not
  executed on a comma-locale JVM.
- **Maximum realistic task/bot counts.** No load test; the 2 s full rebuild is O(bots + tasks)
  and would need measurement before claiming a ceiling.

---

## Appendix — line index

| Symbol | Location |
|---|---|
| HTML text block | `SwarmWebPanel.java:5-355` (content 6–354) |
| Inline CSS | `SwarmWebPanel.java:12-254` |
| Status badge CSS | `SwarmWebPanel.java:142-151` (only ACTIVE/PENDING/COMPLETE/FAILED) |
| `connectedCount` markup | `SwarmWebPanel.java:264` |
| Add-task markup | `SwarmWebPanel.java:284-301` |
| Quick-action markup | `SwarmWebPanel.java:309-318` |
| Command markup | `SwarmWebPanel.java:323-327` |
| Log controls | `SwarmWebPanel.java:336-343` |
| Footer | `SwarmWebPanel.java:346-349` |
| Script tag | `SwarmWebPanel.java:352` |
| JS text block | `SwarmWebPanelScript.java:8-350` (content 9–349) |
| WebSocket open | `SwarmWebPanelScript.java:22-24` |
| Reconnect | `SwarmWebPanelScript.java:33-40` |
| State handler | `SwarmWebPanelScript.java:41-65` |
| `renderBots` | `SwarmWebPanelScript.java:75-169` |
| Nickname input/blur | `SwarmWebPanelScript.java:110-117` |
| Bot stop/resume | `SwarmWebPanelScript.java:142-150` |
| `renderTasks` | `SwarmWebPanelScript.java:171-209` |
| `appendLogEntry` | `SwarmWebPanelScript.java:217-236` |
| `post()` | `SwarmWebPanelScript.java:253-264` |
| `addTask()` | `SwarmWebPanelScript.java:266-278` |
| `sendCmd()` | `SwarmWebPanelScript.java:301-308` |
| `bindPanel()` | `SwarmWebPanelScript.java:314-343` |
| `isAuthorized` | `SwarmWebServer.java:101-110` |
| `isOriginAllowed` | `SwarmWebServer.java:112-117` |
| `sessionCookie` | `SwarmWebServer.java:144-146` |
| `start()` / bind / scheduler | `SwarmWebServer.java:154-180` |
| `addClient` | `SwarmWebServer.java:194-200` |
| `log()` | `SwarmWebServer.java:206-216` |
| `broadcastState` | `SwarmWebServer.java:232-235` |
| `buildStateJson` | `SwarmWebServer.java:237-312` |
| Health format | `SwarmWebServer.java:269` |
| `buildLogJson` | `SwarmWebServer.java:314-327` |
| `handleApiCommand` | `SwarmWebServer.java:329-503` |
| `+ Add` handler | `SwarmWebServer.java:336-400` |
| Silent `clearCompleted` | `SwarmWebServer.java:376` |
| “Any bot” dispatch | `SwarmWebServer.java:398` |
| `extractJson` | `SwarmWebServer.java:505-518` |
| `esc` | `SwarmWebServer.java:520-523` |
| Auth gate | `SwarmWebHandler.java:33-36` |
| Origin gate | `SwarmWebHandler.java:37-40` |
| `check_fuel` 501 | `SwarmWebHandler.java:44-48` |
| WS upgrade | `SwarmWebHandler.java:51-67` |
| Generic POST | `SwarmWebHandler.java:70-75` |
| `GET /` | `SwarmWebHandler.java:78-81` |
| `GET /app.js` | `SwarmWebHandler.java:83-86` |
| WS text frame | `SwarmWebHandler.java:92-102` |
| `sendHtml` / CSP | `SwarmWebHandler.java:115-124` |
| Host state containers | `EnhancedSwarmHost.java:40-42` |
| `getConnectionCount` | `EnhancedSwarmHost.java:277-280` |
| `getWorkerNames` | `EnhancedSwarmHost.java:282-292` |
| `dispatchNextTask` | `EnhancedSwarmHost.java:398-410` |
| `clearHaltedFlags` | `EnhancedSwarmHost.java:450-452` |
| `clearCompleted` | `TaskQueue.java:196-199` |
| Heap-order task list | `TaskQueue.java:71-73,93-95` |
| Task statuses | `BotTask.java:13` |
| Task progress writes | `EnhancedSwarmConnection.java:232-267` |
| Follow → `#follow` | `EnhancedSwarmWorker.java:921-947` |
| `exec` / `#` chat | `EnhancedSwarmWorker.java:336-345` |
