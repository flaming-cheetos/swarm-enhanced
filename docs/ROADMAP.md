# Roadmap

Updated 9 November 2026. This is the order of work and the honest status. If this file disagrees with the code, the
code wins — then fix this file.

## The north star

Start the bots on **iron gear**. Walk away for **one hour**. Come back to: every task finished, chests sorted, at
least one bot on **diamond**, every bot fed, nobody dead with gear on the floor, and nothing asking a question.

That is the product test. Not "can it mine" — can it run a shift without you.

**Passing it means:** 0 manual interventions · every task in a terminal state (`COMPLETE`, or `FAILED` with a
reason) · the input chest empty · no deposit leftovers · at least one bot at diamond · every bot holding food or
above hunger 12 · at most one death per bot, each with a logged cause · no task `RUNNING` for 10 minutes without
progress · no WARN line repeating more than five times.

## Two gates

- **Gate A — Survival 30.** One bot, 30 minutes, no new features. Proves the loop: tasks complete, food works,
  deposits land, nothing hangs.
- **Gate B — The Iron Hour.** The full hour, plus tier progression from iron to diamond.
- **Gate A2 — The Two-Bot Shift.** Two bots, 90 minutes, no task or chest fighting.
  rdh's stated definition of done (`2026-11-13`): "one or 2 bots FULLY surviving for an
  hour or an hour and a half". Task ownership must be settled before this gate, not
  after — the leases that landed on 2026-11-13 are the foundation.

One bot first. A fleet multiplies whatever one bot already is, good or bad.

## Status legend

`DONE` verified live · `CODE ONLY` written, never watched working · `PARTIAL` some of it works · `BROKEN` works
against you · `ABSENT` not written

## Milestones

| # | What | Lane | Status |
|---|---|---|---|
| M0 | Foundation: 26.3 port, modules, task queue, panel, mining, halt, deposit | both | `DONE` (live) |
| M1 | Survival loop: fuel, cooking, completion, pause, staging | bot | `PARTIAL` — outcome-completion gate, deferred completion, deposit ledger and multi-load smelt are `DONE` live (2026-11-13); fuel, food and pickup remain `CODE ONLY` |
| M2 | Storage intelligence: chest counts, reserved slots, sorter | both | `PARTIAL` — chest scan + index + keep-filter verified live 2026-11-13; reserved slots, low-stock warnings and sorter missing |
| M3 | Bulk jobs: shulkers, counting, multi-trip | bot | `ABSENT` |
| M4 | Tier progression: iron → diamond | bot | `ABSENT` |
| M5 | Base utilities: gather zones, furnace registry, self-rebuild | bot | `PARTIAL` |
| M6 | Blueprints: `.litematic` / `.schem` | bot | `ABSENT` |
| M7 | Bodyguard | bot | `ABSENT` |
| M8 | Anchor / respawn bots — the hardest, and the most client-bound | bot | `ABSENT` |
| M9 | Fleet: several bots, no two going for the same chest | bot | `PARTIAL` — task claims, leases, renew and requeue are `DONE` live 2026-11-13; chest/furnace deconfliction `ABSENT` (gate A2 depends on it) |
| M10 | In-game chatbot — independent, buildable any time | bot | `ABSENT` |
| M11 | Hostile-server hardening: pacing, packet rates, reconnects | bot | `ABSENT` |
| M12 | Threat and evasion — priority sits **above** M4 | bot | `ABSENT` |
| M13 | Stash and travel: ender chest, stash registry, nether | bot | `ABSENT` |
| W | Panel v2 at `/v2` — static today; React rewrite planned | panel | `CODE ONLY` — opened on desktop and phone against fake players; no bot in game yet |

## Phase 0 — the dev loop (current work, added 2026-11-13)

Evidence: the 2026-11-13 iron session spent ~70% of its wall clock waiting — 6 live
runs (~54 min), 3 client restarts (~15 min), scans and polling (~10 min), against
~25 min of real code. A live run costs 9 minutes because 78 ingots is 6.5 minutes of
furnace time and because every observation needs a Minecraft client.

Phase 0 builds the loop that makes every later phase cheaper, and ships before any
new gameplay feature. Full contract: `docs/MCP_SERVER_SPEC.md`.

- **Time compression:** measured `/tick rate` ladder on the Pi 5 test server (RCON `192.168.4.57:25575`), two blast furnaces (2× smelt speed) and teleport instead of walking. 78 ingots: 6.5 min today → ~3.2 min at 20 TPS → ~65 s at 60 TPS → ~33 s at 120 TPS. The rate that held (`mspt <= 45`) is recorded in every verdict.
- **Eyes as data:** world probe (blocks, containers, furnace state, entities) plus a `screenshot` tool that reads the client's own PNG — the AI cannot see the game window, so world state must be readable.
- **Decision visibility:** `bot_state` (plan, guards, inventory) and a decision-trace ring buffer, replacing chat-line grepping.
- **Fault injection:** a short chest delivery, a refused chest, an unreachable furnace — so guards like the 2026-11-13 withdraw top-up can be proven in 30 seconds instead of an unproven 9-minute gamble.
- **Verdicts, not logs:** JSONL events in the mod + an assertion engine, so a run is PASS/FAIL with the first mismatch named.
- **Client control:** launch/stop/restart by PID, wait for join, toggle the brain — the manual parts of tonight, automated.
- **MCP server:** Streamable HTTP on loopback (`127.0.0.1:8790/mcp`) so a new chat opens with the tools already connected.

Definition of done for Phase 0: `run_scenario iron78` passes in under 3 minutes with
an assertion verdict, and `run_scenario topup-fault` proves the short-delivery top-up
path in under a minute.

## What is happening now

**Updated 2026-11-13:** Phase 0 (the dev loop) is the current work. Gate A is next,
and is not blocked on Phase 0 finishing — fuel can land in parallel.

1. **The fix stack is on `main`** — fuel, deposits, completion, staging. Landed as a fast-forward, builds green.
2. **Gate A on `main`.** Not run yet, and it is the next thing that matters.
3. **Panel v2 review** — static `/v2` is on `main` and has been opened
   on a desktop browser and a phone, against fake players. Layout, tabs
   and the task queue all render. No bot has driven it in game yet. A
   React rewrite is a direction, not a commitment, and the panel owner
   decides. Panel lane.
4. **B6, the tick-owned request model**, before any tier work. A grind loop on a thread-racy planner produces
   failures that look like logic bugs.

### Tonight's queue (2026-11-14, planned with the operator before bed)

Phase 0 is close enough that Gate A is the next real proof, so the order is:

| # | Work | Exit criterion |
|---|---|---|
| 0 | Deploy the staged jar, relaunch both clients, prove `world_container open=true` reads a furnace's coal | **DONE 04:24** - `coal x42` at `-549 91 606` |
| 1 | Five cheap tools: `log_tail`, `task_watch`, `chest_delta`, `server_restart`, `panic` | each answers once, live |
| 2 | **Fix the 690 vs 768 gap** - the storage index undercounts the 601/602 double chest by exactly one 78-ingot run | **DONE 04:26** - index 832 = 768 + 64, double chest counted once; 690 was a pre-deposit snapshot. Built `/api/debug/storage` to see it |
| 3a | **A3/A4/A5 base registry** - locations as data, id-addressed reads, survives restart | **DONE 2026-11-13 04:33** - 12 entries, `loadedFromDisk=true` after a real stop/start, iron 832 = 768 + 64, `bf1` coal x42 |
| 3 | Phase 1 leftovers: surface the fuel reader (`G2: cook check`) in `bot_state` as `fuels[]` + `fuelTicks`, then the food loop | `swarm_preflight` passes every check on a fresh bot |
| 4 | **Gate A**: one bot, 30 minutes, deposits verified by opening chests, not by chat | verdict PASS with every deposit diffed against the world |

Gate A at 60 TPS needs the RCON password on this machine (`tools/mcp/.rcon-password`);
without it the gate runs at 20 TPS, which is slower but still valid.

Accepted work is above. Unaccepted ideas sit in `docs/IDEAS.md` and are
reviewed, never merged.

## Order after Gate A

Threat and stash (M12, M13, plus `local` vs `anarchy` profiles) → storage and bulk (M2, M3) → fleet (M9) →
blueprints, bodyguard, anchor (M6, M7, M8) → hardening (M11) → then live. The chatbot (M10) can be built any
time; it touches nothing else.

Every default flips once threat and stash land, which is why they come before any real server time.

## Non-goals

- The outbound `/tpr` kit-run loop. Dropped on purpose.
- The mob farm. Unwanted — the code can stay, the settings get renamed.
- The 37 proxy PvP modules stay disabled.
- Porting to a headless runtime this month. Later, and only for the parts that aren't client-bound.
- Writing our own block placement engine to replace Baritone's.

## Red lines

- **The panel is never exposed to the internet.** Loopback is not a trust boundary: token on every request
  including the socket, `Origin` allow-listed, no wildcard CORS, no tunnel. It shows coordinates, and a leaked
  base is unrecoverable.
- **Coordinates never leave the client** — not in chat, not on the panel, not in a screenshot.
- **No movement exploits.** Flight, speed, blink and timer can cost the account.
- **Chat never obeys chat.** The bot answers the server; it takes no orders from it.
- **Ender chest is the only trusted storage.**

## How you'll know it is working

The logs. Every claim has a line behind it: if it says it deposited 99, a chest has 99 more in it. A verdict
without a log line is a hypothesis, and `NOT OBSERVED` is a valid answer.


## v2 backlog — deferred, not cut

rdh, 2026-11-13: "most of this is still what i want in the END once the main core is
battle tested and fully done to the spec." These stay in the plan, ordered behind
Gate B. They are not rejected and not deleted.

| # | What | Why it waits |
|---|---|---|
| V1 | M6 blueprints (`.litematic` / `.schem`) | needs a stable, trusted task/paste path first |
| V2 | M7 bodyguard | depends on threat model (M12) and fleet comms |
| V3 | M8 anchor / respawn bots | hardest and most client-bound; needs restart survival proven (M1 persistence) |
| V4 | M10 in-game chatbot | touches nothing else — can be pulled forward any time it is wanted |
| V5 | M11 hostile-server hardening (pacing, packet rates, reconnects) | only matters once it runs on a real server for real hours |
| V6 | W panel v2 React rewrite | current panel covers the need; revisit after fleet view (Phase 2) |

## Revision history

- `2026-11-13` — Phase 0 added; M1/M2/M9 re-scored from live evidence; Gate A2 added;
  v2 backlog section created from rdh's "defer, do not cut" call.
