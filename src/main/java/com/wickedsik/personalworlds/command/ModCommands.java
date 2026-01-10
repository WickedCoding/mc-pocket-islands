package com.wickedsik.personalworlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionMetadataFile;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.portal.PortalOwnershipManager;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.util.PerformanceMonitor;
import com.wickedsik.personalworlds.util.PermissionHelper;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Command registration and handlers for Pocket Islands.
 *
 * Commands:
 * - /pi create [type]       - Create pocket island (OP 2)
 * - /pi enter               - Enter your island (OP 2)
 * - /pi leave               - Leave to overworld (OP 2)
 * - /pi list                - List all islands (OP 2)
 * - /pi info                - Show island count (OP 2)
 * - /pi invite <player>     - Invite a player (no permission)
 * - /pi uninvite <player>   - Revoke invitation (no permission)
 * - /pi invites             - Show your invitations (no permission)
 * - /pi go <player>         - Visit player's island (no permission)
 * - /pi admin list          - Admin list all islands
 * - /pi admin info <player> - Admin view island details
 * - /pi admin delete <player> [confirm] - Admin delete island
 * - /pi admin tp <player>   - Admin teleport to island
 * - /pi admin reload        - Reload configuration
 */
public class ModCommands {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pi")
                // === Development/Testing Commands (OP level 2+) ===
                .then(CommandManager.literal("create")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> createDimension(ctx.getSource(), "OVERWORLD"))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> createDimension(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")
                        ))
                    )
                )

                .then(CommandManager.literal("enter")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> enterDimension(ctx.getSource()))
                )

                .then(CommandManager.literal("leave")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> leaveDimension(ctx.getSource()))
                )

                // === Player Commands (No Permission Required) ===
                .then(CommandManager.literal("invite")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> invitePlayer(
                            ctx.getSource(),
                            EntityArgumentType.getPlayer(ctx, "player")
                        ))
                    )
                )

                .then(CommandManager.literal("uninvite")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> uninvitePlayer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "player")
                        ))
                    )
                )

                .then(CommandManager.literal("invites")
                    .executes(ctx -> showInvitations(ctx.getSource()))
                )

                .then(CommandManager.literal("go")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> goToPlayer(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "player")
                        ))
                    )
                )

                // === Admin Commands ===
                .then(CommandManager.literal("admin")
                    .then(CommandManager.literal("list")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_LIST, PermissionHelper.DEFAULT_ADMIN_LIST_LEVEL))
                        .executes(ctx -> adminList(ctx.getSource()))
                    )

                    .then(CommandManager.literal("info")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_INFO, PermissionHelper.DEFAULT_ADMIN_INFO_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> adminInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(CommandManager.literal("delete")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_DELETE, PermissionHelper.DEFAULT_ADMIN_DELETE_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> adminDeletePrompt(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                            .then(CommandManager.literal("confirm")
                                .executes(ctx -> adminDeleteConfirm(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player")
                                ))
                            )
                        )
                    )

                    .then(CommandManager.literal("tp")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_TELEPORT, PermissionHelper.DEFAULT_ADMIN_TELEPORT_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> adminTeleport(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(CommandManager.literal("reload")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_RELOAD, PermissionHelper.DEFAULT_ADMIN_RELOAD_LEVEL))
                        .executes(ctx -> adminReload(ctx.getSource()))
                    )
                )

                // === Debug/Testing Commands (OP level 4) ===
                .then(CommandManager.literal("debug")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(CommandManager.literal("perf")
                        .then(CommandManager.literal("enable")
                            .executes(ctx -> {
                                PerformanceMonitor.enable();
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("personalworlds.command.perf.enabled")
                                        .formatted(Formatting.GREEN), true);
                                return 1;
                            }))
                        .then(CommandManager.literal("disable")
                            .executes(ctx -> {
                                PerformanceMonitor.disable();
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("personalworlds.command.perf.disabled")
                                        .formatted(Formatting.YELLOW), true);
                                return 1;
                            }))
                        .then(CommandManager.literal("status")
                            .executes(ctx -> {
                                String status = PerformanceMonitor.getStatusSummary(ctx.getSource().getServer());
                                for (String line : status.split("\n")) {
                                    final String finalLine = line;
                                    ctx.getSource().sendFeedback(() -> Text.literal(finalLine), false);
                                }
                                return 1;
                            }))
                        .then(CommandManager.literal("reset")
                            .executes(ctx -> {
                                PerformanceMonitor.resetCounters();
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("personalworlds.command.perf.reset")
                                        .formatted(Formatting.YELLOW), true);
                                return 1;
                            }))
                    )
                )
        );
    }

    // ==================== Development Commands ====================

    private static int createDimension(ServerCommandSource source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        WorldGenType type = WorldGenType.fromString(typeStr);
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                source.getServer(),
                playerUuid,
                playerName,
                type,
                0  // Default portal type for /pw create command
            );

            TeleportTarget target = new TeleportTarget(
                new Vec3d(0.5, 65, 0.5),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch()
            );
            FabricDimensions.teleport(player, dimension, target);

            source.sendFeedback(() -> Text.translatable(
                "personalworlds.command.info.dimension_created", type.name()
            ), true);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.translatable("personalworlds.command.error.create_failed", e.getMessage()));
            return 0;
        }
    }

    private static int enterDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        UUID playerUuid = player.getUuid();
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());

        if (!registry.hasDimension(playerUuid)) {
            source.sendError(Text.translatable(
                "personalworlds.command.error.no_dimension"
            ));
            return 0;
        }

        PlayerDimensionData data = registry.getDimensionData(playerUuid).orElse(null);
        if (data == null) {
            source.sendError(Text.translatable("personalworlds.command.error.load_failed"));
            return 0;
        }

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                source.getServer(),
                playerUuid,
                player.getName().getString(),
                data.generatorType(),
                data.portalTypeIndex()  // Use portal type from dimension data
            );

            TeleportTarget target = new TeleportTarget(
                new Vec3d(
                    data.spawnPoint().getX() + 0.5,
                    data.spawnPoint().getY(),
                    data.spawnPoint().getZ() + 0.5
                ),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch()
            );
            FabricDimensions.teleport(player, dimension, target);

            source.sendFeedback(() -> Text.translatable("personalworlds.command.enter.success"), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.translatable("personalworlds.command.error.enter_failed", e.getMessage()));
            return 0;
        }
    }

    private static int leaveDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        // Use PortalHelper to properly handle return position
        PortalHelper.teleportToReturnPosition(player, source.getServer());

        return 1;
    }

    // ==================== Player Commands (Invitation System) ====================

    private static int invitePlayer(ServerCommandSource source, ServerPlayerEntity guest) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        InvitationManager.invite(source.getServer(), owner, guest);
        return 1;
    }

    private static int uninvitePlayer(ServerCommandSource source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        UUID guestUuid = null;
        String resolvedName = guestName;

        ServerPlayerEntity onlineGuest = source.getServer().getPlayerManager().getPlayer(guestName);
        if (onlineGuest != null) {
            guestUuid = onlineGuest.getUuid();
            resolvedName = onlineGuest.getName().getString();
        } else {
            PlayerDataManager dataManager = PlayerDataManager.get(source.getServer());
            Set<UUID> sentInvites = dataManager.getSentInvitations(owner.getUuid());
            DimensionRegistry registry = DimensionRegistry.get(source.getServer());

            for (UUID uuid : sentInvites) {
                Optional<PlayerDimensionData> data = registry.getDimensionData(uuid);
                if (data.isPresent() && data.get().ownerName().equalsIgnoreCase(guestName)) {
                    guestUuid = uuid;
                    resolvedName = data.get().ownerName();
                    break;
                }
            }
        }

        if (guestUuid == null) {
            source.sendError(Text.translatable("personalworlds.command.error.player_not_found", guestName));
            return 0;
        }

        InvitationManager.uninvite(source.getServer(), owner, guestUuid, resolvedName);
        return 1;
    }

    private static int showInvitations(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        InvitationManager.showInvitations(player);
        return 1;
    }

    private static int goToPlayer(ServerCommandSource source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        UUID targetUuid = null;
        String resolvedName = targetName;

        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetName);
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUuid();
            resolvedName = onlineTarget.getName().getString();
        } else {
            DimensionRegistry registry = DimensionRegistry.get(source.getServer());
            for (PlayerDimensionData data : registry.getAllDimensions().values()) {
                if (data.ownerName().equalsIgnoreCase(targetName)) {
                    targetUuid = data.ownerUuid();
                    resolvedName = data.ownerName();
                    break;
                }
            }
        }

        if (targetUuid == null) {
            source.sendError(Text.translatable("personalworlds.command.error.player_not_found", targetName));
            return 0;
        }

        if (!InvitationManager.canVisit(source.getServer(), player.getUuid(), targetUuid)) {
            source.sendError(Text.translatable("personalworlds.command.error.not_invited", resolvedName));
            return 0;
        }

        boolean success = PortalHelper.teleportToDimension(player, source.getServer(), targetUuid);
        return success ? 1 : 0;
    }

    // ==================== Admin Commands ====================

    /**
     * List all registered player dimensions with status.
     */
    private static int adminList(ServerCommandSource source) {
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        Map<UUID, PlayerDimensionData> dimensions = registry.getAllDimensions();

        if (dimensions.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.list.empty")
                .formatted(Formatting.GRAY), false);
            return 1;
        }

        int total = dimensions.size();
        long loadedCount = dimensions.values().stream()
            .filter(d -> DimensionManager.isDimensionLoaded(d.ownerUuid()))
            .count();

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

        return 1;
    }

    /**
     * Show detailed information about a player's dimension.
     */
    private static int adminInfo(ServerCommandSource source, String playerName) {
        MinecraftServer server = source.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);

        // Find player by name
        PlayerDimensionData data = null;
        for (PlayerDimensionData d : registry.getAllDimensions().values()) {
            if (d.ownerName().equalsIgnoreCase(playerName)) {
                data = d;
                break;
            }
        }

        if (data == null) {
            source.sendError(Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName));
            return 0;
        }

        // Capture as final for lambda usage
        final PlayerDimensionData dimData = data;

        boolean loaded = DimensionManager.isDimensionLoaded(dimData.ownerUuid());
        ServerWorld world = loaded ? DimensionManager.getLoadedDimension(dimData.ownerUuid()) : null;
        int playerCount = world != null ? world.getPlayers().size() : 0;

        // Get invitation counts
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Set<UUID> sentInvites = dataManager.getSentInvitations(dimData.ownerUuid());
        int inviteCount = sentInvites.size();

        // Build info display
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.header",
            dimData.ownerName()).formatted(Formatting.GOLD), false);

        // Owner info
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.owner",
            dimData.ownerName()), false);

        // Created date
        String createdStr = DATE_FORMAT.format(new Date(dimData.createdAt()));
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.created",
            createdStr), false);

        // World type
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.world_type",
            dimData.generatorType().name()), false);

        // Status
        if (loaded) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.info.status_loaded",
                playerCount), false);
        } else {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.info.status_unloaded"), false);
        }

        // Invitations
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.invitations",
            inviteCount), false);

        // Spawn point
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.spawn",
            dimData.spawnPoint().getX(),
            dimData.spawnPoint().getY(),
            dimData.spawnPoint().getZ()), false);

        return 1;
    }

    /**
     * Prompt for dimension deletion with confirmation.
     */
    private static int adminDeletePrompt(ServerCommandSource source, String playerName) {
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());

        // Find player by name
        PlayerDimensionData data = null;
        for (PlayerDimensionData d : registry.getAllDimensions().values()) {
            if (d.ownerName().equalsIgnoreCase(playerName)) {
                data = d;
                break;
            }
        }

        if (data == null) {
            source.sendError(Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName));
            return 0;
        }

        // Capture as final for lambda usage
        final PlayerDimensionData finalData = data;

        // Play warning sound if admin is a player
        if (source.getEntity() instanceof ServerPlayerEntity admin) {
            VisualEffects.playAdminWarningEffect(admin);
        }

        boolean loaded = DimensionManager.isDimensionLoaded(finalData.ownerUuid());
        ServerWorld world = loaded ? DimensionManager.getLoadedDimension(finalData.ownerUuid()) : null;
        int playerCount = world != null ? world.getPlayers().size() : 0;

        // Warning message
        source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.warning",
            finalData.ownerName()), false);

        if (playerCount > 0) {
            source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.players_ejected",
                playerCount).formatted(Formatting.GOLD), false);
        }

        // Confirmation button
        final String finalName = finalData.ownerName();
        source.sendFeedback(() -> Text.translatable("personalworlds.command.delete.confirm",
            finalName), false);

        return 1;
    }

    /**
     * Actually delete a player's dimension after confirmation.
     */
    private static int adminDeleteConfirm(ServerCommandSource source, String playerName) {
        MinecraftServer server = source.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);

        // Find player by name
        PlayerDimensionData data = null;
        for (PlayerDimensionData d : registry.getAllDimensions().values()) {
            if (d.ownerName().equalsIgnoreCase(playerName)) {
                data = d;
                break;
            }
        }

        if (data == null) {
            source.sendError(Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName));
            return 0;
        }

        UUID ownerUuid = data.ownerUuid();
        String ownerName = data.ownerName();

        // Eject all players if dimension is loaded
        if (DimensionManager.isDimensionLoaded(ownerUuid)) {
            ServerWorld dimWorld = DimensionManager.getLoadedDimension(ownerUuid);
            if (dimWorld != null) {
                ServerWorld overworld = server.getOverworld();
                Vec3d spawnPos = Vec3d.ofCenter(overworld.getSpawnPos());

                // Copy player list to avoid concurrent modification
                List<ServerPlayerEntity> playersToEject = new ArrayList<>(dimWorld.getPlayers());
                for (ServerPlayerEntity player : playersToEject) {
                    TeleportTarget target = new TeleportTarget(
                        spawnPos,
                        Vec3d.ZERO,
                        player.getYaw(),
                        player.getPitch()
                    );
                    FabricDimensions.teleport(player, overworld, target);
                    player.sendMessage(Text.translatable("personalworlds.message.admin_ejected")
                        .formatted(Formatting.RED), false);
                }
            }
        }

        // Remove from registry FIRST (before deletion)
        // This prevents the dimension from being restored on next startup
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
        // For loaded dimensions: Fantasy handles safe unload + folder deletion
        // For unloaded dimensions: Direct folder deletion (no race condition)
        // Note: This also deletes the metadata file inside the dimension folder
        DimensionManager.deleteDimension(server, ownerUuid);

        // Success feedback
        if (source.getEntity() instanceof ServerPlayerEntity admin) {
            VisualEffects.playAdminSuccessEffect(admin);
        }

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.deleted",
            ownerName).formatted(Formatting.GREEN), true);

        return 1;
    }

    /**
     * Teleport admin to a player's dimension.
     */
    private static int adminTeleport(ServerCommandSource source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity admin)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        DimensionRegistry registry = DimensionRegistry.get(server);

        // Find player by name
        PlayerDimensionData data = null;
        for (PlayerDimensionData d : registry.getAllDimensions().values()) {
            if (d.ownerName().equalsIgnoreCase(playerName)) {
                data = d;
                break;
            }
        }

        if (data == null) {
            source.sendError(Text.translatable("personalworlds.command.error.no_dimension_for_player", playerName));
            return 0;
        }

        // Capture as final for lambda usage
        final PlayerDimensionData dimData = data;

        // Load/create dimension and teleport (admin bypass - no permission check)
        ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
            server,
            dimData.ownerUuid(),
            dimData.ownerName(),
            dimData.generatorType(),
            dimData.portalTypeIndex()  // Use portal type from dimension data
        );

        // Store return position
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        dataManager.setReturnData(admin.getUuid(),
            new com.wickedsik.personalworlds.player.ReturnData(
                admin.getServerWorld().getRegistryKey(),
                admin.getBlockPos(),
                admin.getYaw(),
                admin.getPitch()
            ));

        // Teleport with effects
        VisualEffects.playTeleportDepartureEffects(admin);

        TeleportTarget target = new TeleportTarget(
            new Vec3d(
                dimData.spawnPoint().getX() + 0.5,
                dimData.spawnPoint().getY(),
                dimData.spawnPoint().getZ() + 0.5
            ),
            Vec3d.ZERO,
            admin.getYaw(),
            admin.getPitch()
        );
        FabricDimensions.teleport(admin, dimension, target);

        VisualEffects.playTeleportArrivalEffects(admin);

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.teleported",
            dimData.ownerName()).formatted(Formatting.GREEN), true);

        return 1;
    }

    /**
     * Reload configuration from disk.
     */
    private static int adminReload(ServerCommandSource source) {
        // Reload config
        ModConfig.reload();

        // Clear block/item caches so they pick up new values
        ModBlocks.clearCache();
        ModItems.clearCache();

        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.config_reloaded")
            .formatted(Formatting.GREEN), true);

        // Show config file path
        source.sendFeedback(() -> Text.translatable("personalworlds.command.info.config_path",
            ModConfig.getConfigPath()).formatted(Formatting.GRAY), false);

        return 1;
    }
}
