package com.wickedsik.personalworlds.platform.neoforge;

import com.wickedsik.personalworlds.platform.PlatformTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.PortalInfo;

/**
 * The target carries the destination level, so vanilla handles the dimension change. On
 * 1.21.x portals fire in the player tick, outside the movement packet, so the Forge 1.20.1
 * {@code changeDimension} workaround is not needed.
 */
final class NeoForgeTeleport implements PlatformTeleport {

    @Override
    public void teleport(ServerPlayer player, ServerLevel level, PortalInfo target) {
        player.teleport(target);
    }
}
