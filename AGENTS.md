# AGENTS.md

Read this before touching anything. It exists so that any AI session in this repo — the owner's or the
collaborator's — starts from the project's rules instead of guessing.

## What this is

A Meteor Client addon for **Minecraft 26.3** that runs autonomous bots on **anarchy servers**. Java 25.

Anarchy only. No SMP support, no server-compatibility layer, no "just in case" shims. A design that assumes
otherwise gets rejected.

## The goal, in one sentence

One bot on iron gear, left alone for an hour, finishes every task, sorts its chests, makes a diamond's worth of
progress, is still fed, and asks nothing.

Milestones, status and what is deliberately **not** being built: [`docs/ROADMAP.md`](docs/ROADMAP.md). Read it
before starting. It is the answer to "what should I do next" and to "should I add this feature" — usually the
second answer is no.

## Where you work

Two people, two areas. Work where your task is. If you spot something in the other lane, write it down in the
issue — don't fix it in passing.

| Area | Files | Owner |
|---|---|---|
| **Panel** | `src/main/resources/web/v2/` (source today), `src/main/java/**/web/`; `panel/` once a React port is agreed | collaborator |
| **Bot** | `BotBrain`, the drivers (`DepositDriver`, `MiningDriver`, …), `EnhancedSwarmConnection`, `SmeltingManager` | owner |
| **Shared** | `docs/`, `.github/`, root build files | whoever asked first |

Panel work needs no game running: open it in a browser, done. Bot work always needs a singleplayer test, and its
result stays `NOT OBSERVED` until a person watches it happen.

`/` is the old panel and is not being redesigned. `/v2` is the new one. Never break `/`.

## How to work

**One task at a time.** Take it from the issue. If the issue is vague, ask before building.

**Don't add what nobody asked for.** "It seemed natural" is how this project drifts. A feature that isn't in
`docs/ROADMAP.md` and isn't in the issue is out of scope, however small.

**Docs and source are truth, not memory.** Never invent an API, a setting name, a commit, a log line or a test
result. The ladder: this file and `docs/` → the actual source → `javap` on the real jar → derive it, and say
that you derived it.

**Verify before claiming.** Build it, run it, prove it. `code only` and `NOT OBSERVED` are correct answers.
A claim with no log line behind it is a hypothesis.

**Green build at every commit.** One commit per step, message `area: what changed and why`.

## Build

```
JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
.\gradlew.bat build
```

JDK 25, Gradle wrapper, output in `build/libs/`. **Never** pass `-PcopyToMmc`. `gradlew build` must never
invoke Node — the committed panel output is what ships in the jar.

## 26.3 API facts that have already cost a build cycle

Verify anything not on this list with `javap` before writing against it.

- `net.minecraft.resources.Identifier`, not `ResourceLocation`.
- `handleContainerInput`, not `clickSlot`.
- Food is `DataComponents.FOOD`. Cooking fuel is `DataComponents.COOKING_FUEL` → `CookingFuel.burnTime()`,
  a `ResolvableInt` in **ticks**, not item counts. `FuelValues` does not exist on 26.3.
- `#minecraft:coals` is coal and charcoal only — coal blocks are not in it.
- Chest contents are not client-synced until opened. A stale snapshot looks exactly like an empty chest.
- `getSelectedSlot()` / `setSelectedSlot()`.
- 26.3 is Mojang-named and unobfuscated: no `mappings(...)` line, no `modImplementation`, no `remapJar`.
- Baritone for 26.3 is **snapshots only** and ships as a separate jar.

## Hard rules

1. **Baritone is the only block-breaker.** Never call `mc.gameMode.destroyBlock` from bot code — the server
   rejects it and you get ghost blocks.
2. **Nothing sweeps during a break.** No drop collection while destroying, while Baritone is pathing, or within
   about a second of the last sweep.
3. **Halt is a latch, not a `#stop`.** Once halted, everything respects it until an explicit resume.
4. **Never target air**, and never queue an item with no known source.
5. **Ender chest is the only trusted storage.** Never add a deposit path that trusts a normal chest.
6. **The 37 proxy PvP modules stay disabled.** They are not test targets.
7. **A fix that hides a ghost block is worse than a visible one.** Confirm breaks from server evidence — a
   drop entity or an inventory delta — and abandon unconfirmed ones with a log line instead of retrying.

## Testing

Local singleplayer, alone, on a **copied** world. No second player, no `/tp` — a teleport mid-break desyncs the
client and fakes the mining bug. Never test on a world you care about.

A green build proves it compiles. Nothing more.

## The panel

Two pages, one server, one token rule:

- `/` — the old panel, embedded in `SwarmWebPanel.java`. Not being redesigned, but it must keep working.
- `/v2` — hand-written `index.html`, `app.css`, `app.js` in
  `src/main/resources/web/v2/`. Committed as source, no build step, no npm.

**Agreed direction, not started:** `/v2` moves to Vite + React +
TypeScript in `panel/`. If that happens: npm builds into `web/v2/` and
that output is committed; `gradlew build` never invokes Node; it's a
port, not a redesign; no router, no state library, no component kit.

Until someone is explicitly asked to do that, `web/v2/` is the source
and you edit it directly.

The panel is reachable from a phone on the LAN, so every value it shows is hostile:

- never `innerHTML` anything from the server or a player;
- keep the token on every request;
- keep the `Origin` check;
- no endpoint that returns coordinates to anyone but the owner.

## Don't

- Don't claim a test you didn't run.
- Don't weaken a test, skip an assertion, or swallow an error to get green.
- Don't merge, tag or push unless asked.
- Don't add a dependency without asking.
- Don't bump the version in a feature branch.
- Don't reformat a file you only changed one line in.
- Don't invent new top-level directories.
