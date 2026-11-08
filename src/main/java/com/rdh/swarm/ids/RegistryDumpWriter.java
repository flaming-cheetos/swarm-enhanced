package com.rdh.swarm.ids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes registry dumps for the /eswarm ids command.
 *
 * <p>All output is TSV, sorted by the first column. No game state is modified.</p>
 */
public final class RegistryDumpWriter {
    public static final String ITEMS_FILE = "items.txt";
    public static final String BLOCKS_FILE = "blocks.txt";
    public static final String TAGS_FILE = "item-tags.txt";
    public static final String FOODS_FILE = "foods.txt";

    private RegistryDumpWriter() {
    }

    public static Path writeAll(Path directory) throws IOException {
        Files.createDirectories(directory);
        writeItems(directory);
        writeBlocks(directory);
        writeTags(directory);
        writeFoods(directory);
        return directory.toAbsolutePath();
    }

    public static Path writeItems(Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            int maxStack = item.getDefaultMaxStackSize();
            lines.add(itemId(item) + "\t"
                    + item.getClass().getName() + "\t"
                    + maxStack + "\t"
                    + (maxStack > 1));
        }
        return writeSorted(directory, ITEMS_FILE, lines);
    }

    public static Path writeBlocks(Path directory) throws IOException {
        List<Block> blocks = BuiltInRegistries.BLOCK.stream().toList();
        List<BlockEntityType<?>> blockEntityTypes = BuiltInRegistries.BLOCK_ENTITY_TYPE.stream()
                .sorted(Comparator.comparing(type -> String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type))))
                .toList();

        Map<Block, BlockEntityType<?>> blockEntityTypesByBlock = new IdentityHashMap<>();
        for (Block block : blocks) {
            BlockState state = block.defaultBlockState();
            if (!state.hasBlockEntity()) {
                continue;
            }
            for (BlockEntityType<?> type : blockEntityTypes) {
                if (type.isValid(state)) {
                    blockEntityTypesByBlock.put(block, type);
                    break;
                }
            }
        }

        List<String> lines = new ArrayList<>();
        for (Block block : blocks) {
            lines.add(blockId(block) + "\t"
                    + block.getClass().getName() + "\t"
                    + blockEntityClass(block, blockEntityTypesByBlock.get(block)));
        }
        return writeSorted(directory, BLOCKS_FILE, lines);
    }

    public static Path writeTags(Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> {
            List<String> ids = tag.stream()
                    .map(holder -> itemId(holder.value()))
                    .distinct()
                    .sorted()
                    .toList();
            lines.add("#" + tag.key().location() + "\t" + String.join(",", ids));
        });
        return writeSorted(directory, TAGS_FILE, lines);
    }

    public static Path writeFoods(Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            FoodProperties food = item.components().get(DataComponents.FOOD);
            if (food == null) {
                continue;
            }
            lines.add(itemId(item) + "\t" + food.nutrition() + "\t" + food.saturation());
        }
        return writeSorted(directory, FOODS_FILE, lines);
    }

    private static String blockEntityClass(Block block, BlockEntityType<?> type) {
        if (type == null) {
            return "none";
        }
        try {
            BlockEntity blockEntity = type.create(BlockPos.ZERO, block.defaultBlockState());
            return blockEntity == null ? "none" : blockEntity.getClass().getName();
        } catch (Throwable ignored) {
            // A block entity that cannot be constructed without a level is still a
            // block entity; report that instead of pretending the block has none.
            return "unknown";
        }
    }

    private static String itemId(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "unknown" : id.toString();
    }

    private static String blockId(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "unknown" : id.toString();
    }

    private static Path writeSorted(Path directory, String fileName, List<String> lines) throws IOException {
        Files.createDirectories(directory);
        lines.sort(Comparator.naturalOrder());
        Path file = directory.resolve(fileName);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file.toAbsolutePath();
    }
}
