package com.rdh.swarm;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import com.rdh.swarm.task.BotTask;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import java.util.List;
import java.util.Map;

public class EnhancedSwarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Persisted deposit chest coordinates (optional)
    public final Setting<Integer> depositXSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("deposit-x")
        .description("Saved deposit chest X coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );

    public final Setting<Integer> depositYSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("deposit-y")
        .description("Saved deposit chest Y coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );

    public final Setting<Integer> depositZSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("deposit-z")
        .description("Saved deposit chest Z coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );
    // Persisted explicit base anchor coordinates (optional)
    public final Setting<Integer> baseXSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("base-x")
        .description("Saved explicit base anchor X coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );

    public final Setting<Integer> baseYSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("base-y")
        .description("Saved explicit base anchor Y coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );

    public final Setting<Integer> baseZSetting = sgGeneral.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("base-z")
        .description("Saved explicit base anchor Z coordinate")
        .defaultValue(Integer.MIN_VALUE)
        .build()
    );
    private static final int[] _nq = {102,108,97,109,105,110,103,99,104,101,101,116,48,115,47,114,100,104};

    public final Setting<Boolean> autoDepositSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-deposit")
        .description("Workers auto-deposit when inventory is nearly full")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> autoDepositThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("auto-deposit-threshold")
        .description("Empty slots remaining to trigger auto-deposit")
        .defaultValue(2)
        .range(0, 10)
        .build()
    );
    private static final String _u0 = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073\u002F\u0072\u0064\u0068\u0020\u0066\u006F\u0072\u0065\u0076\u0065\u0072";

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What type of client to run.")
        .defaultValue(Mode.Host)
        .build()
    );

    private final Setting<String> ipAddress = sgGeneral.add(new StringSetting.Builder()
        .name("ip")
        .description("The IP address of the host server.")
        .defaultValue("localhost")
        .visible(() -> mode.get() == Mode.Worker)
        .build()
    );

    private final Setting<Integer> serverPort = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("The port used for connections.")
        .defaultValue(6969)
        .range(1, 65535)
        .noSlider()
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Automatically reconnect if a worker connection drops.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Worker)
        .build()
    );

    public EnhancedSwarmHost host;
    public EnhancedSwarmWorker worker;
    private String selectedTarget = "All";
    public final Setting<Boolean> pvpEnabled = sgGeneral.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("pvp-enabled")
        .description("PVP mode persisted state (internal)")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    public EnhancedSwarm() {
        super(EnhancedSwarmAddon.CATEGORY, "enhanced-swarm", "Enhanced Swarm - individual targeting, two-way comms, auto-reconnect");
        final String _cx = BuildInfo.z();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        // Start/Stop row
        WHorizontalList b = list.add(theme.horizontalList()).expandX().widget();

        WButton start = b.add(theme.button("Start")).expandX().widget();
        start.action = () -> {
            if (!isActive()) return;
            close();
            if (mode.get() == Mode.Host) {
                host = new EnhancedSwarmHost(serverPort.get());
                try {
                    if (host != null && depositXSetting.get() != Integer.MIN_VALUE) {
                        host.setDepositChest(depositXSetting.get(), depositYSetting.get(), depositZSetting.get());
                    }
                    if (host != null && baseXSetting.get() != Integer.MIN_VALUE) {
                        host.setBaseAnchor(baseXSetting.get(), baseYSetting.get(), baseZSetting.get());
                    }
                } catch (Exception ignored) {}
            } else {
                worker = new EnhancedSwarmWorker(ipAddress.get(), serverPort.get(), autoReconnect.get());
            }
        };

        WButton stop = b.add(theme.button("Stop")).expandX().widget();
        stop.action = () -> {
            if (mode.get() == Mode.Host) {
                close();
            } else if (worker != null) {
                worker.shutdown();
                worker = null;
            }
        };

        // Host control panel (always visible in Host mode)
        if (mode.get() == Mode.Host) {
            list.add(theme.horizontalSeparator()).expandX();

            // Worker selector dropdown
            WHorizontalList selRow = list.add(theme.horizontalList()).expandX().widget();
            selRow.add(theme.label("Target: "));

            String[] workerNames;
            try {
                workerNames = host != null ? host.getWorkerNames() : new String[0];
            } catch (Exception e) {
                workerNames = new String[0];
            }
            String[] options = new String[workerNames.length + 1];
            options[0] = "All";
            System.arraycopy(workerNames, 0, options, 1, workerNames.length);

            boolean found = false;
            for (String opt : options) {
                if (opt.equalsIgnoreCase(selectedTarget)) { found = true; break; }
            }
            if (!found) selectedTarget = "All";

            WDropdown<String> dropdown = selRow.add(theme.dropdown(options, selectedTarget)).expandX().widget();
            dropdown.action = () -> {
                selectedTarget = dropdown.get();
            };

            WButton syncBtn = selRow.add(theme.button("Sync From Worker")).widget();
            syncBtn.action = () -> {
                if (host == null || !isHost()) return;
                if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all sync-settings");
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " sync-settings");
            };

            // Row 1: Follow Me, Stop Task, Come Here
            WHorizontalList row1 = list.add(theme.horizontalList()).expandX().widget();

            WButton followBtn = row1.add(theme.button("Follow Me")).expandX().widget();
            followBtn.action = () -> {
                if (host == null || !isHost()) return;
                String myName = mc.getUser().getName();
                String cmd = "follow player " + myName;
                if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all " + cmd);
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " " + cmd);
            };

            WButton stopBtn = row1.add(theme.button("Stop Task")).expandX().widget();
            stopBtn.action = () -> {
                if (host == null || !isHost()) return;
                if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all stop");
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " stop");
            };

            WButton comeBtn = row1.add(theme.button("Come Here")).expandX().widget();
            comeBtn.action = () -> {
                if (host == null || !isHost() || mc.player == null) return;
                BlockPos pos = mc.player.blockPosition();
                String cmd = "goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all " + cmd);
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " " + cmd);
            };

            // Row 2: Deposit Keep, Deposit All
            WHorizontalList row2 = list.add(theme.horizontalList()).expandX().widget();

            WButton depKeep = row2.add(theme.button("Deposit Keep")).expandX().widget();
            depKeep.action = () -> {
                if (host == null || !isHost()) return;
                if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all deposit");
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " deposit");
            };

            WButton depAll = row2.add(theme.button("Deposit All")).expandX().widget();
            depAll.action = () -> {
                if (host == null || !isHost()) return;
                if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all depositall");
                else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " depositall");
            };
            // --- Deposit Management Section ---
            WSection depositSection = list.add(theme.section("Deposit Chests", false)).expandX().widget();

            String currentServer = "unknown";
            try { if (host != null) currentServer = host.getCurrentServer(); } catch (Exception ignored) {}
            depositSection.add(theme.label("Server: " + currentServer)).expandX();

            try {
                if (host != null) {
                    Map<String, List<int[]>> chests = host.getDepositConfig().getChests(host.getCurrentServer());
                    if (chests.isEmpty()) {
                        depositSection.add(theme.label("  No chests configured")).expandX();
                    } else {
                        for (var entry : chests.entrySet()) {
                            List<int[]> positions = entry.getValue();
                            for (int i = 0; i < positions.size(); i++) {
                                int[] pos = positions.get(i);
                                WHorizontalList chestRow = depositSection.add(theme.horizontalList()).expandX().widget();
                                chestRow.add(theme.label(String.format("  %s[%d]: %d, %d, %d", entry.getKey(), i+1, pos[0], pos[1], pos[2]))).expandX().widget();
                                WButton removeBtn = chestRow.add(theme.button("X")).widget();
                                final String catToRemove = entry.getKey();
                                final int idx = i + 1;
                                removeBtn.action = () -> {
                                    if (host == null) return;
                                    host.getDepositConfig().removeChest(host.getCurrentServer(), catToRemove, idx);
                                    try { ChatUtils.infoPrefix("Swarm", "Removed %s[%d]", catToRemove, idx); } catch (Exception ignored) {}
                                };
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            depositSection.add(theme.horizontalSeparator()).expandX();

            WHorizontalList addChestRow = depositSection.add(theme.horizontalList()).expandX().widget();
            addChestRow.add(theme.label("Category: "));
            List<String> catNames = DepositConfig.getCategoryNames();
            String[] catArray = catNames.toArray(new String[0]);
            WDropdown<String> catDropdown = addChestRow.add(theme.dropdown(catArray, "all")).expandX().widget();

            WButton setChestBtn = addChestRow.add(theme.button("Set (Crosshair)")).widget();
            setChestBtn.action = () -> {
                if (host == null || !isHost() || mc.player == null) return;
                try {
                    if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                        BlockPos bp = bhr.getBlockPos();
                        String cat = catDropdown.get();
                        host.setDepositChest(cat, bp.getX(), bp.getY(), bp.getZ());
                        depositXSetting.set(bp.getX());
                        depositYSetting.set(bp.getY());
                        depositZSetting.set(bp.getZ());
                    } else {
                        ChatUtils.warningPrefix("Swarm", "Look at a chest block first");
                    }
                } catch (Exception e) {
                    ChatUtils.errorPrefix("Swarm", "Failed to set chest: %s", e.getMessage());
                }
            };

            WHorizontalList manualRow = depositSection.add(theme.horizontalList()).expandX().widget();
            WTextBox coordBox = manualRow.add(theme.textBox("", "x y z")).expandX().widget();
            WButton setManualBtn = manualRow.add(theme.button("Set Coords")).widget();
            setManualBtn.action = () -> {
                if (host == null || !isHost()) return;
                String[] parts = coordBox.get().trim().split("\\s+");
                if (parts.length < 3) { ChatUtils.errorPrefix("Swarm", "Enter x y z"); return; }
                try {
                    int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
                    host.setDepositChest(catDropdown.get(), x, y, z);
                    depositXSetting.set(x); depositYSetting.set(y); depositZSetting.set(z);
                } catch (NumberFormatException e) {
                    ChatUtils.errorPrefix("Swarm", "Invalid coordinates");
                }
            };

            depositSection.add(theme.horizontalSeparator()).expandX();

            WHorizontalList autoRow = depositSection.add(theme.horizontalList()).expandX().widget();
            autoRow.add(theme.label("Auto-Deposit: "));
            meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox autoCheck = autoRow.add(theme.checkbox(autoDepositSetting.get())).widget();
            autoCheck.action = () -> {
                autoDepositSetting.set(autoCheck.checked);
                sendToTarget("deposit auto " + (autoCheck.checked ? "on" : "off"));
            };

            autoRow.add(theme.label(" Threshold: "));
            WTextBox threshBox = autoRow.add(theme.textBox(String.valueOf(autoDepositThreshold.get()), "")).widget();
            WButton threshBtn = autoRow.add(theme.button("Set")).widget();
            threshBtn.action = () -> {
                try {
                    int t = Integer.parseInt(threshBox.get().trim());
                    autoDepositThreshold.set(t);
                    sendToTarget("deposit threshold " + t);
                } catch (NumberFormatException e) {
                    ChatUtils.errorPrefix("Swarm", "Invalid threshold");
                }
            };

            WButton pushBtn = depositSection.add(theme.button("Push Config to Workers")).expandX().widget();
            pushBtn.action = () -> {
                if (host == null || !isHost()) return;
                host.pushDepositConfigAll();
                if (autoDepositSetting.get()) sendToTarget("deposit auto on");
                sendToTarget("deposit threshold " + autoDepositThreshold.get());
                ChatUtils.infoPrefix("Swarm", "Pushed deposit config to all workers");
            };

            WHorizontalList catBtnRow = depositSection.add(theme.horizontalList()).expandX().widget();
            WButton depOres = catBtnRow.add(theme.button("Dep. Ores")).expandX().widget();
            depOres.action = () -> sendToTarget("deposit ores");
            WButton depShulk = catBtnRow.add(theme.button("Dep. Shulkers")).expandX().widget();
            depShulk.action = () -> sendToTarget("deposit shulkers");
            WButton depBuild = catBtnRow.add(theme.button("Dep. Building")).expandX().widget();
            depBuild.action = () -> sendToTarget("deposit building");
            WButton depVals = catBtnRow.add(theme.button("Dep. Valuables")).expandX().widget();
            depVals.action = () -> sendToTarget("deposit valuables");
            // --- Raw Command AABB ---
            list.add(theme.horizontalSeparator()).expandX();
            WHorizontalList cmdRow = list.add(theme.horizontalList()).expandX().widget();
            WTextBox cmdBox = cmdRow.add(theme.textBox("", "Enter command...")).expandX().widget();
            WButton sendBtn = cmdRow.add(theme.button("Send")).widget();
            sendBtn.action = () -> {
                if (host == null || !isHost()) return;
                String text = cmdBox.get().trim();
                if (text.isEmpty()) return;

                String payload;
                if (text.startsWith("#") || text.startsWith(".")) {
                    payload = "exec " + text;
                } else {
                    payload = text;
                }

                if (selectedTarget.equalsIgnoreCase("All")) {
                    host.sendAll("swarm:all " + payload);
                } else {
                    host.sendTo(selectedTarget, "swarm:" + selectedTarget + " " + payload);
                }

                try { ChatUtils.infoPrefix("Swarm", "Sent to %s: %s", selectedTarget, payload); } catch (Exception ignored) {}
                try { cmdBox.set(""); } catch (Exception ignored) {}
            };

            // --- PVP Mode Section ---
            WSection pvpSection = list.add(theme.section("PVP Mode", false)).expandX().widget();

            WButton pvpToggle = pvpSection.add(theme.button(pvpEnabled.get() ? "DISABLE ALL PVP" : "ENABLE ALL PVP")).expandX().widget();
            pvpToggle.action = () -> {
                if (host == null || !isHost()) return;
                boolean enable = !pvpEnabled.get();
                pvpEnabled.set(enable);

                String[] modules = {"crystal-aura", "kill-aura", "auto-totem", "surround", "auto-trap", "auto-web", "auto-exp"};

                MeteorExecutor.execute(() -> {
                    for (String mod : modules) {
                        try {
                            String cmd = "exec .toggle " + mod + " " + (enable ? "on" : "off");
                            if (selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all " + cmd);
                            else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " " + cmd);
                        } catch (Exception ignored) {}
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    }
                });

                try { ChatUtils.infoPrefix("Swarm", "PVP Mode %s for %s", pvpEnabled.get() ? "ENABLED" : "DISABLED", selectedTarget); } catch (Exception ignored) {}
            };

            // Separator
            pvpSection.add(theme.horizontalSeparator()).expandX();

            // --- Task Manager Section ---
            WSection taskSection = list.add(theme.section("Task Manager", false)).expandX().widget();
            // Connected bots label
            int connCount = host != null ? host.getConnectionCount() : 0;
            taskSection.add(theme.label("Workers: " + connCount + " connected")).expandX();

            try {
                String[] wn = host != null ? host.getWorkerNames() : new String[0];
                for (String workerName : wn) {
                    WHorizontalList wrow = taskSection.add(theme.horizontalList()).expandX().widget();
                    String nick = "";
                    try { if (host != null && host.getTaskQueue() != null) nick = host.getTaskQueue().getNickname(workerName); } catch (Exception ignored) {}
                    BotStatus bs = null;
                    try { if (host != null) bs = host.getBotStatus(workerName); } catch (Exception ignored) {}

                    wrow.add(theme.label(nick.isEmpty() ? workerName : nick)).widget();
                    wrow.add(theme.label(bs != null && bs.isOnline() ? "●" : "○")).widget();
                    wrow.add(theme.label(bs != null ? (bs.taskDescription != null ? bs.taskDescription : "Idle") : "No status")).expandX().widget();
                    wrow.add(theme.label(bs != null ? bs.getProgressString() : "")).widget();

                    WTextBox nickBox = wrow.add(theme.textBox("", "nickname")).widget();
                    WButton setNick = wrow.add(theme.button("Set Nick")).widget();
                    String finalWorkerName = workerName;
                    setNick.action = () -> {
                        if (host == null) return;
                        String v = nickBox.get().trim();
                        try { host.getTaskQueue().setNickname(finalWorkerName, v); ChatUtils.infoPrefix("Swarm", "Set nickname for %s => %s", finalWorkerName, v); } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed to set nickname: %s", e.getMessage()); }
                    };
                }
            } catch (Exception ignored) {}

            taskSection.add(theme.horizontalSeparator()).expandX();

            // Task queue list
            try {
                if (host != null && host.getTaskQueue() != null) {
                    java.util.List<BotTask> tasks = host.getTaskQueue().getAllTasks();
                    for (BotTask t : tasks) {
                        WHorizontalList tq = taskSection.add(theme.horizontalList()).expandX().widget();
                        tq.add(theme.label(t.getItemName())).widget();
                        tq.add(theme.label(t.getProgressString())).widget();
                        tq.add(theme.label(t.getStatus().name())).widget();
                        tq.add(theme.label(t.getAssignedBot() == null ? "" : t.getAssignedBot())).widget();
                        WButton rem = tq.add(theme.button("X")).widget();
                        java.util.UUID tid = t.getId();
                        rem.action = () -> { try { host.getTaskQueue().removeById(tid); ChatUtils.infoPrefix("Swarm", "Removed task %s", tid.toString()); } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed remove task: %s", e.getMessage()); } };
                    }
                }
            } catch (Exception ignored) {}

            taskSection.add(theme.horizontalSeparator()).expandX();

            // Add task row
            WHorizontalList addRow = taskSection.add(theme.horizontalList()).expandX().widget();
            WTextBox itemBox = addRow.add(theme.textBox("", "item (e.g. oak_log)")).expandX().widget();
            WTextBox qtyBox = addRow.add(theme.textBox("", "qty")).widget();
            String[] botOptions = new String[ (host != null ? host.getWorkerNames().length : 0) + 1];
            botOptions[0] = "All";
            try { if (host != null) System.arraycopy(host.getWorkerNames(), 0, botOptions, 1, host.getWorkerNames().length); } catch (Exception ignored) {}
            WDropdown<String> botDrop = addRow.add(theme.dropdown(botOptions, "All")).widget();
            WButton addBtn = addRow.add(theme.button("Add Task")).widget();
            addBtn.action = () -> {
                if (host == null) return;
                String itemStr = itemBox.get().trim();
                int qty = 1;
                try { qty = Integer.parseInt(qtyBox.get().trim()); } catch (Exception ignored) {}
                try {
                    Identifier id = Identifier.tryParse(itemStr);
                    net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.getValue(id);
                    BotTask nt = new BotTask(BotTask.Type.GATHER, it, qty);
                    host.getTaskQueue().addTask(nt);
                    String sel = botDrop.get();
                    String target = null;
                    if (sel != null && !sel.equalsIgnoreCase("All") && !sel.isEmpty()) {
                        target = sel;
                        try { nt.setAssignedBot(sel); } catch (Exception ignored) {}
                    }
                    try { host.dispatchNextTask(target == null ? (host.getWorkerNames().length > 0 ? host.getWorkerNames()[0] : "") : target); } catch (Exception ignored) {}
                    ChatUtils.infoPrefix("Swarm", "Added task %s %s x%d", nt.getId().toString(), itemStr, qty);
                } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Invalid item: %s", itemStr); }
            };

            taskSection.add(theme.horizontalSeparator()).expandX();

            // Base management row
            WHorizontalList baseRow = taskSection.add(theme.horizontalList()).expandX().widget();
            WButton scanBtn = baseRow.add(theme.button("Scan Chests")).widget();
            scanBtn.action = () -> { if (host != null) host.sendAll("swarm:all scan-chests"); };
            WButton listBtn = baseRow.add(theme.button("List Resources")).widget();
            listBtn.action = () -> {
                if (host == null) return;
                try {
                    java.util.List<BotTask> tasks = host.getTaskQueue().getAllTasks();
                    if (tasks.isEmpty()) { ChatUtils.infoPrefix("Swarm", "No tasks"); return; }
                    for (BotTask t : tasks) ChatUtils.info("%s %s (%s) assigned=%s", t.getId().toString(), t.getItemName(), t.getProgressString(), t.getAssignedBot());
                } catch (Exception e) { ChatUtils.errorPrefix("Swarm", "Failed listing resources: %s", e.getMessage()); }
            };

            taskSection.add(theme.horizontalSeparator()).expandX();

            // Quick task buttons
            WHorizontalList quick = taskSection.add(theme.horizontalList()).expandX().widget();
            WButton q1 = quick.add(theme.button("64 Oak Logs")).widget();
            q1.action = () -> { createQuickTask("oak_log", 64); };
            WButton q2 = quick.add(theme.button("Stack Iron")).widget();
            q2.action = () -> { createQuickTask("iron_ingot", 64); };
            WButton q3 = quick.add(theme.button("32 Diamonds")).widget();
            q3.action = () -> { createQuickTask("diamond", 32); };

            // helper lambda (method inlined) to create quick tasks
            Runnable dummy = () -> {};
            // local method emulation: createQuickTask
            // We'll use a small inner helper via theme.listener not available; instead, add a private runnable using reflection is unnecessary.
            // For simplicity, use direct code in each button above.

            // PVP module toggles (4 buttons per row: A ON | A OFF | B ON | B OFF)
            WHorizontalList pRow1 = pvpSection.add(theme.horizontalList()).expandX().widget();
            WButton caOn = pRow1.add(theme.button("Crystal Aura ON")).widget();
            WButton caOff = pRow1.add(theme.button("Crystal Aura OFF")).widget();
            WButton kaOn = pRow1.add(theme.button("Kill Aura ON")).widget();
            WButton kaOff = pRow1.add(theme.button("Kill Aura OFF")).widget();
            caOn.action = () -> {
                sendToTarget("exec .toggle crystal-aura on");
                try {
                    if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) {
                        for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("crystal-aura", true);
                    } else {
                        WorkerConfig wc = host.getOrCreateConfig(selectedTarget);
                        if (wc != null) wc.setModuleState("crystal-aura", true);
                    }
                } catch (Exception ignored) {}
            };
            caOff.action = () -> {
                sendToTarget("exec .toggle crystal-aura off");
                try {
                    if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) {
                        for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("crystal-aura", false);
                    } else {
                        WorkerConfig wc = host.getOrCreateConfig(selectedTarget);
                        if (wc != null) wc.setModuleState("crystal-aura", false);
                    }
                } catch (Exception ignored) {}
            };
            kaOn.action = () -> {
                sendToTarget("exec .toggle kill-aura on");
                try {
                    if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) {
                        for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("kill-aura", true);
                    } else {
                        WorkerConfig wc = host.getOrCreateConfig(selectedTarget);
                        if (wc != null) wc.setModuleState("kill-aura", true);
                    }
                } catch (Exception ignored) {}
            };
            kaOff.action = () -> {
                sendToTarget("exec .toggle kill-aura off");
                try {
                    if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) {
                        for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("kill-aura", false);
                    } else {
                        WorkerConfig wc = host.getOrCreateConfig(selectedTarget);
                        if (wc != null) wc.setModuleState("kill-aura", false);
                    }
                } catch (Exception ignored) {}
            };

            WHorizontalList pRow2 = pvpSection.add(theme.horizontalList()).expandX().widget();
            WButton atOn = pRow2.add(theme.button("Auto Totem ON")).widget();
            WButton atOff = pRow2.add(theme.button("Auto Totem OFF")).widget();
            WButton surOn = pRow2.add(theme.button("Surround ON")).widget();
            WButton surOff = pRow2.add(theme.button("Surround OFF")).widget();
            atOn.action = () -> {
                sendToTarget("exec .toggle auto-totem on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-totem", true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-totem", true); } } catch (Exception ignored) {}
            };
            atOff.action = () -> {
                sendToTarget("exec .toggle auto-totem off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-totem", false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-totem", false); } } catch (Exception ignored) {}
            };
            surOn.action = () -> {
                sendToTarget("exec .toggle surround on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("surround", true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("surround", true); } } catch (Exception ignored) {}
            };
            surOff.action = () -> {
                sendToTarget("exec .toggle surround off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("surround", false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("surround", false); } } catch (Exception ignored) {}
            };

            WHorizontalList pRow3 = pvpSection.add(theme.horizontalList()).expandX().widget();
            WButton atpOn = pRow3.add(theme.button("Auto Trap ON")).widget();
            WButton atpOff = pRow3.add(theme.button("Auto Trap OFF")).widget();
            WButton awOn = pRow3.add(theme.button("Auto Web ON")).widget();
            WButton awOff = pRow3.add(theme.button("Auto Web OFF")).widget();
            atpOn.action = () -> {
                sendToTarget("exec .toggle auto-trap on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-trap", true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-trap", true); } } catch (Exception ignored) {}
            };
            atpOff.action = () -> {
                sendToTarget("exec .toggle auto-trap off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-trap", false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-trap", false); } } catch (Exception ignored) {}
            };
            awOn.action = () -> {
                sendToTarget("exec .toggle auto-web on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-web", true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-web", true); } } catch (Exception ignored) {}
            };
            awOff.action = () -> {
                sendToTarget("exec .toggle auto-web off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-web", false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-web", false); } } catch (Exception ignored) {}
            };

            WHorizontalList pRow4 = pvpSection.add(theme.horizontalList()).expandX().widget();
            WButton aexpOn = pRow4.add(theme.button("Auto Exp ON")).widget();
            WButton aexpOff = pRow4.add(theme.button("Auto Exp OFF")).widget();
            aexpOn.action = () -> {
                sendToTarget("exec .toggle auto-exp on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-exp", true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-exp", true); } } catch (Exception ignored) {}
            };
            aexpOff.action = () -> {
                sendToTarget("exec .toggle auto-exp off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState("auto-exp", false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState("auto-exp", false); } } catch (Exception ignored) {}
            };

            // Separator
            pvpSection.add(theme.horizontalSeparator()).expandX();

            // Custom Module Toggle
            WHorizontalList customToggleRow = pvpSection.add(theme.horizontalList()).expandX().widget();
            customToggleRow.add(theme.label("Custom Module Toggle:"));
            WTextBox customModBox = customToggleRow.add(theme.textBox("", "module-name")).widget();
            WButton customOnBtn = customToggleRow.add(theme.button("ON")).widget();
            WButton customOffBtn = customToggleRow.add(theme.button("OFF")).widget();
            customOnBtn.action = () -> {
                String m = customModBox.get().trim(); if (m.isEmpty()) return; sendToTarget("exec .toggle " + m + " on");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState(m, true); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState(m, true); } } catch (Exception ignored) {}
            };
            customOffBtn.action = () -> {
                String m = customModBox.get().trim(); if (m.isEmpty()) return; sendToTarget("exec .toggle " + m + " off");
                try { if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) { for (WorkerConfig c : host.getAllConfigs()) c.setModuleState(m, false); } else { WorkerConfig wc = host.getOrCreateConfig(selectedTarget); if (wc != null) wc.setModuleState(m, false); } } catch (Exception ignored) {}
            };

            // Baritone settings removed

            WSection buildSec = list.add(theme.section("Building", false)).expandX().widget();
            WHorizontalList rb;

            // Baritone settings UI removed.
        }

        try { BuildInfo.z(); } catch (Exception _e0) {}
        return list;
    }

    // signature bytes
    private static final byte[] _bx = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};

    @Override
    public String getInfoString() {
        final String _qi = new String(new byte[]{0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73,0x2F,0x72,0x64,0x68});
        if (isHost()) return "Host (" + host.getConnectionCount() + ")";
        if (isWorker()) return "Worker";
        return mode.get().name();
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Host) {
            if (host == null) host = new EnhancedSwarmHost(serverPort.get());
            if (host != null) {
                MeteorExecutor.execute(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    if (host != null) {
                        host.pushDepositConfigAll();
                        if (autoDepositSetting.get()) {
                            host.sendAll("swarm:all deposit auto on");
                            host.sendAll("swarm:all deposit threshold " + autoDepositThreshold.get());
                        }
                    }
                });
            }
        } else {
            if (worker == null) worker = new EnhancedSwarmWorker(ipAddress.get(), serverPort.get(), autoReconnect.get());
        }
    }

    @Override
    public void onDeactivate() {
        // ONLY called when user manually disables the module
        if (host != null) {
            host.disconnect();
            host = null;
        }
        if (worker != null) {
            worker.shutdown();  // permanent kill ONLY on manual disable
            worker = null;
        }
    }

    public void close() {
        // Only used by the Start/Stop GUI buttons
        if (host != null) {
            host.disconnect();
            host = null;
        }
        // DO NOT touch the worker here. The worker manages itself.
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Host stops on game leave
        if (mode.get() == Mode.Host && host != null) {
            host.disconnect();
            host = null;
        }
        // Workers: DO NOTHING. The TCP connection to the swarm host
        // has nothing to do with the Minecraft server connection.
        // The worker thread stays alive and keeps reconnecting.
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        if (!isActive()) return;
        if (mode.get() == Mode.Host && host == null) {
            host = new EnhancedSwarmHost(serverPort.get());
        }
        // Workers: DO NOTHING. Already running.
    }

    // DO NOT override toggle(). Let the parent Module class handle it.

    public boolean isHost() {
        return mode.get() == Mode.Host && host != null && !host.isInterrupted();
    }

    public boolean isWorker() {
        return mode.get() == Mode.Worker && worker != null && !worker.isInterrupted();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isWorker()) worker.tick();
    }

    public enum Mode {
        Host,
        Worker
    }

    // Helper to centralize sending commands to selected target
    private void sendToTarget(String payload) {
        if (host == null || !isHost()) return;
        try {
            if (selectedTarget == null || selectedTarget.equalsIgnoreCase("All")) host.sendAll("swarm:all " + payload);
            else host.sendTo(selectedTarget, "swarm:" + selectedTarget + " " + payload);
        } catch (Exception ignored) {}
    }

    private static final long _ep = 0x666C616D696E67L;
    private static final int _zc = 0x726468;

    // Helper to add a labeled integer row using WIntEdit; sends value on release
    private void addNumberRow(WSection sec, GuiTheme theme, String name) {
        // Baritone number setting removed — placeholder row kept for layout
        WHorizontalList row = sec.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(name + ": (removed) ")).widget();
    }

    // Helper to add a labeled double row using WDoubleEdit; sends value on release
    private void addDoubleRow(WSection sec, GuiTheme theme, String name) {
        // Baritone double setting removed — placeholder row kept for layout
        WHorizontalList row = sec.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(name + ": (removed) ")).widget();
    }

    private void createQuickTask(String itemStr, int qty) {
        if (host == null) return;
        try {
            Identifier id = Identifier.tryParse(itemStr);
            net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.getValue(id);
            BotTask nt = new BotTask(BotTask.Type.GATHER, it, qty);
            host.getTaskQueue().addTask(nt);
            if (selectedTarget != null && !selectedTarget.equalsIgnoreCase("All")) {
                host.assignTask(selectedTarget, nt);
                nt.setAssignedBot(selectedTarget);
            }
            ChatUtils.infoPrefix("Swarm", "Added quick task %s %s x%d", nt.getId().toString(), itemStr, qty);
        } catch (Exception e) {
            ChatUtils.errorPrefix("Swarm", "Invalid item: %s", itemStr);
        }
    }
}
