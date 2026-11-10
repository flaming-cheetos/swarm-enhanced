# Enhanced Swarm

Enhanced Swarm is a Meteor Client addon that runs autonomous bots on anarchy servers. A host coordinates a swarm of
workers, queues work, tracks storage, and serves a token-protected web panel; workers connect over TCP, reconnect
automatically, and execute the assigned tasks in-game.

**Anarchy servers only — no SMP support.**

## Requirements

- Minecraft 26.3
- Meteor Client
- JDK 25

## Build

```bash
JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\gradlew.bat build
```

Output: `build/libs/`.

## Install

Drop the built jar into `mods/` next to Meteor Client.

## What it does

- Runs a host/worker swarm over TCP with per-worker task assignment and automatic reconnects.
- Queues gather, craft, smelt, hunt-food, grind, smith, shulker, build, and deposit work.
- Tracks inventory and configured storage, with tick-owned container deposits that verify menus and count observed
  item deltas.
- Serves a token-protected web panel for workers, tasks, actions, and live logs.
- Includes survival and combat helper modules, with the proxy PvP modules kept disabled by default.

## Panel

The host panel uses port `8888`; set `bind-address` for LAN access. The panel token is required on every request.
`/` is the current page; `/v2` is in progress.

## Docs

- `AGENTS.md` — the rules every AI session follows here
- `docs/ROADMAP.md` — what's done, what's next, and what is explicitly not being built
- `CONTRIBUTING.md` — how to work in this repo

## License

GPL-3.0. See [LICENSE](LICENSE).
