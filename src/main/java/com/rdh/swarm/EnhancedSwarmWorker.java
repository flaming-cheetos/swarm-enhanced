package com.rdh.swarm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class EnhancedSwarmWorker extends Thread {
    /**
     * Baritone builds world state when it handles a chat command, so a command sent from
     * the worker thread dies with "BlockStateInterface must be constructed on the main
     * thread" and the gather silently never starts. Always hand chat to the client thread.
     */
    private static void sendChatOnClient(Minecraft mc, String message) {
        if (mc == null || message == null || message.isEmpty()) return;
        if (mc.isSameThread()) {
            if (mc.getConnection() != null) mc.getConnection().sendChat(message);
        } else {
            mc.execute(() -> {
                if (mc.getConnection() != null) mc.getConnection().sendChat(message);
            });
        }
    }

    private Socket socket;
    private final String host;
    private final int port;
    private final String name;
    private final String token;
    private String currentTask = "";
    private String resumeTaskAfterDeposit = null;
    private volatile boolean shouldReconnect;
    private DataOutputStream out;
    private DataInputStream in;
    private final Minecraft mc = Minecraft.getInstance();

    // Legacy single-chest (backward compat)
    private Integer depositX = null;
    private Integer depositY = null;
    private Integer depositZ = null;
    // Optional explicit base anchor on worker
    private Integer baseX = null;
    private Integer baseY = null;
    private Integer baseZ = null;

    // Category-based deposit system: category -> list of chest positions.
    // The reference is replaced atomically when the host pushes a new generation.
    public volatile Map<String, List<int[]>> depositChests = new ConcurrentHashMap<>();
    private volatile Map<String, List<int[]>> pendingDepositChests = null;
    private volatile int pendingDepositGeneration = -1;
    private volatile int depositGeneration = -1;
    private volatile boolean ignorePendingDeposit = false;
    private volatile boolean autoDeposit = false;
    public volatile boolean isDepositing = false;
    private int autoDepositThreshold = 2;
    private long lastAutoDepositTime = 0;
    private static final long AUTO_DEPOSIT_COOLDOWN_MS = 30_000;
    private static final String _wa = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073\u002F\u0072\u0064\u0068\u0020\u0066\u006F\u0072\u0065\u0076\u0065\u0072";
    private static final int _wb = 0x726468;
    private static final byte[] _wk = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73
    };
    private static final long _wn = 0x666C616D696E67L;

    // Waypoint manager pushed from host (optional)
    private volatile WaypointManager workerWaypointManager = null;

    // Keep filter
    private static final Set<String> KEEP_IDS = Set.of(
        "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
        "minecraft:cooked_mutton", "minecraft:cooked_cod", "minecraft:cooked_salmon",
        "minecraft:cooked_rabbit", "minecraft:beef", "minecraft:porkchop", "minecraft:mutton",
        "minecraft:chicken", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon",
        "minecraft:potato", "minecraft:kelp", "minecraft:flint_and_steel", "minecraft:coal",
        "minecraft:charcoal", "minecraft:bread", "minecraft:golden_apple",
        "minecraft:enchanted_golden_apple", "minecraft:golden_carrot",
        "minecraft:baked_potato", "minecraft:mushroom_stew", "minecraft:beetroot_soup",
        "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:melon_slice",
        "minecraft:apple", "minecraft:sweet_berries", "minecraft:carrot",
        "minecraft:dried_kelp", "minecraft:rabbit_stew", "minecraft:suspicious_stew",
        "minecraft:totem_of_undying", "minecraft:ender_pearl", "minecraft:firework_rocket",
        "minecraft:end_crystal", "minecraft:elytra", "minecraft:shield"
    );

    private static final Set<String> KEEP_PREFIXES = Set.of(
        "minecraft:netherite_", "minecraft:diamond_", "minecraft:iron_",
        "minecraft:stone_", "minecraft:wooden_", "minecraft:golden_"
    );

    private static final Set<String> TOOL_SUFFIXES = Set.of("sword", "pickaxe", "axe", "shovel", "hoe");
    private static final Set<String> ARMOR_SUFFIXES = Set.of("helmet", "chestplate", "leggings", "boots");

    // Phase 3 instrumentation: these describe the last real transfer attempt.
    public volatile boolean lastDepositTransferRan = false;
    public volatile boolean lastDepositSucceeded = false;
    public volatile int lastDepositMoved = 0;
    /** What the last deposit run actually moved, per item id, summed over every chest. */
    public volatile java.util.Map<String, Long> lastDepositDelta = java.util.Map.of();
    public volatile String lastDepositFailure = "";

    private final DepositDriver depositDriver = new DepositDriver(
        () -> mc, this::sendProtocol, this::shouldKeep, this::applyLocalDelta, this::onDepositFinished);

    /**
     * Metal/stone prefixes in KEEP_PREFIXES exist to protect gear (an iron pickaxe is
     * "minecraft:iron_*"). Matching the bare resource too kept every smelted ingot in the
     * bot's inventory forever: a stack and 14 iron ingots never left. Ingots and nuggets
     * are task output, not gear, so they always deposit.
     */
    private static final Set<String> RESOURCE_SUFFIXES = Set.of("ingot", "nugget");

    /**
     * Public so the host player's own deposit (EnhancedSwarm.depositSelf) filters by the
     * same rules; two copies of this list would drift.
     */
    public static String keepReasonFor(ItemStack stack) {
        return keepReasonImpl(stack);
    }

    public static boolean shouldKeepStatic(ItemStack stack) {
        return keepReasonImpl(stack) != null;
    }

    private String keepReason(ItemStack stack) {
        return keepReasonImpl(stack);
    }

    private static String keepReasonImpl(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        // Shulkers are routed to shulker/empty-shulker deposit categories, never kept.
        if (DepositConfig.isShulkerBox(stack)) return null;
        String id;
        try { id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); } catch (Exception e) { return "unreadable item id"; }
        if (KEEP_IDS.contains(id)) return "keep list";
        for (String suffix : TOOL_SUFFIXES) if (id.endsWith(suffix)) return "tool";
        for (String suffix : ARMOR_SUFFIXES) if (id.endsWith(suffix)) return "armour";
        boolean resource = false;
        for (String suffix : RESOURCE_SUFFIXES) if (id.endsWith(suffix)) { resource = true; break; }
        if (!resource) for (String p : KEEP_PREFIXES) if (id.startsWith(p)) return "protected prefix";
        return null;
    }

    private boolean shouldKeep(ItemStack stack) {
        return keepReason(stack) != null;
    }

    /**
     * Say out loud what a deposit is going to leave behind, so "why is this still in my
     * inventory?" has an answer in the log instead of silence.
     */
    private void logKeptForDeposit() {
        logDepositPlan(mc);
    }

    /**
     * What a deposit will move, and what it will leave behind and why. Both halves matter:
     * "nothing was deposited" is unanswerable without the list of what the inventory holds.
     */
    public static void logDepositPlan(net.minecraft.client.Minecraft mc) {
        try {
            if (mc == null || mc.player == null) return;
            Map<String, Integer> kept = new LinkedHashMap<>();
            Map<String, String> why = new LinkedHashMap<>();
            Map<String, Integer> moving = new LinkedHashMap<>();
            for (ItemStack st : mc.player.getInventory()) {
                if (st == null || st.isEmpty()) continue;
                String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                String reason = keepReasonImpl(st);
                if (reason == null) {
                    moving.merge(id, st.getCount(), Integer::sum);
                } else {
                    kept.merge(id, st.getCount(), Integer::sum);
                    why.put(id, reason);
                }
            }
            ChatUtils.infoPrefix("BotBrain", "DEPOSIT: moving %s",
                moving.isEmpty() ? "nothing" : summarise(moving));
            if (!kept.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                kept.forEach((id, n) -> sb.append(sb.length() == 0 ? "" : ", ")
                    .append(id).append('=').append(n).append(" (").append(why.get(id)).append(')'));
                ChatUtils.infoPrefix("BotBrain", "DEPOSIT: keeping %s", sb);
            }
        } catch (Exception ignored) {}
    }

    private static String summarise(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((id, n) -> sb.append(sb.length() == 0 ? "" : ", ").append(id).append('=').append(n));
        return sb.toString();
    }

    private void resetDepositTracking() {
        lastDepositTransferRan = false;
        lastDepositSucceeded = false;
        lastDepositMoved = 0;
        lastDepositFailure = "";
    }

    public void triggerDeposit() {
        startDeposit("api");
    }

    /** The single entry point for full, manual, low-stock and API deposit requests. */
    public boolean startDeposit(String reason) {
        return startDeposit(reason, null);
    }

    public void cancelDeposit() {
        depositDriver.cancel();
        isDepositing = false;
        lastDepositFailure = depositDriver.failure();
    }

    public boolean startDeposit(String reason, String category) {
        if (isDepositing || depositDriver.isBusy()) return false;
        if (depositChests.isEmpty() && depositX == null) {
            ChatUtils.warningPrefix("BotBrain", "No deposit chest configured for post-task deposit");
            return false;
        }
        if (depositChests.isEmpty() && depositX != null) {
            addLocalChest("all", depositX, depositY, depositZ);
        }
        resetDepositTracking();
        logKeptForDeposit();
        isDepositing = true;
        resumeTaskAfterDeposit = currentTask;
        depositDriver.request(reason, category);
        return true;
    }

    private void sendProtocol(String message) {
        DataOutputStream stream = out;
        if (stream == null) return;
        try {
            synchronized (stream) {
                stream.writeUTF(message);
                stream.flush();
            }
        } catch (IOException ignored) {}
    }

    /**
     * The other half of a double chest, straight from the world's block state: a chest with
     * ChestType LEFT/RIGHT is one inventory with an adjacent chest, not two containers.
     */
    private int[] chestTwin(int x, int y, int z) {
        try {
            if (mc == null || mc.level == null) return null;
            BlockPos pos = new BlockPos(x, y, z);
            var state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return null;
            var type = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            if (type == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return null;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (d.getAxis().isVertical()) continue;
                BlockPos np = pos.relative(d);
                var ns = mc.level.getBlockState(np);
                if (!(ns.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) continue;
                var nt = ns.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
                if (nt == net.minecraft.world.level.block.state.properties.ChestType.SINGLE || nt == type) continue;
                return new int[]{np.getX(), np.getY(), np.getZ()};
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Send the contents of a chest we just opened, so the host replaces its record. */
    private void publishMeasuredChest(int x, int y, int z, int used, int total, java.util.Map<String, Long> counts) {
        try {
            StringBuilder container = new StringBuilder("{");
            if (counts != null) {
                boolean first = true;
                for (var e : counts.entrySet()) {
                    if (!first) container.append(',');
                    first = false;
                    container.append('"').append(e.getKey()).append("\":").append(e.getValue());
                }
            }
            container.append('}');
            String msg = "CHEST_DELTA:measured:" + x + ":" + y + ":" + z + "|{\"counts\":{},"
                + "\"container\":" + container + ",\"absolute\":true,"
                + "\"usedSlots\":" + used + ",\"totalSlots\":" + total + ",";
            int[] twin = chestTwin(x, y, z);
            if (twin != null) {
                msg = msg + "\"twin\":[" + twin[0] + "," + twin[1] + "," + twin[2] + "],";
            }
            msg = msg + "\"lastScan\":" + System.currentTimeMillis() + "}";
            sendProtocol(msg);
        } catch (Exception ignored) {}
    }

    private static String between(String s, String start, String end) {
        if (s == null) return null;
        int i = s.indexOf(start);
        if (i < 0) return null;
        i += start.length();
        int j = s.indexOf(end, i);
        return j < 0 ? null : s.substring(i, j);
    }

    private void applyLocalDelta(Map<String, Long> delta) {
        if (delta == null || delta.isEmpty()) return;
        try {
            var brain = meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(com.rdh.swarm.modules.BotBrain.class);
            if (brain != null) brain.invalidateStorageCounts(delta.keySet());
        } catch (Exception ignored) {}
    }

    private void onDepositFinished() {
        lastDepositTransferRan = depositDriver.transferRan();
        lastDepositSucceeded = depositDriver.succeeded();
        lastDepositMoved = depositDriver.moved();
        lastDepositDelta = depositDriver.runDelta();
        lastDepositFailure = depositDriver.failure();
        isDepositing = false;
        lastAutoDepositTime = System.currentTimeMillis();
        if (lastDepositSucceeded) resumeTask();
    }

    public EnhancedSwarmWorker(String host, int port, boolean shouldReconnect, String token) {
        this.host = host;
        this.port = port;
        this.shouldReconnect = shouldReconnect;
        this.token = token == null ? "" : token;
        String n = "unknown";
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getUser() != null)
                n = Minecraft.getInstance().getUser().getName();
        } catch (Exception ignored) {}
        this.name = n;
        setName("EnhancedSwarmWorker-" + n);
        start();
        final String _wz = BuildInfo.z();
    }

    @Override
    public void run() {
        while (shouldReconnect) {
            try {
                socket = new Socket(host, port);
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());
                try {
                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get()
                        .get(com.rdh.swarm.modules.BotBrain.class);
                    if (brain != null) brain.setOutputStream(out);
                } catch (Exception ignored) {}

                writeLine("worker:" + name + ":" + token);
                // Ask the host to push deposit/config data immediately after connect
                writeLine("REQUEST_CONFIG:" + name);

                long lastStatus = 0;

                while (!isInterrupted() && socket != null && socket.isConnected()) {
                    try {
                        if (in.available() > 0) {
                            String msg = in.readUTF();
                            if (msg == null) continue;
                            String payload = msg;
                            if (payload.startsWith("swarm:")) payload = payload.substring(6).trim();
                            handlePayload(payload);
                        }
                    } catch (IOException e) { break; }

                    long now = System.currentTimeMillis();
                    if (now - lastStatus >= 2000) {
                        sendStatus();
                        lastStatus = now;
                    }

                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                }
            } catch (Exception e) {
                ChatUtils.warningPrefix("Swarm", "Server not found at %s on port %s. Retrying in 5s.", host, port);
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            } finally {
                try { if (in != null) in.close(); } catch (IOException ignored) {}
                try { if (out != null) out.close(); } catch (IOException ignored) {}
                try { if (socket != null) socket.close(); } catch (IOException ignored) {}
                in = null; out = null; socket = null;
                try {
                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get()
                        .get(com.rdh.swarm.modules.BotBrain.class);
                    if (brain != null) brain.setOutputStream(null);
                } catch (Exception ignored) {}
                PathManagers.get().stop();
                if (shouldReconnect) {
                    ChatUtils.infoPrefix("Swarm", "Worker will attempt reconnect in 5s.");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void handlePayload(String payload) {
        final byte[] _ph = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
        try {
            if (payload == null || payload.isEmpty()) return;

            String effective = payload.trim();
            try {
                String[] toks = effective.split("\\s+", 2);
                if (toks.length > 1) {
                    String maybeTarget = toks[0];
                    if (maybeTarget.equalsIgnoreCase(this.name) || maybeTarget.equalsIgnoreCase("all")) {
                        effective = toks[1].trim();
                    }
                }
            } catch (Exception ignored) {}

            // MCP world probe: read-only look at blocks and containers.
            if (effective != null && effective.startsWith("WORLD_PROBE_REQUEST:")) {
                String rest = effective.substring("WORLD_PROBE_REQUEST:".length());
                String nonce = rest;
                String kind = "";
                String args = "";
                int p1 = rest.indexOf('|');
                if (p1 >= 0) {
                    nonce = rest.substring(0, p1);
                    String tail = rest.substring(p1 + 1);
                    int p2 = tail.indexOf('|');
                    if (p2 >= 0) { kind = tail.substring(0, p2); args = tail.substring(p2 + 1); }
                    else kind = tail;
                }
                String json = "container_open".equals(kind) ? probeOpenContainer(args) : worldProbeJson(kind, args);
                writeLine("WORLD_PROBE:" + nonce + "|" + json);
                return;
            }

            // MCP debug probe: reply with what only this process knows.
            if (effective != null && effective.startsWith("DEBUG_STATE_REQUEST:")) {
                String nonce = effective.substring("DEBUG_STATE_REQUEST:".length()).trim();
                String json;
                try {
                    var probeBrain = meteordevelopment.meteorclient.systems.modules.Modules.get()
                        .get(com.rdh.swarm.modules.BotBrain.class);
                    json = probeBrain != null ? probeBrain.debugStateJson() : "{\"error\":\"no_brain\"}";
                } catch (Exception e) {
                    json = "{\"error\":\"debug_state_failed\"}";
                }
                writeLine("DEBUG_STATE:" + nonce + "|" + json);
                return;
            }

            // Handle storage query responses from host
            if (effective != null && effective.startsWith("STORAGE_QUERY_RESPONSE:")) {
                try {
                    String body = effective.substring("STORAGE_QUERY_RESPONSE:".length());
                    if (body != null && !body.isEmpty()) {
                        // Format: <itemId>:<count>:<x,y,z>=count;...  items joined by '#'.
                        // The trailing field is the per-chest breakdown; it is optional so
                        // a legacy id:count reply still parses.
                        String[] items = body.split("#");
                        Map<String, Long> resp = new HashMap<>();
                        Map<String, String> sources = new HashMap<>();
                        for (String rawItem : items) {
                            String item = rawItem == null ? "" : rawItem.trim();
                            if (item.isEmpty()) continue;
                            String[] f = item.split(":", 3);
                            String itemId;
                            String countTok;
                            String chestTok = "";
                            if (f.length >= 3 && f[0].equals("minecraft")) {
                                // minecraft:raw_iron:<count>:<breakdown>
                                String rest = f[2];
                                int sep = rest.indexOf(':');
                                itemId = "minecraft:" + f[1];
                                if (sep >= 0) { countTok = rest.substring(0, sep); chestTok = rest.substring(sep + 1); }
                                else { countTok = rest; }
                            } else if (f.length >= 2) {
                                String rest = f.length >= 3 ? f[1] + ":" + f[2] : f[1];
                                int sep = rest.indexOf(':');
                                itemId = f[0];
                                if (sep >= 0) { countTok = rest.substring(0, sep); chestTok = rest.substring(sep + 1); }
                                else { countTok = rest; }
                            } else {
                                continue;
                            }
                            try {
                                long count = Long.parseLong(countTok);
                                // Key by both spellings: the host echoes the id exactly as
                                // requested, while lookups use the namespaced form.
                                resp.put(itemId, count);
                                resp.put(com.rdh.swarm.StorageIndex.normalizeId(itemId), count);
                                if (!chestTok.isEmpty()) {
                                    sources.put(itemId, chestTok);
                                    sources.put(com.rdh.swarm.StorageIndex.normalizeId(itemId), chestTok);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        try {
                            ChatUtils.infoPrefix("Swarm", "STORAGE: %s sources=%s", resp, sources);
                            var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                            if (brain != null) brain.receiveStorageQueryResponse(resp, sources);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
                return;
            }

            // Never process raw worker protocol messages as chat/commands
            if (effective != null && effective.startsWith("worker:")) return;

            String verb = effective.split("\\s+", 2)[0].toLowerCase();
            String args = effective.length() > verb.length() ? effective.substring(verb.length()).trim() : "";

            try { this.currentTask = effective; } catch (Exception ignored) {}

            switch (verb) {
                case "exec":
                    try {
                        if (args != null && args.startsWith("#")) {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.getConnection() != null) sendChatOnClient(mc, args);
                        } else if (args != null && args.equals("stagefail")) {
                            // Test hook: arm one forced staging failure so the base-protection
                            // guard can be exercised without waiting for a real pathfinding
                            // failure. Fires once, then disarms itself.
                            try {
                                var b = meteordevelopment.meteorclient.systems.modules.Modules.get()
                                    .get(com.rdh.swarm.modules.BotBrain.class);
                                if (b != null) b.forceStagingFailureOnce = true;
                                ChatUtils.infoPrefix("Swarm", "STAGE: forced failure armed (one shot)");
                            } catch (Exception ignored) {}
                        } else {
                            String cleaned = args;
                            if (cleaned != null && cleaned.startsWith(".")) cleaned = cleaned.substring(1);
                            Commands.dispatch(cleaned != null ? cleaned : "");
                        }
                    } catch (Exception ignored) {}
                    break;

                case "stop":
                    this.currentTask = "";
                    try {
                        var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                        if (brain != null) brain.cancel();
                    } catch (Exception ignored) {}
                    break;

                case "cancel":
                    try {
                        var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                        if (brain != null) brain.cancel();
                    } catch (Exception ignored) {}
                    break;

                case "resume":
                    try {
                        var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                        if (brain != null) brain.resume();
                    } catch (Exception ignored) {}
                    break;

                case "sync-settings":
                    handleSyncSettings();
                    break;

                case "say":
                    try { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.getConnection() != null) sendChatOnClient(mc, args); } catch (Exception ignored) {}
                    break;

                case "where":
                    try { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.player != null && mc.getConnection() != null) sendChatOnClient(mc, String.format("I'm at %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())); } catch (Exception ignored) {}
                    break;

                case "help":
                    try { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.getConnection() != null) sendChatOnClient(mc, "Verbs: goto, mine, follow, stop, say, where, deposit, depositall, help"); } catch (Exception ignored) {}
                    break;

                case "deposit":
                    handleDeposit(args);
                    break;

                case "waypoint":
                    try { handleWaypoint(args); } catch (Exception ignored) {}
                    break;

                case "scan":
                    try { handleScan(args); } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Scan handler error: %s", e.getMessage()); }
                    break;

                case "withdraw":
                    // Host sends "swarm:<bot> withdraw ..." and run() strips the transport prefix.
                    // Bare args are: <category> [quantity].
                    try { handleWithdraw(args); } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Withdraw handler error: %s", e.getMessage()); }
                    break;

                case "base":
                    handleBase(args);
                    break;

                case "depositall":
                    handleDepositAll(args);
                    break;

                case "task":
                    try {
                        // Host sends "swarm:<bot> task assign ..." and run() strips the transport prefix.
                        // Here args are bare: <subcmd> <rest>; assign payload is uuid|type|item|qty|bot.
                        // args: <subcmd> <rest>
                        String[] parts = args.split("\\s+", 2);
                        String sub = parts.length > 0 ? parts[0].toLowerCase() : "";
                        String rest = parts.length > 1 ? parts[1] : "";
                        switch (sub) {
                            case "assign": {
                                // format: <taskId>|<type>|<item>|<quantity>|<bot>
                                String[] f = rest.split("\\|");
                                if (f.length >= 4) {
                                    String taskIdStr = f[0];
                                    String typeStr = f[1];
                                    String itemStr = f[2];
                                    int qty = 0;
                                    try { qty = Integer.parseInt(f[3]); } catch (Exception ignored) {}

                                    java.util.UUID tid = null;
                                    try { tid = java.util.UUID.fromString(taskIdStr); } catch (Exception ignored) {}

                                    net.minecraft.world.item.Item item = null;
                                    try { 
                                        var id = net.minecraft.resources.Identifier.tryParse(itemStr);
                                        if (id != null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
                                    } catch (Exception ignored) {}

                                    com.rdh.swarm.task.BotTask.Type ttype = com.rdh.swarm.task.BotTask.Type.GATHER;
                                    try { ttype = com.rdh.swarm.task.BotTask.Type.valueOf(typeStr.toUpperCase()); } catch (Exception ignored) {}

                                    com.rdh.swarm.task.BotTask task;
                                    if (tid != null) {
                                        java.util.Map<String, Object> data = new java.util.HashMap<>();
                                        data.put("item", item);
                                        data.put("quantity", Integer.valueOf(qty));
                                        task = new com.rdh.swarm.task.BotTask(tid, ttype, 0, data);
                                    } else {
                                        task = new com.rdh.swarm.task.BotTask(ttype, item, qty);
                                    }

                                    // Optional trailing lease fields are ignored by
                                    // old hosts; new hosts use them to fence reports.
                                    try {
                                        String leaseToken = f.length > 5 ? f[5] : "";
                                        int attempt = 0;
                                        if (f.length > 6) attempt = Integer.parseInt(f[6]);
                                        task.setLeaseToken(leaseToken);
                                        task.setAttempt(attempt);
                                    } catch (Exception ignored) {}

                                    // assign to local BotBrain if present
                                    try {
                                        var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                                        if (brain == null || !brain.isActive()) {
                                            ChatUtils.warningPrefix("Swarm", "BotBrain module is not active");
                                        } else {
                                            try { brain.setOutputStream(out); } catch (Exception ignored) {}
                                            brain.setTask(task);
                                        }
                                    } catch (Exception e) {
                                        ChatUtils.errorPrefix("Swarm", "Failed assigning task to BotBrain: %s", e.getMessage());
                                    }

                                    // send initial TASK_PROGRESS back to host
                                    try {
                                        if (out != null) {
                                            String msg = task.getLeaseToken().isEmpty()
                                                ? String.format("TASK_PROGRESS:%s:%s:%d:%d:%s:%s", this.name, task.getId().toString(), 0, task.getTargetQuantity(), task.getStatus().name(), "")
                                                : String.format("TASK_PROGRESS:%s:%s:%s:%d:%d:%s:%s", this.name, task.getId().toString(), task.getLeaseToken(), 0, task.getTargetQuantity(), task.getStatus().name(), "");
                                            writeLine(msg);
                                            out.flush();
                                        }
                                    } catch (IOException ignored) {}
                                }
                                break;
                            }
                            case "cancel": {
                                try {
                                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                                    if (brain != null) brain.cancel();
                                } catch (Exception ignored) {}
                                break;
                            }
                            case "reset": {
                                try {
                                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                                    if (brain != null) brain.resetForReconnect();
                                } catch (Exception ignored) {}
                                break;
                            }
                            case "pause": {
                                try {
                                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                                    if (brain != null) brain.pause();
                                } catch (Exception ignored) {}
                                break;
                            }
                            case "resume": {
                                try {
                                    var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                                    if (brain != null) brain.resume();
                                } catch (Exception ignored) {}
                                break;
                            }
                        }
                    } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "task handler error: %s", e.getMessage()); }
                    break;

                default:
                    handleDefault(effective, verb);
                    break;
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Error handling payload: %s", payload);
            e.printStackTrace();
        }
    }

    private void handleDeposit(String args) {
        try {
            if (args != null && args.startsWith("reset")) {
                String[] parts = args.trim().split("\\s+");
                int generation = -1;
                if (parts.length >= 2) {
                    try { generation = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
                pendingDepositGeneration = generation;
                pendingDepositChests = new ConcurrentHashMap<>();
                ignorePendingDeposit = generation >= 0 && generation <= depositGeneration;
                return;
            }
            if (args != null && args.equals("commit")) {
                if (pendingDepositChests != null && !ignorePendingDeposit) {
                    depositChests = pendingDepositChests;
                    depositGeneration = pendingDepositGeneration;
                    int count = 0;
                    for (List<int[]> list : depositChests.values()) count += list.size();
                    ChatUtils.infoPrefix("Swarm", "CHEST: list replaced n=%d gen=%d", count, depositGeneration);
                }
                pendingDepositChests = null;
                pendingDepositGeneration = -1;
                ignorePendingDeposit = false;
                return;
            }
            if (args != null && args.startsWith("set ")) {
                String setArgs = args.substring(4).trim();
                String[] parts = setArgs.split("\\s+");

                if (parts.length >= 3) {
                    // Try old format: deposit set X Y Z
                    try {
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        // legacy single chest: keep legacy fields and also add to 'all'
                        depositX = x; depositY = y; depositZ = z;
                        addLocalChest("all", x, y, z);
                        return;
                    } catch (NumberFormatException ignored) {}

                    // New format: deposit set <category> X Y Z
                    if (parts.length >= 4) {
                        String category = parts[0].toLowerCase();
                        try {
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            addLocalChest(category, x, y, z);
                        } catch (NumberFormatException e) {
                            ChatUtils.errorPrefix("Swarm", "Invalid coords. Use: deposit set [category] X Y Z");
                        }
                    }
                }
            } else if (args != null && args.startsWith("auto ")) {
                String toggle = args.substring(5).trim().toLowerCase();
                autoDeposit = toggle.equals("on") || toggle.equals("true") || toggle.equals("1");
                ChatUtils.infoPrefix("Swarm", "Auto-deposit %s", autoDeposit ? "ENABLED" : "DISABLED");
            } else if (args != null && args.startsWith("threshold ")) {
                try {
                    autoDepositThreshold = Integer.parseInt(args.substring(10).trim());
                    ChatUtils.infoPrefix("Swarm", "Auto-deposit threshold set to %d empty slots", autoDepositThreshold);
                } catch (NumberFormatException e) {
                    ChatUtils.errorPrefix("Swarm", "Invalid threshold");
                }
            } else if (args != null && args.startsWith("list")) {
                if (depositChests.isEmpty()) {
                    ChatUtils.infoPrefix("Swarm", "No deposit chests configured");
                } else {
                    for (var entry : depositChests.entrySet()) {
                        List<int[]> list = entry.getValue();
                        for (int i = 0; i < list.size(); i++) {
                            int[] pos = list.get(i);
                            ChatUtils.infoPrefix("Swarm", "  %s[%d] -> %d %d %d", entry.getKey(), i+1, pos[0], pos[1], pos[2]);
                        }
                    }
                }
                ChatUtils.infoPrefix("Swarm", "Auto-deposit: %s (threshold: %d)", autoDeposit ? "ON" : "OFF", autoDepositThreshold);
            } else if (args != null && args.startsWith("remove ")) {
                String rem = args.substring(7).trim();
                String[] parts = rem.split("\\s+");
                String cat = parts[0].toLowerCase();
                if (parts.length >= 2) {
                    try {
                        int idx = Integer.parseInt(parts[1]);
                        List<int[]> lst = depositChests.get(cat);
                        if (lst != null && idx >= 1 && idx <= lst.size()) {
                            lst.remove(idx-1);
                            if (lst.isEmpty()) depositChests.remove(cat);
                            ChatUtils.infoPrefix("Swarm", "Removed %s[%d]", cat, idx);
                        } else {
                            ChatUtils.warningPrefix("Swarm", "No chest at index %d for category %s", idx, cat);
                        }
                    } catch (NumberFormatException e) {
                        ChatUtils.errorPrefix("Swarm", "Invalid index");
                    }
                } else {
                    if (depositChests.remove(cat) != null) {
                        ChatUtils.infoPrefix("Swarm", "Removed all chests for category: %s", cat);
                    } else {
                        ChatUtils.warningPrefix("Swarm", "No chest for category: %s", cat);
                    }
                }
            } else if (args != null && !args.isEmpty()) {
                // deposit <category>
                String category = args.trim().toLowerCase();
                List<int[]> list = depositChests.get(category);
                // A base with one general pile registers it as "all"; accept a category
                // request against it instead of answering "No deposit chest for category".
                if ((list == null || list.isEmpty())
                    && depositChests.get("all") != null && !depositChests.get("all").isEmpty()) {
                    category = "all";
                    list = depositChests.get("all");
                }
                if (list != null && !list.isEmpty()) {
                    if (!startDeposit("api", category)) {
                        ChatUtils.warningPrefix("Swarm", "Already depositing");
                    }
                } else {
                    ChatUtils.warningPrefix("Swarm", "No deposit chest for category: %s", category);
                }
            } else {
                // deposit (no args) — full category deposit
                if (!depositChests.isEmpty()) {
                    if (!startDeposit("api")) {
                        ChatUtils.warningPrefix("Swarm", "Already depositing");
                    }
                } else if (depositX != null) {
                    depositChests = new ConcurrentHashMap<>();
                    addLocalChest("all", depositX, depositY, depositZ);
                    if (!startDeposit("api")) {
                        ChatUtils.warningPrefix("Swarm", "Already depositing");
                    }
                } else {
                    ChatUtils.warningPrefix("Swarm", "No deposit chest configured");
                }
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Deposit error: %s", e.getMessage());
        }
    }

    private void handleDepositAll(String args) {
        try {
            if (depositChests.isEmpty() && depositX == null) {
                ChatUtils.warningPrefix("Swarm", "No deposit chest configured");
                return;
            }
            if (depositChests.isEmpty()) {
                addLocalChest("all", depositX, depositY, depositZ);
            }
            if (!startDeposit("api", "all")) {
                ChatUtils.warningPrefix("Swarm", "Already depositing");
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "DepositAll error: %s", e.getMessage());
        }
    }

    private void handleBase(String args) {
        try {
            if (args != null && args.startsWith("set ")) {
                String rest = args.substring(4).trim();
                String[] parts = rest.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        baseX = x; baseY = y; baseZ = z;
                        ChatUtils.infoPrefix("Swarm", "Set base anchor at %d %d %d", x, y, z);
                        return;
                    } catch (NumberFormatException e) {
                        ChatUtils.errorPrefix("Swarm", "Invalid coords. Use: base set X Y Z");
                    }
                }
            } else if (args != null && args.startsWith("clear")) {
                baseX = null; baseY = null; baseZ = null;
                ChatUtils.infoPrefix("Swarm", "Cleared base anchor");
            } else if (args != null && args.startsWith("show")) {
                if (baseX != null) ChatUtils.infoPrefix("Swarm", "Base anchor: %d %d %d", baseX, baseY, baseZ);
                else ChatUtils.infoPrefix("Swarm", "No base anchor configured");
            } else {
                // default: show
                if (baseX != null) ChatUtils.infoPrefix("Swarm", "Base anchor: %d %d %d", baseX, baseY, baseZ);
                else ChatUtils.infoPrefix("Swarm", "No base anchor configured");
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Base handler error: %s", e.getMessage());
        }
    }

    public WaypointManager getOrCreateWaypointManager() {
        if (workerWaypointManager == null) workerWaypointManager = new WaypointManager();
        try { workerWaypointManager.ensureLoaded(); } catch (Exception ignored) {}
        return workerWaypointManager;
    }

    private void handleWaypoint(String args) {
        try {
            if (args == null) return;
            if (args.startsWith("set ")) {
                String body = args.substring(4).trim();
                String[] parts = body.split("\\s+");
                if (parts.length >= 5) {
                    String name = parts[0];
                    String category = parts[1];
                    // overwrite overworld coords
                    try {
                        int owx = Integer.parseInt(parts[2]);
                        int owe = Integer.parseInt(parts[3]);
                        int owz = Integer.parseInt(parts[4]);
                        getOrCreateWaypointManager().addWaypoint(name, category, owx, owe, owz, false);
                    } catch (NumberFormatException ignored) {}

                    // optional nether coords
                    if (parts.length >= 8) {
                        try {
                            int nx = Integer.parseInt(parts[5]);
                            int ny = Integer.parseInt(parts[6]);
                            int nz = Integer.parseInt(parts[7]);
                            getOrCreateWaypointManager().addWaypoint(parts[0], parts[1], nx, ny, nz, true);
                        } catch (NumberFormatException ignored) {}
                    }

                    ChatUtils.infoPrefix("Swarm", "Waypoint set: %s (%s)", parts[0], parts[1]);
                }
            } else if (args.startsWith("remove ")) {
                String name = args.substring(7).trim();
                if (!name.isEmpty()) {
                    boolean ok = getOrCreateWaypointManager().removeWaypoint(name);
                    if (ok) ChatUtils.infoPrefix("Swarm", "Removed waypoint: %s", name);
                    else ChatUtils.warningPrefix("Swarm", "No waypoint named: %s", name);
                }
            } else if (args.startsWith("list")) {
                var mgr = getOrCreateWaypointManager();
                var all = mgr.getAllWaypoints();
                if (all == null || all.isEmpty()) {
                    ChatUtils.infoPrefix("Swarm", "No waypoints configured");
                } else {
                    for (SwarmWaypoint wp : all) ChatUtils.info(wp.toString());
                }
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Waypoint handler error: %s", e.getMessage());
        }
    }

    private void addLocalChest(String category, int x, int y, int z) {
        if (category == null || category.isBlank()) category = "all";
        category = category.toLowerCase();
        Map<String, List<int[]>> destination = pendingDepositChests != null ? pendingDepositChests : depositChests;
        List<int[]> list = destination.computeIfAbsent(category, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            for (int[] existing : list) {
                if (existing != null && existing.length >= 3
                    && existing[0] == x && existing[1] == y && existing[2] == z) {
                    ChatUtils.infoPrefix("Swarm", "CHEST: duplicate ignored %s %d %d %d %s",
                        currentDimension(), x, y, z, category);
                    return;
                }
            }
            list.add(new int[]{x, y, z});
        }
        ChatUtils.infoPrefix("Swarm", "CHEST: add %s %d %d %d %s", currentDimension(), x, y, z, category);
    }

    private String currentDimension() {
        try {
            if (mc.level != null) return mc.level.dimension().identifier().toString();
        } catch (Exception ignored) {}
        return "unknown";
    }

    private void resumeTask() {
        try {
            if (resumeTaskAfterDeposit != null && !resumeTaskAfterDeposit.isEmpty()) {
                if (resumeTaskAfterDeposit.startsWith("#")) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getConnection() != null) sendChatOnClient(mc, resumeTaskAfterDeposit);
                } else {
                    try { Commands.dispatch(resumeTaskAfterDeposit); } catch (Exception ignored) {}
                }
                resumeTaskAfterDeposit = null;
            }
        } catch (Exception ignored) {}
    }

    // Find a safe adjacent stand position for interacting with a chest.
    // Returns null if none found.
    private BlockPos findChestStandPos(BlockPos chestPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;

        // Search adjacent positions (horizontal) and small vertical offsets for a safe spot
        for (int yOff = 0; yOff <= 1; yOff++) {
            for (int radius = 1; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // only perimeter positions to prefer nearby walkable spots
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos cand = chestPos.offset(dx, -yOff, dz);
                        try {
                            // don't stand on the chest block itself
                            if (cand.equals(chestPos)) continue;
                            var state = mc.level.getBlockState(cand);
                            var below = mc.level.getBlockState(cand.below());
                            // prefer non-solid/air space at candidate and solid block beneath
                            boolean spaceOk = state.isAir() || !state.canOcclude();
                            boolean supportOk = !below.isAir() && below.isSolid();
                            if (spaceOk && supportOk) {
                                return cand;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // Try same-level cardinal if nothing found
        BlockPos[] fallbacks = new BlockPos[]{
            chestPos.offset(1, 0, 0), chestPos.offset(-1, 0, 0), chestPos.offset(0, 0, 1), chestPos.offset(0, 0, -1)
        };
        for (BlockPos c : fallbacks) {
            try {
                var s = mc.level.getBlockState(c);
                var b = mc.level.getBlockState(c.below());
                if ((s.isAir() || !s.canOcclude()) && !b.isAir()) return c;
            } catch (Exception ignored) {}
        }

        return null;
    }

    // Returns true if player's inventory contains items that can be deposited
    private boolean hasAnyDepositableItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        try {
            for (int i = 0; i < 36; i++) {
                try {
                    ItemStack st = mc.player.getInventory().getItem(i);
                    if (st == null || st.isEmpty()) continue;
                    if (shouldKeep(st)) continue;
                    if (depositChests.containsKey("all")) return true;
                    for (String cat : depositChests.keySet()) {
                        if (cat.equalsIgnoreCase("all")) continue;
                        if (DepositConfig.matchesCategory(cat, st)) return true;
                    }
                    if (depositX != null) return true;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void tick() {
        if (depositDriver.isBusy()) {
            depositDriver.tick();
            lastDepositTransferRan = depositDriver.transferRan();
            lastDepositSucceeded = depositDriver.succeeded();
            lastDepositMoved = depositDriver.moved();
            lastDepositDelta = depositDriver.runDelta();
            lastDepositFailure = depositDriver.failure();
            if (!depositDriver.isBusy()) isDepositing = false;
            return;
        }
        if (isDepositing) isDepositing = false;
        if (!autoDeposit || depositChests.isEmpty()) return;
        if (System.currentTimeMillis() - lastAutoDepositTime < AUTO_DEPOSIT_COOLDOWN_MS) return;

        // Don't fire auto-deposit if BotBrain is already handling return/deposit flow
        try {
            var botBrain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
            if (botBrain != null && botBrain.isActive()) {
                var state = botBrain.getState();
                if (state == com.rdh.swarm.modules.BotBrain.BrainState.RETURNING_TO_BASE
                    || state == com.rdh.swarm.modules.BotBrain.BrainState.DEPOSITING
                    || state == com.rdh.swarm.modules.BotBrain.BrainState.GATHERING) {
                    return;
                }
            }
        } catch (Exception ignored) {}
        
        // Don't fire auto-deposit if BotBrain is already handling return/deposit flow
        try {
            var botBrain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
            if (botBrain != null && botBrain.isActive()) {
                var state = botBrain.getState();
                if (state == com.rdh.swarm.modules.BotBrain.BrainState.RETURNING_TO_BASE 
                    || state == com.rdh.swarm.modules.BotBrain.BrainState.DEPOSITING
                    || state == com.rdh.swarm.modules.BotBrain.BrainState.GATHERING) {
                    return; // BotBrain is handling it, don't interfere
                }
            }
        } catch (Exception ignored) {}

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) emptySlots++;
        }

        if (emptySlots <= autoDepositThreshold) {
            ChatUtils.infoPrefix("Swarm", "Auto-deposit triggered (%d empty <= %d threshold)", emptySlots, autoDepositThreshold);
            if (startDeposit("low")) lastAutoDepositTime = System.currentTimeMillis();
        }
    }

    private void handleSyncSettings() {
        // Baritone/settings push removed — no-op
    }

    private void handleDefault(String effective, String verb) {
        try {
            Set<String> baritoneVerbs = Set.of("goto", "follow", "mine", "stop", "path", "cancel", "pause", "resume");
            if (baritoneVerbs.contains(verb)) {
                Minecraft mc = Minecraft.getInstance();
                String toSend = effective;
                try {
                    if (verb.equalsIgnoreCase("follow")) {
                        String rest = effective.substring(verb.length()).trim();
                        if (!rest.isEmpty()) {
                            String[] parts = rest.split("\\s+");
                            if (parts.length == 1 && mc != null && mc.level != null) {
                                boolean found = mc.level.players().stream().anyMatch(p -> p.getName().getString().equalsIgnoreCase(parts[0]));
                                if (found) toSend = verb + " player " + parts[0];
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (mc != null && mc.getConnection() != null) sendChatOnClient(mc, "#" + toSend);
                else Commands.dispatch(toSend);
            } else {
                Commands.dispatch(effective);
            }
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Failed to dispatch: %s", effective);
        }
    }

    private void handleScan(String args) {
        try {
            String category = null;
            if (args != null && !args.isEmpty()) category = args.trim().split("\\s+", 2).length > 0 ? args.trim().split("\\s+", 2)[0] : null;
            // If category omitted -> scan all configured chests
            List<int[]> targets = new ArrayList<>();
            if (category != null && !category.isEmpty()) {
                List<int[]> l = depositChests.get(category.toLowerCase());
                if (l != null) targets.addAll(l);
            } else {
                for (var e : depositChests.entrySet()) for (int[] p : e.getValue()) targets.add(p);
            }

            if (targets.isEmpty()) { ChatUtils.warningPrefix("Swarm", "No chests configured to scan for category: %s", category == null ? "all" : category); return; }

            for (int[] p : new ArrayList<>(targets)) {
                int x = p[0], y = p[1], z = p[2];
                // perform chest scan similar to deposit open/read but do not transfer
                JsonObject result = new JsonObject();
                result.addProperty("lastScan", System.currentTimeMillis());
                result.addProperty("status", "FRESH");
                try {
                    BlockPos chestPos = new BlockPos(x, y, z);
                    BlockPos standPos = findChestStandPos(chestPos);
                    if (standPos == null) {
                        result.addProperty("status", "UNREACHABLE");
                        sendScanResult(category == null ? "all" : category, x, y, z, result);
                        continue;
                    }

                    if (mc.getConnection() != null) sendChatOnClient(mc, String.format("#goto %d %d %d", standPos.getX(), standPos.getY(), standPos.getZ()));
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < 60000) {
                        if (mc.player != null) {
                            double dx = mc.player.getX() - standPos.getX();
                            double dy = mc.player.getY() - standPos.getY();
                            double dz = mc.player.getZ() - standPos.getZ();
                            if (dx*dx + dy*dy + dz*dz <= 4.0) break;
                        }
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }

                    // open chest
                    mc.execute(() -> {
                        try {
                            if (mc.player == null) return;
                            try { mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(chestPos)); } catch (Exception ignored) {}
                            BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(chestPos), Direction.UP, chestPos, false);
                            try { mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr); } catch (Exception ignored) {}
                        } catch (Exception ignored) {}
                    });

                    long ws = System.currentTimeMillis();
                    boolean opened = false;
                    while (System.currentTimeMillis() - ws < 10000) {
                        try {
                            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                                var handler = mc.player.containerMenu;
                                int containerSlots = 0;
                                for (int si = 0; si < handler.slots.size(); si++) {
                                    try { Slot s = handler.getSlot(si); if (s.container != mc.player.getInventory()) containerSlots++; } catch (Exception ignored) {}
                                }
                                if (containerSlots > 0) { opened = true; break; }
                            }
                        } catch (Exception ignored) {}
                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                    }

                    if (!opened) {
                        result.addProperty("status", "MISSING");
                        sendScanResult(category == null ? "all" : category, x, y, z, result);
                        continue;
                    }

                    var handler = mc.player.containerMenu;
                    int playerStart = handler.slots.size() - 36;
                    int used = 0; int total = 0;
                    Map<String, Long> counts = new LinkedHashMap<>();
                    try {
                        for (int si = 0; si < handler.slots.size(); si++) {
                            try {
                                Slot s = handler.getSlot(si);
                                if (s.container != mc.player.getInventory()) {
                                    total++;
                                    var st = s.getItem();
                                    if (st != null && !st.isEmpty()) {
                                        used++;
                                        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                                        long q = st.getCount();
                                        counts.merge(id, q, Long::sum);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}

                    // Close the container properly. Clearing only the screen left
                    // containerMenu on this chest, so the next chest's open check
                    // matched the old menu and reported identical contents.
                    closeContainerAndWait();

                    result.addProperty("usedSlots", used);
                    result.addProperty("totalSlots", total);
                    result.addProperty("fullness", total == 0 ? 0.0 : (double)used / (double)total);
                    JsonObject countsObj = new JsonObject();
                    for (var e : counts.entrySet()) countsObj.addProperty(e.getKey(), e.getValue());
                    result.add("counts", countsObj);
                    result.addProperty("status", "FRESH");
                    ChatUtils.infoPrefix("Swarm", 
                        "SCAN RESULT [%d %d %d] slots: %d/%d (%.2f) items: %s",
                        x, y, z,
                        used, total,
                        total == 0 ? 0.0 : (double) used / total,
                        counts.toString()
                    );
                    sendScanResult(category == null ? "all" : category, x, y, z, result);

                } catch (Exception e) {
                    try { result.addProperty("status", "UNREACHABLE"); } catch (Exception ignored) {}
                    sendScanResult(category == null ? "all" : category, x, y, z, result);
                }
            }

            // send scan complete
            writeLine("SCAN_COMPLETE:" + (category == null ? "all" : category));

        } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Scan failed: %s", e.getMessage()); }
    }

    private void handleWithdraw(String args) {
        try {
            // args: <category> <x> <y> <z> <itemId> <count>
            if (args == null || args.isEmpty()) return;
            String[] p = args.trim().split("\s+", 6);
            if (p.length < 6) return;
            String category = p[0];
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            String itemId = p[4];
            int needed = Integer.parseInt(p[5]);

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            BlockPos chestPos = new BlockPos(x, y, z);
            BlockPos standPos = findChestStandPos(chestPos);
            if (standPos == null) {
                ChatUtils.warningPrefix("Swarm", "Withdraw: no safe stand pos for %d %d %d", x, y, z);
                return;
            }

            if (mc.getConnection() != null) sendChatOnClient(mc, String.format("#goto %d %d %d", standPos.getX(), standPos.getY(), standPos.getZ()));
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60000) {
                if (mc.player != null) {
                    double dx = mc.player.getX() - standPos.getX();
                    double dy = mc.player.getY() - standPos.getY();
                    double dz = mc.player.getZ() - standPos.getZ();
                    if (dx*dx + dy*dy + dz*dz <= 4.0) break;
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }

            // open chest
            mc.execute(() -> {
                try {
                    if (mc.player == null) return;
                    try { mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(chestPos)); } catch (Exception ignored) {}
                    BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(chestPos), Direction.UP, chestPos, false);
                    try { mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr); } catch (Exception ignored) {}
                } catch (Exception ignored) {}
            });

            long openStart = System.currentTimeMillis();
            boolean opened = false;
            while (System.currentTimeMillis() - openStart < 10000) {
                try {
                    if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                        var handler = mc.player.containerMenu;
                        // A furnace/smoker/blast-furnace menu also has non-inventory slots, so
                        // it used to satisfy the "chest opened" check. The withdraw then
                        // shift-clicked the appliance's own input slot and reported
                        // "took 1/1 … from the chest" while the chest never lost an item
                        // (and the bot's smelt lost the ore it had just inserted).
                        if (handler instanceof net.minecraft.world.inventory.AbstractFurnaceMenu
                            || handler instanceof net.minecraft.world.inventory.CraftingMenu
                            || handler instanceof net.minecraft.world.inventory.InventoryMenu) {
                            ChatUtils.warningPrefix("Swarm", "Withdraw: %d %d %d not open (busy with %s), not taking from it",
                                x, y, z, handler.getClass().getSimpleName());
                            // Tell the host this dispatch was refused, not completed. A
                            // silent return left the host with a send and no result, so the
                            // index kept the count, nothing was debited, and the task sat
                            // behind a withdrawal that never happened.
                            writeLine(String.format("WITHDRAW_REFUSED:%s:%d:%d:%d", itemId, x, y, z));
                            return;
                        }
                        int containerSlots = 0;
                        for (int si = 0; si < handler.slots.size(); si++) {
                            try { Slot s = handler.getSlot(si); if (s.container != mc.player.getInventory()) containerSlots++; } catch (Exception ignored) {}
                        }
                        if (containerSlots > 0) { opened = true; break; }
                    }
                } catch (Exception ignored) {}
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }

            if (!opened) {
                ChatUtils.warningPrefix("Swarm", "Withdraw: chest did not open at %d %d %d", x, y, z);
                return;
            }

            // transfer desired items from container slots to player inventory by shift-clicking container slots
            var handler = mc.player.containerMenu;
            int transferred = 0;
            int playerStart = handler.slots.size() - 36;
            int invBefore = countInInventory(handler, itemId, playerStart);
            int chestBefore = countInContainer(handler, itemId, playerStart);
            ChatUtils.infoPrefix("Swarm", "Withdraw: before click inventory=%d chest=%d (need %d)",
                invBefore, chestBefore, needed);

            for (int si = 0; si < handler.slots.size() && transferred < needed; si++) {
                try {
                    Slot slot = handler.getSlot(si);
                    if (slot.container == mc.player.getInventory()) continue;
                    ItemStack st = slot.getItem();
                    if (st == null || st.isEmpty()) continue;
                    String sid = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    if (!sid.equals(itemId)) continue;
                    int beforeCount = st.getCount();
                    int want = needed - transferred;
                    if (want < beforeCount) {
                        // Exact take. A shift-click moves the whole stack, so a 2-item
                        // need against a 6-ore slot used to pull 6 and over-produce. Pick
                        // the stack up, right-click the needed singles into the inventory,
                        // then put the remainder back into the same chest slot.
                        int dest = findDestSlot(handler, itemId, playerStart);
                        if (dest < 0) {
                            ChatUtils.warningPrefix("Swarm", "Withdraw: no inventory slot for the exact take, stopping");
                            break;
                        }
                        clickSlotSync(si, 0);
                        for (int k = 0; k < want; k++) clickSlotSync(dest, 1);
                        clickSlotSync(si, 0); // remainder back into the chest slot
                        ChatUtils.infoPrefix("Swarm", "Withdraw: exact take %d from a %d stack (slot %d -> %d)",
                            want, beforeCount, si, dest);
                    } else {
                        InvUtils.shiftClick().slotId(si);
                    }
                    // Count only what the menu actually shows moving. The old code added
                    // the intended amount, so a click the server ignored still reported
                    // "took 1/1" and the host debited an index the chest never lost.
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                    int afterCount = 0;
                    try {
                        ItemStack now = handler.getSlot(si).getItem();
                        if (now != null && !now.isEmpty()) {
                            String nid = BuiltInRegistries.ITEM.getKey(now.getItem()).toString();
                            if (nid.equals(itemId)) afterCount = now.getCount();
                        }
                    } catch (Exception ignored) {}
                    int invNow = countInInventory(handler, itemId, playerStart);
                    int moved = Math.max(0, beforeCount - afterCount);
                    int landed = invNow - invBefore - transferred;
                    if (moved == 0) {
                        ChatUtils.warningPrefix("Swarm", "Withdraw: slot %d click moved nothing (%d -> %d), not counting it",
                            si, beforeCount, afterCount);
                    } else if (landed <= 0) {
                        // The chest slot emptied but the inventory did not gain it: a
                        // shift-click that never committed (menu closed mid-move, stale
                        // snapshot). Counting it is what made the host debit for ore the
                        // bot never held.
                        ChatUtils.warningPrefix("Swarm",
                            "Withdraw: slot %d lost %d (chest %d->%d, inventory %d->%d), not counting it",
                            si, moved, beforeCount, afterCount, invBefore, invNow);
                    } else {
                        transferred += Math.min(moved, landed);
                        ChatUtils.infoPrefix("Swarm", "Withdraw: slot %d took %d (inventory %d -> %d)",
                            si, Math.min(moved, landed), invBefore, invNow);
                    }
                    invBefore = invNow;
                    try { Thread.sleep(80 + ThreadLocalRandom.current().nextInt(80)); } catch (InterruptedException ignored) {}
                } catch (Exception ignored) {}
            }

            // A shift-click takes the WHOLE stack, so asking for 2 of a 4-ore stack
            // leaves the bot holding 4. The old "return the excess" loop shift-clicked
            // that whole stack back into the chest — emptying the bot's inventory after
            // a transfer that had already landed, which is why the host debited 2 for
            // ore the bot then held none of. Keep the extra instead (it is the same
            // item the task is short of) and let the debit follow what was received.
            int held = countInInventory(handler, itemId, playerStart);
            if (held > needed) {
                // Should not happen with the exact take; if it does, say so instead of
                // quietly over-producing.
                ChatUtils.warningPrefix("Swarm", "Withdraw: over-delivered %d for a need of %d", held, needed);
            }

            // close (send the container-close packet, not just the screen)
            closeContainerAndWait();

            // Final authority is the bot's own inventory once the menu is gone. A
            // transfer the chest showed leaving but that is not here is reported as
            // nothing, so the host never debits for ore the bot cannot smelt.
            int settled = 0;
            for (int i = 0; i < 10; i++) {
                settled = countInPlayerInventory(itemId);
                if (settled >= transferred) break;
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
            // Report exactly what is in the bot's inventory now, never a slot delta.
            transferred = Math.min(transferred, settled);
            ChatUtils.infoPrefix("Swarm", "Withdraw: inventory holds %d/%d %s after transfer",
                settled, transferred, itemId);
            if (settled < transferred) {
                ChatUtils.warningPrefix("Swarm", "Withdraw: %d %s never reached the inventory; reporting %d",
                    transferred - settled, itemId, settled);
                transferred = settled;
            }

            // Report what the inventory actually holds, not what was asked for: a
            // shift-click takes the whole stack, so the two differ and only the
            // received count is debited.
            ChatUtils.infoPrefix("Swarm", "Withdraw: took %d (asked %d) %s from %d,%d,%d", transferred, needed, itemId, x, y, z);
            try {
                var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                if (brain != null) brain.noteWithdraw(itemId, transferred);
            } catch (Exception ignored) {}
            // notify host of result
            writeLine(String.format("WITHDRAW_RESULT:%s:%d:%d:%d:%d", itemId, x, y, z, transferred));

        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Withdraw exception: %s", e.getMessage());
        }
    }

    /** A player slot that can receive a single item: same item with room, else empty. */
    private int findDestSlot(AbstractContainerMenu handler, String itemId, int playerStart) {
        int empty = -1;
        for (int si = playerStart; si < handler.slots.size(); si++) {
            try {
                ItemStack st = handler.getSlot(si).getItem();
                if (st == null || st.isEmpty()) { if (empty < 0) empty = si; continue; }
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)
                    && st.getCount() < st.getMaxStackSize()) return si;
            } catch (Exception ignored) {}
        }
        return empty;
    }

    /** One container click on the client thread, then a beat for the server to answer. */
    private void clickSlotSync(int slotId, int button) {
        try {
            mc.execute(() -> {
                try {
                    if (mc.player != null && mc.gameMode != null) {
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slotId, button,
                            net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    }
                } catch (Exception ignored) {}
            });
            Thread.sleep(120);
        } catch (InterruptedException ignored) {}
    }

    private int countInInventory(AbstractContainerMenu handler, String itemId, int playerStart) {
        int n = 0;
        for (int si = playerStart; si < handler.slots.size(); si++) {
            try {
                ItemStack st = handler.getSlot(si).getItem();
                if (st == null || st.isEmpty()) continue;
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)) n += st.getCount();
            } catch (Exception ignored) {}
        }
        return n;
    }

    private int countInContainer(AbstractContainerMenu handler, String itemId, int playerStart) {
        int n = 0;
        for (int si = 0; si < playerStart; si++) {
            try {
                ItemStack st = handler.getSlot(si).getItem();
                if (st == null || st.isEmpty()) continue;
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)) n += st.getCount();
            } catch (Exception ignored) {}
        }
        return n;
    }

    /** Authoritative read: the bot's own inventory, menu closed. */
    private int countInPlayerInventory(String itemId) {
        if (mc.player == null) return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st == null || st.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)) n += st.getCount();
        }
        return n;
    }

    /** Sends the container-close packet and waits for the inventory menu to return. */
    private void closeContainerAndWait() {
        try {
            mc.execute(() -> {
                try {
                    if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                        mc.player.closeContainer();
                    }
                    mc.setScreenAndShow(null);
                } catch (Exception ignored) {}
            });
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                if (mc.player == null || mc.player.containerMenu == mc.player.inventoryMenu) return;
                try { Thread.sleep(50); } catch (InterruptedException ignored) { return; }
            }
        } catch (Exception ignored) {}
    }

    private void sendScanResult(String category, int x, int y, int z, JsonObject result) {
        String header = String.format("SCAN_CHEST_RESULT:%s:%d:%d:%d|", category == null ? "all" : category, x, y, z);
        writeLine(header + result.toString());
    }

    // ---- world probe (MCP v0.1: docs/MCP_SERVER_SPEC.md 5.2) --------------
    // Read-only. Answers "what is actually in the world" so a claim in the log can
    // be checked against blocks and containers instead of trusted.
    private static String jq(String s) { return String.valueOf((char) 34) + s + (char) 34; }

    private String worldProbeJson(String kind, String args) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{').append(jq("kind")).append(':').append(jq(kind));
        try {
            if (mc.level == null || mc.player == null) {
                return sb.append(',').append(jq("error")).append(':').append(jq("no_world")).append('}').toString();
            }
            String[] a = (args == null || args.isBlank()) ? new String[0] : args.split(",");

            if ("container".equals(kind)) {
                int x = Integer.parseInt(a[0].trim()), y = Integer.parseInt(a[1].trim()), z = Integer.parseInt(a[2].trim());
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                sb.append(',').append(jq("pos")).append(":[").append(x).append(',').append(y).append(',').append(z).append(']');
                var be = mc.level.getBlockEntity(pos);
                sb.append(',').append(jq("type")).append(':').append(jq(be == null ? "none" : be.getClass().getSimpleName()));
                if (!(be instanceof net.minecraft.world.Container)) {
                    sb.append(',').append(jq("error")).append(':').append(jq("not_a_container"));
                } else {
                    // The client only learns a container's contents through its
                    // ContainerMenu. Reporting an empty list here would claim
                    // "empty" when the truth is "not readable this way".
                    sb.append(',').append(jq("contentsKnown")).append(":false");
                    sb.append(',').append(jq("note")).append(':')
                      .append(jq("a client block entity carries no inventory; call with open=true to read it"));
                }
            } else if ("containers".equals(kind)) {
                int r = Math.min(24, Integer.parseInt(a[0].trim()));
                var base = mc.player.blockPosition();
                sb.append(',').append(jq("radius")).append(':').append(r);
                sb.append(',').append(jq("containers")).append(":[");
                boolean first = true;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            net.minecraft.core.BlockPos pos = base.offset(dx, dy, dz);
                            var be = mc.level.getBlockEntity(pos);
                            if (!(be instanceof net.minecraft.world.Container c)) continue;
                            if (!first) sb.append(',');
                            first = false;
                            sb.append('{').append(jq("pos")).append(":[").append(pos.getX()).append(',')
                              .append(pos.getY()).append(',').append(pos.getZ()).append(']')
                              .append(',').append(jq("type")).append(':').append(jq(be.getClass().getSimpleName()))
                              .append(',').append(jq("contentsKnown")).append(":false");
                            sb.append('}');
                        }
                    }
                }
                sb.append(']');
            } else if ("blocks".equals(kind)) {
                int x = Integer.parseInt(a[0].trim()), y = Integer.parseInt(a[1].trim()), z = Integer.parseInt(a[2].trim());
                int r = Math.min(8, a.length > 3 ? Integer.parseInt(a[3].trim()) : 4);
                net.minecraft.core.BlockPos center = new net.minecraft.core.BlockPos(x, y, z);
                sb.append(',').append(jq("blocks")).append(":[");
                boolean first = true;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            net.minecraft.core.BlockPos pos = center.offset(dx, dy, dz);
                            var st = mc.level.getBlockState(pos);
                            if (st.isAir()) continue;
                            if (!first) sb.append(',');
                            first = false;
                            sb.append('{').append(jq("pos")).append(":[").append(pos.getX()).append(',')
                              .append(pos.getY()).append(',').append(pos.getZ()).append(']')
                              .append(',').append(jq("id")).append(':')
                              .append(jq(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString()))
                              .append('}');
                        }
                    }
                }
                sb.append(']');
            } else {
                sb.append(',').append(jq("error")).append(':').append(jq("unknown_kind"));
            }
        } catch (Exception e) {
            sb.append(',').append(jq("error")).append(':').append(jq(String.valueOf(e.getMessage())));
        }
        return sb.append('}').toString();
    }

    // Authoritative contents. A client-side BlockEntity never holds a chest's
    // inventory - the contents arrive in the ContainerMenu when it is opened - so
    // the only honest answer to "what is in this chest" is to walk to it and open
    // it, exactly the way the scan and deposit paths already do. Blocking on
    // purpose: this runs on the same payload thread as handleScan.
    private String probeOpenContainer(String args) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{').append(jq("kind")).append(':').append(jq("container_open"));
        try {
            String[] a = args.split(",");
            int x = Integer.parseInt(a[0].trim()), y = Integer.parseInt(a[1].trim()), z = Integer.parseInt(a[2].trim());
            BlockPos chestPos = new BlockPos(x, y, z);
            sb.append(',').append(jq("pos")).append(":[").append(x).append(',').append(y).append(',').append(z).append(']');
            var be = mc.level == null ? null : mc.level.getBlockEntity(chestPos);
            if (be != null) sb.append(',').append(jq("type")).append(':').append(jq(be.getClass().getSimpleName()));
            if (!(be instanceof net.minecraft.world.Container)) {
                return sb.append(',').append(jq("error")).append(':').append(jq("not_a_container")).append('}').toString();
            }
            BlockPos standPos = findChestStandPos(chestPos);
            if (standPos == null) {
                return sb.append(',').append(jq("error")).append(':').append(jq("unreachable")).append('}').toString();
            }
            if (mc.getConnection() != null) {
                sendChatOnClient(mc, String.format("#goto %d %d %d", standPos.getX(), standPos.getY(), standPos.getZ()));
            }
            long start = System.currentTimeMillis();
            boolean arrived = false;
            while (System.currentTimeMillis() - start < 25000) {
                if (mc.player != null) {
                    double dx = mc.player.getX() - standPos.getX();
                    double dy = mc.player.getY() - standPos.getY();
                    double dz = mc.player.getZ() - standPos.getZ();
                    if (dx * dx + dy * dy + dz * dz <= 4.0) { arrived = true; break; }
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            sb.append(',').append(jq("arrived")).append(':').append(arrived);
            if (!arrived) {
                return sb.append(',').append(jq("error")).append(':').append(jq("no_path")).append('}').toString();
            }
            final BlockPos target = chestPos;
            mc.execute(() -> {
                try {
                    if (mc.player == null) return;
                    try { mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target)); } catch (Exception ignored) {}
                    BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
                    try { mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr); } catch (Exception ignored) {}
                } catch (Exception ignored) {}
            });
            long ws = System.currentTimeMillis();
            boolean opened = false;
            while (System.currentTimeMillis() - ws < 10000) {
                try {
                    if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                        var handler = mc.player.containerMenu;
                        int containerSlots = 0;
                        for (int si = 0; si < handler.slots.size(); si++) {
                            try { Slot s = handler.getSlot(si); if (s.container != mc.player.getInventory()) containerSlots++; } catch (Exception ignored) {}
                        }
                        if (containerSlots > 0) { opened = true; break; }
                    }
                } catch (Exception ignored) {}
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }
            if (!opened) {
                return sb.append(',').append(jq("error")).append(':').append(jq("did_not_open")).append('}').toString();
            }
            var handler = mc.player.containerMenu;
            int used = 0, total = 0;
            StringBuilder slots = new StringBuilder();
            java.util.LinkedHashMap<String, Long> containerCounts = new java.util.LinkedHashMap<>();
            try {
                for (int si = 0; si < handler.slots.size(); si++) {
                    try {
                        Slot s = handler.getSlot(si);
                        if (s.container == mc.player.getInventory()) continue;
                        total++;
                        var st = s.getItem();
                        if (st == null || st.isEmpty()) continue;
                        used++;
                        if (slots.length() > 0) slots.append(',');
                        String id = "";
                        try { id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); } catch (Exception ignored) {}
                        if (!id.isEmpty()) containerCounts.merge(id, (long) st.getCount(), Long::sum);
                        slots.append('{').append(jq("slot")).append(':').append(si)
                             .append(',').append(jq("item")).append(':').append(jq(id))
                             .append(',').append(jq("count")).append(':').append(st.getCount()).append('}');
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            closeContainerAndWait();
            // Opening a chest is a measurement of it, so publish what the menu said. The
            // host index is a record of measurements, never a running total, and this is
            // the path that keeps it honest outside a deposit.
            publishMeasuredChest(x, y, z, used, total, containerCounts);
            sb.append(',').append(jq("usedSlots")).append(':').append(used);
            sb.append(',').append(jq("totalSlots")).append(':').append(total);
            sb.append(',').append(jq("slots")).append(":[").append(slots).append(']');
            sb.append(',').append(jq("authoritative")).append(":true");
        } catch (Exception e) {
            sb.append(',').append(jq("error")).append(':').append(jq(String.valueOf(e.getMessage())));
        }
        return sb.append('}').toString();
    }

    private void writeLine(String line) {
        DataOutputStream stream = out;
        if (stream == null) return;
        try {
            synchronized (stream) { stream.writeUTF(line); stream.flush(); }
        } catch (IOException ignored) {}
    }

    private void sendStatus() {
        final int _sv = 0x726468;
        try {
            if (out == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            Player p = mc.player;
            if (p == null) return;
            WorkerStatus ws = new WorkerStatus(name, p.getX(), p.getY(), p.getZ(), p.getHealth(), p.getFoodData().getFoodLevel(), currentTask == null ? "" : currentTask);
            writeLine(ws.serialize());
            // Also send BOT_STATUS message for host parsing
                try {
                    // Build a BotStatus object and serialize it to ensure format consistency
                    com.rdh.swarm.BotStatus bs = new com.rdh.swarm.BotStatus();
                    bs.workerName = this.name;

                    // Try to read state and task info from BotBrain if available
                    String brainState = "";
                    String taskDesc = null;
                    int collected = 0;
                    int total = 0;
                    String eta = "";
                    try {
                        var brain = meteordevelopment.meteorclient.systems.modules.Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
                        if (brain != null) {
                            try { brainState = brain.getState() != null ? brain.getState().name() : "WORKER"; } catch (Exception ignored) { brainState = "WORKER"; }
                            try { taskDesc = brain.getTaskDescription(); } catch (Exception ignored) { taskDesc = null; }
                            try { bs.halted = brain.isHalted(); if (bs.halted) brainState = "PAUSED"; } catch (Exception ignored) {}
                            // BotBrain specific collection/eta getters may not exist; leave defaults
                        }
                    } catch (Throwable ignored) {}

                    bs.state = (brainState == null || brainState.isEmpty()) ? "WORKER" : brainState;
                    if (taskDesc != null) bs.taskDescription = taskDesc;
                    else bs.taskDescription = currentTask == null ? "" : currentTask;
                    bs.collected = collected;
                    bs.total = total;
                    bs.eta = eta == null ? "" : eta;

                    // Always use player values for health/food/coords
                    bs.health = p.getHealth();
                    bs.food = p.getFoodData().getFoodLevel();
                    bs.x = p.getX(); bs.y = p.getY(); bs.z = p.getZ();

                    try { bs.tier = com.rdh.swarm.task.DependencyResolver.getCurrentTier().name(); } catch (Exception ignored) { bs.tier = "Unknown"; }

                    writeLine(bs.serialize());
                } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        PathManagers.get().stop();
        ChatUtils.infoPrefix("Swarm", "Temporarily disconnected from host (will retry).");
        interrupt();
    }

    public void shutdown() {
        shouldReconnect = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        PathManagers.get().stop();
        ChatUtils.infoPrefix("Swarm", "Worker permanently disconnected from host.");
        interrupt();
    }

    public String getConnection() {
        return (socket == null ? "" : socket.getInetAddress().getHostAddress()) + ":" + (socket == null ? port : socket.getPort());
    }
}
