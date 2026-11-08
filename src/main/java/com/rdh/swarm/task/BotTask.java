package com.rdh.swarm.task;

import net.minecraft.world.item.Item;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BotTask {

    public enum Type { GATHER, CRAFT, SMELT, EQUIP, DEPOSIT, SCAN_CHESTS, HUNT_FOOD, GRIND }
    public enum Status { PENDING, ACTIVE, BLOCKED, COMPLETE, FAILED }

    private final UUID id;
    private final Type type;
    private final int priority;
    private final Map<String, Object> data;
    private final Instant createdAt;

    private Item targetItem;
    private Item sourceItem;
    private int targetQuantity;
    private int collectedQuantity;
    private Status status;
    private String assignedBot;

    // Primary constructor used by system code
    public BotTask(UUID id, Type type, int priority, Map<String, Object> data) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.data = data;
        this.createdAt = Instant.now();
        this.status = Status.PENDING;
        this.collectedQuantity = 0;

        // Try to populate targetItem/quantity from data map if present
        try {
            Object it = data.get("item");
            if (it instanceof Item) this.targetItem = (Item) it;
            Object q = data.get("quantity");
            if (q instanceof Number) this.targetQuantity = ((Number) q).intValue();
            Object s = data.get("source");
            if (s instanceof Item) this.sourceItem = (Item) s;
        } catch (Exception ignored) {}
    }

    // Convenience constructor for newly created tasks
    public BotTask(Type type, Item targetItem, int quantity) {
        this(UUID.randomUUID(), type, 0, Map.of());
        this.targetItem = targetItem;
        this.targetQuantity = quantity;
    }

    public UUID getId() { return id; }
    public Type getType() { return type; }
    public int getPriority() { return priority; }
    public Map<String, Object> getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }

    public Item getTargetItem() { return targetItem; }
    public Item getSourceItem() { return sourceItem; }
    public int getTargetQuantity() { return targetQuantity; }
    public int getRemaining() { return Math.max(0, targetQuantity - collectedQuantity); }
    public int getCollectedQuantity() { return collectedQuantity; }
    public void addCollected(int amount) { this.collectedQuantity += amount; }
    public boolean isComplete() { return getRemaining() <= 0; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getAssignedBot() { return assignedBot; }
    public void setAssignedBot(String assignedBot) { this.assignedBot = assignedBot; }

    public String getProgressString() { return String.format("%d/%d", collectedQuantity, targetQuantity); }

    public String getItemName() {
        try {
            return targetItem == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).getPath();
        } catch (Exception e) { return ""; }
    }

    public String getElapsedString() {
        Duration d = Duration.between(createdAt, Instant.now());
        long s = d.getSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s/60) + "m " + (s%60) + "s";
        return (s/3600) + "h " + ((s%3600)/60) + "m";
    }

    @Override
    public String toString() {
        return "BotTask{" +
            "id=" + id +
            ", type=" + type +
            ", target=" + getItemName() +
            ", qty=" + targetQuantity +
            ", collected=" + collectedQuantity +
            ", status=" + status +
            '}';
    }
}
