package com.wickedsik.personalworlds.command.service;

import com.wickedsik.personalworlds.compat.WorldCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.PortalInfo;

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
    public static PortalInfo toPosition(ServerLevel world, Vec3 pos, ServerPlayer player) {
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot()
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
    public static PortalInfo toPosition(ServerLevel world, Vec3 pos, float yaw, float pitch) {
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            pos,
            Vec3.ZERO,
            yaw,
            pitch,
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            pos,
            Vec3.ZERO,
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
    public static PortalInfo toBlockPos(ServerLevel world, BlockPos blockPos, ServerPlayer player) {
        Vec3 pos = new Vec3(
            blockPos.getX() + 0.5,
            blockPos.getY(),
            blockPos.getZ() + 0.5
        );
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot()
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
    public static PortalInfo toBlockPos(ServerLevel world, BlockPos blockPos, float yaw, float pitch) {
        Vec3 pos = new Vec3(
            blockPos.getX() + 0.5,
            blockPos.getY(),
            blockPos.getZ() + 0.5
        );
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            pos,
            Vec3.ZERO,
            yaw,
            pitch,
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            pos,
            Vec3.ZERO,
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
    public static PortalInfo toWorldSpawn(ServerLevel world, ServerPlayer player) {
        Vec3 spawnPos = Vec3.atCenterOf(WorldCompat.getSpawnPos(world));
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            spawnPos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            spawnPos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot()
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
    public static PortalInfo toDefaultSpawn(ServerLevel world, ServerPlayer player) {
        Vec3 pos = new Vec3(0.5, 65, 0.5);
        //? if >=1.21 {
        /*return new PortalInfo(
            world,
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot(),
            PortalInfo.DO_NOTHING
        );
        *///?} else {
        return new PortalInfo(
            pos,
            Vec3.ZERO,
            player.getYRot(),
            player.getXRot()
        );
        //?}
    }
}
