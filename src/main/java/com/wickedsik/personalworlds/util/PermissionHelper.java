package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

/**
 * Centralized permission checking utility.
 *
 * Provides soft integration with fabric-permissions-api:
 * - If fabric-permissions-api is available, uses permission nodes
 * - Falls back to vanilla OP levels otherwise
 *
 * This allows the mod to work standalone while supporting
 * LuckPerms and other permission plugins when installed.
 */
public final class PermissionHelper {

    // ==================== Permission Nodes ====================

    // Admin permissions
    public static final String ADMIN_LIST = "personalworlds.admin.list";
    public static final String ADMIN_INFO = "personalworlds.admin.info";
    public static final String ADMIN_DELETE = "personalworlds.admin.delete";
    public static final String ADMIN_TELEPORT = "personalworlds.admin.teleport";
    public static final String ADMIN_RELOAD = "personalworlds.admin.reload";

    // Player permissions (for future use)
    public static final String PLAYER_CREATE = "personalworlds.player.create";
    public static final String PLAYER_INVITE = "personalworlds.player.invite";
    public static final String PLAYER_VISIT = "personalworlds.player.visit";

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

    public static final int DEFAULT_PLAYER_CREATE_LEVEL = 0;
    public static final int DEFAULT_PLAYER_INVITE_LEVEL = 0;
    public static final int DEFAULT_PLAYER_VISIT_LEVEL = 0;

    // Track whether permissions API is available
    private static Boolean permissionsApiAvailable = null;

    // ==================== Permission Checking ====================

    /**
     * Check if the command source has the specified permission.
     * Falls back to OP level check if fabric-permissions-api is unavailable.
     *
     * @param source The command source to check
     * @param permission The permission node to check
     * @param fallbackLevel The OP level required if no permissions plugin
     * @return true if the source has permission
     */
    public static boolean check(ServerCommandSource source, String permission, int fallbackLevel) {
        if (isPermissionsApiAvailable()) {
            try {
                return me.lucko.fabric.api.permissions.v0.Permissions.check(source, permission, fallbackLevel);
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.debug("Permissions API check failed, falling back to OP level", e);
                return source.hasPermissionLevel(fallbackLevel);
            }
        }
        return source.hasPermissionLevel(fallbackLevel);
    }

    /**
     * Create a predicate for command registration.
     * Used with CommandManager.literal().requires()
     *
     * @param permission The permission node to check
     * @param fallbackLevel The OP level required if no permissions plugin
     * @return A predicate that checks the permission
     */
    public static Predicate<ServerCommandSource> require(String permission, int fallbackLevel) {
        return source -> check(source, permission, fallbackLevel);
    }

    // ==================== Convenience Methods ====================

    /**
     * Check admin list permission.
     */
    public static boolean canAdminList(ServerCommandSource source) {
        return check(source, ADMIN_LIST, DEFAULT_ADMIN_LIST_LEVEL);
    }

    /**
     * Check admin info permission.
     */
    public static boolean canAdminInfo(ServerCommandSource source) {
        return check(source, ADMIN_INFO, DEFAULT_ADMIN_INFO_LEVEL);
    }

    /**
     * Check admin delete permission.
     */
    public static boolean canAdminDelete(ServerCommandSource source) {
        return check(source, ADMIN_DELETE, DEFAULT_ADMIN_DELETE_LEVEL);
    }

    /**
     * Check admin teleport permission.
     */
    public static boolean canAdminTeleport(ServerCommandSource source) {
        return check(source, ADMIN_TELEPORT, DEFAULT_ADMIN_TELEPORT_LEVEL);
    }

    /**
     * Check admin reload permission.
     */
    public static boolean canAdminReload(ServerCommandSource source) {
        return check(source, ADMIN_RELOAD, DEFAULT_ADMIN_RELOAD_LEVEL);
    }

    // ==================== API Availability Check ====================

    /**
     * Check if fabric-permissions-api is available at runtime.
     * Result is cached for performance.
     */
    private static boolean isPermissionsApiAvailable() {
        if (permissionsApiAvailable == null) {
            try {
                Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                permissionsApiAvailable = true;
                PersonalWorldsMod.LOGGER.info("fabric-permissions-api detected, using permission nodes");
            } catch (ClassNotFoundException e) {
                permissionsApiAvailable = false;
                PersonalWorldsMod.LOGGER.info("fabric-permissions-api not found, using vanilla OP levels");
            }
        }
        return permissionsApiAvailable;
    }

    /**
     * Reset the availability cache.
     * Useful for testing or if the API becomes available at runtime.
     */
    public static void resetCache() {
        permissionsApiAvailable = null;
    }

    // Prevent instantiation
    private PermissionHelper() {}
}
