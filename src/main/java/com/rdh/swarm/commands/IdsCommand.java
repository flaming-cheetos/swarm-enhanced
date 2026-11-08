package com.rdh.swarm.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rdh.swarm.ids.RegistryDumpWriter;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Path;

/**
 * Local-only registry inspection: /eswarm ids {...}.
 *
 * <p>This reads client registries and the local game directory. It never sends a
 * packet and never changes world state.</p>
 */
public final class IdsCommand {
    private static final String USAGE = "Usage: /eswarm ids <items|blocks|tags|foods|hand|looking|all>";

    private IdsCommand() {
    }

    public static void attach(Command owner, LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        LiteralArgumentBuilder<ClientSuggestionProvider> ids = LiteralArgumentBuilder.literal("ids");
        ids.executes(context -> {
            owner.info(USAGE);
            return 1;
        });
        ids.then(mode(owner, "items"));
        ids.then(mode(owner, "blocks"));
        ids.then(mode(owner, "tags"));
        ids.then(mode(owner, "foods"));
        ids.then(mode(owner, "hand"));
        ids.then(mode(owner, "looking"));
        ids.then(mode(owner, "all"));
        builder.then(ids);
    }

    private static LiteralArgumentBuilder<ClientSuggestionProvider> mode(Command owner, String name) {
        return LiteralArgumentBuilder.<ClientSuggestionProvider>literal(name)
                .executes(context -> run(owner, name));
    }

    private static int run(Command owner, String mode) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                owner.error("Minecraft client is not available");
                return 1;
            }

            if (mode.equals("hand")) {
                return printHand(owner, mc);
            }
            if (mode.equals("looking")) {
                return printLooking(owner, mc);
            }

            if (mc.gameDirectory == null) {
                owner.error("Game directory is not available");
                return 1;
            }

            Path directory = mc.gameDirectory.toPath().resolve("swarm-ids");
            switch (mode) {
                case "items" -> owner.info("Wrote %s", RegistryDumpWriter.writeItems(directory));
                case "blocks" -> owner.info("Wrote %s", RegistryDumpWriter.writeBlocks(directory));
                case "tags" -> owner.info("Wrote %s", RegistryDumpWriter.writeTags(directory));
                case "foods" -> owner.info("Wrote %s", RegistryDumpWriter.writeFoods(directory));
                case "all" -> {
                    RegistryDumpWriter.writeAll(directory);
                    owner.info("Wrote %s", directory.toAbsolutePath());
                }
                default -> owner.info(USAGE);
            }
        } catch (Throwable t) {
            owner.error("IDs dump failed: %s", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
        return 1;
    }

    private static int printHand(Command owner, Minecraft mc) {
        if (mc.player == null) {
            owner.error("Not in a world");
            return 1;
        }
        ItemStack held = mc.player.getMainHandItem();
        owner.info("%s", BuiltInRegistries.ITEM.getKey(held.getItem()));
        return 1;
    }

    private static int printLooking(Command owner, Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            owner.error("Not in a world");
            return 1;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            owner.info("looking: none");
            return 1;
        }

        double distanceSquared = mc.player.getEyePosition().distanceToSqr(hit.getLocation());
        if (distanceSquared > 25.0D) {
            owner.info("looking: none within 5 blocks");
            return 1;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        owner.info("%s", BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return 1;
    }
}
