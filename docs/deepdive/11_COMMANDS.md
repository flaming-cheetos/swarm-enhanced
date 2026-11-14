# 11 — Commands (`/eswarm`, alias `/swarm`)

Root command class `commands/SwarmCommand` (694 lines). Two instances registered:
`new SwarmCommand()` → **`/eswarm`**, and `new SwarmCommand("swarm")` → **`/swarm`** (legacy alias, same tree).

## Literal tree (from `build()`)
```
/eswarm exec <command...>                     // host: sends "swarm:all exec <cmd>"; solo: dispatches locally ("#" = chat, else Meteor command)
/eswarm list                                  // host.listWorkers()
/eswarm send <target> <command...>            // "swarm:all <cmd>" or "swarm:<target> <cmd>"
/eswarm gather <item> <qty>                    // additive; base stock does not count
/eswarm ensure [in <category>] <item> <qty>    // target-level; host queues the measured shortfall
/eswarm task cancel <taskId>                  // "swarm:all task cancel <uuid>"
/eswarm task pause <bot>                      // "swarm:<bot> task pause"
/eswarm task resume <bot>                     // "swarm:<bot> task resume"
/eswarm task list                             // local print of queue
/eswarm deposit set [category] [x y z]        // crosshair if no coords; host.setDepositChest
/eswarm deposit remove <category> [index]
/eswarm deposit list
/eswarm deposit auto <on|off>                 // "swarm:all deposit auto on|off"
/eswarm deposit threshold <0..36>             // "swarm:all deposit threshold <n>"
/eswarm deposit push                          // pushDepositConfigAll + auto/threshold
/eswarm scan [category]                       // "swarm:all scan [cat]"
/eswarm stock [category]                      // prints StorageIndex totals locally
/eswarm base set [x y z] | clear | show | push
/eswarm dumpstorage                           // debug dump of storage index
/eswarm waypoint add <name> <category> <x y z>
/eswarm waypoint portal <name> <x y z>
/eswarm waypoint list | remove <name> | goto <name> | push
```
Tab completions: worker names (`TARGET_SUGGESTIONS` via reflection on `Commands.getCommands()`), command list fallback,
categories (`CATEGORY_SUGGESTIONS`), block ids for `scan` (`diamond_ore, iron_ore, coal_ore, oak_log, stone, dirt, gravel, sand`).

## RAW payloads
```java
mod.host.sendAll("swarm:all exec " + command);                       // :125
mod.host.sendAll("swarm:all " + command);                            // :160  (send all)
mod.host.sendTo(target, "swarm:" + target + " " + command);          // :163  (send one)
mod.host.sendAll("swarm:all task cancel " + idStr);                  // :228
mod.host.sendTo(bot, "swarm:" + bot + " task pause");                // :240
mod.host.sendTo(bot, "swarm:" + bot + " task resume");               // :250
mod.host.sendAll("swarm:all deposit auto " + (on ? "on" : "off"));   // :355
mod.host.sendAll("swarm:all deposit threshold " + val);              // :366
mod.host.sendAll("swarm:all base set " + mod.host.getBaseAnchorString()); // :486
mod.host.sendAll("swarm:all " + cmd);                                // :610  (waypoint goto)
```
Task creation (host-side only):
```java
BotTask t = new BotTask(BotTask.Type.GATHER, it, qty);
mod.host.getTaskQueue().add(t);                 // add() saves
// with bot:
t.setAssignedBot(bot); queue.add(t); host.assignTask(bot, t);   // -> "swarm:<bot> task assign <uuid>|GATHER|<item>|<qty>|<bot>"
```
Deposit set stores locally then relies on push/connect to reach workers — the command itself sends nothing:
```java
mod.host.setDepositChest(cat, x, y, z);
updatePersistedCoords(mod, x, y, z);            // writes deposit-x/y/z settings
```

## Notes
- Every command asserts `mod.isHost()` except `exec` (falls back to local dispatch) and `task add` (host required).
- `waypoint goto` sends a raw `#goto`/`goto`-style command string, no waypoint coordinates are resolved host-side for workers
  (workers hold their own WaypointManager).
- `stock` reads `host.getStorageIndex()` — host-only, no worker round trip.
- 6 × `catch (Exception ignored)` in this file; `TARGET_SUGGESTIONS` reflection failure silently falls back to a hardcoded list.
