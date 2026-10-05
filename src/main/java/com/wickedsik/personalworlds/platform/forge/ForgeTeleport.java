package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.platform.PlatformTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;

import java.util.function.Function;

/**
 * Cross-dimension moves go through {@code changeDimension} with a teleporter that
 * returns the given target, as Fabric's {@code FabricDimensions.teleport} does.
 * <p>
 * Not {@code ServerPlayer#teleportTo(ServerLevel, ...)}: portal entry runs inside the
 * player's movement packet, and after the move the packet handler compares the player
 * with the client's requested position. Only {@code changeDimension} sets
 * {@code isChangingDimension}; without it the handler reports "moved wrongly" and
 * teleports the player back to their old coordinates, now in the new level.
 */
final class ForgeTeleport implements PlatformTeleport {

    @Override
    public void teleport(ServerPlayer player, ServerLevel level, PortalInfo target) {
        if (player.level() == level) {
            player.teleportTo(level, target.pos.x, target.pos.y, target.pos.z, target.yRot, target.xRot);
            return;
        }
        player.changeDimension(level, new FixedTargetTeleporter(target));
    }

    private record FixedTargetTeleporter(PortalInfo target) implements ITeleporter {

        @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
            return target;
        }

        // false: no End obsidian platform; the target is already a safe spot
        @Override
        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw,
                                  Function<Boolean, Entity> repositionEntity) {
            return repositionEntity.apply(false);
        }
    }
}
