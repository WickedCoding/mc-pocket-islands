package com.wickedsik.personalworlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * Command registration and delegation for Pocket Islands.
 *
 * This class registers all /pi commands and delegates execution to specialized executors:
 * - {@link DevCommandExecutor} - create, enter, leave (OP 2)
 * - {@link PlayerCommandExecutor} - invite, uninvite, invites, portals (no permission)
 * - {@link AdminCommandExecutor} - list, info, delete, tp, reload, sanitize (configurable permission)
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

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pi")
                // === Development/Testing Commands (OP level 2+) ===
                .then(Commands.literal("create")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleCreate(ctx.getSource(), "OVERWORLD"))
                    .then(Commands.argument("type", StringArgumentType.word())
                        .executes(ctx -> handleCreate(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")
                        ))
                    )
                )

                .then(Commands.literal("enter")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleEnter(ctx.getSource()))
                )

                .then(Commands.literal("leave")
                    .requires(PermissionHelper.require(PermissionHelper.PLAYER_CREATE, 2))
                    .executes(ctx -> handleLeave(ctx.getSource()))
                )

                // === Player Commands (No Permission Required) ===
                .then(buildInviteCommand())

                .then(Commands.literal("uninvite")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleUninvite(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "player")
                        ))
                    )
                )

                // Conditionally add togglewelcome command
                .then(buildToggleWelcomeCommand())

                .then(Commands.literal("invites")
                    .executes(ctx -> handleInvites(ctx.getSource()))
                )

                .then(Commands.literal("portals")
                    .executes(ctx -> handlePortals(ctx.getSource()))
                )

                // === Admin Commands ===
                .then(Commands.literal("admin")
                    .then(Commands.literal("list")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_LIST, PermissionHelper.DEFAULT_ADMIN_LIST_LEVEL))
                        .executes(ctx -> handleAdminList(ctx.getSource()))
                    )

                    .then(Commands.literal("info")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_INFO, PermissionHelper.DEFAULT_ADMIN_INFO_LEVEL))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(Commands.literal("delete")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_DELETE, PermissionHelper.DEFAULT_ADMIN_DELETE_LEVEL))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminDeletePrompt(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                            .then(Commands.literal("confirm")
                                .executes(ctx -> handleAdminDeleteConfirm(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player")
                                ))
                            )
                        )
                    )

                    .then(Commands.literal("tp")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_TELEPORT, PermissionHelper.DEFAULT_ADMIN_TELEPORT_LEVEL))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminTeleport(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                            ))
                        )
                    )

                    .then(Commands.literal("reload")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_RELOAD, PermissionHelper.DEFAULT_ADMIN_RELOAD_LEVEL))
                        .executes(ctx -> handleAdminReload(ctx.getSource()))
                    )

                    .then(Commands.literal("sanitize")
                        .requires(PermissionHelper.require(PermissionHelper.ADMIN_SANITIZE, PermissionHelper.DEFAULT_ADMIN_SANITIZE_LEVEL))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> handleAdminSanitize(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                4
                            ))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> handleAdminSanitize(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "radius")
                                ))
                            )
                        )
                    )
                )

                // === Debug/Testing Commands (OP level 4) ===
                .then(Commands.literal("debug")
                    .requires(CommandCompat.requiresLevel(4))
                    .then(Commands.literal("perf")
                        .then(Commands.literal("enable")
                            .executes(ctx -> debugExecutor.enablePerf().applyTo(ctx.getSource())))
                        .then(Commands.literal("disable")
                            .executes(ctx -> debugExecutor.disablePerf().applyTo(ctx.getSource())))
                        .then(Commands.literal("status")
                            .executes(ctx -> {
                                debugExecutor.showStatus(ctx.getSource(), ctx.getSource().getServer());
                                return CommandResult.SUCCESS;
                            }))
                        .then(Commands.literal("reset")
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
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildInviteCommand() {
        var playerArg = Commands.argument("player", EntityArgument.player())
            .executes(ctx -> handleInvite(
                ctx.getSource(),
                EntityArgument.getPlayer(ctx, "player"),
                false
            ));

        // Conditionally add "always" subcommand
        if (ModConfig.get().enableAlwaysWelcome) {
            playerArg = playerArg.then(Commands.literal("always")
                .executes(ctx -> handleInvite(
                    ctx.getSource(),
                    EntityArgument.getPlayer(ctx, "player"),
                    true
                ))
            );
        }

        return Commands.literal("invite").then(playerArg);
    }

    /**
     * Build the togglewelcome command.
     * Returns a no-op command if enableAlwaysWelcome is false.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildToggleWelcomeCommand() {
        if (!ModConfig.get().enableAlwaysWelcome) {
            // Return a hidden command that does nothing (won't show in tab-complete)
            return Commands.literal("togglewelcome")
                .requires(source -> false);  // Never passes requirements check
        }

        return Commands.literal("togglewelcome")
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(ctx -> handleToggleWelcome(
                    ctx.getSource(),
                    StringArgumentType.getString(ctx, "player")
                ))
            );
    }

    // ==================== Thin Adapter Methods ====================

    private static int handleCreate(CommandSourceStack source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.createDimension(player, typeStr).applyTo(source);
    }

    private static int handleEnter(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.enterDimension(player).applyTo(source);
    }

    private static int handleLeave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return devExecutor.leaveDimension(player).applyTo(source);
    }

    private static int handleInvite(CommandSourceStack source, ServerPlayer guest, boolean alwaysWelcome) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.invite(owner, guest, alwaysWelcome).applyTo(source);
    }

    private static int handleToggleWelcome(CommandSourceStack source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.toggleWelcome(owner, guestName).applyTo(source);
    }

    private static int handleUninvite(CommandSourceStack source, String guestName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.uninvite(owner, guestName).applyTo(source);
    }

    private static int handleInvites(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return playerExecutor.showInvitations(player).applyTo(source);
    }

    private static int handlePortals(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        playerExecutor.showPortals(player, source);
        return CommandResult.SUCCESS;
    }

    private static int handleAdminList(CommandSourceStack source) {
        adminExecutor.list(source);
        return CommandResult.SUCCESS;
    }

    private static int handleAdminInfo(CommandSourceStack source, String playerName) {
        return adminExecutor.info(source, playerName).applyTo(source);
    }

    private static int handleAdminDeletePrompt(CommandSourceStack source, String playerName) {
        return adminExecutor.deletePrompt(source, playerName).applyTo(source);
    }

    private static int handleAdminDeleteConfirm(CommandSourceStack source, String playerName) {
        return adminExecutor.deleteConfirm(source, playerName).applyTo(source);
    }

    private static int handleAdminTeleport(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer admin)) {
            source.sendFailure(Component.translatable("pocketislands.command.error.must_be_player"));
            return CommandResult.FAILURE;
        }
        return adminExecutor.teleport(admin, playerName).applyTo(source);
    }

    private static int handleAdminReload(CommandSourceStack source) {
        return adminExecutor.reload(source).applyTo(source);
    }

    private static int handleAdminSanitize(CommandSourceStack source, String playerName, int radius) {
        return adminExecutor.sanitize(source, playerName, radius).applyTo(source);
    }
}
