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
 * Command registration and handlers for PersonalWorlds.
 *
 * Commands:
 * - /pw create [type]       - Create personal dimension (OP 2)
 * - /pw enter               - Enter your dimension (OP 2)
 * - /pw leave               - Leave to overworld (OP 2)
 * - /pw list                - List all dimensions (OP 2)
 * - /pw info                - Show dimension count (OP 2)
 * - /pw invite <player>     - Invite a player (no permission)
 * - /pw uninvite <player>   - Revoke invitation (no permission)
 * - /pw invites             - Show your invitations (no permission)
 * - /pw go <player>         - Visit player's dimension (no permission)
 * - /pw admin list          - Admin list all dimensions
 * - /pw admin info <player> - Admin view dimension details
 * - /pw admin delete <player> [confirm] - Admin delete dimension
 * - /pw admin tp <player>   - Admin teleport to dimension
 * - /pw admin reload        - Reload configuration
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
            CommandManager.literal("pw")
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
                                    Text.literal("Performance monitoring enabled")
                                        .formatted(Formatting.GREEN), true);
                                return 1;
                            }))
                        .then(CommandManager.literal("disable")
                            .executes(ctx -> {
                                PerformanceMonitor.disable();
                                ctx.getSource().sendFeedback(() ->
                                    Text.literal("Performance monitoring disabled")
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
                                    Text.literal("Performance counters reset")
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
            source.sendError(Text.literal("Command must be run by a player"));
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

            source.sendFeedback(() -> Text.literal(
                "Created dimension with " + type.name() + " generator. Welcome to your world!"
            ), true);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to create dimension: " + e.getMessage()));
            return 0;
        }
    }

    private static int enterDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        UUID playerUuid = player.getUuid();
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());

        if (!registry.hasDimension(playerUuid)) {
            source.sendError(Text.literal(
                "You don't have a personal dimension. Use /pw create first."
            ));
            return 0;
        }

        PlayerDimensionData data = registry.getDimensionData(playerUuid).orElse(null);
        if (data == null) {
            source.sendError(Text.literal("Failed to load dimension data"));
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

            source.sendFeedback(() -> Text.literal("Entered your personal dimension"), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to enter dimension: " + e.getMessage()));
            return 0;
        }
    }

    private static int leaveDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        // Use PortalHelper to properly handle return position
        PortalHelper.teleportToReturnPosition(player, source.getServer());

        return 1;
    }

    // ==================== Player Commands (Invitation System) ====================

    private static int invitePlayer(ServerCommandSource source, ServerPlayerEntity guest) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        InvitationManager.invite(source.getServer(), owner, guest);
        return 1;
    }

    private static int uninvitePlayer(ServerCommandSource source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.literal("Command must be run by a player"));
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
            source.sendError(Text.literal("Player not found: " + guestName));
            return 0;
        }

        InvitationManager.uninvite(source.getServer(), owner, guestUuid, resolvedName);
        return 1;
    }

    private static int showInvitations(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        InvitationManager.showInvitations(player);
        return 1;
    }

    private static int goToPlayer(ServerCommandSource source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
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
            source.sendError(Text.literal("Player not found: " + targetName));
            return 0;
        }

        if (!InvitationManager.canVisit(source.getServer(), player.getUuid(), targetUuid)) {
            source.sendError(Text.literal("You have not been invited by ")
                .append(Text.literal(resolvedName).formatted(Formatting.YELLOW)));
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
            source.sendFeedback(() -> Text.literal("No player dimensions registered")
                .formatted(Formatting.GRAY), false);
            return 1;
        }

        int total = dimensions.size();
        long loadedCount = dimensions.values().stream()
            .filter(d -> DimensionManager.isDimensionLoaded(d.ownerUuid()))
            .count();

        MutableText header = Text.literal("=== Player Dimensions (")
            .formatted(Formatting.GOLD)
            .append(Text.literal(String.valueOf(total)).formatted(Formatting.WHITE))
            .append(Text.literal(" total, ").formatted(Formatting.GOLD))
            .append(Text.literal(String.valueOf(loadedCount)).formatted(Formatting.GREEN))
            .append(Text.literal(" loaded) ===").formatted(Formatting.GOLD));

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
                line.append(Text.literal("[LOADED")
                    .formatted(Formatting.GREEN));
                if (playerCount > 0) {
                    line.append(Text.literal(", " + playerCount + " player" + (playerCount > 1 ? "s" : ""))
                        .formatted(Formatting.YELLOW));
                }
                line.append(Text.literal("]").formatted(Formatting.GREEN));
            } else {
                line.append(Text.literal("[unloaded]").formatted(Formatting.GRAY));
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
            source.sendError(Text.literal("No dimension found for player: " + playerName));
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
        MutableText header = Text.literal("=== " + dimData.ownerName() + "'s Dimension ===")
            .formatted(Formatting.GOLD);
        source.sendFeedback(() -> header, false);

        // Owner info
        source.sendFeedback(() -> Text.literal("Owner: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(dimData.ownerName()).formatted(Formatting.WHITE))
            .append(Text.literal(" (" + dimData.ownerUuid().toString().substring(0, 8) + "...)")
                .formatted(Formatting.DARK_GRAY)), false);

        // Created date
        String createdStr = DATE_FORMAT.format(new Date(dimData.createdAt()));
        source.sendFeedback(() -> Text.literal("Created: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(createdStr).formatted(Formatting.WHITE)), false);

        // World type
        source.sendFeedback(() -> Text.literal("World type: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(dimData.generatorType().name()).formatted(Formatting.AQUA)), false);

        // Status
        MutableText statusLine = Text.literal("Status: ").formatted(Formatting.GRAY);
        if (loaded) {
            statusLine.append(Text.literal("LOADED").formatted(Formatting.GREEN));
            if (playerCount > 0) {
                statusLine.append(Text.literal(" (" + playerCount + " player" + (playerCount > 1 ? "s" : "") + " inside)")
                    .formatted(Formatting.YELLOW));
            }
        } else {
            statusLine.append(Text.literal("Unloaded").formatted(Formatting.GRAY));
        }
        source.sendFeedback(() -> statusLine, false);

        // Invitations
        source.sendFeedback(() -> Text.literal("Invitations sent: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(inviteCount)).formatted(Formatting.WHITE)), false);

        // Spawn point
        source.sendFeedback(() -> Text.literal("Spawn point: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(String.format("(%d, %d, %d)",
                dimData.spawnPoint().getX(),
                dimData.spawnPoint().getY(),
                dimData.spawnPoint().getZ())).formatted(Formatting.WHITE)), false);

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
            source.sendError(Text.literal("No dimension found for player: " + playerName));
            return 0;
        }

        // Play warning sound if admin is a player
        if (source.getEntity() instanceof ServerPlayerEntity admin) {
            VisualEffects.playAdminWarningEffect(admin);
        }

        boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
        ServerWorld world = loaded ? DimensionManager.getLoadedDimension(data.ownerUuid()) : null;
        int playerCount = world != null ? world.getPlayers().size() : 0;

        // Warning message
        MutableText warning = Text.literal("WARNING: ").formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("This will permanently delete ")
                .formatted(Formatting.RED))
            .append(Text.literal(data.ownerName() + "'s").formatted(Formatting.YELLOW))
            .append(Text.literal(" dimension!").formatted(Formatting.RED));

        source.sendFeedback(() -> warning, false);

        if (playerCount > 0) {
            source.sendFeedback(() -> Text.literal("  " + playerCount + " player(s) will be ejected!")
                .formatted(Formatting.GOLD), false);
        }

        // Confirmation button
        final String finalName = data.ownerName();
        MutableText confirmLine = Text.literal("Run: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal("[CONFIRM DELETE]")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/pw admin delete " + finalName + " confirm"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Click to permanently delete this dimension")
                            .formatted(Formatting.RED)))));

        source.sendFeedback(() -> confirmLine, false);

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
            source.sendError(Text.literal("No dimension found for player: " + playerName));
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
                    player.sendMessage(Text.literal("You have been ejected - dimension deleted by admin")
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
            source.sendFeedback(() -> Text.literal("Cleared " + portalsCleared + " portal ownership record(s)")
                .formatted(Formatting.GRAY), false);
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

        source.sendFeedback(() -> Text.literal("Deleted " + ownerName + "'s dimension")
            .formatted(Formatting.GREEN), true);

        return 1;
    }

    /**
     * Teleport admin to a player's dimension.
     */
    private static int adminTeleport(ServerCommandSource source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity admin)) {
            source.sendError(Text.literal("Command must be run by a player"));
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
            source.sendError(Text.literal("No dimension found for player: " + playerName));
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

        source.sendFeedback(() -> Text.literal("Teleported to " + dimData.ownerName() + "'s dimension")
            .formatted(Formatting.GREEN), true);

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

        source.sendFeedback(() -> Text.literal("Configuration reloaded")
            .formatted(Formatting.GREEN), true);

        // Show config file path
        source.sendFeedback(() -> Text.literal("Config file: " + ModConfig.getConfigPath())
            .formatted(Formatting.GRAY), false);

        return 1;
    }
}
