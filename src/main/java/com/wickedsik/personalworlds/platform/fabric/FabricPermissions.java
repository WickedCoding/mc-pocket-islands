package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.CommandCompat;
import com.wickedsik.personalworlds.platform.PlatformPermissions;
import net.minecraft.commands.CommandSourceStack;

/**
 * Soft integration with fabric-permissions-api (provided by LuckPerms). Falls back to
 * vanilla OP levels when the API is not installed.
 */
final class FabricPermissions implements PlatformPermissions {

    // Whether fabric-permissions-api is on the classpath; resolved on first check
    private Boolean permissionsApiAvailable = null;

    @Override
    public boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        if (isPermissionsApiAvailable()) {
            try {
                return me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, fallbackLevel);
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.debug("Permissions API check failed, falling back to OP level", e);
                return CommandCompat.hasPermissionLevel(source, fallbackLevel);
            }
        }
        return CommandCompat.hasPermissionLevel(source, fallbackLevel);
    }

    private boolean isPermissionsApiAvailable() {
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
}
