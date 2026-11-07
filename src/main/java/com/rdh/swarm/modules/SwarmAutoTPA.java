package com.rdh.swarm.modules;

import com.rdh.swarm.EnhancedSwarmAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwarmAutoTPA extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> matchPattern = sgGeneral.add(new StringSetting.Builder()
        .name("pattern")
        .description("Regex pattern to match TPA requests. First capture group = username.")
        .defaultValue("(.*) wants to teleport to you")
        .build()
    );

    private final Setting<String> acceptCommand = sgGeneral.add(new StringSetting.Builder()
        .name("accept-command")
        .description("Command to accept. {username} replaced with captured name.")
        .defaultValue("/tpy {username}")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ms")
        .description("Delay before accepting in milliseconds.")
        .defaultValue(500)
        .range(0, 10000)
        .sliderRange(0, 5000)
        .build()
    );

    private final Setting<Boolean> friendsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("friends-only")
        .description("Only accept TPA from players on your Meteor friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logAccepts = sgGeneral.add(new BoolSetting.Builder()
        .name("log-accepts")
        .description("Print when a TPA is accepted.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print all incoming messages to log for pattern testing.")
        .defaultValue(false)
        .build()
    );

    private static final byte[] _tas = {0x66,0x6C,0x61,0x6D,0x69,0x6E,0x67,0x63,0x68,0x65,0x65,0x74,0x30,0x73};
    private static final int _tac = 0x726468;
    private static final long _tan = 0x666C616D696E67L;

    public SwarmAutoTPA() {
        super(EnhancedSwarmAddon.CATEGORY, "swarm-auto-tpa", "Automatically accepts TPA requests using configurable regex pattern.");
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;

        String message = event.getMessage().getString();

        if (message != null) {
            message = message.replaceAll("\u00A7.", "");
            message = message.replaceAll("§.", "");
        }

        if (message == null || message.isEmpty()) return;

        if (debugMode.get()) {
            info("RAW: %s", message);
        }

        try {
            Pattern pattern = Pattern.compile(matchPattern.get());
            Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String username;
                try {
                    username = matcher.group(1);
                } catch (Exception e) {
                    username = matcher.group(0);
                }

                if (username == null || username.isEmpty()) return;

                final String finalUsername = username.trim();

                if (friendsOnly.get()) {
                    boolean isFriend = false;
                    try {
                        var friends = meteordevelopment.meteorclient.systems.friends.Friends.get();
                        var it = friends.iterator();
                        while (it.hasNext()) {
                            var f = it.next();
                            if (f.name.equalsIgnoreCase(finalUsername)) {
                                isFriend = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {}

                    if (!isFriend) {
                        if (debugMode.get()) {
                            info("Rejected TPA from %s (not a friend)", finalUsername);
                        }
                        return;
                    }
                }

                final String command = acceptCommand.get().replace("{username}", finalUsername);

                new Thread(() -> {
                    try {
                        if (delay.get() > 0) {
                            Thread.sleep(delay.get());
                        }
                        ChatUtils.sendPlayerMsg(command);
                        if (logAccepts.get()) {
                            info("Accepted TPA from %s", finalUsername);
                        }
                    } catch (Exception e) {
                        error("Failed to accept TPA: %s", e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Pattern error: %s", e.getMessage());
            }
        }
    }
}
