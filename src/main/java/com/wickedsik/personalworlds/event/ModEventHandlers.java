package com.wickedsik.personalworlds.event;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRecoveryScanner;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.portal.ConcurrentPortalGuard;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.recovery.CrashRecoveryHandler;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import com.wickedsik.personalworlds.util.PerformanceMonitor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ModEventHandlers {

    private static int tickCounter = 0;
    private static final int UNLOAD_CHECK_INTERVAL = 600; // 30 seconds

    private static int guardCleanupCounter = 0;
    private static final int GUARD_CLEANUP_INTERVAL = 200; // 10 seconds

    private static final int VOID_EJECTION_THRESHOLD = 0; // Y level for ejection

    public static void register() {
        // Server started - restore all dimensions
        ServerLifecycleEvents.SERVER_STARTED.register(ModEventHandlers::onServerStarted);

        // Server stopping - cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(ModEventHandlers::onServerStopping);

        // Periodic tick for unloading empty dimensions
        ServerTickEvents.END_SERVER_TICK.register(ModEventHandlers::onServerTick);

        // Portal activation via block interaction
        UseBlockCallback.EVENT.register(ModEventHandlers::onUseBlock);

        // Player join - crash recovery
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            CrashRecoveryHandler.onPlayerJoin(handler.getPlayer()));

        // Player disconnect - release portal locks
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ConcurrentPortalGuard.forceRelease(handler.getPlayer().getUuid()));

        PersonalWorldsMod.LOGGER.info("Event handlers registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server started - scanning for orphaned dimensions");

        // Scan filesystem for orphaned dimensions and recover them before normal restore
        // This handles cases where the registry file was corrupted or deleted
        DimensionRecoveryScanner.scanAndRecover(server);

        // Now restore all registered dimensions (including any just recovered)
        PersonalWorldsMod.LOGGER.info("Restoring player dimensions from registry");
        DimensionRegistry.get(server).restoreAllDimensions(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server stopping - unloading all dimensions");
        DimensionManager.unloadAll();
    }

    private static void onServerTick(MinecraftServer server) {
        // Check void falling every tick (safety critical)
        checkVoidFalling(server);

        tickCounter++;
        if (tickCounter >= UNLOAD_CHECK_INTERVAL) {
            tickCounter = 0;
            DimensionManager.unloadEmptyDimensions();

            // Performance monitoring (if enabled)
            PerformanceMonitor.logStatus(server);
        }

        // Cleanup concurrent portal guard
        guardCleanupCounter++;
        if (guardCleanupCounter >= GUARD_CLEANUP_INTERVAL) {
            guardCleanupCounter = 0;
            ConcurrentPortalGuard.cleanup();
        }
    }

    /**
     * Check if any players in personal dimensions have fallen below the ejection threshold.
     * Ejects them safely before void damage can occur.
     */
    private static void checkVoidFalling(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getServerWorld();

            // Only check in personal dimensions
            if (!PortalHelper.isInPersonalDimension(world)) {
                continue;
            }

            // Check if below ejection threshold
            if (player.getY() <= VOID_EJECTION_THRESHOLD) {
                // Reset fall distance to prevent fall damage
                player.fallDistance = 0;

                // Eject to return position
                PortalHelper.teleportToReturnPosition(player, server);

                // Notify player
                player.sendMessage(
                    Text.translatable("personalworlds.void_ejection"),
                    false
                );

                PersonalWorldsMod.LOGGER.info("Player {} fell off island and was ejected at Y={}",
                    player.getName().getString(), player.getY());
            }
        }
    }

    /**
     * Handle block interaction for portal activation.
     * When a player right-clicks with an emerald on or near a nether brick frame,
     * attempt to activate a personal portal.
     */
    private static ActionResult onUseBlock(
            PlayerEntity player,
            World world,
            Hand hand,
            BlockHitResult hitResult
    ) {
        // Only process on server side
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        // Only process for server players
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        // Get the item being used - let portal detection handle validation
        ItemStack heldItem = player.getStackInHand(hand);

        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);

        // Determine the target position for portal activation
        BlockPos targetPos;

        // Check if clicked on any portal frame type
        boolean clickedOnFrame = false;
        for (int i = 0; i < ModConfig.get().portalTypes.size(); i++) {
            if (clickedState.getBlock() == ModBlocks.getFrameBlock(i)) {
                clickedOnFrame = true;
                break;
            }
        }

        if (clickedOnFrame) {
            // Player clicked on frame block - check the block on the clicked face
            targetPos = clickedPos.offset(hitResult.getSide());
        } else {
            // Player clicked on something else (possibly air inside frame)
            targetPos = clickedPos;
        }

        // Target must be air for portal activation
        if (!world.getBlockState(targetPos).isAir()) {
            return ActionResult.PASS;
        }

        // Attempt to activate the portal with activation item
        if (PortalHelper.tryActivatePortal(world, targetPos, serverPlayer, heldItem.getItem())) {
            // Success - don't consume the activation item (swing arm for feedback)
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
