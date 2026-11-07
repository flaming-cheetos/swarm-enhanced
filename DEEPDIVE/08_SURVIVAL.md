# 08 — Survival Loop Modules

All eight are **remote proxies** except AutoTPA (real chat listener). None talk to the host's TCP protocol;
the only "comms" is `SwarmProxyModule.sendToTarget` → `host.sendAll("swarm:all exec .toggle|.settings ...")` or
`sendTo(target, "swarm:<target> ...")`.

## SwarmAutoTPA (145) — real
- Settings: `match-pattern` (regex, default `(.*) wants to teleport to you`), `accept-command` (**`/tpy {username}`**),
  `delay` 500 ms, `friends-only` true, `log-accepts` true, `debug` false.
- On chat message: match regex, extract username, optional friend check, sleep delay, send accept command.
- **Inbound only** — no `/tpr`, no teleport-to-player, no kit.

## SwarmAutoRespawn (12) — proxy
- No settings. `super("swarm-auto-respawn", "Proxy for auto-respawn", "auto-respawn")`.
- Toggling it only sends `.toggle auto-respawn on|off` to workers.

## SwarmAutoArmor (28) — proxy
- Settings: `preferred-protection` (enum Protection), `swap-delay` 1, `avoided-enchantments`, `blast-prot-leggings` true,
  `anti-break` false, `ignore-elytra` true.
- **No tier progression** — it just forwards config to Meteor's auto-armor, which picks the best piece currently in
  inventory. Upgrades to netherite are not initiated here (see 06).

## SwarmAutoEat (31) — proxy
- Settings: `blacklist`, `pause-auras` true, `pause-baritone` true, `search-inventory` false,
  `food-priority` Saturation, `threshold-mode` Any, `health-threshold` 10, `hunger-threshold` 16.
- Picking food is done remotely by Meteor's auto-eat, not by this code.

## SwarmAutoTool (34) — proxy
- Settings: `prefer` Fortune, `silk-touch-for-ender-chest` true, `fortune-for-ores-and-crops` false,
  `anti-break` false + `anti-break-percentage` 10, `switch-back` false, `switch-delay` 0,
  `list-mode` Blacklist, `whitelist`, `blacklist`.
- Selection logic lives in the remote module.

## SwarmChestSwap (18) — proxy
- Settings: `chestplate` (Diamond | Netherite | PreferDiamond | PreferNetherite, default **PreferNetherite**),
  `stay-on` false, `close-inventory` true.
- This is the only place netherite appears in a *usable* survival path, and it depends on Meteor's chest-swap.

## SwarmAutoReplenish (26) — proxy
- Settings: `min-count` 8, `delay` 1, `offhand` true, `unstackable` true, `same-enchants` true (visible when unstackable),
  `search-hotbar` false, `excluded-items`.
- Refills hotbar stacks from inventory remotely.

## SwarmAutoExp (31) — proxy
- Settings: `mode` Both, `replenish` true, `only-on-ground` false, `exp-slot` 6,
  `min-threshold` 30, `max-threshold` 80.

## Loop as it actually runs
1. Worker auto-eat keeps food up; auto-totem/offhand protect; auto-armor equips whatever is best **in inventory**.
2. Auto-tool switches for mining; auto-replenish refills stacks; auto-exp repairs gear (if enabled).
3. Death: AutoRespawn (remote) respawns; BotBrain has its own death recovery (walks to death pos via `#goto`).
4. AutoTPA lets friends teleport in; nothing teleports the bot out.

## Gaps
- No local implementations to audit — bugs here are Meteor's, not this repo's.
- `friendsOnly` relies on a friend list that is not part of this codebase; failure mode is silent deny.
- Gear progression stops at diamond because the survival modules never trigger crafting; only BotBrain does, and only up
  to `RecipeBook`'s recipes.
