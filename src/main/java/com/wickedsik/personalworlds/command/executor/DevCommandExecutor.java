package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.TeleportHelper;
import com.wickedsik.personalworlds.compat.TeleportCompat;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;

/**
 * Executor for development/testing commands.
 * Handles dimension creation and basic navigation (OP level 2+).
 *
 * Commands:
 * - /pi create [type] - Create a pocket island
 * - /pi enter - Enter your island
 * - /pi leave - Leave to overworld
 */
public class DevCommandExecutor {

    /**
     * Create a new pocket island dimension for the player.
     *
     * @param player The player creating the dimension
     * @param typeStr World generation type (e.g., "OVERWORLD", "VOID")
     * @return Command result
     */
    public CommandResult createDimension(ServerPlayerEntity player, String typeStr) {
        WorldGenType type = WorldGenType.fromString(typeStr);
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        MinecraftServer server = player.getServer();

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                server,
                playerUuid,
                playerName,
                type,
                0  // Default portal type for /pi create command
            );

            TeleportCompat.teleport(player, dimension, TeleportHelper.toDefaultSpawn(player));

            return CommandResult.successBroadcast(
                Text.translatable("personalworlds.command.info.dimension_created", type.name())
            );
        } catch (Exception e) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.create_failed", e.getMessage())
            );
        }
    }

    /**
     * Enter the player's existing pocket island.
     *
     * @param player The player entering their dimension
     * @return Command result
     */
    public CommandResult enterDimension(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        MinecraftServer server = player.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);

        if (!registry.hasDimension(playerUuid)) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.no_dimension")
            );
        }

        Optional<PlayerDimensionData> optData = registry.getDimensionData(playerUuid);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.load_failed")
            );
        }

        PlayerDimensionData data = optData.get();

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                server,
                playerUuid,
                player.getName().getString(),
                data.generatorType(),
                data.portalTypeIndex()
            );

            TeleportCompat.teleport(
                player,
                dimension,
                TeleportHelper.toBlockPos(data.spawnPoint(), player)
            );

            return CommandResult.successBroadcast(
                Text.translatable("personalworlds.command.enter.success")
            );
        } catch (Exception e) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.enter_failed", e.getMessage())
            );
        }
    }

    /**
     * Leave the current pocket island and return to the overworld.
     *
     * @param player The player leaving
     * @return Command result (always successful as PortalHelper handles the teleport)
     */
    public CommandResult leaveDimension(ServerPlayerEntity player) {
        PortalHelper.teleportToReturnPosition(player, player.getServer());
        return CommandResult.silent();
    }
}
