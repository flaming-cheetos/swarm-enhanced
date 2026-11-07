package com.rdh.swarm;

/**
 * Represents a waypoint for bot navigation.
 * Portal waypoints store both overworld and nether coordinates under one name.
 */
public class SwarmWaypoint {
    public String name;
    public String category;  // cave, wood, nether_cave, surface, portal, far
    
    // Overworld coordinates (null if not set from overworld)
    public Integer overworldX;
    public Integer overworldY;
    public Integer overworldZ;
    
    // Nether coordinates (null if not set from nether)
    public Integer netherX;
    public Integer netherY;
    public Integer netherZ;
    
    public long createdAt;
    
    public SwarmWaypoint() {
        this.createdAt = System.currentTimeMillis();
    }
    
    public SwarmWaypoint(String name, String category) {
        this.name = name;
        this.category = category;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Set coordinates based on current dimension
     */
    public void setCoords(int x, int y, int z, boolean isNether) {
        if (isNether) {
            this.netherX = x;
            this.netherY = y;
            this.netherZ = z;
        } else {
            this.overworldX = x;
            this.overworldY = y;
            this.overworldZ = z;
        }
    }
    
    public boolean hasOverworldCoords() {
        return overworldX != null && overworldY != null && overworldZ != null;
    }
    
    public boolean hasNetherCoords() {
        return netherX != null && netherY != null && netherZ != null;
    }
    
    public boolean isPortal() {
        return "portal".equalsIgnoreCase(category);
    }
    
    /**
     * Get coordinates for the specified dimension
     * @param nether true for nether coords, false for overworld
     * @return int[3] {x, y, z} or null if not set for that dimension
     */
    public int[] getCoords(boolean nether) {
        if (nether) {
            if (!hasNetherCoords()) return null;
            return new int[]{netherX, netherY, netherZ};
        } else {
            if (!hasOverworldCoords()) return null;
            return new int[]{overworldX, overworldY, overworldZ};
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" [").append(category).append("]");
        if (hasOverworldCoords()) {
            sb.append(" OW:").append(overworldX).append(",").append(overworldY).append(",").append(overworldZ);
        }
        if (hasNetherCoords()) {
            sb.append(" NETHER:").append(netherX).append(",").append(netherY).append(",").append(netherZ);
        }
        return sb.toString();
    }
}
