package com.rdh.swarm.task;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.context.ContextMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SmeltingManager {

    /** Raw food -> cooked result; the existing furnace states consume the raw input. */
    public static final java.util.Map<Item, Item> COOKING = java.util.Map.copyOf(FoodManager.COOKING);

    public enum FuelState { ABSENT, UNRESOLVABLE, RESOLVED }

    public record FuelValue(FuelState state, int ticksPerItem, String reason) {
        public boolean resolved() {
            return state == FuelState.RESOLVED;
        }
    }

    private static final Set<String> UNRESOLVABLE_WARNED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> LAST_FUEL_LOG = new ConcurrentHashMap<>();
    private static volatile String lastFuelProblem = null;

    public static boolean isCookable(Item item) {
        return item != null && COOKING.containsKey(item);
    }

    public static Item cookedResult(Item item) {
        return item == null ? null : COOKING.get(item);
    }

    public static boolean depositFood(Item raw) {
        return isCookable(raw) && depositOre(raw);
    }

    public static boolean isFurnaceOpen() {
        return mc.player != null &&
               mc.player.containerMenu instanceof AbstractFurnaceMenu;
    }

    public static boolean openFurnace(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return false;
        try {
            mc.player.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(pos)
            );
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return true;
        } catch (Exception e) { return false; }
    }

    // Deposit ore into furnace input slot
    public static boolean depositOre(Item ore) {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        int slot = findItemInInventorySlots(handler, ore);
        if (slot == -1) return false;

        InvUtils.move().fromId(slot).toId(0);
        return true;
    }

    // Deposit fuel into furnace fuel slot
    public static boolean depositFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        // Check if fuel slot already has fuel
        ItemStack existing = handler.slots.get(1).getItem();
        FuelValue existingValue = fuelValue(existing, null);
        if (existingValue.resolved()) {
            ChatUtils.infoPrefix("Smelting", "FUEL: %s x%d = %d ticks in furnace",
                itemId(existing), existing.getCount(), existingValue.ticksPerItem() * existing.getCount());
            return true;
        }

        int slot = findFuelInInventorySlots(handler);
        if (slot == -1) {
            ChatUtils.infoPrefix("Smelting", "FUEL: none — no usable fuel in inventory or furnace");
            return false;
        }
        ItemStack fuel = handler.slots.get(slot).getItem();
        FuelValue value = fuelValue(fuel, null);
        if (!value.resolved()) return false;
        ChatUtils.infoPrefix("Smelting", "FUEL: %s x%d = %d ticks available",
            itemId(fuel), fuel.getCount(), value.ticksPerItem() * fuel.getCount());
        InvUtils.move().fromId(slot).toId(1);
        return true;
    }

    // Take result from furnace output slot
    public static boolean takeResult() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        ItemStack result = handler.slots.get(2).getItem();
        if (result.isEmpty()) return false;

        InvUtils.shiftClick().slotId(2);
        return true;
    }

    // Check if furnace has fuel remaining
    public static boolean hasFuel() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        return handler.getLitProgress() > 0 || fuelValue(handler.slots.get(1).getItem(), null).resolved();
    }

    private static boolean isFuel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.COOKING_FUEL);
    }

    /**
     * Resolve a fuel through the same path vanilla's furnace uses:
     * ResolvableInt.getFromItem(... CookingFuel::burnTime, furnace LootContext, 0).
     */
    public static FuelValue fuelValue(ItemStack stack, BlockPos furnacePos) {
        if (!isFuel(stack)) return new FuelValue(FuelState.ABSENT, 0, "not a fuel");
        try {
            LootContext context = buildFuelContext(furnacePos);
            if (context == null) {
                return unresolvable(stack, "no server level (multiplayer client)");
            }
            int ticks = ResolvableInt.getFromItem(
                stack,
                DataComponents.COOKING_FUEL,
                CookingFuel::burnTime,
                context,
                0
            );
            if (ticks <= 0) {
                return unresolvable(stack, "resolver returned " + ticks + " ticks");
            }
            return new FuelValue(FuelState.RESOLVED, ticks, "");
        } catch (Exception e) {
            return unresolvable(stack, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static FuelValue unresolvable(ItemStack stack, String reason) {
        String id = itemId(stack);
        lastFuelProblem = id + " — " + reason;
        if (UNRESOLVABLE_WARNED.add(id)) {
            ChatUtils.warningPrefix("Smelting",
                "FUEL: %s is a fuel but its burn time could not be resolved: %s; cooking blocked",
                id, reason);
        }
        return new FuelValue(FuelState.UNRESOLVABLE, 0, reason);
    }

    private static LootContext buildFuelContext(BlockPos furnacePos) {
        ServerLevel level = serverLevel();
        if (level == null) return null;

        BlockState state = Blocks.FURNACE.defaultBlockState();
        AbstractFurnaceBlockEntity furnace = null;
        BlockPos origin = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        if (furnacePos != null && mc.level != null
            && mc.level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity found) {
            furnace = found;
            state = found.getBlockState();
            origin = furnacePos;
        }

        ContextMap.Builder params = ContextMap.builder()
            .set(LootContextParams.BLOCK_STATE, state)
            .set(LootContextParams.ORIGIN, Vec3.atCenterOf(origin));
        if (furnace != null) {
            params.set(LootContextParams.BLOCK_ENTITY, furnace);
            params.set(LootContextParams.CONTAINER, furnace);
        }

        LootParams lootParams = new LootParams(level, params.build(), Map.of(), 0f);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    private static ServerLevel serverLevel() {
        try {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null || mc.level == null) return null;
            return server.getLevel(mc.level.dimension());
        } catch (Exception e) {
            return null;
        }
    }

    /** Fuel already sitting in a furnace, readable without opening the screen. */
    public static ItemStack fuelStackAt(BlockPos pos) {
        if (pos == null || mc.level == null) return ItemStack.EMPTY;
        if (mc.level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return furnace.getItem(1);
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasFuelAt(BlockPos pos) {
        return fuelValue(fuelStackAt(pos), pos).resolved();
    }

    // Count fuel in player inventory, multiplied by stack size.
    public static int countFuel() {
        return countFuel(null);
    }

    public static int countFuel(BlockPos furnacePos) {
        lastFuelProblem = null;
        if (mc.player == null) return 0;

        int total = 0;
        Map<Item, Integer> counts = new LinkedHashMap<>();
        Map<Item, Integer> byItem = new LinkedHashMap<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            FuelValue value = fuelValue(stack, furnacePos);
            if (!value.resolved()) continue;
            int ticks = value.ticksPerItem() * stack.getCount();
            total += ticks;
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            byItem.merge(stack.getItem(), ticks, Integer::sum);
        }

        for (var entry : byItem.entrySet()) {
            String id = itemId(entry.getKey());
            int ticks = entry.getValue();
            Integer previous = LAST_FUEL_LOG.put(id, ticks);
            if (previous == null || previous != ticks) {
                ChatUtils.infoPrefix("Smelting", "FUEL: %s x%d = %d ticks",
                    id, counts.getOrDefault(entry.getKey(), 0), ticks);
            }
        }
        return total;
    }

    /** Human-readable fuel inventory for the cook decision log. */
    public static String inventoryFuelSummary() {
        if (mc.player == null) return "none";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFuel(stack)) continue;
            FuelValue value = fuelValue(stack, null);
            if (out.length() > 0) out.append(',');
            out.append(itemId(stack))
               .append(" x").append(stack.getCount()).append(" = ");
            if (value.resolved()) {
                out.append(value.ticksPerItem() * stack.getCount()).append(" ticks");
            } else {
                out.append("UNRESOLVABLE (").append(value.reason()).append(')');
            }
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    /** A clear fuel reason for the cook decision, including unresolved fuels. */
    public static String fuelStatus() {
        return lastFuelProblem != null ? "unresolvable: " + lastFuelProblem : "no usable fuel";
    }

    public static String fuelDecision() {
        return "skip: " + fuelStatus();
    }

    /** Ticks per single item, for display and arithmetic. */
    public static int fuelTicksForItem(Item item) {
        if (item == null) return 0;
        return fuelValue(new ItemStack(item), null).ticksPerItem();
    }

    /** Smelts per item, matching the planner's original fuel-power unit. */
    public static int fuelPower(Item item) {
        int ticks = fuelTicksForItem(item);
        return ticks <= 0 ? 0 : Math.max(1, ticks / 200);
    }

    private static String itemId(ItemStack stack) {
        return itemId(stack.getItem());
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static int findFuelInInventorySlots(AbstractFurnaceMenu handler) {
        for (int i = 3; i < handler.slots.size(); i++) {
            ItemStack stack = handler.slots.get(i).getItem();
            if (fuelValue(stack, null).resolved()) return i;
        }
        return -1;
    }

    private static int findItemInInventorySlots(AbstractFurnaceMenu handler, Item item) {
        // Furnace: slots 0-2 are furnace, 3+ are player inventory
        for (int i = 3; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).getItem().getItem() == item &&
                !handler.slots.get(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void closeFurnace() {
        if (mc.player != null) {
            mc.setScreenAndShow(null);
        }
    }

    public static boolean hasInput() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return !handler.slots.get(0).getItem().isEmpty();
    }

    public static int getOutputCount() {
        if (!isFurnaceOpen()) return 0;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return handler.slots.get(2).getItem().getCount();
    }
}
