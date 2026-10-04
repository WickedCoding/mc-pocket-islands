package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if >=1.21 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared setup for the in-game scenarios. Every test runs in the same server, so
 * scenarios use their own player names and the config is pinned once, before the
 * first island exists.
 */
public final class TestSupport {

    /** Pocket rule overrides every scenario relies on. */
    public static final int POCKET_RANDOM_TICK_SPEED = 10;

    // Arena layout (personalworlds:arena, 8x8x8): stone floor at y=1, a portal frame along X
    // at z=3 with interior x=2..3, y=2..4. Players stand at (2,2,4) facing north and walk
    // into the portal; the stored return position is then (2,2,4), on the floor (safe).
    public static final BlockPos FRAME_BOTTOM_LEFT = new BlockPos(1, 1, 3);
    public static final BlockPos PORTAL_INTERIOR = new BlockPos(2, 2, 3);
    private static final BlockPos IN_FRONT_OF_PORTAL = PORTAL_INTERIOR.south();
    private static final float FACING_NORTH = 180.0F;

    // Islands get no pre-built return frame (the void generator's island layers make
    // PortalHelper skip its starter platform), so players build one; this one sits next to
    // the arrival point (0, 65, 0), floating is fine for frame detection
    private static final BlockPos ISLAND_RETURN_FRAME_BOTTOM_LEFT = new BlockPos(3, 65, 2);

    private static boolean configured;

    private TestSupport() {
    }

    /** Pin the config values the scenarios depend on (idempotent). */
    public static synchronized void ensureConfigured() {
        if (configured) {
            return;
        }
        ModConfig config = ModConfig.get();
        Map<String, Object> rules = new LinkedHashMap<>();
        //? if >=1.21 {
        /*rules.put(GameRules.KEEP_INVENTORY.getIdentifier().getPath(), true);
        rules.put(GameRules.RANDOM_TICK_SPEED.getIdentifier().getPath(), POCKET_RANDOM_TICK_SPEED);
        rules.put(GameRules.ADVANCE_TIME.getIdentifier().getPath(), false);
        *///?} else {
        rules.put(GameRules.RULE_KEEPINVENTORY.getId(), true);
        rules.put(GameRules.RULE_RANDOMTICKING.getId(), POCKET_RANDOM_TICK_SPEED);
        rules.put(GameRules.RULE_DAYLIGHT.getId(), false);
        //?}
        config.dimensionGameRules = rules;
        config.allowVisitWhenHostNotHome = false;
        config.enableAlwaysWelcome = false;
        config.consumeActivationItem = false;
        configured = true;
    }

    /** Floor plus an unlit portal frame of portal type 0 at the arena's fixed spot. */
    public static void buildArena(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        buildFrame(helper.getLevel(), helper.absolutePos(FRAME_BOTTOM_LEFT));
    }

    /** An unlit 4x5 frame of portal type 0 along X (2x3 interior), as a player builds it. */
    public static void buildFrame(Level level, BlockPos bottomLeft) {
        BlockState frame = ModBlocks.getFrameBlock(0).defaultBlockState();
        for (int w = 0; w < 4; w++) {
            level.setBlockAndUpdate(bottomLeft.east(w), frame);
            level.setBlockAndUpdate(bottomLeft.east(w).above(4), frame);
        }
        for (int h = 1; h < 4; h++) {
            level.setBlockAndUpdate(bottomLeft.above(h), frame);
            level.setBlockAndUpdate(bottomLeft.east(3).above(h), frame);
        }
    }

    /** Put the player one block in front of the arena portal, facing it. */
    public static void standInFrontOfArenaPortal(GameTestHelper helper, ServerPlayer player) {
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(IN_FRONT_OF_PORTAL));
        moveWithinLevel(player, pos.x, pos.y, pos.z, FACING_NORTH);
    }

    /** Step from in front of the arena portal into it, through the movement packet handler. */
    public static void walkIntoArenaPortal(GameTestHelper helper, ServerPlayer player) {
        player.setPortalCooldown(0);
        ClientInput.moveTo(player, Vec3.atBottomCenterOf(helper.absolutePos(PORTAL_INTERIOR)));
    }

    /** True once the player is on the owner's island, after confirming any pending teleport. */
    public static boolean arrivedOnIslandOf(ServerPlayer player, ServerPlayer owner) {
        ClientInput.acceptPendingTeleport(player);
        return inPocketOf(player, owner);
    }

    public static void moveWithinLevel(ServerPlayer player, double x, double y, double z) {
        moveWithinLevel(player, x, y, z, 0.0F);
    }

    /** Server-side move within the player's current level (no portal checks). */
    public static void moveWithinLevel(ServerPlayer player, double x, double y, double z, float yaw) {
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.teleportTo(x, y, z);
    }

    /**
     * Right-click the top of a frame's bottom block with the activation item, through the
     * player's game mode, so the loader's use-block event (and our handler) runs.
     */
    public static void activate(ServerPlayer player, Level level, BlockPos bottomFrameBlock) {
        Item activationItem = ModItems.getActivationItem(0);
        ItemStack stack = new ItemStack(activationItem);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(bottomFrameBlock).add(0, 0.5, 0), Direction.UP, bottomFrameBlock, false);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
    }

    public static void activateArenaPortal(GameTestHelper helper, ServerPlayer player) {
        activate(player, helper.getLevel(), helper.absolutePos(PORTAL_INTERIOR.below()));
    }

    /** Build and light a return portal on the player's island, and stand in front of it. */
    public static void buildAndActivateIslandReturnPortal(ServerPlayer player) {
        ServerLevel island = EntityCompat.getServerWorld(player);
        buildFrame(island, ISLAND_RETURN_FRAME_BOTTOM_LEFT);
        activate(player, island, ISLAND_RETURN_FRAME_BOTTOM_LEFT.east());
        BlockPos front = islandReturnPortal().south();
        island.setBlockAndUpdate(front.below(), Blocks.STONE.defaultBlockState());
        Vec3 pos = Vec3.atBottomCenterOf(front);
        moveWithinLevel(player, pos.x, pos.y, pos.z, FACING_NORTH);
    }

    public static BlockPos islandReturnPortal() {
        return ISLAND_RETURN_FRAME_BOTTOM_LEFT.east().above();
    }

    /**
     * Step into the island's return portal through the movement packet handler. The portal
     * cooldown counts down in the connection tick, which mock connections never get, so it
     * is cleared here as if the player had waited.
     */
    public static void walkIntoIslandReturnPortal(ServerPlayer player) {
        player.setPortalCooldown(0);
        ClientInput.moveTo(player, Vec3.atBottomCenterOf(islandReturnPortal()));
    }

    public static boolean isPortal(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() == ModBlocks.PERSONAL_PORTAL.get();
    }

    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean inPocket(ServerPlayer player) {
        return PortalHelper.isInPersonalDimension(EntityCompat.getServerWorld(player));
    }

    public static boolean inPocketOf(ServerPlayer player, ServerPlayer owner) {
        return PortalHelper.getDimensionOwner(EntityCompat.getServerWorld(player))
            .map(owner.getUUID()::equals)
            .orElse(false);
    }

    public static ServerLevel level(ServerPlayer player) {
        return EntityCompat.getServerWorld(player);
    }
}
