package com.wickedsik.personalworlds.compat;

//? if <1.21 {
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
//?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.PortalInfo;

/**
 * Compatibility layer for cross-dimension teleportation.
 * <p>
 * MC 1.20.x uses: FabricDimensions.teleport(entity, world, TeleportTarget)
 * MC 1.21.x uses: Entity#teleportTo(TeleportTarget) - FabricDimensions was removed
 * <p>
 * This class centralizes all cross-dimension teleportation to simplify version migration.
 * Works alongside TeleportHelper which constructs TeleportTarget instances.
 */
public final class TeleportCompat {

    private TeleportCompat() {
        // Utility class
    }

    /**
     * Teleport a player to a target world with explicit position and rotation.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param position    The target position
     * @param yaw         The target yaw (horizontal rotation)
     * @param pitch       The target pitch (vertical rotation)
     */
    public static void teleport(
            ServerPlayer player,
            ServerLevel targetWorld,
            Vec3 position,
            float yaw,
            float pitch
    ) {
        //? if >=1.21 {
        /*// MC 1.21+ uses Entity#teleportTo() - FabricDimensions.teleport() was removed
        // TeleportTarget now contains the destination world
        PortalInfo target = new PortalInfo(
            targetWorld,
            position,
            Vec3.ZERO,
            yaw,
            pitch,
            PortalInfo.DO_NOTHING
        );
        player.teleport(target);
        *///?} else {
        PortalInfo target = new PortalInfo(position, Vec3.ZERO, yaw, pitch);
        teleport(player, targetWorld, target);
        //?}
    }

    /**
     * Teleport a player using a pre-constructed TeleportTarget.
     * This method bridges TeleportHelper (which creates TeleportTargets) with the actual teleport call.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param target      The TeleportTarget with position, velocity, and rotation
     */
    public static void teleport(
            ServerPlayer player,
            ServerLevel targetWorld,
            PortalInfo target
    ) {
        //? if >=1.21 {
        /*// MC 1.21+ uses Entity#teleportTo() - FabricDimensions.teleport() was removed
        // TeleportTarget is now a record with method accessors instead of field access
        PortalInfo newTarget = new PortalInfo(
            targetWorld,
            target.position(),
            target.deltaMovement(),
            target.yRot(),
            target.xRot(),
            PortalInfo.DO_NOTHING
        );
        player.teleport(newTarget);
        *///?} else {
        FabricDimensions.teleport(player, targetWorld, target);
        //?}
    }

    /**
     * Teleport a player to the center of a block position.
     * Adds 0.5 to X and Z for centering.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param blockPos    The target block position
     * @param yaw         The target yaw
     * @param pitch       The target pitch
     */
    public static void teleportToBlock(
            ServerPlayer player,
            ServerLevel targetWorld,
            BlockPos blockPos,
            float yaw,
            float pitch
    ) {
        Vec3 position = Vec3.atCenterOf(blockPos);
        teleport(player, targetWorld, position, yaw, pitch);
    }

    /**
     * Teleport a player to a position, preserving their current rotation.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param position    The target position
     */
    public static void teleportPreserveRotation(
            ServerPlayer player,
            ServerLevel targetWorld,
            Vec3 position
    ) {
        teleport(player, targetWorld, position, player.getYRot(), player.getXRot());
    }

    /**
     * Teleport a player to the center of a block, preserving their current rotation.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param blockPos    The target block position
     */
    public static void teleportToBlockPreserveRotation(
            ServerPlayer player,
            ServerLevel targetWorld,
            BlockPos blockPos
    ) {
        Vec3 position = Vec3.atCenterOf(blockPos);
        teleportPreserveRotation(player, targetWorld, position);
    }
}
