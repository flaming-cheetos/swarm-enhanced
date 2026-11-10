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

One bot first. A fleet multiplies whatever one bot already is, good or bad.

## Status legend

`DONE` verified live · `CODE ONLY` written, never watched working · `PARTIAL` some of it works · `BROKEN` works
against you · `ABSENT` not written

## Milestones

| # | What | Lane | Status |
|---|---|---|---|
| M0 | Foundation: 26.3 port, modules, task queue, panel, mining, halt, deposit | both | `DONE` (live) |
| M1 | Survival loop: fuel, cooking, completion, pause, staging | bot | `CODE ONLY` — fixes pending |
| M2 | Storage intelligence: chest counts, reserved slots, sorter | both | `PARTIAL` |
| M3 | Bulk jobs: shulkers, counting, multi-trip | bot | `ABSENT` |
| M4 | Tier progression: iron → diamond | bot | `ABSENT` |
| M5 | Base utilities: gather zones, furnace registry, self-rebuild | bot | `PARTIAL` |
| M6 | Blueprints: `.litematic` / `.schem` | bot | `ABSENT` |
| M7 | Bodyguard | bot | `ABSENT` |
| M8 | Anchor / respawn bots — the hardest, and the most client-bound | bot | `ABSENT` |
| M9 | Fleet: several bots, no two going for the same chest | bot | `ABSENT` |
| M10 | In-game chatbot — independent, buildable any time | bot | `ABSENT` |
| M11 | Hostile-server hardening: pacing, packet rates, reconnects | bot | `ABSENT` |
| M12 | Threat and evasion — priority sits **above** M4 | bot | `ABSENT` |
| M13 | Stash and travel: ender chest, stash registry, nether | bot | `ABSENT` |
| W | Panel v2 at `/v2` — static today; React rewrite planned | panel | `CODE ONLY` — opened on desktop and phone against fake players; no bot in game yet |

## What is happening now

1. **The fix stack is on `main`** — fuel, deposits, completion, staging. Landed as a fast-forward, builds green.
2. **Gate A on `main`.** Not run yet, and it is the next thing that matters.
3. **Panel v2 review** — static `/v2` is on `main` and has been opened
   on a desktop browser and a phone, against fake players. Layout, tabs
   and the task queue all render. No bot has driven it in game yet. A
   React rewrite is a direction, not a commitment, and the panel owner
   decides. Panel lane.
4. **B6, the tick-owned request model**, before any tier work. A grind loop on a thread-racy planner produces
   failures that look like logic bugs.

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
