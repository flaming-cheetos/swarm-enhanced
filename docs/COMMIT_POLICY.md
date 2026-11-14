# Commit policy — full history, pushed as it happens

Owner's standing rule, 2026-11-14. This file is the canonical copy for the repo;
`AGENTS.md` carries the same rule for an agent's first read. If one changes, change both.

## The rule

**One commit per change, pushed immediately.** Not one per session, not one per feature.
The moment a change lands — code, doc, config, test rig — it gets its own commit and a
`git push` to `main`, so the Discord feed shows the work as it happens instead of a dump
at the end.

**Ugly changes get their own commit too.** A bug reproduction is a commit. A fix that did
not work is a commit, and the message says so. A revert is a commit. A build that fails
because of an experiment is a commit — `WIP:`, `repro:`, `failed:` prefixes are fine.
Full history over tidy history: a committed failure is a fact, an uncommitted one is a
rumour, and a squashed session is a story you cherry-picked afterwards.

**Never squash, never amend to tidy, never force-push this repo.** If a commit is wrong,
fix it forward with another commit that says what changed.

## What this replaces

`AGENTS.md`'s older "Don't merge, tag or push unless asked" still holds for **merges,
tags and other people's branches** — those need a request. It no longer holds for the
routine commit-and-push of your own work on `main`: that permission is standing, and
asking every time is the thing being removed.

## What it does not cover

- **No force-push, ever.** History is append-only here.
- **No tags or releases** without being asked.
- **No pushing someone else's branch** or a branch you did not work on.
- **The local personality block in `AGENTS.md`** is the one change that stays out of
  history on purpose: that file carries a skip-worktree flag on the owner's machine.
