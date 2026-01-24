package com.wickedsik.personalworlds.recovery;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.util.SafeSpawnFinder;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles crash recovery for players who were in personal dimensions.
 *
 * When a player disconnects or server crashes while in a personal dimension,
 * their position is saved in that dimension. On next login, they might:
 * 1. Spawn correctly in the dimension (Fantasy preserved their data)
 * 2. Need to be teleported back to their return position
 * 3. Need emergency teleport if return position is invalid
 */
public class CrashRecoveryHandler {

    /**
     * Check and handle crash recovery for a joining player.
     * Called from ServerPlayConnectionEvents.JOIN.
     *
     * @param player The player who just joined
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerUuid = player.getUuid();
        ServerWorld currentWorld = player.getServerWorld();

        // Case 1: Player logged in inside a personal dimension
        if (PortalHelper.isInPersonalDimension(currentWorld)) {
            handleLoginInPersonalDimension(player, server, currentWorld);
            return;
        }

        // Case 2: Player has orphaned return data (was in dimension, crashed, respawned elsewhere)
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        if (dataManager.hasReturnData(playerUuid)) {
            handleOrphanedReturnData(player, server, dataManager);
        }
    }

    /**
     * Handle a player who logged in while inside a personal dimension.
     * Verify they have permission to be there.
     */
    private static void handleLoginInPersonalDimension(
            ServerPlayerEntity player,
            MinecraftServer server,
            ServerWorld personalWorld
    ) {
        UUID playerUuid = player.getUuid();
        Optional<UUID> ownerOpt = PortalHelper.getDimensionOwner(personalWorld);

        if (ownerOpt.isEmpty()) {
            // Invalid personal dimension - emergency evacuation
            PersonalWorldsMod.LOGGER.warn("Player {} in invalid personal dimension, evacuating",
                player.getName().getString());
            emergencyEvacuate(player, server, "Invalid dimension detected");
            return;
        }

        UUID ownerUuid = ownerOpt.get();

        // Check permission
        if (!playerUuid.equals(ownerUuid) &&
            !InvitationManager.canVisit(server, playerUuid, ownerUuid)) {
            // No longer has permission - invitation was revoked while offline
            PersonalWorldsMod.LOGGER.info("Player {} no longer has permission to {}'s dimension, evacuating",
                player.getName().getString(), ownerUuid);

            player.sendMessage(Text.translatable("personalworlds.message.ejected_offline")
                .formatted(Formatting.GOLD), false);

            evacuateToReturnPosition(player, server);
        }

        // Otherwise: player has permission, let them stay
        PersonalWorldsMod.LOGGER.debug("Player {} logged in to personal dimension with valid permission",
            player.getName().getString());
    }

    /**
     * Handle orphaned return data - player has return data but isn't in a personal dimension.
     * This indicates they crashed/disconnected and respawned at world spawn.
     */
    private static void handleOrphanedReturnData(
            ServerPlayerEntity player,
            MinecraftServer server,
            PlayerDataManager dataManager
    ) {
        UUID playerUuid = player.getUuid();

        // Check if the return data is stale (more than 24 hours old)
        // For now, just clear it - player can re-enter their dimension via portal
        dataManager.clearReturnData(playerUuid);

        PersonalWorldsMod.LOGGER.debug("Cleared orphaned return data for player {}",
            player.getName().getString());
    }

    /**
     * Evacuate player to their stored return position, or overworld spawn as fallback.
     */
    private static void evacuateToReturnPosition(ServerPlayerEntity player, MinecraftServer server) {
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(player.getUuid());

        ServerWorld targetWorld;
        Vec3d targetPos;
        float yaw, pitch;

        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getWorld(returnData.dimension());

            if (targetWorld != null && SafeSpawnFinder.isSafeSpawn(targetWorld, returnData.position())) {
                targetPos = Vec3d.ofCenter(returnData.position());
                yaw = returnData.yaw();
                pitch = returnData.pitch();
            } else {
                // Return position invalid - use overworld spawn
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(targetWorld, targetWorld.getSpawnPos()));
                yaw = player.getYaw();
                pitch = player.getPitch();
            }

            dataManager.clearReturnData(player.getUuid());
        } else {
            // No return data - try bed spawn first
            BlockPos bedPos = player.getSpawnPointPosition();
            ServerWorld bedWorld = null;

            if (bedPos != null) {
                bedWorld = server.getWorld(player.getSpawnPointDimension());
            }

            if (bedWorld != null) {
                // Use bed spawn
                BlockPos safePos = SafeSpawnFinder.findSafePosition(bedWorld, bedPos);
                targetWorld = bedWorld;
                targetPos = Vec3d.ofCenter(safePos);
            } else {
                // Fallback: overworld world spawn
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(targetWorld, targetWorld.getSpawnPos()));
            }
            yaw = player.getYaw();
            pitch = player.getPitch();
        }

        TeleportTarget target = new TeleportTarget(targetPos, Vec3d.ZERO, yaw, pitch);
        FabricDimensions.teleport(player, targetWorld, target);

        player.sendMessage(Text.translatable("personalworlds.message.returned_overworld"), true);
    }

    /**
     * Emergency evacuation when something is seriously wrong.
     */
    private static void emergencyEvacuate(ServerPlayerEntity player, MinecraftServer server, String reason) {
        ServerWorld overworld = server.getOverworld();
        Vec3d spawnPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(overworld, overworld.getSpawnPos()));

        TeleportTarget target = new TeleportTarget(spawnPos, Vec3d.ZERO, player.getYaw(), player.getPitch());
        FabricDimensions.teleport(player, overworld, target);

        player.sendMessage(Text.translatable("personalworlds.message.emergency_teleport", reason)
            .formatted(Formatting.RED), false);

        // Clear any corrupt return data
        PlayerDataManager.get(server).clearReturnData(player.getUuid());
    }
}
