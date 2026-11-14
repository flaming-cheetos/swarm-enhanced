# Enhanced Swarm `/v2` — Panel Recreation Specification

## TL;DR — briefing for another AI

Recreate the Enhanced Swarm `/v2` host panel 1:1. The current shipped panel is a static host-operator control surface with four tabs: Overview, Tasks, Log, and Actions. It has no build step, npm, router, state library, external font, icon set, image, or database. For an exact vanilla clone, use the committed `index.html`, `app.css`, and `app.js`; only replace the `__SWARM_TOKEN__` meta value at serve time.

**React/JS rebuild:** supported. You may implement this as a React + JavaScript app (Vite or equivalent) instead of shipping the current vanilla files. The visual, DOM, state, endpoint, auth, and behavior contracts in this document are the target; the implementation language is not. If the app ships inside the addon, commit the build output into `web/v2/` and keep `gradlew build` free of Node. No router, state library, component kit, or external UI asset set unless explicitly requested. The current repo does **not** already contain a React app; this is a rebuild path, not a description of the shipped files.

Server/auth contract:

- Bind host/port (documented default `127.0.0.1:8888`); serve `/v2`, `/v2/app.css`, `/v2/app.js`, `/api/items`, and `/ws`.
- Require the shared token on every request via `X-Swarm-Token`, then `?token=`, then the `swarm_token` cookie; compare in constant time.
- Allow no `Origin`; if `Origin` is present, require exactly `http://<Host>`. No CORS, no TLS.
- Set the cookie as `swarm_token=<token>; Path=/; HttpOnly; SameSite=Strict`, plus `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and the strict CSP.
- Return `{"ok":true}` for every accepted POST, even for unknown APIs, invalid payloads, missing host state, or unexecuted worker commands. Unknown GET is 404. `/api/action/check_fuel` is always 501.
- On `/ws`, send `state`, then `log_history`, then state every 2 seconds; push logs immediately.

Data contract:

- `state` has `modVersion`, `buildCommit`, `hostName`, `dimension`, `connected`, `bots[]`, and `tasks[]`. Offline bots omit `halted`; task JSON exposes lease diagnostics but never the lease token.
- Logs are `{time, level, msg}`. The server keeps 500 entries; the browser keeps 2,000. Clearing the log is local-only.
- `/api/items` is a cached, sorted registry list and includes `minecraft:air`; the UI requests it once.

UI contract:

- Keep the exact four tabs, labels, colors, breakpoints, text, titles, and no-`innerHTML` rule.
- Key worker cards by `username` and task rows by `id`; preserve focused inputs while live updates stream in.
- The status strip follows the first online bot and goes stale after 5 seconds without a state frame.
- Card Stop posts `/api/task/cancel {bot}` without removing the queue row; row ✕ posts `/api/task/remove {id}` without stopping the worker.
- All Quick Actions fire immediately with no confirmation. The Scan Chests and Check Fuel buttons are disabled and send nothing.
- Recreate the missing v2 HP/food-bar CSS exactly as it is: the DOM exists, but there are no `.hp-bar`/`.hp-fill` rules, so the bars remain invisible.
- Status is `CODE ONLY`; prior browser evidence used fake players only, with no in-game observation.

**Snapshot:** branch `fix/phantom-task-types`, `HEAD 226b1b3bcef5e6eeb5e564539040ff806adbc336`, working tree read on 2026-11-13. The `/v2` HTML/CSS/JS files are at `HEAD`; `SwarmWebServer`, `TaskQueue`, `BotTask` and several bot classes have uncommitted working-tree edits. This document describes the current working tree, not `origin/main` (`d99e758`). See **Source index** at the end for hashes.

**Status:** `CODE ONLY`. `/v2` has been opened on a desktop browser and a phone against fake players. Layout, tabs and the task queue render. No bot has driven it in game. The old `/` page is legacy and is not part of this recreation target.

**What this document is:** a build contract for an engineer or another AI that has to reproduce the panel 1:1. It covers the purpose, visual system, DOM, client behavior, live data shape, every HTTP/WebSocket endpoint, server-side dispatch, known quirks, and an acceptance checklist.

**What this document is not:** a replacement for the source. If the source and this document disagree, the source wins. If a behavior is marked **derived**, it was inferred from CSS/JS/server structure and was not watched in a browser.

---

## 1. What the site is for

Enhanced Swarm is a Meteor Client addon for Minecraft 26.3 that runs a host and multiple worker bots on anarchy servers. The `/v2` panel is the host operator’s browser control surface. It is not a public website, a player portal, or a multi-user dashboard.

The panel exists to answer four questions and provide four controls:

1. **Who is connected, where are they, and what are they doing?** — the Overview tab.
2. **What work is queued, running, blocked or failed?** — the Tasks tab.
3. **What just happened?** — the live Log tab.
4. **Can I send a group action or a raw command right now?** — the Actions tab.

The data path is:

```text
Minecraft host (EnhancedSwarm)
        |
        |  TCP worker connections
        +-------------------------------> worker bots
        |
        |  SwarmWebServer (Netty)
        |    GET  /v2, /v2/app.css, /v2/app.js
        |    GET  /api/items
        |    POST /api/*
        |    WS   /ws
        v
Browser panel (this document)
```

The panel never talks to a worker directly. Every action becomes a host-side call (`sendAll`, `sendTo`, queue mutation, or a worker verb) and the server acknowledges the HTTP request. The HTTP 200 means “the host accepted the request,” not “a worker performed it.”

### Operating assumptions

- One host, one shared token, one operator.
- The panel is normally opened from the same machine or from a phone on the same trusted LAN.
- The server binds a configurable address and port; the documented default is `127.0.0.1:8888`.
- There is no account system, no per-user data, no database, no multi-host switcher, no TLS, and no CORS.
- The token is the only authentication factor. It is printed to the host chat and is placed in the panel URL.
- The current panel is deliberately static: `index.html`, `app.css`, and `app.js` are committed source and served from the jar. There is no npm/build step for the current panel. A React/JS rebuild is allowed; see §8.6.
- `/` is the old panel. It must keep working, but it is not the page being recreated here.

The panel’s own security posture is part of the product contract:

- Every HTTP request and the WebSocket handshake require the token.
- A browser `Origin` header, when present, must be exactly `http://<Host>`.
- Responses set `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and `Cache-Control: no-store`.
- The page is served with a restrictive CSP and an `HttpOnly; SameSite=Strict` cookie.
- There is no `Access-Control-Allow-Origin`; the browser is expected to be same-origin.
- Everything the panel displays is text, never HTML. The JS uses `textContent` and `createElement`, not `innerHTML`.

These rules are not decoration. A recreation that makes the panel prettier but drops the token/origin checks is not a 1:1 recreation.

---

## 2. Source map and runtime architecture

### 2.1 Files that define the page

| File | Lines | Bytes | Role |
|---|---:|---:|---|
| `src/main/resources/web/v2/index.html` | 153 | 8,717 | Static page skeleton, all IDs, tab structure, labels, forms, CSP-relevant markup. |
| `src/main/resources/web/v2/app.css` | 490 | 12,247 | Entire visual system, desktop rail, cards, colors, responsive breakpoints. |
| `src/main/resources/web/v2/app.js` | 769 | 26,481 | WebSocket client, state renderer, log viewer, token recovery, all UI actions. |
| `src/main/java/com/rdh/swarm/web/SwarmWebPanelV2.java` | 50 | 1,535 | Loads the three classpath assets and replaces the `__SWARM_TOKEN__` meta value. |
| `src/main/java/com/rdh/swarm/web/SwarmWebHandler.java` | 187 | 7,475 | Netty HTTP routing, auth/origin gates, WebSocket upgrade, static/API responses. |
| `src/main/java/com/rdh/swarm/web/SwarmWebServer.java` | 620 | 30,275 | Token, cookie, state JSON, task/log JSON, item cache, API command switch, 2-second broadcast. |
| `src/main/java/com/rdh/swarm/web/SwarmWebPanel.java` | — | 11,055 | Legacy `/` page. Not specified here. |
| `src/main/java/com/rdh/swarm/task/BotTask.java` | 165 | — | Task fields, types, statuses, lease state, progress strings. |
| `src/main/java/com/rdh/swarm/task/TaskQueue.java` | — | — | Queue ordering, clear/remove/cancel, nicknames, persistence, claim/lease behavior. |

File hashes for the exact source snapshot are in **§12**.

### 2.2 How the page is served

`SwarmWebPanelV2` has three static strings loaded from the classpath:

- `/web/v2/index.html`
- `/web/v2/app.css`
- `/web/v2/app.js`

`isAvailable()` is true only when **all three** are non-empty. If any one is empty, `GET /v2` returns HTTP 500 `v2 assets missing`. The CSS and JS routes independently return 404 when their own asset is empty.

`html(token)` replaces every literal `__SWARM_TOKEN__` in `index.html` with an HTML-escaped token. The token is placed in:

```html
<meta name="swarm-token" content="...">
```

The JS reads that meta tag, then may override it from the query string or sessionStorage. The server does not template anything else.

The page uses no external fonts, images, scripts, styles, or CDN resources. Emoji in the labels are literal Unicode text. The only local assets are `/v2/app.css` and `/v2/app.js`.

### 2.3 Netty server

`SwarmWebServer.start()` builds a Netty server with:

- one boss event-loop thread and two worker threads;
- `HttpServerCodec`;
- `HttpObjectAggregator(65536)` — 64 KiB request aggregation limit;
- `WebSocketServerCompressionHandler`;
- `SwarmWebHandler`.

It binds the configured `bindAddress` and `port`. `panelHost()` reports `127.0.0.1` when the bind address is blank, `0.0.0.0`, or `::`; otherwise it reports the configured address. The chat log line is:

```text
Web panel: http://<panelHost>:<port>/?token=<token>
```

If no token was configured, `generateToken()` creates 24 random bytes and formats them as 48 lowercase hex characters. The server prints that generated token once.

Every response calls `ChannelFutureListener.CLOSE`; the HTTP connection is closed after one response. There is no keep-alive contract to reproduce.

### 2.4 Live update loop

- `addClient(channel)` adds the channel to the WebSocket group.
- Immediately after the handshake, the client receives one `type: "state"` frame and one `type: "log_history"` frame.
- A single-thread scheduler calls `broadcastState()` every 2 seconds, but only when at least one WebSocket client exists.
- `log(...)` appends a log entry to a 500-entry server-side ring and broadcasts it immediately as `type: "log"`.
- After any API command, `handleApiCommand` calls `broadcastState()` once more, so a successful control can update the page before the next 2-second tick.
- The browser appends incoming logs as they arrive; its local log buffer is capped at 2,000 entries.

### 2.5 Auth and origin order

For every HTTP request, including static assets, the handler runs:

1. `isAuthorized(req)` — token from `X-Swarm-Token`, then `?token=`, then the `swarm_token` cookie. Comparison uses `MessageDigest.isEqual`.
2. `isOriginAllowed(req)` — no `Origin` header is allowed; if present, it must equal `http://` + the `Host` header, case-insensitive.

Failure is `401 text/plain Unauthorized` or `403 text/plain Origin not allowed`. There is no loopback bypass in the current code.

The WebSocket handshake goes through the same two checks before the upgrade. A browser opened at `https://...` would send `Origin: https://...`, which does **not** match the server’s `http://` rule; the current panel is an HTTP-only contract.

### 2.6 Response headers

Every response, including errors:

```http
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
Cache-Control: no-store
Content-Length: <bytes>
```

HTML responses additionally set:

```http
Set-Cookie: swarm_token=<token>; Path=/; HttpOnly; SameSite=Strict
Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws://<host>; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

The CSP’s `connect-src` gains ` ws://<Host>` only when `Host` is present. There is no `Secure` attribute on the cookie, no HSTS, and no `Access-Control-Allow-Origin`.

---

## 3. What the page looks like

### 3.1 Desktop wireframe

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ ● Connected · Miner01   overworld   -549 91 598   Gathering · 23/64   ❤ 19.5 · 🍗 17   running │  status strip
├─────────────────────────────────────────────────────────────────────────────┤
│ ⚡ Enhanced Swarm  [v2]                              ● Connected | 2 bots online │  header
├───────────────┬─────────────────────────────────────────────────────────────┤
│ Overview      │  ┌───────────────────────────────────────────────────────┐  │
│ Tasks         │  │ 🤖 WORKERS                                            │  │
│ Log           │  │  ┌────────────────────┐  ┌────────────────────┐      │  │
│ Actions       │  │  │ Miner01  Iron [n]  │  │ Wood02  Wood [n]  │      │  │
│               │  │  │ ❤ 19.5  🍗 17     │  │ ❤ 20    🍗 20     │      │  │
│               │  │  │ Gathering 23/64    │  │ Idle               │      │  │
│               │  │  │ ⏹ Stop             │  │ ⏹ Stop             │      │  │
│               │  │  └────────────────────┘  └────────────────────┘      │  │
│               │  └───────────────────────────────────────────────────────┘  │
├───────────────┴─────────────────────────────────────────────────────────────┤
│ Enhanced Swarm version 0.1.0+abc1234 · built from abc1234                   │  footer
└─────────────────────────────────────────────────────────────────────────────┘
```

The rail is 190 px wide on desktop. The content column is `minmax(0, 1fr)`. The whole shell is capped at 1,500 px and centered. The status strip sits above the header; the header is sticky at the top of the viewport; the rail is sticky below it.

### 3.2 Design tokens

These are exact CSS custom properties from `app.css`:

| Token | Value | Used by |
|---|---|---|
| `--bg` | `#0d0d0d` | page background |
| `--card` | `#1a1a1a` | standard cards |
| `--card-2` | `#111` | bot cards, task rows |
| `--header` | `#1a1a2e` | header |
| `--line` | `#2a2a2a` | card borders, separators |
| `--line-2` | `#333` | controls, tab borders |
| `--accent` | `#7b7bff` | headings, labels, focus |
| `--accent-2` | `#4a4aff` | header underline, active rail |
| `--text` | `#e0e0e0` | body text |
| `--muted` | `#888` | secondary text |
| `--muted-2` | `#555` | empty/help text |
| `--ok` | `#4cff8a` | online, success |
| `--warn` | `#ffaa44` | warning, blocked |
| `--err` | `#ff4c4c` | offline, failure, danger |
| `--rail` | `190px` | desktop rail width |

The page is permanently dark. There is no theme toggle, no light mode, and no system-color media query.

### 3.3 Typography and base rules

```css
body {
  font-family: 'Segoe UI', monospace;
  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
  overflow-x: hidden;
}
```

- Root/body reset: `box-sizing: border-box`, zero margin and padding.
- Buttons, inputs and selects inherit the font.
- The fallback is a monospace face, even though the first choice is Segoe UI.
- Headings and card titles are uppercase with `letter-spacing: 1px`.
- Log rows, coordinates and status coordinates use monospace.
- `::selection` is not customized.
- Scrollbars are WebKit-styled: 6 px wide, `#111` track, `#333` thumb, 3 px radius.

### 3.4 Status strip

Container:

- relative, `z-index: 160`;
- background `#101018`;
- bottom border `1px solid var(--line)`;
- padding `8px 16px`.

`.status-line`:

- flex, centered, 8 px gap;
- max-width `1500px`, centered;
- font-size `.78rem`, color `#b8b8c8`;
- `white-space: nowrap`, `overflow: hidden`.

Field colors:

| Field | Color |
|---|---|
| `.status-sep` | `#555` |
| `.status-bot` | white, weight 600 |
| `.status-dim` | `#888` |
| `.status-coords` | `#7b7bff`, monospace |
| `.status-task` | `#cfcfe0`, flex-shrink with ellipsis |
| `.status-vitals` | `#cfcfe0` |
| `.status-halt` | `#ffaa44` |
| `.status-conn` | `#888` |

The connection dot is an 8 px circle, red by default, green when `.connected`, amber when `.rejected`.

The token row (`#tokenStrip`) is hidden by default. When shown it is a flex row, wraps, max-width 1500, margin-top 8, top border `1px solid #3a2a1a`, font-size `.8rem`, color `#ffaa44`. Its input is up to 320 px wide / 55vw, background `#222`, border `#333`, white text, radius 4.

### 3.5 Header

```css
.header {
  background: var(--header);
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-bottom: 2px solid var(--accent-2);
  position: sticky;
  top: 0;
  z-index: 100;
}
```

- Title `h1`: `.1rem`, accent color, nowrap.
- The `v2` tag is inline-block, 1 px accent-2 border, 3 px radius, `.62rem`, accent, vertical-align middle.
- Header status is `.8rem`, muted, nowrap.
- Header connection dot uses the same `.ws-indicator` style.

### 3.6 Shell, rail and cards

```css
.app-shell {
  display: grid;
  grid-template-columns: var(--rail) minmax(0, 1fr);
  gap: 12px;
  max-width: 1500px;
  margin: 0 auto;
  padding: 12px;
  align-items: start;
}
```

The rail is a sticky flex column at `top: 74px`, 6 px gap. Each tab:

- left-aligned;
- background `#141420`;
- border `1px solid #2a2a3a`;
- text `#9a9ab0`, `.85rem`;
- padding `10px 12px`, radius 6, min-height 40;
- hover background `#1d1d2e`, white text;
- active background `#2a2a4a`, border accent-2, white text.

Cards:

- background `--card`;
- border `1px solid --line`;
- radius 8;
- padding 12;
- `min-width: 0`.

Card titles are `.85rem`, accent, uppercase, letter-spacing 1, bottom border, 10 px bottom margin.

### 3.7 Buttons and form controls

Base button:

- background `#2a2a4a`;
- accent text;
- border `1px solid accent-2`;
- padding `6px 10px`, radius 4;
- `.8rem`, min-height 34, nowrap;
- hover background `#3a3a6a`, white text;
- `transition: all .15s`.

Variants:

- `.danger`: background `#3a1a1a`, red text, red border; hover `#5a2a2a`.
- `.success`: background `#1a3a1a`, green text, green border; hover `#2a5a2a`.
- `.warn`: background `#3a2a1a`, amber text, amber border.
- `:disabled`: background `#151515`, text `#666`, border `#333`; no hover change; `cursor: not-allowed`.

Inputs and selects:

- background `#222`, border `#333`, white text;
- padding `6px 8px`, radius 4, `.82rem`, width 100%;
- focus removes outline and changes border to accent.

### 3.8 Section-specific visual rules

The rest of the exact visual values are in the component sections below. The major ones:

- Worker grid: `repeat(auto-fit, minmax(280px, 1fr))`, 8 px gap.
- Task rows: flex, 8 px padding, `#111` background, radius 6, `.82rem`.
- Add-task grid: `minmax(0, 1fr) 64px 110px 120px auto`.
- Actions grid: three equal columns on desktop.
- Log viewport: `height: 56vh; min-height: 260px; max-height: 640px`.
- Footer: centered, `#555`, `.72rem`, padding `10px 12px 18px`.

### 3.9 Responsive rules

There are three breakpoints.

#### `max-width: 899px`

- `body` gets bottom padding `calc(72px + env(safe-area-inset-bottom))`.
- Status strip becomes sticky at `top: 0`, padding `6px 10px`.
- Header becomes static (not sticky), padding `10px 12px`; title `1rem`.
- Shell switches to `display: block`, padding `10px 10px calc(18px + env(safe-area-inset-bottom))`.
- Rail becomes fixed at the bottom, full width, horizontal flex, `z-index: 220`, background `#12121a`, top border, safe-area padding.
- Tabs become equal-width, centered, min-height 44, padding `8px 4px`.
- Action buttons get min-height 44.
- Composer becomes sticky above the bottom rail at `bottom: calc(62px + env(safe-area-inset-bottom))`, background card, 8 px top padding, `z-index: 50`.

#### `max-width: 700px`

- Add-task grid becomes `72px minmax(0, 1fr)`.
- Item spans both columns; qty is col 1; type col 2; bot spans both; add button spans both.
- Actions grid becomes two columns.
- Bots grid becomes one column.
- Log viewport is `50vh`.

#### `max-width: 520px`

- Hide `.status-dim`, `.status-coords`, `.status-vitals`, `.status-sep`.
- Status line gap 6, font `.72rem`; task field keeps the remaining flex space.
- Header wraps, 4 px gap; status `.72rem`.
- Log chips become a horizontal scroller with no wrapping; filter goes full-width.
- Command target select becomes 84 px.

### 3.10 Visual gaps that are part of the current truth

- `app.js` creates `.hp-bar`, `.hp-fill` and `.food-fill` elements in each worker card, but `app.css` has **no rules for those three classes**. They are empty block divs with no height, no background, and no visible fill. The current panel therefore has no visible health/food bars, even though the legend `❤` and `🍗` values render in the stats line. This is derived from the absence of CSS rules, not from a screenshot.
- The CSS uses emoji and no icon library. Replacing emoji with SVG icons is a visual change, not a 1:1 port.
- The `v2` tag is only the string `v2` inside a bordered span; it is not a version number.

---

## 4. Screen and component contract

### 4.1 Page shell

The HTML order is exactly:

1. `.status-strip#statusStrip`
2. `.header`
3. `.app-shell`
   - `nav.tab-rail[role=tablist]`
   - `main.panels`
4. `footer.footer`
5. `<script src="/v2/app.js"></script>`

The four tabs, in order:

| Tab ID | `data-tab` | Label | Controlled panel |
|---|---|---|---|
| `tab-overview` | `overview` | `Overview` | `panel-overview` |
| `tab-tasks` | `tasks` | `Tasks` | `panel-tasks` |
| `tab-log` | `log` | `Log` | `panel-log` |
| `tab-actions` | `actions` | `Actions` | `panel-actions` |

Only one panel is visible. The initial active tab is `overview` unless `sessionStorage["swarm.tab"]` names one of the four known tabs. Each tab has `role="tab"`, `aria-selected` and `aria-controls`; the panels have `role="tabpanel"` and `aria-labelledby`. The active tab gets `.is-active` and `aria-selected="true"`; inactive panels get the `hidden` attribute.

The active tab persists in `sessionStorage` under `swarm.tab` after every `activateTab` call.

### 4.2 Header

Copy:

```html
<h1>⚡ Enhanced Swarm <span class="v2-tag">v2</span></h1>
<div class="status">
  <span class="ws-indicator" id="wsIndicator"></span>
  <span id="wsStatus">Connecting...</span>
  &nbsp;|&nbsp;
  <span id="connectedCount">0</span> bots online
</div>
```

`connectedCount` is the number of bot entries with `online === true`, not the server’s `connected` field. `wsStatus` and both indicators change through `setConnection()`:

| Mode | `wsStatus` / `stripConn` | Indicator classes |
|---|---|---|
| `connected` | `Connected` | `ws-indicator connected` |
| `reconnecting` | `Reconnecting...` | `ws-indicator` |
| `rejected` | `Token rejected` | `ws-indicator rejected` |

At boot, `setConnection("reconnecting")` is called before `connect()`.

### 4.3 Status strip

DOM:

```html
<div class="status-strip" id="statusStrip">
  <div class="status-line">
    <span class="ws-indicator" id="stripIndicator"></span>
    <span class="status-conn" id="stripConn">Connecting...</span>
    <span class="status-sep">·</span>
    <span class="status-bot" id="stripBot">no bot online</span>
    <span class="status-dim" id="stripDim">dimension unknown</span>
    <span class="status-coords" id="stripCoords">unknown</span>
    <span class="status-task" id="stripTask">idle</span>
    <span class="status-vitals" id="stripVitals">❤ unknown · 🍗 unknown</span>
    <span class="status-halt" id="stripHalt">unknown</span>
  </div>
  <div class="token-strip" id="tokenStrip" hidden>...</div>
</div>
```

`renderStrip(bots, ageMs)` chooses `firstOnline(bots)` — the first bot object with `online` truthy. It never cycles through bots and never shows a different bot just because the first one is stale.

Staleness:

- `ageMs = Date.now() - lastStateAt`.
- `stale = ageMs > 5000`.
- A stale value is rendered as `unknown (stale <age>)`, where `<age>` is `Ns`, `Nm`, or `Nh` (`ageLabel`).
- The one-second interval only re-renders the strip after a state; it does not update cards.

No online bot branch:

| Field | Text |
|---|---|
| bot | `no bot online` |
| dimension | `dimension unknown` (or stale text) |
| coords | stale text or `unknown` |
| task | `idle` |
| vitals | `❤ unknown · 🍗 unknown` (or stale text on both sides) |
| halt | stale text or `unknown` |

Online bot branch:

- Name: `bot.nickname || bot.username || 'unknown'`.
- Dimension: `state.dimension || 'unknown'`; this is global state, not per bot.
- Coordinates: if `Number(bot.x/y/z)` is finite, `x y z`; otherwise `unknown`. The server already casts coordinates to integers.
- Task: `(bot.task || 'idle')` plus ` · <progress>` when `bot.progress` is non-empty.
- Vitals: `❤ <health> · 🍗 <food>`; missing/empty health or food becomes `unknown`.
- Halt: `halted` → `halted`; otherwise `bot.state || 'running'`.

Token strip:

```html
<span class="token-msg">Token rejected. Paste the token from the client log.</span>
<input id="tokenInput" type="password" autocomplete="off" placeholder="token" aria-label="Swarm token">
<button id="tokenRetry" class="success" type="button">Retry</button>
```

It is hidden initially. It appears only after `rejectToken()`. The input is cleared after a successful retry. The user can press Enter or click Retry.

### 4.4 Overview / Workers tab

Card:

```html
<div class="card" id="botsCard">
  <h2>🤖 Workers</h2>
  <div id="botsList" class="bots-list">
    <div class="empty" id="botsEmpty">No bots connected</div>
  </div>
</div>
```

Empty state: `No bots connected`, muted-2, `.82rem`.

The worker grid is `repeat(auto-fit, minmax(280px, 1fr))` with an 8 px gap. Each card is keyed by `bot.username` in a `Map`; cards are created once and updated in place. Removed workers are removed from the DOM.

Card structure:

```text
.bot-card
  .bot-name
    .dot (●)
    .bot-label (nickname || username || unknown)
    .tier-badge (tier || Wood)
    input.nick-input
  .hp-bar > .hp-fill
  .hp-bar > .hp-fill.food-fill
  .bot-stats
    span (❤ health)
    span (🍗 food)
    span (state || IDLE, or halted)
  .bot-task
  .bot-progress
  .bot-coords
  .bot-actions > button
```

Card visual:

- background `#111`, border-left `3px solid #4cff8a`, padding 10, radius 6;
- offline adds `.offline`, making the left border `#ff4c4c`;
- `.bot-name` is `.95rem`, bold white, flex with wrap;
- `.bot-label` truncates with ellipsis if needed;
- `.dot` is `.7rem`; `.online` green, `.offline` red;
- `.bot-stats` is `.8rem`, `#aaa`, 12 px gap, wrap;
- `.bot-task` is `.82rem`, `#ccc`, `overflow-wrap: anywhere`;
- `.bot-progress` is `.8rem`, accent;
- `.bot-coords` is `.75rem`, `#666`, monospace;
- `.bot-actions` is a 4 px-gap flex row, margin-top 6.

Tier badge values:

| Tier | Background | Text |
|---|---|---|
| `WOOD` | `#5c3d1e` | `#d4a054` |
| `STONE` | `#3a3a3a` | `#aaa` |
| `IRON` | `#2a3a4a` | `#88aacc` |
| `DIAMOND` | `#0d2a3a` | `#4ae8ff` |
| `NETHERITE` | `#2a1a2a` | `#cc88ff` |

The tier text uses the server value as-is (`bot.tier || 'Wood'`) while the class uses uppercase. Unknown tiers keep `.tier-badge` with no tier color.

Health/food:

- `health = Number(bot.health)`, `food = Number(bot.food)`.
- Fill width is clamped to `0..100%`: `value / 20 * 100`.
- Missing values render text `unknown`; the fill is 0%.
- `.hp-bar` / `.hp-fill` have no CSS in the v2 stylesheet; see §3.10. The DOM exists but there is no visible bar.

Nickname input:

- `.nick-input`, 90 px wide max 45%, `.75rem`.
- Value is `bot.nickname || bot.username || ''`.
- `data-server-value` is updated on every render.
- On `blur`, if the trimmed value is non-empty and differs from the last server value, POST `/api/nickname` with `{username: bot.username, nickname: value}`.
- Enter does not save; only blur.
- If the input is focused, incoming state does not overwrite it.
- No length/duplicate validation.

Action button:

- If `bot.halted` is true: `▶ Resume`, `.success`, title `swarm:<username> resume`, data-halted `true`.
- Otherwise: `⏹ Stop`, `.danger`, title `swarm:<username> cancel`, data-halted `false`.
- Clicking calls `resumeBot(bot.username)` or `cancelBot(bot.username)`.
- Offline bots normally lack `halted` in JSON, so they render Stop; clicking sends a cancel to an offline worker and the server logs it.

The card does not have a separate “remove worker” control, a per-worker task picker, or a confirmation dialog.

### 4.5 Tasks tab

Card:

```html
<h2>📋 Task Queue</h2>
<div id="tasksList" class="tasks-list">
  <div class="empty" id="tasksEmpty">No tasks</div>
</div>
<div class="add-task">...</div>
<datalist id="itemOptions"></datalist>
<div class="task-actions">
  <button id="clearDoneButton" title="Remove COMPLETE, FAILED and CANCELLED tasks (/api/task/clear_done)" type="button">Clear Done/Failed</button>
</div>
```

Task rows are keyed by `task.id`. A row is:

```text
.task-item
  .task-info
    .task-name      "<item || type || Task> × <quantity ?? 0>"
    .task-meta      "<progress || ''> | <bot || 'any'>"
    .task-meta.lease
    .task-meta.reason
  span.task-status
  button.danger "✕"
```

Visual:

- `.tasks-list` is a 6 px-gap column.
- `.task-item` is flex, space-between, centered, gap 6, padding 8, `#111`, radius 6, `.82rem`.
- `.task-info` flex 1, min-width 0.
- `.task-name` white bold, wraps anywhere.
- `.task-meta` muted `.75rem`, wraps anywhere.
- `.task-meta.lease` is `#8bb`; hidden when no lease text.
- `.task-meta.reason` is `#c96`; hidden when no reason.
- `.task-status` is `.7rem`, 2×6 padding, radius 3, 6 px horizontal margin, nowrap.

Status colors:

| Status | Background | Text |
|---|---|---|
| `ACTIVE` | `#1a3a1a` | `#4cff8a` |
| `PENDING` | `#2a2a1a` | `#ffcc44` |
| `COMPLETE` | `#1a1a3a` | `#7b7bff` |
| `FAILED` | `#3a1a1a` | `#ff4c4c` |
| `BLOCKED` | `#3a2a1a` | `#ffaa44` |
| `CANCELLED` | `#2a2a2a` | `#999` |
| unknown | `#2a2a2a` | `#aaa` |

Lease line building order:

1. `attempt N` if `attempt` is present and not null.
2. `reason <statusReason>` if `statusReason` is non-empty.
3. `lease <leaseOwner>` if present.
4. `expires <locale time>` if `leaseExpiresAt` is truthy; otherwise `last <lastOwner>` if present.
5. `heartbeat Ns ago` if `heartbeatAgeMs` is truthy.

Fields are joined with ` | `. The line is hidden when no fields are present. The UI does not display `leaseActive` or `leaseExpiresInMs` directly.

Reason line: `task.reason` (the server’s `failureReason`) is shown in `.task-meta.reason`; hidden if empty.

Remove button: `✕`, class danger, title `Remove task <id> (/api/task/remove)`; click posts `{id}` to `/api/task/remove`. There is no confirmation. This removes the row from the host queue; it does **not** send a worker cancel.

#### Add-task form

```html
<input id="taskItem" placeholder="item (e.g. cobblestone)" type="text" list="itemOptions" autocomplete="off">
<input id="taskQty" placeholder="qty" type="number" value="64">
<select id="taskType">
  <option value="AUTO">Auto</option>
  <option value="CRAFT">Craft</option>
  <option value="GATHER">Gather</option>
  <option value="HUNT_FOOD">Hunt food</option>
  <option value="GRIND">Grind</option>
  <option value="SMITH">Smith</option>
  <option value="BUILD">Build</option>
</select>
<select id="taskBot"><option value="any">Any bot</option></select>
<button class="success" id="addTaskButton" title="Queue a task (/api/task/add)" type="button">+ Add</button>
```

Desktop grid columns: item 1fr, qty 64 px, type 110 px, bot 120 px, button auto. All controls 6 px gap, 10 px top margin.

`#itemOptions` is a `<datalist>` filled on first focus of `#taskItem` from `GET /api/items`. The request is made once (`itemsRequested`). If it fails, plain text entry still works.

The bot select is populated from current bots:

- `Any bot` value `any`.
- Each worker option value is `nickname || username`, label the same nickname.
- The command target select is different: value `username`, label nickname.

Client-side add validation:

- Read item trim.
- `qty = parseInt(value, 10) || 64` — empty or invalid becomes 64; zero becomes 64 because `0` is falsy.
- Type default `AUTO`.
- Bot value from select.
- `actionType` is true for `HUNT_FOOD` and `GRIND`.
- If item is empty and not an action type, show `Enter an item`, focus the item input, do not post.
- Payload `{item, qty, bot}`; add `type` only when type is not `AUTO`.
- On any successful HTTP response, clear the item input. The server may still reject/queue nothing; the client does not inspect the response body.

The Add form omits `SMELT`, `SHULKER`, `EQUIP`, `DEPOSIT`, and `SCAN_CHESTS` from the type selector even though some of those are valid server enum values. The UI labels are exactly as listed above.

Clear button posts `{}` to `/api/task/clear_done`. The label is `Clear Done/Failed`; the server also removes `CANCELLED`. There is no confirmation.

### 4.6 Log tab

Card structure:

```html
<h2>📜 Live Log</h2>
<div class="log-controls">
  <input class="log-filter" id="logFilter" placeholder="Filter logs...">
  <div class="log-chips" id="logChips" role="group" aria-label="Log filters">
    <button class="chip" data-chip="TASK">TASK</button>
    <button class="chip" data-chip="DEPOSIT">DEPOSIT</button>
    <button class="chip" data-chip="HALT">HALT</button>
    <button class="chip" data-chip="HUNT">HUNT</button>
    <button class="chip" data-chip="STAGE">STAGE</button>
    <button class="chip" data-chip="CHEST">CHEST</button>
    <button class="chip" data-chip="WARN">WARN</button>
    <button class="chip" data-chip="ERROR">ERROR</button>
  </div>
  <button id="copyLogButton" title="Copy the last 200 visible lines">Copy</button>
  <button id="clearLogsButton" title="Clear the visible log (no server request)">Clear</button>
  <label class="auto-scroll"><input type="checkbox" id="autoScroll" checked> Auto-scroll</label>
  <button id="jumpLatest" class="success" hidden>Jump to latest</button>
</div>
<div class="log-entries" id="logEntries"></div>
<div class="log-toast" id="logToast" role="status" hidden></div>
```

Controls:

- Filter input is case-insensitive substring match on `entry.msg` only; it does not search level or time.
- Chips are one-at-a-time. Clicking the active chip clears it. `WARN` and `ERROR` match `entry.level` exactly; all other chips match `entry.msg.toUpperCase().startsWith(chip)`.
- Filter changes rebuild the visible list from the in-memory log buffer.
- Copy takes the last 200 **visible** entries, formats each as `<time> [<LEVEL>] <msg>`, and tries `navigator.clipboard` when `window.isSecureContext`; otherwise it uses a hidden textarea and `document.execCommand('copy')`; if that fails, it selects the log contents and shows `Selected — press Ctrl+C`.
- Clear empties the local array and the DOM. It sends no request. Server history remains; a reload can bring it back.
- Auto-scroll is checked by default. Scroll handling pauses automatic scrolling when the user is more than 24 px from the bottom; `Jump to latest` becomes visible. Jumping scrolls to the bottom, sets `paused = false`, hides itself, and re-checks Auto-scroll.
- The toast shows `Copied N lines`, `No visible lines to copy`, or the selection fallback, then hides after 2.5 s.

Log viewport:

- height `56vh`, min 260, max 640;
- overflow-y auto;
- monospace `.75rem`;
- background `#080808`, border `#1a1a1a`, radius 4, padding 8;
- row is flex, gap 6, border-bottom `#111`;
- time `#444`; message `#ccc`, `pre-wrap`, wrap anywhere;
- INFO level `#777`; WARN `#ffaa44`; ERROR `#ff4c4c`;
- WARN/ERROR messages take the level color.

Buffer limits:

- Server stores 500 entries and sends them as `log_history`.
- Browser stores 2,000 entries (`MAX_LOGS`).
- The history frame replaces the browser array with the last 2,000 entries from the server history.

### 4.7 Actions tab

Quick Actions grid: 3 columns on desktop, 2 at `max-width: 700px`. Each button is min-height 34, 44 on mobile.

| Label | `data-action` | Tooltip/title | Endpoint | Host effect |
|---|---|---|---|---|
| `👥 Follow Me` | `follow` | `swarm:all follow player <hostName>` | `/api/action/follow` | `sendAll("swarm:all follow player " + hostName)` |
| `🛑 Stop All` | `cancel` | `swarm:all cancel` | `/api/action/cancel` | `sendAll("swarm:all cancel")` |
| `▶ Resume All` | `resume` | `swarm:all resume` | `/api/action/resume` | clear halted flags + `sendAll("swarm:all resume")` |
| `📦 Deposit All` | `deposit` | `swarm:all depositall` | `/api/action/deposit` | `sendAll("swarm:all depositall")` |
| `⛏ Dep. Ores` | `deposit_ores` | `swarm:all deposit ores` | `/api/action/deposit_ores` | `sendAll("swarm:all deposit ores")` |
| `📫 Dep. Shulkers` | `deposit_shulkers` | `swarm:all deposit shulkers` | `/api/action/deposit_shulkers` | `sendAll("swarm:all deposit shulkers")` |
| `🔍 Scan Chests (unavailable)` | none | disabled; container reader unavailable note | none | no request |
| `🔥 Check Fuel (unavailable)` | none | disabled; container reader unavailable note | none | no request |
| `⚔ PVP ON` | `pvp_on` | seven `exec .toggle ... on` messages | `/api/action/pvp_on` | toggles seven PvP modules on |
| `🕊 PVP OFF` | `pvp_off` | seven `exec .toggle ... off` messages | `/api/action/pvp_off` | toggles seven PvP modules off |

The PvP module list, in order, is:

```text
crystal-aura
kill-aura
auto-totem
surround
auto-trap
auto-web
auto-exp
```

The two disabled buttons have no `data-action`, so the generic click binder never attaches to them. They send no request. The server still has a live `/api/action/scan_chests` command path (see §7.5); the UI has simply not been re-enabled.

The Follow button title is initially `swarm:all follow player <your player name>`; when state arrives with `hostName`, the title is replaced with `swarm:all follow player <actual hostName>`.

#### Send Command composer

```html
<h2>💬 Send Command</h2>
<div class="cmd-row">
  <select id="cmdTarget"><option value="all">All</option></select>
  <input id="cmdInput" placeholder="command..." type="text" title="Type a command; Send wraps it in exec">
  <button id="sendButton" title="exec <command from the box>" type="button">Send</button>
</div>
```

- `cmdTarget` has `all` value `all`, then one option per bot with value `bot.username` and label `bot.nickname || bot.username`.
- The select is 110 px desktop / 84 px at 520px.
- `cmdInput` flexes to fill.
- Send button title updates on input:
  - empty: `exec <command from the box>`;
  - starts with `#`: `chat <command>`;
  - otherwise: `exec <command>`.
- Enter in the input sends. On mobile, focusing the input scrolls it into view after 180 ms.
- Send posts `{target, cmd}` to `/api/command`; on success clears the input.
- Empty command does nothing.
- No confirmation, no command history, no shell-style parsing.

### 4.8 Footer

```html
<footer class="footer">
  Enhanced Swarm <span class="meta" id="modVersion">version unknown</span>
  &nbsp;·&nbsp; built from <span class="meta" id="buildCommit">commit unknown</span>
</footer>
```

- centered, `#555`, `.72rem`, padding `10px 12px 18px`;
- `.meta` is accent;
- `modVersion` comes from state `modVersion`, fallback `version unknown`;
- `buildCommit` comes from state `buildCommit`, fallback `commit unknown`.

---

## 5. Client runtime contract

### 5.1 Boot sequence

1. Define `MAX_LOGS = 2000`, status/tier sets, `$` helper.
2. Read the meta token; `bootToken` is trimmed.
3. Read `?token=` from `location.search`; if present, remove `sessionStorage["swarmToken"]`.
4. `currentToken = urlToken || sessionStorage["swarmToken"] || bootToken`.
5. Initial state: empty bots/tasks, empty dimension/hostName/modVersion/buildCommit.
6. On DOM ready (or immediately if already ready), `bindPanel()`.
7. Bind tabs, add/clear, send/keys, log controls, token retry, action buttons, first item-list focus.
8. `activateTab(sessionStorage["swarm.tab"] || "overview")`.
9. `updateSendTitle()`, `setConnection("reconnecting")`, `connect()`.
10. Start a one-second interval that re-renders the status strip when `lastStateAt` is older than 5s.

### 5.2 Token lifecycle

- `authHeaders(extra)` adds `X-Swarm-Token` when `currentToken` is non-empty. `fetch` uses `credentials: "same-origin"` so the cookie is also sent.
- WebSocket URL: `ws(s)://<location.host>/ws?token=<encoded currentToken>`; scheme is `wss` only when the page is `https:`.
- `probeAuth()` fetches `/v2/app.js` with `cache: "no-store"`, same-origin credentials, and auth headers. It returns `false` only for HTTP 401; network errors return `true`.
- On WebSocket close:
  - if the rejected flag is set, show rejected and do not reconnect;
  - otherwise show reconnecting, probe auth;
  - 401 → `rejectToken()`;
  - otherwise `scheduleReconnect()`.
- `rejectToken()` is idempotent: remove stored token, set current token to empty, close socket, show rejected, reveal and focus the token strip.
- `retryToken()` trims the input, ignores empty, stores in `sessionStorage`, sets `currentToken`, clears the input, clears rejected, resets retry delay to 3s, hides the strip, and calls `connect()`.
- Reconnect delay starts at 3,000 ms and doubles to a maximum of 30,000 ms. A successful open resets it to 3,000 ms.
- Query token is not persisted; only a manual retry stores it.
- A token in the URL remains visible in browser history; the code does not rewrite the URL to remove it.

### 5.3 WebSocket messages

`onmessage` parses JSON; malformed JSON is logged to the browser console and ignored. Three message types matter:

- `state`: replace the whole state object, set `lastStateAt = Date.now()`, call `renderState()`.
- `log`: push to logs, trim to 2,000, append if it matches the current filter.
- `log_history`: replace the log array with the incoming array sliced to the last 2,000, then rebuild the visible log.

The client does not send WebSocket messages. The server technically accepts text frames and passes the raw message to `handleApiCommand`, but the browser never uses that path. All controls are HTTP POSTs.

### 5.4 State rendering

`renderState()`:

1. Normalize `bots` and `tasks` to arrays.
2. Count `online` bots; write to `#connectedCount`.
3. Write `modVersion`, `buildCommit`.
4. If `state.hostName`, update Follow title.
5. Render bots, tasks, bot selects, strip.

Bots are keyed by `username` in a `Map`. New bots create a card; existing cards update; unseen cards are removed. Tasks are keyed by `id` in the same way. This preserves focused inputs, scroll positions and DOM node identity across 2-second broadcasts.

`renderBotSelects()` computes a signature from each bot’s username and nickname. It skips rebuilding while either select is focused. Rebuilding preserves the previous values if the option still exists. `taskBot` options use nicknames as values; `cmdTarget` options use usernames as values.

### 5.5 Tab behavior

`activateTab(name)` accepts only one of `overview`, `tasks`, `log`, `actions`; anything else falls back to `overview`. It updates `.is-active`, `aria-selected`, and `hidden` on the four panels, then writes `swarm.tab` to sessionStorage.

### 5.6 HTTP helper behavior

`post(path, body)`:

- `POST`, same-origin credentials, `Content-Type: application/json`, auth headers;
- 401 → `rejectToken()`, return false;
- other non-OK → toast `Request failed: <status>`, return false;
- network throw → toast `Panel request failed`, return false;
- OK → true, no toast.

The UI never reads a JSON response body from a POST. A server `{ok:true}` is enough. A server that returns 200 while doing nothing still looks successful to the client.

### 5.7 What the client intentionally does not do

- The current static client uses no framework, router, state library, component kit, bundler, or npm. The React/JS path in §8.6B may use React and a build tool, but still no router, state library, or component kit unless explicitly requested.
- No `innerHTML` for server/player data.
- No autosave of task input.
- No confirmation dialogs for destructive actions.
- No polling of `/api/items` after the first focus; the list is cached for the page lifetime.
- No retry of a failed POST.
- No optimistic mutation of task status; it waits for the next state frame.
- No display of the raw `connected` state field.
- No display of `leaseActive` or `leaseExpiresInMs`.
- No per-bot pagination; every worker is rendered in one grid.

---

## 6. Live data contract

### 6.1 State frame

The server builds JSON by hand (not Gson) for state and tasks. A representative active frame:

```json
{
  "type": "state",
  "modVersion": "0.1.0+abc1234",
  "buildCommit": "abc1234",
  "hostName": "HostPlayer",
  "dimension": "minecraft:overworld",
  "connected": 2,
  "bots": [
    {
      "username": "Miner01",
      "nickname": "Miner",
      "online": true,
      "health": 19.5,
      "food": 17,
      "x": -549,
      "y": 91,
      "z": 598,
      "task": "Gathering cobblestone",
      "progress": "23/64",
      "eta": "2m",
      "tier": "Iron",
      "state": "GATHERING",
      "halted": false
    },
    {
      "username": "Wood02",
      "nickname": "Wood02",
      "online": false,
      "health": 0,
      "food": 0,
      "x": 0,
      "y": 0,
      "z": 0,
      "task": "Offline",
      "progress": "",
      "eta": "",
      "tier": "Wood",
      "state": "OFFLINE"
    }
  ],
  "tasks": [
    {
      "id": "d617ac41-0000-0000-0000-000000000000",
      "type": "GATHER",
      "item": "cobblestone",
      "quantity": 64,
      "progress": "23/64",
      "status": "ACTIVE",
      "reason": "",
      "bot": "Miner01",
      "attempt": 2,
      "statusReason": "leased",
      "lastOwner": "",
      "leaseOwner": "Miner01",
      "leaseExpiresAt": 1760000000000,
      "leaseExpiresInMs": 24000,
      "lastHeartbeatAt": 1760000000000,
      "heartbeatAgeMs": 500,
      "leaseActive": true
    }
  ]
}
```

Top-level fields:

| Field | Type | Meaning |
|---|---|---|
| `type` | string | Always `state`. |
| `modVersion` | string | `version` from `/build-info.properties`, else `unknown`. |
| `buildCommit` | string | `commit` from `/build-info.properties`, else `unknown`. |
| `hostName` | string | Minecraft client user name, blank if unavailable. |
| `dimension` | string | Host’s current dimension, blank if unavailable. |
| `connected` | number | Host connection count. The UI mostly uses bot `online` count instead. |
| `bots` | array | One object per worker name, including offline names known to the host. |
| `tasks` | array | All queue rows (`getAllTasks()`), not only pending/active. |

Bot fields:

| Field | Type | Notes |
|---|---|---|
| `username` | string | Worker name; card map key. |
| `nickname` | string | Task queue nickname, or username. |
| `online` | boolean | `BotStatus.isOnline()`: last update under 5 seconds. |
| `health` | number | One decimal place. |
| `food` | number | Integer. |
| `x`,`y`,`z` | number | Server casts double coordinates to integers. |
| `task` | string | Task description or `Idle`. Offline branch uses `Offline`. |
| `progress` | string | `collected/total`. |
| `eta` | string | Free text; may be empty. |
| `tier` | string | Free text; server fallback `Wood`. |
| `state` | string | Free text; server fallback `IDLE`; offline `OFFLINE`. |
| `halted` | boolean | Present for online/status objects; **absent** in the offline fallback object. |

Task fields:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID string | Queue identity; row key. |
| `type` | enum string | `GATHER`, `CRAFT`, `SMELT`, `EQUIP`, `DEPOSIT`, `SCAN_CHESTS`, `HUNT_FOOD`, `GRIND`, `SMITH`, `SHULKER`, `BUILD`. |
| `item` | string | Item **path** (no namespace) from `getItemName()`; can be empty for itemless/action tasks. |
| `quantity` | number | Target quantity. |
| `progress` | string | `collected/target`. |
| `status` | enum string | `PENDING`, `ACTIVE`, `BLOCKED`, `COMPLETE`, `FAILED`, `CANCELLED`. |
| `reason` | string | Failure reason, or empty. |
| `bot` | string | Assigned bot, or `any`. |
| `attempt` | number | Incremented on claim/requeue/recovery. |
| `statusReason` | string | Lease/lifecycle reason, e.g. `leased`, `cancelled`, `lease-expired`. |
| `lastOwner` | string | Previous owner, or empty. |
| `leaseOwner` | string | Assigned bot, or empty. |
| `leaseExpiresAt` | number | Epoch ms; 0 when no lease. |
| `leaseExpiresInMs` | number | Derived max(0, expiry-now); 0 if no expiry. |
| `lastHeartbeatAt` | number | Epoch ms; 0 if none. |
| `heartbeatAgeMs` | number | Derived max(0, now-heartbeat); 0 if none. |
| `leaseActive` | boolean | `status == ACTIVE && leaseExpiresAt > now`. |

The server does **not** expose the fencing token (`leaseToken`). `buildTaskJson` intentionally omits it.

### 6.2 Empty/inactive state

When the module is absent, not host, or has no host:

```json
{
  "type": "state",
  "modVersion": "unknown",
  "buildCommit": "unknown",
  "hostName": "",
  "dimension": "",
  "connected": 0,
  "bots": [],
  "tasks": []
}
```

The panel still renders; it shows “No bots connected”, “No tasks”, and the strip’s no-bot defaults.

### 6.3 Log frames

Live:

```json
{"type":"log","time":"14:03:22","level":"INFO","msg":"Task added: cobblestone x64 → any"}
```

History:

```json
{"type":"log_history","logs":[{"type":"log","time":"14:03:22","level":"INFO","msg":"..."}]}
```

`time` is `HH:mm:ss` server-local time. Levels used by the server are `INFO`, `WARN`, and `ERROR`. The server’s ring holds 500; the browser caps at 2,000.

### 6.4 Item list

`GET /api/items`:

```json
{"items":["minecraft:acacia_boat","minecraft:air","..."]}
```

- Built once from `BuiltInRegistries.ITEM` and cached in `itemsJson`.
- Sorted lexicographically.
- Includes `minecraft:air`; the Add form does not filter it.
- No pagination, no filtering, no metadata.

### 6.5 Escaping and malformed data

- `buildStateJson` uses a hand-rolled `esc()` that escapes backslash, double quote, and newline only. Carriage returns, tabs, and other control characters are not escaped. A hostile nickname could therefore break JSON parsing in the browser.
- Log entries use Gson, so they are valid JSON for normal strings.
- The client catches `JSON.parse` errors and logs `Bad panel message` to the console; it does not show a toast.
- If a state frame is malformed, the panel keeps the previous state and the strip can go stale.

---

## 7. HTTP and WebSocket endpoint reference

### 7.1 Request pipeline

`SwarmWebHandler.handleHttp` runs this exact order:

```text
1. token check                         -> 401 text/plain "Unauthorized"
2. Origin check                        -> 403 text/plain "Origin not allowed"
3. strip query string from URI
4. URI == /api/action/check_fuel       -> 501 JSON error
5. URI == /ws                          -> WebSocket upgrade
6. method == POST                      -> handleApiCommand(uri, body); 200 {"ok":true}
7. method == GET and URI == /          -> legacy SwarmWebPanel HTML
8. method == GET and URI == /app.js    -> legacy inline script
9. method == GET and URI == /v2 or /v2/ -> v2 HTML
10. method == GET and URI == /v2/app.css -> CSS
11. method == GET and URI == /v2/app.js  -> JS
12. method == GET and URI == /api/items  -> item JSON
13. everything else                    -> 404 text/plain "Not found"
```

Important consequences:

- `/api/action/check_fuel` is special-cased before the POST branch, so it returns 501 for GET, POST, or any method.
- Any POST to an authenticated, same-origin path is routed through `handleApiCommand` and then receives 200 `{"ok":true}`. Unknown paths do not 404 on POST.
- POST `/api/items` returns the generic `{"ok":true}`, not the item list.
- GET `/api/task/add` is 404; only POST is accepted.
- OPTIONS and HEAD are not implemented; after auth they fall through to 404.
- There are no CORS headers, so a cross-origin preflight fails at auth/origin or 404.
- Query strings are stripped only for route matching; `?token=` is still used by auth.
- Every response closes the connection.

### 7.2 Route inventory

| Method | Path | Auth | Success body | Purpose |
|---|---|---|---|---|
| GET | `/` | yes | HTML | Legacy v1 panel, inline Java HTML. |
| GET | `/app.js` | yes | JS | Legacy v1 inline script. |
| GET | `/v2` or `/v2/` | yes | HTML | Current panel; token substituted into meta. |
| GET | `/v2/app.css` | yes | CSS | Current stylesheet. |
| GET | `/v2/app.js` | yes | JS | Current client. |
| GET | `/api/items` | yes | JSON | Cached registry item list. |
| GET | `/ws` | yes | 101 / WS frames | WebSocket upgrade. |
| GET/POST | `/api/action/check_fuel` | yes | 501 JSON | Explicitly unavailable. |
| POST | `/api/task/add` | yes | `{ok:true}` | Queue a task and try to dispatch. |
| POST | `/api/task/clear_done` | yes | `{ok:true}` | Remove terminal rows. |
| POST | `/api/task/remove` | yes | `{ok:true}` | Remove one row by id, no worker notification. |
| POST | `/api/task/pause` | yes | `{ok:true}` | Send `task pause` to one worker. |
| POST | `/api/task/resume` | yes | `{ok:true}` | Clear local halt, send resume, dispatch. |
| POST | `/api/task/cancel` | yes | `{ok:true}` | Cancel by id or send cancel to a bot. |
| POST | `/api/action/follow` | yes | `{ok:true}` | Tell all workers to follow the host player. |
| POST | `/api/action/cancel` | yes | `{ok:true}` | Tell all workers to cancel. |
| POST | `/api/action/stop` | yes | `{ok:true}` | Alias of cancel. |
| POST | `/api/action/resume` | yes | `{ok:true}` | Clear all halt flags and resume all. |
| POST | `/api/action/deposit` | yes | `{ok:true}` | `depositall` to all. |
| POST | `/api/action/deposit_ores` | yes | `{ok:true}` | `deposit ores` to all. |
| POST | `/api/action/deposit_shulkers` | yes | `{ok:true}` | `deposit shulkers` to all. |
| POST | `/api/action/pvp_on` | yes | `{ok:true}` | Toggle seven PvP modules on. |
| POST | `/api/action/pvp_off` | yes | `{ok:true}` | Toggle seven PvP modules off. |
| POST | `/api/action/scan_chests` | yes | `{ok:true}` | Send a storage scan verb to all workers. |
| POST | `/api/command` | yes | `{ok:true}` | Send an exec command to one/all workers. |
| POST | `/api/nickname` | yes | `{ok:true}` | Persist a worker nickname. |
| POST | anything else | yes | `{ok:true}` | No switch case; no effect except a state broadcast. |
| other | anything else | yes | 404 text | Not found. |

### 7.3 Common POST behavior

All command paths go through `SwarmWebServer.handleApiCommand(path, body)`:

- If the `EnhancedSwarm` module is missing, not host, or has no host, the method returns immediately. The HTTP response is still 200 `{"ok":true}`.
- `extractJson` parses a top-level JSON object and returns `value.getAsString()` for the requested key. Malformed JSON, a missing key, null, or a non-object gives `""`.
- There is no content-type check, schema validation beyond individual cases, rate limit, or CSRF token beyond the shared token and same-origin rules.
- Exceptions inside a case are caught and logged as `API error on <path>: <message>`; the HTTP response is still 200.
- After the switch, `broadcastState()` runs, even for an unknown path, so every accepted POST can refresh the panel.
- A command is dispatched to the host’s worker connection; it is not executed by the browser, and HTTP success does not prove worker execution.

### 7.4 Task endpoints

#### `POST /api/task/add`

Request fields:

| Field | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `item` | string | conditional | `""` | Registry id or path. Empty/`none` means itemless. |
| `qty` | number/string | no | 64 | Parsed as int; invalid/missing becomes 64. No positive/upper bound. |
| `bot` | string | no | `any` | Worker username or nickname; `any`/empty means unassigned. |
| `type` | string | no | `AUTO` | `BotTask.Type` value or `AUTO`; explicit value must be dispatchable. |

Processing order:

1. Parse `item`, `qty`, `bot`, `type`.
2. If item is non-empty and not `none`, resolve it as `Identifier` (adds `minecraft:` when no namespace) and look it up in `BuiltInRegistries.ITEM`.
3. Unknown/air item: log `unknown item '<item>', not queued`; return. No row is added. HTTP is still 200.
4. Resolve type:
   - missing/empty/`auto`: `RecipeBook.hasRecipe(mcItem)` ? `CRAFT` : `GATHER`.
   - explicit: `BotTask.Type.valueOf(type.toUpperCase(Locale.ROOT))`; invalid enum logs `unknown task type '<type>', not queued` and returns.
   - explicit `EQUIP`, `DEPOSIT`, `SCAN_CHESTS`: `isDispatchable()` is false; log `task type <TYPE> has no handler, not queued`; return. These three are the only non-dispatchable enum values.
5. If the task is `GATHER` and the requested item is a smelt output, convert it to a `SMELT` task:
   - `RecipeBook.getOreForIngot(mcItem)` for ingots;
   - otherwise a reverse lookup in `FoodManager.COOKING` for cooked food;
   - task data: `{item: mcItem, quantity: qty, source: smeltSource}`.
6. If item is non-empty and `DependencyResolver.hasKnownSource(mcItem)` is false, create the task, set status `FAILED`, reason `no known source`, add it to the queue, log `TASK: <id> failed — no known source`, and return. This is a failed row, not “nothing queued.”
7. Add the task to the queue (`addTask`, which persists).
8. Resolve `bot` against the nickname map: if a nickname matches, use its username; otherwise keep the requested string. `any` becomes null for dispatch.
9. Call `host.dispatchNextTask(...)` with the resolved bot, or the first worker name if unassigned; this is best-effort and does not guarantee execution.
10. Log `Task added: <item|TYPE> x<qty> → <bot|any>`.

UI behavior:

- The form posts `{item, qty, bot}` and adds `type` only for non-AUTO.
- It clears the item field on any successful HTTP response, including the “no known source” failed-row path and the unknown-item no-op path.
- It does not display a server error toast for those semantic failures; only HTTP/network failures produce toasts.
- The form’s type list is `AUTO`, `CRAFT`, `GATHER`, `HUNT_FOOD`, `GRIND`, `SMITH`, `BUILD`. It does not offer `SMELT`, `SHULKER`, `EQUIP`, `DEPOSIT`, or `SCAN_CHESTS`.
- `HUNT_FOOD` and `GRIND` are the two itemless types accepted by the client; the server also accepts itemless explicit `SMELT`/`SHULKER`/`BUILD`/`SMITH`/`CRAFT`/`GATHER` if called directly, subject to the itemless checks.

#### `POST /api/task/clear_done`

Body: `{}`. Calls `TaskQueue.clearCompleted()`, which removes every row whose status is `COMPLETE`, `FAILED`, or `CANCELLED`, persists, and logs `Cleared completed tasks`. The UI label says “Clear Done/Failed” and does not mention cancelled rows.

#### `POST /api/task/remove`

Body: `{"id":"<uuid>"}`. Parses the UUID, calls `TaskQueue.removeById`, persists, and logs `Task removed: <id>`. Invalid UUID logs `Invalid task id: <id>`. It does **not** send a worker cancel. The UI row’s `✕` uses this endpoint.

#### `POST /api/task/pause`

Body: `{"bot":"<username>"}`. Sends `swarm:<bot> task pause` to that worker and logs `Paused: <bot>`. There is no local status mutation and no panel button for it.

#### `POST /api/task/resume`

Body: `{"bot":"<username>"}`. If a `BotStatus` exists, sets `halted = false` locally; sends `swarm:<bot> resume`; calls `dispatchNextTask(bot)`; logs `resume received for <bot>`. The local flag flips before worker confirmation.

#### `POST /api/task/cancel`

Body may be:

```json
{"id":"<uuid>"}
{"bot":"<username>"}
{"id":"<uuid>","bot":"<username>"}
```

Behavior:

- If `id` is present and non-blank: parse UUID; `TaskQueue.cancelById` sets `CANCELLED`, `statusReason=cancelled`, clears lease fields, removes the row, persists, emits `TASK: cancelled <id> [owner=<bot>]`, and returns the owner. The owner becomes the target if non-blank.
- Invalid id logs `Invalid task id: <id>`.
- If the resulting target is non-blank: send `swarm:<target> cancel`; log `cancel sent to <target>`.
- If no id is supplied, this endpoint only sends the worker cancel; it does not mutate/remove a queue row. The per-bot Stop button uses this form.

This distinction is important: the card Stop control does not remove the task row, while the row `✕` removes it without stopping the worker.

### 7.5 Action endpoints

All of these take `{}` from the UI. The effect is host-side dispatch; no worker acknowledgement is returned in the HTTP response.

#### `POST /api/action/follow`

Reads the Minecraft client user name (`Minecraft.getInstance().getUser().getName()`), sends `swarm:all follow player <name>` to all workers, logs `Follow me sent to all bots`. If the name is unavailable it becomes empty; the message still goes out.

#### `POST /api/action/cancel` and `POST /api/action/stop`

Both send `swarm:all cancel` and log `cancel received — sent to all bots`. `stop` is only an alias; the UI uses `cancel`.

#### `POST /api/action/resume`

`host.clearHaltedFlags()`, then `swarm:all resume`, log `resume received — sent to all bots`.

#### `POST /api/action/deposit`

`swarm:all depositall`, log `Deposit all sent to all bots`.

#### `POST /api/action/deposit_ores`

`swarm:all deposit ores`, log `Deposit ores sent to all bots`.

#### `POST /api/action/deposit_shulkers`

`swarm:all deposit shulkers`, log `Deposit shulkers sent to all bots`.

#### `POST /api/action/pvp_on`

For each module in the exact list below, sends `swarm:all exec .toggle <module> on`; then logs `PVP mode ENABLED`.

```text
crystal-aura
kill-aura
auto-totem
surround
auto-trap
auto-web
auto-exp
```

#### `POST /api/action/pvp_off`

The same seven modules with `off`, then `PVP mode DISABLED`.

#### `POST /api/action/scan_chests`

Current server behavior:

```java
host.sendAll("swarm:all scan");
logInfo("Scan Chests: requested a storage scan from all workers");
```

Despite the UI label and the old docs, this endpoint does send a real worker scan verb. The `/v2` button is disabled and has no `data-action`, so the panel itself never calls it. This is a deliberate API/UI mismatch in the current tree.

#### `POST /api/action/check_fuel`

Handled before the POST switch in `SwarmWebHandler`:

```http
HTTP/1.1 501 Not Implemented
Content-Type: application/json

{"ok":false,"error":"check_fuel unavailable: container reader not implemented"}
```

There is no fuel-state query in the panel. The button is disabled and sends nothing.

### 7.6 Command and nickname endpoints

#### `POST /api/command`

Request:

```json
{"target":"all","cmd":"say hello"}
```

Behavior:

- Target `all` (case-insensitive): iterate every worker name and send `swarm:<worker> exec <cmd>`.
- Any other target: send `swarm:<target> exec <cmd>` once.
- The command is always wrapped in `exec`; a leading `#` is preserved in the string. The UI’s Send title says `chat <cmd>` when the input starts with `#`, but the server does not strip/replace it; worker-side handling decides what `#` means.
- Logs `Command sent to <target>: <cmd>`.
- Empty target sends to the empty name; empty command sends `exec `. No validation.

#### `POST /api/nickname`

Request:

```json
{"username":"Miner01","nickname":"Miner"}
```

Calls `TaskQueue.setNickname`, which stores the mapping, persists `task-queue.json`, and logs `Nickname set: <username> → <nickname>`. No length, character, duplicate, or HTML validation. The UI sends on blur; the server itself does not know the browser input state.

### 7.7 Item endpoint

#### `GET /api/items`

Response:

```json
{"items":["minecraft:acacia_boat","minecraft:air","..."]}
```

- JSON `application/json`.
- Registry ids are sorted and cached for the server lifetime.
- Includes `minecraft:air`.
- The panel requests it once, on first focus of the item field.
- No query parameters, search, pagination, or counts.

### 7.8 WebSocket `/ws`

Upgrade requirements:

- Token must pass `isAuthorized`.
- If an `Origin` header is present, it must pass `isOriginAllowed`.
- The server creates a handshaker with URL `ws://<Host>/ws`, no subprotocol, and `allowExtensions=true`.
- On successful handshake, `addClient` sends state then log history.

Frames:

| Direction | Type | Behavior |
|---|---|---|
| server → browser | Text | State, log, or log_history JSON. |
| browser → server | Text | `handleApiCommand(msg, msg)`; the entire text is used as the path. The UI never sends this. |
| browser → server | Close | Closes and removes the client. |
| browser → server | Ping | Server sends a Pong with the same payload. |
| server → browser | Binary | Not produced. Incoming binary is ignored by this handler. |

Multiple WebSocket clients may connect. `broadcast` writes to all in the channel group. A client that disconnects is removed on close or channel inactivity.

Because a browser text frame is passed as both path and body, only a literal frame such as `/api/task/clear_done` would match a switch case (with body equal to the path, which is not valid JSON); it is not a usable control channel. HTTP POST is the real command path.

### 7.9 Status/error matrix

| Condition | Status | Body |
|---|---|---|
| Missing/wrong token | 401 | `text/plain` `Unauthorized` |
| Foreign `Origin` | 403 | `text/plain` `Origin not allowed` |
| Unknown GET | 404 | `text/plain` `Not found` |
| `/v2` with missing HTML/CSS/JS | 500 | `text/plain` `v2 assets missing` |
| `/v2/app.css` or `/v2/app.js` empty asset | 404 | `text/plain` `Not found` |
| `/api/action/check_fuel` | 501 | JSON error shown above |
| Any accepted POST, including semantic failures | 200 | `application/json` `{"ok":true}` |
| Successful GET HTML/CSS/JS/items | 200 | Asset/HTML/JSON |
| WebSocket upgrade | 101 | Frames |

No endpoint returns a structured error for an unknown POST, a missing host, an unknown item, an invalid task type, an invalid UUID, or a failed worker command. The log is the only diagnostic.

### 7.10 Direct request examples

Use a token from the host log; never commit a real token.

```bash
TOKEN='paste-token'
BASE='http://127.0.0.1:8888'

curl -sS -H "X-Swarm-Token: $TOKEN" "$BASE/api/items"

curl -sS -X POST "$BASE/api/task/add" \
  -H "X-Swarm-Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"item":"cobblestone","qty":64,"bot":"any","type":"GATHER"}'

curl -sS -X POST "$BASE/api/action/scan_chests" \
  -H "X-Swarm-Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}'
```

The token can also be supplied as `?token=...` or as the `swarm_token` cookie. A browser request with `Origin: http://127.0.0.1:8888` is accepted; `Origin: https://127.0.0.1:8888` is not.

---

## 8. Recreating the server contract

### 8.1 Minimum implementation order

1. Serve the three static assets from `/v2`, `/v2/app.css`, `/v2/app.js`.
2. Replace `__SWARM_TOKEN__` in the HTML meta tag.
3. Implement token auth from header/query/cookie and the exact `http://<Host>` origin check.
4. Implement `GET /api/items`.
5. Implement a WebSocket endpoint that immediately sends `state` and `log_history`, then a state every 2 seconds and logs as they occur.
6. Implement every POST route in §7.4–§7.6 against a mock host adapter.
7. Reproduce the response headers, cookie, CSP, and status codes.
8. Only then wire it to a real host.

A mock host can supply the state fixture in §6.1 and accept the dispatch strings without Minecraft. That is enough for a full visual/UI recreation and endpoint contract test.

### 8.2 Pseudocode for the HTTP handler

```text
handleHttp(request):
  if !authorized(request): 401 Unauthorized
  if !originAllowed(request): 403 Origin not allowed
  path = request.uri.split("?")[0]
  if path == "/api/action/check_fuel": 501 JSON
  if path == "/ws": upgrade and addClient
  if request.method == POST:
      handleApiCommand(path, utf8(body))
      return 200 {"ok":true}
  if GET /: legacyHtml
  if GET /app.js: legacyJs
  if GET /v2 or /v2/: v2Html(token)
  if GET /v2/app.css: css
  if GET /v2/app.js: js
  if GET /api/items: {"items": cachedIds}
  return 404 Not found
```

Every response must add the three security headers and close the connection. HTML additionally adds the cookie and CSP.

### 8.3 Pseudocode for task add

```text
item = body.item or ""
qty = parseInt(body.qty) or 64
bot = body.bot or "any"
type = body.type or "AUTO"
if item is not empty and item != "none":
    mcItem = registry lookup(item)
    if mcItem is null or air: log warning; return
if type is AUTO:
    taskType = hasRecipe(mcItem) ? CRAFT : GATHER
else:
    taskType = enum(type)
    if !dispatchable(taskType): log warning; return
if taskType == GATHER and target is a smelt output:
    task = SMELT with {item: mcItem, quantity: qty, source: sourceItem}
else:
    task = new BotTask(taskType, mcItem, qty)
if item not empty and !hasKnownSource(mcItem):
    task.status = FAILED
    task.reason = "no known source"
    queue.add(task)
    log failure
    return
queue.add(task)
dispatch best effort to resolved bot
log "Task added"
```

### 8.4 State builder

Use the exact field names in §6.1. The current server hand-builds JSON; a recreation may use a real serializer as long as the wire shape and types match.

- `modVersion` and `buildCommit` come from `build-info.properties`.
- `hostName` and `dimension` are host-side values; blank if unavailable.
- `connected` is the host connection count.
- Bots come from the host’s known worker names; offline workers are included with the offline fallback shape.
- Tasks come from all queue rows; do not sort or filter unless you deliberately change behavior.
- Do not expose a task’s lease/fencing token.
- Send the offline bot object without `halted`, matching the current server.

### 8.5 Broadcast and logs

- State broadcast interval: 2,000 ms.
- Send state only when clients exist.
- On new client: state, then history.
- Log ring: 500 entries.
- Log entry: `type`, `time` (`HH:mm:ss`), `level`, `msg`.
- Any accepted POST triggers one immediate state broadcast.
- Browser log ring: 2,000 entries.

### 8.6 Recreating the page: vanilla or React/JS

There are two valid implementations. The contract is the same; only the implementation differs.

**A. Vanilla static (current shipped form).** Use `index.html` as the DOM contract and `app.css` as the visual contract. If the source files are available, ship them unchanged except for the token replacement. If not, preserve:

- the four-tab layout and exact labels;
- all IDs from §4 and the CSS classes used by `app.js`;
- the exact color tokens and breakpoints;
- the static copy, emoji, titles, placeholders, and aria attributes;
- the no-innerHTML rule;
- the 2-second state / immediate log model.

**B. React + JavaScript rebuild (supported).** Implement the page as React components in JavaScript; Vite or an equivalent toolchain is fine. The result must match:

- the same four tabs and labels, visible copy, emoji, colors, breakpoints, and empty/stale states;
- the same DOM hooks (`id`, `data-tab`, CSS classes) or functional equivalents that preserve styling and tests;
- the same state/WebSocket contract, token/auth rules, endpoints, and documented quirks;
- no router, state library, component kit, or external UI asset set unless explicitly requested; it is a port, not a redesign;
- React's normal escaping instead of `innerHTML`; do not use `dangerouslySetInnerHTML` for server/player data.

If the React build ships inside the addon, commit the built output into `web/v2/`, keep `gradlew build` free of Node, and keep the same classpath/static-serving path and token substitution. The server needs no new API. A standalone dev server is fine for development, but production must keep the same same-origin asset/API/WebSocket contract.

---

## 9. Fidelity traps and known gaps

These are the behaviors most likely to be “fixed” by a reimplementation and thereby stop matching the current panel.

1. **No visible HP/food bars.** The JS creates `.hp-bar`, `.hp-fill`, `.food-fill`; the v2 CSS has no rules for them. They are zero-height and invisible. The text values remain. This is derived from CSS, not a screenshot.
2. **Scan Chests is a live endpoint but a disabled button.** `/api/action/scan_chests` sends `swarm:all scan`; the UI button has no `data-action` and sends nothing. `docs/FEATURES.md` now records this split behavior; do not re-label the endpoint as a no-op.
3. **Check Fuel is a 501 and a disabled button.** `/api/action/check_fuel` is special-cased at the HTTP layer, not handled by the API switch.
4. **Every accepted POST returns 200.** Unknown paths, unknown items, invalid task types, invalid UUIDs, no host, and worker non-response all look successful at the HTTP layer. The UI only toasts HTTP/network failures.
5. **Unknown item does not add a row.** “No known source” does add a `FAILED` row with reason `no known source`. These are different failure paths.
6. **Task add has no client/server quantity validation.** `0` from the UI becomes 64 because `parseInt(...) || 64`; direct API can send 0 or negative values.
7. **Clear Done/Failed also clears CANCELLED.** The label is shorter than the behavior.
8. **Row ✕ removes; card Stop cancels.** Remove does not tell a worker. Cancel by bot does not remove the row. The UI has no id-based cancel control, though the API accepts one.
9. **Stop/Resume toggle is based on `bot.halted`.** Offline bot JSON usually omits `halted`, so offline cards show Stop.
10. **Bot selects and command selects use different values.** Task bot select values are nicknames; command target values are usernames. A nickname can contain characters that are not valid worker names; the server resolves it against the nickname map.
11. **Header online count is not `state.connected`.** The header counts `bot.online`; the server’s `connected` field is host connection count and is otherwise unused by the UI.
12. **The status strip uses only the first online bot.** It does not rotate, list all bots, or show per-bot dimensions.
13. **Staleness only repaints the strip.** After 5 seconds without a state, the strip says `unknown (stale ...)`; bot cards and task rows keep their last values.
14. **Logs are local-clear only.** Clearing the log sends no request; a reload can restore server history. Copy takes the last 200 visible lines, not 200 total.
15. **Log chips are exclusive and prefix-based.** `TASK`, `DEPOSIT`, etc. match message prefixes; `WARN`/`ERROR` match the level. The text filter only searches the message.
16. **Token precedence and storage.** URL token clears stored token; otherwise sessionStorage, then meta. A successful retry stores the token in sessionStorage only.
17. **HTTP-only origin rule.** `Origin` must be exactly `http://<Host>`; `https://` and a proxied TLS terminator fail. The cookie has no `Secure`.
18. **No CORS.** Cross-origin panel/browser access is not supported. There is no preflight route.
19. **WebSocket browser text frames are not a command channel.** The server passes the whole text as both path and body; only HTTP POST is used by the UI.
20. **The state JSON is hand-escaped.** `esc()` handles backslash, quote and newline only; carriage returns/control characters in nicknames can break parsing. Logs use Gson.
21. **The item list includes air and is cached forever.** It is built once; no invalidation when the registry changes (normally static during a session).
22. **No destructive-action confirmation.** Stop All, Deposit All, PvP, clear and remove all fire immediately.
23. **No optimistic state.** After a POST, the UI waits for the immediate state broadcast or the 2-second tick.
24. **The page has no favicon or manifest.** No `/favicon.ico` route; it is 404 (after auth).
25. **`/` is not `/v2`.** The old panel is a separate inline page with its own script; do not route `/` to the new page.

---

## 10. Acceptance checklist for a 1:1 recreation

### Visual

- [ ] Desktop at 1,440–1,500 px: 190 px rail, 12 px gaps/padding, 1,500 px max shell.
- [ ] Status strip above header with the exact field order and colors.
- [ ] Header title, `v2` tag, connection dot, status text and online count.
- [ ] Four tabs with exact labels; Overview is the default; active tab persists in sessionStorage.
- [ ] Worker grid auto-fits at 280 px; cards have the green/red left border and exact tier/status colors.
- [ ] Worker cards show name, tier, nickname input, stats, task, progress/ETA, coords, Stop/Resume.
- [ ] HP/food bar elements exist in the DOM but are invisible because the v2 CSS has no rules for them.
- [ ] Task rows show `item × qty`, `progress | bot`, hidden lease/reason lines, status badge, ✕.
- [ ] Add form grid, default quantity 64, exact labels/options, item datalist.
- [ ] Log controls, chips, viewport height, row colors, toast, jump button.
- [ ] Actions grid with exact labels and two disabled buttons; command composer.
- [ ] Footer version/commit line.
- [ ] Mobile at 899/700/520: fixed bottom tabs, sticky status, static header, sticky composer, two-column actions, hidden narrow fields.
- [ ] No light theme, external icon set, image, or font.

### React/JS path (if rebuilding)

- [ ] The React + JavaScript build matches the visible UI, tab behavior, state rendering, logs, and action semantics in this document.
- [ ] It uses the same token/auth/origin rules and the same HTTP/WebSocket endpoints and data shapes.
- [ ] The build output is committed into `web/v2/`; `gradlew build` never invokes Node.
- [ ] No router, state library, component kit, or external UI asset set is added without an explicit request.
- [ ] Server/player strings are never injected with `dangerouslySetInnerHTML`.

### Data and protocol

- [ ] `GET /v2` replaces the meta token and sets the cookie/CSP.
- [ ] Missing token is 401; foreign `Origin` is 403.
- [ ] Unknown GET is 404; `/api/action/check_fuel` is 501; accepted POST is 200 `{"ok":true}`.
- [ ] `/ws` sends state then history on connect; state every 2 s; logs immediately.
- [ ] State fields and types match §6.1; offline bots omit `halted`.
- [ ] Task JSON omits lease tokens and includes all diagnostic fields.
- [ ] `/api/items` is cached, sorted, and includes air.
- [ ] Every path in §7.2 exists with the documented effect.
- [ ] `/api/task/add` handles AUTO, explicit types, smelt conversion, unknown item, and no-known-source FAILED row.
- [ ] `/api/task/cancel` distinguishes id cancellation (removes row) from bot cancellation (sends only).
- [ ] `/api/action/scan_chests` sends the scan verb even though the UI button is disabled.
- [ ] `/api/command` sends per-worker `exec` messages for `all`.

### Security

- [ ] Token required on every HTTP and WS request.
- [ ] `MessageDigest.isEqual`-equivalent comparison.
- [ ] Exact `http://<Host>` origin rule.
- [ ] Cookie: `Path=/; HttpOnly; SameSite=Strict`; no `Secure` in the current contract.
- [ ] CSP and the three no-store/no-sniff/no-referrer headers.
- [ ] No CORS headers.
- [ ] No server/player text reaches `innerHTML`.
- [ ] Token is not logged by the panel JS; server chat/URL behavior is documented.

### Honesty

- [ ] The recreation is tested against a mock state as browser-only evidence.
- [ ] Do not mark the panel `OBSERVED` in game unless someone actually watched a bot drive it in Minecraft.
- [ ] Record any deliberate divergence (for example, adding visible HP bars or enabling Scan Chests) in the project docs.

---

## 11. Fast path for another AI

If the three static files are available and you want the exact shipped form, use them verbatim and focus on the server contract. If you are rebuilding in React/JS, use §8.6B and treat the steps below as the behavior checklist. If the files are unavailable, build in this order:

1. Create the exact HTML skeleton from §4, with all IDs and tab/panel relationships.
2. Add the token table and layout from §3; then the component styles from §4.
3. Implement the state fixture and a WebSocket broadcaster.
4. Implement `renderBots`, `renderTasks`, the status strip, and the tab switcher.
5. Add logs and filters.
6. Add the token recovery path.
7. Add the POST action table and confirm each button’s endpoint.
8. Implement the server auth/header/route rules from §7.
9. Test with two fake bots, one offline bot, one active task, one pending task, one failed task, and a 500-line history.
10. Verify the mobile breakpoints and the two disabled buttons.

A minimal mock fixture:

```json
{"type":"state","modVersion":"0.1.0+test","buildCommit":"test","hostName":"Host","dimension":"minecraft:overworld","connected":2,"bots":[{"username":"Miner01","nickname":"Miner","online":true,"health":19.5,"food":17,"x":-549,"y":91,"z":598,"task":"Gathering cobblestone","progress":"23/64","eta":"2m","tier":"Iron","state":"GATHERING","halted":false},{"username":"Wood02","nickname":"Wood02","online":false,"health":0,"food":0,"x":0,"y":0,"z":0,"task":"Offline","progress":"","eta":"","tier":"Wood","state":"OFFLINE"}],"tasks":[{"id":"11111111-1111-1111-1111-111111111111","type":"GATHER","item":"cobblestone","quantity":64,"progress":"23/64","status":"ACTIVE","reason":"","bot":"Miner01","attempt":1,"statusReason":"leased","lastOwner":"","leaseOwner":"Miner01","leaseExpiresAt":1760000000000,"leaseExpiresInMs":24000,"lastHeartbeatAt":1760000000000,"heartbeatAgeMs":500,"leaseActive":true},{"id":"22222222-2222-2222-2222-222222222222","type":"HUNT_FOOD","item":"","quantity":4,"progress":"0/4","status":"PENDING","reason":"","bot":"any","attempt":0,"statusReason":"","lastOwner":"","leaseOwner":"","leaseExpiresAt":0,"leaseExpiresInMs":0,"lastHeartbeatAt":0,"heartbeatAgeMs":0,"leaseActive":false}]}
```

For a browser-only visual check, the fake state is enough. It does not prove in-game task behavior; do not upgrade the panel’s status from `CODE ONLY` on that evidence.

---

## 12. Source index

### 12.1 Snapshot

- Branch: `fix/phantom-task-types`
- `HEAD`: `226b1b3bcef5e6eeb5e564539040ff806adbc336`
- `origin/main`: `d99e7580a97e722ff533829be8fd0c50997bc527`
- Panel assets match `HEAD`; server/task files have uncommitted working-tree edits.
- This document describes the working tree on 2026-11-13, not a released tag.

### 12.2 Asset hashes

```text
893596f1955b076aa942b73f9f93463380a6459b91442fd1836f32550fb9f70a  src/main/resources/web/v2/index.html
9647dfa27527349cf285e29a39c0e03eead5b465efd8604a1427dc5cd2a58663  src/main/resources/web/v2/app.css
8e7091767b9682dab38d69069da83eb4f66c6be323a26edd5401927b53a94bb9  src/main/resources/web/v2/app.js
ded2da9af92cef8f14479728a861b2f2808857470c58b0a414e2aa7fd14c5f19  src/main/java/com/rdh/swarm/web/SwarmWebHandler.java
c27c34764e5b6fb87703885d0595db5eaa169004c10229dfe17f78d20837d4b0  src/main/java/com/rdh/swarm/web/SwarmWebPanelV2.java
b5b78f3660fd125ed1c3bd836974248535e046679c0dcecaa3d4c64e1076bf7d  src/main/java/com/rdh/swarm/web/SwarmWebServer.java
```

### 12.3 Key source locations

| Topic | Source |
|---|---|
| Page skeleton, labels, IDs, tabs, forms | `src/main/resources/web/v2/index.html` |
| Colors, layout, components, breakpoints | `src/main/resources/web/v2/app.css` |
| Token precedence, WebSocket, renderers, logs, actions | `src/main/resources/web/v2/app.js` |
| Classpath asset loading, token substitution | `SwarmWebPanelV2.java` (`load`, `html`, `css`, `js`, `isAvailable`) |
| HTTP routes, auth gates, CSP, cookie, WebSocket upgrade | `SwarmWebHandler.java` (`handleHttp`, `handleWebSocket`, `sendResponse`) |
| Token, cookie, state JSON, logs, API switch, item cache | `SwarmWebServer.java` (`isAuthorized`, `isOriginAllowed`, `buildStateJson`, `buildTaskJson`, `buildItemsJson`, `handleApiCommand`) |
| Task fields, types, statuses, dispatchability | `BotTask.java` (`Type`, `Status`, `isDispatchable`) |
| Queue, clear/remove/cancel, nicknames, leases | `TaskQueue.java` (`clearCompleted`, `removeById`, `cancelById`, `setNickname`, `getAllTasks`) |
| Legacy `/` | `SwarmWebPanel.java`, `SwarmWebPanelScript.java` |
| Product status/context | `docs/FEATURES.md`, `README.md`, `AGENTS.md` |

### 12.4 Scan-chests wording

This matches the corrected `docs/FEATURES.md`: the `/v2` Scan Chests button is disabled and sends no request; calling `/api/action/scan_chests` directly does send `swarm:all scan`, and the server logs `Scan Chests: requested a storage scan from all workers`. `SwarmWebServer.java` is the authority for the endpoint.

