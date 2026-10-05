package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.command.service.TeleportHelper;
import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.compat.TeleportCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.cleanup.ChunkSanitizer;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalOwnershipManager;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.chunk.ChunkAccess;
//? if >=1.21 {
/*import net.minecraft.world.level.chunk.status.ChunkStatus;
*///?} else {
import net.minecraft.world.level.chunk.ChunkStatus;
//?}
import net.minecraft.world.level.chunk.LevelChunk;

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
 * - /pi admin sanitize <player> [radius] - Force-load and sanitize an island
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
    public void list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);
        Map<UUID, PlayerDimensionData> dimensions = registry.getAllDimensions();

        if (dimensions.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.list.empty")
                .withStyle(ChatFormatting.GRAY), false);
            return;
        }

        MutableComponent header = Component.translatable("pocketislands.command.list.header")
            .withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> header, false);

        for (PlayerDimensionData data : dimensions.values()) {
            boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
            ServerLevel world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
            int playerCount = world != null ? world.players().size() : 0;

            MutableComponent line = Component.literal(" - ")
                .append(Component.literal(data.ownerName())
                    .withStyle(loaded ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(" (")
                    .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(data.generatorType().name())
                    .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(") ")
                    .withStyle(ChatFormatting.DARK_GRAY));

            // Brackets stay outside the translations: [LOADED, 2 players] / [unloaded]
            if (loaded) {
                line.append(Component.literal("[").withStyle(ChatFormatting.GREEN));
                line.append(Component.translatable("pocketislands.command.list.loaded")
                    .withStyle(ChatFormatting.GREEN));
                if (playerCount > 0) {
                    line.append(Component.literal(", " + playerCount + " player" + (playerCount > 1 ? "s" : ""))
                        .withStyle(ChatFormatting.YELLOW));
                }
                line.append(Component.literal("]").withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("pocketislands.command.list.unloaded").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
            }

            source.sendSuccess(() -> line, false);
        }
    }

    /**
     * Show detailed information about a player's dimension.
     *
     * @param source Command source for output
     * @param playerName The owner name to look up
     * @return Command result
     */
    public CommandResult info(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();
        boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
        ServerLevel world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
        int playerCount = world != null ? world.players().size() : 0;

        // Get invitation counts
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Set<UUID> sentInvites = dataManager.getSentInvitations(data.ownerUuid());
        int inviteCount = sentInvites.size();

        // Build info display
        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.header",
            data.ownerName()).withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.owner",
            data.ownerName()), false);

        String createdStr = DATE_FORMAT.format(new Date(data.createdAt()));
        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.created",
            createdStr), false);

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.world_type",
            data.generatorType().name()), false);

        if (loaded) {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.info.status_loaded",
                playerCount), false);
        } else {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.info.status_unloaded"), false);
        }

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.invitations",
            inviteCount), false);

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.spawn",
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
    public CommandResult deletePrompt(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();

        // Play warning sound if admin is a player
        if (source.getEntity() instanceof ServerPlayer admin) {
            VisualEffects.playAdminWarningEffect(admin);
        }

        boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
        ServerLevel world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
        int playerCount = world != null ? world.players().size() : 0;

        // Warning message
        source.sendSuccess(() -> Component.translatable("pocketislands.command.delete.warning",
            data.ownerName()), false);

        if (playerCount > 0) {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.delete.players_ejected",
                playerCount).withStyle(ChatFormatting.GOLD), false);
        }

        // Confirmation prompt
        source.sendSuccess(() -> Component.translatable("pocketislands.command.delete.confirm",
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
    public CommandResult deleteConfirm(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();
        UUID ownerUuid = data.ownerUuid();
        String ownerName = data.ownerName();

        // Eject all players if dimension is loaded
        if (DimensionManager.isDimensionLoaded(ownerUuid)) {
            ServerLevel dimWorld = DimensionManager.getLoadedDimension(ownerUuid);
            if (dimWorld != null) {
                ServerLevel overworld = server.overworld();

                // Copy player list to avoid concurrent modification
                List<ServerPlayer> playersToEject = new ArrayList<>(dimWorld.players());
                for (ServerPlayer player : playersToEject) {
                    TeleportCompat.teleport(player, overworld, TeleportHelper.toWorldSpawn(overworld, player));
                    player.displayClientMessage(Component.translatable("pocketislands.message.admin_ejected")
                        .withStyle(ChatFormatting.RED), false);
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
            source.sendSuccess(() -> Component.translatable("pocketislands.command.info.cleared_portals",
                portalsCleared).withStyle(ChatFormatting.GRAY), false);
        }

        // Delete the dimension and its folder
        DimensionManager.deleteDimension(server, ownerUuid);

        // Success feedback
        if (source.getEntity() instanceof ServerPlayer admin) {
            VisualEffects.playAdminSuccessEffect(admin);
        }

        return CommandResult.successBroadcast(
            Component.translatable("pocketislands.command.info.deleted", ownerName)
                .withStyle(ChatFormatting.GREEN)
        );
    }

    /**
     * Teleport admin to a player's dimension.
     *
     * @param admin The admin teleporting
     * @param playerName The target island owner
     * @return Command result
     */
    public CommandResult teleport(ServerPlayer admin, String playerName) {
        MinecraftServer server = EntityCompat.getServer(admin);

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();

        // Load/create dimension and teleport (admin bypass - no permission check)
        ServerLevel dimension = DimensionManager.getOrCreatePlayerDimension(
            server,
            data.ownerUuid(),
            data.ownerName(),
            data.generatorType(),
            data.portalTypeIndex()
        );

        // Store return position
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.setReturnData(admin.getUUID(),
            new ReturnData(
                EntityCompat.getServerWorld(admin).dimension(),
                admin.blockPosition(),
                admin.getYRot(),
                admin.getXRot()
            ));

        // Teleport with effects
        VisualEffects.playTeleportDepartureEffects(admin);
        TeleportCompat.teleport(admin, dimension, TeleportHelper.toBlockPos(dimension, data.spawnPoint(), admin));
        VisualEffects.playTeleportArrivalEffects(admin);

        return CommandResult.successBroadcast(
            Component.translatable("pocketislands.command.info.teleported", data.ownerName())
                .withStyle(ChatFormatting.GREEN)
        );
    }

    /** Maximum chunk radius allowed for a single sanitize invocation. */
    public static final int MAX_SANITIZE_RADIUS = 16;

    /**
     * Force-load a player's dimension and sanitize every chunk in a bounded
     * region around spawn. Bypasses the config gates — an admin invoking
     * this command explicitly wants the full sweep to run.
     *
     * @param source     command source for feedback
     * @param playerName owner name to sanitize
     * @param radius     chunk radius around (0, 0); the swept region is
     *                   ({@code 2*radius + 1})² chunks
     * @return command result
     */
    public CommandResult sanitize(CommandSourceStack source, String playerName, int radius) {
        if (radius < 0) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.sanitize.error.negative_radius")
            );
        }
        if (radius > MAX_SANITIZE_RADIUS) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.sanitize.error.radius_too_large", MAX_SANITIZE_RADIUS)
            );
        }

        MinecraftServer server = source.getServer();

        Optional<PlayerDimensionData> optData = playerLookup.findDimensionByOwnerName(server, playerName);
        if (optData.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.no_dimension_for_player", playerName)
            );
        }

        PlayerDimensionData data = optData.get();

        // Force-load the dimension. Safe: getOrCreatePlayerDimension is
        // idempotent for existing dimensions and returns the loaded world.
        ServerLevel world = DimensionManager.getOrCreatePlayerDimension(
            server,
            data.ownerUuid(),
            data.ownerName(),
            data.generatorType(),
            data.portalTypeIndex()
        );

        int diameter = 2 * radius + 1;
        int planned = diameter * diameter;

        source.sendSuccess(() -> Component.translatable(
            "pocketislands.command.sanitize.start",
            data.ownerName(), planned
        ).withStyle(ChatFormatting.GRAY), false);

        int chunksScanned = 0;
        int chunksTouched = 0;
        int totalOrphanBEs = 0;
        int totalOrphanBlocks = 0;
        int totalOrphanItems = 0;

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                ChunkAccess raw = world.getChunk(cx, cz, ChunkStatus.FULL, true);
                if (!(raw instanceof LevelChunk chunk)) {
                    continue;
                }
                chunksScanned++;

                ChunkSanitizer.Result result = ChunkSanitizer.sanitizeLoadedChunk(world, chunk, true, true);
                if (result.anyRemoved()) {
                    chunksTouched++;
                    totalOrphanBEs += result.orphanBlockEntities();
                    totalOrphanBlocks += result.orphanBlocks();
                    totalOrphanItems += result.orphanItems();
                }
            }
        }

        final int scanned = chunksScanned;
        final int touched = chunksTouched;
        final int bes = totalOrphanBEs;
        final int blocks = totalOrphanBlocks;
        final int items = totalOrphanItems;

        source.sendSuccess(() -> Component.translatable(
            "pocketislands.command.sanitize.summary",
            data.ownerName(), scanned, touched, bes, blocks, items
        ).withStyle(touched > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);

        return CommandResult.silent();
    }

    /**
     * Reload configuration from disk.
     *
     * @param source Command source for output
     * @return Command result
     */
    public CommandResult reload(CommandSourceStack source) {
        // Reload config
        ModConfig.reload();

        // Clear block/item caches so they pick up new values
        ModBlocks.clearCache();
        ModItems.clearCache();

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.config_reloaded")
            .withStyle(ChatFormatting.GREEN), true);

        source.sendSuccess(() -> Component.translatable("pocketislands.command.info.config_path",
            ModConfig.getConfigPath()).withStyle(ChatFormatting.GRAY), false);

        return CommandResult.silent();
    }
}
