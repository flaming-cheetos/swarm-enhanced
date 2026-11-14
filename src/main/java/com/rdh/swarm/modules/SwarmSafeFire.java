package com.rdh.swarm.modules;

import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Client-side collision fence for fire and lava.
 *
 * Ported from NumbyHack's SafeFire (originally Tanuki/walaryne), GPLv3.
 * Difference for a bot: lava defaults to ON, because lava inside a walking
 * path is invisible until the bot is already burning.
 */
public class SwarmSafeFire extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<Boolean> fire = sg.add(new BoolSetting.Builder()
        .name("fire")
        .description("Refuse to walk into fire.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> soulFire = sg.add(new BoolSetting.Builder()
        .name("soul-fire")
        .description("Refuse to walk into soul fire.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> lava = sg.add(new BoolSetting.Builder()
        .name("lava")
        .description("Refuse to walk into lava. On by default for bots.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> campfire = sg.add(new BoolSetting.Builder()
        .name("campfire")
        .description("Refuse to walk into campfires.")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> soulCampfire = sg.add(new BoolSetting.Builder()
        .name("soul-campfire")
        .description("Refuse to walk into soul campfires.")
        .defaultValue(false)
        .build());

    public SwarmSafeFire() {
        super(com.rdh.swarm.EnhancedSwarmAddon.CATEGORY, "swarm-safe-fire",
            "Client-side collision fence for fire and lava.");
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (event.state == null) return;

        if (fire.get() && event.state.getBlock() instanceof FireBlock) {
            event.shape = Shapes.block();
        } else if (soulFire.get() && event.state.getBlock() instanceof SoulFireBlock) {
            event.shape = Shapes.block();
        } else if (lava.get() && event.state.getBlock() == Blocks.LAVA) {
            event.shape = Shapes.block();
        } else if (campfire.get() && event.state.getBlock() == Blocks.CAMPFIRE) {
            event.shape = Shapes.block();
        } else if (soulCampfire.get() && event.state.getBlock() == Blocks.SOUL_CAMPFIRE) {
            event.shape = Shapes.block();
        }
    }
}
