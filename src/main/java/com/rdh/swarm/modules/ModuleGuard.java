package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.player.AntiHunger;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoReplenish;
import meteordevelopment.meteorclient.systems.modules.player.AutoRespawn;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import com.rdh.swarm.task.FoodManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Owns the Meteor survival modules used by a worker. It snapshots their
 * original state, enables the guarded set, and restores only that set.
 */
public final class ModuleGuard {
    private record GuardSpec(Class<? extends Module> type, Map<String, String> settings, boolean hostileMobsOnly) {}

    private static final List<GuardSpec> SPECS = List.of(
        new GuardSpec(SwarmSafeFire.class, Map.of(), false),
        new GuardSpec(NoSlow.class, Map.of("items", "true"), false),
        new GuardSpec(SwarmShield.class, Map.of(), false),
        new GuardSpec(SwarmFlee.class, Map.of(), false),
        new GuardSpec(AutoEat.class, Map.of(
            "pause-baritone", "true",
            "pause-auras", "false",
            "threshold-mode", "Any",
            "health-threshold", "10.0",
            "hunger-threshold", "16",
            "food-priority", "Saturation",
            "search-inventory", "true"
        ), false),
        new GuardSpec(AutoArmor.class, Map.of(
            "preferred-protection", "Protection",
            "swap-delay", "1",
            "ignore-elytra", "true",
            "blast-prot-leggings", "true",
            "anti-break", "true"
        ), false),
        new GuardSpec(AutoTotem.class, Map.of(
            "mode", "Smart",
            "delay", "0",
            "health", "10",
            "elytra", "true",
            "fall", "true",
            "explosion", "true"
        ), false),
        new GuardSpec(AutoTool.class, Map.of(
            "prefer", "Fortune",
            "silk-touch-for-ender-chest", "true",
            "anti-break", "true",
            "switch-back", "true",
            "switch-delay", "0"
        ), false),
        new GuardSpec(AutoReplenish.class, Map.of(
            "min-count", "8",
            "delay", "1",
            "offhand", "true",
            "unstackable", "true",
            "same-enchants", "true",
            "search-hotbar", "false"
        ), false),
        new GuardSpec(NoFall.class, Map.of(
            "mode", "Packet",
            "reset-on-disable", "true",
            "air-place-mode", "BeforeDeath",
            "rotate", "true"
        ), false),
        new GuardSpec(AntiHunger.class, Map.of(
            "sprint", "true",
            "on-ground", "true"
        ), false),
        new GuardSpec(AutoRespawn.class, Map.of(), false),
        new GuardSpec(KillAura.class, Map.of(
            "pause-baritone", "true",
            "ignore-passive", "true",
            "attack-when-holding", "Weapons",
            "rotate", "Always",
            "auto-switch", "false",
            "max-targets", "1",
            "range", "4.5"
        ), true)
    );

    /**
     * Operator intent, keyed by module class. Non-empty for a module means the guard
     * must keep it in that state: on = never disable it, off = never re-enable it.
     * Static because a BotBrain restart must not forget what the operator wanted, and
     * because a test or script can force a state without racing the guard.
     */
    private static final Map<Class<? extends Module>, Boolean> INTENT = new LinkedHashMap<>();

    private final Map<Class<? extends Module>, Module> guarded = new LinkedHashMap<>();
    private final Map<Class<? extends Module>, Boolean> originalState = new LinkedHashMap<>();
    /** What this guard last forced, so a change we did not make is recognisably manual. */
    private final Map<Class<? extends Module>, Boolean> enforced = new LinkedHashMap<>();
    private boolean active;
    private boolean killAuraSuppressed;
    private Boolean rawFoodFallback;
    private boolean warnedNoBlacklist;

    public void activate(boolean fightMobs) {
        if (active) return;
        active = true;

        int enabled = 0;
        int settingsApplied = 0;
        for (GuardSpec spec : SPECS) {
            if (spec.hostileMobsOnly() && !fightMobs) continue;

            Module module = Modules.get().get(spec.type());
            if (module == null) {
                ChatUtils.warningPrefix("ModuleGuard", "Missing module: %s", spec.type().getSimpleName());
                continue;
            }

            originalState.put(spec.type(), module.isActive());
            guarded.put(spec.type(), module);

            for (Map.Entry<String, String> entry : spec.settings().entrySet()) {
                if (applySetting(module, entry.getKey(), entry.getValue())) settingsApplied++;
            }
            if (spec.hostileMobsOnly() && !applyMonsterTargets(module)) {
                ChatUtils.warningPrefix("ModuleGuard", "Could not set hostile-only targets on %s", module.name);
            }

            Boolean intended = INTENT.get(spec.type());
            boolean target = intended != null ? intended : true;
            if (module.isActive() != target) {
                try {
                    if (target) { module.enable(); enabled++; }
                    else module.disable();
                } catch (Exception e) {
                    ChatUtils.warningPrefix("ModuleGuard", "Could not %s %s: %s",
                        target ? "enable" : "disable", module.name, e.getMessage());
                }
            }
            enforced.put(spec.type(), module.isActive());
        }
        ChatUtils.infoPrefix("ModuleGuard", "Activated %d modules (%d newly enabled, %d settings)", guarded.size(), enabled, settingsApplied);
    }

    public void tick(int tickCounter) {
        if (!active || tickCounter % 20 != 0) return;
        if (mc.player == null || mc.level == null) return;

        for (Map.Entry<Class<? extends Module>, Module> entry : guarded.entrySet()) {
            Module module = entry.getValue();
            if (killAuraSuppressed && module instanceof KillAura) continue;

            Boolean intended = INTENT.get(entry.getKey());
            if (intended != null) {
                // Explicit intent wins over the guard's own preference.
                if (module.isActive() != intended) {
                    try {
                        if (intended) module.enable();
                        else module.disable();
                        ChatUtils.infoPrefix("ModuleGuard", "Applied intent: %s %s",
                            module.name, intended ? "on" : "off");
                    } catch (Exception e) {
                        ChatUtils.warningPrefix("ModuleGuard", "Could not apply intent to %s: %s", module.name, e.getMessage());
                    }
                }
                enforced.put(entry.getKey(), intended);
                continue;
            }

            Boolean forced = enforced.get(entry.getKey());
            if (forced != null && module.isActive() != forced) {
                // The guard did not make this change, so a person or a script did.
                // Record it as intent instead of fighting it every 20 ticks.
                boolean now = module.isActive();
                INTENT.put(entry.getKey(), now);
                enforced.put(entry.getKey(), now);
                ChatUtils.infoPrefix("ModuleGuard", "Manual toggle kept: %s %s",
                    module.name, now ? "on" : "off");
                continue;
            }

            if (!module.isActive()) {
                try {
                    module.enable();
                    enforced.put(entry.getKey(), true);
                    ChatUtils.infoPrefix("ModuleGuard", "Re-enabled %s", module.name);
                } catch (Exception e) {
                    ChatUtils.warningPrefix("ModuleGuard", "Could not re-enable %s: %s", module.name, e.getMessage());
                }
            }
        }
    }

    public void deactivate() {
        if (!active) return;
        int restored = 0;
        int honored = 0;
        for (Map.Entry<Class<? extends Module>, Module> entry : guarded.entrySet()) {
            Module module = entry.getValue();
            Boolean intended = INTENT.get(entry.getKey());
            // Intent outranks the snapshot taken at activate(): a module the operator
            // turned on or off while the guard ran must stay that way.
            boolean target = intended != null
                ? intended
                : originalState.getOrDefault(entry.getKey(), module.isActive());
            if (intended != null) honored++;
            try {
                if (target && !module.isActive()) {
                    module.enable();
                    restored++;
                } else if (!target && module.isActive()) {
                    module.disable();
                    restored++;
                }
            } catch (Exception e) {
                ChatUtils.warningPrefix("ModuleGuard", "Could not restore %s: %s", module.name, e.getMessage());
            }
        }
        guarded.clear();
        originalState.clear();
        enforced.clear();
        active = false;
        ChatUtils.infoPrefix("ModuleGuard", "Restored guarded module state (%d changes, %d kept by intent)",
            restored, honored);
    }

    /**
     * Force a module on or off. Unlike a bare .toggle this survives guard ticks and
     * guard teardown, because the state is stored as intent rather than as a momentary
     * toggle the next guard pass can undo.
     */
    public static boolean setIntent(String moduleName, boolean enabled) {
        Module module = Modules.get().get(moduleName);
        if (module == null) return false;
        @SuppressWarnings("unchecked")
        Class<? extends Module> type = (Class<? extends Module>) module.getClass();
        INTENT.put(type, enabled);
        if (module.isActive() != enabled) {
            if (enabled) module.enable();
            else module.disable();
        }
        ChatUtils.infoPrefix("ModuleGuard", "Intent set: %s %s", module.name, enabled ? "on" : "off");
        return true;
    }

    public static Boolean getIntent(String moduleName) {
        Module module = Modules.get().get(moduleName);
        return module == null ? null : INTENT.get(module.getClass());
    }

    public static boolean clearIntent(String moduleName) {
        Module module = Modules.get().get(moduleName);
        return module != null && INTENT.remove(module.getClass()) != null;
    }

    public void setKillAuraSuppressed(boolean suppressed) {
        killAuraSuppressed = suppressed;
        Module killAura = guarded.get(KillAura.class);
        if (killAura == null) return;
        if (suppressed) {
            if (killAura.isActive()) killAura.disable();
            enforced.put(KillAura.class, false);
            INTENT.remove(KillAura.class); // suppression is functional, not operator intent
        } else {
            if (!killAura.isActive()) killAura.enable();
            enforced.put(KillAura.class, true);
        }
    }

    /** Allow safe raw meat in AutoEat by removing it from the blacklist; false restores it. */
    public void setRawFoodFallback(boolean allow) {
        if (rawFoodFallback != null && rawFoodFallback == allow) return;
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat == null) return;
        Setting<?> raw = autoEat.settings.get("blacklist");
        if (raw == null) {
            if (!warnedNoBlacklist) {
                warnedNoBlacklist = true;
                ChatUtils.warningPrefix("ModuleGuard", "AutoEat has no blacklist setting; raw-food fallback unavailable");
            }
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Setting<java.util.List<Item>> blacklist = (Setting<java.util.List<Item>>) raw;
            java.util.List<Item> items = new ArrayList<>(blacklist.get());
            boolean changed = false;
            for (Item rawFood : FoodManager.SAFE_RAW) {
                if (allow) changed |= items.remove(rawFood);
                else if (!items.contains(rawFood)) { items.add(rawFood); changed = true; }
            }
            if (changed) blacklist.set(items);
            rawFoodFallback = allow;
        } catch (Exception e) {
            ChatUtils.warningPrefix("ModuleGuard", "Could not update AutoEat blacklist: %s", e.getMessage());
        }
    }

    public boolean isEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat != null && autoEat.eating) return true;
        return mc.player != null && mc.player.isUsingItem();
    }

    public boolean isBaritonePaused() {
        if (isEating()) return true;
        KillAura killAura = Modules.get().get(KillAura.class);
        return killAura != null && killAura.isActive() && killAura.attacking;
    }

    public boolean shouldFreezeTimers() {
        return isEating() || isBaritonePaused();
    }

    private boolean applySetting(Module module, String name, String value) {
        Setting<?> setting = module.settings.get(name);
        if (setting == null) {
            ChatUtils.warningPrefix("ModuleGuard", "%s has no setting %s", module.name, name);
            return false;
        }
        try {
            if (!setting.parse(value)) {
                ChatUtils.warningPrefix("ModuleGuard", "Could not parse %s=%s for %s", name, value, module.name);
                return false;
            }
            return true;
        } catch (Exception e) {
            ChatUtils.warningPrefix("ModuleGuard", "Could not set %s=%s for %s", name, value, module.name);
            return false;
        }
    }

    /** KillAura's entity list is a Set setting, so it cannot be set through parse(). */
    private boolean applyMonsterTargets(Module module) {
        Setting<?> raw = module.settings.get("entities");
        if (!(raw instanceof EntityTypeListSetting entitySetting)) return false;

        Set<EntityType<?>> monsters = new LinkedHashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() == MobCategory.MONSTER) monsters.add(type);
        }
        if (monsters.isEmpty()) return false;
        return entitySetting.set(monsters);
    }
}
