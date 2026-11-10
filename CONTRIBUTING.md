# Contributing

Two people, one `main`. Read this once and you won't need it again.

## What this is

A Meteor Client addon that runs autonomous bots on **anarchy servers**. That is the only target. There is no SMP
support, no server-compatibility layer, and no plan to add one — designs that assume otherwise get rejected, kindly.

## Build

```
JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
.\gradlew.bat build
```

JDK 25, Gradle wrapper, no extra flags. `-PcopyToMmc` is a personal convenience and never goes in a PR.

Output: `build/libs/`. Drop the jar in `mods/` next to Meteor.

## Branches

`main` is always buildable. Never commit to it directly, never force-push it.

```bash
git switch main && git pull
git switch -c feat/panel-log-filters
```

`<type>/<kebab-topic>`, done. Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `exp`, `release`. Named after the
work, never after the person. Branches live **days**. If yours is a week old, rebase it or delete it.

`exp/*` is for spikes that answer a question and then get thrown away. No CI, no review, no merge — just close it.

## Before you start

Write the files you expect to touch in the issue. If someone else's open PR touches one of them, talk first. Two
edits can merge cleanly and still break the bot at runtime, and git will not warn you.

Hot files, one holder at a time: `DepositDriver`, `EnhancedSwarmConnection`, `BotBrain`, `SmeltingManager`,
`SwarmWebServer`, `SwarmWebHandler`, any `*.mixins.json`.

## Pull requests

- One thing per PR. If the title needs "and", split it.
- Keep it under ~400 changed lines. Bigger is fine when the change is genuinely one thing — say why in the body.
- Fill in the template. The **Live test** section is not optional: either you watched it work and say where, or you
  write `code only — not observed`. Both are acceptable answers. Claiming a result you didn't see is not.
- Opening a draft PR early is encouraged. That's how the other person knows what's in flight.
- Squash merge by default, then delete the branch. Exception: if another open branch is based on yours, it lands as-is
  (merge commit or fast-forward) — squashing it would force everyone stacked above you to rebase.
- If a PR is green and reviewed, the author merges it. Nobody owns a merge, nobody waits on a person who's asleep.

## Testing

Copy the world first. Singleplayer, alone, no `/tp`, no second player. Never test on a world you care about.

Build green means it compiles. It does not mean it works. If you didn't observe it, don't say it.

## Red lines

- **Anarchy servers only.** No SMP, no Hypixel, no compatibility shims.
- **Ender chest is the only trusted storage.** Never add a deposit path that trusts a normal chest.
- **The 37 proxy PvP modules stay disabled.** Needing one enabled is a design discussion, not a config change.
- **Threat evasion outranks tier progression.** If a change makes the bot braver, it's probably wrong.
- **No secrets in git.** Panel token, API keys, account details, nothing.
- **Feature PRs never bump the version.** Only `release/*` does.
- **No new dependencies without asking.** Every jar can break the other person's build.

## Style

4 spaces, LF, UTF-8, no trailing whitespace. Match the surrounding code, including its naming. Don't reformat a file
you're only changing one line in — it hides your real change.

Commit messages: `area: what changed and why`, so `main` reads like a changelog.

```
panel: keep finished tasks when adding a new one
fix: resolve fuel from the client world on multiplayer servers
```

## Panel

The panel may be a React app in `panel/` (Vite). Build it with `npm run build`, which writes into
`src/main/resources/web/v2/`, and commit that output — `gradlew build` never runs Node, and nobody hand-edits the
dist. `/` stays on the old page; the new one is `/v2`.

If you touch the web panel: it's reachable from a phone over the LAN, so treat every value as hostile. Never
`innerHTML` server or player-provided text, keep the token on every request, keep the `Origin` check, don't add an
endpoint that returns coordinates to anyone but the owner. The full contract is in `docs/panel.md`.
