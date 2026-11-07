package com.rdh.swarm.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;

/**
 * Ported BOInvUtils with Managers references replaced.
 */
@SuppressWarnings("DataFlowIssue")
public class BOInvUtils {
    // Minimal stubs to avoid depending on packet classes and meteor internals.
    public static boolean pickSwitch(int slot) {
        return false;
    }

    public static void pickSwapBack() {
    }

    public static boolean invSwitch(int slot) {
        return false;
    }

    public static void swapBack() {
    }

    // Compatibility helper: get the selected hotbar slot in a way that works across mappings.
    public static int getSelectedSlot() {
        try {
            PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
            try {
                java.lang.reflect.Method m = PlayerInventory.class.getMethod("getSelectedSlot");
                Object res = m.invoke(inv);
                if (res instanceof Integer) return (Integer) res;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                java.lang.reflect.Field f = PlayerInventory.class.getDeclaredField("selectedSlot");
                f.setAccessible(true);
                return f.getInt(inv);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        } catch (Throwable t) {
            // fall through to default
        }

        return 0;
    }
}
