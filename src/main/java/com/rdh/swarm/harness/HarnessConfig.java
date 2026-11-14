package com.rdh.swarm.harness;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class HarnessConfig {
    static final String FILE_NAME = "enhanced-swarm-harness.json";
    static final int DEFAULT_PORT = 8899;

    final boolean enabled;
    final int port;
    final String token;
    final boolean allowActions;

    private HarnessConfig(boolean enabled, int port, String token, boolean allowActions) {
        this.enabled = enabled;
        this.port = port;
        this.token = token;
        this.allowActions = allowActions;
    }

    static HarnessConfig load(Path file) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        boolean enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
        if (!enabled) return new HarnessConfig(false, 0, "", false);

        int port = root.has("port") ? root.get("port").getAsInt() : DEFAULT_PORT;
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("harness port must be between 1024 and 65535");
        }

        String token = root.has("token") && !root.get("token").isJsonNull()
            ? root.get("token").getAsString().trim()
            : "";
        if (token.length() < 16 || token.length() > 256) {
            throw new IllegalArgumentException("harness token must be 16-256 characters");
        }

        boolean allowActions = root.has("allowActions")
            && !root.get("allowActions").isJsonNull()
            && root.get("allowActions").getAsBoolean();

        return new HarnessConfig(true, port, token, allowActions);
    }
}
