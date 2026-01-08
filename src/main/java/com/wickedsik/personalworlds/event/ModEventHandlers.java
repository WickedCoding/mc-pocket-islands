package com.wickedsik.personalworlds.event;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ModEventHandlers {

    private static int tickCounter = 0;
    private static final int UNLOAD_CHECK_INTERVAL = 600; // 30 seconds

    public static void register() {
        // Server started - restore all dimensions
        ServerLifecycleEvents.SERVER_STARTED.register(ModEventHandlers::onServerStarted);

        // Server stopping - cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(ModEventHandlers::onServerStopping);

        // Periodic tick for unloading empty dimensions
        ServerTickEvents.END_SERVER_TICK.register(ModEventHandlers::onServerTick);

        // Portal activation via block interaction
        UseBlockCallback.EVENT.register(ModEventHandlers::onUseBlock);

        PersonalWorldsMod.LOGGER.info("Event handlers registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server started - restoring player dimensions");
        DimensionRegistry.get(server).restoreAllDimensions(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server stopping - unloading all dimensions");
        DimensionManager.unloadAll();
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= UNLOAD_CHECK_INTERVAL) {
            tickCounter = 0;
            DimensionManager.unloadEmptyDimensions();
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

        // Check if player is holding the activation item (emerald)
        ItemStack heldItem = player.getStackInHand(hand);
        if (heldItem.getItem() != ModItems.getActivationItem()) {
            return ActionResult.PASS;
        }

        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);

        // Determine the target position for portal activation
        BlockPos targetPos;
        if (clickedState.getBlock() == ModBlocks.getFrameBlock()) {
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

        // Attempt to activate the portal
        if (PortalHelper.tryActivatePortal(world, targetPos, serverPlayer)) {
            // Success - don't consume the emerald (swing arm for feedback)
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
