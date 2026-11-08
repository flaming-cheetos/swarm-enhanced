package com.rdh.swarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages waypoints per-server with JSON persistence.
 * File location: .minecraft/enhanced-swarm/waypoints/<server>/waypoints.json
 */
public class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Minecraft mc = Minecraft.getInstance();
    
    // server -> (waypoint name -> waypoint)
    private final Map<String, Map<String, SwarmWaypoint>> waypointsByServer = new ConcurrentHashMap<>();
    
    // Resource to category mapping
    private static final Map<String, String> RESOURCE_CATEGORIES = new HashMap<>();
    
    static {
        // Cave resources
        RESOURCE_CATEGORIES.put("diamond_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_diamond_ore", "cave");
        RESOURCE_CATEGORIES.put("iron_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_iron_ore", "cave");
        RESOURCE_CATEGORIES.put("gold_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_gold_ore", "cave");
        RESOURCE_CATEGORIES.put("copper_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_copper_ore", "cave");
        RESOURCE_CATEGORIES.put("coal_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_coal_ore", "cave");
        RESOURCE_CATEGORIES.put("lapis_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_lapis_ore", "cave");
        RESOURCE_CATEGORIES.put("redstone_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_redstone_ore", "cave");
        RESOURCE_CATEGORIES.put("emerald_ore", "cave");
        RESOURCE_CATEGORIES.put("deepslate_emerald_ore", "cave");
        RESOURCE_CATEGORIES.put("amethyst_block", "cave");
        RESOURCE_CATEGORIES.put("amethyst_cluster", "cave");
        
        // Nether resources
        RESOURCE_CATEGORIES.put("ancient_debris", "nether_cave");
        RESOURCE_CATEGORIES.put("nether_gold_ore", "nether_cave");
        RESOURCE_CATEGORIES.put("nether_quartz_ore", "nether_cave");
        RESOURCE_CATEGORIES.put("glowstone", "nether_cave");
        
        // Wood resources
        RESOURCE_CATEGORIES.put("oak_log", "wood");
        RESOURCE_CATEGORIES.put("birch_log", "wood");
        RESOURCE_CATEGORIES.put("spruce_log", "wood");
        RESOURCE_CATEGORIES.put("dark_oak_log", "wood");
        RESOURCE_CATEGORIES.put("jungle_log", "wood");
        RESOURCE_CATEGORIES.put("acacia_log", "wood");
        RESOURCE_CATEGORIES.put("mangrove_log", "wood");
        RESOURCE_CATEGORIES.put("cherry_log", "wood");
        RESOURCE_CATEGORIES.put("crimson_stem", "wood");
        RESOURCE_CATEGORIES.put("warped_stem", "wood");
        
        // Surface resources
        RESOURCE_CATEGORIES.put("sand", "surface");
        RESOURCE_CATEGORIES.put("red_sand", "surface");
        RESOURCE_CATEGORIES.put("clay", "surface");
        RESOURCE_CATEGORIES.put("gravel", "surface");
        RESOURCE_CATEGORIES.put("stone", "cave");
        RESOURCE_CATEGORIES.put("cobblestone", "cave");
    }
    
    /**
     * Get the category for a resource block/item name
     * @return category string, or "far" if not mapped (uses random staging fallback)
     */
    public static String getCategoryForResource(String resourceName) {
        if (resourceName == null) return "far";
        String clean = resourceName.toLowerCase().replace("minecraft:", "");
        if (clean.equals("cobblestone")) clean = "stone";
        return RESOURCE_CATEGORIES.getOrDefault(clean, "far");
    }
    
    /**
     * Check if a category requires nether dimension
     */
    public static boolean isNetherCategory(String category) {
        return "nether_cave".equalsIgnoreCase(category);
    }
    
    /**
     * Get current server identifier for storage
     */
    public String getCurrentServer() {
        if (mc.getCurrentServer() != null) {
            return sanitizeServerName(mc.getCurrentServer().ip);
        }
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            return "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }
    
    private String sanitizeServerName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Get waypoints directory for current server
     */
    private Path getWaypointsDir(String server) {
        Path mcDir = mc.gameDirectory.toPath();
        return mcDir.resolve("enhanced-swarm").resolve("waypoints").resolve(sanitizeServerName(server));
    }
    
    private Path getWaypointsFile(String server) {
        return getWaypointsDir(server).resolve("waypoints.json");
    }
    
    /**
     * Load waypoints for a server from disk
     */
    public void load(String server) {
        Path file = getWaypointsFile(server);
        if (!Files.exists(file)) {
            waypointsByServer.put(server, new ConcurrentHashMap<>());
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<Map<String, SwarmWaypoint>>(){}.getType();
            Map<String, SwarmWaypoint> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                waypointsByServer.put(server, new ConcurrentHashMap<>(loaded));
            } else {
                waypointsByServer.put(server, new ConcurrentHashMap<>());
            }
        } catch (Exception e) {
            ChatUtils.warningPrefix("Waypoints", "Failed to load waypoints: %s", e.getMessage());
            waypointsByServer.put(server, new ConcurrentHashMap<>());
        }
    }
    
    /**
     * Save waypoints for a server to disk
     */
    public void save(String server) {
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.get(server);
        if (waypoints == null) return;
        
        try {
            Path dir = getWaypointsDir(server);
            Files.createDirectories(dir);
            Path file = getWaypointsFile(server);
            
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(waypoints, writer);
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Waypoints", "Failed to save waypoints: %s", e.getMessage());
        }
    }
    
    /**
     * Ensure waypoints are loaded for current server
     */
    public void ensureLoaded() {
        String server = getCurrentServer();
        if (!waypointsByServer.containsKey(server)) {
            load(server);
        }
    }
    
    /**
     * Add or update a waypoint
     */
    public void addWaypoint(String name, String category, int x, int y, int z, boolean isNether) {
        ensureLoaded();
        String server = getCurrentServer();
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.computeIfAbsent(server, k -> new ConcurrentHashMap<>());
        
        SwarmWaypoint wp = waypoints.get(name.toLowerCase());
        if (wp == null) {
            wp = new SwarmWaypoint(name.toLowerCase(), category.toLowerCase());
        }
        wp.setCoords(x, y, z, isNether);
        waypoints.put(name.toLowerCase(), wp);
        save(server);
    }
    
    /**
     * Add a portal waypoint (auto-detects dimension)
     */
    public void addPortalWaypoint(String name, int x, int y, int z, boolean isNether) {
        addWaypoint(name, "portal", x, y, z, isNether);
    }
    
    /**
     * Remove a waypoint by name
     */
    public boolean removeWaypoint(String name) {
        ensureLoaded();
        String server = getCurrentServer();
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.get(server);
        if (waypoints == null) return false;
        
        SwarmWaypoint removed = waypoints.remove(name.toLowerCase());
        if (removed != null) {
            save(server);
            return true;
        }
        return false;
    }
    
    /**
     * Get a waypoint by name
     */
    public SwarmWaypoint getWaypoint(String name) {
        ensureLoaded();
        String server = getCurrentServer();
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.get(server);
        if (waypoints == null) return null;
        return waypoints.get(name.toLowerCase());
    }
    
    /**
     * Get all waypoints for a category
     */
    public List<SwarmWaypoint> getWaypointsByCategory(String category) {
        ensureLoaded();
        String server = getCurrentServer();
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.get(server);
        if (waypoints == null) return Collections.emptyList();
        
        List<SwarmWaypoint> result = new ArrayList<>();
        for (SwarmWaypoint wp : waypoints.values()) {
            if (category.equalsIgnoreCase(wp.category)) {
                result.add(wp);
            }
        }
        return result;
    }
    
    /**
     * Get the first portal waypoint (for v1, user will have one portal)
     */
    public SwarmWaypoint getFirstPortal() {
        List<SwarmWaypoint> portals = getWaypointsByCategory("portal");
        return portals.isEmpty() ? null : portals.get(0);
    }
    
    /**
     * Get all waypoints for current server
     */
    public Collection<SwarmWaypoint> getAllWaypoints() {
        ensureLoaded();
        String server = getCurrentServer();
        Map<String, SwarmWaypoint> waypoints = waypointsByServer.get(server);
        if (waypoints == null) return Collections.emptyList();
        return waypoints.values();
    }
    
    /**
     * Find best waypoint for a resource
     * @param resourceName the block/item being gathered
     * @param inNether whether bot is currently in nether
     * @return waypoint to use, or null to fall back to random staging
     */
    public SwarmWaypoint findWaypointForResource(String resourceName, boolean inNether) {
        String category = getCategoryForResource(resourceName);
        
        // If resource is nether category but we're in overworld, we need portal first
        // That logic is handled by BotBrain, here we just find the right waypoint
        
        List<SwarmWaypoint> candidates = getWaypointsByCategory(category);
        if (candidates.isEmpty()) return null;
        
        // For now, return first matching waypoint
        // Future: could pick closest, least-used, etc.
        for (SwarmWaypoint wp : candidates) {
            // Make sure it has coords for the dimension we're in
            if (inNether && wp.hasNetherCoords()) return wp;
            if (!inNether && wp.hasOverworldCoords()) return wp;
        }
        
        return null;
    }
}
