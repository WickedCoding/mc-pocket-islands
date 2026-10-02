package com.wickedsik.personalworlds.compat;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

//? if >=1.21 {
/*import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
*///?}

/**
 * Compatibility layer for command permission checking.
 * <p>
 * MC 1.20.x uses: source.hasPermissionLevel(level), player.hasPermissionLevel(level)
 * MC 1.21.x uses: getPermissions().hasPermission(new Permission.Level(PermissionLevel))
 * <p>
 * This class centralizes all permission checks to simplify version migration.
 */
public final class CommandCompat {

    private CommandCompat() {
        // Utility class
    }

    /**
     * Check if a command source has a specific permission level.
     *
     * @param source The command source to check
     * @param level  The required permission level (0-4)
     * @return true if the source has at least the required permission level
     */
    public static boolean hasPermissionLevel(ServerCommandSource source, int level) {
        if (level <= 0) {
            return true; // level 0 is everyone, as in 1.20.x
        }
        //? if >=1.21 {
        /*return source.getPermissions().hasPermission(levelPermission(level));
        *///?} else {
        return source.hasPermissionLevel(level);
        //?}
    }

    /**
     * Check if a player has a specific permission level.
     *
     * @param player The player to check
     * @param level  The required permission level (0-4)
     * @return true if the player has at least the required permission level
     */
    public static boolean hasPermissionLevel(ServerPlayerEntity player, int level) {
        if (level <= 0) {
            return true; // level 0 is everyone, as in 1.20.x
        }
        //? if >=1.21 {
        /*return player.getPermissions().hasPermission(levelPermission(level));
        *///?} else {
        return player.hasPermissionLevel(level);
        //?}
    }

    //? if >=1.21 {
    /*// Ask through the PermissionPredicate interface. Not every predicate is a
    // LeveledPermissionPredicate (NONE/ALL are lambdas, or() builds an
    // OrPermissionPredicate), so casting to read the level throws.
    private static Permission levelPermission(int level) {
        return new Permission.Level(PermissionLevel.fromLevel(level));
    }
    *///?}

    /**
     * Get a Predicate for command requirements that checks permission level.
     * For use with Brigadier's .requires() method.
     *
     * @param level The required permission level
     * @return A predicate that checks the permission level
     */
    public static java.util.function.Predicate<ServerCommandSource> requiresLevel(int level) {
        return source -> hasPermissionLevel(source, level);
    }
}
