package com.wickedsik.personalworlds.compat;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

//? if >=1.21 {
/*import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
*///?}

/**
 * Compatibility layer for command permission checking.
 * <p>
 * MC 1.20.x uses: source.hasPermissionLevel(level), player.hasPermissionLevel(level)
 * MC 1.21.x uses: PermissionPredicate system via getPermissions() with PermissionLevel
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
        //? if >=1.21 {
        /*LeveledPermissionPredicate permissions = (LeveledPermissionPredicate) source.getPermissions();
        return permissions.getLevel().isAtLeast(PermissionLevel.fromLevel(level));
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
        //? if >=1.21 {
        /*LeveledPermissionPredicate permissions = (LeveledPermissionPredicate) player.getPermissions();
        return permissions.getLevel().isAtLeast(PermissionLevel.fromLevel(level));
        *///?} else {
        return player.hasPermissionLevel(level);
        //?}
    }

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
