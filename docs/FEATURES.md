# Enhanced Swarm — feature status

This is the current description of what the addon does. It was rebuilt from the
deep-dive notes and checked against `src/` on `main` at `12dd6e6`. The notes
predate the fix stack; where they disagree, the code is the authority.

Two statuses only:

- **OBSERVED** — a person watched it work in game.
- **CODE ONLY** — it compiles and the source path exists, but nobody has watched
  it work in game.

No gameplay was launched for this document. Every gameplay row below is
`CODE ONLY`. The `/v2` panel is the one browser exception: it was opened on a
desktop browser and a phone, against fake players. Layout, tabs and the task
queue render. No bot has driven it in game yet. That is still not in-game
observation, so the row stays `CODE ONLY`.

## Mining and gathering

**What it does.** A host dispatches a `GATHER` task to a worker. `BotBrain`
accepts `GATHER`, `CRAFT` and `SMELT` in its idle state, builds a plan for
crafting/smelting through `DependencyResolver`, and enters `handleGathering`.
The dedicated gather path maps an item to a block with `MINE_FOR` and
`MINE_FOR_2` (for example `cobblestone -> stone`, `diamond -> diamond_ore`,
`raw_iron -> iron_ore`), then sends `#mine minecraft:<block>` through Baritone.
`handleGathering` watches inventory counts and stops Baritone when the quantity
is met. `armBreakWatch`/`tickBreakConfirmation` watch for the block becoming air
and for nearby server-side drops. `DropCollector` sweeps the drops separately.

**How you start it.** `/eswarm gather <item> <quantity>` (additive) or
`/eswarm ensure <item> <quantity>` or `ensure in <category> <item> <quantity>` (target level), the `/v2` Tasks tab,
or `POST /api/task/add` with `mode=gather|ensure` and an optional `scope`.
`type=auto` chooses `CRAFT` when `RecipeBook.hasRecipe` knows the item, otherwise
`GATHER`.

**What it needs.** A host, a connected worker with `BotBrain` active, Baritone
chat commands, a task whose item has a known source, and a usable tool when the
fallback path decides the target requires one. `gather-staging` moves the bot
away from the base before mining; `gather-staging-distance` and
`gather-staging-band` control that search. `collect-drops` must be enabled for
the final pickup sweep to produce completion evidence.

**What it breaks it.** Unknown items and items without a known source are
failed rather than queued (`/api/task/add` and `SwarmCommand` both check
`DependencyResolver.hasKnownSource`). The dedicated `GATHER` branch does not
perform the fallback path's `requiresTool` check, so a tool-required target can
be sent to `#mine` without a usable tool. That branch also has no inventory-progress deadline: it re-sends only when the block
changes and otherwise waits on inventory, so a mine that never produces items
can sit there. It does fail an air transition that cannot be confirmed within
two seconds (`unconfirmed break`), but a block that never breaks is only cleared
from the break watch and retried. The fallback mining path is different: a
tool-required target with no usable tool sets `STUCK`, and no inventory progress
for 30 seconds stops Baritone and fails the task with `no mining progress`
without reissuing the command. Staging candidates
are scored by floor and headroom, not proven reachable, and a failed goto is
bounded to 60 seconds with two reissues. The `MINE_FOR` alias list is small; a
block without an alias is mined by its item id.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/modules/BotBrain.java`
(`handleIdle`, `handleGathering`, `MINE_FOR`, `MINE_FOR_2`, `armBreakWatch`,
`tickBreakConfirmation`, `ensureStagedAwayFromBase`),
`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` (`dispatchNextTask`),
`src/main/java/com/rdh/swarm/task/DependencyResolver.java` (`resolve`,
`hasKnownSource`), `src/main/java/com/rdh/swarm/task/RecipeBook.java`
(`hasRecipe`).

## The task queue

**What it does.** `TaskQueue` is a `PriorityQueue` of `BotTask` records, with
`PENDING`, `ACTIVE`, `BLOCKED`, `COMPLETE`, `FAILED` and `CANCELLED` statuses.
`getNextPendingForBot` copies the queue and sorts by priority before choosing a
task, so priority dispatch is honored there. `EnhancedSwarmHost.dispatchNextTask`
allows one active task per bot, assigns an unassigned/`any`/`all` task to the
chosen bot, sets it `ACTIVE`, and sends the assignment. `cleanDead` calls
`requeueActiveForBot` when a worker disappears. `BotBrain` implements pause,
resume and cancel through its halt state; the queue keeps paused work and drops
a cancelled task. `/api/task/add` no longer calls `clearCompleted`; only
`/api/task/clear_done` prunes finished rows. Host-side cancel is
`/api/task/cancel {"id":"<uuid>"}` → `TaskQueue.cancelById` marks the row
`CANCELLED`, clears the lease and removes it, then sends `swarm:<bot> cancel` to
the owning worker. Verified live: `TASK: cancelled d617ac41-… owner=jamal` and
`HALT: cancelled d617ac41-…` with no lease renewal or re-dispatch afterwards
(`test-runs/live-smelt-day2/blast-furnace-and-cancel-trace.log`). Before the
fix the endpoint only sent `cancel`, so the `ACTIVE` row stayed queued and was
re-dispatched; the old workaround was stopping it worker-side.

**How you start it.** `/eswarm gather`, `/eswarm ensure`, `/eswarm task list`,
`/eswarm task cancel`, `pause`/`resume`, or the same controls in `/v2` and the
old panel. No command takes a bot name: the lease assigns the task. The queue is created by `EnhancedSwarmHost` and reloaded at host
construction.

**What it needs.** A host process, a writable
`meteor-client/enhanced-swarm/task-queue.json`, a connected worker for dispatch,
and an item with a known source for `GATHER`/`CRAFT` work. A bot with an active
task is not given another one.

**Completion is the outcome, not the plan.** `finishTaskWithEvidence` is the one
path that may mark a task `COMPLETE`, and it no longer trusts the plan's own
counter. For `GATHER`, `SMELT` and `CRAFT` it recomputes the result as
`countInInventory(target) + what this task actually put in a chest`, and when that
is short it logs `TASK: refused-complete <id> inventory=<have>/<need>
deposited=<landed>` and rewrites `collectedQuantity` to the truth so the brain
goes back to work instead of re-declaring the same win. When the items exist but
are still carried, the completion is deferred with `TASK: holding complete <id>
until the deposit lands`, the deposit runs, and the move is credited with
`TASK: ledger <id> += <n> <item> landed in chests` before `TASK: complete`. If the
deposit cannot land, the task completes on the inventory proof with a warning
instead of hanging. `BUILD`, `GRIND`, `SHULKER`, `SMITH` and `HUNT_FOOD` are not
measured this way — their output is not an inventory count.

That replaced a real lie: on 2026-11-13 the host printed `✓ jamal completed:
iron_ingot x12 in 2s` while the smelt had not started and finished 73 s later,
because a requeued task carried a stale `collectedQuantity` and `isComplete()`
read it as a win. Proof of the fix, one run, ordering visible in the timestamps:
`test-runs/live-smelt-day2/full-stack-deposit-and-completion.log` — 64 ore
withdrawn, `ingot total after=64 (before=0, delta=64)`, `holding complete`,
`DEPOSIT: complete moved=64`, `ledger += 64`, then `TASK: complete` and the host's
`✓` last, with chest totals 373 -> 501 (+64 per run).

**What it breaks it.** A lease, owner token and attempt counter fence every claim
(`TASK: claimed <id> attempt=N`, `lease renewed`, `lease expired … requeued`), and
`module-guard` still skips BotBrain, so a task can only run while the module is
active — a disabled BotBrain answers every claim with `BotBrain module is not
active` and the task churns through attempts. `load()`
restores `ACTIVE` rows as `ACTIVE`, and the host constructor does not requeue all
active rows; after a host restart a task can stay assigned to a worker that no
longer owns it, while `dispatchNextTask` sees `hasActiveTask` and refuses to give
it out. The enum also declares `EQUIP`, `DEPOSIT` and `SCAN_CHESTS`, but
`BotBrain.handleIdle` has no execution branch for those types. Both panels
omit them from the Add form, and `/api/task/add` rejects them before queueing;
an older saved row that still carries one reaches `failUnhandledTask`. Priority is honored by
`getNextPendingForBot`, not by the raw `PriorityQueue` iterator.

Status: **LIVE** — claims, leases, requeues and the outcome-completion gate were
all observed on 2026-11-13
(`test-runs/live-smelt-day2/full-stack-deposit-and-completion.log`). Sources:
`src/main/java/com/rdh/swarm/task/TaskQueue.java`
(`add`, `addTask`, `save`, `load`, `clearCompleted`, `getNextPendingForBot`,
`requeueActiveForBot`, `hasActiveTask`), `src/main/java/com/rdh/swarm/task/BotTask.java`
(`Type`, `Status`), `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`
(`dispatchNextTask`, `cleanDead`), `src/main/java/com/rdh/swarm/modules/BotBrain.java`
(`handleIdle`, `cancel`, `pause`, `resume`).

## Deposits and chests

**What it does.** `DepositConfig` keeps per-server, per-category lists of chest
positions and a base anchor, with a monotonically increasing generation.
`/eswarm deposit set` records a chest and `pushDepositConfigAll` sends a
`deposit reset <generation>`, one `deposit set` per chest, and
`deposit commit`; the worker replaces its list atomically and logs
`CHEST: list replaced`. `DepositDriver` is the tick-owned transfer path:
`IDLE -> OPENING -> VERIFYING -> TRANSFERRING -> CLOSING -> DONE/FAILED`, one
container action per tick. It checks the container type, the block still at the
target, the observed `containerId`, and counts an observed inventory delta before
publishing `CHEST_DELTA`; a no-op is not a success. `StorageIndex` stores chest
counts and categories. `scan` walks configured chests, reads slots, and sends
`SCAN_CHEST_RESULT`. `WITHDRAW_REQUEST` is handled by the host connection and
`handleWithdraw` on the worker.

**How you start it.** `/eswarm deposit set [category] [x y z]`,
`/eswarm deposit push`, `/eswarm deposit auto on|off`,
`/eswarm deposit threshold <n>`, `/eswarm scan [category]`, or the panel's
deposit actions. `auto-deposit` in `EnhancedSwarm` and `BotBrain.shouldDeposit`
trigger the same `startDeposit` entry point when inventory is full or nearly
full; `/eswarm deposit` remains the manual path.

**What it will not deposit, and why that is now visible.** `keepReason` protects a
fixed keep list (food, coal/charcoal, totems, pearls, elytra, fireworks) plus item
suffixes for tools and armour, plus material prefixes (`minecraft:iron_`,
`minecraft:diamond_`, `minecraft:golden_`, `minecraft:netherite_`,
`minecraft:stone_`, `minecraft:wooden_`). The prefix rule exists for gear, but as
a prefix it also caught the bare resource: `minecraft:iron_ingot` starts with
`minecraft:iron_`, so every smelted ingot was kept forever — a stack and 14 iron
ingots sat in the inventory and were never deposited, with nothing in the log to
say so. Ingots and nuggets now bypass the prefix rule (they are task output, not
gear) and every deposit announces both halves of its plan:
`DEPOSIT: moving minecraft:iron_ingot=12` and
`DEPOSIT: keeping minecraft:diamond_sword=1 (tool)`. Anything else that matches a
prefix (blocks, ore, stone bricks, diamond_block) is still kept — that part of the
list is unchanged and deliberate. Live proof, smelt through to chest:
`test-runs/live-smelt-day2/iron-deposit-proof.log` — 12 ore withdrawn, smelted
(`ore into furnace=12, ingot total before=0` → `ingot total after=12 … delta=12`),
`DEPOSIT: step=1 moved=12`, `moved=14 … success=true` (12 ingots plus a cobble
and a furnace), chest `[-549 91 598]` then held `minecraft:iron_ingot=12`, and the
next deposit reported `DEPOSIT: moving nothing`.

**The host player's own inventory.** Deposits used to drive workers only: the
panel's deposit action is relayed to bots, so an item in the host player's
inventory was never eligible for anything, however the keep list was written.
`EnhancedSwarm.depositSelf(reason)` now runs a `DepositDriver` over the local
player with the host's own chest list (`EnhancedSwarmHost.currentDepositChests()`)
and the same keep rules, so both inventories behave identically. It is wired to
`/api/action/deposit_self` and `/eswarm deposit self`, announces
`Self-deposit: starting over N chest group(s)` and the same
`DEPOSIT: moving … / keeping …` plan, and must run on the client thread (it logs
and reads player state). Refusals are explicit: no chest configured, not the
host, or no player in a world. `/api/action/deposit` still relays to bots only,
so it does not touch the host inventory.

Proven live (`test-runs/live-smelt-day2/player-deposit-proof.log`): 12 ingots in
the player's inventory gave `DEPOSIT: moving minecraft:iron_ingot=12`,
`DEPOSIT: step=1 moved=12 stacks=1 menu=ChestMenu`,
`DEPOSIT: complete moved=12 stacks=1 delta={minecraft:iron_ingot=12}`, the
destination chest went 12 -> 24 (+12, exactly the plan), and the next plan said
`DEPOSIT: moving nothing`. The bot path on the same build moved its own 2 ingots
(`moved=2 … delta={minecraft:iron_ingot=2}`) and that chest went 24 -> 26.

**The test fixture.** The host player has no task and no worker, and the panel's
commands are relayed to workers, so there was no way to put a known stack in that
inventory. `/api/action/test_give {"item","count"}` sends a vanilla `/give` for the
local player (`TEST FIXTURE: /give @s …`); it is test setup, not a survival path,
the server's permissions decide, and no task calls it.

**Where the 64 + 14 went** (`test-runs/live-smelt-day2/ingot-location-answer.log`).
On this build neither inventory holds iron ingots (`DEPOSIT: moving nothing` on
the host, and on the bot after its own deposit), and the chests held 12 at
`-549 91 598` and 87 at `-550 91 599`. 64 + 14 = 78, so that chest's 87 misses a
raw match by 9; the logs only account for 78 -> 80 -> 86 across two earlier smelts
(2 and 6), leaving one unattributed ingot. The 64/14 slot split cannot be checked
inside the 87 because the scan reports a chest as an aggregate, and this deposit
path cannot have put them there — `minecraft:iron_` has been in `KEEP_PREFIXES`
since commit `24e8f79` (2026-11-07), so no deposit moved an ingot before the
exemption. Stowed by hand is the only remaining explanation, and that is a
deduction about the path, not a claim about who did it.

**What it needs.** At least one chest configured for the current server, a safe
standing position, a container block that is still there when the menu opens, an
empty player menu before opening, and items that match the category. Withdraw
also needs a `StorageIndex` entry and a worker that can path to the chest.

**What it breaks it.** No chest gives `DEPOSIT: refused reason=no-target`. An
already-open menu, a changed block, a different container id or a mismatched menu
gives `menu-mismatch`; a failed transfer gives `timeout`, `no-progress`,
`step-cap` or `click-failed`. The `/v2` Scan Chests button is disabled and sends no request; calling
`/api/action/scan_chests` directly does send `swarm:all scan`. The `scan`
worker path is synchronous: it can wait up to 60 seconds for
a goto and 10 seconds for a menu, so it blocks that worker thread. Storage
counts are only what the last scan or observed delta reported; there is no TTL
or periodic refresh. The host's `WITHDRAW_REQUEST` handler and the worker's
`handleWithdraw` path are separate from the deposit driver; a withdraw that never
lands leaves the worker in `WITHDRAWING` until its timeout.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/DepositConfig.java`
(`save`, `load`, `addChest`, `getGeneration`, `matchesCategory`),
`src/main/java/com/rdh/swarm/DepositDriver.java` (`request`, `tick`,
`tickOpening`, `tickVerifying`, `tickTransferring`, `afterClose`),
`src/main/java/com/rdh/swarm/EnhancedSwarmHost.java` (`setDepositChest`,
`pushDepositConfigAll`), `src/main/java/com/rdh/swarm/EnhancedSwarmWorker.java`
(`handleDeposit`, `startDeposit`, `handleScan`, `handleWithdraw`),
`src/main/java/com/rdh/swarm/EnhancedSwarmConnection.java`
(`WITHDRAW_REQUEST` branch), `src/main/java/com/rdh/swarm/StorageIndex.java`
(`applyChestResult`, `applyChestDelta`, `save`, `load`),
`src/main/java/com/rdh/swarm/modules/BotBrain.java` (`handleWithdrawing`,
`startDeposit`).

## Fuel

**What it does.** `SmeltingManager` resolves a fuel through the vanilla
`DataComponents.COOKING_FUEL` component and `ResolvableInt.getFromItem(...,
CookingFuel::burnTime, ...)`. It counts fuel in the player inventory, deposits
fuel into furnace slot 1, checks whether the furnace is lit or has fuel, and
takes the result. `BotBrain` runs the furnace states: scan for a furnace (or
place/craft one), walk to it, open it, insert fuel and ore, wait, refuel if it
runs dry, collect, and verify the result. `maybeQueueCooking` can queue cooking
for raw food when a furnace and fuel are nearby.

**How you start it.** Queue a `SMELT` task, queue a `CRAFT` task whose plan
contains a smelt step, or enable `auto-cook` and let `maybeQueueCooking` create
the cooking task. The panel's Quick Action `Check Fuel` is not a fuel check:
`SwarmWebHandler` answers `/api/action/check_fuel` with HTTP 501 and
`check_fuel unavailable: container reader not implemented`.

**What it needs.** An appliance found by the bot's own scan (smoker preferred for
food, blast furnace for ore work), the ore/raw food in the inventory, and fuel
that is either in the inventory or already sitting in the appliance.

**Debug visibility.** `BotBrain.debugStateJson()` publishes `fuels[]` and
`fuelTicks` in `/api/debug/bot` (`bot_state`). Each entry is
`{id,count,ticksPerItem,ticks,resolved,reason}` and is resolved through the same
`SmeltingManager.fuelValue()` path as the cook decision. The list is
**inventory-only**; a furnace's own fuel slot is separate and readable only while
its menu is open. Unresolved fuel is reported with `resolved:false`, `ticks:0`,
and the resolver's reason instead of being counted as usable. This makes the
preflight fuel check possible without opening a furnace. The fields are
`CODE ONLY` as of 2026-11-14; they have not been read from a live bot yet.

**Multiplayer.** Block-entity slots are not synced to a client, so a stocked
furnace looks empty until its menu is open. `BotBrain.peekApplianceFuel` opens
the appliance, reads all three slots through the menu
(`SmeltingManager.readOpenAppliance`), caches the answer for a minute, and the
cook decision uses it (`G2: appliance read [...] in=… fuel=… out=…`). Burn time
falls back to published vanilla values (`estimatedBurnTicks`) when
`SmeltingManager.serverLevel()` is null, which it always is on a multiplayer
client. Cooking was previously blocked there with
`no server level (multiplayer client)`; it now runs — live proof:
`FUEL: minecraft:coal x64 = 102400 ticks in furnace` then
`SMELT: complete, have 3/3 cooked_beef`, and `SMELT: using Smoker at -551 91 605`
then `SMELT: complete, have 2/2 cooked_chicken`.

**What it breaks it.** No furnace and fewer than eight cobblestone leaves the
state at `STUCK`. No fuel produces `FUEL: none` or `FUEL: ... unresolvable` and
stops the deposit. An input that never empties times out after ten minutes with
`SMELT: wait timed out after 10 minutes`. A result that is below the requested
quantity leaves the bot `STUCK` rather than claiming completion. The
`check_fuel` endpoint is a 501 stub, so it cannot tell you whether fuel exists.

Status: **LIVE — furnace, smoker and blast furnace all verified in a
multiplayer session** (2026-11-12). Cook proof:
`test-runs/live-smelt-day2/cook-live-trace.log`; ore proof:
`test-runs/live-smelt-day2/blast-furnace-and-cancel-trace.log`. The blast-furnace
ore run works end to end: the storage query returns the host index count and the
chest it came from (`[STORAGE QUERY] server=… req=minecraft:raw_iron:3 ->
STORAGE_QUERY_RESPONSE:minecraft:raw_iron:4:-549,91,598=4`), the gather step
reports that source (`Planner: minecraft:raw_iron x4 in base storage, withdrawing
4 from -549,91,598=4`) and advances the plan,
and the bot picks the appliance by name (`SMELT: using Blast Furnace at -551 91
606`), inserts its own coal, waits and collects (`SMELT: complete, have 1/1
iron_ingot`). Things that used to block it and are now fixed: a smeltable target
queued as a `GATHER` job (it "completed" 1/1 without smelting), a satisfied
furnace/fuel gather step that never advanced the plan, a deposit trip that ran
while a smelt step was still waiting for its ore, and a smelt state that failed
from far away instead of walking back to the base appliance.

**The furnace that would not open.** The open phase used to retry the same interact
from wherever the bot stood. At the edge of reach (4.0 blocks) that never lands,
so it spent 100 attempts and 25 s and failed the task `reason=stuck` on a furnace
it could see; the same furnace opened fine at 1.0 blocks. It now re-paths onto the
block (`#goto <furnace>`) when the first attempts fail
(`SMELT: furnace did not open from here (attempt 20), stepping closer`) before the
100-tick timeout, and still fails `STUCK` if that does not help.

**Storage query wire format.** `STORAGE_QUERY_REQUEST:<id>:<need>` per item;
the host replies `STORAGE_QUERY_RESPONSE:<id>:<count>:<x,y,z>=count;...`, items
joined by `#`. The trailing field is `StorageIndex.getBreakdown`, so a total is
attributable to a container and a legacy `id:count` reply still parses. The
gather step now queries whenever it has no answer for the item (not only every
100th tick) and refuses to `#mine` until a reply lands, so it withdraws ore the
base already holds instead of mining more.

**Withdraw.** `WITHDRAW_REQUEST:<itemId>|<needed>|<category>` — '|' because the id
is namespace-qualified and the old `id:needed` form split on ':' gave the host
`minecraft:minecraft`, which matched no chest and dispatched nothing. The host
answers `[WITHDRAW SEND] -> (x,y,z) taking N of <id>` and the worker verifies each
shift-click against the menu before counting it (`Withdraw: slot 21 click moved
nothing (64 -> 64)`); a click that the server ignores no longer reports success.
`handleWithdraw` refuses to run on a furnace/crafting menu, which used to pass the
"chest opened" check and shift-click the blast furnace's own input slot. On
`WITHDRAW_RESULT` the host debits the index (`[WITHDRAW DONE] … moved=1 index 1 ->
0`) and the worker drops that item from the bot's cached reply so the next planner
tick re-queries instead of re-reading a stale number. Live proof of the chain,
including the count stepping 3 → 2 → 1 at `-549,91,598=…` and the bot then
holding the ore (`Gathering raw_iron: 1/1`):
`test-runs/live-smelt-day2/withdraw-chain-trace.log`.

**One withdrawal per task.** A task may dispatch at most one withdrawal for a
given item, however many plan steps or re-plans it goes through. `withdrawTaskId`
+ `withdrawTaskItem` record it when the request is sent; a later planner tick that
still wants that item logs `already withdrawn for this task; no second withdrawal,
gathering the rest` and mines the shortfall instead of asking again.
`withdrawInFlightItem` additionally keeps the step from re-dispatching while the
transfer is walking to the chest (a bare 5 s throttle used to re-fire mid-transfer
and pull several ores for a one-ore step). A dispatch is skipped entirely while
the bot has another menu open, because the worker refuses a chest at that point
and the refusal would spend the task's one withdrawal for nothing
(`WITHDRAW_REFUSED` is still reported if it happens). A take that is dispatched but
delivers nothing does not keep the guard either: when the in-flight window times
out with the inventory no higher than when the request went out, the guard is
cleared (`Withdraw: <item> attempt produced nothing; allowing the task to retry`).
That mattered live — a chest that never opened spent the task's one withdrawal,
after which every plan tick said `already withdrawn for this task` forever and the
task mined for ore the storage index still listed, until it failed `reason=stuck`.
The amount dispatched is the deficit:
the plan is resolved for `target - already held` (`Plan deficit: iron_ingot x2
(task needs 50, have 48)`) instead of the task's gross quantity, which is what
used to plan 3 ores for one missing ingot. Live: one `WITHDRAW SEND` of 2, one
`WITHDRAW DONE … moved=2 index 4 -> 2`, zero further sends for that task —
`test-runs/live-smelt-day2/one-withdraw-per-task-trace.log`.

**The take is exact, and the landing is verified against the bot's inventory,
not the chest.** A shift-click moves a whole stack, so a slot larger than the
need is not shift-clicked: the bot picks the stack up, right-clicks the needed
singles into a player slot, then puts the remainder back into the same chest
slot (`Withdraw: exact take 2 from a 4 stack (slot 42 -> 54)`). A slot that
holds no more than the need is still shift-clicked whole, which is exact by
definition. Either way each click is checked by reading the bot inventory by
item id and count (`Withdraw: slot 42 took 2 (inventory 0 -> 2)`), and after the
menu closes a bounded wait re-reads the inventory (`Withdraw: inventory holds
2/2 minecraft:raw_iron after transfer`). What the inventory does not hold is not
counted and not debited, so `[WITHDRAW DONE] … moved=N` always equals what the
bot received. Live: a task needing 2 from a 4-ore slot took 2, the inventory
went 0 → 2, `moved=2 index 4 -> 2`, and the smelt finished at exactly the
planned quantity (78 + 2 = 80, task target 80). When the chest holds less than
the need the bot takes what exists and mines the rest, still without a second
withdrawal in the same task: needing 6 with 2 in the chest gave `moved=2 index 2
-> 0`, `already withdrawn for this task; no second withdrawal, gathering the
rest`, and a smelt of `ore into furnace=6` (2 withdrawn + 4 mined, 80 + 6 = 86,
task target 86) — `test-runs/live-smelt-day2/chest-to-smelt-exact.log`. The
appliance scan will not fall back to a smoker for a non-food smelt (a smoker
rejects ore, so that fallback stalled the task until it was removed).
The smelt step logs the two quantities
that make the output checkable: `SMELT: ore into furnace=6, ingot total
before=72` at insert and `SMELT: ingot total after=78 (before=72, delta=6, ore
in furnace=6)` after collection, so ore-in, ingot delta and the debit all read
as the same number. The earliest full chain (kept for history, it still
shift-clicked whole stacks and therefore over-delivered) —
`test-runs/live-smelt-day2/chest-to-smelt-complete.log`: one `WITHDRAW SEND`,
`raw_iron` 0 → 6 in the inventory by item id and count, `ore into furnace=6`,
`ingot total after=78 (before=72, delta=6 …)`, task completed, no `reason=stuck`.
Caveats that still hold: the exact take is several clicks (pickup, one
right-click per item, return the rest) at roughly 120 ms each, so a large take
from a big stack is slower than a shift-click, and a menu closing mid-take can
leave it partly done — the inventory readback is what decides the debit, so a
partial take is debited as a partial take. Cancelling a task after the ore has
been inserted leaves that ore inside the appliance; it is not reclaimed. And the
task report's `xN` is the task target while `have A/B` is held/step-need, which
are different quantities.
Still not clean: the chest's menu-read count can rise during the same task because
the bot keeps mining and depositing in parallel, so a single before/after scan
does not isolate the withdrawn amount; the index-level debit plus the item
appearing in the bot's inventory is the evidence that holds. Also still true:
the plan can only withdraw what the index claims, and an index entry that goes
stale is now cleared on a zero-move withdrawal, which costs one extra pass
before the bot mines that item itself. Sources:
`src/main/java/com/rdh/swarm/task/SmeltingManager.java` (`fuelValue`,
`estimatedBurnTicks`, `readOpenAppliance`, `buildFuelContext`, `serverLevel`,
`depositFuel`, `hasFuel`, `countFuel`, `fuelStatus`),
`src/main/java/com/rdh/swarm/modules/BotBrain.java` (`peekApplianceFuel`,
`findSmelter`, `maybeQueueCooking`, `hasCookFuel`), and
`src/main/java/com/rdh/swarm/web/SwarmWebHandler.java` (`/api/action/check_fuel`).

## Item pickup

**What it does.** `DropCollector` is a bounded sweep, not a general item vacuum.
It is enabled by `collect-drops`, uses `pickup-radius` and `pickup-wait-ms`,
scans `ItemEntity` clusters near the player, cancels Baritone mining, paths to a
cluster, waits for vanilla pickup, and blacklists unsafe or unreachable clusters
for 30 seconds. `BotBrain` ticks the collector before its own state machine.
Before a task is marked complete, `finishTaskWithEvidence` calls
`startFinalSweep`; only `EVIDENCED` or `NOTHING_TO_COLLECT` allow completion.
`UNPROVEN` gets one retry and then fails the task with
`reason=unproven-completion`.

**How you start it.** It runs automatically while `collect-drops` is true, after
a block break or kill that the brain observes, and as the final sweep for a task.
There is no separate pickup command.

**What it needs.** `collect-drops` enabled, Baritone pathing, item entities in
range, a safe non-fluid floor with headroom, and inventory space. The final sweep
also needs the task to have a valid id and the collector to be idle.

**What it breaks it.** With `collect-drops` off, `startFinalSweep` returns
`UNPROVEN` and the task fails after one retry. A full inventory logs
`DROPS: inventory full` and skips the sweep. Unsafe clusters are blacklisted with
`unsafe: ...`; unreachable clusters are blacklisted for 30 seconds. The final
sweep only proves that at least one item was collected, not that the task's
specific item count was observed. `completeShulker` uses `shulkerMovedAny`, set
when a shift-click is issued, rather than a measured inventory delta.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/DropCollector.java`
(`startWorkUnit`, `startFinalSweep`, `tick`, `scan`, `startPath`, `finish`),
`src/main/java/com/rdh/swarm/modules/BotBrain.java` (`onTick`,
`finishTaskWithEvidence`, `completeShulker`).

## The panel (`/v2` and the old `/`)

**What it does.** `SwarmWebServer` binds the configured `bind-address` and
`web-port` (default `127.0.0.1:8888`) and broadcasts state every two seconds.
Every HTTP and WebSocket request must carry the shared token in `X-Swarm-Token`,
`?token=`, or the `swarm_token` cookie; the comparison uses
`MessageDigest.isEqual`. A request with an `Origin` header is accepted only when
it is exactly `http://<Host>`; the panel sets `HttpOnly; SameSite=Strict`, a CSP,
and other security headers. `SwarmWebHandler` serves `/` and `/app.js` from the
inline v1 `SwarmWebPanel`/`SwarmWebPanelScript`, `/v2` and `/v2/app.css`/`app.js`
from `web/v2`, `/api/items` from the registry cache, the POST API, and `/ws`.
`/v2` adds tabs, a status strip, keyed task/bot updates, the log controls and the
phone layout. The old `/` is still the v1 inline page and is not being redesigned.

**How you start it.** Enable the host module; the chat log prints
`Web panel: http://<host>:<port>/?token=<token>`. Open `/` or `/v2` in a browser.
The WebSocket is `ws://<host>/ws`.

**What it needs.** A running host, a reachable bind address and port, the shared
token (or the generated session token), and a same-origin browser request.

**What it breaks it.** A missing or wrong token is 401; a foreign `Origin` is
403. `/api/action/check_fuel` is 501. `/api/action/scan_chests` sends
`swarm:all scan` if called directly, but the `/v2` button is disabled and
sends nothing. `/api/items` returns every
registry id, including `minecraft:air`. There is no TLS, and the token is placed
in the query string and printed to chat. Exposing the panel beyond loopback is a
red line. The panel was not opened against a real Minecraft host in this pass, so
the Java server's live auth/WS behavior is not part of the browser evidence.

Status: **CODE ONLY**. `/v2` has been opened on a desktop browser and a phone,
against fake players. Layout, tabs and the task queue all render. No bot has
driven it in game yet. The old `/` was not opened in this pass. Sources:
`src/main/java/com/rdh/swarm/web/SwarmWebServer.java` (`isAuthorized`,
`isOriginAllowed`, `buildStateJson`, `handleApiCommand`),
`src/main/java/com/rdh/swarm/web/SwarmWebHandler.java` (`handleHttp`,
`handleWebSocket`), `src/main/java/com/rdh/swarm/web/SwarmWebPanel.java`,
`src/main/java/com/rdh/swarm/web/SwarmWebPanelV2.java`,
`src/main/resources/web/v2/index.html`, `src/main/resources/web/v2/app.js`,
`src/main/resources/web/v2/app.css`.

## Task modes: gather and ensure (2026-11-14)

**What it does.** A queued task now says which question it answers.

- `gather` is additive. The task moves its own quantity and deposits it; base stock is
  irrelevant to whether it is done, and it never withdraws from storage or closes on a
  chest count. `gather oak_log 64` with 400 oak_log in a chest still mines and deposits 64.
- `ensure` is target-level. The host measures the base when the task is created and
  queues only the shortfall: `ensure cooked_beef 200` with 137 stored queues 63, and with
  200 stored it queues nothing and records `already-in-stock` on the spot. It refuses a
  reading older than 30 s rather than guess, and completes only against a reading taken
  after its own deposit — or fails `ensure-undergather`.

**Why.** Both old behaviours were the same bug from two sides. A top-level `GATHER` could
close on stock, so `gather 3 birch` finished in 3 s with 16 logs in a chest and nothing
moved; and if it did not close it withdrew the stock and deposited it back, which is net
zero movement reported as progress. Stock can now only feed a *plan step* (a craft asking
for material the base owns); `BotBrain.handleGathering` skips both paths for `GATHER`.

**Grammar.** `ensure <item> <qty>` targets the whole base; `ensure in <category> <item> <qty>`
targets one category. `in` is a literal so the category slot is unambiguous — with the old
two-shape form the first token could be an item or a category and no suggestion list could
know which slot was being typed.

**Autofill.** Every argument that can be known is suggested from live data: `item` lists what
the base holds first (with the count) then the registry; `quantity` on ensure offers
`have + 64/128/256/512` with the shortfall in the tooltip; `category` lists the categories this
base actually has chests for; `task cancel` lists live task ids with their progress; `x y z`
suggests your block position; waypoint names, deposit indices and on/off come from their own
sources.

**Scope.** `ensure in <category> <item> <qty>` counts only chests registered under that
deposit category. Categories are item-class filters (`food`, `tools`, `all`), not places:
beef sitting in an `all` chest is invisible to `ensure food beef`, which is why named
chest zones are the planned follow-up rather than the final answer.

**What it needs.** For ensure, a chest reading within 30 s — of the item when the count is
non-zero, of any in-scope chest when it is zero. Older readings are refused with their age
instead of being trusted.

**Completion is measured, not counted.** An ensure does not close on its own ledger: before
`COMPLETE` it re-reads the base, waits up to 45 s for a fresh answer, and fails
`ensure-undergather <have>/<target>` if the base is short. Overshoot is accepted by design;
finishing below the target is the one outcome that is never reported as success.

**What it breaks it.** Tree mining overshoots: `ensure birch_log 21` with 20 stored mined
one tree and the base ended at 29. Ensure guarantees *at least* the target, not an exact
count. The persistence fields (`mode`, `ensureTarget`, `ensureBaseline`) live in
`task-queue.json`, so a restart keeps the distinction.

---

## Commands and keybinds

**What it does.** `EnhancedSwarmAddon` registers `/eswarm` and the alias
`/swarm`. `SwarmCommand.build` defines `cancel`/`stop`/`resume`, `exec`, `list`,
`send`, `gather`, `ensure`, `task cancel|pause|resume|list`, `deposit set|remove|list|auto|
threshold|push`, `scan`, `stock`, `base set|clear|show|push`, `dumpstorage`,
`waypoint add|portal|list|remove|goto|push`, and attaches `IdsCommand`. Most
commands require `mod.isHost()`; `exec` falls back to local dispatch when the
client is not hosting. There is no addon-wide keybind. The only keybind setting
in the source is `SwarmHoleFiller.forceFill`; ordinary Meteor module keybinds
still toggle modules.

**How you start it.** Type `/eswarm <command>` (or `/swarm <command>`) in chat.
Use Meteor's module keybind system for module toggles; `force-fill` is the one
addon setting that is explicitly a keybind.

**What it needs.** A host for host-only commands. `exec` needs either a host
connection or a local `BotBrain`/Meteor command dispatcher. `send` needs a known
worker name, `all`, or a nickname that resolves to a worker. Commands that create
tasks need a known item source.

**What it breaks it.** A command run on a worker with no host reports
`Not hosting`. `gather` rejects unknown items and items with no known source;
`ensure` refuses when the last chest reading is older than 30 s.
`waypoint push` only prints a message; it does not serialize or send waypoints,
despite being in the tree. `waypoint goto` sends one `exec #goto` string and does
not confirm arrival. There is no `grind`, `get`, `sort`, or `build` command.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/EnhancedSwarmAddon.java`
(`Commands.add(new SwarmCommand())`, `Commands.add(new SwarmCommand("swarm"))`),
`src/main/java/com/rdh/swarm/commands/SwarmCommand.java` (`build`, `haltAll`,
`resumeAll`, `setDepositFromCrosshair`), `src/main/java/com/rdh/swarm/modules/SwarmHoleFiller.java`
(`forceFill`), `src/main/java/com/rdh/swarm/EnhancedSwarm.java` (`onActivate`,
`onDeactivate`).

## Persistence across restart

**What it does.** `TaskQueue` writes
`meteor-client/enhanced-swarm/task-queue.json` on add, remove, nickname,
requeue and clear, and loads it in its constructor. `DepositConfig` writes
`meteor-client/enhanced-swarm/deposit-chests.json` with per-server chest lists,
base anchors and generations; the host loads it when the TCP server starts.
`StorageIndex` writes `meteor-client/enhanced-swarm/storage-index.json` and loads
it in its constructor. `WaypointManager` writes
`<gameDirectory>/enhanced-swarm/waypoints/<server>/waypoints.json` lazily, keyed
by the sanitized server address. Meteor persists the module settings, including
`auto-deposit`, the deposit/base coordinate settings and other module settings.
`DeathTracker` does not persist: `DeathTracker.load()` is an intentional no-op,
so death positions and dimensions are memory-only. `WorkerConfig` has no file.

**How you start it.** No command: `EnhancedSwarmHost` constructs `TaskQueue`,
`DepositConfig` and `StorageIndex`, and `EnhancedSwarm` constructs the host on
module activation or game join. `WaypointManager` loads on first use.

**What it needs.** A writable game directory, the same server address for
per-server keys, and the same Meteor config directory. A restart does not
preserve a worker's in-memory `WorkerConfig` or `DeathTracker` records.

**What it breaks it.** `TaskQueue.load()` skips `COMPLETE`, `FAILED` and
`CANCELLED`, but keeps `ACTIVE`; the host does not requeue all active tasks on
startup, so a restart can strand a task as described above. `StorageIndex` trusts
the last reported counts and has no expiry. `DeathTracker` loses the record on
restart. `TaskQueue.load()` re-resolves the item id; an unknown id loads with a
null item. `WaypointManager` keys by the sanitized server address, so a changed
address is a different waypoint file.

Status: **CODE ONLY**. Sources: `src/main/java/com/rdh/swarm/task/TaskQueue.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/DepositConfig.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/StorageIndex.java`
(`CONFIG_FILE`, `save`, `load`), `src/main/java/com/rdh/swarm/WaypointManager.java`
(`getWaypointsFile`, `load`, `save`, `ensureLoaded`),
`src/main/java/com/rdh/swarm/task/DeathTracker.java` (`load`),
`src/main/java/com/rdh/swarm/WorkerConfig.java`, `src/main/java/com/rdh/swarm/EnhancedSwarmHost.java`
(constructor).

## The base registry (A3/A4/A5, 2026-11-13)

`BaseRegistry` (`src/main/java/com/rdh/swarm/BaseRegistry.java`) is the single place that
knows where the base stores and smelts. Coordinates are data on disk
(`meteor-client/enhanced-swarm/base-registry.json`), not literals on the observation path:
a grep for `-549`/`-550`/`-551` outside that class returns nothing.

- **Entries:** 12 - `chest-main` (double, twin recorded), `chest-full`, two spares, and
  eight furnaces (`bf1`-`bf3`, `smoker1`-`smoker3`, `furnace1`-`furnace2`). Every entry
  carries the measurement that seeded it in `source`.
- **Readable:** `POST /api/debug/registry`, and the `base_registry` MCP tool.
- **Id-addressed reads:** `world_container {id: "bf1"}` resolves position and kind from
  the registry and opens the container; no coordinates are passed by the caller.
- **Registry-driven index view:** `/api/debug/storage` now reports `registryChests` and
  `registryFurnaces` alongside the totals.

**Proof (04:29-04:33).** Boot 1 seeded the file (`loadedFromDisk=false`, 2708 bytes).
Both instances were then stopped, the jar redeployed (`md5 89a60bf`, later `d564d2d`,
identical on both), and restarted: `loadedFromDisk=true`, `count=12`, same `seededAt`,
**no re-seeding**. After that restart:

| check | value |
|---|---|
| `/api/debug/storage` iron | **832**, breakdown `-549,91,598=64; -550,91,601=768` |
| `world_container {id:bf1}` | `BlastFurnaceBlockEntity`, `coal x42` |
| `world_container {id:chest-main}` | `ChestBlockEntity`, **768 iron**, 32/54 slots |
| `swarm_preflight` | **pass=true, failed=[]** (11 checks) |
| selftest | **20 passed, 0 failed** |

## B1 operator and diagnostic tools (2026-11-13)

Five tools, each an MCP tool backed by a `/api/debug/*` endpoint on the host:

| tool | endpoint | what it returns |
|---|---|---|
| `log_tail` | `POST /api/debug/logs` | real tail of that instance's log, filtered, timestamped |
| `task_watch` | `POST /api/debug/tasks` | live `TaskQueue` state; waits for a terminal status |
| `chest_delta` | `POST /api/debug/chest` | registry chest by id, opened; per-item delta since the last read |
| `server_restart` | `POST /api/debug/health` for readiness | stop by pid, relaunch, wait for a new pid to answer |
| `panic` | `POST /api/debug/panic` / `resume` | clears the queue, stops dispatch, tells bots to abandon paths |

`panic` is a host-side switch (`isPanicked`) consulted by `dispatchNextTask`, deliberately
separate from each bot's brain toggle: resuming does not re-enable a brain an operator
turned off. **Resume:** `panic {resume:true}` or `POST /api/debug/resume`.

**Live evidence (04:50-05:05).** `log_tail bot` returned real BotBrain HALT lines and
`log_tail main` returned the host log; `chest_delta` on `chest-main` read 768 iron, then
after `give 5` + `deposit_self` reported `delta={'iron_ingot': 5}` with 768 -> 773 and
32 -> 33 slots; `panic` left `panickedAfter=true` with the brain still on, `resume` cleared
it; `server_restart bot` killed pid 21836, relaunched, and reported
`ok=true newPid=26196 bootMs=20655 readyBy=log`.

Two limits worth writing down: a **worker instance serves no debug panel of its own**, so
`log_tail` falls back to reading that instance's log file and `server_restart` judges worker
readiness from a new pid plus a fresh `Initializing Enhanced Swarm`/join line rather than
`/api/debug/health`. `mc_command` refuses anything off its safe list (`/item replace` was
rejected), which is why the chest test used the real deposit path.

## The task loop actually completes (2026-11-13)

A queued task now runs to a verified end on the live test server:

```
ensure minecraft:cobblestone 32        (host measures the base, queues only the shortfall)
  -> bot mines, deposits, host ledger records the evidence
TASK: ledger de521082 += 32 minecraft:cobblestone landed in chests
TASK: complete de521082 evidence=verified-empty      (progress 32/32)
storage cobblestone 1646 -> 1678, chest-main 519 -> 551
```

Six defects blocked that before today, all fixed and deployed: the deposit chest list
was never seeded from the base registry (workers answered "No deposit chest for
category"); a stale hand-typed chest coordinate shadowed a real one and failed whole
runs; a category request did not fall back to the base's general `all` pile; a chest
left open made every later chest fail the menu guard; the open interaction was tried
once so one lost packet aborted the run; and a run that reached every chest with only
keep-list items reported `failed`, so the gate retried forever.

**Honest limit:** a deposit with nothing movable is now reported as
`DEPOSIT: nothing to deposit (keep-list items only)` and counts as complete rather than
retrying. `deposit` with no chest still fails loudly.

## Two honesty bugs found by running tasks (2026-11-13, fixed)

Running real work surfaced two completions that were theatre, both fixed:

1. **Withdraw-and-return counts as gathering.** A `gather 64 oak_log` task found 64
   oak_log already in the chests, withdrew them, counted the withdrawal as gathering,
   deposited them back into the same chest and printed `COMPLETE` with a ledger entry.
   Net movement: zero. *Superseded 2026-11-14:* closing on stock was itself dishonest for
   an additive gather, so the stock branch was removed entirely — see "Task modes" above.
2. **A requeued task was never re-dispatched.** Every dispatch trigger was an event, so
   after a lease expiry the task sat `PENDING` while the bot idled, with no failure to
   explain it. The maintenance sweep now hands pending work to workers that report
   themselves idle.

**Live after the fix:** `gather oak_log 64` (stocked) -> `COMPLETE evidence=already-in-stock`,
nothing moved; `gather andesite 200` (56 short) -> mined, deposited, `DEPOSIT: complete
moved=215`, `ledger += 200 minecraft:andesite landed in chests`, `COMPLETE
evidence=verified-empty`.

## Status table

| Feature | Status | Where it lives |
|---|---|---|
| Mining and gathering | `DONE` (live 2026-11-13) | `BotBrain.handleGathering`; `EnhancedSwarmHost.dispatchNextTask`; `TaskQueue` |
| Task queue | `DONE` (live 2026-11-13 — claims, leases, requeues, outcome gate) | `TaskQueue`; `EnhancedSwarmHost.dispatchNextTask`; `BotBrain` halt state |
| Deposits and chests | `DONE` (live 2026-11-13 — chest totals +78 verified by scan) | `DepositDriver`; `DepositConfig`; `StorageIndex`; `EnhancedSwarmWorker.handleScan`/`handleWithdraw` |
| Fuel | `CODE ONLY` | `SmeltingManager`; `BotBrain.handleSmeltingDeposit`/`handleSmeltingWait` |
| Item pickup | `CODE ONLY` | `DropCollector`; `BotBrain.finishTaskWithEvidence` |
| Panel (`/v2`, old `/`) | `CODE ONLY` | `SwarmWebServer`; `SwarmWebHandler`; `SwarmWebPanelV2`; `web/v2/*` |
| Commands and keybinds | `CODE ONLY` | `SwarmCommand`; `EnhancedSwarmAddon`; `SwarmHoleFiller.forceFill` |
| Persistence across restart | `PARTIAL` - base registry proven across a real restart 2026-11-13 (12 entries, no re-seed); tasks/waypoints/death still unproven | `TaskQueue`, `DepositConfig`, `StorageIndex`, `WaypointManager`; `DeathTracker` is memory-only |
| SafeFire | `CODE ONLY` (2026-11-14) - lava on by default, unlike the ported original | `SwarmSafeFire` (`CollisionShapeEvent`) |
| Shield while fighting | `CODE ONLY` (2026-11-14) - requires KillAura, yields to a locked totem, drops while moving | `SwarmShield` |
| Flee when low | `CODE ONLY` (2026-11-14) - Baritone direction goal, restores the task's goal afterwards | `SwarmFlee` |

Three rows are `DONE` on live evidence from 2026-11-13: mining and gathering,
the task queue, and deposits and chests (`test-runs/live-smelt-day2/` — the
64-ingot run plus four 78-ingot runs, the last ending `612 -> 690` ingots,
+78, with the completion line after the deposit). Fuel, item pickup, panel,
commands and persistence stay `CODE ONLY`; the `/v2` row's only browser
evidence is fake players, not a Minecraft client.

## Described but not in the code

Short list, not a roadmap:

- **Tier grinder.** `GearAudit`, `GrindPlanner`, `grindTarget` and
  `/eswarm grind <worker> <tier>` are not present. The only grinder is the
  mob-camp `GRIND` flow in `BotBrain.beginGrind`/`handleGrinding` with
  `auto-grind` and `grind-x/y/z`; it has no tier argument.
- **Logistics commands and types.** `/eswarm get`, `/eswarm sort`,
  `ContainerHelper`, `FETCH`, `SORT`, `sort-rules.json` and `BULK_GATHER` are
  not present. Withdraw exists as `handleWithdraw`/`WITHDRAW_REQUEST`, not as a
  general fetch/sort system.
- **Task types with no handler.** `BotTask.Type.EQUIP`, `DEPOSIT` and
  `SCAN_CHESTS` are enum values only. `BotBrain.handleIdle` does not dispatch
  them; deposits happen in the `DEPOSITING` state and scans happen through the
  `scan` verb.
- **Building and shulker gaps.** There is no shulker **fill** path (only
  `BotBrain.handleShulker` emptying), no `ShulkerHelper`, no `MaterialList`, no
  schematic material planning, and no nether-portal builder. `BUILD` starts
  Baritone but `completeBuild` deliberately fails with
  `unproven-completion` because placement evidence is absent.
- **Registry/storage HTTP endpoints.** `/api/registry/items`,
  `/api/registry/blocks`, `/api/storage`, `/api/waypoints` and
  `/api/grind/status` are not registered. The actual item endpoint is
  `/api/items`; `/api/action/scan_chests` sends `swarm:all scan` when called
  directly, but the `/v2` button is disabled and sends no request.
  `/api/action/check_fuel` is a 501.
- **Task leasing.** `LEASE_REQUEST`, a lease table and atomic task claiming are
  not present. `TaskQueue` has `requeueActiveForBot` and `hasActiveTask` only.
- **Death persistence.** `DeathTracker.load()` is a no-op; there is no
  `deaths.json` writer.
- **Waypoint push.** `/eswarm waypoint push` exists in the tree but only logs
  that workers will query the host; it does not serialize or send waypoints.

## Base inventory as measured (2026-11-13, `world_containers` + opened chest menus)

Authoritative, read from the world rather than from the storage index:

| position | block | contents |
|---|---|---|
| -550 91 601 **+** -550 91 602 | double chest | 32/54 slots, **768 iron_ingot**, 519 cobblestone, 118 dirt, 113 diorite |
| -549 91 598 | chest | 54/54 full, 64 iron_ingot, 1127 cobblestone, 832 dirt, 128 andesite, 71 cobbled_deepslate |
| -549 91 601 / -549 91 602 | chest | empty |
| -549/-550/-551 91 606 | 3x blast furnace | **holds coal** - `world_container open=true` read `coal x42` in 1/3 slots (probe, 04:24) |
| -549/-550/-551 91 605 | 3x smoker | not read (same block-entity limit) |
| -550/-549 91 607 | 2x furnace | not read (same block-entity limit) |

**How each row was measured matters.** Chest rows come from an opened container menu
(`world_container open=true`). Furnace rows come from the mod's own fuel reader
(`G2: cook check` in the bot log) - a furnace's contents are *not* readable from its
block entity, and an earlier version of this table wrongly said "empty" because it
tried. A client-side block entity never carries an inventory; contents only exist in
the `ContainerMenu` after opening. `world_containers` therefore lists containers
without claiming to know what is inside them.

Three corrections against earlier notes: the iron sits in the **double chest at
601/602** (768 ingots - the "690" figure came from the storage index, which only sees
one half of a double chest), the base has **three** blast furnaces and not two, and
those furnaces are **fuelled with coal**, not empty. A double chest is two blocks with
one inventory, so both halves report identical contents.

**The 690-vs-768 question is closed (04:26).** `/api/debug/storage` (new) reports
`iron_ingot = 832` with breakdown `-549,91,598=64; -550,91,601=768`: the double chest is
counted **once**, not twice, and 832 = 768 + 64 is exactly what the opened chests hold.
The old 690 was a snapshot taken before the final deposit landed, not a counting bug.
Before this endpoint existed the index was unobservable from outside, which is why the
gap survived two sessions of speculation.

## Gate A, 2026-11-13: what the last shift proved and what still blocks it

**Fixed and proven live (jar +cb67009, both instances, identical md5).**

- **Baritone commands now run on the client thread.** The worker sent chat straight from
  its socket thread, so Baritone built world state off-thread and threw
  `IllegalStateException: BlockStateInterface must be constructed on the main thread`.
  Every `#mine` was accepted (`Mining [granite]`) and broke nothing, while the task sat at
  the stock figure. This was the root cause behind every "stalled gather" this session,
  and it is why granite/andesite appeared unreachable. All worker chat is marshalled with
  `mc.execute`; after the fix dirt gathers mine continuously.
- **Gather watchdog is per task and tolerates travel.** It previously carried its state
  into the next task of the same item (second task failed without any attempt), and it
  treated a flat item count as a stall even while the bot was walking to the next pocket.
  It now resets on task id, alternates walking to the block and re-issuing the mine up to
  four times, and counts movement as progress.

**Verdict: Gate A is NOT passed.** In the longest shift (6 dirt arcs, brain on, T0
09:25:11) every task reached a terminal state in four minutes with 0 deaths and 0 crashes,
but five of the six completed with `evidence=already-in-stock` citing **2807 then 3800
dirt in base storage** while the four containers, opened live, held **1940** dirt total.
Real movement for the run was 993 items. A COMPLETE backed by an index figure the chests
do not support is exactly the failure the gate exists to catch, so the run is void.

**Open defect (the blocker).** The host storage index drifts upward relative to the live
containers: it reported chest `-550,91,601 = 1591` dirt and that container opened to 724.
Deposit deltas are published per transfer and added by `applyChestDelta`, so a delta that
is re-published for the same chest is counted twice. Until the index is a measurement
(live rescan) rather than a running total, `already-in-stock` completions cannot be
trusted, and any gate that counts them is measuring the index against itself.

**Also found:** `/api/task/cancel` and `/api/task/remove` are no-ops for PENDING rows, so
a queue cannot be emptied once queued; only terminal rows can be cleared.

## Deposit chests: why a fifth chest vanished (2026-11-14)

Adding a chest through `deposit set` or the panel wrote it to `depositConfig` and then
called `pushDepositConfigAll()`. That function starts with `adoptRegistryChests()`, which
compares the deposit list against the **registry** and, when they differ, deletes the whole
`all` category and rebuilds it from the registry alone. With four registry chests, the
fifth chest was removed about a second after it was added, before the push went out. The
worker was never at fault: `addLocalChest` appends with no cap.

Fixed in `c43343d`: `setDepositChest()` registers the position in `BaseRegistry` (reusing
an existing entry's id and updating its category), so the config and the registry agree
before reconciliation runs. `/api/chest/add` and `/api/chest/remove` do both halves in one
action, `/api/debug/deposit` prints the two lists side by side, and MCP exposes `chest_add`,
`chest_remove`, `deposit_config`.

Proven live on jar `0.1.0+c43343d`: add at `-549,91,603` -> config 5 / registry 5 /
worker `CHEST: list replaced n=5 gen=116`, and it survived a forced `deposit_push`;
remove -> config 4 / registry 4 / worker `n=4 gen=117`.

**Lesson worth keeping:** the registry is the single source of truth for chests, so every
path that adds one must write it. Any future "add chest" feature that only touches the
deposit config will be erased by the next push.

## Gather staging was fail-open (2026-11-14)

`ensureStagedAwayFromBase()` runs before every gather. With no base anchor it returned
"staged" immediately, and with a staging goto that timed out it also returned "staged" and
let mining start from wherever the bot stood. Both paths are silent. On 2026-11-14 the
anchor did not exist until 15:03:51 and the log shows `No base anchor set` at 15:03:48 with
no `STAGE:` line all session: every gather ran unstaged, and Baritone's `#mine` picked the
nearest dirt — under the test base floor.

Staging is not a protection radius: it only moves the bot far away, and `#mine` then picks
the nearest target to the bot. So the fix is to stop treating a failed stage as permission
to dig:

- missing anchor now logs a one-shot error naming the fix (`.eswarm base set`)
- every give-up path (no target, no candidate after 2 tries, goto unreachable after 3
  tries) calls `mayMineHere(base)`: if the bot is closer than `gather-staging-distance / 2`
  (default 80, floor 8) to the base it does **not** mine, logs the distance and the radius,
  and the task goes `STUCK`

Committed as `e9d0c00`-era work; not yet live on the running clients.

## Double chests: measured, not inferred (verified 2026-11-14 15:40, jar 0.1.0+1b4b079)

Live before/after on the test base, all three storage containers opened by the bot:

| reading | dirt total | records |
|---|---|---|
| before | 4404 | `-550,91,601`=576, `-550,91,602`=576, `-549,91,601`=1178, `-549,91,602`=1178, `-549,91,598`=896 |
| after | 2650 | `-550,91,601`=576, `-549,91,601`=1178, `-549,91,598`=896 |

2650 = 576 + 1178 + 896, and `per chest` lists exactly three containers. The deposit list and
registry settled at 9 entries and the worker logged `CHEST: list replaced n=9 gen=148`.

Each record now persists `twin` and `doubleChest`, so the collapse survives a restart
(`-550,91,601 twin [-550,91,602]`, `-549,91,601 twin [-549,91,602]`, `-549,91,598 twin
[-549,91,599]` — the last one was never in the deposit list, so nothing to prune there).

The first version of this fix was wrong and the live check caught it: folding a reading into
an existing record at the twin's coordinates updated that record and left the reading's own
record in place, so dirt stayed at 4404. A twin reading now always consumes the other half's
record and writes at the opened half — one record per double chest, at whichever half was
last opened.

## Base-protection guard, verified live (2026-11-14 16:36, jar 0.1.0+2eedb9e)

Forced with the one-shot `stagefail` hook (worker exec verb) plus a resource with zero stock
so the bot had to gather: `birch_log`, bot standing 8 blocks from the anchor at -545,90,602.

```
STAGING: waypoint for 'birch_log' -> none
STAGE: origin -545 90 602 band=16
STAGE: gave up reason=no-candidate after=0
STAGE: refusing to mine 8 blocks from base (need 80) — task stalled instead of digging the base
tick state=STUCK planStep=0 planSize=1 task=birch_log
```

Board: `GATHER birch_log 1 -> FAILED | stuck`. `#mine minecraft:birch_log` was never
dispatched — zero occurrences in the bot log after the refusal. Before this change every one
of those give-up paths returned "staged" and the mine command went out at the base.

Note the give-up came from `no-candidate` rather than the forced flag: the 64-sample search
found no reachable floor with headroom in band 16 around that anchor, which is its own
finding — staging from inside a built-up base is likely to fail, and until now that failure
meant mining the base.

## Staging candidate search, fixed and proven (2026-11-14 18:39, jar 0.1.0+6ecaec0)

Old search sampled one random Y inside the band per column and required air at exactly that
Y. From a base at y=90 under a grass surface almost every column is stone at that Y, so 64
samples returned `no-candidate` every time. It now walks the column outward from the base's
own elevation to the nearest spot with a solid floor and two blocks of headroom, and falls
back to the heightmap surface when a column has nothing underground.

Unstaged gather from inside the base, `birch_log` (0 in stock):

```
STAGING: waypoint for 'birch_log' -> none
STAGE: origin -545 90 602 band=16
STAGE: selected -645 84 613 floor=grass_block
Staging complete, beginning gather
GATHER: item=birch_log method=mine issuing=#mine minecraft:birch_log | dispatch
```

Bot walked from -549,91,596 to -627,79,607 (83 blocks from the anchor) and mined.

## Open defect this run found: honest completions fail as unproven

Same run, immediately after the gather:

```
18:40:07 DEPOSIT: start chest=-551 91 598 ... complete moved=0 delta={}
18:40:09 DEPOSIT: landed for c2df8759 ... (mov...
18:40:09 TASK: retry c2df8759 reason=unproven-completion
18:40:09 TASK: failed c2df8759 reason=unproven-completion
18:40:10 TASK: complete a3dce021 evidence=already-in-stock (2 minecraft:birch_log in base storage)
```

The bot gathered, came back and deposited; the evidence check then declared the completion
unproven and failed the task — while the very next task, a minute later, completed on the same
`already-in-stock` evidence. The difference is timing: the first check ran before the deposit's
chest measurement landed. A race, not a policy problem, but it means an honest gather fails and
Gate A cannot pass while it exists.

## Completion sweep: a skip is not a failure (verified 2026-11-14, jar 0.1.0+88b38ea)

`DropCollector.startFinalSweep` returned UNPROVEN whenever `startWorkUnit()` refused — a 1 s
cooldown since the previous sweep, still breaking, another sweep active, or inventory full. An
honest gather that had just deposited therefore failed one tick after its own deposit sweep.

Now: `DEFERRED` for those scheduler states, `BotBrain` waits up to 5 s and then closes on the
count gate that already accepted `have + landed >= need`; a disabled collector answers
`NOTHING_TO_COLLECT`. No code path returns UNPROVEN any more — `grep -n UNPROVEN
src/main/java/com/rdh/swarm/task/DropCollector.java` finds only the enum constant and its
comment, so a gather cannot be failed for an unproven sweep at all. The gate that remains is
the count gate, which compares real numbers.

Live, on the test base:

```
TASK: complete 5f94586a evidence=inv        (gather -> deposit -> complete, no unproven)
TASK: complete 9913864b evidence=inv
[Bot Brain] Setting Collect Drops changed to false.
[Bot Brain] Setting Collect Drops is false.
TASK: complete c42ac52b evidence=verified-empty   (collector off, task still closes)
[Bot Brain] Setting Collect Drops changed to true.
```

Not reproduced deliberately: the exact one-second cooldown collision. It is structurally gone
rather than merely unobserved, per the grep above; the three tasks above cover the happy path
and the disabled path.
