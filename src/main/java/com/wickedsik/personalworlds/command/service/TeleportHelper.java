package com.wickedsik.personalworlds.command.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

/**
 * Factory for creating TeleportTarget instances.
 * Reduces boilerplate in teleportation code.
 */
public final class TeleportHelper {

    private TeleportHelper() {
        // Utility class - no instantiation
    }

    /**
     * Create a teleport target to a specific position, preserving player rotation.
     *
     * @param pos The target position
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget for the position
     */
    public static TeleportTarget toPosition(Vec3d pos, ServerPlayerEntity player) {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
    }

    /**
     * Create a teleport target to a specific position with explicit rotation.
     *
     * @param pos The target position
     * @param yaw The target yaw
     * @param pitch The target pitch
     * @return TeleportTarget for the position
     */
    public static TeleportTarget toPosition(Vec3d pos, float yaw, float pitch) {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            yaw,
            pitch
        );
    }

    /**
     * Create a teleport target to the center of a block position, preserving player rotation.
     * Adds 0.5 to X and Z for centering.
     *
     * @param blockPos The target block position
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget centered on the block
     */
    public static TeleportTarget toBlockPos(BlockPos blockPos, ServerPlayerEntity player) {
        return new TeleportTarget(
            new Vec3d(
                blockPos.getX() + 0.5,
                blockPos.getY(),
                blockPos.getZ() + 0.5
            ),
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
    }

    /**
     * Create a teleport target to a block position with explicit rotation.
     * Adds 0.5 to X and Z for centering.
     *
     * @param blockPos The target block position
     * @param yaw The target yaw
     * @param pitch The target pitch
     * @return TeleportTarget centered on the block
     */
    public static TeleportTarget toBlockPos(BlockPos blockPos, float yaw, float pitch) {
        return new TeleportTarget(
            new Vec3d(
                blockPos.getX() + 0.5,
                blockPos.getY(),
                blockPos.getZ() + 0.5
            ),
            Vec3d.ZERO,
            yaw,
            pitch
        );
    }

    /**
     * Create a teleport target to a world's spawn point.
     *
     * @param world The target world
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget at world spawn
     */
    public static TeleportTarget toWorldSpawn(ServerWorld world, ServerPlayerEntity player) {
        Vec3d spawnPos = Vec3d.ofCenter(world.getSpawnPos());
        return new TeleportTarget(
            spawnPos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
    }

    /**
     * Create a teleport target to the default dimension spawn (0.5, 65, 0.5).
     * Used when first entering a newly created dimension.
     *
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget at default spawn
     */
    public static TeleportTarget toDefaultSpawn(ServerPlayerEntity player) {
        return new TeleportTarget(
            new Vec3d(0.5, 65, 0.5),
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
    }
}
