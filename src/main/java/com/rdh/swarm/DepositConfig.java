package com.rdh.swarm;

import com.google.gson.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server, per-category deposit chest configuration.
 * Persists to meteor-client/enhanced-swarm/deposit-chests.json
 */
public class DepositConfig {
    private static final Path CONFIG_DIR = Path.of("meteor-client", "enhanced-swarm");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("deposit-chests.json");

    // serverAddress -> (category -> [{x,y,z}, ...])
    private final Map<String, Map<String, List<int[]>>> serverChests = new ConcurrentHashMap<>();
    // serverAddress -> base anchor {x,y,z}
    private final Map<String, int[]> serverBases = new ConcurrentHashMap<>();
    private static final String _da = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073\u002F\u0072\u0064\u0068\u0020\u0066\u006F\u0072\u0065\u0076\u0065\u0072";
    private static final long _dn = 0x666C616D696E67L;
    private static final int _dx = 0x726468;

    // Built-in item categories — "__CONTAINS:xxx" = substring match on item ID
    private static final Map<String, Set<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("ores", Set.of(
            "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
            "minecraft:iron_ingot", "minecraft:coal", "minecraft:lapis_lazuli",
            "minecraft:redstone", "minecraft:copper_ingot", "minecraft:ancient_debris",
            "minecraft:netherite_ingot", "minecraft:netherite_scrap",
            "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
            "minecraft:quartz", "minecraft:amethyst_shard",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:gold_nugget", "minecraft:iron_nugget"
        ));

        CATEGORIES.put("shulkers", Set.of("__CONTAINS:shulker_box"));
        CATEGORIES.put("empty_shulkers", Set.of("__CONTAINS:shulker_box"));

        CATEGORIES.put("tools", Set.of(
            "__CONTAINS:pickaxe", "__CONTAINS:axe", "__CONTAINS:shovel", "__CONTAINS:hoe", "__CONTAINS:sword"
        ));

        CATEGORIES.put("armor", Set.of(
            "__CONTAINS:helmet", "__CONTAINS:chestplate", "__CONTAINS:leggings", "__CONTAINS:boots"
        ));

        CATEGORIES.put("building", Set.of(
            "minecraft:cobblestone", "minecraft:stone", "minecraft:dirt",
            "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
            "minecraft:sand", "minecraft:gravel", "minecraft:netherrack",
            "minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:deepslate",
            "minecraft:sandstone", "minecraft:red_sandstone",
            "minecraft:smooth_stone", "minecraft:bricks",
            "minecraft:mossy_cobblestone", "minecraft:obsidian",
            "minecraft:end_stone", "minecraft:basalt", "minecraft:blackstone",
            "minecraft:calcite", "minecraft:dripstone_block",
            "minecraft:grass_block", "minecraft:mud", "minecraft:clay"
        ));

        CATEGORIES.put("valuables", Set.of(
            "minecraft:diamond", "minecraft:emerald", "minecraft:netherite_ingot",
            "minecraft:enchanted_golden_apple", "minecraft:elytra",
            "minecraft:totem_of_undying", "minecraft:beacon",
            "minecraft:nether_star", "minecraft:heart_of_the_sea",
            "minecraft:dragon_egg", "minecraft:dragon_breath",
            "minecraft:diamond_block", "minecraft:emerald_block",
            "minecraft:netherite_block", "minecraft:gold_block"
        ));

        CATEGORIES.put("food", Set.of(
            "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
            "minecraft:cooked_mutton", "minecraft:cooked_cod", "minecraft:cooked_salmon",
            "minecraft:cooked_rabbit", "minecraft:bread", "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple", "minecraft:golden_carrot",
            "minecraft:baked_potato", "minecraft:mushroom_stew", "minecraft:beetroot_soup",
            "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:melon_slice",
            "minecraft:apple", "minecraft:sweet_berries", "minecraft:carrot",
            "minecraft:dried_kelp", "minecraft:rabbit_stew", "minecraft:suspicious_stew"
        ));
    }

    /**
     * Stack-aware category check. Shulker contents live in the CONTAINER data
     * component in 26.3; empty and non-empty shulkers must route differently.
     */
    public static boolean matchesCategory(String category, ItemStack stack) {
        if (stack == null || stack.isEmpty() || category == null) return false;
        String itemId;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Exception e) {
            return false;
        }
        if (category.equalsIgnoreCase("empty_shulkers")) {
            return isShulkerId(itemId) && isEmptyShulker(stack);
        }
        if (category.equalsIgnoreCase("shulkers")) {
            return isShulkerId(itemId) && !isEmptyShulker(stack);
        }
        return matchesCategory(category, itemId);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return isShulkerId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isShulkerId(String itemId) {
        return itemId != null && itemId.contains("shulker_box");
    }

    private static boolean isEmptyShulker(ItemStack stack) {
        try {
            var contents = stack.get(DataComponents.CONTAINER);
            return contents == null || contents.nonEmptyItemCopyStream().findAny().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    /** Check if an item ID matches a category. "all" matches everything. */
    public static boolean matchesCategory(String category, String itemId) {
        if (category == null || itemId == null) return false;
        if (category.equalsIgnoreCase("all")) return true;

        Set<String> items = CATEGORIES.get(category.toLowerCase());
        if (items == null) return false;

        for (String matcher : items) {
            if (matcher.startsWith("__CONTAINS:")) {
                if (itemId.contains(matcher.substring(11))) return true;
            } else {
                if (matcher.equals(itemId)) return true;
            }
        }
        return false;
    }

    /** All known category names including "all" */
    public static List<String> getCategoryNames() {
        List<String> names = new ArrayList<>(CATEGORIES.keySet());
        names.add(0, "all");
        return names;
    }

    // ---- Per-server storage ----

    public Map<String, List<int[]>> getChests(String serverAddress) {
        Map<String, List<int[]>> chests = serverChests.get(normalize(serverAddress));
        if (chests == null) return Collections.emptyMap();
        Map<String, List<int[]>> copy = new LinkedHashMap<>();
        for (var e : chests.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    // ---- Base anchor handling ----
    public void setBase(String serverAddress, int x, int y, int z) {
        serverBases.put(normalize(serverAddress), new int[]{x, y, z});
        save();
    }

    public int[] getBase(String serverAddress) {
        return serverBases.get(normalize(serverAddress));
    }

    public boolean hasBase(String serverAddress) {
        return serverBases.containsKey(normalize(serverAddress));
    }

    public void removeBase(String serverAddress) {
        serverBases.remove(normalize(serverAddress));
        save();
    }

    /** Add a chest entry to a category (appends). */
    public void addChest(String serverAddress, String category, int x, int y, int z) {
        serverChests.computeIfAbsent(normalize(serverAddress), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(category.toLowerCase(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new int[]{x, y, z});
        save();
    }

    /** Backwards-compatible setter which replaces the category with a single chest. */
    public void setChest(String serverAddress, String category, int x, int y, int z) {
        List<int[]> list = Collections.synchronizedList(new ArrayList<>());
        list.add(new int[]{x, y, z});
        serverChests.computeIfAbsent(normalize(serverAddress), k -> new ConcurrentHashMap<>()).put(category.toLowerCase(), list);
        save();
    }

    /** Remove all chests for a category. */
    public void removeChest(String serverAddress, String category) {
        Map<String, List<int[]>> chests = serverChests.get(normalize(serverAddress));
        if (chests != null) {
            chests.remove(category.toLowerCase());
            save();
        }
    }

    /** Remove a specific chest by 1-based index in a category. */
    public void removeChest(String serverAddress, String category, int indexOneBased) {
        Map<String, List<int[]>> chests = serverChests.get(normalize(serverAddress));
        if (chests == null) return;
        List<int[]> list = chests.get(category.toLowerCase());
        if (list == null) return;
        int idx = indexOneBased - 1;
        if (idx >= 0 && idx < list.size()) {
            list.remove(idx);
            if (list.isEmpty()) chests.remove(category.toLowerCase());
            save();
        }
    }

    /** Get the first chest for a category (backwards compatible). */
    public int[] getChest(String serverAddress, String category) {
        Map<String, List<int[]>> chests = serverChests.get(normalize(serverAddress));
        if (chests == null) return null;
        List<int[]> list = chests.get(category.toLowerCase());
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    public boolean hasChest(String serverAddress, String category) {
        Map<String, List<int[]>> chests = serverChests.get(normalize(serverAddress));
        return chests != null && chests.containsKey(category.toLowerCase()) && !chests.get(category.toLowerCase()).isEmpty();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            for (var entry : serverChests.entrySet()) {
                JsonObject serverObj = new JsonObject();
                for (var chest : entry.getValue().entrySet()) {
                    // chest.getValue() is a List<int[]>
                    var list = chest.getValue();
                    JsonArray arr = new JsonArray();
                    for (int[] p : list) {
                        JsonObject posObj = new JsonObject();
                        posObj.addProperty("x", p[0]);
                        posObj.addProperty("y", p[1]);
                        posObj.addProperty("z", p[2]);
                        arr.add(posObj);
                    }
                    serverObj.add(chest.getKey(), arr);
                }
                root.add(entry.getKey(), serverObj);
            }

            // also persist bases for servers
            for (var be : serverBases.entrySet()) {
                String server = be.getKey();
                int[] p = be.getValue();
                JsonObject baseObj = new JsonObject();
                baseObj.addProperty("x", p[0]);
                baseObj.addProperty("y", p[1]);
                baseObj.addProperty("z", p[2]);
                // if server already exists, merge; otherwise add as server entry
                if (root.has(server)) {
                    JsonObject so = root.getAsJsonObject(server);
                    so.add("__base", baseObj);
                } else {
                    JsonObject so = new JsonObject();
                    so.add("__base", baseObj);
                    root.add(server, so);
                }
            }
            Files.writeString(CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void load() {
        try {
            if (!Files.exists(CONFIG_FILE)) return;
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            serverChests.clear();
            serverBases.clear();
            for (var serverEntry : root.entrySet()) {
                Map<String, List<int[]>> chests = new ConcurrentHashMap<>();
                JsonObject serverObj = serverEntry.getValue().getAsJsonObject();
                for (var chestEntry : serverObj.entrySet()) {
                    // skip special base anchor entry if present
                    if ("__base".equals(chestEntry.getKey())) continue;

                    // support both legacy object {x,y,z} and new array [{x,y,z}, ...]
                    try {
                        if (chestEntry.getValue().isJsonArray()) {
                            List<int[]> list = Collections.synchronizedList(new ArrayList<>());
                            for (JsonElement el : chestEntry.getValue().getAsJsonArray()) {
                                JsonObject posObj = el.getAsJsonObject();
                                list.add(new int[]{ posObj.get("x").getAsInt(), posObj.get("y").getAsInt(), posObj.get("z").getAsInt() });
                            }
                            if (!list.isEmpty()) chests.put(chestEntry.getKey(), list);
                        } else if (chestEntry.getValue().isJsonObject()) {
                            JsonObject posObj = chestEntry.getValue().getAsJsonObject();
                            List<int[]> list = Collections.synchronizedList(new ArrayList<>());
                            list.add(new int[]{ posObj.get("x").getAsInt(), posObj.get("y").getAsInt(), posObj.get("z").getAsInt() });
                            chests.put(chestEntry.getKey(), list);
                        }
                    } catch (Exception ignored) {}
                }
                serverChests.put(serverEntry.getKey(), chests);
                // check for base entry
                try {
                    if (serverObj.has("__base") && serverObj.get("__base").isJsonObject()) {
                        JsonObject b = serverObj.getAsJsonObject("__base");
                        int bx = b.get("x").getAsInt();
                        int by = b.get("y").getAsInt();
                        int bz = b.get("z").getAsInt();
                        serverBases.put(serverEntry.getKey(), new int[]{bx, by, bz});
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String normalize(String addr) {
        if (addr == null || addr.isEmpty()) return "unknown";
        return addr.toLowerCase().trim();
    }
}
