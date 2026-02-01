package com.wickedsik.personalworlds.compat;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

/**
 * Compatibility layer for cross-dimension teleportation.
 * <p>
 * MC 1.20.x uses: FabricDimensions.teleport(entity, world, TeleportTarget)
 * MC 1.21.x uses: Different API (DimensionTransition or updated Fabric API)
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
            ServerPlayerEntity player,
            ServerWorld targetWorld,
            Vec3d position,
            float yaw,
            float pitch
    ) {
        TeleportTarget target = new TeleportTarget(position, Vec3d.ZERO, yaw, pitch);
        teleport(player, targetWorld, target);
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
            ServerPlayerEntity player,
            ServerWorld targetWorld,
            TeleportTarget target
    ) {
        //? if >=1.21 {
        /*// MC 1.21+ uses updated teleportation API
        // TODO: Implement 1.21 teleportation when adding 1.21 support
        FabricDimensions.teleport(player, targetWorld, target);*/
        //? } else {
        FabricDimensions.teleport(player, targetWorld, target);
        //? }
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
            ServerPlayerEntity player,
            ServerWorld targetWorld,
            BlockPos blockPos,
            float yaw,
            float pitch
    ) {
        Vec3d position = Vec3d.ofCenter(blockPos);
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
            ServerPlayerEntity player,
            ServerWorld targetWorld,
            Vec3d position
    ) {
        teleport(player, targetWorld, position, player.getYaw(), player.getPitch());
    }

    /**
     * Teleport a player to the center of a block, preserving their current rotation.
     *
     * @param player      The player to teleport
     * @param targetWorld The destination world
     * @param blockPos    The target block position
     */
    public static void teleportToBlockPreserveRotation(
            ServerPlayerEntity player,
            ServerWorld targetWorld,
            BlockPos blockPos
    ) {
        Vec3d position = Vec3d.ofCenter(blockPos);
        teleportPreserveRotation(player, targetWorld, position);
    }
}
