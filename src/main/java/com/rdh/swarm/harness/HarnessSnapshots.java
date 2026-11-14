package com.rdh.swarm.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Copies the current client state into JSON; callers must run this on the client thread. */
final class HarnessSnapshots {
    private static final int MAX_COMPONENTS_CHARS = 2048;

    private HarnessSnapshots() {}

    static JsonObject health(Minecraft mc) {
        JsonObject root = ok();
        root.addProperty("inGame", inGame(mc));
        root.addProperty("screenOpen", mc.gui.screen() != null);
        root.addProperty("menuOpen", mc.gui.screen() instanceof AbstractContainerScreen<?> && mc.player != null);
        return root;
    }

    static JsonObject state(Minecraft mc) {
        JsonObject root = ok();
        boolean inGame = inGame(mc);
        root.addProperty("inGame", inGame);
        root.add("screen", screenSummary(mc.gui.screen()));
        root.add("menu", menuSummary(mc));

        if (inGame) {
            root.add("player", player(mc.player, mc));
            root.add("inventory", inventory(mc));
        } else {
            root.add("player", JsonNull.INSTANCE);
            root.add("inventory", new JsonArray());
        }
        modules(root);
        return root;
    }

    /**
     * Live combat-module state, so a test can read what the client believes instead
     * of inferring it from damage alone.
     */
    private static void modules(JsonObject root) {
        root.addProperty("botBrain", active("bot-brain"));
        root.addProperty("swarmShield", active("swarm-shield"));
        root.addProperty("swarmSafeFire", active("swarm-safe-fire"));
        root.addProperty("swarmFlee", active("swarm-flee"));
        root.addProperty("killAura", active("kill-aura"));
        com.rdh.swarm.modules.SwarmFlee flee =
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(com.rdh.swarm.modules.SwarmFlee.class);
        root.addProperty("fleeing", flee != null && flee.isFleeing());

        com.rdh.swarm.modules.SwarmShield shield =
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(com.rdh.swarm.modules.SwarmShield.class);
        root.addProperty("shieldBlocking", shield != null && shield.isBlocking());

        com.rdh.swarm.modules.BotBrain brain =
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(com.rdh.swarm.modules.BotBrain.class);
        String refusal = brain == null ? "" : brain.getLastHuntRefusal();
        root.addProperty("huntRefusal", refusal == null ? "" : refusal);
    }

    private static boolean active(String moduleName) {
        meteordevelopment.meteorclient.systems.modules.Module module =
            meteordevelopment.meteorclient.systems.modules.Modules.get().get(moduleName);
        return module != null && module.isActive();
    }

    static JsonObject screen(Minecraft mc) {
        JsonObject root = ok();
        Screen screen = mc.gui.screen();
        root.addProperty("open", screen != null);
        if (screen == null) return root;

        root.addProperty("class", screen.getClass().getName());
        root.addProperty("title", screen.getTitle().getString());
        root.addProperty("width", screen.width);
        root.addProperty("height", screen.height);

        JsonArray widgets = new JsonArray();
        int index = 0;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("index", index++);
            entry.addProperty("type", widget.getClass().getSimpleName());
            entry.addProperty("x", widget.getX());
            entry.addProperty("y", widget.getY());
            entry.addProperty("width", widget.getWidth());
            entry.addProperty("height", widget.getHeight());
            entry.addProperty("text", widget.getMessage().getString());
            entry.addProperty("active", widget.isActive());
            entry.addProperty("visible", widget.visible);
            widgets.add(entry);
        }
        root.add("widgets", widgets);
        return root;
    }

    static JsonObject snapshot(Minecraft mc) {
        JsonObject root = new JsonObject();
        root.add("menu", menu(mc));
        root.add("inventory", inventory(mc));
        return root;
    }

    static JsonObject menu(Minecraft mc) {
        JsonObject root = ok();
        Screen screen = mc.gui.screen();
        boolean open = screen instanceof AbstractContainerScreen<?> && mc.player != null;
        root.addProperty("open", open);
        if (!open) return root;

        AbstractContainerMenu menu = mc.player.containerMenu;
        root.addProperty("class", menu.getClass().getName());
        root.addProperty("title", menuTitle(screen));
        root.addProperty("containerId", menu.containerId);
        root.addProperty("stateId", menu.getStateId());
        root.add("cursor", item(menu.getCarried()));

        JsonArray slots = new JsonArray();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("slot", slot.index);
            entry.addProperty("x", slot.x);
            entry.addProperty("y", slot.y);
            entry.addProperty("coordinateSpace", "menu-local");
            entry.addProperty("playerSlot", slot.container == mc.player.getInventory());
            entry.add("item", item(slot.getItem()));
            slots.add(entry);
        }
        root.add("slots", slots);
        return root;
    }

    private static JsonObject player(LocalPlayer player, Minecraft mc) {
        JsonObject root = new JsonObject();
        root.addProperty("name", player.getName().getString());
        root.addProperty("uuid", player.getUUID().toString());
        root.addProperty("health", player.getHealth());
        root.addProperty("food", player.getFoodData().getFoodLevel());
        root.addProperty("x", player.getX());
        root.addProperty("y", player.getY());
        root.addProperty("z", player.getZ());
        root.addProperty("dimension", mc.level == null ? "" : mc.level.dimension().identifier().toString());
        root.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        root.addProperty("mainHand", player.getMainHandItem().getItem().toString());
        root.addProperty("attackDamage", player.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        return root;
    }

    static JsonArray inventory(Minecraft mc) {
        JsonArray root = new JsonArray();
        if (mc.player == null) return root;

        int selected = mc.player.getInventory().getSelectedSlot();
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("selected", i == selected);
            entry.add("item", item(mc.player.getInventory().getItem(i)));
            root.add(entry);
        }
        return root;
    }

    private static JsonObject screenSummary(Screen screen) {
        JsonObject root = new JsonObject();
        root.addProperty("open", screen != null);
        if (screen != null) {
            root.addProperty("class", screen.getClass().getName());
            root.addProperty("title", screen.getTitle().getString());
        }
        return root;
    }

    private static JsonObject menuSummary(Minecraft mc) {
        JsonObject root = new JsonObject();
        boolean open = mc.gui.screen() instanceof AbstractContainerScreen<?> && mc.player != null;
        root.addProperty("open", open);
        if (open) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            root.addProperty("class", menu.getClass().getName());
            root.addProperty("containerId", menu.containerId);
        }
        return root;
    }

    private static String menuTitle(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            return containerScreen.getTitle().getString();
        }
        return screen.getTitle().getString();
    }

    private static JsonElement item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return JsonNull.INSTANCE;

        JsonObject root = new JsonObject();
        root.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        root.addProperty("count", stack.getCount());
        root.addProperty("name", stack.getHoverName().getString());
        root.addProperty("damage", stack.getDamageValue());

        String components = String.valueOf(stack.getComponents());
        if (components.length() > MAX_COMPONENTS_CHARS) {
            components = components.substring(0, MAX_COMPONENTS_CHARS) + "...";
        }
        root.addProperty("components", components);
        return root;
    }

    private static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.level != null;
    }

    static JsonObject ok() {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        return root;
    }
}
