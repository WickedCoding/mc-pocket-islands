package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

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
    public static BlockPos findSafePosition(ServerWorld world, BlockPos target) {
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
    public static boolean isSafeSpawn(ServerWorld world, BlockPos pos) {
        // Must have solid ground below
        BlockState ground = world.getBlockState(pos.down());
        if (!ground.isSolidBlock(world, pos.down())) {
            return false;
        }

        // Must have air at feet and head level
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        // Not in lava, water, or other hazards
        if (ground.getFluidState().isStill()) {
            return false;
        }

        return true;
    }

    /**
     * Search vertically for a safe position.
     */
    private static BlockPos searchVertically(ServerWorld world, BlockPos target) {
        // Search upward first (safer)
        for (int dy = 0; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.up(dy);
            if (check.getY() < world.getTopY() && isSafeSpawn(world, check)) {
                return check;
            }
        }

        // Search downward
        for (int dy = 1; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.down(dy);
            if (check.getY() > world.getBottomY() && isSafeSpawn(world, check)) {
                return check;
            }
        }

        return null;
    }

    /**
     * Search in an expanding spiral pattern.
     */
    private static BlockPos searchSpiral(ServerWorld world, BlockPos target) {
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the outer ring of the current radius
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos horizontal = target.add(dx, 0, dz);

                    // Try using heightmap for faster search
                    int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING,
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
    private static BlockPos findSafeNearSpawn(ServerWorld world) {
        BlockPos spawn = world.getSpawnPos();

        // Try spawn directly
        if (isSafeSpawn(world, spawn)) {
            return spawn;
        }

        // Search near spawn
        return searchSpiral(world, spawn);
    }
}
