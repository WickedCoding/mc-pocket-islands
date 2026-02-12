package com.wickedsik.personalworlds.command.service;

import com.wickedsik.personalworlds.compat.WorldCompat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

/**
 * Factory for creating TeleportTarget instances.
 * Reduces boilerplate in teleportation code.
 *
 * Note: In 1.21+, TeleportTarget requires ServerWorld and PostDimensionTransition.
 * This class provides 1.20.x-style API; use TeleportCompat for actual teleportation.
 */
public final class TeleportHelper {

    private TeleportHelper() {
        // Utility class - no instantiation
    }

    /**
     * Create a teleport target to a specific position, preserving player rotation.
     *
     * @param world The target world (required in 1.21+)
     * @param pos The target position
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget for the position
     */
    public static TeleportTarget toPosition(ServerWorld world, Vec3d pos, ServerPlayerEntity player) {
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch(),
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        //?}
    }

    /**
     * Create a teleport target to a specific position with explicit rotation.
     *
     * @param world The target world (required in 1.21+)
     * @param pos The target position
     * @param yaw The target yaw
     * @param pitch The target pitch
     * @return TeleportTarget for the position
     */
    public static TeleportTarget toPosition(ServerWorld world, Vec3d pos, float yaw, float pitch) {
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            pos,
            Vec3d.ZERO,
            yaw,
            pitch,
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            yaw,
            pitch
        );
        //?}
    }

    /**
     * Create a teleport target to the center of a block position, preserving player rotation.
     * Adds 0.5 to X and Z for centering.
     *
     * @param world The target world (required in 1.21+)
     * @param blockPos The target block position
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget centered on the block
     */
    public static TeleportTarget toBlockPos(ServerWorld world, BlockPos blockPos, ServerPlayerEntity player) {
        Vec3d pos = new Vec3d(
            blockPos.getX() + 0.5,
            blockPos.getY(),
            blockPos.getZ() + 0.5
        );
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch(),
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        //?}
    }

    /**
     * Create a teleport target to a block position with explicit rotation.
     * Adds 0.5 to X and Z for centering.
     *
     * @param world The target world (required in 1.21+)
     * @param blockPos The target block position
     * @param yaw The target yaw
     * @param pitch The target pitch
     * @return TeleportTarget centered on the block
     */
    public static TeleportTarget toBlockPos(ServerWorld world, BlockPos blockPos, float yaw, float pitch) {
        Vec3d pos = new Vec3d(
            blockPos.getX() + 0.5,
            blockPos.getY(),
            blockPos.getZ() + 0.5
        );
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            pos,
            Vec3d.ZERO,
            yaw,
            pitch,
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            yaw,
            pitch
        );
        //?}
    }

    /**
     * Create a teleport target to a world's spawn point.
     *
     * @param world The target world
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget at world spawn
     */
    public static TeleportTarget toWorldSpawn(ServerWorld world, ServerPlayerEntity player) {
        Vec3d spawnPos = Vec3d.ofCenter(WorldCompat.getSpawnPos(world));
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            spawnPos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch(),
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            spawnPos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        //?}
    }

    /**
     * Create a teleport target to the default dimension spawn (0.5, 65, 0.5).
     * Used when first entering a newly created dimension.
     *
     * @param world The target world (required in 1.21+)
     * @param player The player being teleported (for yaw/pitch)
     * @return TeleportTarget at default spawn
     */
    public static TeleportTarget toDefaultSpawn(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = new Vec3d(0.5, 65, 0.5);
        //? if >=1.21 {
        /*return new TeleportTarget(
            world,
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch(),
            TeleportTarget.NO_OP
        );
        *///?} else {
        return new TeleportTarget(
            pos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        //?}
    }
}
