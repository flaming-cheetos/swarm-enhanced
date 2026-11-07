# 07 — Module Catalog (44)

`SwarmProxyModule` (60 lines) is the base for all "proxy" modules: it holds `targetSetting` (default `"All"`), and
`pushSetting(name, value)` sends `exec .settings <remoteModule> <name> <value>`; `toggle(on)` sends
`exec .toggle <remoteModule> on|off`. So a proxy module has **no local behaviour** — it is a remote control panel for the
real Meteor module of the same name running on workers.

| # | file (lines) | base | what it is | key settings |
|---|---|---|---|---|
| 1 | BotBrain (3038) | Module | **Real.** Autonomous task AI: GATHERING → CRAFTING_PREP → CRAFTING → SMELTING_DEPOSIT/WAIT/COLLECT → DEPOSITING; Baritone `#mine`/`#goto`, staging, death recovery | autoEquip=true, craftingTableRange=5, depositThreshold=4, gatherStaging=true, gatherStagingDistance=160 |
| 2 | SwarmAirCrystal (410) | Module | **Real.** Air-place crystal aura with damage calc, extrapolation, combo cooldown | target, minAirHeight=.5, range=5, minDamage=4, maxSelfDamage=8, antiSuicide |
| 3 | SwarmAnchorAura (46) | proxy | anchor-aura remote | target/minSelfDamage/place/break ranges, delays |
| 4 | SwarmAntiHunger (15) | proxy | anti-hunger | sprint, on-ground |
| 5 | SwarmAntiVoid (21) | proxy | anti-void | mode=Jump |
| 6 | SwarmArrowDodge (29) | proxy | arrow-dodge | move-type=Velocity, speed, ground-check |
| 7 | SwarmAutoArmor (28) | proxy | auto-armor (no tier logic!) | preferred-protection, swap-delay, avoided-enchantments, blast-leggings, ignore-elytra |
| 8 | SwarmAutoCity (36) | proxy | auto-city | target/break range, support, render |
| 9 | SwarmAutoEat (31) | proxy | auto-eat | blacklist, pause-auras, health/hunger thresholds |
| 10 | SwarmAutoExp (31) | proxy | auto-exp | mode=Both, replenish, thresholds 30/80, slot 6 |
| 11 | SwarmAutoReplenish (26) | proxy | auto-replenish | min-count=8, offhand, unstackable, same-enchants |
| 12 | SwarmAutoRespawn (12) | proxy | auto-respawn | *(none)* |
| 13 | SwarmAutoTool (34) | proxy | auto-tool | prefer=Fortune, silk-touch-for-ender-chest, anti-break, lists |
| 14 | SwarmAutoTotem (29) | proxy | auto-totem | mode=Smart, delay, health=10, elytra/fall/explosion |
| 15 | SwarmAutoTPA (145) | Module | **Real.** Accepts incoming TPA by chat regex; `/tpy {username}` | matchPattern, acceptCommand, delay=500, friendsOnly |
| 16 | SwarmAutoTrap (47) | proxy | auto-trap | whitelist, ranges, top/bottom modes, render |
| 17 | SwarmAutoWeb (36) | proxy | auto-web | ranges, predict-movement, doubles |
| 18 | SwarmBedAura (43) | proxy | bed-aura | delay=9, target/min/max damage, auto-move |
| 19 | SwarmBowAimbot (27) | proxy | bow-aimbot | range=20, entities, priority |
| 20 | SwarmChestSwap (18) | proxy | chest-swap | chestplate=PreferNetherite, stay-on |
| 21 | SwarmCrystalAura (85) | proxy | crystal-aura | ~40 settings (damage, place/break, support, pause) |
| 22 | SwarmElytraFly (64) | proxy | elytra-fly | ~35 settings (mode, speeds, auto-pilot, fireworks) |
| 23 | SwarmFastUse (21) | proxy | fast-use | mode=All, items, cooldown |
| 24 | SwarmFlight (31) | proxy | flight | mode=Abilities, speed, anti-kick |
| 25 | SwarmHoleFiller (61) | proxy | hole-filler | blocks, ranges, smart, render |
| 26 | SwarmJesus (30) | proxy | jesus | water/lava modes, dip rules |
| 27 | SwarmKillAura (55) | proxy | kill-aura | rotation, shield-mode, ranges, delays |
| 28 | SwarmNoFall (29) | proxy | no-fall | mode=Packet, placed-item=Bucket |
| 29 | SwarmNoRotate (10) | proxy | no-rotate | *(none)* |
| 30 | SwarmNoSlow (33) | proxy | no-slow | items, slowness |
| 31 | SwarmNuker (39) | proxy | nuker | shape=Sphere, mode=Flatten, ranges, packet-mine |
| 32 | SwarmOffhand (35) | proxy | offhand | item=Crystal, right-gapple, sword-gap, min-health |
| 33 | SwarmPearlChase (198) | Module | **Real.** Pearl + wind-charge pursuit of a named target | targetName, pearlDistance=6, cooldown, arc |
| 34 | SwarmProxyModule (60) | abstract | base: target selector + `pushSetting`/`sendToTarget` | target="All" |
| 35 | SwarmReach (17) | proxy | reach | block/entity reach |
| 36 | SwarmSafeWalk (23) | proxy | safe-walk | fall distance, sneak, edge distance |
| 37 | SwarmScaffold (48) | proxy | scaffold | blocks, fast-tower, placement, render |
| 38 | SwarmSpam (212) | Module | **Real.** Timed chat spam (up to 2 h), placeholders, random order | delaySeconds=60, messages list |
| 39 | SwarmSpeed (28) | proxy | speed | mode=Vanilla, speeds, timer |
| 40 | SwarmSpeedMine (27) | proxy | speed-mine | mode=Damage, haste, instamine, grim-bypass |
| 41 | SwarmSprint (25) | proxy | sprint | mode=Strict, keep-sprint |
| 42 | SwarmStep (25) | proxy | step | height=1.25, safe-step |
| 43 | SwarmSurround (55) | proxy | surround | blocks, center, double-height, protect |
| 44 | SwarmTimer (14) | proxy | timer | multiplier=1 |

## Done vs stub
- **Real logic (5):** BotBrain, SwarmAirCrystal, SwarmPearlChase, SwarmSpam, SwarmAutoTPA.
- **Functional proxies (37):** settings round-trip to remote Meteor modules; nothing runs locally unless the worker has
  that Meteor module installed and `.settings`/`.toggle` works.
- **Effectively empty (3):** SwarmAutoRespawn, SwarmNoRotate (no settings at all), SwarmTimer (one setting).
- No module contains a TODO; the only "placeholder" comments are in BotBrain/DepositConfig/DeathTracker/PlacementHelper.
- Related gap: proxies never verify the remote module exists — failures are silent (`catch (Exception ignored)` in
  `sendToTarget`).
