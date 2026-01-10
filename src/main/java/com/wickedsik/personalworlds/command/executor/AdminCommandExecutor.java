package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.command.service.TeleportHelper;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalOwnershipManager;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Executor for admin commands.
 * Handles island management, teleportation, and configuration.
 *
 * Commands:
 * - /pi admin list - List all islands
 * - /pi admin info <player> - View island details
 * - /pi admin delete <player> - Delete an island (with confirmation)
 * - /pi admin tp <player> - Teleport to an island
 * - /pi admin reload - Reload configuration
 */
public class AdminCommandExecutor {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final PlayerLookupService playerLookup;

    public AdminCommandExecutor(PlayerLookupService playerLookup) {
        this.playerLookup = playerLookup;
    }

    /**
     * List all registered player dimensions with status.
     *
     * @param source Command source for output
     */
    public void list(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);
        Map<UUID, PlayerDimensionData> dimensions = registry.getAllDimensions();

        if (dimensions.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.list.empty")
                .formatted(Formatting.GRAY), false);
            return;
        }

        MutableText header = Text.translatable("personalworlds.command.list.header")
            .formatted(Formatting.GOLD);
        source.sendFeedback(() -> header, false);

        for (PlayerDimensionData data : dimensions.values()) {
            boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
            ServerWorld world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
            int playerCount = world != null ? world.getPlayers().size() : 0;

            MutableText line = Text.literal(" - ")
                .append(Text.literal(data.ownerName())
                    .formatted(loaded ? Formatting.GREEN : Formatting.GRAY))
                .append(Text.literal(" (")
                    .formatted(Formatting.DARK_GRAY))
                .append(Text.literal(data.generatorType().name())
                    .formatted(Formatting.AQUA))
                .append(Text.literal(") ")
                    .formatted(Formatting.DARK_GRAY));

            if (loaded) {
                line.append(Text.translatable("personalworlds.command.list.loaded")
                    .formatted(Formatting.GREEN));
                if (playerCount > 0) {
                    line.append(Text.literal(", " + playerCount + " player" + (playerCount > 1 ? "s" : ""))
                        .formatted(Formatting.YELLOW));
                }
                line.append(Text.literal("]").formatted(Formatting.GREEN));
            } else {
                line.append(Text.translatable("personalworlds.command.list.unloaded").formatted(Formatting.GRAY));
            }

            source.sendFeedback(() -> line, false);
        }
    }

    /**
     * Show detailed information about a player's dimension.
     *
     * @param source Command source for output
     * @param playerName The owner name to look up
     * @return Command result
     */
    public CommandResult info(ServerCommandSource source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();
        boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
        ServerWorld world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
        int playerCount = world != null ? world.getPlayers().size() : 0;

        // Get invitation counts
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Set<UUID> sentInvites = dataManager.getSentInvitations(data.ownerUuid());
        int inviteCount = sentInvites.size();

        // Build info display
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.header",
            data.ownerName()).formatted(Formatting.GOLD), false);

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.owner",
            data.ownerName()), false);

        String createdStr = DATE_FORMAT.format(new Date(data.createdAt()));
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.created",
            createdStr), false);

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.world_type",
            data.generatorType().name()), false);

        if (loaded) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.info.status_loaded",
                playerCount), false);
        } else {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.info.status_unloaded"), false);
        }

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.invitations",
            inviteCount), false);

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.spawn",
            data.spawnPoint().getX(),
            data.spawnPoint().getY(),
            data.spawnPoint().getZ()), false);

        return CommandResult.silent();
    }

    /**
     * Prompt for dimension deletion with confirmation.
     *
     * @param source Command source for output
     * @param playerName The owner name to delete
     * @return Command result
     */
    public CommandResult deletePrompt(ServerCommandSource source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();

        // Play warning sound if admin is a player
        if (source.getEntity() instanceof ServerPlayerEntity admin) {
            VisualEffects.playAdminWarningEffect(admin);
        }

        boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
        ServerWorld world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
        int playerCount = world != null ? world.getPlayers().size() : 0;

        // Warning message
        source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.warning",
            data.ownerName()), false);

        if (playerCount > 0) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.players_ejected",
                playerCount).formatted(Formatting.GOLD), false);
        }

        // Confirmation prompt
        source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.confirm",
            data.ownerName()), false);

        return CommandResult.silent();
    }

    /**
     * Actually delete a player's dimension after confirmation.
     *
     * @param source Command source for output
     * @param playerName The owner name to delete
     * @return Command result
     */
    public CommandResult deleteConfirm(ServerCommandSource source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();
        UUID ownerUuid = data.ownerUuid();
        String ownerName = data.ownerName();

        // Eject all players if dimension is loaded
        if (DimensionManager.isDimensionLoaded(ownerUuid)) {
            ServerWorld dimWorld = DimensionManager.getLoadedDimension(ownerUuid);
            if (dimWorld != null) {
                ServerWorld overworld = server.getOverworld();

                // Copy player list to avoid concurrent modification
                List<ServerPlayerEntity> playersToEject = new ArrayList<>(dimWorld.getPlayers());
                for (ServerPlayerEntity player : playersToEject) {
                    FabricDimensions.teleport(player, overworld, TeleportHelper.toWorldSpawn(overworld, player));
                    player.sendMessage(Text.translatable("personalworlds.message.admin_ejected")
                        .formatted(Formatting.RED), false);
                }
            }
        }

        // Remove from registry FIRST (before deletion)
        DimensionRegistry registry = DimensionRegistry.get(server);
        registry.removeDimension(ownerUuid);

        // Clean up invitations (both sent and received)
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.clearAllInvitationsFor(ownerUuid);

        // Clean up portal ownership records for this owner
        PortalOwnershipManager portalManager = PortalOwnershipManager.get(server);
        int portalsCleared = portalManager.clearPortalsOwnedBy(ownerUuid);
        if (portalsCleared > 0) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.info.cleared_portals",
                portalsCleared).formatted(Formatting.GRAY), false);
        }

        // Delete the dimension and its folder
        DimensionManager.deleteDimension(server, ownerUuid);

        // Success feedback
        if (source.getEntity() instanceof ServerPlayerEntity admin) {
            VisualEffects.playAdminSuccessEffect(admin);
        }

        return CommandResult.successBroadcast(
            Text.translatable("personalworlds.command.info.deleted", ownerName)
                .formatted(Formatting.GREEN)
        );
    }

    /**
     * Teleport admin to a player's dimension.
     *
     * @param admin The admin teleporting
     * @param playerName The target island owner
     * @return Command result
     */
    public CommandResult teleport(ServerPlayerEntity admin, String playerName) {
        MinecraftServer server = admin.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();

        // Load/create dimension and teleport (admin bypass - no permission check)
        ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
            server,
            data.ownerUuid(),
            data.ownerName(),
            data.generatorType(),
            data.portalTypeIndex()
        );

        // Store return position
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.setReturnData(admin.getUuid(),
            new ReturnData(
                admin.getServerWorld().getRegistryKey(),
                admin.getBlockPos(),
                admin.getYaw(),
                admin.getPitch()
            ));

        // Teleport with effects
        VisualEffects.playTeleportDepartureEffects(admin);
        FabricDimensions.teleport(admin, dimension, TeleportHelper.toBlockPos(data.spawnPoint(), admin));
        VisualEffects.playTeleportArrivalEffects(admin);

        return CommandResult.successBroadcast(
            Text.translatable("personalworlds.command.info.teleported", data.ownerName())
                .formatted(Formatting.GREEN)
        );
    }

    /**
     * Reload configuration from disk.
     *
     * @param source Command source for output
     * @return Command result
     */
    public CommandResult reload(ServerCommandSource source) {
        // Reload config
        ModConfig.reload();

        // Clear block/item caches so they pick up new values
        ModBlocks.clearCache();
        ModItems.clearCache();

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.config_reloaded")
            .formatted(Formatting.GREEN), true);

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.config_path",
            ModConfig.getConfigPath()).formatted(Formatting.GRAY), false);

        return CommandResult.silent();
    }
}
