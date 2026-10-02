package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.WorldCompat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds safe spawn locations with multiple fallback strategies.
 *
 * Used when:
 * - Return position is in deleted/unloaded chunk
 * - Return position is now inside a solid block
 * - Emergency evacuation needed
 */
public class SafeSpawnFinder {

    private static final int SEARCH_RADIUS = 16;
    private static final int MAX_Y_SEARCH = 32;

    /**
     * Find a safe spawn position near the target position.
     * Implements multiple fallback strategies.
     *
     * @param world The world to search in
     * @param target The desired position
     * @return A safe spawn position
     */
    public static BlockPos findSafePosition(ServerLevel world, BlockPos target) {
        // Strategy 1: Target position is already safe
        if (isSafeSpawn(world, target)) {
            return target;
        }

        // Strategy 2: Search vertically at target X/Z
        BlockPos vertical = searchVertically(world, target);
        if (vertical != null) {
            return vertical;
        }

        // Strategy 3: Search in expanding spiral around target
        BlockPos spiral = searchSpiral(world, target);
        if (spiral != null) {
            return spiral;
        }

        // Strategy 4: Use world spawn
        BlockPos worldSpawn = findSafeNearSpawn(world);
        if (worldSpawn != null) {
            PersonalWorldsMod.LOGGER.warn("Using world spawn as fallback for {}",
                target);
            return worldSpawn;
        }

        // Strategy 5: Emergency spawn (above void)
        PersonalWorldsMod.LOGGER.error("No safe spawn found near {}, using emergency position",
            target);
        return new BlockPos(0, 100, 0);
    }

    /**
     * Check if a position is safe for spawning.
     *
     * @param world The world to check
     * @param pos The position to check
     * @return true if position is safe
     */
    public static boolean isSafeSpawn(ServerLevel world, BlockPos pos) {
        // Must have solid ground below
        BlockState ground = world.getBlockState(pos.below());
        if (!ground.isRedstoneConductor(world, pos.below())) {
            return false;
        }

        // Must have air at feet and head level
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.above());

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        // Not in lava, water, or other hazards
        if (ground.getFluidState().isSource()) {
            return false;
        }

        return true;
    }

    /**
     * Search vertically for a safe position.
     */
    private static BlockPos searchVertically(ServerLevel world, BlockPos target) {
        // Search upward first (safer)
        for (int dy = 0; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.above(dy);
            if (check.getY() < WorldCompat.getTopY(world) && isSafeSpawn(world, check)) {
                return check;
            }
        }

        // Search downward
        for (int dy = 1; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.below(dy);
            if (check.getY() > WorldCompat.getBottomY(world) && isSafeSpawn(world, check)) {
                return check;
            }
        }

        return null;
    }

    /**
     * Search in an expanding spiral pattern.
     */
    private static BlockPos searchSpiral(ServerLevel world, BlockPos target) {
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the outer ring of the current radius
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos horizontal = target.offset(dx, 0, dz);

                    // Try using heightmap for faster search
                    int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        horizontal.getX(), horizontal.getZ());
                    BlockPos surface = new BlockPos(horizontal.getX(), surfaceY, horizontal.getZ());

                    if (isSafeSpawn(world, surface)) {
                        return surface;
                    }

                    // Fallback to vertical search at this position
                    BlockPos vertical = searchVertically(world, horizontal);
                    if (vertical != null) {
                        return vertical;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find a safe position near world spawn.
     */
    private static BlockPos findSafeNearSpawn(ServerLevel world) {
        BlockPos spawn = WorldCompat.getSpawnPos(world);

        // Try spawn directly
        if (isSafeSpawn(world, spawn)) {
            return spawn;
        }

        // Search near spawn
        return searchSpiral(world, spawn);
    }
}
