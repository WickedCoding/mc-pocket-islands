package com.wickedsik.personalworlds.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;

/**
 * Compatibility layer for World method access.
 * <p>
 * MC 1.20.x uses: world.getSpawnPos() returning BlockPos
 * MC 1.21.x uses: world.getSpawnPoint().pos() returning BlockPos from SpawnPoint record
 * <p>
 * MC 1.20.x uses: world.getTopY() returning int (max Y)
 * MC 1.21.x uses: world.getBottomY() + world.getHeight() for max Y
 * <p>
 * This class centralizes all world-related method access to simplify version migration.
 */
public final class WorldCompat {

    private WorldCompat() {
        // Utility class
    }

    /**
     * Get the spawn position of a world as a BlockPos.
     *
     * @param world The server world
     * @return The spawn position as BlockPos
     */
    public static BlockPos getSpawnPos(ServerLevel world) {
        //? if >=1.21 {
        /*return world.getRespawnData().pos();
        *///?} else {
        return world.getSharedSpawnPos();
        //?}
    }

    /**
     * Get the maximum Y coordinate for a world (exclusive).
     *
     * @param world The world
     * @return The maximum Y coordinate
     */
    public static int getTopY(Level world) {
        //? if >=1.21 {
        /*return world.getMinY() + world.getHeight();
        *///?} else {
        return world.getMaxBuildHeight();
        //?}
    }

    /**
     * Get the minimum Y coordinate for a world (inclusive).
     *
     * @param world The world or other height-limited view (e.g. a chunk generator's HeightLimitView)
     * @return The minimum Y coordinate
     */
    public static int getBottomY(LevelHeightAccessor world) {
        //? if >=1.21 {
        /*return world.getMinY();
        *///?} else {
        return world.getMinBuildHeight();
        //?}
    }
}
