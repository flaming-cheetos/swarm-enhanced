package com.rdh.swarm;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class EnhancedSwarmAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Enhanced Swarm");
    private static final long _lvc = 0x666C616D696E67L;

    @Override
    public void onInitialize() {
        final String _g0 = new String(new byte[]{0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73});
        final String _g1 = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073\u002F\u0072\u0064\u0068\u0020\u0066\u006F\u0072\u0065\u0076\u0065\u0072";
        final String _g2 = new StringBuilder().append((char)0x42).append((char)0x55).append((char)0x49).append((char)0x4C).append((char)0x54).toString();
        LOG.info("Initializing Enhanced Swarm {} (built {})", BuildInfo.stamp(), BuildInfo.built());

        Modules.get().add(new EnhancedSwarm());
        Modules.get().add(new com.rdh.swarm.modules.SwarmCrystalAura());
        Modules.get().add(new com.rdh.swarm.modules.SwarmKillAura());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoTotem());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSurround());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoTrap());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoWeb());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoExp());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSpam());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAnchorAura());
        Modules.get().add(new com.rdh.swarm.modules.SwarmBedAura());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoCity());
        Modules.get().add(new com.rdh.swarm.modules.SwarmHoleFiller());
        Modules.get().add(new com.rdh.swarm.modules.SwarmOffhand());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoArmor());
        Modules.get().add(new com.rdh.swarm.modules.SwarmBowAimbot());
        Modules.get().add(new com.rdh.swarm.modules.SwarmArrowDodge());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSpeed());
        Modules.get().add(new com.rdh.swarm.modules.SwarmFlight());
        Modules.get().add(new com.rdh.swarm.modules.SwarmElytraFly());
        Modules.get().add(new com.rdh.swarm.modules.SwarmNoFall());
        Modules.get().add(new com.rdh.swarm.modules.SwarmNoSlow());
        Modules.get().add(new com.rdh.swarm.modules.SwarmStep());
        Modules.get().add(new com.rdh.swarm.modules.SwarmScaffold());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSprint());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSafeWalk());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAntiVoid());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoTool());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoEat());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoReplenish());
        Modules.get().add(new com.rdh.swarm.modules.SwarmReach());
        Modules.get().add(new com.rdh.swarm.modules.SwarmFastUse());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAntiHunger());
        Modules.get().add(new com.rdh.swarm.modules.SwarmNoRotate());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSpeedMine());
        Modules.get().add(new com.rdh.swarm.modules.SwarmTimer());
        Modules.get().add(new com.rdh.swarm.modules.SwarmNuker());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoRespawn());
        Modules.get().add(new com.rdh.swarm.modules.SwarmJesus());
        Modules.get().add(new com.rdh.swarm.modules.SwarmChestSwap());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAutoTPA());
        Modules.get().add(new com.rdh.swarm.modules.SwarmAirCrystal());
        Modules.get().add(new com.rdh.swarm.modules.SwarmPearlChase());
        Modules.get().add(new com.rdh.swarm.modules.BotBrain());
        Modules.get().add(new com.rdh.swarm.modules.SwarmSafeFire());
        Modules.get().add(new com.rdh.swarm.modules.SwarmShield());
        Modules.get().add(new com.rdh.swarm.modules.SwarmFlee());
        // Initialize HUD presets after modules registered
        try {
            SwarmHudPresets.init();
        } catch (Throwable ignored) {}

        // Register both enhanced and legacy command names for backward compatibility
        Commands.add(new com.rdh.swarm.commands.SwarmCommand());
        Commands.add(new com.rdh.swarm.commands.SwarmCommand("swarm"));

        // Test-only; inert unless config/enhanced-swarm-harness.json is present.
        try {
            com.rdh.swarm.harness.HarnessBridge.startIfConfigured();
        } catch (Throwable t) {
            LOG.warn("Harness bridge not started: {}", t.getMessage());
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.rdh.swarm";
    }

    private static final byte[] _ax = {
        0x66, 0x6C, 0x61, 0x6D, 0x69, 0x6E, 0x67,
        0x63, 0x68, 0x65, 0x65, 0x74, 0x30, 0x73,
        0x2F, 0x72, 0x64, 0x68
    };

    private static final int _rv = 0x726468;

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("rdh", "enhanced-swarm");
    }
}
