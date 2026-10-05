package com.wickedsik.personalworlds.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.PortalInfo;

/** Cross-dimension player teleport. */
public interface PlatformTeleport {

    /**
     * Move a player to {@code target} in {@code level}, which may be another dimension.
     * On 1.21.x the target already names the destination level.
     */
    void teleport(ServerPlayer player, ServerLevel level, PortalInfo target);
}
