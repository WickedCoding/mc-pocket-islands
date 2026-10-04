package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.platform.Platform;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * Centralized permission checking utility.
 *
 * Defines the permission nodes and their OP-level fallbacks. The check itself goes
 * through the loader's permission API (see {@link Platform#permissions()}), which
 * falls back to vanilla OP levels when no permission plugin is installed.
 */
public final class PermissionHelper {

    // ==================== Permission Nodes ====================

    // Admin permissions
    public static final String ADMIN_LIST = "pocketislands.admin.list";
    public static final String ADMIN_INFO = "pocketislands.admin.info";
    public static final String ADMIN_DELETE = "pocketislands.admin.delete";
    public static final String ADMIN_TELEPORT = "pocketislands.admin.teleport";
    public static final String ADMIN_RELOAD = "pocketislands.admin.reload";
    public static final String ADMIN_SANITIZE = "pocketislands.admin.sanitize";

    // Player permissions (for future use)
    public static final String PLAYER_CREATE = "pocketislands.player.create";
    public static final String PLAYER_INVITE = "pocketislands.player.invite";
    public static final String PLAYER_VISIT = "pocketislands.player.visit";

    /** Every node above; loaders that need nodes declared up front (Forge) register these. */
    public static final List<String> ALL_NODES = List.of(
        ADMIN_LIST, ADMIN_INFO, ADMIN_DELETE, ADMIN_TELEPORT, ADMIN_RELOAD, ADMIN_SANITIZE,
        PLAYER_CREATE, PLAYER_INVITE, PLAYER_VISIT
    );

    // ==================== Default OP Levels ====================

    // Level 0: All players
    // Level 1: Can bypass spawn protection
    // Level 2: Can use /gamemode, /tp, etc.
    // Level 3: Can use /ban, /kick, etc.
    // Level 4: Can use /stop, /save-all, etc.

    public static final int DEFAULT_ADMIN_LIST_LEVEL = 2;
    public static final int DEFAULT_ADMIN_INFO_LEVEL = 2;
    public static final int DEFAULT_ADMIN_DELETE_LEVEL = 4;
    public static final int DEFAULT_ADMIN_TELEPORT_LEVEL = 2;
    public static final int DEFAULT_ADMIN_RELOAD_LEVEL = 3;
    public static final int DEFAULT_ADMIN_SANITIZE_LEVEL = 3;

    public static final int DEFAULT_PLAYER_CREATE_LEVEL = 0;
    public static final int DEFAULT_PLAYER_INVITE_LEVEL = 0;
    public static final int DEFAULT_PLAYER_VISIT_LEVEL = 0;

    // ==================== Permission Checking ====================

    /**
     * Check if the command source has the specified permission.
     * Falls back to OP level check if no permission plugin is installed.
     *
     * @param source The command source to check
     * @param permission The permission node to check
     * @param fallbackLevel The OP level required if no permissions plugin
     * @return true if the source has permission
     */
    public static boolean check(CommandSourceStack source, String permission, int fallbackLevel) {
        return Platform.get().permissions().check(source, permission, fallbackLevel);
    }

    /**
     * Create a predicate for command registration.
     * Used with Commands.literal().requires()
     *
     * @param permission The permission node to check
     * @param fallbackLevel The OP level required if no permissions plugin
     * @return A predicate that checks the permission
     */
    public static Predicate<CommandSourceStack> require(String permission, int fallbackLevel) {
        return source -> check(source, permission, fallbackLevel);
    }

    // ==================== Convenience Methods ====================

    /**
     * Check admin list permission.
     */
    public static boolean canAdminList(CommandSourceStack source) {
        return check(source, ADMIN_LIST, DEFAULT_ADMIN_LIST_LEVEL);
    }

    /**
     * Check admin info permission.
     */
    public static boolean canAdminInfo(CommandSourceStack source) {
        return check(source, ADMIN_INFO, DEFAULT_ADMIN_INFO_LEVEL);
    }

    /**
     * Check admin delete permission.
     */
    public static boolean canAdminDelete(CommandSourceStack source) {
        return check(source, ADMIN_DELETE, DEFAULT_ADMIN_DELETE_LEVEL);
    }

    /**
     * Check admin teleport permission.
     */
    public static boolean canAdminTeleport(CommandSourceStack source) {
        return check(source, ADMIN_TELEPORT, DEFAULT_ADMIN_TELEPORT_LEVEL);
    }

    /**
     * Check admin reload permission.
     */
    public static boolean canAdminReload(CommandSourceStack source) {
        return check(source, ADMIN_RELOAD, DEFAULT_ADMIN_RELOAD_LEVEL);
    }

    // Prevent instantiation
    private PermissionHelper() {}
}
