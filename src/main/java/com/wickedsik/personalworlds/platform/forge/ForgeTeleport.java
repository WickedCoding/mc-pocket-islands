package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.platform.PlatformTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.PortalInfo;

/**
 * Vanilla {@code ServerPlayer#teleportTo(ServerLevel, ...)} changes dimension when the
 * level differs and fires Forge's dimension-travel events. Infiniverse uses the same
 * call to move players out of unregistered levels.
 */
final class ForgeTeleport implements PlatformTeleport {

    @Override
    public void teleport(ServerPlayer player, ServerLevel level, PortalInfo target) {
        player.teleportTo(level, target.pos.x, target.pos.y, target.pos.z, target.yRot, target.xRot);
        player.setDeltaMovement(target.speed);
    }
}
