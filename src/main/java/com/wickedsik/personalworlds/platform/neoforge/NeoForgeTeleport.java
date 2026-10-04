package com.wickedsik.personalworlds.platform.neoforge;

import com.wickedsik.personalworlds.platform.PlatformTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.PortalInfo;

/**
 * The target carries the destination level; vanilla handles the dimension change, as
 * on Fabric 1.21. Portal blocks fire in the player tick on 1.21.x, not inside the
 * movement packet, so the Forge 1.20.1 "moved wrongly" problem does not arise.
 */
final class NeoForgeTeleport implements PlatformTeleport {

    @Override
    public void teleport(ServerPlayer player, ServerLevel level, PortalInfo target) {
        player.teleport(target);
    }
}
