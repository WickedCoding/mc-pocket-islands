package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.platform.PlatformTeleport;
//? if <1.21 {
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.PortalInfo;

final class FabricTeleport implements PlatformTeleport {

    @Override
    public void teleport(ServerPlayer player, ServerLevel level, PortalInfo target) {
        //? if >=1.21 {
        /*// The target carries the destination level; vanilla handles the dimension change
        player.teleport(target);
        *///?} else {
        FabricDimensions.teleport(player, level, target);
        //?}
    }
}
