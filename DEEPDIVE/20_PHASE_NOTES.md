# 20 — Phase implementation notes

## G1 — ModuleGuard + survival foundation

Branch: `feat/g1-survival` (from `port-26.3` at `04f358c`).

### What changed

- Added `com.rdh.swarm.modules.ModuleGuard`:
  - Snapshots the guarded module states, enables the guarded survival set, applies settings through `Setting.parse(String)`, re-enables guarded modules every 20 ticks, and restores only the snapshotted modules on deactivate.
  - Guarded: `AutoEat`, `AutoArmor`, `AutoTotem`, `AutoTool`, `AutoReplenish`, `NoFall`, `AntiHunger`, `AutoRespawn`, and optionally `KillAura`.
  - Do not guard `Offhand` or `AutoSmelter`.
  - `KillAura` is configured with `pause-baritone=true`, `ignore-passive=true`, and an `EntityTypeListSetting` containing all `MobCategory.MONSTER` types; players and passive mobs are not included.
  - `isEating()` uses `AutoEat.eating` with a `Player.isUsingItem()` fallback. `isBaritonePaused()` also treats an active `KillAura.attacking` as paused.
- Added BotBrain settings:
  - `manage-modules` Bool, default `true`.
  - `fight-mobs` Bool, default `true`.
- BotBrain lifecycle:
  - `onActivate()` activates the guard (respecting `fight-mobs`) and resets death-capture fields.
  - Tick calls `ModuleGuard.tick()` every 20 ticks.
  - Tick freezes and resets the stuck/state timers while eating or while the guard reports a Baritone pause.
  - `onDeactivate()` restores the guard, sends the chat `#stop`, and calls Baritone `IPathingBehavior.cancelEverything()` in a `try/catch (Exception | NoClassDefFoundError)`.
- `handleIdle` now fails unsupported main task types with one WARN and `TASK_FAILED`, instead of silently entering `GATHERING`; unknown dependency-step types use the same failure path.
- Death capture:
  - Tracks the last alive block position and dimension identifier every callback.
  - Records exactly one death per death event via the new `DeathTracker.recordDeath(UUID, String, BlockPos, String)` overload.
  - `DeathTracker` now stores the dimension and exposes `getDeathDimension()`.
  - `handleRecovery` refuses cross-dimension recovery rather than sending an Overworld `#goto` for a Nether death.

### Public signatures

```text
com.rdh.swarm.modules.ModuleGuard
  void activate(boolean fightMobs)
  void tick(int tickCounter)
  void deactivate()
  boolean isEating()
  boolean isBaritonePaused()
  boolean shouldFreezeTimers()

com.rdh.swarm.task.DeathTracker
  void recordDeath(UUID player, String cause, BlockPos pos)
  void recordDeath(UUID player, String cause, BlockPos pos, String dimension)
  String getDeathDimension()
```

### Settings

| Owner | Name | Type | Default |
|---|---|---|---|
| BotBrain | `manage-modules` | Bool | `true` |
| BotBrain | `fight-mobs` | Bool | `true` |

### Commands / endpoints

None added in G1.

### Verification

- `./gradlew.bat build` with JDK 25.0.2: **BUILD SUCCESSFUL**.
- The build emitted only two pre-existing `BlockStateBase.isSolid()` deprecation warnings.

### Known issues / not verified

- No in-game activation test was possible. `Setting.parse` behavior for every enum/entity setting is a runtime assumption; parse failures produce one WARN and leave the setting unchanged.
- `KillAura`'s pause flag is private; `isBaritonePaused()` approximates it with `KillAura.attacking` and `AutoEat.eating`, so a paused path outside those states may still age timers.
- Death recovery still only navigates to the last death position; it does not guarantee item pickup or safe routing.
- The guard re-enables modules every 20 ticks, so it will also override a manual disable while BotBrain is active by design.
