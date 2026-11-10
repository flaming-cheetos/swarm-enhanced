# 10 — Utils, Timers, Mixin

## utils/
| file (lines) | purpose | public API | callers |
|---|---|---|---|
| BODamageUtils (33) | crystal/explosion damage math for the auras | `crystalDamage(LivingEntity, Box, Vec3d, boolean ignoreTerrain)`, `crystalDamage(..., BlockPos ignorePos, boolean)` | SwarmAirCrystal, SwarmCrystalAura-adjacent code |
| BOInvUtils (49) | hotbar/inventory swap helpers (pick-switch + swap-back) | `pickSwitch(int)`, `pickSwapBack()`, `invSwitch(int)`, `swapBack()`, `getSelectedSlot()` | CraftingManager (`getSelectedSlot` for table placement), auras |
| EpicInterface (6) | 2-arg functional interface used by extrapolation | `apply(T, E)` | ExtrapolationUtils |
| ExtrapolationUtils (132) | player position prediction (tick extrapolation + smoothing) | `preInit()`, `extrapolateMap(Map<AbstractClientPlayerEntity,Box>, EpicInterface, EpicInterface)`, `extrapolate(player, int, int)`, `extrapolate(player, List<Vec3d>, int, int)` | SwarmAirCrystal, SwarmKillAura/Crystal proxies' upstream code |
| OLEPOSSUtils (101) | geometry/enchant helpers shared by auras | `feet(Box)`, `getMiddle(Box)`, `inside(PlayerEntity,Box)`, `hasAquaAffinity(LivingEntity)`, `hasEnchantment(RegistryKey,ItemStack)`, `getLevel(...)`, `closerToZero(int)`, `getClosest(Vec3d,Vec3d,double,double)`, `strictDir(BlockPos,Direction)`, `getCrystalBox(BlockPos)`, `getCrystalBox(Vec3d)` | crystal/air-crystal/placement code |
| PlacementHelper (173) | safe block placement with item selection + rotation | `placeBlockSafely(BlockPos, Item)`, `placeBlockSafely(BlockPos)` (legacy, "dummy item" fallback + warning) | AirCrystal / proxy place modules |
| RotationUtils (60) | yaw/pitch stepping and angle math | `nextYaw(double,double,double)`, `yawAngle(...)`, `nextPitch(...)`, `radAngle(Vec2f,Vec2f)`, `getYaw(Vec3d,Vec3d)`, `getPitch(Vec3d,Vec3d)` | auras (rotation smoothing) |
| TimerList (69) | generic timed list (value → endTime) | `add(T,double)`, `update()`, `clear()`, `getMap()`, `getList()`, `remove(Predicate)`, `contains(T)`, static `Timer<T>` | auras for cooldown tracking |

## timers/TimerList (69) — duplicate
Identical API to `utils/TimerList` (`add/update/clear/getMap/getList/remove/contains` + inner `Timer`), but **different file
bytes** (MD5 `2B24…` vs `C9A0…`); the `timers` copy uses a fully-qualified `java.util.function.Predicate`. No imports of
`com.rdh.swarm.timers.TimerList` were found — it is dead duplication and a trap for future edits.

## mixin/ChatUtilsMixin (18)
```java
@Mixin(ChatUtils.class)
public class ChatUtilsMixin {
    @Inject(method = "info(Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private static void onInfo(String message, Object[] args, CallbackInfo ci) {
        if ("BotBrain".equals(message) && (args == null || args.length == 0)) ci.cancel();
    }
}
```
`enhanced-swarm.mixins.json`: `required: true`, `package: com.rdh.swarm.mixin`, `compatibilityLevel: JAVA_21`,
`client: ["ChatUtilsMixin"]`. Purpose: drop the bare `[BotBrain]` prefix-only chat line that BotBrain emitted every tick.
Risk: injects into a Meteor internal signature; a Meteor update changes the descriptor and the mixin fails at load
(required=true → hard crash, not silent).

## Notes
- All utils are static-method classes, no state, no tests.
- `PlacementHelper.placeBlockSafely(target)` is a legacy shim that warns and places with the first `BlockItem`.
- `BODamageUtils` has two overloads where the `BlockPos ignorePos` variant lets callers exclude the placed crystal's own
  block from terrain resistance.
