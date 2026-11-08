package com.rdh.swarm.modules;

import com.rdh.swarm.EnhancedSwarmAddon;
import com.rdh.swarm.task.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import com.rdh.swarm.WaypointManager;
import com.rdh.swarm.SwarmWaypoint;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;

import java.util.*;
import java.io.DataOutputStream;
import java.io.IOException;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BotBrain extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoEquip = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-equip")
        .description("Automatically upgrade equipment before gathering.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> craftingTableRange = sgGeneral.add(new IntSetting.Builder()
        .name("crafting-table-range")
        .description("Range to search for crafting tables.")
        .defaultValue(5)
        .range(3, 15)
        .build()
    );

    private final Setting<Integer> depositThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("deposit-threshold")
        .description("Empty slots remaining to trigger deposit run.")
        .defaultValue(4)
        .range(1, 15)
        .build()
    );

    private final Setting<Boolean> gatherStaging = sgGeneral.add(new BoolSetting.Builder()
        .name("gather-staging")
        .description("Stage a short distance away from explicit base before mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> gatherStagingDistance = sgGeneral.add(new IntSetting.Builder()
        .name("gather-staging-distance")
        .description("Distance from base to stage before gathering")
        .defaultValue(160)
        .range(32, 512)
        .sliderRange(32, 512)
        .build()
    );

    private final Setting<Boolean> collectDrops = sgGeneral.add(new BoolSetting.Builder()
        .name("collect-drops")
        .description("Walk to nearby mining and combat drops in bounded clusters.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pickupRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("pickup-radius")
        .description("Maximum distance to scan for dropped items.")
        .defaultValue(4.0)
        .min(1.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Integer> pickupWaitMs = sgGeneral.add(new IntSetting.Builder()
        .name("pickup-wait-ms")
        .description("Maximum time to wait at a drop cluster for vanilla pickup.")
        .defaultValue(3000)
        .range(500, 10000)
        .build()
    );

    private final Setting<Boolean> manageModules = sgGeneral.add(new BoolSetting.Builder()
        .name("manage-modules")
        .description("Keep the guarded Meteor survival modules enabled while BotBrain is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fightMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("fight-mobs")
        .description("Include hostile-mob KillAura in the guarded module set.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoHunt = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-hunt")
        .description("Start a food hunt when total food drops below food-low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCook = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-cook")
        .description("Cook raw food at a nearby furnace when fuel is available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> foodLow = sgGeneral.add(new IntSetting.Builder()
        .name("food-low")
        .description("Start a food hunt when total food drops below this count.")
        .defaultValue(6)
        .range(0, 64)
        .build()
    );

    private final Setting<Integer> foodTarget = sgGeneral.add(new IntSetting.Builder()
        .name("food-target")
        .description("Stop a food hunt at this total food count.")
        .defaultValue(20)
        .range(1, 128)
        .build()
    );

    private final Setting<Integer> huntRange = sgGeneral.add(new IntSetting.Builder()
        .name("hunt-range")
        .description("Maximum animal search radius for food hunts.")
        .defaultValue(96)
        .range(16, 256)
        .build()
    );

    private final Setting<Boolean> eatRawFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("eat-raw-fallback")
        .description("Allow AutoEat to eat safe raw meat when no cooked food is available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoGrind = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-grind")
        .description("Start a grinder session when idle and grind coordinates are set.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> grindX = sgGeneral.add(new IntSetting.Builder()
        .name("grind-x")
        .description("Grinder standing position X.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .build()
    );

    private final Setting<Integer> grindY = sgGeneral.add(new IntSetting.Builder()
        .name("grind-y")
        .description("Grinder standing position Y.")
        .defaultValue(0)
        .range(-256, 512)
        .build()
    );

    private final Setting<Integer> grindZ = sgGeneral.add(new IntSetting.Builder()
        .name("grind-z")
        .description("Grinder standing position Z.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .build()
    );

    private final Setting<Integer> grindRange = sgGeneral.add(new IntSetting.Builder()
        .name("grind-range")
        .description("Hostile mob search radius around the player.")
        .defaultValue(16)
        .range(8, 48)
        .build()
    );

    private final Setting<Integer> grindDuration = sgGeneral.add(new IntSetting.Builder()
        .name("grind-duration")
        .description("Grinder session length in seconds.")
        .defaultValue(300)
        .range(30, 3600)
        .build()
    );

    private final Setting<Boolean> grindCollect = sgGeneral.add(new BoolSetting.Builder()
        .name("grind-collect")
        .description("Walk to and collect nearby grinder drops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoNetherite = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-netherite")
        .description("Smith diamond gear into netherite when a template, ingot, and table are available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> smithingRange = sgGeneral.add(new IntSetting.Builder()
        .name("smithing-range")
        .description("Range to search for a smithing table.")
        .defaultValue(32)
        .range(8, 64)
        .build()
    );

    private final Setting<Boolean> autoEmptyShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-empty-shulkers")
        .description("Place and empty filled shulker boxes when idle.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoBuild = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-build")
        .description("Start a Baritone builder job from the configured schematic when idle.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> buildFile = sgGeneral.add(new StringSetting.Builder()
        .name("build-file")
        .description("Schematic file path for BUILD tasks.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> buildX = sgGeneral.add(new IntSetting.Builder()
        .name("build-x")
        .description("Schematic origin X.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .build()
    );

    private final Setting<Integer> buildY = sgGeneral.add(new IntSetting.Builder()
        .name("build-y")
        .description("Schematic origin Y.")
        .defaultValue(0)
        .range(-256, 512)
        .build()
    );

    private final Setting<Integer> buildZ = sgGeneral.add(new IntSetting.Builder()
        .name("build-z")
        .description("Schematic origin Z.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .build()
    );

    public enum SwarmRole { GENERAL, MINER, GRINDER, FOOD }

    private final Setting<SwarmRole> swarmRole = sgGeneral.add(new EnumSetting.Builder<SwarmRole>()
        .name("swarm-role")
        .description("Preferred automatic work for this bot.")
        .defaultValue(SwarmRole.GENERAL)
        .build()
    );

    public enum BrainState {
        IDLE,
        CHECKING_EQUIPMENT,
        GATHERING,
        HUNTING,
        GRINDING,
        SMITHING,
        SHULKER,
        BUILDING,
        WITHDRAWING,
        RETURNING_TO_BASE,
        CRAFTING_PREP,
        CRAFTING,
        SMELTING_DEPOSIT,
        SMELTING_WAIT,
        SMELTING_COLLECT,
        DEPOSITING,
        RECOVERING,
        STUCK
    }

    private BrainState state = BrainState.IDLE;
    private BotTask currentTask = null;
    private List<BotTask> executionPlan = null;
    // Resolver outputs stored at task assignment
    private Map<net.minecraft.world.item.Item, Integer> resolverGatherList = null;
    private List<BotTask> resolverCraftPlan = null;
    private boolean resolverNeeds3x3 = false;

    // Crafting prep state
    private int craftPrepPhase = 0;
    private BlockPos craftingTablePos = null;
    private int craftPrepTicks = 0;
    private boolean waitingForBaritone = false;
    // Prep-craft target (e.g. crafting table) in CRAFTING_PREP
    private net.minecraft.world.item.Item prepCraftTarget = null;

    private static final int PREP_SCAN = 0;
    private static final int PREP_GOTO = 1;
    private static final int PREP_CRAFT_TABLE = 2;
    private static final int PREP_PLACE_TABLE = 3;
    private static final int PREP_WAIT_PLACE = 4;
    private static final int PREP_OPEN_TABLE = 5;
    private int planStep = 0;
    private long stateEnterTime = 0;
    private long lastBrainTick = 0;
    private static final long BRAIN_TICK_MS = 250;
    private int tickCounter = 0;
    private int stuckCounter = 0;
    private boolean mineCommandSent = false;
    private int itemsCollectedSinceLastCheck = 0;
    private long lastItemCheckTime = 0;
    private int lastItemCount = 0;
    private int lastReportedCount = 0;
    private String currentTaskDescription = "";
    private String currentEta = "";
    private long lastProgressTime = 0;
    private int lastProgressCount = 0;
    private long craftingWaitStart = 0;
    private int craftFailCount = 0;

    // The current material that Baritone is mining (to avoid resending the same command)
    private String currentlyMining = null;
    private final DropCollector dropCollector = new DropCollector(
        () -> collectDrops.get(), () -> pickupRadius.get(), () -> pickupWaitMs.get(),
        this::canStartDropSweep, this::onSweepFinished);
    private boolean gatherStagingCommandSent = false;
    private BlockPos stagingTarget = null;
    private BlockPos surfaceTarget = null;
    private boolean surfaceCommandSent = false;
    private boolean stagingComplete = false;
    private int stagingAttempts = 0;
    private boolean pendingDepositAfterReturn = false;
    // Waypoint manager for worker-side waypoint lookups
    private WaypointManager waypointManager = null;
    // Resource currently gathering (e.g., "diamond_ore")
    private String currentGatherResource = null;
    // Nether transit state
    private boolean inNetherTransit = false;          // true when task requires nether dimension
    private boolean awaitingDimensionChange = false;   // true after entering portal, waiting for world swap
    private String expectedDimension = null;           // "nether" or "overworld" — what we expect after transit
    private BlockPos portalApproachPos = null;         // position near portal to goto before entering
    private boolean portalApproachSent = false;        // true after #goto to approach pos sent
    private boolean portalEntrySent = false;           // true after #goto nether_portal sent
    private long dimensionWaitStartTime = 0;           // timestamp when we started waiting for dimension change
    private static final long DIMENSION_WAIT_TIMEOUT_MS = 30_000;  // 30 seconds to transit or fail
    private static final int PORTAL_APPROACH_DISTANCE = 10;        // blocks away to stop before #goto nether_portal
    // Nether return trip (dedicated, separate from navigatePortal)
    private boolean netherReturnGotoSent = false;
    private boolean netherReturnPortalSent = false;
    private long netherReturnStartTime = 0;

    // Material aliases: what to MINE to GET the item
    private static final Map<String, String> MINE_ALIASES = Map.of(
        "cobblestone", "stone",
        "flint", "gravel",
        "charcoal", "oak_log",
        "stick", "oak_log",
        "planks", "oak_log",
        "oak_planks", "oak_log",
        "coal", "coal_ore",
        "raw_iron", "iron_ore",
        "raw_gold", "gold_ore",
        "raw_copper", "copper_ore"
    );

    // Explicit maps for direct GATHER tasks (user-facing aliases)
    private static final Map<String, String> MINE_FOR = Map.of(
        "cobblestone",    "stone",
        "raw_iron",       "iron_ore",
        "raw_gold",       "gold_ore",
        "raw_copper",     "copper_ore",
        "diamond",        "diamond_ore",
        "emerald",        "emerald_ore",
        "lapis_lazuli",   "lapis_ore",
        "redstone",       "redstone_ore",
        "coal",           "coal_ore",
        "flint",          "gravel"
    );

    private static final Map<String, String> MINE_FOR_2 = Map.of(
        "quartz",         "nether_quartz_ore",
        "glowstone_dust", "glowstone",
        "ancient_debris", "ancient_debris",
        "nether_gold",    "nether_gold_ore"
    );

    // When entering CRAFTING_PREP, keep track of the plan step to return to
    private int craftPrepReturnPlanStep = 0;

    private com.rdh.swarm.task.BotTask pendingTask = null;
    private net.minecraft.world.item.Item lastMissingIngredient = null;

    // Death tracking
    private final DeathTracker deathTracker = new DeathTracker();
    private final ModuleGuard moduleGuard = new ModuleGuard();
    private BlockPos lastAlivePos = null;
    private String lastAliveDimension = "";
    private boolean deathRecorded = false;

    // Food hunt state
    private static final Set<EntityType<?>> HUNT_TYPES = Set.of(
        EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.CHICKEN, EntityTypes.RABBIT
    );
    private long huntStartTime = 0;
    private long lastHuntExplore = 0;
    private int huntExploreRounds = 0;
    private int huntInitialFood = 0;
    private double lastHuntDistance = Double.MAX_VALUE;
    private boolean huntFollowing = false;
    private UUID lastHuntTargetId = null;
    private Entity lastHuntTargetEntity = null;
    private long lastAutoHunt = 0;
    private long lastHuntProgress = 0;
    private static final long AUTO_HUNT_COOLDOWN = 120_000L;
    private long lastCookAttempt = 0;
    private static final long COOK_COOLDOWN = 300_000L;
    private String lastCookLog = "";
    private boolean noCookFurnaceWarned = false;
    private boolean autoHuntWasEnabled = true;
    private boolean autoHuntSkipLogged = false;
    private boolean autoCookSkipLogged = false;
    private static final int COOK_FURNACE_RANGE = 45;
    private boolean directSmelt = false;

    // Grinder state
    private BlockPos grindPos = null;
    private long grindStartTime = 0;
    private long lastGrindProgress = 0;
    private long lastGrindGoto = 0;
    private long lastAutoGrind = 0;
    private int grindAttacks = 0;

    // Smithing state
    private static final int SMITH_SCAN = 0;
    private static final int SMITH_GOTO = 1;
    private static final int SMITH_OPEN = 2;
    private static final int SMITH_INSERT = 3;
    private static final int SMITH_TAKE = 4;
    private static final int SMITH_VERIFY = 5;
    private int smithPhase = SMITH_SCAN;
    private BlockPos smithPos = null;
    private int smithTicks = 0;
    private long lastSmithGoto = 0;
    private long lastAutoSmith = 0;

    // Shulker emptying
    private static final int SHULKER_PLACE = 0;
    private static final int SHULKER_OPEN = 1;
    private static final int SHULKER_EMPTY = 2;
    private static final int SHULKER_BREAK = 3;
    private static final int SHULKER_COLLECT = 4;
    private int shulkerPhase = SHULKER_PLACE;
    private int shulkerTicks = 0;
    private BlockPos shulkerPos = null;
    private Item shulkerItem = null;
    private long lastAutoShulker = 0;

    // Builder state
    private long buildStartTime = 0;
    private long lastAutoBuild = 0;

    // Rate limiting (dont spam commands)
    private long lastBaritoneCommand = 0;
    private static final long BARITONE_COMMAND_COOLDOWN = 3000;

    // Crafting state
    private long craftingTableOpenTime = 0;
    private boolean waitingForCraftingOpen = false;
    private volatile boolean paused = false;
    private volatile boolean halted = false;
    private long lastHaltLog = 0L;

    // Tick-driven crafting fields
    private int craftingStep = 0;
    private int craftGridSlotIndex = 0;
    private List<Object[]> craftActions = null; // Each Object[] = {sourceInventorySlot, targetGridSlot, Item ingredient}

    // Smelting state
    private int smeltPhase = 0;
    private BlockPos smelterPos = null;
    private int smeltTicks = 0;
    private long smeltWaitStart = 0;
    private static final int SMELT_SCAN = 0;
    private static final int SMELT_GOTO = 1;
    private static final int SMELT_PLACE = 2;
    private static final int SMELT_WAIT_PLACE = 3;
    private static final int SMELT_OPEN = 4;
    private static final int SMELT_INSERT = 5;
    private long lastSmeltCheckTime = 0;
    private static final long SMELT_CHECK_INTERVAL = 10000; // 10 seconds
    // Base return state
    private BlockPos basePos = null;
    private boolean baseReturnCommandSent = false;

    // Pending placement tracking
    private BlockPos pendingPlacementPos = null;
    private net.minecraft.world.item.Item pendingPlacementItem = null;
    private int pendingPlacementRetries = 0;
    private static final int MAX_PENDING_PLACEMENT_RETRIES = 3;

    // Output stream to host (set by EnhancedSwarmWorker)
    private DataOutputStream out = null;

    // Storage query response cache (non-blocking)
    private volatile Map<String, Long> lastStorageCache = Collections.emptyMap();

    /** Reflect a completed chest transfer in the local storage cache immediately. */
    public void mergeLocalStorageCounts(Map<String, Long> delta) {
        if (delta == null || delta.isEmpty()) return;
        synchronized (this) {
            Map<String, Long> merged = new HashMap<>(lastStorageCache == null ? Collections.emptyMap() : lastStorageCache);
            for (var e : delta.entrySet()) merged.merge(e.getKey(), e.getValue(), Long::sum);
            lastStorageCache = merged;
        }
    }
    private volatile long lastCacheTime = 0L;
    private volatile boolean storageQueryPending = false;
    // Pending withdrawals requested by planner (itemId -> qty)
    private volatile List<Map.Entry<String, Integer>> pendingWithdrawals = null;
    private volatile boolean withdrawInProgress = false;
    private volatile long withdrawStartTime = 0L;
    private static final long WITHDRAW_TIMEOUT_MS = 120_000L;

    public void setOutputStream(DataOutputStream out) {
        this.out = out;
    }

    // Called by EnhancedSwarmWorker when a STORAGE_QUERY_RESPONSE arrives
    public void receiveStorageQueryResponse(Map<String, Long> response) {
        try {
            this.lastStorageCache = response != null ? new HashMap<>(response) : Collections.emptyMap();
            this.lastCacheTime = System.currentTimeMillis();
            this.storageQueryPending = false;
            System.out.println(String.format("[QUERY_RESPONSE] thread=%s cacheSize=%d", Thread.currentThread().getName(), this.lastStorageCache.size()));
        } catch (Exception ignored) {}
    }

    // Query host storage for availability before gathering. Returns map itemId->available
    private Map<String, Long> queryStorageBeforeGathering(Map<String, Integer> needs) {
        if (tickCounter % 100 == 0) System.out.println(String.format("[QUERY_START] thread=%s out=%s", Thread.currentThread().getName(), out == null ? "null" : out.toString()));
        Map<String, Long> empty = Collections.emptyMap();
        if (needs == null || needs.isEmpty()) return empty;
        if (out == null) return empty;

        // Build request: STORAGE_QUERY_REQUEST:<item1>:<need1>:<item2>:<need2>:...
        StringBuilder sb = new StringBuilder();
        sb.append("STORAGE_QUERY_REQUEST:");
        boolean first = true;
        for (var e : needs.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (!first) sb.append(":");
            first = false;
            sb.append(id).append(":").append(qty);
        }

        // Fire-and-forget: send the request asynchronously and return the last known cache immediately.
        final String msg = sb.toString();
        final DataOutputStream outRef = this.out;
        if (outRef != null) {
            MeteorExecutor.execute(() -> {
                try {
                    synchronized (outRef) {
                        outRef.writeUTF(msg);
                        outRef.flush();
                    }
                } catch (Exception ignored) {}
            });
        }
        Map<String, Long> cache = this.lastStorageCache;
        return cache != null ? new HashMap<>(cache) : empty;
    }

    // Signature
    private static final byte[] _bbs = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _bbc = 0x726468;

    public BotBrain() {
        super(EnhancedSwarmAddon.CATEGORY, "bot-brain",
            "Autonomous bot AI. Gathers, crafts, smelts, deposits, and upgrades equipment.");
    }

    @Override
    public void onActivate() {
        state = BrainState.IDLE;
        currentTask = null;
        executionPlan = null;
        planStep = 0;
        stuckCounter = 0;
        deathTracker.load();
        lastAlivePos = null;
        lastAliveDimension = "";
        deathRecorded = false;
        lastCookLog = "";
        noCookFurnaceWarned = false;
        autoHuntWasEnabled = autoHunt.get();
        autoHuntSkipLogged = false;
        autoCookSkipLogged = false;
        if (manageModules.get()) {
            moduleGuard.activate(fightMobs.get());
            moduleGuard.setRawFoodFallback(eatRawFallback.get());
        }
        stateEnterTime = System.currentTimeMillis();
        lastBrainTick = System.currentTimeMillis();
        lastItemCheckTime = System.currentTimeMillis();
        dropCollector.cancel();
        dropCollector.resetTaskTracking();
        lastHuntTargetId = null;
        lastHuntTargetEntity = null;
    }

    @Override
    public void onDeactivate() {
        state = BrainState.IDLE;
        currentTask = null;
        pendingTask = null;
        executionPlan = null;
        directSmelt = false;
        grindPos = null;
        grindStartTime = 0;
        smithPos = null;
        shulkerPos = null;
        shulkerPhase = SHULKER_PLACE;
        buildStartTime = 0;
        SmithingManager.close();
        stopHuntMovement();
        dropCollector.cancel();
        if (manageModules.get()) moduleGuard.deactivate();
        stopBaritoneOnDeactivate();
    }

    private void stopBaritoneOnDeactivate() {
        try {
            if (mc.getConnection() != null) mc.getConnection().sendChat("#stop");
        } catch (Exception ignored) {}
        try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (Exception | NoClassDefFoundError ignored) {}
    }

    /** Record one death per death event, using the last position observed while alive. */
    private void trackDeath() {
        if (mc.player == null || mc.level == null) return;
        float health = mc.player.getHealth();
        String dimension = mc.level.dimension().identifier().toString();
        if (health > 0) {
            lastAlivePos = mc.player.blockPosition();
            lastAliveDimension = dimension;
            deathRecorded = false;
            return;
        }
        if (deathRecorded) return;
        BlockPos pos = lastAlivePos != null ? lastAlivePos : mc.player.blockPosition();
        String dim = lastAliveDimension.isEmpty() ? dimension : lastAliveDimension;
        deathTracker.recordDeath(mc.player.getUUID(), "death", pos, dim);
        deathRecorded = true;
        ChatUtils.warningPrefix("BotBrain", "Death recorded at %d %d %d (%s)", pos.getX(), pos.getY(), pos.getZ(), dim);
    }

    private void failUnhandledTask(BotTask step) {
        String reason = "no handler for " + (step == null ? "unknown" : step.getType());
        ChatUtils.warningPrefix("BotBrain", "Task failed: %s", reason);
        BotTask task = currentTask;
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        currentTask = null;
        executionPlan = null;
        planStep = 0;
        setState(BrainState.IDLE);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Don't run BotBrain on host clients
        var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
        if (swarmMod == null || swarmMod.mode.get() == com.rdh.swarm.EnhancedSwarm.Mode.Host) return;

        if (mc.player == null || mc.level == null) return;
        trackDeath();

        long now = System.currentTimeMillis();
        if (now - lastBrainTick < BRAIN_TICK_MS) return;
        lastBrainTick = now;
        tickCounter++;
        if (manageModules.get()) {
            moduleGuard.tick(tickCounter);
            moduleGuard.setRawFoodFallback(eatRawFallback.get());
        }
        if (halted) {
            releaseMovementKeys();
            if (now - lastHaltLog >= 10000L) {
                lastHaltLog = now;
                ChatUtils.infoPrefix("BotBrain", "HALT: still halted, planner suppressed");
            }
            return;
        }
        tickFoodToggles();
        if (tickCounter % 40 == 0) maybeInterruptForFood();
        if (tickCounter % 20 == 0 && currentTask != null) {
            ChatUtils.infoPrefix("BotBrain", "tick state=%s planStep=%d planSize=%d task=%s", state, planStep, executionPlan == null ? 0 : executionPlan.size(), currentTask == null ? "" : currentTask.getItemName());
        }

        if (paused) return;
        if (dropCollector.tick()) return;

        if (manageModules.get() && moduleGuard.shouldFreezeTimers()) {
            stateEnterTime = System.currentTimeMillis();
            stuckCounter = 0;
            return;
        }

        // Check for death recovery first
        if (deathTracker.hasDeath() && deathTracker.canRecover()) {
            handleRecovery();
            return;
        } else if (deathTracker.hasDeath() && !deathTracker.canRecover()) {
            deathTracker.clearDeath();
            if (currentTask != null) {
                try { sendTaskFailed(currentTask, "Bot died"); } catch (Exception ignored) {}
                currentTask = null;
            }
            setState(BrainState.IDLE);
        }

        // Standing food-cooking check: raw food plus a furnace/fuel in range.
        if (tickCounter % 20 == 0 && (state == BrainState.IDLE || state == BrainState.HUNTING)) {
            maybeQueueCooking();
        }

        // Check inventory fullness and transition to depositing if needed
        if (state == BrainState.GATHERING && shouldDeposit() && System.currentTimeMillis() - stateEnterTime > 5000) {
            setState(BrainState.DEPOSITING);
            // send #stop once when we detected the need to deposit (handled in state transition)
            if (canSendBaritone()) sendBaritoneChat("#stop");
        }

        switch (state) {
            case IDLE -> handleIdle();
            case CHECKING_EQUIPMENT -> handleEquipmentCheck();
            case GATHERING -> handleGathering();
            case HUNTING -> handleHunting();
            case GRINDING -> handleGrinding();
            case SMITHING -> handleSmithing();
            case SHULKER -> handleShulker();
            case BUILDING -> handleBuilding();
            case WITHDRAWING -> handleWithdrawing();
            case RETURNING_TO_BASE -> handleReturningToBase();
            case CRAFTING_PREP -> handleCraftingPrep();
            case CRAFTING -> handleCrafting();
            case SMELTING_DEPOSIT -> handleSmeltingDeposit();
            case SMELTING_WAIT -> handleSmeltingWait();
            case SMELTING_COLLECT -> handleSmeltingCollect();
            case DEPOSITING -> handleDepositing();
            case RECOVERING -> handleRecovery();
            case STUCK -> handleStuck();
        }

        updateEta();
    }

    // === STATE HANDLERS ===

    private void handleIdle() {
        if (currentTask == null) {
        if (tryAutoHunt()) return;
            if (tryAutoGrind()) return;
            if (tryAutoSmithing()) return;
            if (tryAutoShulker()) return;
            if (tryAutoBuild()) return;
            currentTaskDescription = "Idle — no task";
            return;
        }

        if (currentTask.isComplete()) {
            if (dropCollector.startFinalSweep(currentTask.getId())) return;
            ChatUtils.infoPrefix("BotBrain", "Task complete: %s %s",
                currentTask.getItemName(), currentTask.getProgressString());
            // Notify host
            try { sendTaskComplete(currentTask); } catch (Exception ignored) {}
            currentTask = null;
            currentTaskDescription = "Idle — task complete";
            return;
        }

        BotTask.Type taskType = currentTask.getType();
        if (directSmelt && taskType == BotTask.Type.SMELT) {
            setState(BrainState.SMELTING_DEPOSIT);
            return;
        }
        if (taskType == BotTask.Type.HUNT_FOOD) {
            if (state != BrainState.HUNTING) beginHunt();
            return;
        }
        if (taskType == BotTask.Type.SMITH) {
            if (state != BrainState.SMITHING) beginSmithing();
            return;
        }
        if (taskType == BotTask.Type.SHULKER) {
            if (state != BrainState.SHULKER) beginShulker();
            return;
        }
        if (taskType == BotTask.Type.BUILD) {
            if (state != BrainState.BUILDING) beginBuild();
            return;
        }
        if (taskType != BotTask.Type.GATHER && taskType != BotTask.Type.CRAFT && taskType != BotTask.Type.SMELT) {
            failUnhandledTask(currentTask);
            return;
        }

        // Build execution plan
        if (executionPlan == null || executionPlan.isEmpty()) {
            executionPlan = DependencyResolver.resolve(
                currentTask.getTargetItem(),
                currentTask.getRemaining()
            ).steps;
            planStep = 0;

            // Planner-side storage checks moved out of worker; runtime queries happen via STORAGE_QUERY_REQUEST/RESPONSE
        }

        if (executionPlan.isEmpty()) {
            // Can gather directly
            setState(BrainState.GATHERING);
            return;
        }

        // Get current step
        BotTask step = getCurrentStep();
        if (step == null) {
            // All dependency steps done, do the main gather
            setState(BrainState.GATHERING);
            return;
        }

        switch (step.getType()) {
            case CRAFT -> {
                if (autoEquip.get()) setState(BrainState.CHECKING_EQUIPMENT);
                else {
                    craftPrepReturnPlanStep = planStep;
                    setState(BrainState.CRAFTING_PREP);
                }
            }
            case SMELT -> setState(BrainState.SMELTING_DEPOSIT);
            case GATHER -> {
                if (autoEquip.get()) setState(BrainState.CHECKING_EQUIPMENT);
                else setState(BrainState.GATHERING);
            }
            default -> failUnhandledTask(step);
        }
    }

    private void handleEquipmentCheck() {
        RecipeBook.Tier needed;
        BotTask step = getCurrentStep();

        if (step != null) {
            needed = RecipeBook.getRequiredTier(step.getTargetItem());
        } else {
            needed = RecipeBook.getRequiredTier(currentTask.getTargetItem());
        }

        RecipeBook.Tier current = DependencyResolver.getCurrentTier();

        if (current.level >= needed.level) {
            currentTaskDescription = "Equipment OK (" + current.name() + ")";
            if (step != null && step.getType() == BotTask.Type.CRAFT) {
                craftPrepReturnPlanStep = planStep;
                setState(BrainState.CRAFTING_PREP);
            } else {
                setState(BrainState.GATHERING);
            }
            return;
        }

        // Need to upgrade — resolve equipment tasks
        List<BotTask> equipTasks = DependencyResolver.getEquipmentTasks(needed);
        if (!equipTasks.isEmpty()) {
            // Prepend equipment tasks to execution plan
            executionPlan.addAll(0, equipTasks);
            planStep = 0;
            currentTaskDescription = "Upgrading to " + needed.name() + " tier";
            setState(BrainState.IDLE);
        } else {
            // Already have what we need somehow
            if (step != null && step.getType() == BotTask.Type.CRAFT) {
                setState(BrainState.CRAFTING_PREP);
            } else {
                setState(BrainState.GATHERING);
            }
        }
    }

    /** Count the inventory result of a mining target, including vanilla drop aliases. */
    private int countForTask(Item target) {
        if (target == null) return 0;
        int count = CraftingManager.countInInventory(target);
        if (target == Items.STONE) count += CraftingManager.countInInventory(Items.COBBLESTONE);
        return count;
    }

    private void handleGathering() {
        // Staging debug (once every 200 ticks)
        if (tickCounter % 200 == 0) {
            ChatUtils.infoPrefix("BotBrain", "[STAGING DEBUG] gatherStaging=%s distance=%d",
                gatherStaging.get(), gatherStagingDistance.get());
        }
        // Dedicated GATHER task type flow (no dependency resolution)
        if (currentTask != null && currentTask.getType() == com.rdh.swarm.task.BotTask.Type.GATHER) {
            net.minecraft.world.item.Item tgt = currentTask.getTargetItem();
            if (tgt == null || tgt == Items.AIR) {
                failUnhandledTask(currentTask);
                return;
            }

            String tgtId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tgt).toString();
            String cleanTarget = tgtId.startsWith("minecraft:") ? tgtId.substring(10) : tgtId;

            // apply alias maps
            String mineBlock = MINE_FOR.getOrDefault(cleanTarget, MINE_FOR_2.getOrDefault(cleanTarget, cleanTarget));

            // Periodic inventory check every ~20 ticks
            if (tickCounter % 20 == 0) {
                int have = countForTask(tgt);
                int need = currentTask.getTargetQuantity();
                ChatUtils.infoPrefix("BotBrain", "Gathering %s: %d/%d", cleanTarget, have, need);
                long inStorage = lastStorageCache != null ? lastStorageCache.getOrDefault(tgtId, lastStorageCache.getOrDefault(cleanTarget, 0L)) : 0L;
                int effectiveHave = have + (int) Math.min(inStorage, Integer.MAX_VALUE);
                if (tickCounter % 100 == 0) ChatUtils.infoPrefix("BotBrain", "[PLANNER] %s need=%d inv=%d storage=%d effective=%d", cleanTarget, need, have, inStorage, effectiveHave);
                if (have >= need) {
                    if (dropCollector.startFinalSweep(currentTask.getId())) return;
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    ChatUtils.infoPrefix("BotBrain", "Gathered enough %s (%d/%d)", cleanTarget, have, need);
                    // mark task complete
                    try { currentTask.addCollected(currentTask.getTargetQuantity() - currentTask.getCollectedQuantity()); } catch (Exception ignored) {}
                    currentTask.setStatus(com.rdh.swarm.task.BotTask.Status.COMPLETE);
                    try { sendTaskComplete(currentTask); } catch (Exception ignored) {}
                    try {
                        var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
                        // prefer returning to base before depositing when base known
                        BlockPos bp = getBasePosition();
                        if (bp != null && swarmMod != null && swarmMod.worker != null) {
                            pendingDepositAfterReturn = true;
                            currentTask = null;
                            executionPlan = null;
                            planStep = 0;
                            setState(BrainState.RETURNING_TO_BASE);
                        } else if (swarmMod != null && swarmMod.worker != null) {
                            // fallback: no known base, trigger immediate deposit
                            swarmMod.worker.triggerDeposit();
                            currentTask = null;
                            executionPlan = null;
                            planStep = 0;
                            setState(BrainState.DEPOSITING);
                        } else {
                            currentTask = null;
                            executionPlan = null;
                            planStep = 0;
                            setState(BrainState.IDLE);
                        }
                    } catch (Exception e) {
                        currentTask = null;
                        executionPlan = null;
                        planStep = 0;
                        setState(BrainState.IDLE);
                    }
                    // reset mining flags
                    currentlyMining = null;
                    mineCommandSent = false;
                    return;
                }
            }

            // Query storage availability (non-blocking). Withdraw attempts are disabled here
            try {
                int have = countForTask(tgt);
                int need = currentTask.getTargetQuantity();
                int deficit = need - have;
                if (deficit > 0 && out != null) {
                    String itemId = BuiltInRegistries.ITEM.getKey(tgt).toString();
                    Map<String, Integer> needs = new HashMap<>();
                    needs.put(itemId, deficit);
                    Map<String, Long> avail = Collections.emptyMap();
                    if ((tickCounter % 100 == 0) || (!storageQueryPending && (lastStorageCache == null || lastStorageCache.isEmpty()))) {
                        storageQueryPending = true;
                        avail = queryStorageBeforeGathering(needs);
                    }
                    long a = avail.getOrDefault(itemId, 0L);
                    // Non-blocking mode: do not issue WITHDRAW_REQUEST here; rely on cache and async responses.
                    // Future: implement non-blocking withdraw worker to request from host.
                }
            } catch (Exception ignored) {}

                // Send mine command once. First try a goal that ends on the block.
                if (!ensureStagedAwayFromBase()) return;
                if (!mineBlock.equals(currentlyMining)) {
                    String cmd = "#mine minecraft:" + mineBlock;

                    ChatUtils.infoPrefix("BotBrain", "GATHER: %s", cmd);
                    if (currentlyMining != null && !mineBlock.equals(currentlyMining)) {
                        // use sendBaritoneChat to respect cooldown handling
                        sendBaritoneChat("#stop");
                        currentlyMining = null;
                        mineCommandSent = false;
                        return; // avoid sending a new mine command this tick
                    }
                    if (canSendBaritone()) {
                        sendBaritoneChat(cmd);
                        currentlyMining = mineBlock;
                        mineCommandSent = true;
                    }
                }

                return;
        }
        // If this is a CRAFT task, use resolverGatherList to ensure raw materials are gathered first
        if (currentTask != null && currentTask.getType() == BotTask.Type.CRAFT && resolverGatherList != null && !resolverGatherList.isEmpty()) {
            boolean allMet = true;
            String mineTarget = null;
            int mineNeed = 0;
            int mineHave = 0;

            // Build storageAvailable map from lastStorageCache (non-blocking cache)
            Map<Item, Long> storageAvailable = new HashMap<>();
            if (lastStorageCache != null && !lastStorageCache.isEmpty()) {
                for (Map.Entry<String, Long> cacheEntry : lastStorageCache.entrySet()) {
                    String id = cacheEntry.getKey();
                    long count = cacheEntry.getValue();
                        if (count > 0) {
                            net.minecraft.resources.Identifier idf = net.minecraft.resources.Identifier.tryParse(com.rdh.swarm.StorageIndex.normalizeId(id));
                            if (idf != null) {
                                Item it = BuiltInRegistries.ITEM.getValue(idf);
                                if (it != null) storageAvailable.put(it, count);
                            }
                        }
                }
            }

            // Cache debug: show raw cache entries and resolved map (throttled)
            if (tickCounter % 100 == 0) {
                ChatUtils.infoPrefix("BotBrain", "[CACHE DEBUG] lastStorageCache=%d entries, storageAvailable=%d entries",
                    lastStorageCache != null ? lastStorageCache.size() : -1,
                    storageAvailable.size());
                if (lastStorageCache != null) {
                    for (var e : lastStorageCache.entrySet()) {
                        ChatUtils.infoPrefix("BotBrain", "[CACHE RAW] %s = %d", e.getKey(), e.getValue());
                    }
                }
            }

            for (var entry : resolverGatherList.entrySet()) {
                Item material = entry.getKey();
                int need = entry.getValue();
                int have = CraftingManager.countInInventory(material);
                // Apply drop aliases BEFORE computing effectiveHave
                // Mining stone drops cobblestone, not stone item
                if (material == Items.STONE) {
                    have += CraftingManager.countInInventory(Items.COBBLESTONE);
                }
                // Mining gravel can drop flint
                if (material == Items.GRAVEL) {
                    have += CraftingManager.countInInventory(Items.FLINT);
                }
                long inStorage = storageAvailable.getOrDefault(material, 0L);
                int effectiveHave = have + (int) Math.min(inStorage, Integer.MAX_VALUE);
                if (tickCounter % 100 == 0) ChatUtils.infoPrefix("BotBrain", "[PLANNER] %s need=%d inv=%d storage=%d effective=%d",
                    BuiltInRegistries.ITEM.getKey(material).getPath(), need, have, inStorage, effectiveHave);

                if (tickCounter % 100 == 0) ChatUtils.infoPrefix("BotBrain", "[GATHER CHECK] %s need=%d have=%d",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material).getPath(), need, have);

                if (effectiveHave < need) {
                    allMet = false;
                    if (mineTarget == null) {  // pick FIRST missing only
                        mineTarget = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(material).toString();
                        mineNeed = need;
                        mineHave = effectiveHave;
                    }
                }
            }

            if (allMet) {
                // Stop Baritone immediately, bypass cooldown
                // Stop Baritone immediately, bypass cooldown
                sendBaritoneChat("#stop");
                // Skip past all GATHER steps in executionPlan
                if (executionPlan != null) {
                    while (planStep < executionPlan.size() && executionPlan.get(planStep).getType() == BotTask.Type.GATHER) {
                        executionPlan.get(planStep).setStatus(BotTask.Status.COMPLETE);
                        planStep++;
                    }
                }
                // Determine if any materials are satisfied by storage but not present in inventory
                List<Map.Entry<String, Integer>> toWithdraw = new ArrayList<>();
                if (resolverGatherList != null && lastStorageCache != null && !lastStorageCache.isEmpty()) {
                    for (var entry : resolverGatherList.entrySet()) {
                        Item material = entry.getKey();
                        int need = entry.getValue();
                        int have = CraftingManager.countInInventory(material);
                        // apply drop aliases matching gather logic
                        if (material == Items.STONE) have += CraftingManager.countInInventory(Items.COBBLESTONE);
                        if (material == Items.GRAVEL) have += CraftingManager.countInInventory(Items.FLINT);
                        String id = BuiltInRegistries.ITEM.getKey(material).toString();
                        long inStorage = lastStorageCache.getOrDefault(id, 0L);
                        int effectiveHave = have + (int) Math.min(inStorage, Integer.MAX_VALUE);
                        if (effectiveHave >= need && have < need) {
                            int needQty = Math.max(0, need - have);
                            toWithdraw.add(new AbstractMap.SimpleEntry<>(id, Integer.valueOf(needQty)));
                        }
                    }
                }

                if (toWithdraw != null && !toWithdraw.isEmpty()) {
                    this.pendingWithdrawals = toWithdraw;
                    this.withdrawInProgress = false;
                    ChatUtils.infoPrefix("BotBrain", "Need to withdraw %d items from storage before crafting", toWithdraw.size());
                    setState(BrainState.WITHDRAWING);
                    return;
                }

                ChatUtils.infoPrefix("BotBrain", "GATHERING COMPLETE: all materials satisfied");
                setState(BrainState.RETURNING_TO_BASE);
                return;
            }
            // Continue gathering first missing (mineTarget now valid)

            // Strip minecraft: prefix if present
            String cleanTarget = mineTarget.startsWith("minecraft:") ? mineTarget.substring(10) : mineTarget;

            // Material aliasing: mine one thing to get another (vanilla behavior)
            String mineBlock = cleanTarget;
            if (MINE_ALIASES.containsKey(cleanTarget)) {
                mineBlock = MINE_ALIASES.get(cleanTarget);
            }

            // Safety: do not mine craftable items/tools.
            String[] notMineable = {
                "pickaxe", "axe", "sword", "shovel", "hoe",
                "helmet", "chestplate", "leggings", "boots",
                "planks", "stick", "crafting_table", "furnace",
                "chest", "bucket", "shield", "bow", "arrow",
                "ladder", "boat", "door", "bed", "bread", "paper"
            };
            for (String keyword : notMineable) {
                if (cleanTarget.contains(keyword)) {
                    ChatUtils.infoPrefix("BotBrain", "SAFETY: %s is not mineable! Failing task.", cleanTarget);
                    try { sendTaskFailed(currentTask, "mine target not mineable: " + cleanTarget); } catch (Exception ignored) {}
                    currentTask = null;
                    executionPlan = null;
                    planStep = 0;
                    resolverGatherList = null;
                    resolverCraftPlan = null;
                    resolverNeeds3x3 = false;
                    setState(BrainState.IDLE);
                    return;
                }
            }

            // cobblestone drops from stone
            if (cleanTarget.equals("cobblestone") || cleanTarget.equals("minecraft:cobblestone")) {
                cleanTarget = "stone";
                mineBlock = "stone";
            }

            // Before sending mine command, query storage for all missing resolver materials and withdraw available
            try {
                if (resolverGatherList != null && out != null) {
                    Map<String, Integer> needs = new HashMap<>();
                    for (var entry : resolverGatherList.entrySet()) {
                        Item material = entry.getKey();
                        int need = entry.getValue();
                        int have = CraftingManager.countInInventory(material);
                        if (material == Items.STONE) have += CraftingManager.countInInventory(Items.COBBLESTONE);
                        if (material == Items.GRAVEL) have += CraftingManager.countInInventory(Items.FLINT);
                        int deficitNow = Math.max(0, need - have);
                        if (deficitNow > 0) {
                            String id = BuiltInRegistries.ITEM.getKey(material).toString();
                            needs.put(id, deficitNow);
                        }
                    }

                    if (!needs.isEmpty()) {
                        Map<String, Long> avail = Collections.emptyMap();
                        if ((tickCounter % 100 == 0) || (!storageQueryPending && (lastStorageCache == null || lastStorageCache.isEmpty()))) {
                            storageQueryPending = true;
                            avail = queryStorageBeforeGathering(needs);
                        }
                        // Non-blocking mode: we do not send WITHDRAW_REQUESTs here. Cache will be used instead.
                    }
                }
            } catch (Exception ignored) {}

                if (!ensureStagedAwayFromBase()) return;
                if (!mineBlock.equals(currentlyMining)) {
                String cmd = "#mine minecraft:" + mineBlock;
                

                if (!mineBlock.equals(cleanTarget)) {
                    ChatUtils.infoPrefix("BotBrain", "Mining %s to get %s", mineBlock, cleanTarget);
                }
                ChatUtils.infoPrefix("BotBrain", "GATHERING: %s (have %d/%d)", cmd, mineHave, mineNeed);
                if (currentlyMining != null && !mineBlock.equals(currentlyMining)) {
                    sendBaritoneChat("#stop");
                    currentlyMining = null;
                    mineCommandSent = false;
                    return;
                }
                if (canSendBaritone()) {
                    sendBaritoneChat(cmd);
                    currentlyMining = mineBlock;
                    mineCommandSent = true;
                }
            }

            return;
        }

        // FALLBACK: existing gather behavior for GATHER-type tasks or when no resolver data
        BotTask step = getCurrentStep();
        Item target = step != null ? step.getTargetItem() : currentTask.getTargetItem();
        int needed = step != null ? step.getRemaining() : currentTask.getRemaining();

        // If this gather step is marked as FROM_STORAGE, we do NOT perform blocking withdraws here.
        // Query/cache will be used instead; withdrawal will be handled asynchronously in the future.
        if (step != null && step.getData() != null && Boolean.TRUE.equals(step.getData().get("from_storage")) && out != null) {
            // Intentionally non-blocking: do not send WITHDRAW_REQUEST here.
        }

        String itemName = BuiltInRegistries.ITEM.getKey(target).getPath();
        currentTaskDescription = "Mining " + itemName;

        // Check current inventory
        int have = countForTask(target);

        // Track progress time for no-progress detection
        if (have > lastProgressCount) {
            lastProgressCount = have;
            lastProgressTime = System.currentTimeMillis();
        }

        // Report progress to host when count changes
        if (have != lastReportedCount && currentTask != null) {
            lastReportedCount = have;
            try {
                if (out != null) {
                    out.writeUTF(String.format("TASK_PROGRESS:%s:%s:%d:%d:%s:%s",
                        getWorkerName(),
                        currentTask.getId().toString(),
                        have,
                        currentTask.getTargetQuantity(),
                        state.name(),
                        currentEta != null ? currentEta : ""));
                    out.flush();
                }
            } catch (Exception ignored) {}
        }

        // If we already have enough, finish step and potentially the main task
        if (have >= needed) {
            sendBaritoneChat("#stop");

            // Log gather progress for debugging
            if (step != null && step.getType() == BotTask.Type.GATHER) {
                ChatUtils.infoPrefix("BotBrain", "Gather check: have %d/%d %s", have, needed, java.util.Optional.ofNullable(step.getTargetItem()).map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath()).orElse(""));
            }

            if (step != null) {
                step.setStatus(BotTask.Status.COMPLETE);
                try { sendTaskProgress(step); } catch (Exception ignored) {}
                advancePlan();
            }

            // Check if main task is now complete
            if (currentTask != null) {
                int totalHave = countForTask(currentTask.getTargetItem());
                if (totalHave >= currentTask.getTargetQuantity()) {
                    if (dropCollector.startFinalSweep(currentTask.getId())) return;
                    // update collected count to reflect actual inventory before marking complete
                    try {
                        currentTask.addCollected(currentTask.getTargetQuantity() - currentTask.getCollectedQuantity());
                    } catch (Exception ignored) {}
                    currentTask.setStatus(BotTask.Status.COMPLETE);
                    ChatUtils.infoPrefix("BotBrain", "Task complete: %s %s", currentTask.getItemName(), currentTask.getProgressString());
                    try { sendTaskComplete(currentTask); } catch (Exception ignored) {}

                    // Trigger deposit after task complete
                    try {
                        var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
                        if (swarmMod != null && swarmMod.worker != null) {
                            BlockPos bp = getBasePosition();
                            if (bp != null) {
                                pendingDepositAfterReturn = true;
                                currentTask = null; executionPlan = null; planStep = 0;
                                setState(BrainState.RETURNING_TO_BASE);
                            } else {
                                swarmMod.worker.triggerDeposit();
                                currentTask = null; executionPlan = null; planStep = 0;
                                setState(BrainState.DEPOSITING);
                            }
                        } else {
                            currentTask = null;
                            executionPlan = null;
                            planStep = 0;
                            setState(BrainState.IDLE);
                        }
                    } catch (Exception e) {
                        currentTask = null;
                        executionPlan = null;
                        planStep = 0;
                        setState(BrainState.IDLE);
                    }

                    mineCommandSent = false;
                    return;
                }
            }

            mineCommandSent = false;
            setState(BrainState.IDLE);
            return;
        }

        // Send baritone mine command once when entering gathering — prefer tools for tool-required targets
        if (!ensureStagedAwayFromBase()) return;
        if (!mineCommandSent) {
            String targetId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).toString();
            if (requiresTool(targetId) && !hasUsableTool()) {
                ChatUtils.warningPrefix("BotBrain", "No usable tool for %s. Pausing.", targetId);
                setState(BrainState.STUCK);
                return;
            }

            

            if (canSendBaritone()) {
                sendBaritoneChat("#mine minecraft:" + itemName);
                mineCommandSent = true;
                lastProgressTime = System.currentTimeMillis();
            } else {
                ChatUtils.infoPrefix("BotBrain", "Waiting for baritone cooldown before sending mine command");
            }
        }

        // No-progress timeout: if we sent mine and nothing changed for 30 seconds, retry
        if (mineCommandSent && System.currentTimeMillis() - lastProgressTime > 30000) {
            ChatUtils.warningPrefix("BotBrain", "No progress for 30 seconds. Retrying.");
            mineCommandSent = false;
            currentlyMining = null;
            lastProgressTime = System.currentTimeMillis();
        }

        // Track progress for ETA
        trackItemCollection(target);
    }

    private void handleReturningToBase() {
        // If we're in the nether and did a nether transit, return through portal using dedicated logic
        if (inNetherTransit) {
            // Already back in overworld? Clear flags and continue normal return
            if (!isInNether()) {
                ChatUtils.infoPrefix("BotBrain", "Back in overworld — resuming normal return to base");
                inNetherTransit = false;
                netherReturnGotoSent = false;
                netherReturnPortalSent = false;
                netherReturnStartTime = 0;
                baseReturnCommandSent = false;
                surfaceTarget = null;
                surfaceCommandSent = false;
                // Fall through to normal return logic below
            } else {
                // Still in nether — navigate to portal
                SwarmWaypoint portal = getPortalWaypoint();
                if (portal == null || !portal.hasNetherCoords()) {
                    ChatUtils.errorPrefix("BotBrain", "No portal waypoint with nether coords — stuck in nether!");
                    setState(BrainState.STUCK);
                    return;
                }

                int[] nc = portal.getCoords(true);

                // Timeout check (2 minutes to get back to portal)
                if (netherReturnStartTime == 0) {
                    netherReturnStartTime = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - netherReturnStartTime > 120_000) {
                    ChatUtils.errorPrefix("BotBrain", "Nether return timed out after 2 minutes");
                    setState(BrainState.STUCK);
                    return;
                }

                // Step 1: Send goto nether portal coords
                if (!netherReturnGotoSent) {
                    if (canSendBaritone()) {
                        ChatUtils.infoPrefix("BotBrain", "Returning to nether portal at %d %d %d", nc[0], nc[1], nc[2]);
                        sendBaritoneChat(String.format("#goto %d %d %d", nc[0], nc[1], nc[2]));
                        netherReturnGotoSent = true;
                    }
                    return;
                }

                // Step 2: Check if close enough to portal, then walk in
                if (!netherReturnPortalSent) {
                    double dx = mc.player.getX() - nc[0];
                    double dy = mc.player.getY() - nc[1];
                    double dz = mc.player.getZ() - nc[2];
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist < 15) {
                        if (canSendBaritone()) {
                            ChatUtils.infoPrefix("BotBrain", "Near nether portal, entering — #goto nether_portal");
                            sendBaritoneChat("#goto nether_portal");
                            netherReturnPortalSent = true;
                            // Reset timeout — give another 30 seconds for portal transit
                            netherReturnStartTime = System.currentTimeMillis();
                        }
                    }
                    return;
                }

                // Step 3: Portal entry sent, waiting for dimension change
                // isInNether() check at the top of this block will catch it
                return;
            }
        }
        if (basePos == null) {
            basePos = getBasePosition();
            if (basePos == null) {
                ChatUtils.warningPrefix("BotBrain", "No base position known (no deposit chests configured). Trying to craft/smelt on site.");
                // Fall through to next step type directly
                BotTask nextStep = getCurrentStep();
                if (nextStep != null && nextStep.getType() == BotTask.Type.SMELT) {
                    setState(BrainState.SMELTING_DEPOSIT);
                } else if (nextStep != null && nextStep.getType() == BotTask.Type.CRAFT) {
                    craftPrepReturnPlanStep = planStep;
                    setState(BrainState.CRAFTING_PREP);
                } else {
                    setState(BrainState.IDLE);
                }
                return;
            }
        }

        currentTaskDescription = String.format("Returning to base (%d, %d, %d)", basePos.getX(), basePos.getY(), basePos.getZ());

        if (tickCounter % 20 == 0) ChatUtils.infoPrefix("BotBrain", "[RETURN DEBUG] sent=%s canSend=%s base=%s",
            baseReturnCommandSent,
            canSendBaritone(),
            basePos == null ? "null" : (basePos.getX() + "," + basePos.getY() + "," + basePos.getZ()));

        // Send goto command once (only mark sent when actually sent)
        if (!baseReturnCommandSent) {
            int playerY = mc.player.blockPosition().getY();
            int baseY = basePos.getY();
            
            if (playerY < baseY - 30) {
                // Need to surface first. Send goto ONCE using a saved target.
                if (surfaceTarget == null) {
                    int surfaceX = mc.player.blockPosition().getX();
                    int surfaceZ = mc.player.blockPosition().getZ();
                    surfaceTarget = new BlockPos(surfaceX, baseY, surfaceZ);
                    ChatUtils.infoPrefix("BotBrain", "Surfacing first to %d %d %d (then will goto base)", surfaceX, baseY, surfaceZ);
                }
                
                if (!surfaceCommandSent && canSendBaritone()) {
                    String cmd = String.format("#goto %d %d %d", surfaceTarget.getX(), surfaceTarget.getY(), surfaceTarget.getZ());
                    sendBaritoneChat(cmd);
                    surfaceCommandSent = true;
                }
                
                // Check if surfaced
                if (playerY >= baseY - 30) {
                    ChatUtils.infoPrefix("BotBrain", "Surfaced, now heading to base");
                    surfaceTarget = null;
                    surfaceCommandSent = false;
                    // Fall through to send base goto next tick
                }
                
                return; // Don't send base goto yet
            }
            
            // At or above surface level, go to base
            if (canSendBaritone()) {
                String cmd = String.format("#goto %d %d %d", basePos.getX(), basePos.getY(), basePos.getZ());
                sendBaritoneChat(cmd);
                ChatUtils.infoPrefix("BotBrain", "Returning to base: %s", cmd);
                baseReturnCommandSent = true;
            }
        }

        // Check if we've arrived (within 5 blocks)
        if (mc.player != null) {
            double distSq = mc.player.blockPosition().distSqr(basePos);
            if (distSq <= 25) {  // 5 blocks
                if (canSendBaritone()) sendBaritoneChat("#stop");
                ChatUtils.infoPrefix("BotBrain", "Arrived at base. Proceeding with smelt/craft.");

                // If we were asked to deposit after returning, do it now
                try {
                    if (pendingDepositAfterReturn) {
                        var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
                        if (swarmMod != null && swarmMod.worker != null) {
                            ChatUtils.infoPrefix("BotBrain", "Gather complete, returning to base before deposit");
                            swarmMod.worker.triggerDeposit();
                            pendingDepositAfterReturn = false;
                            currentTask = null; executionPlan = null; planStep = 0;
                            setState(BrainState.DEPOSITING);
                            return;
                        }
                    }
                } catch (Exception ignored) {}

                // Transition to whatever the next plan step needs
                BotTask nextStep = getCurrentStep();
                if (nextStep != null && nextStep.getType() == BotTask.Type.SMELT) {
                    setState(BrainState.SMELTING_DEPOSIT);
                } else if (nextStep != null && nextStep.getType() == BotTask.Type.CRAFT) {
                    craftPrepReturnPlanStep = planStep;
                    setState(BrainState.CRAFTING_PREP);
                } else {
                    setState(BrainState.IDLE);
                }
                return;
            }
        }

        // Timeout after 5 minutes of pathing
        if (System.currentTimeMillis() - stateEnterTime > 300000) {
            ChatUtils.warningPrefix("BotBrain", "Timeout returning to base. Going STUCK.");
            setState(BrainState.STUCK);
        }
    }

    private void handleWithdrawing() {
        try {
            if (pendingWithdrawals == null || pendingWithdrawals.isEmpty()) {
                withdrawInProgress = false;
                setState(BrainState.RETURNING_TO_BASE);
                return;
            }

            // current target is the first entry
            Map.Entry<String, Integer> target = pendingWithdrawals.get(0);
            String itemId = target.getKey();
            int qty = target.getValue();

            // If a withdraw is already in progress, poll inventory for completion or timeout
            if (withdrawInProgress) {
                // check inventory for item
                try {
                    net.minecraft.resources.Identifier idf = net.minecraft.resources.Identifier.tryParse(com.rdh.swarm.StorageIndex.normalizeId(itemId));
                    net.minecraft.world.item.Item it = idf == null ? null : BuiltInRegistries.ITEM.getValue(idf);
                    int have = 0;
                    if (it != null) have = CraftingManager.countInInventory(it);
                    if (have >= qty) {
                        // completed
                        pendingWithdrawals.remove(0);
                        withdrawInProgress = false;
                        return;
                    }
                } catch (Exception ignored) {}

                // check timeout
                if (System.currentTimeMillis() - withdrawStartTime > WITHDRAW_TIMEOUT_MS) {
                    ChatUtils.warningPrefix("BotBrain", "Withdraw timed out for %s, going STUCK", itemId);
                    withdrawInProgress = false;
                    setState(BrainState.STUCK);
                    return;
                }

                // still waiting
                return;
            }

            // No withdraw in progress; initiate one using worker deposit chest list
            var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
            if (swarmMod == null || swarmMod.worker == null) {
                ChatUtils.warningPrefix("BotBrain", "No worker available for withdraw");
                setState(BrainState.STUCK);
                return;
            }

            Map<String, List<int[]>> chests = null;
            try { chests = swarmMod.worker.depositChests; } catch (Exception ignored) {}
            List<int[]> candidates = null;
            if (chests != null) candidates = chests.get("all");
            if ((candidates == null || candidates.isEmpty()) && chests != null) {
                for (var e : chests.entrySet()) { if (e.getValue() != null && !e.getValue().isEmpty()) { candidates = e.getValue(); break; } }
            }

            if (candidates == null || candidates.isEmpty()) {
                ChatUtils.warningPrefix("BotBrain", "No deposit chests known to perform withdraw");
                setState(BrainState.STUCK);
                return;
            }

            int[] pos = candidates.get(0);
            int x = pos[0], y = pos[1], z = pos[2];
            String category = "all";

            String args = String.format("%s %d %d %d %s %d", category, x, y, z, itemId, qty);

            try {
                java.lang.reflect.Method m = swarmMod.worker.getClass().getDeclaredMethod("handleWithdraw", String.class);
                m.setAccessible(true);
                withdrawInProgress = true;
                withdrawStartTime = System.currentTimeMillis();
                MeteorExecutor.execute(() -> {
                    try { m.invoke(swarmMod.worker, args); } catch (Exception e) { ChatUtils.errorPrefix("BotBrain", "Withdraw invoke failed: %s", e.getMessage()); }
                });
            } catch (NoSuchMethodException e) {
                ChatUtils.errorPrefix("BotBrain", "Worker withdraw method not found: %s", e.getMessage());
                setState(BrainState.STUCK);
            } catch (Exception e) {
                ChatUtils.errorPrefix("BotBrain", "Failed to initiate withdraw: %s", e.getMessage());
                setState(BrainState.STUCK);
            }

        } catch (Exception e) {
            ChatUtils.errorPrefix("BotBrain", "handleWithdrawing error: %s", e.getMessage());
            setState(BrainState.STUCK);
        }
    }

    private boolean hasUsableTool() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            try {
                if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 5) continue;
            } catch (Exception ignored) {}
            String id = "";
            try { id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); } catch (Exception ignored) {}
            if (id.contains("pickaxe") || id.contains("axe") || id.contains("shovel")) return true;
        }
        return false;
    }

    private boolean requiresTool(String itemId) {
        if (itemId == null) return false;
        String id = itemId.toLowerCase();
        return id.contains("ore") ||
            id.contains("stone") ||
            id.contains("cobblestone") ||
            id.contains("obsidian") ||
            id.contains("iron") ||
            id.contains("gold") ||
            id.contains("diamond") ||
            id.contains("emerald") ||
            id.contains("lapis") ||
            id.contains("redstone") ||
            id.contains("copper") ||
            id.contains("ancient_debris") ||
            id.contains("netherrack") ||
            id.contains("deepslate") ||
            id.contains("basalt") ||
            id.contains("blackstone") ||
            id.contains("nether_bricks");
    }

    private boolean ensureItemInMainHand(Item item) {
        if (mc.player == null) return false;

        // Check if already in main hand
        if (mc.player.getMainHandItem().getItem() == item) return true;

        // Try hotbar first
        FindItemResult hotbar = InvUtils.findInHotbar(item);
        if (hotbar.found()) {
            InvUtils.swap(hotbar.slot(), true);
            if (mc.player.getMainHandItem().getItem() == item) return true;
            ChatUtils.infoPrefix("BotBrain", "Hotbar swap failed for %s", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
            return false;
        }

        // Try full inventory
        FindItemResult inv = InvUtils.find(item);
        if (!inv.found()) return false;

        // Move to current hotbar slot
        int currentHotbar = com.rdh.swarm.utils.BOInvUtils.getSelectedSlot();
        InvUtils.move().from(inv.slot()).toHotbar(currentHotbar);
        if (mc.player.getMainHandItem().getItem() == item) return true;

        ChatUtils.infoPrefix("BotBrain", "Inventory move failed for %s", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        return false;
    }

    private void handleCraftingPrep() {
        BotTask step = getCurrentStep();
        if (step == null) {
            setState(BrainState.IDLE);
            return;
        }

        net.minecraft.world.item.Item target = step.getTargetItem();
        String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).getPath();
        currentTaskDescription = "Preparing to craft " + itemName;

        RecipeBook.Recipe recipe = RecipeBook.getRecipe(target);
        if (recipe == null) {
            ChatUtils.warningPrefix("BotBrain", "No recipe for %s", itemName);
            setState(BrainState.IDLE);
            return;
        }

        if (!recipe.needsCraftingTable) {
            setState(BrainState.CRAFTING);
            return;
        }

        // Phased handling
        craftPrepTicks++;
        switch (craftPrepPhase) {
            case PREP_SCAN -> {
                if (craftPrepTicks == 1) ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Scanning for nearby table...");
                BlockPos found = CraftingManager.findCraftingTable(45);
                if (found != null) {
                    craftingTablePos = found;
                    double distSq = mc.player.blockPosition().distSqr(found);
                    ChatUtils.infoPrefix("BotBrain", "Found crafting table at %d %d %d (%.0f blocks away)", found.getX(), found.getY(), found.getZ(), Math.sqrt(distSq));
                    if (distSq <= 16) {
                        CraftingManager.openCraftingTable(found);
                        planStep = craftPrepReturnPlanStep;
                        setState(BrainState.CRAFTING);
                        return;
                    } else {
                        craftPrepPhase = PREP_GOTO;
                        waitingForBaritone = false;
                        craftPrepTicks = 0;
                    }
                } else {
                    ChatUtils.infoPrefix("BotBrain", "No crafting table within 30 blocks, will craft/place one");
                    if (CraftingManager.countInInventory(Items.CRAFTING_TABLE) > 0) {
                        craftPrepPhase = PREP_PLACE_TABLE;
                        craftPrepTicks = 0;
                    } else {
                        craftPrepPhase = PREP_CRAFT_TABLE;
                        craftPrepTicks = 0;
                    }
                }
            }

            case PREP_GOTO -> {
                if (!waitingForBaritone) {
                    String cmd = String.format("#goto %d %d %d", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
                    if (canSendBaritone()) mc.getConnection().sendChat(cmd);
                    ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Found table at %d %d %d, walking to it", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
                    waitingForBaritone = true;
                }
                double distSq = mc.player.blockPosition().distSqr(craftingTablePos);
                if (distSq <= 16) {
                    if (canSendBaritone()) mc.getConnection().sendChat("#stop");
                    waitingForBaritone = false;
                    CraftingManager.openCraftingTable(craftingTablePos);
                    planStep = craftPrepReturnPlanStep;
                    setState(BrainState.CRAFTING);
                    return;
                }
                if (craftPrepTicks > 600) {
                    if (canSendBaritone()) mc.getConnection().sendChat("#stop");
                    waitingForBaritone = false;
                    ChatUtils.infoPrefix("BotBrain", "Timeout walking to table, will craft/place one");
                    if (CraftingManager.countInInventory(Items.CRAFTING_TABLE) > 0) craftPrepPhase = PREP_PLACE_TABLE;
                    else if (resolverNeeds3x3 && resolverCraftPlan != null) { setState(BrainState.CRAFTING); return; }
                    else craftPrepPhase = PREP_CRAFT_TABLE;
                    craftPrepTicks = 0;
                }
            }

            case PREP_CRAFT_TABLE -> {
                if (craftPrepTicks == 1) ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: No table nearby, crafting one inline");

                if (prepCraftTarget == null) {
                    if (CraftingManager.countInInventory(Items.OAK_PLANKS) < 4) {
                        prepCraftTarget = Items.OAK_PLANKS;
                    } else {
                        prepCraftTarget = Items.CRAFTING_TABLE;
                    }
                    craftActions = null;
                    craftingStep = 0;
                    craftGridSlotIndex = 0;
                }

                boolean stillCrafting = tickCraftingPrepTarget(prepCraftTarget);
                if (stillCrafting) return;

                if (prepCraftTarget == Items.OAK_PLANKS) {
                    if (CraftingManager.countInInventory(Items.OAK_PLANKS) < 4) {
                        prepCraftTarget = Items.OAK_PLANKS;
                        craftActions = null;
                        craftingStep = 0;
                        craftGridSlotIndex = 0;
                        return;
                    }
                    prepCraftTarget = Items.CRAFTING_TABLE;
                    craftActions = null;
                    craftingStep = 0;
                    craftGridSlotIndex = 0;
                    return;
                }

                prepCraftTarget = null;
                craftPrepPhase = PREP_PLACE_TABLE;
                craftPrepTicks = 0;
                return;
            }

            case PREP_PLACE_TABLE -> {
                if (craftPrepTicks == 1) ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Have table item, placing...");

                BlockPos placePos = findPlacementSpot();
                if (placePos == null) {
                    ChatUtils.infoPrefix("BotBrain", "No valid spot to place table within 3 blocks!");
                    BlockPos fallback = mc.player.blockPosition();
                    if (!mc.level.getBlockState(fallback).isAir()) fallback = fallback.above();
                    if (mc.level.getBlockState(fallback).isAir()) {
                        placePos = fallback;
                        ChatUtils.infoPrefix("BotBrain", "Using fallback placement at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
                    } else {
                        ChatUtils.infoPrefix("BotBrain", "Cannot find ANY spot for table, going STUCK");
                        setState(BrainState.STUCK);
                        return;
                    }
                }

                // Ensure crafting table in main hand
                if (!ensureItemInMainHand(Items.CRAFTING_TABLE)) {
                    if (CraftingManager.countInInventory(Items.CRAFTING_TABLE) == 0) {
                        ChatUtils.infoPrefix("BotBrain", "No crafting table available");
                        if (resolverNeeds3x3 && resolverCraftPlan != null) {
                            setState(BrainState.CRAFTING);
                            return;
                        }
                        setState(BrainState.STUCK);
                    }
                    // Retry next tick
                    craftPrepTicks = 0;
                    return;
                }

                boolean placed = com.rdh.swarm.utils.PlacementHelper.placeBlockSafely(placePos, Items.CRAFTING_TABLE);
                if (placed) {
                    ChatUtils.infoPrefix("BotBrain", "Placed crafting table at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
                    craftingTablePos = placePos;
                    craftPrepPhase = PREP_WAIT_PLACE;
                    craftPrepTicks = 0;
                } else {
                    ChatUtils.infoPrefix("BotBrain", "Placement failed for crafting table at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
                    craftPrepTicks = 0;
                }
            }

            case PREP_WAIT_PLACE -> {
                if (craftPrepTicks >= 5) {
                    if (isBlockAt(craftingTablePos, Blocks.CRAFTING_TABLE)) {
                        ChatUtils.infoPrefix("BotBrain", "Crafting table placed at %d %d %d", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
                        pendingPlacementPos = null;
                        pendingPlacementItem = null;
                        pendingPlacementRetries = 0;
                        craftPrepPhase = PREP_OPEN_TABLE;
                        craftPrepTicks = 0;
                    } else {
                        pendingPlacementRetries++;
                        if (pendingPlacementRetries < MAX_PENDING_PLACEMENT_RETRIES) {
                            ChatUtils.infoPrefix("BotBrain", "Table not detected yet, retrying place (attempt %d)", pendingPlacementRetries);
                            craftPrepPhase = PREP_PLACE_TABLE;
                            craftPrepTicks = 0;
                        } else {
                            ChatUtils.infoPrefix("BotBrain", "Table not detected after %d attempts, clearing pending and choosing new target", pendingPlacementRetries);
                            pendingPlacementPos = null;
                            pendingPlacementItem = null;
                            pendingPlacementRetries = 0;
                            craftingTablePos = null;
                            craftPrepPhase = PREP_PLACE_TABLE;
                            craftPrepTicks = 0;
                        }
                    }
                }
            }

            case PREP_OPEN_TABLE -> {
                if (craftPrepTicks == 1) {
                    if (!isBlockAt(craftingTablePos, Blocks.CRAFTING_TABLE)) {
                        pendingPlacementPos = null;
                        pendingPlacementItem = null;
                        pendingPlacementRetries = 0;
                        craftingTablePos = null;
                        craftPrepPhase = PREP_PLACE_TABLE;
                        craftPrepTicks = 0;
                        return;
                    }
                    double dx = mc.player.getX() - (craftingTablePos.getX() + 0.5);
                    double dy = mc.player.getY() - (craftingTablePos.getY() + 0.5);
                    double dz = mc.player.getZ() - (craftingTablePos.getZ() + 0.5);
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > 4.0) {
                        String cmd = String.format("#goto %d %d %d", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
                        if (canSendBaritone()) mc.getConnection().sendChat(cmd);
                        ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Walking to table (dist=%.1f)", dist);
                        craftPrepTicks = 0;
                        return;
                    }
                    if (canSendBaritone()) mc.getConnection().sendChat("#stop");
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(craftingTablePos), Direction.UP, craftingTablePos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Opening table...");
                }
                if (CraftingManager.isCraftingOpen()) {
                    if (craftPrepTicks == 1) ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: 3x3 GUI open, resuming craft");
                    pendingPlacementPos = null;
                    pendingPlacementItem = null;
                    pendingPlacementRetries = 0;
                    planStep = craftPrepReturnPlanStep;
                    craftPrepPhase = PREP_SCAN;
                    craftPrepTicks = 0;
                    setState(BrainState.CRAFTING);
                    return;
                }
                if (craftPrepTicks > 100) {
                    ChatUtils.infoPrefix("BotBrain", "Failed to open table, going STUCK");
                    setState(BrainState.STUCK);
                    return;
                }
            }
        }
    }

    private BlockPos findPlacementSpot() {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();

        for (int radius = 1; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    for (int y = -1; y <= 1; y++) {
                        BlockPos candidate = playerPos.offset(x, y, z);
                        BlockState state = mc.level.getBlockState(candidate);
                        BlockState below = mc.level.getBlockState(candidate.below());
                        if (state.isAir() && below.isSolid()) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isBlockAt(BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (pos == null || mc.player == null || mc.level == null) return false;
        try {
            return mc.level.getBlockState(pos).getBlock() == block;
        } catch (Exception e) {
            return false;
        }
    }

    private BlockPos getBasePosition() {
        // Use deposit chest coordinates as base location
        try {
            var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
            if (swarmMod != null && swarmMod.worker != null) {
                // Prefer explicit base anchor if worker has one
                try {
                    java.lang.reflect.Field bx = swarmMod.worker.getClass().getDeclaredField("baseX");
                    java.lang.reflect.Field by = swarmMod.worker.getClass().getDeclaredField("baseY");
                    java.lang.reflect.Field bz = swarmMod.worker.getClass().getDeclaredField("baseZ");
                    bx.setAccessible(true); by.setAccessible(true); bz.setAccessible(true);
                    Object ox = bx.get(swarmMod.worker);
                    Object oy = by.get(swarmMod.worker);
                    Object oz = bz.get(swarmMod.worker);
                    if (ox instanceof Integer && oy instanceof Integer && oz instanceof Integer) {
                        Integer x = (Integer) ox;
                        Integer y = (Integer) oy;
                        Integer z = (Integer) oz;
                        if (x != null && y != null && z != null) return new BlockPos(x, y, z);
                    }
                } catch (Exception ignored) {}

                if (swarmMod.worker.depositChests != null) {
                    // Find any deposit chest coordinate — prefer "all" category
                    for (var entry : swarmMod.worker.depositChests.entrySet()) {
                        java.util.List<int[]> chests = entry.getValue();
                        if (chests != null && !chests.isEmpty()) {
                            int[] coords = chests.get(0);
                            return new BlockPos(coords[0], coords[1], coords[2]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void handleCrafting() {
        BotTask step = getCurrentStep();
        if (step == null) {
            setState(BrainState.IDLE);
            return;
        }

        net.minecraft.world.item.Item target = step.getTargetItem();
        com.rdh.swarm.task.RecipeBook.Recipe recipe =
            com.rdh.swarm.task.RecipeBook.getRecipe(target);

        if (recipe == null) {
            setState(BrainState.IDLE);
            return;
        }

        net.minecraft.world.inventory.AbstractContainerMenu handler =
            mc.player.containerMenu;

        boolean isCraftingTable = handler instanceof
            net.minecraft.world.inventory.CraftingMenu;
        boolean isPlayerScreen = handler instanceof
            net.minecraft.world.inventory.InventoryMenu;

        if (recipe.needsCraftingTable && !isCraftingTable) {
            craftPrepReturnPlanStep = planStep;
            setState(BrainState.CRAFTING_PREP);
            return;
        }

        int skipSlots = isCraftingTable ? 10 : 9;

        // Remap pattern to correct grid slots if needed
        java.util.Map<Integer, net.minecraft.world.item.Item> pattern;
        if (isPlayerScreen) {
            pattern = new java.util.HashMap<>();
            for (var entry : recipe.pattern.entrySet()) {
                int newSlot = switch (entry.getKey()) {
                    case 5 -> 1;
                    case 6 -> 2;
                    case 8 -> 3;
                    case 9 -> 4;
                    default -> -1;
                };
                if (newSlot == -1) {
                    setState(BrainState.IDLE);
                    return;
                }
                pattern.put(newSlot, entry.getValue());
            }
        } else {
            pattern = recipe.pattern;
        }

        // Build action list once
        if (craftActions == null) {
            craftActions = new java.util.ArrayList<>();
            for (var entry : pattern.entrySet()) {
                int gridSlot = entry.getKey();
                net.minecraft.world.item.Item ingredient = entry.getValue();
                craftActions.add(new Object[]{-1, gridSlot, ingredient});
            }
            craftGridSlotIndex = 0;
            craftingStep = 0;
        }

        // PHASE 1: Place items one per tick
        if (craftingStep == 0) {
            if (craftGridSlotIndex >= craftActions.size()) {
                craftingStep = 1;
                return;
            }

            Object[] action = craftActions.get(craftGridSlotIndex);
            int gridSlot = (Integer) action[1];
            net.minecraft.world.item.Item ingredient =
                (net.minecraft.world.item.Item) action[2];

            // Skip if already correct
            if (CraftingManager.gridSlotHasItem(
                    ingredient, handler, gridSlot)) {
                craftGridSlotIndex++;
                return;
            }

            // Find in inventory
            int srcSlot = CraftingManager.findIngredientSlot(
                ingredient, handler, skipSlots);
            if (srcSlot == -1) {
                // Missing ingredient handling: rate-limit logging and trigger re-resolve -> GATHERING
                String missingId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient).getPath();
                if (lastMissingIngredient == null || lastMissingIngredient != ingredient) {
                    ChatUtils.warningPrefix("BotBrain", "Missing ingredient: %s", missingId);
                    // stop any baritone activity
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    lastMissingIngredient = ingredient;
                    craftFailCount = 1;
                } else {
                    craftFailCount++;
                }

                // Re-resolve remaining needs and go gather (either immediately or after 3 failures)
                try {
                    if (craftFailCount >= 1) {
                        ChatUtils.infoPrefix("BotBrain", "Missing ingredient %s detected, re-resolving and gathering", missingId);
                        if (currentTask != null) {
                            var plan = DependencyResolver.resolve(currentTask.getTargetItem(), currentTask.getRemaining());
                            this.executionPlan = plan.steps;
                            this.resolverGatherList = plan.gatherList;
                            this.resolverCraftPlan = plan.craftPlan;
                        }
                        craftActions = null;
                        setState(BrainState.GATHERING);
                        return;
                    }
                } catch (Exception e) {
                    ChatUtils.warningPrefix("BotBrain", "Failed to re-resolve after missing ingredient: %s", e.getMessage());
                    craftActions = null;
                    setState(BrainState.IDLE);
                    return;
                }
            }

            // Pick up stack
            mc.gameMode.handleContainerInput(handler.containerId,
                srcSlot, 0,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);
            // Place 1 in grid
            mc.gameMode.handleContainerInput(handler.containerId,
                gridSlot, 1,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);
            // Put rest back
            mc.gameMode.handleContainerInput(handler.containerId,
                srcSlot, 0,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);

            craftGridSlotIndex++;
            return;
        }

        // PHASE 2: Take output
        if (craftingStep == 1) {
            if (CraftingManager.outputReady(handler)) {
                mc.gameMode.handleContainerInput(handler.containerId,
                    0, 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                    mc.player);
                ChatUtils.infoPrefix("BotBrain", "Crafted %s",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).getPath());
                step.setStatus(
                    com.rdh.swarm.task.BotTask.Status.COMPLETE);
                advancePlan();
                craftActions = null;

                // After each craft step, check if we already have the main target (early completion)
                if (currentTask != null) {
                    int haveNow = CraftingManager.countInInventory(currentTask.getTargetItem());
                    if (haveNow >= currentTask.getTargetQuantity()) {
                        ChatUtils.infoPrefix("BotBrain", "Target item acquired early after craft step; have %d/%d %s", haveNow, currentTask.getTargetQuantity(), currentTask.getItemName());
                        // Mark task complete and notify host
                        try { currentTask.addCollected(currentTask.getTargetQuantity() - currentTask.getCollectedQuantity()); } catch (Exception ignored) {}
                        currentTask.setStatus(BotTask.Status.COMPLETE);
                        ChatUtils.infoPrefix("BotBrain", "Task complete: %s %s", currentTask.getItemName(), currentTask.getProgressString());
                        try { sendTaskComplete(currentTask); } catch (Exception ignored) {}
                        // Trigger deposit (prefer returning to base first)
                        try {
                            var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
                            if (swarmMod != null && swarmMod.worker != null) {
                                BlockPos bp = getBasePosition();
                                if (bp != null) {
                                    pendingDepositAfterReturn = true;
                                    currentTask = null; executionPlan = null; planStep = 0;
                                    setState(BrainState.RETURNING_TO_BASE);
                                    return;
                                } else {
                                    swarmMod.worker.triggerDeposit();
                                    currentTask = null; executionPlan = null; planStep = 0;
                                    setState(BrainState.DEPOSITING);
                                    return;
                                }
                            }
                        } catch (Exception ignored) {}
                        currentTask = null;
                        executionPlan = null;
                        planStep = 0;
                        setState(BrainState.IDLE);
                        return;
                    }
                }

                // If this was the last craft step, after advancing plan check for completion or re-resolve
                if (executionPlan == null || planStep >= (executionPlan == null ? 0 : executionPlan.size())) {
                    if (currentTask != null) {
                        int have = CraftingManager.countInInventory(currentTask.getTargetItem());
                        if (have >= currentTask.getTargetQuantity()) {
                            ChatUtils.infoPrefix("BotBrain", "All craft steps done, have %d/%d %s", have, currentTask.getTargetQuantity(), currentTask.getItemName());
                            try { currentTask.addCollected(currentTask.getTargetQuantity() - currentTask.getCollectedQuantity()); } catch (Exception ignored) {}
                            currentTask.setStatus(BotTask.Status.COMPLETE);
                            ChatUtils.infoPrefix("BotBrain", "Task complete: %s %s", currentTask.getItemName(), currentTask.getProgressString());
                            try { sendTaskComplete(currentTask); } catch (Exception ignored) {}
                            try {
                                var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
                                if (swarmMod != null && swarmMod.worker != null) {
                                    swarmMod.worker.triggerDeposit();
                                    currentTask = null;
                                    executionPlan = null;
                                    planStep = 0;
                                    setState(BrainState.DEPOSITING);
                                    return;
                                }
                            } catch (Exception ignored) {}
                            currentTask = null;
                            executionPlan = null;
                            planStep = 0;
                            setState(BrainState.IDLE);
                            return;
                        } else {
                            // Re-resolve and go gather
                            ChatUtils.infoPrefix("BotBrain", "Craft steps done but only have %d/%d %s, re-resolving", have, currentTask.getTargetQuantity(), currentTask.getItemName());
                            try {
                                var plan = DependencyResolver.resolve(currentTask.getTargetItem(), currentTask.getRemaining());
                                this.executionPlan = plan.steps;
                                this.resolverGatherList = plan.gatherList;
                                this.resolverCraftPlan = plan.craftPlan;
                            } catch (Exception ignored) {}
                            setState(BrainState.GATHERING);
                            return;
                        }
                    }
                }
                setState(BrainState.IDLE);
            } else {
                // Wait one more tick
                craftingStep = 0;
                craftGridSlotIndex = 0;
                craftActions = null;
                setState(BrainState.CRAFTING);
            }
            return;
        }
    }

    private boolean tickCraftingPrepTarget(net.minecraft.world.item.Item target) {
        if (mc.player == null) return false;

        net.minecraft.world.inventory.AbstractContainerMenu handler = mc.player.containerMenu;
        boolean isPlayerScreen = handler instanceof net.minecraft.world.inventory.InventoryMenu;
        if (!isPlayerScreen) return false;

        RecipeBook.Recipe recipe = RecipeBook.getRecipe(target);
        if (recipe == null) return false;

        // Remap pattern for player 2x2 grid
        java.util.Map<Integer, net.minecraft.world.item.Item> pattern = new java.util.HashMap<>();
        for (var entry : recipe.pattern.entrySet()) {
            int newSlot = switch (entry.getKey()) {
                case 5 -> 1;
                case 6 -> 2;
                case 8 -> 3;
                case 9 -> 4;
                default -> -1;
            };
            if (newSlot == -1) continue;
            pattern.put(newSlot, entry.getValue());
        }

        int skipSlots = 9;

        // Build action list once for this prep craft
        if (craftActions == null) {
            craftActions = new java.util.ArrayList<>();
            for (var entry : pattern.entrySet()) {
                int gridSlot = entry.getKey();
                net.minecraft.world.item.Item ingredient = entry.getValue();
                craftActions.add(new Object[]{-1, gridSlot, ingredient});
            }
            craftGridSlotIndex = 0;
            craftingStep = 0;
        }

        // PHASE 1: Place items one per tick
        if (craftingStep == 0) {
            if (craftGridSlotIndex >= craftActions.size()) {
                craftingStep = 1;
                return true;
            }

            Object[] action = craftActions.get(craftGridSlotIndex);
            int gridSlot = (Integer) action[1];
            net.minecraft.world.item.Item ingredient =
                (net.minecraft.world.item.Item) action[2];

            // Skip if already correct
            if (CraftingManager.gridSlotHasItem(
                    ingredient, handler, gridSlot)) {
                craftGridSlotIndex++;
                return true;
            }

            // Find in inventory
            int srcSlot = CraftingManager.findIngredientSlot(
                ingredient, handler, skipSlots);
            if (srcSlot == -1) {
                // Missing ingredient handling: rate-limit logging and trigger gather
                String missingId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient).getPath();
                if (lastMissingIngredient == null || lastMissingIngredient != ingredient) {
                    ChatUtils.warningPrefix("BotBrain", "Missing ingredient: %s", missingId);
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    lastMissingIngredient = ingredient;
                    craftFailCount = 1;
                } else {
                    craftFailCount++;
                }

                if (craftFailCount >= 1) {
                    ChatUtils.infoPrefix("BotBrain", "Missing ingredient %s detected, re-gathering", missingId);
                    craftActions = null;
                    setState(BrainState.GATHERING);
                    return false;
                }

                return true;
            }

            // Pick up stack
            mc.gameMode.handleContainerInput(handler.containerId,
                srcSlot, 0,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);
            // Place 1 in grid
            mc.gameMode.handleContainerInput(handler.containerId,
                gridSlot, 1,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);
            // Put rest back
            mc.gameMode.handleContainerInput(handler.containerId,
                srcSlot, 0,
                net.minecraft.world.inventory.ContainerInput.PICKUP,
                mc.player);

            craftGridSlotIndex++;
            return true;
        }

        // PHASE 2: Take output
        if (craftingStep == 1) {
            if (CraftingManager.outputReady(handler)) {
                mc.gameMode.handleContainerInput(handler.containerId,
                    0, 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                    mc.player);
                ChatUtils.infoPrefix("BotBrain", "Crafted %s",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).getPath());
                craftActions = null;
                return false;
            } else {
                // Wait one more tick
                craftingStep = 0;
                craftGridSlotIndex = 0;
                craftActions = null;
                return true;
            }
        }

        return false;
    }

    private void handleSmeltingDeposit() {
        BotTask step = getCurrentStep();
        if (step == null) { setState(BrainState.IDLE); return; }
        Item ingot = step.getTargetItem();
        Item ore = step.getSourceItem();
        if (ore == null) ore = RecipeBook.getOreForIngot(ingot);
        if (ore == null) { setState(BrainState.IDLE); return; }

        currentTaskDescription = "Smelting " + BuiltInRegistries.ITEM.getKey(ore).getPath();

        smeltTicks++;
        switch (smeltPhase) {
            case SMELT_SCAN -> {
                if (smeltTicks == 1) ChatUtils.infoPrefix("BotBrain", "SMELT: scanning for nearby furnace...");
                // Search for furnace within 45 blocks
                if (mc.player == null || mc.level == null) { setState(BrainState.STUCK); return; }
                BlockPos playerPos = mc.player.blockPosition();
                int range = 45;
                boolean cookingTask = isCookingTask();
                BlockPos found = null;
                for (int x = -range; x <= range && found == null; x++) {
                    for (int y = -10; y <= 10 && found == null; y++) {
                        for (int z = -range; z <= range; z++) {
                            BlockPos pos = playerPos.offset(x, y, z);
                            var b = mc.level.getBlockState(pos).getBlock();
                            if (b == Blocks.FURNACE || b == Blocks.SMOKER || (!cookingTask && b == Blocks.BLAST_FURNACE)) {
                                found = pos;
                                break;
                            }
                        }
                    }
                }
                if (found != null) {
                    smelterPos = found;
                    smeltPhase = SMELT_GOTO;
                    smeltTicks = 0;
                } else {
                    // If we have a furnace item, place one nearby; otherwise attempt to craft one
                    if (InvUtils.find(Items.FURNACE).found() || InvUtils.findInHotbar(Items.FURNACE).found()) {
                        smeltPhase = SMELT_PLACE;
                        smeltTicks = 0;
                    } else {
                        // No furnace item — check if we can craft one (8 cobblestone)
                        if (CraftingManager.countInInventory(Items.COBBLESTONE) >= 8) {
                            ChatUtils.infoPrefix("BotBrain", "No furnace nearby or in inventory, crafting one (have cobblestone)");
                            // Insert a furnace craft step before the current smelt step
                            executionPlan.add(planStep, new BotTask(BotTask.Type.CRAFT, Items.FURNACE, 1));
                            craftPrepReturnPlanStep = planStep;
                            setState(BrainState.CRAFTING_PREP);
                            return;
                        } else {
                            ChatUtils.infoPrefix("BotBrain", "No furnace nearby, no furnace item, not enough cobblestone to craft one");
                            setState(BrainState.STUCK);
                            return;
                        }
                    }
                }
            }

            case SMELT_GOTO -> {
                if (smelterPos == null) { smeltPhase = SMELT_SCAN; smeltTicks = 0; return; }

                double distSq = mc.player.blockPosition().distSqr(smelterPos);

                // Already within interaction range — go straight to open
                if (distSq <= 20) {  // ~4.5 blocks
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    ChatUtils.infoPrefix("BotBrain", "SMELT: furnace within reach (%.1f blocks)", Math.sqrt(distSq));
                    smeltPhase = SMELT_OPEN;
                    smeltTicks = 0;
                    return;
                }

                // Need to walk closer — goto a position NEAR the furnace, not ON it
                // Use 2 blocks offset to avoid Baritone mining the furnace
                if (!mineCommandSent) {
                    if (canSendBaritone()) {
                        // Goto 2 blocks away from furnace (toward player) instead of exact coords
                        int gotoX = smelterPos.getX();
                        int gotoY = smelterPos.getY();
                        int gotoZ = smelterPos.getZ() - 2;
                        String cmd = String.format("#goto %d %d %d", gotoX, gotoY, gotoZ);
                        sendBaritoneChat(cmd);
                        ChatUtils.infoPrefix("BotBrain", "SMELT: walking near furnace at %d %d %d", smelterPos.getX(), smelterPos.getY(), smelterPos.getZ());
                        mineCommandSent = true;
                    }
                }

                // Check if close enough now
                if (distSq <= 20) {
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    smeltPhase = SMELT_OPEN;
                    smeltTicks = 0;
                    return;
                }

                if (smeltTicks > 600) {
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    ChatUtils.infoPrefix("BotBrain", "Timed out walking to furnace");
                    if (InvUtils.find(Items.FURNACE).found() || InvUtils.findInHotbar(Items.FURNACE).found()) {
                        smeltPhase = SMELT_PLACE;
                        smeltTicks = 0;
                    } else {
                        setState(BrainState.STUCK);
                        return;
                    }
                }
            }

            case SMELT_PLACE -> {
                if (smeltTicks == 1) ChatUtils.infoPrefix("BotBrain", "SMELT: placing furnace from inventory");
                // Find placement spot adjacent to player (reuse pending if present)
                BlockPos placePos = (pendingPlacementPos != null && pendingPlacementItem == Items.FURNACE) ? pendingPlacementPos : findPlacementSpot();
                if (placePos == null) {
                    placePos = mc.player.blockPosition().above();
                }

                // Ensure furnace in main hand
                if (!ensureItemInMainHand(Items.FURNACE)) {
                    if (InvUtils.find(Items.FURNACE).found()) {
                        // Retry next tick — ensureItemInMainHand will attempt to move it
                        smeltTicks = 0;
                        return;
                    }
                    ChatUtils.infoPrefix("BotBrain", "No furnace item available to place");
                    setState(BrainState.STUCK);
                    return;
                }

                boolean placed = com.rdh.swarm.utils.PlacementHelper.placeBlockSafely(placePos, Items.FURNACE);
                if (placed) {
                    smelterPos = placePos;
                    pendingPlacementPos = placePos;
                    pendingPlacementItem = Items.FURNACE;
                    pendingPlacementRetries = 0;
                    smeltPhase = SMELT_WAIT_PLACE;
                    smeltTicks = 0;
                } else {
                    ChatUtils.infoPrefix("BotBrain", "Placement failed for furnace at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
                    smeltTicks = 0; // helper enforces cooldown
                }
            }

            case SMELT_WAIT_PLACE -> {
                if (smeltTicks >= 4) {
                    if (isBlockAt(smelterPos, Blocks.FURNACE)) {
                        pendingPlacementPos = null;
                        pendingPlacementItem = null;
                        pendingPlacementRetries = 0;
                        ChatUtils.infoPrefix("BotBrain", "Furnace confirmed at %d %d %d", smelterPos.getX(), smelterPos.getY(), smelterPos.getZ());
                        smeltPhase = SMELT_OPEN;
                        smeltTicks = 0;
                    } else {
                        pendingPlacementRetries++;
                        if (pendingPlacementRetries < MAX_PENDING_PLACEMENT_RETRIES) {
                            ChatUtils.infoPrefix("BotBrain", "Furnace not detected yet, retrying place (attempt %d)", pendingPlacementRetries);
                            smeltPhase = SMELT_PLACE;
                            smeltTicks = 0;
                        } else {
                            ChatUtils.infoPrefix("BotBrain", "Furnace not detected after %d attempts, clearing pending and trying fresh", pendingPlacementRetries);
                            pendingPlacementPos = null;
                            pendingPlacementItem = null;
                            pendingPlacementRetries = 0;
                            smelterPos = null;
                            smeltPhase = SMELT_PLACE;
                            smeltTicks = 0;
                        }
                    }
                }
            }

            case SMELT_OPEN -> {
                if (smelterPos == null) { smeltPhase = SMELT_SCAN; smeltTicks = 0; return; }

                if (!isBlockAt(smelterPos, Blocks.FURNACE)) {
                    pendingPlacementPos = null;
                    pendingPlacementItem = null;
                    pendingPlacementRetries = 0;
                    smelterPos = null;
                    smeltPhase = SMELT_PLACE;
                    smeltTicks = 0;
                    return;
                }

                // Check if screen is already open
                if (SmeltingManager.isFurnaceOpen()) {
                    ChatUtils.infoPrefix("BotBrain", "SMELT: furnace GUI open");
                    smeltPhase = SMELT_INSERT;
                    smeltTicks = 0;
                    return;
                }

                // Send interact only once every ~20 ticks (5 seconds at 250ms brain tick)
                if (smeltTicks == 1 || smeltTicks % 20 == 0) {
                    ChatUtils.infoPrefix("BotBrain", "SMELT: opening furnace (attempt %d)...", smeltTicks);
                    SmeltingManager.openFurnace(smelterPos);
                }

                if (smeltTicks > 100) {
                    ChatUtils.infoPrefix("BotBrain", "Failed to open furnace after timeout");
                    setState(BrainState.STUCK);
                    return;
                }
            }

            case SMELT_INSERT -> {
                // Deposit fuel then ore
                if (smeltTicks == 1) ChatUtils.infoPrefix("BotBrain", "SMELT: inserting fuel and ore into furnace");
                boolean fuelOk = SmeltingManager.depositFuel();
                boolean oreOk = SmeltingManager.depositOre(ore);
                if (!fuelOk && !SmeltingManager.hasFuel() && SmeltingManager.countFuel() == 0) {
                    ChatUtils.warningPrefix("BotBrain", "SMELT: no fuel in furnace or inventory");
                    setState(BrainState.STUCK);
                    return;
                }
                if (!fuelOk) {
                    ChatUtils.warningPrefix("BotBrain", "SMELT: fuel not inserted");
                    setState(BrainState.STUCK);
                    return;
                }
                if (!oreOk) {
                    // try to move ore into hotbar
                    if (InvUtils.find(ore).found()) {
                        InvUtils.move().from(InvUtils.find(ore).slot()).toHotbar(com.rdh.swarm.utils.BOInvUtils.getSelectedSlot());
                        return;
                    }
                }

                // Assume inserted and go wait for smelt to complete
                smeltWaitStart = System.currentTimeMillis();
                // Close furnace screen so player isn't stuck in GUI
                if (mc.player != null) mc.setScreenAndShow(null);
                ChatUtils.infoPrefix("BotBrain", "SMELT: ore and fuel inserted, closing furnace and waiting");
                setState(BrainState.SMELTING_WAIT);
                return;
            }
        }
    }

    private void handleSmeltingWait() {
        currentTaskDescription = "Waiting for smelter";
        BotTask step = getCurrentStep();
        if (step == null) { setState(BrainState.IDLE); return; }
        if (smelterPos == null) { setState(BrainState.IDLE); return; }

        long now = System.currentTimeMillis();

        // Wait at least 10 seconds after inserting before first check
        if (now - smeltWaitStart < SMELT_CHECK_INTERVAL) return;

        // Only check furnace every SMELT_CHECK_INTERVAL ms
        if (now - lastSmeltCheckTime < SMELT_CHECK_INTERVAL) return;
        lastSmeltCheckTime = now;

        // Open furnace to inspect current progress
        if (!SmeltingManager.isFurnaceOpen()) {
            SmeltingManager.openFurnace(smelterPos);
            return;
        }

        // If input still exists, smelting is not finished yet
        boolean hasInput = SmeltingManager.hasInput();
        int outputCount = SmeltingManager.getOutputCount();

        if (hasInput) {
            ChatUtils.infoPrefix("BotBrain", "SMELT: still smelting (input remains, output=%d)", outputCount);

            // If furnace ran out of fuel but still has input, refuel
            if (!SmeltingManager.hasFuel() && SmeltingManager.countFuel() > 0) {
                ChatUtils.infoPrefix("BotBrain", "SMELT: refueling furnace");
                SmeltingManager.depositFuel();
            }

            SmeltingManager.closeFurnace();

            // 10 minute timeout
            if (now - smeltWaitStart > 600000) {
                ChatUtils.warningPrefix("BotBrain", "SMELT: wait timed out after 10 minutes");
                setState(BrainState.STUCK);
            }
            return;
        }

        // Input is empty, so smelting should be complete. Collect output.
        if (outputCount > 0) {
            boolean took = SmeltingManager.takeResult();
            if (took) {
                ChatUtils.infoPrefix("BotBrain", "SMELT: collected final smelted items");
            }
        }

        SmeltingManager.closeFurnace();

        // Verify inventory now contains enough result items
        Item targetIngot = step.getTargetItem();
        if (targetIngot != null) {
            int have = CraftingManager.countInInventory(targetIngot);
            int need = step.getTargetQuantity();
            if (have >= need) {
                ChatUtils.infoPrefix("BotBrain", "SMELT: complete, have %d/%d %s", have, need, BuiltInRegistries.ITEM.getKey(targetIngot).getPath());
                step.setStatus(BotTask.Status.COMPLETE);
                if (directSmelt && step == currentTask) currentTask.addCollected(currentTask.getRemaining());
                try { sendTaskProgress(step); } catch (Exception ignored) {}
                advancePlan();
                setState(BrainState.IDLE);
                return;
            } else {
                ChatUtils.warningPrefix("BotBrain", "SMELT: input empty but only have %d/%d %s after collection", have, need, BuiltInRegistries.ITEM.getKey(targetIngot).getPath());
                setState(BrainState.STUCK);
                return;
            }
        }

        setState(BrainState.IDLE);
    }

    private void handleSmeltingCollect() {
        // This state is a fallback — primary collection happens in SMELTING_WAIT
        // Just redirect to WAIT logic
        setState(BrainState.SMELTING_WAIT);
    }

    private long lastDepositCheckLog = 0L;

    private void handleDepositing() {
        currentTaskDescription = "Depositing items";
        
        try {
            var swarmMod = Modules.get()
                .get(com.rdh.swarm.EnhancedSwarm.class);
            if (swarmMod != null && swarmMod.worker != null) {
                if (!swarmMod.worker.isDepositing && System.currentTimeMillis() - lastDepositCheckLog >= 5000) {
                    lastDepositCheckLog = System.currentTimeMillis();
                    boolean pathing = false;
                    try { pathing = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing(); } catch (Throwable ignored) {}
                    boolean atChest = mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu;
                    ChatUtils.infoPrefix("BotBrain", "DEPOSIT: completion check pathing=%b distance=unknown atChest=%b transferRan=%b moved=%d success=%b",
                        pathing, atChest, swarmMod.worker.lastDepositTransferRan, swarmMod.worker.lastDepositMoved, swarmMod.worker.lastDepositSucceeded);
                }
                if (!swarmMod.worker.isDepositing) {
                    if (!swarmMod.worker.lastDepositSucceeded) {
                        ChatUtils.warningPrefix("BotBrain", "DEPOSIT: aborted — %s", swarmMod.worker.lastDepositFailure.isEmpty() ? "transfer did not complete" : swarmMod.worker.lastDepositFailure);
                        setState(BrainState.IDLE);
                        return;
                    }
                    ChatUtils.infoPrefix("BotBrain", "Deposit complete, going idle");
                    setState(BrainState.IDLE);
                    // If a task was queued while depositing, start it now
                    if (this.pendingTask != null) {
                        ChatUtils.infoPrefix("BotBrain", "Starting queued task after deposit: %s x%d", this.pendingTask.getItemName(), this.pendingTask.getTargetQuantity());
                        BotTask t = this.pendingTask;
                        this.pendingTask = null;
                        setTask(t);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {}
        
        if (System.currentTimeMillis() - stateEnterTime > 60000) {
            ChatUtils.infoPrefix("BotBrain",
                "Deposit timeout, going idle");
            setState(BrainState.IDLE);
        }
    }

    private void handleRecovery() {
        if (!deathTracker.canRecover()) {
            deathTracker.clearDeath();
            setState(BrainState.IDLE);
            return;
        }

        if (mc.level != null && !deathTracker.getDeathDimension().isEmpty()
            && !deathTracker.getDeathDimension().equals(mc.level.dimension().identifier().toString())) {
            ChatUtils.warningPrefix("BotBrain", "Death was in %s; refusing cross-dimension recovery", deathTracker.getDeathDimension());
            deathTracker.clearDeath();
            setState(BrainState.IDLE);
            return;
        }

        BlockPos deathPos = deathTracker.getDeathPos();
        currentTaskDescription = String.format("Recovering items at %d %d %d (%.0fs left)",
            deathPos.getX(), deathPos.getY(), deathPos.getZ(),
            deathTracker.getTimeRemainingMs() / 1000.0);

        if (canSendBaritone()) {
            sendBaritoneChat(String.format("#goto %d %d %d", deathPos.getX(), deathPos.getY(), deathPos.getZ()));
        }

        // Check if we're at death location
        if (mc.player != null) {
            double dist = mc.player.blockPosition().distSqr(deathPos);
            if (dist < 9) {
                ChatUtils.infoPrefix("BotBrain", "Arrived at death location. Picking up items.");
                deathTracker.clearDeath();
                setState(BrainState.IDLE);
            }
        }
    }

    private void beginHunt() {
        huntStartTime = System.currentTimeMillis();
        lastHuntProgress = huntStartTime;
        lastHuntExplore = 0;
        huntExploreRounds = 0;
        huntInitialFood = FoodManager.countFood();
        lastHuntDistance = Double.MAX_VALUE;
        huntFollowing = false;
        lastHuntTargetId = null;
        lastHuntTargetEntity = null;
        moduleGuard.setKillAuraSuppressed(true);
        currentTaskDescription = "Hunting food";
        setState(BrainState.HUNTING);
        ChatUtils.infoPrefix("BotBrain", "Food hunt started (food=%d target=%d)", huntInitialFood, currentTask == null ? 0 : currentTask.getTargetQuantity());
    }

    private void handleHunting() {
        if (currentTask == null) {
            stopHuntMovement();
            setState(BrainState.IDLE);
            return;
        }
        if (huntStartTime == 0) {
            beginHunt();
            return;
        }

        long now = System.currentTimeMillis();
        int targetFood = currentTask.getTargetQuantity() > 0 ? currentTask.getTargetQuantity() : foodTarget.get();
        int food = FoodManager.countFood();
        if (now - lastHuntProgress >= 10_000L) {
            lastHuntProgress = now;
            try { sendTaskProgress(currentTask); } catch (Exception ignored) {}
            ChatUtils.infoPrefix("BotBrain", "Food hunt: %d/%d food, gained %d", food, targetFood, Math.max(0, food - huntInitialFood));
        }
        int gained = Math.max(0, food - huntInitialFood);
        if (gained > currentTask.getCollectedQuantity()) currentTask.addCollected(gained - currentTask.getCollectedQuantity());
        if (food >= targetFood) {
            currentTask.addCollected(currentTask.getRemaining());
            completeHunt("food target reached");
            return;
        }
        if (now - huntStartTime > 300_000L) {
            failHunt("food hunt timeout");
            return;
        }

        if (lastHuntTargetEntity != null && !lastHuntTargetEntity.isAlive()) {
            lastHuntTargetEntity = null;
            lastHuntTargetId = null;
            stopHuntMovement();
            if (dropCollector.startWorkUnit()) return;
        }

        Entity target = findHuntTarget();
        if (target != null) {
            lastHuntTargetId = target.getUUID();
            lastHuntTargetEntity = target;
            huntExploreRounds = 0;
            if (!huntFollowing) {
                try {
                    baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().follow(this::isHuntTarget);
                    huntFollowing = true;
                } catch (Exception | NoClassDefFoundError e) {
                    ChatUtils.warningPrefix("BotBrain", "Food hunt follow unavailable");
                }
            }

            double distance = mc.player.distanceToSqr(target);
            if (distance + 1.0 < lastHuntDistance) {
                lastHuntDistance = distance;
                stateEnterTime = now;
            }
            currentTaskDescription = "Hunting " + target.getType().getDescription().getString();
            if (distance <= 9.0 && mc.gameMode != null) {
                Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
                if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                }
            }
            return;
        }

        if (lastHuntTargetEntity != null && !lastHuntTargetEntity.isAlive()) {
            lastHuntTargetEntity = null;
            lastHuntTargetId = null;
            stopHuntMovement();
            if (dropCollector.startWorkUnit()) return;
        }
        if (huntFollowing) stopHuntMovement();
        ItemEntity drop = findHuntDrop();
        if (drop != null) {
            currentTaskDescription = "Collecting food drops";
            if (canSendBaritone()) {
                sendBaritoneChat(String.format("#goto %d %d %d", drop.blockPosition().getX(), drop.blockPosition().getY(), drop.blockPosition().getZ()));
            }
            return;
        }

        if (now - lastHuntExplore >= 60_000L) {
            huntExploreRounds++;
            if (huntExploreRounds > 3) {
                failHunt("no animals found after 3 rounds");
                return;
            }
            currentTaskDescription = "Searching for animals (" + huntExploreRounds + "/3)";
            if (canSendBaritone()) sendBaritoneChat("#explore");
            lastHuntExplore = now;
        }
    }

    private Entity findHuntTarget() {
        if (mc.player == null || mc.level == null) return null;
        AABB box = new AABB(mc.player.blockPosition()).inflate(huntRange.get());
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, this::isHuntTarget)) {
            double distance = entity.distanceToSqr(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private boolean isHuntTarget(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || !HUNT_TYPES.contains(mob.getType())) return false;
        if (mob instanceof AgeableMob ageable && ageable.isBaby()) return false;
        if (mob.hasCustomName()) return false;
        if (mob instanceof TamableAnimal tamable && tamable.isTame()) return false;
        return mob.getLeashData() == null;
    }

    private ItemEntity findHuntDrop() {
        if (mc.player == null || mc.level == null) return null;
        AABB box = new AABB(mc.player.blockPosition()).inflate(8.0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof ItemEntity)) {
            if (!(entity instanceof ItemEntity drop)) continue;
            if (drop.getItem().isEmpty()) continue;
            double distance = drop.distanceToSqr(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = drop;
            }
        }
        return best;
    }

    private void stopHuntMovement() {
        if (huntFollowing) {
            try {
                baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().cancel();
            } catch (Exception | NoClassDefFoundError ignored) {}
            huntFollowing = false;
        }
    }

    private void completeHunt(String reason) {
        if (currentTask != null && dropCollector.startFinalSweep(currentTask.getId())) {
            stopHuntMovement();
            return;
        }
        stopHuntMovement();
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(task); } catch (Exception ignored) {}
        }
        ChatUtils.infoPrefix("BotBrain", "Food hunt complete: %s", reason);
        resumeAfterHunt(true);
    }

    private void failHunt(String reason) {
        stopHuntMovement();
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        ChatUtils.warningPrefix("BotBrain", "Food hunt failed: %s", reason);
        lastAutoHunt = System.currentTimeMillis();
        resumeAfterHunt(false);
    }

    private void cancelHunt(String reason) {
        boolean hunting = state == BrainState.HUNTING
            || (currentTask != null && currentTask.getType() == BotTask.Type.HUNT_FOOD);
        if (!hunting) return;

        stopHuntMovement();
        if (canSendBaritone()) sendBaritoneChat("#stop");
        try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        } catch (Exception | NoClassDefFoundError ignored) {}

        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        directSmelt = false;
        huntStartTime = 0;
        currentTaskDescription = "Idle — " + reason;
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        setState(BrainState.IDLE);
        ChatUtils.infoPrefix("BotBrain", "G2: hunt cancelled (%s)", reason);

        if (pendingTask != null) {
            BotTask next = pendingTask;
            pendingTask = null;
            setTask(next);
        }
    }

    private void resumeAfterHunt(boolean success) {
        if (pendingTask != null) {
            BotTask next = pendingTask;
            pendingTask = null;
            ChatUtils.infoPrefix("BotBrain", "Resuming interrupted task: %s x%d", next.getItemName(), next.getTargetQuantity());
            setTask(next);
            return;
        }
        if (success) maybeQueueCooking();
    }

    /** Auto-start a hunt when idle and food is below the low-water mark. */
    private boolean tryAutoHunt() {
        if (swarmRole.get() != SwarmRole.GENERAL && swarmRole.get() != SwarmRole.FOOD) {
            autoHuntSkipLogged = false;
            return false;
        }
        if (mc.player == null || mc.level == null) return false;
        int low = foodLow.get();
        if (low <= 0) {
            autoHuntSkipLogged = false;
            return false;
        }
        int food = FoodManager.countFood();
        if (food >= low) {
            autoHuntSkipLogged = false;
            return false;
        }
        if (!autoHunt.get()) {
            if (!autoHuntSkipLogged) {
                ChatUtils.infoPrefix("BotBrain", "G2: hunt skipped (auto-hunt off)");
                autoHuntSkipLogged = true;
            }
            return false;
        }
        autoHuntSkipLogged = false;
        long now = System.currentTimeMillis();
        if (now - lastAutoHunt < AUTO_HUNT_COOLDOWN) return false;
        lastAutoHunt = now;
        ChatUtils.warningPrefix("BotBrain", "Food low (%d < %d), starting automatic food hunt", food, low);
        setTask(new BotTask(BotTask.Type.HUNT_FOOD, null, foodTarget.get()));
        return true;
    }

    /** Interrupt a gathering task when the bot is completely out of food. */
    private void maybeInterruptForFood() {
        if (currentTask == null || currentTask.getType() == BotTask.Type.HUNT_FOOD) return;
        if (state != BrainState.GATHERING && state != BrainState.IDLE) return;
        if (FoodManager.countFood() > 0) {
            autoHuntSkipLogged = false;
            return;
        }
        if (!autoHunt.get()) {
            if (!autoHuntSkipLogged) {
                ChatUtils.infoPrefix("BotBrain", "G2: hunt skipped (auto-hunt off)");
                autoHuntSkipLogged = true;
            }
            return;
        }
        autoHuntSkipLogged = false;
        long now = System.currentTimeMillis();
        if (now - lastAutoHunt < AUTO_HUNT_COOLDOWN) return;
        lastAutoHunt = now;
        pendingTask = currentTask;
        currentTask = null;
        executionPlan = null;
        directSmelt = false;
        stopHuntMovement();
        if (canSendBaritone()) sendBaritoneChat("#stop");
        ChatUtils.warningPrefix("BotBrain", "Out of food; interrupting current task to hunt");
        setTask(new BotTask(BotTask.Type.HUNT_FOOD, null, foodTarget.get()));
    }

    /** After a successful hunt, cook surplus raw food when fuel is available. */
    private void maybeQueueCooking() {
        if (mc.player == null || mc.level == null) return;
        if (!autoCook.get()) {
            if (hasAnyRawFood() && !autoCookSkipLogged) {
                ChatUtils.infoPrefix("BotBrain", "G2: cook skipped (auto-cook off)");
                autoCookSkipLogged = true;
            }
            return;
        }
        long now = System.currentTimeMillis();

        BlockPos furnace = findCookFurnace();
        String rawIds = cookRawIds();
        String fuelIds = cookFuelIds(furnace);
        boolean hungry = FoodManager.countFood() < foodLow.get();
        String furnaceInfo = furnace == null ? "none"
            : String.valueOf((int) Math.round(Math.sqrt(mc.player.blockPosition().distSqr(furnace))));

        Item bestRaw = null;
        int bestCount = 0;
        for (Item raw : FoodManager.COOKING.keySet()) {
            int count = FoodManager.countRaw(raw);
            if (count > bestCount) {
                bestCount = count;
                bestRaw = raw;
            }
        }
        Item cooked = FoodManager.cookedResult(bestRaw);
        int want = 0;
        if (cooked != null) {
            int have = CraftingManager.countInInventory(cooked);
            want = Math.min(bestCount, Math.max(1, foodTarget.get() - have));
        }

        boolean hasRaw = bestRaw != null;
        boolean inventoryFuel = SmeltingManager.countFuel() > 0;
        boolean furnaceFuel = hasCookFuel(furnace);

        if (hasRaw && furnace == null && !noCookFurnaceWarned) {
            ChatUtils.warningPrefix("BotBrain", "G2: no furnace within %d blocks; cannot cook raw food", COOK_FURNACE_RANGE);
            noCookFurnaceWarned = true;
        } else if (furnace != null || !hasRaw) {
            noCookFurnaceWarned = false;
        }

        String decision;
        if (now - lastCookAttempt < COOK_COOLDOWN) decision = "skip: cooldown";
        else if (!hasRaw) decision = "skip: no raw food";
        else if (furnace == null) decision = "skip: no furnace";
        else if (!inventoryFuel && !furnaceFuel) decision = "skip: no fuel";
        else if (cooked == null) decision = "skip: no recipe";
        else if (want <= 0) decision = "skip: target reached";
        else decision = "cook";

        if ("cook".equals(decision) && currentTask != null) {
            if (currentTask.getType() != BotTask.Type.HUNT_FOOD) decision = "skip: busy";
            else if (pendingTask != null) decision = "skip: pending task";
        }

        String line = String.format("G2: cook check -> raw=%s fuel=%s furnace=%s hungry=%b decision=%s",
            rawIds, fuelIds, furnaceInfo, hungry, decision);
        if (!line.equals(lastCookLog)) {
            ChatUtils.infoPrefix("BotBrain", line);
            lastCookLog = line;
        }

        if (!"cook".equals(decision)) return;
        if (currentTask != null) {
            if (currentTask.getType() != BotTask.Type.HUNT_FOOD || pendingTask != null) return;
            BotTask hunt = currentTask;
            currentTask = null;
            executionPlan = null;
            stopHuntMovement();
            if (canSendBaritone()) sendBaritoneChat("#stop");
            try {
                baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            } catch (Exception | NoClassDefFoundError ignored) {}
            try { hunt.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(hunt); } catch (Exception ignored) {}
            ChatUtils.infoPrefix("BotBrain", "Food hunt complete: raw food ready to cook");
            setState(BrainState.IDLE);
        }
        lastCookAttempt = now;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("item", cooked);
        data.put("quantity", want);
        data.put("source", bestRaw);
        BotTask cook = new BotTask(java.util.UUID.randomUUID(), BotTask.Type.SMELT, 0, data);
        ChatUtils.infoPrefix("BotBrain", "Queuing cooking task: %s x%d", cook.getItemName(), want);
        setTask(cook);
    }

    private boolean isCookingTask() {
        return currentTask != null && currentTask.getType() == BotTask.Type.SMELT
            && currentTask.getSourceItem() != null && currentTask.getTargetItem() != null
            && currentTask.getTargetItem() == SmeltingManager.cookedResult(currentTask.getSourceItem());
    }

    private BlockPos findCookFurnace() {
        if (mc.player == null || mc.level == null) return null;
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -COOK_FURNACE_RANGE; x <= COOK_FURNACE_RANGE; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -COOK_FURNACE_RANGE; z <= COOK_FURNACE_RANGE; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    var block = mc.level.getBlockState(pos).getBlock();
                    if (block != Blocks.FURNACE && block != Blocks.SMOKER) continue;
                    double distance = playerPos.distSqr(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isCookFurnaceLit(BlockPos pos) {
        if (pos == null || mc.level == null) return false;
        try {
            return mc.level.getBlockState(pos)
                .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAnyRawFood() {
        for (Item raw : FoodManager.COOKING.keySet()) {
            if (FoodManager.countRaw(raw) > 0) return true;
        }
        return false;
    }

    private void tickFoodToggles() {
        boolean huntEnabled = autoHunt.get();
        if (huntEnabled != autoHuntWasEnabled) {
            autoHuntWasEnabled = huntEnabled;
            autoHuntSkipLogged = false;
            if (!huntEnabled) cancelHunt("auto-hunt off");
        }

        if (!autoCook.get()) {
            if (hasAnyRawFood()) {
                if (!autoCookSkipLogged) {
                    ChatUtils.infoPrefix("BotBrain", "G2: cook skipped (auto-cook off)");
                    autoCookSkipLogged = true;
                }
            } else {
                autoCookSkipLogged = false;
            }
        } else {
            autoCookSkipLogged = false;
        }
    }

    private boolean hasCookFuel(BlockPos furnace) {
        if (furnace != null && (isCookFurnaceLit(furnace) || SmeltingManager.hasFuelAt(furnace))) return true;
        if (SmeltingManager.isFurnaceOpen() && mc.player.containerMenu instanceof AbstractFurnaceMenu menu) {
            return menu.getLitProgress() > 0 || !menu.slots.get(1).getItem().isEmpty();
        }
        return false;
    }

    private String cookRawIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Item raw : FoodManager.COOKING.keySet()) {
            int count = FoodManager.countRaw(raw);
            if (count > 0) ids.add(itemPath(raw) + " x" + count);
        }
        ids.sort(String::compareTo);
        return ids.isEmpty() ? "none" : String.join(",", ids);
    }

    private String cookFuelIds(BlockPos furnace) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String inventoryFuel = SmeltingManager.inventoryFuelSummary();
        if (!"none".equals(inventoryFuel)) ids.add(inventoryFuel);
        if (furnace != null && isCookFurnaceLit(furnace)) ids.add("furnace:lit");
        boolean loggedFurnaceFuel = false;
        if (furnace != null) {
            var stored = SmeltingManager.fuelStackAt(furnace);
            if (!stored.isEmpty()) {
                ids.add("furnace:" + itemPath(stored.getItem()) + " x" + stored.getCount());
                loggedFurnaceFuel = true;
            }
        }
        if (!loggedFurnaceFuel && SmeltingManager.isFurnaceOpen() && mc.player.containerMenu instanceof AbstractFurnaceMenu menu) {
            var fuel = menu.slots.get(1).getItem();
            if (!fuel.isEmpty()) ids.add("furnace:" + itemPath(fuel.getItem()) + " x" + fuel.getCount());
        }
        return ids.isEmpty() ? "none" : String.join(",", ids);
    }

    private void beginBuild() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        String path = null;
        Object dataPath = currentTask.getData().get("file");
        if (dataPath instanceof String s && !s.isEmpty()) path = s;
        if (path == null || path.isEmpty()) path = buildFile.get();
        if (path == null || path.isEmpty()) { failBuild("no schematic file configured"); return; }
        java.io.File file = new java.io.File(path);
        if (!file.isFile()) { failBuild("schematic file not found: " + path); return; }
        try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess()
                .build("swarm-build", file, new net.minecraft.core.Vec3i(buildX.get(), buildY.get(), buildZ.get()));
            buildStartTime = System.currentTimeMillis();
            setState(BrainState.BUILDING);
            currentTaskDescription = "Building " + file.getName();
            ChatUtils.infoPrefix("BotBrain", "Builder started: %s", file.getName());
        } catch (Exception | NoClassDefFoundError e) {
            failBuild("builder unavailable: " + e.getMessage());
        }
    }

    private void handleBuilding() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        try {
            boolean active = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive();
            if (!active) { completeBuild(); return; }
        } catch (Exception | NoClassDefFoundError e) {
            failBuild("builder unavailable: " + e.getMessage());
            return;
        }
        if (System.currentTimeMillis() - buildStartTime > 1_800_000L) failBuild("builder timed out after 30 minutes");
    }

    private boolean tryAutoBuild() {
        if (swarmRole.get() != SwarmRole.GENERAL) return false;
        if (!autoBuild.get() || currentTask != null) return false;
        String path = buildFile.get();
        if (path == null || path.isEmpty() || !new java.io.File(path).isFile()) return false;
        long now = System.currentTimeMillis();
        if (now - lastAutoBuild < 60_000L) return false;
        lastAutoBuild = now;
        setTask(new BotTask(BotTask.Type.BUILD, null, 1));
        return true;
    }

    private void completeBuild() {
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        buildStartTime = 0;
        currentTaskDescription = "Idle — build complete";
        setState(BrainState.IDLE);
        if (task != null) {
            task.addCollected(task.getRemaining());
            try { task.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(task); } catch (Exception ignored) {}
        }
        ChatUtils.infoPrefix("BotBrain", "Builder complete");
    }

    private void failBuild(String reason) {
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        buildStartTime = 0;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        ChatUtils.warningPrefix("BotBrain", "Builder failed: %s", reason);
    }

    private void beginShulker() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        shulkerItem = findFilledShulker();
        if (shulkerItem == null) { failShulker("no filled shulker box in inventory"); return; }
        shulkerPos = null;
        shulkerPhase = SHULKER_PLACE;
        shulkerTicks = 0;
        setState(BrainState.SHULKER);
        currentTaskDescription = "Emptying " + itemPath(shulkerItem);
        ChatUtils.infoPrefix("BotBrain", "Shulker emptying started: %s", itemPath(shulkerItem));
    }

    private void handleShulker() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        shulkerTicks++;
        switch (shulkerPhase) {
            case SHULKER_PLACE -> {
                if (mc.player == null || mc.level == null) { setState(BrainState.STUCK); return; }
                if (shulkerPos != null && mc.level.getBlockState(shulkerPos).getBlock() != Blocks.AIR) {
                    shulkerPhase = SHULKER_OPEN; shulkerTicks = 0; return;
                }
                BlockPos placePos = findPlacementSpot();
                if (placePos == null) placePos = mc.player.blockPosition().above();
                if (!ensureItemInMainHand(shulkerItem)) {
                    if (shulkerTicks > 60) failShulker("could not hold shulker box");
                    return;
                }
                if (com.rdh.swarm.utils.PlacementHelper.placeBlockSafely(placePos, shulkerItem)) {
                    shulkerPos = placePos; shulkerTicks = 0;
                } else if (shulkerTicks > 100) failShulker("could not place shulker box");
            }
            case SHULKER_OPEN -> {
                if (mc.player.containerMenu instanceof net.minecraft.world.inventory.ShulkerBoxMenu) {
                    shulkerPhase = SHULKER_EMPTY; shulkerTicks = 0; return;
                }
                if (shulkerPos == null) { failShulker("shulker position lost"); return; }
                try {
                    mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(shulkerPos));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(shulkerPos), Direction.UP, shulkerPos, false));
                } catch (Exception ignored) {}
                if (shulkerTicks > 60) failShulker("could not open shulker box");
            }
            case SHULKER_EMPTY -> {
                if (!(mc.player.containerMenu instanceof net.minecraft.world.inventory.ShulkerBoxMenu menu)) {
                    shulkerPhase = SHULKER_OPEN; shulkerTicks = 0; return;
                }
                boolean moved = false;
                for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
                    if (!menu.slots.get(i).getItem().isEmpty()) {
                        InvUtils.shiftClick().slotId(i);
                        moved = true;
                        break;
                    }
                }
                if (!moved) {
                    mc.setScreenAndShow(null);
                    shulkerPhase = SHULKER_BREAK; shulkerTicks = 0;
                } else if (shulkerTicks > 400) failShulker("shulker empty timed out");
            }
            case SHULKER_BREAK -> {
                if (shulkerPos == null) { failShulker("shulker position lost"); return; }
                if (mc.level.getBlockState(shulkerPos).getBlock() == Blocks.AIR) {
                    shulkerPhase = SHULKER_COLLECT; shulkerTicks = 0; return;
                }
                if (mc.gameMode != null) mc.gameMode.destroyBlock(shulkerPos);
                if (shulkerTicks > 200) failShulker("could not break shulker box");
            }
            case SHULKER_COLLECT -> {
                ItemEntity drop = findHuntDrop();
                if (drop != null && canSendBaritone()) {
                    sendBaritoneChat(String.format("#goto %d %d %d",
                        drop.blockPosition().getX(), drop.blockPosition().getY(), drop.blockPosition().getZ()));
                }
                if (drop == null || shulkerTicks > 200) completeShulker();
            }
            default -> failShulker("unknown shulker phase");
        }
    }

    private Item findFilledShulker() {
        if (mc.player == null) return null;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!com.rdh.swarm.DepositConfig.isShulkerBox(stack)) continue;
            var contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (contents != null && contents.size() > 0) return stack.getItem();
        }
        return null;
    }

    private boolean tryAutoShulker() {
        if (swarmRole.get() != SwarmRole.GENERAL) return false;
        if (!autoEmptyShulkers.get() || currentTask != null) return false;
        if (findFilledShulker() == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastAutoShulker < 60_000L) return false;
        lastAutoShulker = now;
        setTask(new BotTask(BotTask.Type.SHULKER, null, 1));
        return true;
    }

    private void completeShulker() {
        BotTask task = currentTask;
        currentTask = null; executionPlan = null;
        shulkerPos = null; shulkerPhase = SHULKER_PLACE; shulkerTicks = 0;
        currentTaskDescription = "Idle — shulker emptied";
        setState(BrainState.IDLE);
        if (task != null) {
            task.addCollected(task.getRemaining());
            try { task.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(task); } catch (Exception ignored) {}
        }
        ChatUtils.infoPrefix("BotBrain", "Shulker emptied and collected");
    }

    private void failShulker(String reason) {
        try { if (mc.player != null) mc.setScreenAndShow(null); } catch (Exception ignored) {}
        BotTask task = currentTask;
        currentTask = null; executionPlan = null;
        shulkerPos = null; shulkerPhase = SHULKER_PLACE; shulkerTicks = 0;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        ChatUtils.warningPrefix("BotBrain", "Shulker failed: %s", reason);
    }

    private void beginSmithing() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        Item base = currentTask.getTargetItem();
        Item result = SmithingManager.upgradeResult(base);
        if (result == null) {
            failSmithing("not an upgradeable diamond item");
            return;
        }
        if (!hasSmithingMaterials(base)) {
            failSmithing("missing smithing template, netherite ingot, or base item");
            return;
        }
        smithPos = null;
        smithPhase = SMITH_SCAN;
        smithTicks = 0;
        lastSmithGoto = 0;
        setState(BrainState.SMITHING);
        currentTaskDescription = "Preparing to smith " + itemPath(result);
        ChatUtils.infoPrefix("BotBrain", "Smithing started: %s -> %s", itemPath(base), itemPath(result));
    }

    private void handleSmithing() {
        if (currentTask == null) { setState(BrainState.IDLE); return; }
        Item base = currentTask.getTargetItem();
        Item result = SmithingManager.upgradeResult(base);
        if (result == null) { failSmithing("not an upgradeable diamond item"); return; }
        smithTicks++;

        switch (smithPhase) {
            case SMITH_SCAN -> {
                if (mc.player == null || mc.level == null) { setState(BrainState.STUCK); return; }
                smithPos = findSmithingTable();
                if (smithPos == null) {
                    failSmithing("no smithing table within " + smithingRange.get() + " blocks");
                    return;
                }
                smithPhase = SMITH_GOTO;
                smithTicks = 0;
            }
            case SMITH_GOTO -> {
                if (smithPos == null) { smithPhase = SMITH_SCAN; smithTicks = 0; return; }
                double distSq = mc.player.blockPosition().distSqr(smithPos);
                if (distSq <= 20.0) {
                    if (canSendBaritone()) sendBaritoneChat("#stop");
                    smithPhase = SMITH_OPEN;
                    smithTicks = 0;
                    return;
                }
                currentTaskDescription = String.format("Walking to smithing table %d %d %d", smithPos.getX(), smithPos.getY(), smithPos.getZ());
                long now = System.currentTimeMillis();
                if (now - lastSmithGoto >= 3000L) {
                    lastSmithGoto = now;
                    if (canSendBaritone()) sendBaritoneChat(String.format("#goto %d %d %d", smithPos.getX(), smithPos.getY(), smithPos.getZ()));
                }
                if (smithTicks > 600) failSmithing("timed out walking to smithing table");
            }
            case SMITH_OPEN -> {
                if (SmithingManager.isSmithingOpen()) { smithPhase = SMITH_INSERT; smithTicks = 0; return; }
                if (smithPos == null) { smithPhase = SMITH_SCAN; smithTicks = 0; return; }
                SmithingManager.openSmithingTable(smithPos);
                if (smithTicks > 60) failSmithing("could not open smithing table");
            }
            case SMITH_INSERT -> {
                if (!SmithingManager.isSmithingOpen()) { smithPhase = SMITH_OPEN; smithTicks = 0; return; }
                boolean ok = SmithingManager.ensureTemplate()
                    && SmithingManager.ensureBase(base)
                    && SmithingManager.ensureIngot();
                if (ok) { smithPhase = SMITH_TAKE; smithTicks = 0; return; }
                if (smithTicks > 100) failSmithing("could not fill smithing slots");
            }
            case SMITH_TAKE -> {
                if (!SmithingManager.isSmithingOpen()) { smithPhase = SMITH_OPEN; smithTicks = 0; return; }
                if (SmithingManager.resultReady() && SmithingManager.takeResult()) {
                    smithPhase = SMITH_VERIFY;
                    smithTicks = 0;
                    return;
                }
                if (smithTicks > 100) failSmithing("smithing result never appeared");
            }
            case SMITH_VERIFY -> {
                if (CraftingManager.countInInventory(result) > 0) {
                    completeSmithing(result);
                    return;
                }
                if (smithTicks > 100) failSmithing("netherite result missing after take");
            }
            default -> failSmithing("unknown smithing phase");
        }
    }

    private BlockPos findSmithingTable() {
        if (mc.player == null || mc.level == null) return null;
        BlockPos origin = mc.player.blockPosition();
        int range = smithingRange.get();
        for (int x = -range; x <= range; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.SMITHING_TABLE) return pos;
                }
            }
        }
        return null;
    }

    private boolean hasSmithingMaterials(Item base) {
        return CraftingManager.countInInventory(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) > 0
            && CraftingManager.countInInventory(Items.NETHERITE_INGOT) > 0
            && CraftingManager.countInInventory(base) > 0;
    }

    private Item findUpgradeBase() {
        for (Item base : SmithingManager.UPGRADES.keySet()) {
            if (CraftingManager.countInInventory(base) > 0) return base;
        }
        return null;
    }

    private boolean tryAutoSmithing() {
        if (swarmRole.get() != SwarmRole.GENERAL) return false;
        if (!autoNetherite.get() || currentTask != null) return false;
        if (CraftingManager.countInInventory(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) <= 0) return false;
        if (CraftingManager.countInInventory(Items.NETHERITE_INGOT) <= 0) return false;
        Item base = findUpgradeBase();
        if (base == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastAutoSmith < 30_000L) return false;
        lastAutoSmith = now;
        ChatUtils.infoPrefix("BotBrain", "Auto-smithing %s", itemPath(base));
        setTask(new BotTask(BotTask.Type.SMITH, base, 1));
        return true;
    }

    private void completeSmithing(Item result) {
        SmithingManager.close();
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        smithPos = null;
        smithPhase = SMITH_SCAN;
        smithTicks = 0;
        currentTaskDescription = "Idle — smithed " + itemPath(result);
        setState(BrainState.IDLE);
        if (task != null) {
            task.addCollected(task.getRemaining());
            try { task.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(task); } catch (Exception ignored) {}
        }
        ChatUtils.infoPrefix("BotBrain", "Smithing complete: %s", itemPath(result));
    }

    private void failSmithing(String reason) {
        SmithingManager.close();
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        smithPos = null;
        smithPhase = SMITH_SCAN;
        smithTicks = 0;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        ChatUtils.warningPrefix("BotBrain", "Smithing failed: %s", reason);
    }

    private String itemPath(Item item) {
        try { return BuiltInRegistries.ITEM.getKey(item).getPath(); } catch (Exception e) { return String.valueOf(item); }
    }

    private void beginGrind() {
        grindPos = resolveGrindPos();
        if (grindPos == null) {
            failGrind("no grinder position configured");
            return;
        }
        grindStartTime = System.currentTimeMillis();
        lastGrindProgress = 0;
        lastGrindGoto = 0;
        grindAttacks = 0;
        setState(BrainState.GRINDING);
        currentTaskDescription = String.format("Grinding at %d %d %d", grindPos.getX(), grindPos.getY(), grindPos.getZ());
        ChatUtils.infoPrefix("BotBrain", "Grinder started at %d %d %d (range=%d, %ds)",
            grindPos.getX(), grindPos.getY(), grindPos.getZ(), grindRange.get(), grindDuration.get());
    }

    private BlockPos resolveGrindPos() {
        if (currentTask != null) {
            Object x = currentTask.getData().get("x");
            Object y = currentTask.getData().get("y");
            Object z = currentTask.getData().get("z");
            if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                return new BlockPos(nx.intValue(), ny.intValue(), nz.intValue());
            }
        }
        if (grindX.get() == 0 && grindY.get() == 0 && grindZ.get() == 0) return null;
        return new BlockPos(grindX.get(), grindY.get(), grindZ.get());
    }

    private void handleGrinding() {
        if (currentTask == null) {
            setState(BrainState.IDLE);
            return;
        }
        if (grindStartTime == 0 || grindPos == null) {
            beginGrind();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - grindStartTime >= grindDuration.get() * 1000L) {
            completeGrind("duration reached");
            return;
        }
        if (shouldDeposit()) {
            completeGrind("inventory full");
            return;
        }
        if (mc.player == null || mc.level == null) {
            setState(BrainState.STUCK);
            return;
        }

        if (mc.player.blockPosition().distSqr(grindPos) > 25.0) {
            currentTaskDescription = String.format("Walking to grinder %d %d %d", grindPos.getX(), grindPos.getY(), grindPos.getZ());
            if (now - lastGrindGoto >= 3000L) {
                lastGrindGoto = now;
                if (canSendBaritone()) sendBaritoneChat(String.format("#goto %d %d %d", grindPos.getX(), grindPos.getY(), grindPos.getZ()));
            }
            return;
        }

        if (grindCollect.get()) {
            ItemEntity drop = findGrindDrop();
            if (drop != null) {
                currentTaskDescription = "Collecting grinder drops";
                if (now - lastGrindGoto >= 3000L) {
                    lastGrindGoto = now;
                    if (canSendBaritone()) sendBaritoneChat(String.format("#goto %d %d %d",
                        drop.blockPosition().getX(), drop.blockPosition().getY(), drop.blockPosition().getZ()));
                }
                recordGrindProgress(now);
                return;
            }
        }

        Entity target = findGrindTarget();
        if (target == null) {
            currentTaskDescription = "Grinding — no mobs in range";
            recordGrindProgress(now);
            return;
        }

        currentTaskDescription = "Grinding " + target.getType().getDescription().getString();
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
        double distance = mc.player.distanceToSqr(target);
        if (distance <= 9.0 && mc.gameMode != null && mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            grindAttacks++;
        } else if (distance > 9.0 && now - lastGrindGoto >= 3000L) {
            lastGrindGoto = now;
            if (canSendBaritone()) sendBaritoneChat(String.format("#goto %d %d %d",
                target.blockPosition().getX(), target.blockPosition().getY(), target.blockPosition().getZ()));
        }
        recordGrindProgress(now);
    }

    private void recordGrindProgress(long now) {
        if (currentTask == null) return;
        long seconds = Math.max(0, (now - grindStartTime) / 1000L);
        int reported = (int) Math.min(Integer.MAX_VALUE, seconds);
        int delta = reported - currentTask.getCollectedQuantity();
        if (delta > 0) currentTask.addCollected(delta);
        if (now - lastGrindProgress >= 10_000L) {
            lastGrindProgress = now;
            try { sendTaskProgress(currentTask); } catch (Exception ignored) {}
            ChatUtils.infoPrefix("BotBrain", "Grinder: %ds, %d attacks", seconds, grindAttacks);
        }
    }

    private Entity findGrindTarget() {
        if (mc.player == null || mc.level == null) return null;
        AABB box = new AABB(mc.player.blockPosition()).inflate(grindRange.get());
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, this::isGrindTarget)) {
            double distance = entity.distanceToSqr(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private boolean isGrindTarget(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive()) return false;
        if (mob.getType().getCategory() != MobCategory.MONSTER) return false;
        if (mob instanceof TamableAnimal tamable && tamable.isTame()) return false;
        return !mob.hasCustomName();
    }

    private ItemEntity findGrindDrop() {
        if (mc.player == null || mc.level == null) return null;
        AABB box = new AABB(mc.player.blockPosition()).inflate(12.0);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof ItemEntity)) {
            if (!(entity instanceof ItemEntity drop) || drop.getItem().isEmpty()) continue;
            double distance = drop.distanceToSqr(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = drop;
            }
        }
        return best;
    }

    private void completeGrind(String reason) {
        BotTask task = currentTask;
        if (task != null && dropCollector.startFinalSweep(task.getId())) {
            currentTaskDescription = "Collecting final grinder drops";
            return;
        }
        int seconds = grindStartTime == 0 ? 0 : (int) ((System.currentTimeMillis() - grindStartTime) / 1000L);
        currentTask = null;
        executionPlan = null;
        grindPos = null;
        grindStartTime = 0;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            task.addCollected(task.getRemaining());
            try { task.setStatus(BotTask.Status.COMPLETE); } catch (Exception ignored) {}
            try { sendTaskComplete(task); } catch (Exception ignored) {}
        }
        ChatUtils.infoPrefix("BotBrain", "Grinder complete: %s (%ds, %d attacks)", reason, seconds, grindAttacks);
    }

    private void failGrind(String reason) {
        BotTask task = currentTask;
        currentTask = null;
        executionPlan = null;
        grindPos = null;
        grindStartTime = 0;
        currentTaskDescription = "Idle — " + reason;
        setState(BrainState.IDLE);
        if (task != null) {
            try { task.setStatus(BotTask.Status.FAILED); } catch (Exception ignored) {}
            try { sendTaskFailed(task, reason); } catch (Exception ignored) {}
        }
        ChatUtils.warningPrefix("BotBrain", "Grinder failed: %s", reason);
    }

    private boolean tryAutoGrind() {
        if (swarmRole.get() != SwarmRole.GENERAL && swarmRole.get() != SwarmRole.GRINDER) return false;
        if (!autoGrind.get()) return false;
        if (currentTask != null) return false;
        if (FoodManager.countFood() < 2) return false;
        if (grindX.get() == 0 && grindY.get() == 0 && grindZ.get() == 0) return false;
        long now = System.currentTimeMillis();
        if (now - lastAutoGrind < 60_000L) return false;
        lastAutoGrind = now;
        ChatUtils.infoPrefix("BotBrain", "Starting automatic grinder session");
        setTask(new BotTask(BotTask.Type.GRIND, null, grindDuration.get()));
        return true;
    }

    private void handleStuck() {
        currentTaskDescription = "Stuck — waiting";
        stuckCounter++;

        if (stuckCounter > 20) {
            ChatUtils.warningPrefix("BotBrain", "Been stuck too long. Resetting.");
            stuckCounter = 0;
            executionPlan = null;
            setState(BrainState.IDLE);
        }
    }

    // === TASK MANAGEMENT ===

    public void setTask(BotTask task) {
        if (halted) {
            ChatUtils.warningPrefix("BotBrain", "HALT: task assignment ignored while halted");
            return;
        }
        if (task != null && task.getTargetItem() != null) {
            if (task.getTargetItem() == Items.AIR) {
                task.setStatus(BotTask.Status.FAILED);
                task.setFailureReason("refusing to target air");
                try { sendTaskFailed(task, "refusing to target air"); } catch (Exception ignored) {}
                ChatUtils.warningPrefix("BotBrain", "TASK: %s failed — refusing to target air", task.getId());
                return;
            }
            if (!com.rdh.swarm.task.DependencyResolver.hasKnownSource(task.getTargetItem())) {
                task.setStatus(BotTask.Status.FAILED);
                task.setFailureReason("no known source");
                try { sendTaskFailed(task, "no known source"); } catch (Exception ignored) {}
                ChatUtils.warningPrefix("BotBrain", "TASK: %s failed — no known source", task.getId());
                return;
            }
        }
        // If we're currently depositing, queue the task locally and don't start processing yet
        try {
            var swarmMod = Modules.get().get(com.rdh.swarm.EnhancedSwarm.class);
            if ((this.state == BrainState.DEPOSITING) || (swarmMod != null && swarmMod.worker != null && swarmMod.worker.isDepositing)) {
                ChatUtils.infoPrefix("BotBrain", "Received task while depositing, queuing locally: %s x%d", task.getItemName(), task.getTargetQuantity());
                this.pendingTask = task;
                return;
            }
        } catch (Exception ignored) {}

        // If we're already processing an active task, ignore new assignment
        if (this.currentTask != null && this.currentTask.getStatus() == BotTask.Status.ACTIVE) {
            ChatUtils.warningPrefix("BotBrain", "Already processing task, ignoring new assignment");
            return;
        }

        this.currentTask = task;
        this.dropCollector.cancel();
        this.dropCollector.resetTaskTracking();
        this.lastHuntTargetId = null;
        this.lastHuntTargetEntity = null;
        this.executionPlan = null;
        this.directSmelt = false;
        this.planStep = 0;
        this.lastReportedCount = 0;
        this.basePos = null;
        this.baseReturnCommandSent = false;
        try { task.setStatus(BotTask.Status.ACTIVE); } catch (Exception ignored) {}

        if (task.getType() == BotTask.Type.HUNT_FOOD) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            beginHunt();
            return;
        }

        if (task.getType() == BotTask.Type.BUILD) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            setState(BrainState.IDLE);
            return;
        }

        if (task.getType() == BotTask.Type.SHULKER) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            setState(BrainState.IDLE);
            return;
        }

        if (task.getType() == BotTask.Type.SMITH) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            setState(BrainState.IDLE);
            return;
        }

        if (task.getType() == BotTask.Type.GRIND) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            beginGrind();
            return;
        }

        if (task.getType() == BotTask.Type.SMELT && task.getSourceItem() != null && task.getTargetItem() != null
            && task.getTargetItem() == SmeltingManager.cookedResult(task.getSourceItem())) {
            this.executionPlan = new ArrayList<>();
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
            this.directSmelt = true;
            setState(BrainState.IDLE);
            ChatUtils.infoPrefix("BotBrain", "Cooking task queued: %s x%d", task.getItemName(), task.getTargetQuantity());
            return;
        }

        // Resolve dependencies once at assignment and store results
        try {
            var plan = DependencyResolver.resolve(task.getTargetItem(), task.getTargetQuantity());
            this.executionPlan = plan.steps;
            this.resolverGatherList = plan.gatherList;
            this.resolverCraftPlan = plan.craftPlan;
            // detect if any craft step requires a 3x3 table
            boolean needs3 = false;
            if (this.resolverCraftPlan != null) {
                for (BotTask t : this.resolverCraftPlan) {
                    if (t.getTargetItem() != null && RecipeBook.requires3x3(t.getTargetItem())) { needs3 = true; break; }
                }
            }
            this.resolverNeeds3x3 = needs3;
            ChatUtils.infoPrefix("BotBrain", "Dependency plan for %s x%d:", task.getItemName(), task.getTargetQuantity());
            ChatUtils.infoPrefix("BotBrain", "  Gather: %s", resolverGatherList == null ? "{}" : resolverGatherList.toString());
            if (resolverCraftPlan != null) {
                for (int i = 0; i < resolverCraftPlan.size(); i++) {
                    BotTask s = resolverCraftPlan.get(i);
                    ChatUtils.infoPrefix("BotBrain", "  CraftPlan[%d]: %s x%d (3x3=%s)", i, s.getItemName(), s.getTargetQuantity(), RecipeBook.requires3x3(s.getTargetItem()));
                }
            } else {
                ChatUtils.infoPrefix("BotBrain", "  Craft steps: []");
            }
        } catch (Exception e) {
            ChatUtils.warningPrefix("BotBrain", "Failed to resolve dependencies: %s", e.getMessage());
            this.executionPlan = null;
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
        }

        setState(BrainState.IDLE);
        ChatUtils.infoPrefix("BotBrain", "New task: %s x%d",
            task.getItemName(), task.getTargetQuantity());
    }

    public BotTask getCurrentTask() { return currentTask; }
    public BrainState getState() { return state; }
    public String getTaskDescription() { return currentTaskDescription; }
    public String getEta() { return currentEta; }

    // === HELPERS ===

    private void setState(BrainState newState) {
        this.state = newState;
        if (newState != BrainState.HUNTING) moduleGuard.setKillAuraSuppressed(false);
        this.stateEnterTime = System.currentTimeMillis();
        // Reset misc state trackers when entering a new state
        // Only reset mining state when leaving GATHERING
        if (newState != BrainState.GATHERING) this.mineCommandSent = false;
        this.lastReportedCount = 0;
        this.lastProgressTime = System.currentTimeMillis();
        this.lastProgressCount = 0;
        this.craftingWaitStart = 0;
        this.craftFailCount = 0;
        if (newState != BrainState.GATHERING) {
            this.currentlyMining = null;
        }
        // Reset gather staging flags when entering states that begin fresh flows
        if (newState == BrainState.IDLE || newState == BrainState.GATHERING || newState == BrainState.RETURNING_TO_BASE || newState == BrainState.DEPOSITING || newState == BrainState.STUCK) {
            this.gatherStagingCommandSent = false;
            this.stagingTarget = null;
            this.stagingComplete = false;
            this.stagingAttempts = 0;
        }
        // Reset tick-driven crafting state
        this.craftingStep = 0;
        this.craftGridSlotIndex = 0;
        this.craftActions = null;
        // Clear any prep-craft target when leaving prep state
        if (newState != BrainState.CRAFTING_PREP) prepCraftTarget = null;
        if (newState != BrainState.CRAFTING_PREP) craftPrepReturnPlanStep = 0;
        // reset crafting prep state when entering CRAFTING_PREP
        if (newState == BrainState.CRAFTING_PREP) {
            craftPrepPhase = PREP_SCAN;
            craftingTablePos = null;
            craftPrepTicks = 0;
            waitingForBaritone = false;
        }
        // reset smelting state when entering/exiting smelting
        if (newState == BrainState.SMELTING_DEPOSIT) {
            smeltPhase = SMELT_SCAN;
            smelterPos = null;
            smeltTicks = 0;
            smeltWaitStart = 0;
            lastSmeltCheckTime = 0;
        }
        if (newState != BrainState.SMELTING_DEPOSIT && newState != BrainState.SMELTING_WAIT && newState != BrainState.SMELTING_COLLECT) {
            smeltPhase = 0;
            smelterPos = null;
            smeltTicks = 0;
            smeltWaitStart = 0;
        }
        if (newState == BrainState.IDLE) {
            basePos = null;
            baseReturnCommandSent = false;
            surfaceTarget = null;
            surfaceCommandSent = false;
            // Clear gather resource tracking when fully idle
            this.currentGatherResource = null;
            // Clear nether transit state
            this.inNetherTransit = false;
            this.awaitingDimensionChange = false;
            this.expectedDimension = null;
            this.portalApproachPos = null;
            this.portalApproachSent = false;
            this.portalEntrySent = false;
            this.dimensionWaitStartTime = 0;
            // Clear dedicated nether-return flags
            this.netherReturnGotoSent = false;
            this.netherReturnPortalSent = false;
            this.netherReturnStartTime = 0;
        }
        if (newState == BrainState.GATHERING) {
            // Track the current gather resource string for waypoint lookups
            try {
                if (currentTask != null && currentTask.getType() == BotTask.Type.GATHER) {
                    String tgtId = BuiltInRegistries.ITEM.getKey(currentTask.getTargetItem()).toString();
                    String clean = tgtId.startsWith("minecraft:") ? tgtId.substring(10) : tgtId;
                    this.currentGatherResource = MINE_FOR.getOrDefault(clean, MINE_FOR_2.getOrDefault(clean, clean));
                }
            } catch (Exception ignored) {}
        }
        if (newState == BrainState.IDLE || newState == BrainState.CRAFTING || newState == BrainState.SMELTING_DEPOSIT || newState == BrainState.STUCK) {
            pendingPlacementPos = null;
            pendingPlacementItem = null;
            pendingPlacementRetries = 0;
        }
        if (newState != BrainState.STUCK) stuckCounter = 0;
        if (newState == BrainState.STUCK) {
            // clear nether transit state on stuck
            this.inNetherTransit = false;
            this.awaitingDimensionChange = false;
            this.expectedDimension = null;
            this.portalApproachPos = null;
            this.portalApproachSent = false;
            this.portalEntrySent = false;
            this.dimensionWaitStartTime = 0;
            // Clear dedicated nether-return flags
            this.netherReturnGotoSent = false;
            this.netherReturnPortalSent = false;
            this.netherReturnStartTime = 0;
            try { sendTaskFailed(currentTask, "stuck"); } catch (Exception ignored) {}
        }
    }

    private BotTask getCurrentStep() {
        if (directSmelt && currentTask != null) return currentTask;
        if (executionPlan == null || planStep >= executionPlan.size()) return null;
        return executionPlan.get(planStep);
    }

    private void advancePlan() {
        if (directSmelt) {
            directSmelt = false;
            executionPlan = null;
            planStep = 0;
            return;
        }
        planStep++;
        if (planStep >= executionPlan.size()) {
            executionPlan = null;
            planStep = 0;
        }
    }

    private boolean shouldDeposit() {
        if (mc.player == null) return false;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) empty++;
        }
        return empty <= depositThreshold.get();
    }

    /** A sweep may only take over when no break and no Baritone path are active. */
    private boolean canStartDropSweep() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.gameMode != null && mc.gameMode.isDestroying()) return false;
        try {
            if (baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getPathingBehavior().isPathing()) return false;
        } catch (Throwable ignored) {}
        if (state == BrainState.GATHERING) {
            Vec3 movement = mc.player.getDeltaMovement();
            if (movement.x * movement.x + movement.z * movement.z > 0.0025D) return false;
        }
        return true;
    }

    private void onSweepFinished() {
        currentlyMining = null;
        mineCommandSent = false;
    }

    private boolean canSendBaritone() {
        return System.currentTimeMillis() - lastBaritoneCommand > BARITONE_COMMAND_COOLDOWN;
    }

    private void sendBaritoneChat(String command) {
        lastBaritoneCommand = System.currentTimeMillis();
        if (mc.getConnection() != null) {
            mc.getConnection().sendChat(command);
        }
    }

    private WaypointManager getWaypointManager() {
        // Prefer worker-side pushed manager when available
        try {
            var mods = meteordevelopment.meteorclient.systems.modules.Modules.get();
            var mod = mods.get(com.rdh.swarm.EnhancedSwarm.class);
            if (mod != null) {
                try {
                    com.rdh.swarm.EnhancedSwarm esm = (com.rdh.swarm.EnhancedSwarm) mod;
                    if (esm.worker != null) {
                        WaypointManager wm = esm.worker.getOrCreateWaypointManager();
                        if (wm != null) {
                            waypointManager = wm;
                            waypointManager.ensureLoaded();
                            return waypointManager;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (waypointManager == null) waypointManager = new WaypointManager();
        waypointManager.ensureLoaded();
        return waypointManager;
    }

    private boolean isInNether() {
        return mc.level != null && mc.level.dimension() == Level.NETHER;
    }

    /**
     * Check if current gather resource requires nether dimension
     */
    private boolean currentTaskRequiresNether() {
        if (currentGatherResource == null) return false;
        String category = WaypointManager.getCategoryForResource(currentGatherResource);
        return WaypointManager.isNetherCategory(category);
    }

    /**
     * Get the portal waypoint for transit.
     * Returns first portal waypoint, or null if none configured.
     */
    private SwarmWaypoint getPortalWaypoint() {
        return getWaypointManager().getFirstPortal();
    }

    /**
     * Calculate approach position near portal (PORTAL_APPROACH_DISTANCE blocks away)
     * Uses the portal coords for the current dimension
     */
    private BlockPos calculatePortalApproach(SwarmWaypoint portal) {
        boolean nether = isInNether();
        int[] coords = portal.getCoords(nether);
        if (coords == null) return null;
        
        // If we have player position, approach from player's direction
        if (mc.player != null) {
            double dx = mc.player.getX() - coords[0];
            double dz = mc.player.getZ() - coords[2];
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist > 1) {
                // Normalize and scale to approach distance
                double scale = PORTAL_APPROACH_DISTANCE / dist;
                int approachX = coords[0] + (int)(dx * scale);
                int approachZ = coords[2] + (int)(dz * scale);
                return new BlockPos(approachX, coords[1], approachZ);
            }
        }
        
        // Fallback: offset in X direction
        return new BlockPos(coords[0] + PORTAL_APPROACH_DISTANCE, coords[1], coords[2]);
    }

    /**
     * Check if dimension has changed to the expected one.
     * Returns true if we're now in the expected dimension.
     */
    private boolean hasReachedExpectedDimension() {
        if (expectedDimension == null) return false;
        if ("nether".equals(expectedDimension)) return isInNether();
        if ("overworld".equals(expectedDimension)) return !isInNether();
        return false;
    }

    /**
     * Check if dimension wait has timed out
     */
    private boolean hasDimensionWaitTimedOut() {
        return System.currentTimeMillis() - dimensionWaitStartTime > DIMENSION_WAIT_TIMEOUT_MS;
    }

    /**
     * Handle portal transit logic. Called from tick when awaitingDimensionChange is true.
     * Returns true if transit is complete (dimension changed successfully).
     * Returns false if still waiting.
     * Sets state to STUCK if timed out.
     */
    private boolean handleDimensionWait() {
        if (!awaitingDimensionChange) return false;
        
        if (hasReachedExpectedDimension()) {
            ChatUtils.infoPrefix("BotBrain", "Dimension change detected — now in %s", expectedDimension);
            
            // Clear transit flags
            awaitingDimensionChange = false;
            portalApproachPos = null;
            portalApproachSent = false;
            portalEntrySent = false;
            expectedDimension = null;
            dimensionWaitStartTime = 0;
            
            // Stop any lingering Baritone path from previous dimension
            if (canSendBaritone()) {
                sendBaritoneChat("#stop");
            }
            
            return true;
        }
        
        if (hasDimensionWaitTimedOut()) {
            ChatUtils.errorPrefix("BotBrain", "Portal transit timed out after %d seconds", DIMENSION_WAIT_TIMEOUT_MS / 1000);
            awaitingDimensionChange = false;
            portalApproachPos = null;
            portalApproachSent = false;
            portalEntrySent = false;
            expectedDimension = null;
            dimensionWaitStartTime = 0;
            setState(BrainState.STUCK);
            return false;
        }
        
        return false;
    }

    /**
     * Initiate approach to portal and entry.
     * Call this each tick until it returns true (transit complete).
     * 
     * Flow:
     * 1. #goto approach position (10 blocks from portal)
     * 2. Wait until close enough
     * 3. #goto nether_portal (Baritone walks into portal)
     * 4. Set awaitingDimensionChange, wait for dimension swap
     * 
     * @param portal the portal waypoint
     * @param targetDim "nether" or "overworld"
     * @return true when transit is complete
     */
    private boolean navigatePortal(SwarmWaypoint portal, String targetDim) {
        if (mc.player == null) return false;
        
        // Phase 1: Calculate and goto approach position
        if (portalApproachPos == null) {
            portalApproachPos = calculatePortalApproach(portal);
            if (portalApproachPos == null) {
                ChatUtils.errorPrefix("BotBrain", "Portal '%s' has no coords for %s", portal.name, isInNether() ? "nether" : "overworld");
                setState(BrainState.STUCK);
                return false;
            }
            ChatUtils.infoPrefix("BotBrain", "Approaching portal '%s' at %d %d %d", 
                portal.name, portalApproachPos.getX(), portalApproachPos.getY(), portalApproachPos.getZ());
        }
        
        // Phase 2: Send approach command
        if (!portalApproachSent && canSendBaritone()) {
            sendBaritoneChat(String.format("#goto %d %d %d", 
                portalApproachPos.getX(), portalApproachPos.getY(), portalApproachPos.getZ()));
            portalApproachSent = true;
            return false;
        }
        
        // Phase 3: Check if close enough to portal for entry command
        if (portalApproachSent && !portalEntrySent) {
            boolean nether = isInNether();
            int[] portalCoords = portal.getCoords(nether);
            if (portalCoords == null) {
                ChatUtils.errorPrefix("BotBrain", "Portal '%s' missing %s coords", portal.name, nether ? "nether" : "overworld");
                setState(BrainState.STUCK);
                return false;
            }
            
            double dx = mc.player.getX() - portalCoords[0];
            double dy = mc.player.getY() - portalCoords[1];
            double dz = mc.player.getZ() - portalCoords[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            if (dist < PORTAL_APPROACH_DISTANCE + 5) {
                // Close enough, send portal entry command
                if (canSendBaritone()) {
                    ChatUtils.infoPrefix("BotBrain", "Entering portal — #goto nether_portal");
                    sendBaritoneChat("#goto nether_portal");
                    portalEntrySent = true;
                    awaitingDimensionChange = true;
                    expectedDimension = targetDim;
                    dimensionWaitStartTime = System.currentTimeMillis();
                }
                return false;
            }
            
            return false;
        }
        
        // Phase 4: Waiting for dimension change
        if (awaitingDimensionChange) {
            return handleDimensionWait();
        }
        
        return false;
    }

    private void trackItemCollection(Item target) {
        long now = System.currentTimeMillis();
        if (now - lastItemCheckTime > 10000) {
            int currentCount = CraftingManager.countInInventory(target);
            itemsCollectedSinceLastCheck = currentCount - lastItemCount;
            lastItemCount = currentCount;
            lastItemCheckTime = now;
        }
    }

    private void updateEta() {
        if (currentTask == null || currentTask.isComplete()) {
            currentEta = "";
            return;
        }

        if (state == BrainState.GATHERING && itemsCollectedSinceLastCheck > 0) {
            double itemsPerSecond = itemsCollectedSinceLastCheck / 10.0;
            if (itemsPerSecond > 0) {
                int remaining = currentTask.getRemaining();
                long secondsLeft = (long) (remaining / itemsPerSecond);
                if (secondsLeft < 60) currentEta = secondsLeft + "s";
                else if (secondsLeft < 3600) currentEta = (secondsLeft / 60) + "m";
                else currentEta = (secondsLeft / 3600) + "h " + ((secondsLeft % 3600) / 60) + "m";
                return;
            }
        }

        // Fallback to distance based
        // TODO: guard separate Baritone mod 26.3
        try {
            if (baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                currentEta = "Pathing";
            } else {
                currentEta = "Waiting";
            }
        } catch (Exception | NoClassDefFoundError e) {
            currentEta = "Unknown";
        }
    }

    private boolean ensureStagedAwayFromBase() {
        if (!gatherStaging.get() || stagingComplete || mc.player == null) return true;

        BlockPos base = getBasePosition();
        if (base == null) { stagingComplete = true; return true; }

        // If we already have a staging target, continue with existing logic
        if (stagingTarget != null) {
            return continueToStagingTarget(base);
        }

        // Try to find a waypoint for the current resource
        SwarmWaypoint waypoint = null;
        if (currentGatherResource != null) {
            String category = WaypointManager.getCategoryForResource(currentGatherResource);

            // Nether resource while in overworld — need portal transit
            if (WaypointManager.isNetherCategory(category) && !isInNether()) {
                SwarmWaypoint portal = getPortalWaypoint();
                if (portal == null) {
                    ChatUtils.errorPrefix("BotBrain", "No portal waypoint configured! Use: .eswarm waypoint portal <name>");
                    ChatUtils.errorPrefix("BotBrain", "Run command at overworld portal, go through, run again at nether side");
                    setState(BrainState.STUCK);
                    return false;
                }

                if (!portal.hasOverworldCoords()) {
                    ChatUtils.errorPrefix("BotBrain", "Portal '%s' missing overworld coords — stand at overworld portal and run: .eswarm waypoint portal %s", portal.name, portal.name);
                    setState(BrainState.STUCK);
                    return false;
                }

                if (!portal.hasNetherCoords()) {
                    ChatUtils.errorPrefix("BotBrain", "Portal '%s' missing nether coords — go through portal and run: .eswarm waypoint portal %s", portal.name, portal.name);
                    setState(BrainState.STUCK);
                    return false;
                }

                // Mark that we're doing nether transit
                inNetherTransit = true;

                // Navigate to portal and cross
                boolean transitComplete = navigatePortal(portal, "nether");
                if (!transitComplete) {
                    // Still in transit — return false to keep ticking
                    return false;
                }

                // We're now in the nether! Continue to staging logic below
                // Reset approach flags so staging can use them fresh
                ChatUtils.infoPrefix("BotBrain", "In nether — looking for staging waypoint");
            }

            // If we need nether and we're already in nether, find nether staging waypoint
            if (WaypointManager.isNetherCategory(category) && isInNether()) {
                waypoint = getWaypointManager().findWaypointForResource(currentGatherResource, true);
            } else {
                waypoint = getWaypointManager().findWaypointForResource(currentGatherResource, isInNether());
            }
        }

        ChatUtils.infoPrefix("BotBrain", "STAGING: waypoint for '%s' -> %s",
            currentGatherResource != null ? currentGatherResource : "unknown", waypoint == null ? "none" : "found");

        if (waypoint != null) {
            int[] coords = waypoint.getCoords(isInNether());
            if (coords != null) {
                stagingTarget = new BlockPos(coords[0], coords[1], coords[2]);
                ChatUtils.infoPrefix("BotBrain", "Staging to waypoint '%s' [%s]: %d %d %d", 
                    waypoint.name, waypoint.category, coords[0], coords[1], coords[2]);
            }
        }

        // Fallback to random staging if no waypoint found
        if (stagingTarget == null) {
            // Determine staging origin: nether portal if in nether, base if in overworld
            BlockPos stagingOrigin;
            if (isInNether() && inNetherTransit) {
                SwarmWaypoint portal = getPortalWaypoint();
                if (portal != null && portal.hasNetherCoords()) {
                    int[] nc = portal.getCoords(true);
                    stagingOrigin = new BlockPos(nc[0], nc[1], nc[2]);
                } else {
                    // Use current position as fallback
                    stagingOrigin = mc.player.blockPosition();
                }
            } else {
                stagingOrigin = base;
            }
            
            double distToOrigin = Math.sqrt(mc.player.blockPosition().distSqr(stagingOrigin));
            if (distToOrigin >= gatherStagingDistance.get()) { 
                stagingComplete = true; 
                return true; 
            }
            
            if (stagingAttempts >= 2) {
                ChatUtils.warningPrefix("BotBrain", "STAGING: giving up after 2 attempts; mining locally");
                stagingComplete = true;
                return true;
            }
            stagingAttempts++;
            BlockPos safe = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                double angle = Math.random() * 2 * Math.PI;
                int distance = 100 + (int)(Math.random() * 200);
                int targetX = stagingOrigin.getX() + (int)(Math.cos(angle) * distance);
                int targetZ = stagingOrigin.getZ() + (int)(Math.sin(angle) * distance);
                int targetY = stagingOrigin.getY();
                BlockPos candidate = new BlockPos(targetX, targetY, targetZ);
                boolean floor = false;
                boolean reachable = false;
                try {
                    floor = mc.level != null && !mc.level.getBlockState(candidate.below()).isAir()
                        && mc.level.getBlockState(candidate.below()).getFluidState().isEmpty();
                    reachable = mc.level != null && mc.level.getBlockState(candidate).isAir()
                        && mc.level.getBlockState(candidate).getFluidState().isEmpty()
                        && mc.level.getBlockState(candidate.above()).isAir();
                } catch (Exception ignored) {}
                ChatUtils.infoPrefix("BotBrain", "STAGING: waypoint for '%s' -> none; random point %d,%d,%d floor=%b reachable=%b",
                    currentGatherResource != null ? currentGatherResource : "unknown", targetX, targetY, targetZ, floor, reachable);
                if (floor && reachable) { safe = candidate; break; }
                ChatUtils.warningPrefix("BotBrain", "STAGING: rejected point %d,%d,%d (floor=%b reachable=%b)", targetX, targetY, targetZ, floor, reachable);
            }
            if (safe == null) {
                ChatUtils.warningPrefix("BotBrain", "STAGING: giving up after 2 attempts; mining locally");
                stagingComplete = true;
                return true;
            }
            stagingTarget = safe;
            ChatUtils.infoPrefix("BotBrain", "No waypoint for '%s', random staging from %s: goto %d %d %d",
                currentGatherResource != null ? currentGatherResource : "unknown",
                isInNether() ? "nether portal" : "base", safe.getX(), safe.getY(), safe.getZ());
        }

        return continueToStagingTarget(base);
    }

    private boolean continueToStagingTarget(BlockPos base) {
        if (stagingTarget == null) {
            stagingComplete = true;
            return true;
        }

        if (!gatherStagingCommandSent && canSendBaritone()) {
            sendBaritoneChat(String.format("#goto %d %d %d", stagingTarget.getX(), stagingTarget.getY(), stagingTarget.getZ()));
            gatherStagingCommandSent = true;
        }

        double dx = mc.player.getX() - stagingTarget.getX();
        double dy = mc.player.getY() - stagingTarget.getY();
        double dz = mc.player.getZ() - stagingTarget.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (dist < 20) {
            ChatUtils.infoPrefix("BotBrain", "Staging complete, beginning gather");
            stagingComplete = true;
            return true;
        }

        return false;
    }

    // === TASK MESSAGING ===

    private String getWorkerName() {
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getUser() != null)
                return Minecraft.getInstance().getUser().getName();
        } catch (Exception ignored) {}
        return "unknown";
    }

    private void sendTaskProgress(BotTask task) {
        if (task == null || out == null) return;
        try {
            String worker = getWorkerName();
            String msg = String.format("TASK_PROGRESS:%s:%s:%d:%d:%s:%s", worker, task.getId().toString(), task.getCollectedQuantity(), task.getTargetQuantity(), task.getStatus() == null ? "" : task.getStatus().name(), task.getElapsedString());
            synchronized (out) {
                out.writeUTF(msg);
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    private void sendTaskComplete(BotTask task) {
        if (task == null || out == null) return;
        try {
            String worker = getWorkerName();
            String msg = String.format("TASK_COMPLETE:%s:%s:%s:%d:%s", worker, task.getId().toString(), task.getItemName(), task.getTargetQuantity(), task.getElapsedString());
            synchronized (out) {
                out.writeUTF(msg);
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    private void sendTaskFailed(BotTask task, String reason) {
        if (task == null || out == null) return;
        try {
            String worker = getWorkerName();
            String msg = String.format("TASK_FAILED:%s:%s:%s", worker, task.getId().toString(), reason == null ? "" : reason);
            synchronized (out) {
                out.writeUTF(msg);
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    private void sendTaskCancelled(BotTask task, String reason) {
        if (task == null || out == null) return;
        try {
            String worker = getWorkerName();
            String msg = String.format("TASK_CANCELLED:%s:%s:%s", worker, task.getId().toString(), reason == null ? "" : reason);
            synchronized (out) {
                out.writeUTF(msg);
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    private void resolveDependenciesForCurrentTask() {
        if (currentTask == null) return;
        try {
            int remaining = currentTask.getRemaining();
            var plan = DependencyResolver.resolve(currentTask.getTargetItem(), remaining);
            this.executionPlan = plan.steps;
            this.resolverGatherList = plan.gatherList;
            this.resolverCraftPlan = plan.craftPlan;
            boolean needs3 = false;
            if (this.resolverCraftPlan != null) {
                for (BotTask t : this.resolverCraftPlan) {
                    if (t.getTargetItem() != null && RecipeBook.requires3x3(t.getTargetItem())) { needs3 = true; break; }
                }
            }
            this.resolverNeeds3x3 = needs3;
            ChatUtils.infoPrefix("BotBrain", "Re-resolved dependency plan (remaining=%d)", remaining);
        } catch (Exception e) {
            ChatUtils.warningPrefix("BotBrain", "Failed to resolve dependencies: %s", e.getMessage());
            this.executionPlan = null;
            this.resolverGatherList = null;
            this.resolverCraftPlan = null;
        }
    }

    // === PAUSE/CANCEL/RESUME ===

    public void cancel() {
        // Set the latch first: the next tick must not re-plan between the
        // Baritone cancel and the task cleanup.
        halted = true;
        paused = false;
        long now = System.currentTimeMillis();
        lastHaltLog = now;
        ChatUtils.infoPrefix("BotBrain", "HALT: requested by command");

        BotTask abandoned = this.currentTask;
        if (abandoned != null) {
            try { abandoned.setStatus(BotTask.Status.CANCELLED); } catch (Exception ignored) {}
            try { sendTaskCancelled(abandoned, "cancelled by user"); } catch (Exception ignored) {}
            ChatUtils.infoPrefix("BotBrain", "HALT: bot idle, task '%s' abandoned", abandoned.getId());
        }
        this.currentTask = null;
        this.executionPlan = null;
        this.planStep = 0;
        this.directSmelt = false;
        this.resolverGatherList = null;
        this.resolverCraftPlan = null;
        this.pendingWithdrawals = null;
        this.withdrawInProgress = false;

        try {
            var bar = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
            bar.getPathingBehavior().cancelEverything();
            bar.getCustomGoalProcess().onLostControl();
        } catch (Throwable ignored) {}
        releaseMovementKeys();
        dropCollector.cancel();
        stopHuntMovement();
        moduleGuard.setKillAuraSuppressed(false);
        try { SmithingManager.close(); } catch (Exception ignored) {}
        try {
            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.execute(() -> { try { mc.setScreenAndShow(null); } catch (Exception ignored) {} });
            }
        } catch (Exception ignored) {}
        setState(BrainState.IDLE);
    }

    private void releaseMovementKeys() {
        try {
            var options = mc.options;
            if (options == null) return;
            options.keyUp.setDown(false);
            options.keyDown.setDown(false);
            options.keyLeft.setDown(false);
            options.keyRight.setDown(false);
            options.keyJump.setDown(false);
            options.keyShift.setDown(false);
            options.keySprint.setDown(false);
        } catch (Exception ignored) {}
    }

    public boolean isHalted() { return halted; }

    public void pause() {
        paused = true;
        sendBaritoneChat("#stop");
    }

    public void resume() {
        halted = false;
        paused = false;
        lastHaltLog = 0L;
        ChatUtils.infoPrefix("BotBrain", "HALT: resumed by command");
        setState(BrainState.IDLE);
    }

    @Override
    public String getInfoString() {
        if (currentTask == null) return "Idle";
        return String.format("%s %s | %s | %s",
            currentTaskDescription,
            currentTask.getProgressString(),
            currentEta == null || currentEta.isEmpty() ? "Waiting" : currentEta,
            com.rdh.swarm.task.DependencyResolver.getCurrentTier().name());
    }
}
