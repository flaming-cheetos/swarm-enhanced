// BotBrain.java new fields
private BlockPos pendingPlacementPos = null;
private net.minecraft.item.Item pendingPlacementItem = null;
private int pendingPlacementRetries = 0;
private static final int MAX_PENDING_PLACEMENT_RETRIES = 3;

// BotBrain.java helper method
private boolean isBlockAt(BlockPos pos, net.minecraft.block.Block block) {
    if (pos == null || mc.player == null || mc.world == null) return false;
    return mc.world.getBlockState(pos).getBlock() == block;
}

// BotBrain.java PREP_PLACE_TABLE replacement
case PREP_PLACE_TABLE -> {
    if (craftPrepTicks == 1) ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Have table item, placing...");

    BlockPos placePos;
    if (pendingPlacementPos == null || pendingPlacementItem != Items.CRAFTING_TABLE) {
        placePos = findPlacementSpot();
        if (placePos == null) {
            ChatUtils.infoPrefix("BotBrain", "No valid spot to place table within 3 blocks!");
            BlockPos fallback = mc.player.getBlockPos();
            if (!mc.world.getBlockState(fallback).isAir()) fallback = fallback.up();
            if (mc.world.getBlockState(fallback).isAir()) {
                placePos = fallback;
                ChatUtils.infoPrefix("BotBrain", "Using fallback placement at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
            } else {
                ChatUtils.infoPrefix("BotBrain", "Cannot find ANY spot for table, going STUCK");
                setState(BrainState.STUCK);
                return;
            }
        }
        pendingPlacementPos = placePos;
        pendingPlacementItem = Items.CRAFTING_TABLE;
        pendingPlacementRetries = 0;
        ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Placing table at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
    } else {
        placePos = pendingPlacementPos;
    }

    if (!ensureItemInMainHand(Items.CRAFTING_TABLE)) {
        if (CraftingManager.countInInventory(Items.CRAFTING_TABLE) == 0) {
            ChatUtils.infoPrefix("BotBrain", "No crafting table available");
            if (resolverNeeds3x3 && resolverCraftPlan != null) {
                setState(BrainState.CRAFTING);
                return;
            }
            setState(BrainState.STUCK);
        }
        craftPrepTicks = 0;
        return;
    }

    boolean placed = com.rdh.swarm.utils.PlacementHelper.placeBlockSafely(placePos, Items.CRAFTING_TABLE);
    if (placed) {
        craftingTablePos = placePos;
        craftPrepPhase = PREP_WAIT_PLACE;
        craftPrepTicks = 0;
    } else {
        ChatUtils.infoPrefix("BotBrain", "Placement failed for crafting table at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
        craftPrepTicks = 0;
    }
},

// BotBrain.java PREP_WAIT_PLACE replacement
case PREP_WAIT_PLACE -> {
    if (craftPrepTicks >= 5) {
        if (isBlockAt(craftingTablePos, Blocks.CRAFTING_TABLE)) {
            ChatUtils.infoPrefix("BotBrain", "Crafting table confirmed at %d %d %d", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
            pendingPlacementPos = null;
            pendingPlacementItem = null;
            pendingPlacementRetries = 0;
            craftPrepPhase = PREP_OPEN_TABLE;
            craftPrepTicks = 0;
        } else {
            pendingPlacementRetries++;
            if (pendingPlacementRetries < MAX_PENDING_PLACEMENT_RETRIES) {
                ChatUtils.infoPrefix("BotBrain", "Table not confirmed yet (retry %d/%d), returning to place", pendingPlacementRetries, MAX_PENDING_PLACEMENT_RETRIES);
                craftPrepPhase = PREP_PLACE_TABLE;
                craftPrepTicks = 0;
            } else {
                ChatUtils.infoPrefix("BotBrain", "Max retries exceeded for table placement, clearing and rescanning");
                pendingPlacementPos = null;
                pendingPlacementItem = null;
                pendingPlacementRetries = 0;
                craftingTablePos = null;
                craftPrepPhase = PREP_PLACE_TABLE;
                craftPrepTicks = 0;
            }
        }
    }
},

// BotBrain.java PREP_OPEN_TABLE replacement
case PREP_OPEN_TABLE -> {
    if (!isBlockAt(craftingTablePos, Blocks.CRAFTING_TABLE)) {
        ChatUtils.infoPrefix("BotBrain", "Tracked crafting table missing at %d %d %d, need to replace", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
        pendingPlacementPos = null;
        pendingPlacementItem = null;
        pendingPlacementRetries = 0;
        craftingTablePos = null;
        craftPrepPhase = PREP_PLACE_TABLE;
        craftPrepTicks = 0;
        return;
    }
    if (craftPrepTicks == 1) {
        double dx = mc.player.getX() - (craftingTablePos.getX() + 0.5);
        double dy = mc.player.getY() - (craftingTablePos.getY() + 0.5);
        double dz = mc.player.getZ() - (craftingTablePos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > 4.0) {
            String cmd = String.format("#goto %d %d %d", craftingTablePos.getX(), craftingTablePos.getY(), craftingTablePos.getZ());
            if (canSendBaritone()) mc.getNetworkHandler().sendChatMessage(cmd);
            ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Walking to table (dist=%.1f)", dist);
            craftPrepTicks = 0;
            return;
        }
        if (canSendBaritone()) mc.getNetworkHandler().sendChatMessage("#stop");
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(craftingTablePos), Direction.UP, craftingTablePos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: Opening table...");
    }
    if (CraftingManager.isCraftingOpen()) {
        ChatUtils.infoPrefix("BotBrain", "CRAFTING_PREP: 3x3 GUI open, resuming craft");
        planStep = craftPrepReturnPlanStep;
        setState(BrainState.CRAFTING);
        return;
    }
    if (craftPrepTicks > 100) {
        ChatUtils.infoPrefix("BotBrain", "Failed to open table, going STUCK");
        setState(BrainState.STUCK);
        return;
    }
},

// BotBrain.java SMELT_PLACE replacement
case SMELT_PLACE -> {
    if (smeltTicks == 1) ChatUtils.infoPrefix("BotBrain", "SMELT: placing furnace from inventory");
    // Find placement spot adjacent to player
    BlockPos placePos = findPlacementSpot();
    if (placePos == null) {
        placePos = mc.player.getBlockPos().up();
    }

    // Ensure furnace in main hand
    if (!ensureItemInMainHand(Items.FURNACE)) {
        if (InvUtils.find(Items.FURNACE).found()) {
            // Retry next tick
            smeltTicks = 0;
            return;
        }
        ChatUtils.infoPrefix("BotBrain", "No furnace item available to place");
        setState(BrainState.STUCK);
        return;
    }

    boolean placed = com.rdh.swarm.utils.PlacementHelper.placeBlockSafely(placePos, Items.FURNACE);
    if (placed && isBlockAt(placePos, Blocks.FURNACE)) {
        smelterPos = placePos;
        ChatUtils.infoPrefix("BotBrain", "Furnace confirmed at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
        smeltPhase = SMELT_OPEN;
        smeltTicks = 0;
    } else {
        ChatUtils.infoPrefix("BotBrain", "Furnace placement failed/not confirmed at %d %d %d", placePos.getX(), placePos.getY(), placePos.getZ());
        smeltTicks = 0;
    }
},

// BotBrain.java setState additions
        if (newState == BrainState.IDLE || newState == BrainState.CRAFTING || newState == BrainState.SMELTING_DEPOSIT || newState == BrainState.STUCK) {
            pendingPlacementPos = null;
            pendingPlacementItem = null;
            pendingPlacementRetries = 0;
        }

