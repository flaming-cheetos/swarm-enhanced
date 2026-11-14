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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** One fuel type in the player's inventory, resolved through the cook path's reader. */
    public record FuelEntry(String id, int count, int ticksPerItem, int ticks, boolean resolved, String reason) {}

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
    private static boolean oreClickMovePending = false;
    private static long lastOreMissLog = 0L;

    public static boolean depositOre(Item ore) {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;

        if (inputHasOre(ore)) { oreClickMovePending = false; return true; }

        int slot = findItemInInventorySlots(handler, ore);
        if (slot == -1) {
            if (System.currentTimeMillis() - lastOreMissLog > 1000L) {
                lastOreMissLog = System.currentTimeMillis();
                int free = 0;
                StringBuilder held = new StringBuilder();
                for (int i = 3; i < handler.slots.size(); i++) {
                    net.minecraft.world.item.ItemStack s = handler.slots.get(i).getItem();
                    if (s.isEmpty()) { free++; continue; }
                    if (held.length() < 90) {
                        held.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath())
                            .append('x').append(s.getCount()).append(' ');
                    }
                }
                ChatUtils.infoPrefix("Smelting", "ORE: %s not in inventory (free=%d/%d input=%s held=[%s])",
                    ore, free, handler.slots.size() - 3, handler.slots.get(0).getItem().getCount(), held.toString().trim());
            }
            return false;
        }

        if (!oreClickMovePending) {
            // First attempt: quick-move through the menu's own path.
            oreClickMovePending = true;
            InvUtils.shiftClick().slotId(slot);
        } else {
            // Quick-move did not land. Pick the stack up onto the cursor and place
            // it in the input slot; the caller verifies the slot each tick, so this
            // only runs while the furnace is still empty.
            oreClickMovePending = false;
            InvUtils.click().slotId(slot);
            InvUtils.click().slotId(0);
        }
        ChatUtils.infoPrefix("Smelting", "ORE: attempted insert from slot %d (input now %d)",
            slot, handler.slots.get(0).getItem().getCount());
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

        // Client-side truth first: a constant burn time is carried in the item's
        // COOKING_FUEL component and needs no world at all (ResolvableInt.Constant
        // is a plain int record — verified against minecraft-merged 26.3). Vanilla
        // fuels such as coal and logs are constants, so a multiplayer client can
        // read them straight off the stack instead of being blocked until a
        // ServerLevel exists.
        try {
            CookingFuel component = stack.get(DataComponents.COOKING_FUEL);
            if (component != null && component.burnTime() instanceof ResolvableInt.Constant constant) {
                int constantTicks = constant.value();
                if (constantTicks > 0) return new FuelValue(FuelState.RESOLVED, constantTicks, "constant");
            }
        } catch (Exception ignored) {
        }

        try {
            LootContext context = buildFuelContext(furnacePos);
            if (context == null) {
                // Multiplayer client: no ServerLevel, so the loot-context resolver
                // cannot run. Fall back to published vanilla burn times so cooking
                // is not blocked; every value is the item's furnace burn time in
                // ticks (vanilla CookingFuel data). Anything unlisted is treated as
                // a short generic fuel and the wait loop still watches real progress.
                int estimated = estimatedBurnTicks(stack);
                if (estimated > 0) return new FuelValue(FuelState.RESOLVED, estimated, "estimated");
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

    /** Vanilla furnace burn times in ticks, used only when the resolver has no ServerLevel. */
    private static int estimatedBurnTicks(ItemStack stack) {
        String id = itemId(stack);
        int exact = switch (id) {
            case "minecraft:coal", "minecraft:charcoal" -> 1600;
            case "minecraft:coal_block" -> 16000;
            case "minecraft:blaze_rod" -> 2400;
            case "minecraft:lava_bucket" -> 20000;
            case "minecraft:dried_kelp_block" -> 4001;
            case "minecraft:stick" -> 100;
            case "minecraft:bamboo" -> 50;
            case "minecraft:kelp" -> 200;
            default -> 0;
        };
        if (exact > 0) return exact;
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_planks")
            || path.startsWith("stripped_")) return 300;
        if (path.endsWith("_sapling")) return 100;
        return 200;
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

    /** Structured fuel inventory for the debug snapshot; same reader as the cook path. */
    public static List<FuelEntry> inventoryFuels() {
        return inventoryFuels(null);
    }

    public static List<FuelEntry> inventoryFuels(BlockPos furnacePos) {
        if (mc.player == null) return List.of();

        Map<Item, Integer> counts = new LinkedHashMap<>();
        Map<Item, FuelValue> values = new LinkedHashMap<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFuel(stack)) continue;
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            FuelValue value = fuelValue(stack, furnacePos);
            FuelValue previous = values.get(stack.getItem());
            if (previous == null || (!previous.resolved() && value.resolved())) {
                values.put(stack.getItem(), value);
            }
        }

        List<FuelEntry> entries = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            Item item = entry.getKey();
            int count = entry.getValue();
            FuelValue value = values.get(item);
            boolean resolved = value != null && value.resolved();
            int ticks = resolved ? value.ticksPerItem() * count : 0;
            entries.add(new FuelEntry(
                itemId(item),
                count,
                value == null ? 0 : value.ticksPerItem(),
                ticks,
                resolved,
                value == null ? "unknown" : value.reason()
            ));
        }
        return entries;
    }

    public static int inventoryFuelTicks() {
        int total = 0;
        for (FuelEntry entry : inventoryFuels()) total += entry.ticks();
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
            // Clearing the screen alone leaves containerMenu on the appliance, so
            // the server still thinks it is open and every later open/probe fails.
            try {
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            } catch (Exception ignored) {
            }
            mc.setScreenAndShow(null);
        }
    }

    /**
     * What an appliance actually holds.
     *
     * On a multiplayer client the block entity's slots are NOT synced: furnace,
     * smoker and blast furnace contents only exist client-side while their menu is
     * open (BaseContainerBlockEntity sends the contents with the screen). The one
     * always-visible signal is the LIT blockstate. So the only truthful way to see
     * inside is to open the appliance and read the menu slots — that is what this
     * record is filled from, and what BotBrain's appliance probe uses.
     */
    public record ApplianceView(BlockPos pos, String inputId, int inputCount, String fuelId, int fuelCount,
                                String outputId, int outputCount, boolean fuelUsable, boolean lit) {}

    /** Reads the currently open appliance. Returns null when no furnace-type menu is open. */
    public static ApplianceView readOpenAppliance(BlockPos pos) {
        if (!isFurnaceOpen()) return null;
        try {
            AbstractFurnaceMenu handler = (AbstractFurnaceMenu) mc.player.containerMenu;
            ItemStack in = handler.slots.get(0).getItem();
            ItemStack fuel = handler.slots.get(1).getItem();
            ItemStack out = handler.slots.get(2).getItem();
            boolean lit = handler.getLitProgress() > 0;
            boolean usable = isFuel(fuel) || lit;
            return new ApplianceView(pos,
                in.isEmpty() ? "empty" : itemId(in), in.getCount(),
                fuel.isEmpty() ? "empty" : itemId(fuel), fuel.getCount(),
                out.isEmpty() ? "empty" : itemId(out), out.getCount(),
                usable, lit);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasInput() {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return !handler.slots.get(0).getItem().isEmpty();
    }

    /** Items currently sitting in the furnace input slot. */
    public static int inputCount() {
        if (!isFurnaceOpen()) return 0;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return handler.slots.get(0).getItem().getCount();
    }

    /** True when the furnace input slot holds at least one of {@code ore}. */
    public static boolean inputHasOre(Item ore) {
        if (!isFurnaceOpen()) return false;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        net.minecraft.world.item.ItemStack in = handler.slots.get(0).getItem();
        return in.is(ore) && in.getCount() > 0;
    }

    public static int getOutputCount() {
        if (!isFurnaceOpen()) return 0;
        AbstractFurnaceMenu handler =
            (AbstractFurnaceMenu) mc.player.containerMenu;
        return handler.slots.get(2).getItem().getCount();
    }
}
