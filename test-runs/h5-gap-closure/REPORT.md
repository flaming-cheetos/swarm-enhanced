# H5 gap-closure evidence — 2026-11-12

Source commit: `4f24d62` (`fix(h5): expose lease state and prove progress/matrix paths`).
Java: `C:\Program Files\Java\jdk-25.0.2` (Fabric Loom requires JVM 25; default Java 21 cannot configure the build).
Built jar (local, not relabeled/deployed): `build/libs/enhanced-swarm-0.1.0+4f24d62.jar`.

## Scope

Close the three H5 plan gaps that do not depend on the build-identity decision:

1. Panel/log lease visibility (fields present, token absent, rendered, logged on real paths).
2. Old/new protocol matrix covered by an automated test with documented expectations.
3. Progress accounting: completed rows report real clamped progress (2/2), proven by test.

**The live H5 gate was NOT rerun in this pass.** Step 4 live PASS on `0.1.0+8e2eed6`
stands as recorded in `test-runs/h5-step4-lease-restart/`; that directory and
`test-runs/h1-3x-e62b2f9/` are untouched.

## Verification

| Check | Log | Result |
|---|---|---|
| Focused H5 probes after restore to 4f24d62 | `focused-tests-restored.log` | 6/6 PASS |
| `clean build` (full suite) from clean state | `build-clean.log` | BUILD SUCCESSFUL, 12 tasks |
| Panel reversion (expose `leaseToken`) | `reversion-panel-fails.log` | FAILS as required (`raw leaseToken was exposed`) |
| Progress reversion (remove clamp) | `reversion-progress-fails.log` | FAILS as required (`progress was not clamped to the target quantity`) |
| Fencing reversion (disable holder check) | `reversion-fencing-fails.log` | FAILS as required (`stale completion after reconnect was accepted`) |
| Restore + protocol/fencing rerun | `fencing-restored-green.log` | PASS, worktree clean |
| Weak token-only mutation (pre-restart note) | `reversion-protocol-note.log` | Still PASS — owner/status gates catch the stale case even with token check weakened (defense in depth, not a probe hole) |

## Panel JSON contract (proven by `PanelLeaseJsonTest`)

Emitted per task: `attempt`, `statusReason`, `lastOwner`, `leaseOwner`,
`leaseExpiresAt`, `leaseExpiresInMs`, `lastHeartbeatAt`, `heartbeatAgeMs`,
`leaseActive`. `leaseToken` and its value are asserted absent. Both v1
(`SwarmWebPanelScript`) and v2 (`web/v2/app.js`) render
attempt/reason/lease-owner/expiry/heartbeat age.

## Protocol matrix (proven by `ProtocolMatrixTest`)

| host / worker | progress | completion | failure / cancel |
|---|---|---|---|
| new host + new worker (token) | accepted | accepted | accepted |
| new host + old worker (no token) | accepted while owner ACTIVE | accepted while owner ACTIVE | accepted while owner ACTIVE |
| old host + new worker (no token field) | accepted | accepted | accepted |
| missing token, active legacy worker | accepted | accepted | accepted |
| stale token after reconnect/restart | rejected | rejected | rejected |

Plus reconnect (`worker-disconnect`) and host-startup (`host-restart`)
recovery, fresh-token re-claim, exactly-one completion. Legacy fallback in
`FencingReconnectTest` stays covered.

## Progress accounting (proven by `ProgressAccountingTest`)

Root cause: the GATHER loop logged counts but never sent `TASK_PROGRESS`.
Fix: `BotBrain.syncTaskProgress` publishes clamped progress during gathering
and before completion; `TaskQueue.recordProgressIfLeaseMatches` clamps to
target. Completed row reports `2/2`, `collected: 2`, `COMPLETE`.

## Smelting-appliance research (for the next workstream)

Code facts, not assumptions:

- `SmeltingManager` operates purely on `AbstractFurnaceMenu` /
  `AbstractFurnaceBlockEntity`, which cover furnace, blast furnace, and
  smoker containers. `fuelStackAt`/`hasFuelAt` use the same abstraction.
- `BotBrain` SMELT scan already accepts `Blocks.FURNACE`, `Blocks.SMOKER`,
  and `Blocks.BLAST_FURNACE` (blast furnace excluded for cooking tasks).
- `RecipeBook` has a `furnace` crafting recipe but no `blast_furnace` recipe,
  so a blast furnace must be pre-placed, not crafted, by the bot.
- Conclusion: no appliance-support code gap found at the scan/menu layer.
  Live verification (fuel, slots, timeouts, completion per appliance) still
  requires the user's running test server and is recorded as OPEN below.

## Open / not done here

- Live H5 gate rerun (deliberately out of scope for this pass).
- Live smelting-matrix runs against furnace/blast furnace/smoker.
- Backlog: EQUIP handler, FETCH/get/SORT, registry/storage/waypoint endpoints,
  shulker fill/BULK, build material planning, netherite acquisition links,
  host-side role assignment, grind command/tier.
