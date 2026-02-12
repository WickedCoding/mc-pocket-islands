package com.wickedsik.personalworlds.recovery;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.compat.TeleportCompat;
import com.wickedsik.personalworlds.compat.WorldCompat;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.util.SafeSpawnFinder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles crash recovery for players who were in personal dimensions.
 *
 * When a player disconnects or server crashes while in a personal dimension,
 * their position is saved in that dimension. On next login, they might:
 * 1. Spawn correctly in the dimension (Fantasy preserved their data)
 * 2. Need permission verification if in another player's dimension
 * 3. Need dimension restoration if logged out on island and dimension unloaded
 * 4. Need emergency teleport if return position is invalid
 *
 * Recovery fallback chain:
 * 1. Pocket Dimension (restore from tracking) -> if fails:
 * 2. Return Data (where they entered from) -> if fails:
 * 3. Bed Spawn -> if fails:
 * 4. World Spawn (always available)
 */
public class CrashRecoveryHandler {

    /**
     * Check and handle crash recovery for a joining player.
     * Called from ServerPlayConnectionEvents.JOIN.
     *
     * @param player The player who just joined
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = EntityCompat.getServer(player);
        if (server == null) return;

        UUID playerUuid = player.getUuid();
        ServerWorld currentWorld = EntityCompat.getServerWorld(player);
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        // Case 1: Player IS in personal dimension (normal login or Fantasy restored them)
        if (PortalHelper.isInPersonalDimension(currentWorld)) {
            handleLoginInPersonalDimension(player, server, currentWorld);
            return;
        }

        // Case 2: Player SHOULD be in personal dimension but isn't (dimension wasn't loaded)
        Optional<RegistryKey<World>> expectedDimension = dataManager.getCurrentPocketDimension(playerUuid);
        if (expectedDimension.isPresent()) {
            handleMisplacedPlayer(player, server, expectedDimension.get());
            return;
        }

        // Case 3: Player has orphaned return data (was in dimension, crashed, respawned elsewhere)
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

            player.sendMessage(Text.translatable("pocketislands.message.ejected_offline")
                .formatted(Formatting.GOLD), false);

            // Clear tracking and evacuate
            PlayerDataManager dataManager = PlayerDataManager.get(server);
            dataManager.clearCurrentPocketDimension(playerUuid);
            teleportToFallbackPosition(player, server, dataManager);
            return;
        }

        // Player has permission - ensure tracking is up to date
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.setCurrentPocketDimension(playerUuid, personalWorld.getRegistryKey());

        PersonalWorldsMod.LOGGER.debug("Player {} logged in to personal dimension with valid permission",
            player.getName().getString());
    }

    /**
     * Handle a player who SHOULD be in a pocket dimension but spawned elsewhere.
     * This happens when the player logged out on their island and the dimension
     * was unloaded before they logged back in.
     *
     * @param player The player who just joined
     * @param server The Minecraft server
     * @param expectedDimension The dimension they should be in
     */
    private static void handleMisplacedPlayer(
            ServerPlayerEntity player,
            MinecraftServer server,
            RegistryKey<World> expectedDimension
    ) {
        UUID playerUuid = player.getUuid();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        // Extract owner UUID from dimension key (personalworlds:pw_<uuid>)
        Optional<UUID> ownerOpt = PortalHelper.getDimensionOwner(expectedDimension);

        if (ownerOpt.isEmpty()) {
            // Invalid dimension key - clear tracking and use fallback
            PersonalWorldsMod.LOGGER.warn("Invalid pocket dimension key for player {}: {}",
                player.getName().getString(), expectedDimension.getValue());
            dataManager.clearCurrentPocketDimension(playerUuid);
            teleportToFallbackPosition(player, server, dataManager);
            return;
        }

        UUID ownerUuid = ownerOpt.get();

        // Verify permission (owner or valid invitation)
        if (!playerUuid.equals(ownerUuid) &&
            !InvitationManager.canVisit(server, playerUuid, ownerUuid)) {
            // No longer has permission - use fallback
            PersonalWorldsMod.LOGGER.info("Player {} lost permission to dimension {}, using fallback",
                player.getName().getString(), ownerUuid);
            dataManager.clearCurrentPocketDimension(playerUuid);
            player.sendMessage(Text.translatable("pocketislands.message.ejected_offline")
                .formatted(Formatting.GOLD), false);
            teleportToFallbackPosition(player, server, dataManager);
            return;
        }

        // Look up dimension data from registry (contains owner name, gen type, portal type, spawn)
        DimensionRegistry registry = DimensionRegistry.get(server);
        Optional<PlayerDimensionData> dimDataOpt = registry.getDimensionData(ownerUuid);

        if (dimDataOpt.isEmpty()) {
            // Dimension was deleted or registry corrupted - use fallback
            PersonalWorldsMod.LOGGER.warn("No registry data for dimension {}, using fallback",
                ownerUuid);
            dataManager.clearCurrentPocketDimension(playerUuid);
            teleportToFallbackPosition(player, server, dataManager);
            return;
        }

        PlayerDimensionData dimData = dimDataOpt.get();

        // Restore dimension and teleport player there
        try {
            ServerWorld targetWorld = DimensionManager.getOrCreatePlayerDimension(
                server, ownerUuid, dimData.ownerName(), dimData.generatorType(), dimData.portalTypeIndex());

            if (targetWorld == null) {
                throw new RuntimeException("Failed to restore dimension - getOrCreatePlayerDimension returned null");
            }

            // Use stored spawn point from dimension data
            BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, dimData.spawnPoint());

            TeleportCompat.teleportToBlockPreserveRotation(player, targetWorld, safePos);

            PersonalWorldsMod.LOGGER.info("Restored player {} to pocket dimension after fallback spawn",
                player.getName().getString());
            player.sendMessage(Text.translatable("pocketislands.message.dimension_restored"), false);

        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.error("Failed to restore dimension for {}: {}",
                player.getName().getString(), e.getMessage());
            dataManager.clearCurrentPocketDimension(playerUuid);
            teleportToFallbackPosition(player, server, dataManager);
        }
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
     * Teleport player to their fallback position using the recovery chain:
     * 1. Return Data (where they entered from)
     * 2. Bed Spawn
     * 3. World Spawn (always available)
     *
     * @param player The player to teleport
     * @param server The Minecraft server
     * @param dataManager The player data manager
     */
    private static void teleportToFallbackPosition(
            ServerPlayerEntity player,
            MinecraftServer server,
            PlayerDataManager dataManager
    ) {
        UUID playerUuid = player.getUuid();
        ServerWorld targetWorld;
        Vec3d targetPos;
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        // Priority 1: Stored return data
        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(playerUuid);
        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getWorld(returnData.dimension());

            if (targetWorld != null) {
                BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, returnData.position());
                targetPos = Vec3d.ofCenter(safePos);
                yaw = returnData.yaw();
                pitch = returnData.pitch();
                dataManager.clearReturnData(playerUuid);
                teleportPlayer(player, targetWorld, targetPos, yaw, pitch);
                return;
            }
            // Return dimension not found - fall through to next priority
            dataManager.clearReturnData(playerUuid);
        }

        // Priority 2: Bed spawn
        BlockPos bedPos = com.wickedsik.personalworlds.compat.EntityCompat.getSpawnPointPosition(player);
        if (bedPos != null) {
            ServerWorld bedWorld = server.getWorld(com.wickedsik.personalworlds.compat.EntityCompat.getSpawnPointDimension(player));
            if (bedWorld != null) {
                BlockPos safePos = SafeSpawnFinder.findSafePosition(bedWorld, bedPos);
                targetPos = Vec3d.ofCenter(safePos);
                teleportPlayer(player, bedWorld, targetPos, yaw, pitch);
                return;
            }
        }

        // Priority 3: Overworld spawn (always available)
        targetWorld = server.getOverworld();
        BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, WorldCompat.getSpawnPos(targetWorld));
        targetPos = Vec3d.ofCenter(safePos);
        teleportPlayer(player, targetWorld, targetPos, yaw, pitch);
    }

    /**
     * Helper method to teleport player and show message.
     */
    private static void teleportPlayer(
            ServerPlayerEntity player,
            ServerWorld world,
            Vec3d pos,
            float yaw,
            float pitch
    ) {
        TeleportCompat.teleport(player, world, pos, yaw, pitch);
        player.sendMessage(Text.translatable("pocketislands.message.returned_overworld"), true);
    }

    /**
     * Evacuate player to their stored return position, or overworld spawn as fallback.
     * Used when player is in a dimension but loses permission.
     */
    private static void evacuateToReturnPosition(ServerPlayerEntity player, MinecraftServer server) {
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        teleportToFallbackPosition(player, server, dataManager);
    }

    /**
     * Emergency evacuation when something is seriously wrong.
     */
    private static void emergencyEvacuate(ServerPlayerEntity player, MinecraftServer server, String reason) {
        ServerWorld overworld = server.getOverworld();
        BlockPos safePos = SafeSpawnFinder.findSafePosition(overworld, WorldCompat.getSpawnPos(overworld));

        TeleportCompat.teleportToBlockPreserveRotation(player, overworld, safePos);

        player.sendMessage(Text.translatable("pocketislands.message.emergency_teleport", reason)
            .formatted(Formatting.RED), false);

        // Clear any corrupt data
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.clearReturnData(player.getUuid());
        dataManager.clearCurrentPocketDimension(player.getUuid());
    }
}
