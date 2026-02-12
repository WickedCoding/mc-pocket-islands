package com.wickedsik.personalworlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wickedsik.personalworlds.command.executor.AdminCommandExecutor;
import com.wickedsik.personalworlds.command.executor.DebugCommandExecutor;
import com.wickedsik.personalworlds.command.executor.DevCommandExecutor;
import com.wickedsik.personalworlds.command.executor.PlayerCommandExecutor;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.compat.CommandCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.util.PermissionHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Command registration and delegation for Pocket Islands.
 *
 * This class registers all /pi commands and delegates execution to specialized executors:
 * - {@link DevCommandExecutor} - create, enter, leave (OP 2)
 * - {@link PlayerCommandExecutor} - invite, uninvite, invites, portals (no permission)
 * - {@link AdminCommandExecutor} - list, info, delete, tp, reload (configurable permission)
 * - {@link DebugCommandExecutor} - perf commands (OP 4)
 */
public class ModCommands {

    // Executors
    private static DevCommandExecutor devExecutor;
    private static PlayerCommandExecutor playerExecutor;
    private static AdminCommandExecutor adminExecutor;
    private static DebugCommandExecutor debugExecutor;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            initializeExecutors();
            registerCommands(dispatcher);
        });
    }

    private static void initializeExecutors() {
        // Services
        PlayerLookupService playerLookup = new PlayerLookupService();

        // Initialize executors with dependencies
        devExecutor = new DevCommandExecutor();
        playerExecutor = new PlayerCommandExecutor(playerLookup);
        adminExecutor = new AdminCommandExecutor(playerLookup);
        debugExecutor = new DebugCommandExecutor();
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pi")
                // === Development/Testing Commands (OP level 2+) ===
                .then(CommandManager.literal("create")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleCreate(ctx.getSource(), "OVERWORLD"))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> handleCreate(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")
                        ))
                    )
                )

                .then(CommandManager.literal("enter")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleEnter(ctx.getSource()))
                )

                .then(CommandManager.literal("leave")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleLeave(ctx.getSource()))
                )

                // === Player Commands (No Permission Required) ===
                .then(buildInviteCommand())

                .then(CommandManager.literal("uninvite")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleUninvite(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "player")
                        ))
                    )
                )

                // Conditionally add togglewelcome command
                .then(buildToggleWelcomeCommand())

                .then(CommandManager.literal("invites")
                    .executes(ctx -> handleInvites(ctx.getSource()))
                )

                .then(CommandManager.literal("portals")
                    .executes(ctx -> handlePortals(ctx.getSource()))
                )

                // === Admin Commands ===
                .then(CommandManager.literal("admin")
                    .then(CommandManager.literal("list")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_LIST, PermissionHelper.DEFAULT_ADMIN_LIST_LEVEL))
                        .executes(ctx -> handleAdminList(ctx.getSource()))
                    )

                    .then(CommandManager.literal("info")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_INFO, PermissionHelper.DEFAULT_ADMIN_INFO_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(CommandManager.literal("delete")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_DELETE, PermissionHelper.DEFAULT_ADMIN_DELETE_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminDeletePrompt(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                            .then(CommandManager.literal("confirm")
                                .executes(ctx -> handleAdminDeleteConfirm(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player")
                                ))
                            )
                        )
                    )

                    .then(CommandManager.literal("tp")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_TELEPORT, PermissionHelper.DEFAULT_ADMIN_TELEPORT_LEVEL))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminTeleport(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(CommandManager.literal("reload")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_RELOAD, PermissionHelper.DEFAULT_ADMIN_RELOAD_LEVEL))
                        .executes(ctx -> handleAdminReload(ctx.getSource()))
                    )
                )

                // === Debug/Testing Commands (OP level 4) ===
                .then(CommandManager.literal("debug")
                    .requires(CommandCompat.requiresLevel(4))
                    .then(CommandManager.literal("perf")
                        .then(CommandManager.literal("enable")
                            .executes(ctx -> debugExecutor.enablePerf().applyTo(ctx.getSource())))
                        .then(CommandManager.literal("disable")
                            .executes(ctx -> debugExecutor.disablePerf().applyTo(ctx.getSource())))
                        .then(CommandManager.literal("status")
                            .executes(ctx -> {
                                debugExecutor.showStatus(ctx.getSource(), ctx.getSource().getServer());
                                return CommandResult.SUCCESS;
                            }))
                        .then(CommandManager.literal("reset")
                            .executes(ctx -> debugExecutor.resetCounters().applyTo(ctx.getSource())))
                    )
                )
        );
    }

    // ==================== Command Builders ====================

    /**
     * Build the invite command with optional "always" subcommand.
     * The "always" variant is only available when enableAlwaysWelcome is true.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildInviteCommand() {
        var playerArg = CommandManager.argument("player", EntityArgumentType.player())
            .executes(ctx -> handleInvite(
                ctx.getSource(),
                EntityArgumentType.getPlayer(ctx, "player"),
                false
            ));

        // Conditionally add "always" subcommand
        if (ModConfig.get().enableAlwaysWelcome) {
            playerArg = playerArg.then(CommandManager.literal("always")
                .executes(ctx -> handleInvite(
                    ctx.getSource(),
                    EntityArgumentType.getPlayer(ctx, "player"),
                    true
                ))
            );
        }

        return CommandManager.literal("invite").then(playerArg);
    }

    /**
     * Build the togglewelcome command.
     * Returns a no-op command if enableAlwaysWelcome is false.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildToggleWelcomeCommand() {
        if (!ModConfig.get().enableAlwaysWelcome) {
            // Return a hidden command that does nothing (won't show in tab-complete)
            return CommandManager.literal("togglewelcome")
                .requires(source -> false);  // Never passes requirements check
        }

        return CommandManager.literal("togglewelcome")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> handleToggleWelcome(
                    ctx.getSource(),
                    StringArgumentType.getString(ctx, "player")
                ))
            );
    }

    // ==================== Thin Adapter Methods ====================

    private static int handleCreate(ServerCommandSource source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.createDimension(player, typeStr).applyTo(source);
    }

    private static int handleEnter(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.enterDimension(player).applyTo(source);
    }

    private static int handleLeave(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.leaveDimension(player).applyTo(source);
    }

    private static int handleInvite(ServerCommandSource source, ServerPlayerEntity guest, boolean alwaysWelcome) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.invite(owner, guest, alwaysWelcome).applyTo(source);
    }

    private static int handleToggleWelcome(ServerCommandSource source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.toggleWelcome(owner, guestName).applyTo(source);
    }

    private static int handleUninvite(ServerCommandSource source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity owner)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.uninvite(owner, guestName).applyTo(source);
    }

    private static int handleInvites(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.showInvitations(player).applyTo(source);
    }

    private static int handlePortals(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        playerExecutor.showPortals(player, source);
        return CommandResult.SUCCESS;
    }

    private static int handleAdminList(ServerCommandSource source) {
        adminExecutor.list(source);
        return CommandResult.SUCCESS;
    }

    private static int handleAdminInfo(ServerCommandSource source, String playerName) {
        return adminExecutor.info(source, playerName).applyTo(source);
    }

    private static int handleAdminDeletePrompt(ServerCommandSource source, String playerName) {
        return adminExecutor.deletePrompt(source, playerName).applyTo(source);
    }

    private static int handleAdminDeleteConfirm(ServerCommandSource source, String playerName) {
        return adminExecutor.deleteConfirm(source, playerName).applyTo(source);
    }

    private static int handleAdminTeleport(ServerCommandSource source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity admin)) {
            source.sendError(Text.translatable("personalworlds.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return adminExecutor.teleport(admin, playerName).applyTo(source);
    }

    private static int handleAdminReload(ServerCommandSource source) {
        return adminExecutor.reload(source).applyTo(source);
    }
}
