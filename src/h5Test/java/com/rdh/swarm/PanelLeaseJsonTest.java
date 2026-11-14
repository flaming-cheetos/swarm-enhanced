package com.rdh.swarm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rdh.swarm.task.BotTask;
import com.rdh.swarm.web.SwarmWebPanelScript;
import com.rdh.swarm.web.SwarmWebPanelV2;
import com.rdh.swarm.web.SwarmWebServer;

import java.util.Map;
import java.util.UUID;

/**
 * H5 panel regression probe: lease diagnostics are visible without exposing
 * the fencing token, and both panel renderers consume the new fields.
 */
public final class PanelLeaseJsonTest {
    private PanelLeaseJsonTest() {}

    public static void main(String[] args) {
        long now = 1_800_000_000_000L;
        BotTask task = new BotTask(UUID.fromString("44444444-4444-4444-4444-444444444444"),
            BotTask.Type.GATHER, 0, Map.of("quantity", 2));
        task.setStatus(BotTask.Status.ACTIVE);
        task.setAssignedBot("jamal");
        task.setAttempt(3);
        task.setStatusReason("host-restart");
        task.setLastOwner("jamal");
        task.setLeaseToken("secret-fencing-token");
        task.setLeaseExpiresAt(now + 30_000L);
        task.setLastHeartbeatAt(now - 1_500L);
        task.addCollected(2);

        String json = SwarmWebServer.buildTaskJson(task, now);
        JsonObject row = JsonParser.parseString(json).getAsJsonObject();
        check(row.get("attempt").getAsInt() == 3, "attempt missing from panel JSON");
        check("host-restart".equals(row.get("statusReason").getAsString()),
            "statusReason missing from panel JSON");
        check("jamal".equals(row.get("leaseOwner").getAsString()),
            "leaseOwner missing from panel JSON");
        check(row.get("leaseExpiresAt").getAsLong() == now + 30_000L,
            "leaseExpiresAt missing from panel JSON");
        check(row.get("leaseExpiresInMs").getAsLong() == 30_000L,
            "lease expiry age missing from panel JSON");
        check(row.get("heartbeatAgeMs").getAsLong() == 1_500L,
            "heartbeat age missing from panel JSON");
        check(json.indexOf("leaseToken") < 0, "raw leaseToken was exposed");
        check(json.indexOf("secret-fencing-token") < 0, "token value was exposed");

        String v1 = SwarmWebPanelScript.getScript();
        check(v1.contains("task.attempt"), "v1 panel does not render attempt");
        check(v1.contains("task.statusReason"), "v1 panel does not render statusReason");
        check(v1.contains("task.leaseExpiresAt"), "v1 panel does not render lease expiry");

        String v2 = SwarmWebPanelV2.js();
        check(v2.contains("task.attempt"), "v2 panel does not render attempt");
        check(v2.contains("task.statusReason"), "v2 panel does not render statusReason");
        check(v2.contains("task.leaseExpiresAt"), "v2 panel does not render lease expiry");

        System.out.println("PASS PanelLeaseJsonTest: visible lease diagnostics without token");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
