package com.wickedsik.personalworlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TestCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pw")
                // Admin commands (OP level 2+)
                .then(CommandManager.literal("create")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> createDimension(ctx.getSource(), "OVERWORLD"))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> createDimension(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")
                        ))
                    )
                )

                .then(CommandManager.literal("enter")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> enterDimension(ctx.getSource()))
                )

                .then(CommandManager.literal("leave")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> leaveDimension(ctx.getSource()))
                )

                .then(CommandManager.literal("list")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> listDimensions(ctx.getSource()))
                )

                .then(CommandManager.literal("info")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> showInfo(ctx.getSource()))
                )

                // Player commands (no permission required)
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
        );
    }

    // --- Admin Commands ---

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
                type
            );

            // Teleport player to the dimension using FabricDimensions
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
                data.generatorType()
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

        ServerWorld overworld = source.getServer().getOverworld();
        Vec3d spawnPos = Vec3d.ofCenter(overworld.getSpawnPos());

        TeleportTarget target = new TeleportTarget(
            spawnPos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        FabricDimensions.teleport(player, overworld, target);

        source.sendFeedback(() -> Text.literal("Returned to overworld"), true);
        return 1;
    }

    private static int listDimensions(ServerCommandSource source) {
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        Map<UUID, PlayerDimensionData> dimensions = registry.getAllDimensions();

        if (dimensions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No player dimensions registered"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Registered dimensions:\n");
        for (PlayerDimensionData data : dimensions.values()) {
            boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
            sb.append(String.format("  - %s (%s) [%s]\n",
                data.ownerName(),
                data.generatorType().name(),
                loaded ? "LOADED" : "unloaded"
            ));
        }

        final String message = sb.toString();
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private static int showInfo(ServerCommandSource source) {
        int loaded = DimensionManager.getLoadedDimensionCount();
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        int total = registry.getAllDimensions().size();

        String info = String.format(
            "Personal Worlds Status:\n  Total registered: %d\n  Currently loaded: %d",
            total, loaded
        );

        source.sendFeedback(() -> Text.literal(info), false);
        return 1;
    }

    // --- Player Commands (Invitation System) ---

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

        // Find guest UUID by name - check online players first
        UUID guestUuid = null;
        String resolvedName = guestName;

        // Try online player
        ServerPlayerEntity onlineGuest = source.getServer().getPlayerManager().getPlayer(guestName);
        if (onlineGuest != null) {
            guestUuid = onlineGuest.getUuid();
            resolvedName = onlineGuest.getName().getString();
        } else {
            // Try to find by sent invitations (for offline players)
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

        // Find target player's UUID
        UUID targetUuid = null;
        String resolvedName = targetName;

        // Try online player
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetName);
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUuid();
            resolvedName = onlineTarget.getName().getString();
        } else {
            // Try to find in dimension registry (for offline players)
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

        // Check permission
        if (!InvitationManager.canVisit(source.getServer(), player.getUuid(), targetUuid)) {
            source.sendError(Text.literal("You have not been invited by ")
                .append(Text.literal(resolvedName).formatted(Formatting.YELLOW)));
            return 0;
        }

        // Teleport
        boolean success = PortalHelper.teleportToDimension(player, source.getServer(), targetUuid);
        return success ? 1 : 0;
    }
}
