package com.rdh.swarm.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.Commands;
import net.minecraft.client.Minecraft;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.rdh.swarm.DepositConfig;
import com.rdh.swarm.EnhancedSwarm;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.task.TaskQueue;
import com.rdh.swarm.SwarmWaypoint;
import com.rdh.swarm.WaypointManager;
import net.minecraft.world.level.Level;

public class SwarmCommand extends Command {
    private static final String _xo = "\u0066\u006C\u0061\u006D\u0069\u006E\u0067\u0063\u0068\u0065\u0065\u0074\u0030\u0073\u002F\u0072\u0064\u0068";
    private static final int[] _xk = {102,108,97,109,105,110,103};
    private static final byte[] _xb = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73
    };
    private static final int _xc = 0x726468;
    public SwarmCommand() { this("eswarm"); }

    public SwarmCommand(String name) {
        super(name, "Enhanced swarm control");
    }

    private static final SuggestionProvider<ClientSuggestionProvider> TARGET_SUGGESTIONS = (CommandContext<ClientSuggestionProvider> ctx, SuggestionsBuilder builder) -> {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod != null && mod.isHost() && mod.host != null) {
            builder.suggest("all");
            for (String n : mod.host.getWorkerNames()) builder.suggest(n);
        } else {
            builder.suggest("all");
        }
        return CompletableFuture.completedFuture(builder.build());
    };

    private static final SuggestionProvider<ClientSuggestionProvider> CATEGORY_SUGGESTIONS = (CommandContext<ClientSuggestionProvider> ctx, SuggestionsBuilder builder) -> {
        for (String cat : DepositConfig.getCategoryNames()) builder.suggest(cat);
        return CompletableFuture.completedFuture(builder.build());
    };

    private static final SuggestionProvider<ClientSuggestionProvider> COMMAND_SUGGESTIONS = (CommandContext<ClientSuggestionProvider> ctx, SuggestionsBuilder builder) -> {
        String[] names = getRegisteredCommandNames();
        String remaining = builder.getRemaining().trim();
        if (!remaining.isEmpty()) {
            String[] parts = remaining.split("\\s+");
            String first = parts.length > 0 ? parts[0] : "";
            String secondPartial = parts.length > 1 ? parts[1] : "";

            if (first.equalsIgnoreCase("mine")) {
                String[] commonBlocks = {"diamond_ore", "iron_ore", "coal_ore", "oak_log", "stone", "dirt", "gravel", "sand"};
                for (String b : commonBlocks) {
                    if (secondPartial.isEmpty() || b.startsWith(secondPartial)) builder.suggest("mine " + b);
                }
                return CompletableFuture.completedFuture(builder.build());
            }
            if (first.equalsIgnoreCase("follow")) {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.level != null) {
                        mc.level.players().forEach(p -> {
                            String pname = p.getName().getString();
                            if (secondPartial.isEmpty() || pname.toLowerCase().startsWith(secondPartial.toLowerCase())) builder.suggest("follow " + pname);
                        });
                        return CompletableFuture.completedFuture(builder.build());
                    }
                } catch (Throwable ignored) {}
            }
        }
        for (String n : names) builder.suggest(n);
        return CompletableFuture.completedFuture(builder.build());
    };

    private static String[] getRegisteredCommandNames() {
        try {
            Class<?> cls = Class.forName("meteordevelopment.meteorclient.commands.Commands");
            try {
                java.lang.reflect.Method m = cls.getMethod("getCommands");
                Object res = m.invoke(null);
                if (res instanceof java.util.Map) {
                    return ((java.util.Map<?,?>) res).keySet().stream().map(Object::toString).toArray(String[]::new);
                }
            } catch (NoSuchMethodException ignored) {}
            try {
                java.lang.reflect.Method get = cls.getMethod("get");
                Object inst = get.invoke(null);
                if (inst != null) {
                    try {
                        java.lang.reflect.Method m2 = inst.getClass().getMethod("getCommands");
                        Object res2 = m2.invoke(inst);
                        if (res2 instanceof java.util.Map) {
                            return ((java.util.Map<?,?>) res2).keySet().stream().map(Object::toString).toArray(String[]::new);
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return new String[]{"goto", "mine", "follow", "stop", "say", "where", "tp", "help", "scatter", "gather", "stay", "resume", "deposit", "depositall", "exec"};
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // ---- halt/resume ----
        builder.then(literal("cancel").executes(context -> haltAll()));
        builder.then(literal("stop").executes(context -> haltAll()));
        builder.then(literal("resume").executes(context -> resumeAll()));

        // ---- exec ----
        builder.then(literal("exec").then(argument("command", StringArgumentType.greedyString()).executes(context -> {
            String command = StringArgumentType.getString(context, "command");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);

            if (mod != null && mod.isHost()) {
                mod.host.sendAll("swarm:all exec " + command);
                info("Broadcast exec: %s", command);
                return SINGLE_SUCCESS;
            }

            try {
                if (command.startsWith("#")) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getConnection() != null) mc.getConnection().sendChat(command);
                } else {
                    Commands.dispatch(command);
                }
            } catch (Exception e) { error("Error: %s", e.getMessage()); }
            return SINGLE_SUCCESS;
        })));

        // ---- list ----
        builder.then(literal("list").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost()) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.listWorkers();
            return SINGLE_SUCCESS;
        }));

        // ---- send ----
        LiteralArgumentBuilder<ClientSuggestionProvider> send = literal("send");
        send.then(argument("target", StringArgumentType.word()).suggests(TARGET_SUGGESTIONS)
            .then(argument("command", StringArgumentType.greedyString()).suggests(COMMAND_SUGGESTIONS).executes(context -> {
                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost()) { error("Not hosting"); return SINGLE_SUCCESS; }

                String target = StringArgumentType.getString(context, "target");
                String command = StringArgumentType.getString(context, "command");

                if (target.equalsIgnoreCase("all")) {
                    mod.host.sendAll("swarm:all " + command);
                    info("Sent to all: %s", command);
                } else {
                    mod.host.sendTo(target, "swarm:" + target + " " + command);
                    info("Sent to %s: %s", target, command);
                }
                return SINGLE_SUCCESS;
            })));
        builder.then(send);

        // ---- task ----
        LiteralArgumentBuilder<ClientSuggestionProvider> task = literal("task");

        // add: .eswarm task add <item> <quantity> [bot]
        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, String> itemArg = argument("item", StringArgumentType.word());
        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, Integer> qtyArg = argument("quantity", IntegerArgumentType.integer(1));

        qtyArg.executes(context -> {
            String item = StringArgumentType.getString(context, "item");
            int qty = IntegerArgumentType.getInteger(context, "quantity");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

            try {
                net.minecraft.world.item.Item it = resolveKnownItem(item);
                if (it == null) {
                    error("unknown item '%s', not queued", item);
                    return SINGLE_SUCCESS;
                }
                BotTask t = new BotTask(BotTask.Type.GATHER, it, qty);
                if (!com.rdh.swarm.task.DependencyResolver.hasKnownSource(it)) {
                    t.setStatus(BotTask.Status.FAILED);
                    t.setFailureReason("no known source");
                    mod.host.getTaskQueue().add(t);
                    info("TASK: %s failed — no known source", t.getId());
                    return SINGLE_SUCCESS;
                }
                mod.host.getTaskQueue().add(t);
                info("Added task %s (%s x%d)", t.getId().toString(), item, qty);
            } catch (Exception e) { error("Invalid item: %s", item); }

            return SINGLE_SUCCESS;
        });

        qtyArg.then(argument("bot", StringArgumentType.word()).suggests(TARGET_SUGGESTIONS).executes(context -> {
            String item = StringArgumentType.getString(context, "item");
            int qty = IntegerArgumentType.getInteger(context, "quantity");
            String bot = StringArgumentType.getString(context, "bot");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

            try {
                net.minecraft.world.item.Item it = resolveKnownItem(item);
                if (it == null) {
                    error("unknown item '%s', not queued", item);
                    return SINGLE_SUCCESS;
                }
                BotTask t = new BotTask(BotTask.Type.GATHER, it, qty);
                if (!com.rdh.swarm.task.DependencyResolver.hasKnownSource(it)) {
                    t.setStatus(BotTask.Status.FAILED);
                    t.setFailureReason("no known source");
                    t.setAssignedBot(bot);
                    mod.host.getTaskQueue().add(t);
                    info("TASK: %s failed — no known source", t.getId());
                    return SINGLE_SUCCESS;
                }
                t.setAssignedBot(bot);
                mod.host.getTaskQueue().add(t);
                mod.host.assignTask(bot, t);
                info("Added and assigned task %s to %s", t.getId().toString(), bot);
            } catch (Exception e) { error("Invalid item: %s", item); }

            return SINGLE_SUCCESS;
        }));

        itemArg.then(qtyArg);
        task.then(literal("add").then(itemArg));

        // cancel: .eswarm task cancel <taskId>
        task.then(literal("cancel").then(argument("taskId", StringArgumentType.word()).executes(context -> {
            String idStr = StringArgumentType.getString(context, "taskId");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

            try {
                UUID id = UUID.fromString(idStr);
                boolean removed = mod.host.getTaskQueue().removeById(id);
                if (removed) info("Removed task %s from queue", idStr);
                else {
                    mod.host.sendAll("swarm:all task cancel " + idStr);
                    info("Broadcasted cancel %s", idStr);
                }
            } catch (Exception e) { error("Invalid UUID: %s", idStr); }
            return SINGLE_SUCCESS;
        })));

        // pause: .eswarm task pause <botName>
        task.then(literal("pause").then(argument("bot", StringArgumentType.word()).suggests(TARGET_SUGGESTIONS).executes(context -> {
            String bot = StringArgumentType.getString(context, "bot");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.sendTo(bot, "swarm:" + bot + " task pause");
            info("Sent pause to %s", bot);
            return SINGLE_SUCCESS;
        })));

        // resume: .eswarm task resume <botName>
        task.then(literal("resume").then(argument("bot", StringArgumentType.word()).suggests(TARGET_SUGGESTIONS).executes(context -> {
            String bot = StringArgumentType.getString(context, "bot");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.sendTo(bot, "swarm:" + bot + " task resume");
            info("Sent resume to %s", bot);
            return SINGLE_SUCCESS;
        })));

        // list: .eswarm task list
        task.then(literal("list").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            TaskQueue q = mod.host.getTaskQueue();
            List<BotTask> tasks = q.toList();
            if (tasks.isEmpty()) { info("No tasks in queue"); return SINGLE_SUCCESS; }
            info("Tasks in queue (%d):", tasks.size());
            for (BotTask t : tasks) info("  %s %s x%d assigned=%s status=%s reason=%s", t.getId().toString(), t.getItemName(), t.getTargetQuantity(), t.getAssignedBot(), t.getStatus(), t.getFailureReason() == null ? "" : t.getFailureReason());
            return SINGLE_SUCCESS;
        }));

        builder.then(task);

        // ---- deposit ----
        LiteralArgumentBuilder<ClientSuggestionProvider> deposit = literal("deposit");

        // deposit set (crosshair, "all" category)
        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");

        set.executes(context -> {
            return setDepositFromCrosshair(context, "all");
        });

        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, String> categoryArg = argument("category", StringArgumentType.word())
            .suggests(CATEGORY_SUGGESTIONS)
            .executes(context -> {
                String cat = StringArgumentType.getString(context, "category");
                return setDepositFromCrosshair(context, cat);
            });

        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, Integer> coords = argument("x", IntegerArgumentType.integer())
            .then(argument("y", IntegerArgumentType.integer())
            .then(argument("z", IntegerArgumentType.integer())
            .executes(context -> {
                String cat = StringArgumentType.getString(context, "category");
                int x = IntegerArgumentType.getInteger(context, "x");
                int y = IntegerArgumentType.getInteger(context, "y");
                int z = IntegerArgumentType.getInteger(context, "z");

                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                mod.host.setDepositChest(cat, x, y, z);
                updatePersistedCoords(mod, x, y, z);
                info("Set %s chest at %d %d %d", cat, x, y, z);
                return SINGLE_SUCCESS;
            })));

        categoryArg.then(coords);
        set.then(categoryArg);
        deposit.then(set);

        // deposit remove <category> [index]
        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, String> remCat = argument("category", StringArgumentType.word()).suggests(CATEGORY_SUGGESTIONS);
        remCat.executes(context -> {
            String cat = StringArgumentType.getString(context, "category");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.getDepositConfig().removeChest(mod.host.getCurrentServer(), cat);
            info("Removed all chests for %s", cat);
            return SINGLE_SUCCESS;
        });

        remCat.then(argument("index", IntegerArgumentType.integer(1)).executes(context -> {
            String cat = StringArgumentType.getString(context, "category");
            int idx = IntegerArgumentType.getInteger(context, "index");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.getDepositConfig().removeChest(mod.host.getCurrentServer(), cat, idx);
            info("Removed %s[%d]", cat, idx);
            return SINGLE_SUCCESS;
        }));

        deposit.then(literal("remove").then(remCat));

        // deposit list
        deposit.then(literal("list").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            java.util.Map<String, java.util.List<int[]>> chests = mod.host.getDepositConfig().getChests(mod.host.getCurrentServer());
            if (chests.isEmpty()) { info("No deposit chests configured for this server"); return SINGLE_SUCCESS; }
            info("Deposit chests for %s:", mod.host.getCurrentServer());
            for (var entry : chests.entrySet()) {
                List<int[]> list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    int[] pos = list.get(i);
                    info("  %s[%d]: %d %d %d", entry.getKey(), i+1, pos[0], pos[1], pos[2]);
                }
            }
            return SINGLE_SUCCESS;
        }));

        // deposit auto on|off
        deposit.then(literal("auto").then(argument("toggle", StringArgumentType.word()).executes(context -> {
            String toggle = StringArgumentType.getString(context, "toggle");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            boolean on = toggle.equalsIgnoreCase("on") || toggle.equalsIgnoreCase("true");
            mod.autoDepositSetting.set(on);
            mod.host.sendAll("swarm:all deposit auto " + (on ? "on" : "off"));
            info("Auto-deposit %s", on ? "ENABLED" : "DISABLED");
            return SINGLE_SUCCESS;
        })));

        // deposit threshold <N>
        deposit.then(literal("threshold").then(argument("value", IntegerArgumentType.integer(0, 36)).executes(context -> {
            int val = IntegerArgumentType.getInteger(context, "value");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.autoDepositThreshold.set(val);
            mod.host.sendAll("swarm:all deposit threshold " + val);
            info("Auto-deposit threshold set to %d", val);
            return SINGLE_SUCCESS;
        })));

        // deposit self (deposit the host player's own inventory)
        deposit.then(literal("self").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null) { error("Swarm module not loaded"); return SINGLE_SUCCESS; }
            if (!mod.depositSelf("command")) error("Self-deposit refused (see log)");
            return SINGLE_SUCCESS;
        }));

        // deposit push (push config to workers)
        deposit.then(literal("push").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.pushDepositConfigAll();
            if (mod.autoDepositSetting.get()) mod.host.sendAll("swarm:all deposit auto on");
            mod.host.sendAll("swarm:all deposit threshold " + mod.autoDepositThreshold.get());
            info("Pushed deposit config to all workers");
            return SINGLE_SUCCESS;
        }));

        builder.then(deposit);

        // ---- scan ----
        builder.then(literal("scan").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.sendAll("swarm:all scan");
            info("Scan started on all workers");
            return SINGLE_SUCCESS;
        }).then(argument("category", StringArgumentType.word()).suggests(CATEGORY_SUGGESTIONS).executes(context -> {
            String cat = StringArgumentType.getString(context, "category");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.sendAll("swarm:all scan " + cat);
            info("Scan started for category: %s", cat);
            return SINGLE_SUCCESS;
        })));

        // ---- stock ----
        builder.then(literal("stock").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            String server = mod.host.getCurrentServer();
            var names = DepositConfig.getCategoryNames();
            info("Storage stock summary for %s:", server);
            for (String cat : names) {
                var totals = mod.host.getStorageIndex().categoryTotals(server, cat);
                long itemCount = totals.values().stream().mapToLong(Long::longValue).sum();
                info("  %s: %d unique items, %d total count", cat, totals.size(), itemCount);
            }
            return SINGLE_SUCCESS;
        }).then(argument("category", StringArgumentType.word()).suggests(CATEGORY_SUGGESTIONS).executes(context -> {
            String cat = StringArgumentType.getString(context, "category");
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            String server = mod.host.getCurrentServer();
            var totals = mod.host.getStorageIndex().categoryTotals(server, cat);
            long itemCount = totals.values().stream().mapToLong(Long::longValue).sum();
            info("Category %s: %d unique items, %d total count", cat, totals.size(), itemCount);
            // display low-stock simple flags for a few categories
            if (cat.equalsIgnoreCase("food") || cat.equalsIgnoreCase("tools") || cat.equalsIgnoreCase("armor") || cat.equalsIgnoreCase("fuel") || cat.toLowerCase().contains("shulker")) {
                long total = itemCount;
                if (total < 16) info("  LOW STOCK: %d (threshold 16)", total);
            }
            return SINGLE_SUCCESS;
        })));

        // ---- base ----
        LiteralArgumentBuilder<ClientSuggestionProvider> base = literal("base");

        base.then(literal("set").executes(context -> {
            return setBaseFromCrosshair(context);
        }));

        // ---- dumpstorage ----
        builder.then(literal("dumpstorage").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            try { mod.host.getStorageIndex().debugPrint(mod.host.getCurrentServer()); } catch (Exception ignored) {}
            info("Storage index dumped to console");
            return SINGLE_SUCCESS;
        }));

        com.mojang.brigadier.builder.RequiredArgumentBuilder<ClientSuggestionProvider, Integer> bx = argument("x", IntegerArgumentType.integer())
            .then(argument("y", IntegerArgumentType.integer())
            .then(argument("z", IntegerArgumentType.integer())
            .executes(context -> {
                int x = IntegerArgumentType.getInteger(context, "x");
                int y = IntegerArgumentType.getInteger(context, "y");
                int z = IntegerArgumentType.getInteger(context, "z");

                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                mod.host.setBaseAnchor(x, y, z);
                updatePersistedBase(mod, x, y, z);
                info("Set base anchor at %d %d %d", x, y, z);
                return SINGLE_SUCCESS;
            })));

        bx.then(argument("y", IntegerArgumentType.integer()));
        base.then(bx);

        base.then(literal("clear").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            mod.host.clearBaseAnchor();
            try { mod.baseXSetting.set(Integer.MIN_VALUE); mod.baseYSetting.set(Integer.MIN_VALUE); mod.baseZSetting.set(Integer.MIN_VALUE); } catch (Exception ignored) {}
            info("Cleared base anchor");
            return SINGLE_SUCCESS;
        }));

        base.then(literal("show").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            if (mod.host.hasBaseAnchor()) info("Base: %s", mod.host.getBaseAnchorString());
            else info("No base anchor set");
            return SINGLE_SUCCESS;
        }));

        base.then(literal("push").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }
            if (mod.host.hasBaseAnchor()) {
                mod.host.sendAll("swarm:all base set " + mod.host.getBaseAnchorString());
                info("Pushed base anchor to workers");
            } else info("No base anchor to push");
            return SINGLE_SUCCESS;
        }));

        builder.then(base);
        
        // ---- waypoint ----
        LiteralArgumentBuilder<ClientSuggestionProvider> waypoint = literal("waypoint");

        // waypoint add <name> <category>
        waypoint.then(literal("add")
            .then(argument("name", StringArgumentType.word())
            .then(argument("category", StringArgumentType.word())
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                String category = StringArgumentType.getString(context, "category");
                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.player == null || mc.level == null) { error("No player"); return SINGLE_SUCCESS; }

                int x = mc.player.blockPosition().getX();
                int y = mc.player.blockPosition().getY();
                int z = mc.player.blockPosition().getZ();
                boolean isNether = mc.level.dimension() == Level.NETHER;

                mod.host.getWaypointManager().addWaypoint(name, category, x, y, z, isNether);
                info("Added waypoint '%s' [%s] at %d %d %d (%s)", name, category, x, y, z, isNether ? "nether" : "overworld");
                return SINGLE_SUCCESS;
            }))));

        // waypoint portal <name> — marks current location as portal (auto-detects dimension)
        waypoint.then(literal("portal")
            .then(argument("name", StringArgumentType.word())
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.player == null || mc.level == null) { error("No player"); return SINGLE_SUCCESS; }

                int x = mc.player.blockPosition().getX();
                int y = mc.player.blockPosition().getY();
                int z = mc.player.blockPosition().getZ();
                boolean isNether = mc.level.dimension() == Level.NETHER;

                mod.host.getWaypointManager().addPortalWaypoint(name, x, y, z, isNether);
                
                SwarmWaypoint wp = mod.host.getWaypointManager().getWaypoint(name);
                if (wp != null && wp.hasOverworldCoords() && wp.hasNetherCoords()) {
                    info("Portal '%s' complete — OW: %d,%d,%d | NETHER: %d,%d,%d", 
                        name, wp.overworldX, wp.overworldY, wp.overworldZ,
                        wp.netherX, wp.netherY, wp.netherZ);
                } else {
                    info("Portal '%s' %s side set at %d %d %d — go through and run again to set other side",
                        name, isNether ? "nether" : "overworld", x, y, z);
                }
                return SINGLE_SUCCESS;
            })));

        // waypoint list
        waypoint.then(literal("list").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

            var waypoints = mod.host.getWaypointManager().getAllWaypoints();
            if (waypoints.isEmpty()) {
                info("No waypoints configured for this server");
                return SINGLE_SUCCESS;
            }

            info("Waypoints (%d):", waypoints.size());
            for (SwarmWaypoint wp : waypoints) {
                info("  %s", wp.toString());
            }
            return SINGLE_SUCCESS;
        }));

        // waypoint remove <name>
        waypoint.then(literal("remove")
            .then(argument("name", StringArgumentType.word())
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                boolean removed = mod.host.getWaypointManager().removeWaypoint(name);
                if (removed) {
                    info("Removed waypoint '%s'", name);
                } else {
                    error("Waypoint '%s' not found", name);
                }
                return SINGLE_SUCCESS;
            })));

        // waypoint goto <name> — sends bot to waypoint (for testing)
        waypoint.then(literal("goto")
            .then(argument("name", StringArgumentType.word())
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
                if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

                SwarmWaypoint wp = mod.host.getWaypointManager().getWaypoint(name);
                if (wp == null) {
                    error("Waypoint '%s' not found", name);
                    return SINGLE_SUCCESS;
                }

                Minecraft mc = Minecraft.getInstance();
                boolean isNether = mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
                
                int[] wpCoords = wp.getCoords(isNether);
                if (wpCoords == null) {
                    error("Waypoint '%s' has no %s coordinates", name, isNether ? "nether" : "overworld");
                    return SINGLE_SUCCESS;
                }

                // Send goto command to all workers
                String cmd = String.format("exec #goto %d %d %d", wpCoords[0], wpCoords[1], wpCoords[2]);
                mod.host.sendAll("swarm:all " + cmd);
                info("Sent goto %d %d %d to all workers", wpCoords[0], wpCoords[1], wpCoords[2]);
                return SINGLE_SUCCESS;
            })));

        // waypoint push — push waypoints to workers (for future use)
        waypoint.then(literal("push").executes(context -> {
            EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
            if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

            var waypoints = mod.host.getWaypointManager().getAllWaypoints();
            if (waypoints.isEmpty()) {
                info("No waypoints to push");
                return SINGLE_SUCCESS;
            }

            // For now just log — actual push to workers would serialize waypoints
            // Workers will query host when they need waypoint info
            info("Waypoint system ready — workers will query host for waypoints during tasks");
            return SINGLE_SUCCESS;
        }));

        builder.then(waypoint);

        // ---- ids (local registry inspection; no gameplay side effects) ----
        IdsCommand.attach(this, builder);
    }

    private int haltAll() {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod != null && mod.isHost() && mod.host != null) {
            long queued = mod.host.getTaskQueue().getAllTasks().stream()
                .filter(t -> t != null && t.getStatus() == BotTask.Status.PENDING)
                .count();
            mod.host.sendAll("swarm:all cancel");
            info("swarm: halted — current task cancelled, %d queued task(s) kept", queued);
            return SINGLE_SUCCESS;
        }
        try {
            var brain = Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
            if (brain != null) {
                brain.cancel();
                info("swarm: halted — current task cancelled");
                return SINGLE_SUCCESS;
            }
        } catch (Exception ignored) {}
        error("Not hosting and no local BotBrain");
        return SINGLE_SUCCESS;
    }

    private int resumeAll() {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod != null && mod.isHost() && mod.host != null) {
            mod.host.clearHaltedFlags();
            mod.host.sendAll("swarm:all resume");
            info("swarm: resumed");
            return SINGLE_SUCCESS;
        }
        try {
            var brain = Modules.get().get(com.rdh.swarm.modules.BotBrain.class);
            if (brain != null) {
                brain.resume();
                info("swarm: resumed");
                return SINGLE_SUCCESS;
            }
        } catch (Exception ignored) {}
        error("Not hosting and no local BotBrain");
        return SINGLE_SUCCESS;
    }

    private static net.minecraft.world.item.Item resolveKnownItem(String item) {
        Identifier id = Identifier.tryParse(item);
        if (id == null) return null;
        net.minecraft.world.item.Item resolved = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return resolved == net.minecraft.world.item.Items.AIR ? null : resolved;
    }

    private int setDepositFromCrosshair(CommandContext<ClientSuggestionProvider> context, String category) {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) { error("No world"); return SINGLE_SUCCESS; }

            net.minecraft.world.phys.HitResult hr = mc.hitResult;
            if (hr == null || hr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { error("Look at a block"); return SINGLE_SUCCESS; }

            net.minecraft.world.phys.BlockHitResult bhr = (net.minecraft.world.phys.BlockHitResult) hr;
            net.minecraft.core.BlockPos pos = bhr.getBlockPos();

            if (mc.level.getBlockState(pos).getMenuProvider(mc.level, pos) == null) { error("Not a container block"); return SINGLE_SUCCESS; }

            mod.host.setDepositChest(category, pos.getX(), pos.getY(), pos.getZ());
            updatePersistedCoords(mod, pos.getX(), pos.getY(), pos.getZ());
            info("Set %s chest at %d %d %d", category, pos.getX(), pos.getY(), pos.getZ());
        } catch (Exception e) { error("Error: %s", e.getMessage()); }
        return SINGLE_SUCCESS;
    }

    private int setBaseFromCrosshair(CommandContext<ClientSuggestionProvider> context) {
        EnhancedSwarm mod = Modules.get().get(EnhancedSwarm.class);
        if (mod == null || !mod.isHost() || mod.host == null) { error("Not hosting"); return SINGLE_SUCCESS; }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) { error("No world"); return SINGLE_SUCCESS; }

            net.minecraft.world.phys.HitResult hr = mc.hitResult;
            if (hr == null || hr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { error("Look at a block"); return SINGLE_SUCCESS; }

            net.minecraft.world.phys.BlockHitResult bhr = (net.minecraft.world.phys.BlockHitResult) hr;
            net.minecraft.core.BlockPos pos = bhr.getBlockPos();

            mod.host.setBaseAnchor(pos.getX(), pos.getY(), pos.getZ());
            updatePersistedBase(mod, pos.getX(), pos.getY(), pos.getZ());
            info("Set base anchor at %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        } catch (Exception e) { error("Error: %s", e.getMessage()); }
        return SINGLE_SUCCESS;
    }

    private void updatePersistedCoords(EnhancedSwarm mod, int x, int y, int z) {
        try {
            mod.depositXSetting.set(x);
            mod.depositYSetting.set(y);
            mod.depositZSetting.set(z);
        } catch (Exception ignored) {}
    }

    private void updatePersistedBase(EnhancedSwarm mod, int x, int y, int z) {
        try {
            mod.baseXSetting.set(x);
            mod.baseYSetting.set(y);
            mod.baseZSetting.set(z);
        } catch (Exception ignored) {}
    }
}
