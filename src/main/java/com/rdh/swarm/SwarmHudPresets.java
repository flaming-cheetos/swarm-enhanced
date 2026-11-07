package com.rdh.swarm;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.elements.TextHud;

public class SwarmHudPresets {

    public static void init() {
        final HudElementInfo<TextHud>[] holder = new HudElementInfo[1];
        HudElementInfo<TextHud> info = new HudElementInfo<TextHud>(
            Hud.GROUP,
            "swarm-bot",
            "Displays Enhanced Swarm bot status.",
            () -> new TextHud(holder[0])
        );
        holder[0] = info;

        info.addPreset("BOT1 Status", hud -> hud.text.set("Bot Brain: {meteor.get_module_info(\"bot-brain\")}"));
        info.addPreset("BOT2 Status", hud -> hud.text.set("Bot Brain: {meteor.get_module_info(\"bot-brain\")}"));
        info.addPreset("BOT3 Status", hud -> hud.text.set("Bot Brain: {meteor.get_module_info(\"bot-brain\")}"));

        Hud.get().register(info);
    }
}
