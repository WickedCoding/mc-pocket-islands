package com.wickedsik.personalworlds.dimension.cleanup;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizes pocket-dimension chunks as they load, purging state left behind by
 * removed mods.
 *
 * Post-load, vanilla has already resolved unknown block IDs to air and dropped
 * unresolvable ItemStacks to {@link net.minecraft.item.ItemStack#EMPTY}. Two
 * classes of orphan remain:
 *
 * 1. Block entities whose backing block state is air (mod block removed, BE
 *    persisted). These are invisible but still tick and serialize.
 * 2. Blocks that can no longer survive in their position because the block they
 *    depended on was removed (fire, torches, redstone, snow layer, carpets,
 *    saplings, ladders, vines, pressure plates). Vanilla would break these on
 *    the next block update; we accelerate that so they don't linger as ghost
 *    geometry.
 *
 * The pure sanitization logic operates on a {@link Target} interface so it can
 * be tested without a Minecraft runtime. {@link #onChunkLoad} adapts a live
 * {@code ServerWorld}/{@code WorldChunk} pair to that interface and enforces
 * pocket-dimension scoping via {@link PortalHelper#isInPersonalDimension}.
 */
public final class ChunkSanitizer {

    private ChunkSanitizer() {
    }

    /**
     * Abstract surface for the sanitizer's chunk operations. The production
     * adapter wraps {@link WorldChunk} + {@link ServerWorld}; tests provide an
     * in-memory fake.
     */
    public interface Target {
        Iterable<BlockPos> blockEntityPositions();

        boolean isAirAt(BlockPos pos);

        void removeBlockEntity(BlockPos pos);

        Iterable<BlockPos> nonAirPositions();

        boolean canBlockSurviveAt(BlockPos pos);

        void setAir(BlockPos pos);

        void markDirty();
    }

    public record Result(int orphanBlockEntities, int orphanBlocks) {
        public boolean anyRemoved() {
            return orphanBlockEntities > 0 || orphanBlocks > 0;
        }
    }

    /**
     * Sanitize the target. Returns counts of what was removed. Only calls
     * {@link Target#markDirty()} if at least one removal happened.
     *
     * @param target             the chunk-shaped surface to clean
     * @param removeOrphanBlocks whether to run the canBlockSurviveAt sweep
     */
    public static Result sanitize(Target target, boolean removeOrphanBlocks) {
        int orphanBEs = removeOrphanBlockEntities(target);
        int orphanBlocks = removeOrphanBlocks ? removeOrphanSupportBlocks(target) : 0;

        Result result = new Result(orphanBEs, orphanBlocks);
        if (result.anyRemoved()) {
            target.markDirty();
        }
        return result;
    }

    private static int removeOrphanBlockEntities(Target target) {
        List<BlockPos> orphans = new ArrayList<>();
        for (BlockPos pos : target.blockEntityPositions()) {
            if (target.isAirAt(pos)) {
                orphans.add(pos);
            }
        }
        for (BlockPos pos : orphans) {
            target.removeBlockEntity(pos);
        }
        return orphans.size();
    }

    private static int removeOrphanSupportBlocks(Target target) {
        List<BlockPos> orphans = new ArrayList<>();
        for (BlockPos pos : target.nonAirPositions()) {
            if (!target.canBlockSurviveAt(pos)) {
                orphans.add(pos);
            }
        }
        for (BlockPos pos : orphans) {
            target.setAir(pos);
        }
        return orphans.size();
    }

    /**
     * Fabric {@code ServerChunkEvents.CHUNK_LOAD} handler. Enforces
     * pocket-dimension scoping and config flags, then delegates to
     * {@link #sanitize}.
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        ModConfig config = ModConfig.get();
        if (!config.sanitizeChunksOnLoad) {
            return;
        }
        if (!PortalHelper.isInPersonalDimension(world)) {
            return;
        }

        Result result = sanitize(new WorldChunkTarget(world, chunk), config.sanitizeRemoveOrphanBlocks);

        if (result.anyRemoved()) {
            PersonalWorldsMod.LOGGER.info(
                "Sanitized chunk {} in {}: removed {} orphan block entities, {} unsupported blocks",
                chunk.getPos(), world.getRegistryKey().getValue(),
                result.orphanBlockEntities(), result.orphanBlocks()
            );
        }
    }
}
