package com.rdh.swarm;

import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Tick-owned deposit transfer. The only thread allowed to call {@link #tick()}
 * is the Minecraft client thread; the socket thread may only call
 * {@link #request(String, String)} to queue the next run.
 */
public final class DepositDriver {
    private enum Phase { IDLE, OPENING, VERIFYING, TRANSFERRING, CLOSING, DONE, FAILED }

    private static final long OPEN_TIMEOUT_MS = 5_000L;
    private static final long OPEN_RETRY_MS = 400L;
    private long lastOpenAttemptMs = 0L;
    private static final long VERIFY_TIMEOUT_MS = 1_000L;
    private static final long TRANSFER_TIMEOUT_MS = 30_000L;
    private static final long CLOSE_TIMEOUT_MS = 3_000L;
    private static final long GOTO_TIMEOUT_MS = 60_000L;
    private static final int NO_PROGRESS_LIMIT = 3;
    private static final int DEFAULT_STEP_CAP = 128;

    private final Supplier<Minecraft> mcSupplier;
    private final Consumer<String> protocol;
    private final Predicate<ItemStack> keepFilter;
    private final Consumer<Map<String, Long>> localDelta;
    private final Runnable onFinished;
    private final int stepCap;

    private volatile boolean requested;
    private volatile boolean active;
    private String reason = "api";
    private String onlyCategory;

    private Phase phase = Phase.IDLE;
    private final List<Target> targets = new ArrayList<>();
    private int targetIndex;
    private Target target;
    private BlockPos standPos;
    private BlockState expectedState;
    private AbstractContainerMenu menu;
    private int containerId = -1;
    private boolean ownedMenu;
    private boolean gotoIssued;
    private boolean openAttempted;
    private boolean closeSent;
    private boolean awaitingStep;

    private long gotoDeadline;
    private long phaseDeadline;
    private int stepBeforeInv;
    private int stepBeforeSource;
    private ItemStack stepStack = ItemStack.EMPTY;

    private int targetMoved;
    private int targetStacks;
    private int targetSteps;
    private int noProgress;
    private int movedTotal;
    private int totalSteps;
    private int stacksTotal;
    private boolean transferRan;
    private boolean succeeded;
    private boolean failureLogged;
    private String failure = "";
    private String targetFailure = "";
    private final Map<String, Long> targetDelta = new LinkedHashMap<>();

    public DepositDriver(Supplier<Minecraft> mcSupplier,
                         Consumer<String> protocol,
                         Predicate<ItemStack> keepFilter,
                         Consumer<Map<String, Long>> localDelta,
                         Runnable onFinished) {
        this(mcSupplier, protocol, keepFilter, localDelta, onFinished, DEFAULT_STEP_CAP);
    }

    public DepositDriver(Supplier<Minecraft> mcSupplier,
                         Consumer<String> protocol,
                         Predicate<ItemStack> keepFilter,
                         Consumer<Map<String, Long>> localDelta,
                         Runnable onFinished,
                         int stepCap) {
        this.mcSupplier = mcSupplier;
        this.protocol = protocol;
        this.keepFilter = keepFilter;
        this.localDelta = localDelta;
        this.onFinished = onFinished;
        this.stepCap = Math.max(1, stepCap);
    }

    /** Queue a deposit. Safe from the socket thread; no game state is touched here. */
    public synchronized void request(String reason, String category) {
        if (requested || active) return;
        this.reason = normalizeReason(reason);
        this.onlyCategory = category == null || category.isBlank() ? null : category.toLowerCase();
        this.requested = true;
        this.phase = Phase.IDLE;
        this.targets.clear();
        this.targetIndex = 0;
        this.target = null;
        this.standPos = null;
        this.expectedState = null;
        this.menu = null;
        this.containerId = -1;
        this.ownedMenu = false;
        this.gotoIssued = false;
        this.openAttempted = false;
        this.closeSent = false;
        this.awaitingStep = false;
        this.gotoDeadline = 0L;
        this.phaseDeadline = 0L;
        this.targetMoved = 0;
        this.targetStacks = 0;
        this.targetSteps = 0;
        this.noProgress = 0;
        this.movedTotal = 0;
        this.totalSteps = 0;
        this.stacksTotal = 0;
        this.transferRan = false;
        this.succeeded = false;
        this.failure = "";
        this.failureLogged = false;
        this.targetFailure = "";
        this.targetDelta.clear();
    }

    public boolean isBusy() {
        return requested || active;
    }

    public boolean isActive() {
        return active;
    }

    public boolean transferRan() {
        return transferRan;
    }

    public boolean succeeded() {
        return succeeded;
    }

    public int moved() {
        return movedTotal;
    }

    public String failure() {
        return failure == null ? "" : failure;
    }

    /** Cancel only a menu this driver opened, then leave the state machine idle. */
    public synchronized void cancel() {
        if (!isBusy()) return;
        Minecraft mc = mcSupplier.get();
        if (ownedMenu && mc != null && mc.player != null && menu != null
            && mc.player.containerMenu == menu) {
            try { mc.player.closeContainer(); mc.setScreenAndShow(null); } catch (Exception ignored) {}
        }
        ownedMenu = false;
        menu = null;
        active = false;
        requested = false;
        phase = Phase.FAILED;
        failure = "cancelled";
        if (onFinished != null) onFinished.run();
    }

    /** One tick, one container action. */
    public void tick() {
        if (!requested && !active) return;
        Minecraft mc = mcSupplier.get();
        if (mc == null || mc.player == null || mc.level == null) {
            fail("no-world");
            return;
        }
        if (requested) begin(mc);
        if (!active) return;

        long now = System.currentTimeMillis();
        switch (phase) {
            case IDLE -> tickIdle(mc, now);
            case OPENING -> tickOpening(mc, now);
            case VERIFYING -> tickVerifying(mc, now);
            case TRANSFERRING -> tickTransferring(mc, now);
            case CLOSING -> tickClosing(mc, now);
            default -> {
            }
        }
    }

    private void begin(Minecraft mc) {
        requested = false;
        active = true;
        synchronized (runDelta) {
            runDelta.clear();
        }
        phase = Phase.IDLE;
        targets.clear();
        targetIndex = 0;
        movedTotal = 0;
        stacksTotal = 0;
        totalSteps = 0;
        transferRan = false;
        succeeded = false;
        failure = "";
        target = null;

        String dimension;
        try {
            dimension = mc.level.dimension().identifier().toString();
        } catch (Exception e) {
            fail("no-target");
            return;
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> categories = new ArrayList<>();
        if (onlyCategory != null) {
            List<int[]> forCategory = depositChestsSnapshot().get(onlyCategory);
            categories.add(onlyCategory);
            // A base with one general pile registers it as "all". A category request
            // that only looks under its own key finds no target and refuses the whole
            // deposit even though the base has chests, so fall back to the general pile.
            if ((forCategory == null || forCategory.isEmpty()) && depositChestsSnapshot().containsKey("all")) {
                categories.add("all");
            }
        } else {
            for (String key : depositChestsSnapshot().keySet()) {
                if (key != null && !key.equalsIgnoreCase("all")) categories.add(key.toLowerCase());
            }
            categories.sort(Comparator.naturalOrder());
            if (depositChestsSnapshot().containsKey("all")) categories.add("all");
        }

        for (String category : categories) {
            // Skipping a category that merely lacks a chest would leave an empty
            // target set for the real request; the loop below then reports no-target.
            List<int[]> positions = depositChestsSnapshot().get(category);
            if (positions == null || positions.isEmpty()) continue;
            for (int[] p : new ArrayList<>(positions)) {
                if (p == null || p.length < 3) continue;
                String key = dimension + "|" + category.toLowerCase() + "|" + p[0] + "|" + p[1] + "|" + p[2];
                if (!seen.add(key)) continue;
                targets.add(new Target(category.toLowerCase(), new BlockPos(p[0], p[1], p[2]), dimension));
            }
        }

        if (targets.isEmpty()) {
            ChatUtils.warningPrefix("BotBrain", "DEPOSIT: refused reason=no-target");
            fail("no-target");
            return;
        }
        selectNext(mc);
    }

    /**
     * Chests supplied by the caller instead of the worker. The host player runs its own
     * driver (nothing else deposits that inventory) and has no worker to ask.
     */
    private Map<String, List<int[]>> chestsOverride;

    public void useChests(Map<String, List<int[]>> chests) {
        this.chestsOverride = chests;
    }

    private Map<String, List<int[]>> depositChestsSnapshot() {
        if (chestsOverride != null && !chestsOverride.isEmpty()) return chestsOverride;
        try {
            var worker = currentWorker();
            if (worker != null) return worker.depositChests;
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private EnhancedSwarmWorker currentWorker() {
        try {
            var mod = meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(EnhancedSwarm.class);
            return mod == null ? null : mod.worker;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int menuClearWaits = 0;

    private void selectNext(Minecraft mc) {
        if (targetIndex >= targets.size()) {
            finish();
            return;
        }
        target = targets.get(targetIndex++);
        phase = Phase.IDLE;
        targetFailure = "";
        targetMoved = 0;
        targetStacks = 0;
        targetSteps = 0;
        noProgress = 0;
        targetDelta.clear();
        gotoIssued = false;
        openAttempted = false;
        lastOpenAttemptMs = 0L;
        closeSent = false;
        ownedMenu = false;
        menu = null;
        containerId = -1;
        standPos = null;
        expectedState = null;
        awaitingStep = false;

        ChatUtils.infoPrefix("BotBrain", "DEPOSIT: start chest=%s %d %d %d reason=%s",
            target.dimension, target.pos.getX(), target.pos.getY(), target.pos.getZ(), reason);

        // A previous target can leave its chest GUI on screen. The guard below
        // then rejected every later target instantly ("failed … after=1 steps"),
        // which is why a four-chest base never completed a deposit.
        try {
            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
                mc.setScreenAndShow(null);
            }
        } catch (Exception ignored) {}

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            // The close is only observed on the next tick, so failing here rejected
            // every chest in a base the moment one menu was still closing. Wait a
            // few ticks for it to settle before giving up on the target.
            if (menuClearWaits < 10) {
                menuClearWaits++;
                targetIndex--; // re-select this target on the next tick
                return;
            }
            menuClearWaits = 0;
            failTarget("menu-mismatch");
            return;
        }
        menuClearWaits = 0;

        BlockState state = mc.level.getBlockState(target.pos);
        if (!isContainerBlock(state)) {
            failTarget("no-target");
            return;
        }
        standPos = findStandPos(mc, target.pos);
        if (standPos == null) {
            failTarget("no-target");
            return;
        }
        gotoDeadline = System.currentTimeMillis() + GOTO_TIMEOUT_MS;
    }

    private void tickIdle(Minecraft mc, long now) {
        if (target == null || standPos == null) {
            failTarget("no-target");
            return;
        }
        if (now > gotoDeadline) {
            failTarget("timeout");
            return;
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            failTarget("menu-mismatch");
            return;
        }

        double dist = mc.player.blockPosition().distSqr(standPos);
        if (dist > 4.0D) {
            if (!gotoIssued) {
                try {
                    if (mc.getConnection() != null) {
                        mc.getConnection().sendChat(String.format("#goto %d %d %d",
                            standPos.getX(), standPos.getY(), standPos.getZ()));
                        gotoIssued = true;
                    }
                } catch (Exception ignored) {
                }
            }
            return;
        }

        BlockState state = mc.level.getBlockState(target.pos);
        if (!isContainerBlock(state)) {
            failTarget("no-target");
            return;
        }
        expectedState = state;
        phase = Phase.OPENING;
        phaseDeadline = now + OPEN_TIMEOUT_MS;
        openAttempted = false;
        openTarget(mc);
    }

    private void openTarget(Minecraft mc) {
        openAttempted = true;
        lastOpenAttemptMs = System.currentTimeMillis();
        try {
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(target.pos));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target.pos), Direction.UP, target.pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        } catch (Exception ignored) {
        }
    }

    private void tickOpening(Minecraft mc, long now) {
        // One interaction packet is easy to lose — reach, a lid animation, a
        // transient block. The driver used to try exactly once and then report
        // "timeout", so a single missed click aborted the whole deposit. Retry
        // on a short cadence inside the same deadline.
        if (!openAttempted || now - lastOpenAttemptMs >= OPEN_RETRY_MS) {
            openTarget(mc);
        }
        if (now > phaseDeadline) {
            failTarget("timeout");
            return;
        }
        AbstractContainerMenu current = mc.player.containerMenu;
        if (current == null || current == mc.player.inventoryMenu) return;

        if (!isExpectedMenu(current) || !targetBlockStillMatches(mc)) {
            failTarget("menu-mismatch");
            return;
        }
        menu = current;
        containerId = current.containerId;
        ownedMenu = true;
        phase = Phase.VERIFYING;
        phaseDeadline = now + VERIFY_TIMEOUT_MS;
    }

    private void tickVerifying(Minecraft mc, long now) {
        if (now > phaseDeadline) {
            failTarget("menu-mismatch");
            return;
        }
        if (menu == null || mc.player.containerMenu != menu
            || menu.containerId != containerId
            || !isExpectedMenu(menu)
            || !targetBlockStillMatches(mc)) {
            failTarget("menu-mismatch");
            return;
        }
        phase = Phase.TRANSFERRING;
        phaseDeadline = now + TRANSFER_TIMEOUT_MS;
        transferRan = true;
        stepBeforeInv = countPlayerItems(menu);
    }

    private void tickTransferring(Minecraft mc, long now) {
        if (now > phaseDeadline) {
            failTarget("timeout");
            return;
        }
        if (menu == null || mc.player.containerMenu != menu || menu.containerId != containerId) {
            failTarget("menu-mismatch");
            return;
        }

        if (awaitingStep) {
            awaitingStep = false;
            int afterInv = countPlayerItems(menu);
            int afterSource = countMatching(menu, stepStack, true);
            int exactMoved = Math.max(0, stepBeforeSource - afterSource);
            int totalMoved = Math.max(0, stepBeforeInv - afterInv);
            int moved = exactMoved > 0 ? exactMoved : totalMoved;
            if (moved > 0) {
                targetMoved += moved;
                movedTotal += moved;
                targetStacks++;
                stacksTotal++;
                targetSteps++;
                totalSteps++;
                noProgress = 0;
                String itemId = itemId(stepStack);
                if (!itemId.isEmpty()) targetDelta.merge(itemId, (long) moved, Long::sum);
                ChatUtils.infoPrefix("BotBrain", "DEPOSIT: step=%d moved=%d stacks=%d menu=%s",
                    totalSteps, moved, stacksTotal, menuName(menu));
            } else {
                noProgress++;
                ChatUtils.infoPrefix("BotBrain", "DEPOSIT: no-op chest=%s %d %d %d",
                    target.dimension, target.pos.getX(), target.pos.getY(), target.pos.getZ());
                if (noProgress >= NO_PROGRESS_LIMIT) {
                    failTarget("no-progress");
                    return;
                }
            }
            if (totalSteps >= stepCap) {
                failTarget("step-cap");
                return;
            }
        }

        int slotIndex = findEligibleSlot(mc, menu);
        if (slotIndex < 0) {
            phase = Phase.CLOSING;
            phaseDeadline = now + CLOSE_TIMEOUT_MS;
            return;
        }

        Slot slot = menu.getSlot(slotIndex);
        stepStack = slot.getItem().copy();
        stepBeforeInv = countPlayerItems(menu);
        stepBeforeSource = countMatching(menu, stepStack, true);
        try {
            InvUtils.shiftClick().slotId(slotIndex);
            awaitingStep = true;
        } catch (Exception e) {
            failTarget("click-failed");
        }
    }

    private void tickClosing(Minecraft mc, long now) {
        if (ownedMenu && !closeSent) {
            closeSent = true;
            try {
                if (mc.player.containerMenu == menu) {
                // Clearing only the screen left the server (and containerMenu)
                // still on the chest, so the close never confirmed and every
                // deposit ended in "refused reason=timeout".
                mc.player.closeContainer();
                mc.setScreenAndShow(null);
            }
            } catch (Exception ignored) {
            }
        }
        if (mc.player.containerMenu == mc.player.inventoryMenu) {
            ownedMenu = false;
            afterClose(mc);
            return;
        }
        if (now > phaseDeadline) {
            ownedMenu = false;
            if (targetFailure.isEmpty()) targetFailure = "timeout";
            afterClose(mc);
        }
    }

    private void afterClose(Minecraft mc) {
        menu = null;
        containerId = -1;
        if (!targetFailure.isEmpty()) {
            String reasonFailed = targetFailure;
            failure = reasonFailed;
            targetFailure = "";
            if (targetMoved > 0) publishDelta(target, targetDelta);
            ChatUtils.warningPrefix("BotBrain", "DEPOSIT: refused reason=%s", reasonFailed);
            target = null;
            selectNext(mc);
            return;
        }

        ChatUtils.infoPrefix("BotBrain", "DEPOSIT: complete moved=%d stacks=%d delta=%s",
            targetMoved, targetStacks, targetDelta);
        if (targetMoved > 0) {
            succeeded = true;
            publishDelta(target, targetDelta);
        } else {
            ChatUtils.infoPrefix("BotBrain", "DEPOSIT: no-op chest=%s %d %d %d",
                target.dimension, target.pos.getX(), target.pos.getY(), target.pos.getZ());
        }
        target = null;
        selectNext(mc);
    }

    private void failTarget(String reason) {
        if ("no-target".equals(reason) && !ownedMenu) {
            // A chest list can name a block the world no longer has. One dead entry must
            // not fail a run that already reached the other chests, so skip it and go on.
            if (target != null) {
                ChatUtils.warningPrefix("BotBrain", "DEPOSIT: skipped chest=%s %d %d %d (not a container)",
                    target.dimension, target.pos.getX(), target.pos.getY(), target.pos.getZ());
            }
            target = null;
            Minecraft skipped = mcSupplier.get();
            if (skipped != null) selectNext(skipped); else finish();
            return;
        }
        if ("timeout".equals(reason)) {
            long nowMs = System.currentTimeMillis();
            Minecraft d = mcSupplier.get();
            ChatUtils.infoPrefix("BotBrain", "DEPOSIT: timeout detail phase=%s gotoIn=%d phaseIn=%d dist=%s",
                phase, gotoDeadline - nowMs, phaseDeadline - nowMs,
                (d != null && d.player != null && standPos != null)
                    ? String.format("%.2f", d.player.blockPosition().distSqr(standPos)) : "none");
        }
        if (targetFailure.isEmpty()) targetFailure = reason;
        if (ownedMenu) {
            phase = Phase.CLOSING;
            phaseDeadline = System.currentTimeMillis() + CLOSE_TIMEOUT_MS;
            return;
        }
        Minecraft mc = mcSupplier.get();
        if (mc == null || mc.player == null) {
            fail(reason);
            return;
        }
        afterClose(mc);
    }

    private void fail(String reason) {
        failure = reason == null ? "unknown" : reason;
        if (active) {
            ChatUtils.warningPrefix("BotBrain", "DEPOSIT: failed reason=%s after=%d steps", failure, totalSteps);
            failureLogged = true;
        }
        finish();
    }

    private void finish() {
        active = false;
        requested = false;
        if (!succeeded && (failure == null || failure.isEmpty()) && runDelta().isEmpty() && totalSteps == 0) {
            // Reaching every chest and moving nothing is not an error: the inventory held
            // only keep-list items. Reporting it as failed made the task gate retry a
            // deposit that has nothing to do, forever.
            succeeded = true;
            ChatUtils.infoPrefix("BotBrain", "DEPOSIT: nothing to deposit (keep-list items only)");
        }
        phase = succeeded ? Phase.DONE : Phase.FAILED;
        if (!succeeded) {
            if (failure == null || failure.isEmpty()) failure = "no-op";
            if (!failureLogged) {
                ChatUtils.warningPrefix("BotBrain", "DEPOSIT: failed reason=%s after=%d steps", failure, totalSteps);
                failureLogged = true;
            }
        }
        try {
            if (onFinished != null) onFinished.run();
        } catch (Exception ignored) {
        }
    }

    /**
     * Everything this whole run moved, summed across targets. The per-target delta only
     * ever describes one chest, so a caller that needs "what did the deposit actually
     * move" (the task-completion gate) had no honest source before this.
     */
    private final Map<String, Long> runDelta = new LinkedHashMap<>();

    public Map<String, Long> runDelta() {
        synchronized (runDelta) {
            return new LinkedHashMap<>(runDelta);
        }
    }

    private void publishDelta(Target target, Map<String, Long> delta) {
        if (delta == null || delta.isEmpty()) return;
        synchronized (runDelta) {
            delta.forEach((id, n) -> runDelta.merge(id, n, Long::sum));
        }
        JsonObject counts = new JsonObject();
        for (Map.Entry<String, Long> entry : delta.entrySet()) {
            counts.addProperty(entry.getKey(), entry.getValue());
        }
        JsonObject root = new JsonObject();
        root.add("counts", counts);
        root.addProperty("lastScan", System.currentTimeMillis());
        try {
            if (protocol != null) {
                protocol.accept("CHEST_DELTA:" + target.category + ":" + target.pos.getX() + ":"
                    + target.pos.getY() + ":" + target.pos.getZ() + "|" + root);
            }
            if (localDelta != null) localDelta.accept(new LinkedHashMap<>(delta));
        } catch (Exception ignored) {
        }
    }

    private int findEligibleSlot(Minecraft mc, AbstractContainerMenu menu) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container != mc.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;
            if (keepFilter != null && keepFilter.test(stack)) continue;
            if (target != null && !target.category.equalsIgnoreCase("all")
                && !DepositConfig.matchesCategory(target.category, stack)) continue;
            return i;
        }
        return -1;
    }

    private int countPlayerItems(AbstractContainerMenu menu) {
        int total = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container != mcSupplier.get().player.getInventory()) continue;
            total += slot.getItem().getCount();
        }
        return total;
    }

    private int countMatching(AbstractContainerMenu menu, ItemStack sample, boolean playerSlots) {
        if (sample == null || sample.isEmpty()) return 0;
        Minecraft mc = mcSupplier.get();
        if (mc == null || mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            boolean isPlayer = slot.container == mc.player.getInventory();
            if (isPlayer != playerSlots) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(sample, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean targetBlockStillMatches(Minecraft mc) {
        if (target == null || expectedState == null) return false;
        BlockState state = mc.level.getBlockState(target.pos);
        return state.getBlock() == expectedState.getBlock() && isContainerBlock(state);
    }

    private boolean isContainerBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock
            || state.getBlock() instanceof BarrelBlock
            || state.getBlock() instanceof ShulkerBoxBlock
            || state.getBlock() instanceof EnderChestBlock;
    }

    /**
     * Storage containers accepted as a deposit target. The driver already
     * treats barrels (and hoppers/dispensers) as valid container blocks, but
     * the old check only accepted ChestMenu and ShulkerBoxMenu, so any
     * barrel-based deposit chest failed with "menu-mismatch" and could never
     * complete. Matching on the menu's simple name keeps this mapping-proof.
     */
    private boolean isExpectedMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        if (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu) return true;
        String name = menu.getClass().getSimpleName();
        return name.equals("BarrelMenu") || name.equals("HopperMenu")
            || name.equals("DispenserMenu") || name.equals("CrafterMenu");
    }

    private String menuName(AbstractContainerMenu menu) {
        return menu == null ? "none" : menu.getClass().getSimpleName();
    }

    private String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private BlockPos findStandPos(Minecraft mc, BlockPos chestPos) {
        if (mc.level == null) return null;
        for (int yOff = 0; yOff <= 1; yOff++) {
            for (int radius = 1; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos candidate = chestPos.offset(dx, -yOff, dz);
                        if (candidate.equals(chestPos)) continue;
                        BlockState state = mc.level.getBlockState(candidate);
                        BlockState below = mc.level.getBlockState(candidate.below());
                        if ((state.isAir() || !state.canOcclude()) && !below.isAir() && below.isSolid()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        BlockPos[] fallbacks = {
            chestPos.offset(1, 0, 0), chestPos.offset(-1, 0, 0),
            chestPos.offset(0, 0, 1), chestPos.offset(0, 0, -1)
        };
        for (BlockPos candidate : fallbacks) {
            BlockState state = mc.level.getBlockState(candidate);
            BlockState below = mc.level.getBlockState(candidate.below());
            if ((state.isAir() || !state.canOcclude()) && !below.isAir()) return candidate;
        }
        return null;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) return "api";
        return switch (value.toLowerCase()) {
            case "full", "manual", "low", "api" -> value.toLowerCase();
            default -> "api";
        };
    }

    private static final class Target {
        final String category;
        final BlockPos pos;
        final String dimension;

        Target(String category, BlockPos pos, String dimension) {
            this.category = category;
            this.pos = pos;
            this.dimension = dimension;
        }
    }
}
