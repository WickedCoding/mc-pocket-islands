package com.wickedsik.personalworlds.compat;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    public static BlockPos getSpawnPos(ServerWorld world) {
        //? if >=1.21 {
        /*return world.getSpawnPoint().getPos();
        *///?} else {
        return world.getSpawnPos();
        //?}
    }

    /**
     * Get the maximum Y coordinate for a world (exclusive).
     *
     * @param world The world
     * @return The maximum Y coordinate
     */
    public static int getTopY(World world) {
        //? if >=1.21 {
        /*return world.getBottomY() + world.getHeight();
        *///?} else {
        return world.getTopY();
        //?}
    }
}
