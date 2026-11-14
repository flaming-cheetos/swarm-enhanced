package com.rdh.swarm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rdh.swarm.task.SmeltingManager;
import com.rdh.swarm.task.SmeltingManager.FuelEntry;
import com.rdh.swarm.task.SmeltingManager.FuelLedger;
import com.rdh.swarm.task.SmeltingManager.FuelObservation;

import java.util.List;

/**
 * Fuel reader probe: known fuel computes the expected ticks, an empty inventory
 * reports zero, and unresolved fuel is reported honestly instead of counted.
 */
public final class FuelReaderTest {
    private FuelReaderTest() {}

    public static void main(String[] args) {
        knownFuelMergesStacksAndComputesTicks();
        emptyInventoryReportsZero();
        unresolvedFuelIsReportedAndExcluded();
        System.out.println("PASS FuelReaderTest: known/empty/unresolved fuel");
    }

    private static void knownFuelMergesStacksAndComputesTicks() {
        FuelLedger ledger = SmeltingManager.summarizeFuels(List.of(
            new FuelObservation("minecraft:coal", 40, 1600, true, "constant"),
            new FuelObservation("minecraft:coal", 2, 1600, true, "constant")
        ));

        check(ledger.entries().size() == 1, "duplicate coal stacks were not merged");
        FuelEntry coal = ledger.entries().get(0);
        check("minecraft:coal".equals(coal.id()), "coal id changed");
        check(coal.count() == 42, "coal count should be 42");
        check(coal.ticksPerItem() == 1600, "coal burn time should be 1600 ticks");
        check(coal.ticks() == 67_200, "coal total should be 42 * 1600 = 67200");
        check(coal.resolved(), "coal should be resolved");
        check(ledger.fuelTicks() == 67_200, "usable fuel ticks should be 67200");

        JsonObject json = JsonParser.parseString(SmeltingManager.fuelStateJson(ledger)).getAsJsonObject();
        check(json.get("fuelTicks").getAsInt() == 67_200, "JSON fuelTicks should be 67200");
        JsonArray fuels = json.getAsJsonArray("fuels");
        check(fuels.size() == 1, "JSON should contain one fuel entry");
        JsonObject coalJson = fuels.get(0).getAsJsonObject();
        check(coalJson.get("count").getAsInt() == 42, "JSON coal count should be 42");
        check(coalJson.get("ticks").getAsInt() == 67_200, "JSON coal ticks should be 67200");
        check(coalJson.get("resolved").getAsBoolean(), "JSON coal should be resolved");
    }

    private static void emptyInventoryReportsZero() {
        check(SmeltingManager.inventoryFuels().isEmpty(),
            "a client with no player should report no fuels");
        check(SmeltingManager.inventoryFuelTicks() == 0,
            "a client with no player should report zero fuel ticks");

        FuelLedger empty = SmeltingManager.summarizeFuels(List.of());
        check(empty.entries().isEmpty(), "empty inventory should have no fuel entries");
        check(empty.fuelTicks() == 0, "empty inventory should have zero fuel ticks");

        JsonObject json = JsonParser.parseString(SmeltingManager.fuelStateJson(empty)).getAsJsonObject();
        check(json.get("fuelTicks").getAsInt() == 0, "empty JSON fuelTicks should be zero");
        check(json.getAsJsonArray("fuels").isEmpty(), "empty JSON fuels should be an empty array");
    }

    private static void unresolvedFuelIsReportedAndExcluded() {
        FuelObservation mystery = new FuelObservation(
            "minecraft:mystery_fuel", 3, 0, false, "resolver returned 0 ticks");
        FuelObservation coal = new FuelObservation(
            "minecraft:coal", 1, 1600, true, "constant");

        FuelLedger ledger = SmeltingManager.summarizeFuels(List.of(mystery, coal));
        check(ledger.entries().size() == 2, "both fuel ids should be present");

        FuelEntry unresolved = find(ledger, "minecraft:mystery_fuel");
        check(unresolved != null, "unresolved fuel disappeared from the ledger");
        check(!unresolved.resolved(), "unresolved fuel was marked resolved");
        check(unresolved.ticks() == 0, "unresolved fuel contributed ticks");
        check(unresolved.reason().contains("resolver returned 0 ticks"),
            "unresolved fuel lost its reason");

        check(ledger.fuelTicks() == 1_600,
            "usable ticks must exclude unresolved fuel and keep the known coal");

        FuelLedger unresolvedOnly = SmeltingManager.summarizeFuels(List.of(mystery));
        check(unresolvedOnly.fuelTicks() == 0,
            "unresolved-only inventory reported usable ticks");

        JsonObject json = JsonParser.parseString(SmeltingManager.fuelStateJson(unresolvedOnly)).getAsJsonObject();
        check(json.get("fuelTicks").getAsInt() == 0, "JSON counted unresolved fuel as usable");
        JsonObject first = json.getAsJsonArray("fuels").get(0).getAsJsonObject();
        check(!first.get("resolved").getAsBoolean(), "JSON marked unresolved fuel as resolved");
        check(first.get("ticks").getAsInt() == 0, "JSON gave unresolved fuel ticks");
        check(first.get("reason").getAsString().contains("resolver returned 0 ticks"),
            "JSON dropped the unresolved reason");
    }

    private static FuelEntry find(FuelLedger ledger, String id) {
        for (FuelEntry entry : ledger.entries()) {
            if (id.equals(entry.id())) return entry;
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
