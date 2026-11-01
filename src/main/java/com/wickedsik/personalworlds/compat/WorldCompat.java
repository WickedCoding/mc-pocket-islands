package com.wickedsik.personalworlds.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;

/**
 * Compatibility layer for Level method access.
 * <p>
 * MC 1.20.x uses: world.getSharedSpawnPos() returning BlockPos
 * MC 1.21.x uses: world.getRespawnData().pos() returning BlockPos from the respawn data record
 * <p>
 * MC 1.20.x uses: world.getMaxBuildHeight() returning int (max Y)
 * MC 1.21.x uses: world.getMinY() + world.getHeight() for max Y
 * <p>
 * MC 1.20.x-1.21.x ChunkPos: fields x/z, toLong(), new ChunkPos(long)
 * MC 26.1+ ChunkPos is a record: x()/z(), pack(), ChunkPos.unpack(long)
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
     * @param world The world or other height-limited view (e.g. a chunk generator's LevelHeightAccessor)
     * @return The minimum Y coordinate
     */
    public static int getBottomY(LevelHeightAccessor world) {
        //? if >=1.21 {
        /*return world.getMinY();
        *///?} else {
        return world.getMinBuildHeight();
        //?}
    }

    /**
     * Get the time of day of a level (ticks, not wrapped at a day).
     * MC 26.1 replaced the level's day time with world clocks: this reads the clock of the
     * level's dimension type (the overworld clock for pocket islands).
     *
     * @param level The level
     * @return The time of day in ticks
     */
    public static long getDayTime(Level level) {
        //? if >=26.1 {
        /*return level.getDefaultClockTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }

    /**
     * Get a chunk position's X coordinate (in chunks).
     *
     * @param pos The chunk position
     * @return The chunk X coordinate
     */
    public static int chunkX(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.x();
        *///?} else {
        return pos.x;
        //?}
    }

    /**
     * Get a chunk position's Z coordinate (in chunks).
     *
     * @param pos The chunk position
     * @return The chunk Z coordinate
     */
    public static int chunkZ(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.z();
        *///?} else {
        return pos.z;
        //?}
    }

    /**
     * Pack a chunk position into a long.
     *
     * @param pos The chunk position
     * @return The packed position
     */
    public static long packChunk(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }

    /**
     * Unpack a chunk position packed by {@link #packChunk}.
     *
     * @param packed The packed position
     * @return The chunk position
     */
    public static ChunkPos unpackChunk(long packed) {
        //? if >=26.1 {
        /*return ChunkPos.unpack(packed);
        *///?} else {
        return new ChunkPos(packed);
        //?}
    }
}
