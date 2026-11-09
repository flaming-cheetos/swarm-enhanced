# Panel v2 — build notes

Built 2026-11-09 on `feat/panel-v2` from `fix/staging`. The new page is additive at
`/v2`; `/` is still the v1 page.

## Files

- `src/main/resources/web/v2/index.html` — 8,564 B
- `src/main/resources/web/v2/app.css` — 11,679 B
- `src/main/resources/web/v2/app.js` — 24,979 B
- `src/main/java/com/rdh/swarm/web/SwarmWebPanelV2.java` — classpath loader + meta-token injection
- `DEEPDIVE/panel/v2-shots/{overview,tasks,log}-{1280,390}.png`
- `DEEPDIVE/panel/v2_capture/server.py` — mock harness used for the browser checks (not production code)

## What changed

- **Status strip** above the unchanged header: connection dot (connected / reconnecting /
  rejected), bot, dimension, coords, task + progress, health/food, halt state. Unknown values
  render `unknown`, stale values `unknown (stale Nm)`. The online count now counts workers with
  `online: true`, not socket connections.
- **Tabs**: Overview (strip + worker cards), Tasks (task list + add box), Log, Actions (Quick
  Actions + Send Command). Desktop left rail, phone bottom bar. Selection is stored in
  `sessionStorage` under `swarm.tab`; the WebSocket is not touched by tab changes.
- **Keyed updates**: bot and task nodes are held in maps and patched in place. Inputs are
  outside the patched regions.
- **Log**: `white-space: pre-wrap`, 2,000-row DOM cap, free-text filter, prefix chips, follow at
  bottom, pause on scroll-up + jump-to-latest, copy last 200 visible lines with clipboard
  fallbacks. Only WARN/ERROR are coloured.
- **Token fallback**: 401 shows one inline strip with an input and Retry. The pasted token is
  stored in `sessionStorage` only, sent as `X-Swarm-Token`, cleared on the next 401, and the dot
  says `rejected`; no retry loop.
- **Phone**: ≤899 px bottom tab bar (44 px targets), sticky strip, sticky command composer with
  `scrollIntoView` on focus, safe-area padding, no horizontal scroll at 360/390 px. The item
  field autocompletes from `GET /api/items` on first focus.

## The four defects

1. **Badges.** v1 had no `.status-BLOCKED` or `.status-CANCELLED` rules. v2 has a pill for every
   state; measured: BLOCKED `rgb(58,42,26)` on `rgb(255,170,68)`, CANCELLED `rgb(42,42,42)` on
   `rgb(153,153,153)`, all six states present in the DOM.
2. **No full rebuild.** The 2-second poll now patches keyed nodes. With a task list and the
   cursor in a nickname field, three polls (7 s) kept the same node, the same focus, and the
   typed value `Focus Probe`; the server value stayed `Iron Goblin` until a real blur.
3. **`+ Add` no longer prunes.** `SwarmWebServer.handleApiCommand` `/api/task/add` contains no
   `clearCompleted()` call. `clearCompleted()` now appears only under `/api/task/clear_done`.
   Code-verified; a live task queue was not available.
4. **Quotes.** `extractJson` now parses with Gson (`JsonParser` + `getAsString`) instead of
   splitting on quotes. The browser sent `{"target":"all","cmd":"say \"hi\" test"}` exactly; a
   standalone Gson parse printed `say "hi" test`.

## Verification

- `./gradlew build --console=plain` with Java 25: **BUILD SUCCESSFUL**, `test NO-SOURCE`.
  The jar contains `web/v2/index.html`, `app.css`, `app.js` and `SwarmWebPanelV2.class`.
- Auth via the mock: `/v2` without token 401, with `?token=` 200 and `swarm_token` set;
  `/v2/app.css` and `/api/items` 401 without credentials.
- Browser at 1280 and 390: all four tabs reachable, six badges, log filter/copy/pause, token
  strip/retry, autocomplete (12 items after one `GET /api/items`), no console errors.
- 401 path: clear auth → strip appears, dot `rejected`, no reconnect loop (one `ws_open`, one
  WS 401); paste token → Retry → `Connected`; a later 401 cleared the stored token again.
- 360 px: `scrollWidth === 360`, no overflow; tabs 44 px; composer `position: sticky`, input
  visible after a real focus.
- Cold load: **45,220 B** served (`index.html` after token substitution 8,562 B + CSS 11,679 B +
  JS 24,979 B), three HTTP GETs + one WebSocket. v1 was 21,619 B inline, two GETs + one WS.
  `/api/items` is fetched only on first item focus.

## Deliberately left alone

- `/`'s markup, CSS and JS. `git diff` for `SwarmWebPanel.java` and `SwarmWebPanelScript.java`
  is empty. The v1 fallback in `SwarmWebHandler` is unchanged; the v2 routes and `/api/items`
  are inserted above it.
- BotBrain, SmeltingManager, DepositDriver, DropCollector, EnhancedSwarmConnection and the
  proxy modules. No framework, bundler, npm, CDN or web font was added.
- Existing wording, palette, card/table/badge density. The only brand change is the small `v2`
  chip in the header.
- `check_fuel` remains 501; it was not part of this build.

## Differences from `current/shots/` and limits

- Intended differences: the status strip, the tab rail/bottom bar, the `v2` chip, and the
  mobile stacking. Card styling, colours and labels otherwise match v1.
- The top-left `Freebuff` badge in the captures is the desktop host chrome, not the page.
- No Minecraft client/server was running. The Java-side task queue (prune), the real registry
  contents behind `/api/items`, and the live WS auth handshake are **code-verified, not
  live-verified**. The browser checks used `DEEPDIVE/panel/v2_capture/server.py` fixtures.
- `GET /api/items` returns registry IDs, including `minecraft:air`; `/api/task/add` already
  rejects air. If the picker should hide air, that is a one-line filter, not a v2 blocker.
