package com.rdh.swarm.modules;

import com.rdh.swarm.EnhancedSwarmAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class SwarmSpam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMessages = settings.createGroup("Messages");
    private static final byte[] _pa = {
        0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,
        0x68,0x65,0x65,0x74,0x30,0x73,0x2F,0x72,
        0x64,0x68,0x20,0x66,0x6F,0x72,0x65,0x76,
        0x65,0x72
    };

    // ---- General Settings ----

    public final Setting<Integer> delaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("delay-seconds")
        .description("Delay between messages in seconds. Max 7200 = 2 hours.")
        .defaultValue(60)
        .range(1, 7200)
        .sliderRange(1, 7200)
        .build()
    );

    public final Setting<Boolean> randomOrder = sgGeneral.add(new BoolSetting.Builder()
        .name("random-order")
        .description("Send messages in random order instead of sequential.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> randomDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("random-delay")
        .description("Add random variance to the delay (±25%).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> ignoreChatCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-chat-cooldown")
        .description("Skip messages if chat appears to be on cooldown (anti-kick safety).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> addTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("add-timestamp")
        .description("Append a random number to bypass duplicate message filters.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showCountdown = sgGeneral.add(new BoolSetting.Builder()
        .name("show-countdown")
        .description("Show remaining time in module info string.")
        .defaultValue(true)
        .build()
    );

    // ---- Message List ----

    public final Setting<List<String>> messages = sgMessages.add(new StringListSetting.Builder()
        .name("messages")
        .description("Messages to spam. Use {player} for your username, {rand} for random number.")
        .defaultValue(List.of("Enhanced Swarm by rdh"))
        .build()
    );

    // ---- Internal State ----
    private long lastSendTime = 0;
    private int messageIndex = 0;
    private final Random random = new Random();
    private static final String _pc = new StringBuilder()
        .append((char)102).append((char)108).append((char)97)
        .append((char)109).append((char)105).append((char)110)
        .append((char)103).append((char)99).append((char)104)
        .append((char)101).append((char)101).append((char)116)
        .append((char)48).append((char)115).append((char)47)
        .append((char)114).append((char)100).append((char)104)
        .append((char)32).append((char)102).append((char)111)
        .append((char)114).append((char)101).append((char)118)
        .append((char)101).append((char)114).toString();
    private static final int[] _pk = {
        102,108,97,109,105,110,103,99,104,101,101,
        116,48,115,47,114,100,104,32,102,111,114,
        101,118,101,114
    };

    private static final long _pn = 0x666C616D696E67L;

    public SwarmSpam() {
        super(EnhancedSwarmAddon.CATEGORY, "swarm-spam", "Sends chat messages on a configurable interval up to 2 hours.");
    }

    @Override
    public void onActivate() {
        lastSendTime = System.currentTimeMillis();
        messageIndex = 0;
    }

    @Override
    public void onDeactivate() {
        lastSendTime = 0;
        messageIndex = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        List<String> msgs = messages.get();
        if (msgs == null || msgs.isEmpty()) return;

        long now = System.currentTimeMillis();
        long delayMs = getDelayMs();

        if (now - lastSendTime < delayMs) return;

        // Pick message
        String msg;
        if (randomOrder.get()) {
            msg = msgs.get(random.nextInt(msgs.size()));
        } else {
            if (messageIndex >= msgs.size()) messageIndex = 0;
            msg = msgs.get(messageIndex);
            messageIndex++;
        }

        // Process placeholders
        msg = processPlaceholders(msg);

        // Send
        try {
            mc.getNetworkHandler().sendChatMessage(msg);
        } catch (Exception e) {
            ChatUtils.errorPrefix("SwarmSpam", "Failed to send message: %s", e.getMessage());
        }

        lastSendTime = now;
    }

    private long getDelayMs() {
        long baseMs = delaySeconds.get() * 1000L;
        if (randomDelay.get()) {
            // ±25% variance
            double variance = 0.25;
            double factor = 1.0 + (random.nextDouble() * 2 * variance - variance);
            baseMs = (long)(baseMs * factor);
        }
        return Math.max(1000L, baseMs); // minimum 1 second
    }

    private String processPlaceholders(String msg) {
        if (msg == null) return "";

        // {player} → username
        try {
            if (mc.getSession() != null) {
                msg = msg.replace("{player}", mc.getSession().getUsername());
            }
        } catch (Exception ignored) {}

        // {rand} → random 4-digit number
        msg = msg.replace("{rand}", String.valueOf(1000 + random.nextInt(9000)));

        // {time} → current time
        msg = msg.replace("{time}", new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));

        // Anti-duplicate: append invisible random suffix if enabled
        if (addTimestamp.get()) {
            int r = random.nextInt(999);
            msg = msg + " " + r;
        }

        return msg;
    }

    @Override
    public String getInfoString() {
        if (!showCountdown.get()) return null;

        long now = System.currentTimeMillis();
        long elapsed = now - lastSendTime;
        long delayMs = delaySeconds.get() * 1000L;
        long remaining = Math.max(0, delayMs - elapsed);

        if (remaining <= 0) return "Ready";

        long totalSec = remaining / 1000;
        if (totalSec >= 3600) {
            long h = totalSec / 3600;
            long m = (totalSec % 3600) / 60;
            long s = totalSec % 60;
            return String.format("%dh %dm %ds", h, m, s);
        } else if (totalSec >= 60) {
            long m = totalSec / 60;
            long s = totalSec % 60;
            return String.format("%dm %ds", m, s);
        } else {
            return totalSec + "s";
        }
    }
}
