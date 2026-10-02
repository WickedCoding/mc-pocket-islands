package com.wickedsik.personalworlds.dimension.cleanup;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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

    private static final PendingChunkQueue<ServerWorld> PENDING = new PendingChunkQueue<>();

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

        Iterable<InventorySlot> inventorySlots();

        void markDirty();
    }

    /**
     * A single slot inside an inventory-bearing block entity. The sanitizer
     * only knows how to ask whether the slot holds a malformed stack and how
     * to clear it — the adapter decides what "malformed" means.
     */
    public interface InventorySlot {
        boolean isPlaceholder();

        void clear();
    }

    public record Result(int orphanBlockEntities, int orphanBlocks, int orphanItems) {
        public boolean anyRemoved() {
            return orphanBlockEntities > 0 || orphanBlocks > 0 || orphanItems > 0;
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
        int orphanItems = clearMalformedInventorySlots(target);

        Result result = new Result(orphanBEs, orphanBlocks, orphanItems);
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

    private static int clearMalformedInventorySlots(Target target) {
        int cleared = 0;
        for (InventorySlot slot : target.inventorySlots()) {
            if (slot.isPlaceholder()) {
                slot.clear();
                cleared++;
            }
        }
        return cleared;
    }

    /**
     * Fabric {@code ServerChunkEvents.CHUNK_LOAD} handler. Enforces
     * pocket-dimension scoping and config flags, then queues the chunk for
     * {@link #processPending}, which the server-tick handler calls.
     *
     * Nothing may touch the world from here. The callback runs on the server
     * thread inside the chunk-load task pump, before the chunk's load future
     * completes. Looking the chunk up (or a neighbour, via {@code canPlaceAt})
     * makes the thread wait for a load that can only finish after this
     * callback returns, and the watchdog kills the server.
     *
     * {@link MinecraftServer#execute} does not help: on the server thread it
     * runs the task immediately instead of queueing it.
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        if (!ModConfig.get().sanitizeChunksOnLoad) {
            return;
        }
        if (!PortalHelper.isInPersonalDimension(world)) {
            return;
        }

        PENDING.enqueue(world, chunk.getPos().toLong());
    }

    /**
     * Sanitizes every chunk queued by {@link #onChunkLoad}. Call from the end
     * of the server tick, outside any chunk-loading code.
     */
    public static void processPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        boolean removeOrphans = ModConfig.get().sanitizeRemoveOrphanBlocks;
        PENDING.drain((world, chunkPos) -> {
            try {
                runDeferredSanitize(world, new ChunkPos(chunkPos), removeOrphans);
            } catch (RuntimeException e) {
                PersonalWorldsMod.LOGGER.warn("Failed to sanitize chunk {} in {}",
                    new ChunkPos(chunkPos), IdentifierCompat.fromKey(world.getRegistryKey()), e);
            }
        });
    }

    /** Drops queued work, e.g. on server stop. */
    public static void clearPending() {
        PENDING.clear();
    }

    /**
     * Directly sanitize a live chunk. Intended for admin-triggered sweeps
     * that run on the tick thread and have already ensured the target chunk
     * and its neighbours are loaded.
     *
     * @param world              the world containing the chunk
     * @param chunk              the chunk to sanitize
     * @param fullChunk          when true, sweeps the entire 16×16 chunk
     *                           footprint. Only pass true if all four
     *                           horizontally adjacent chunks are loaded —
     *                           otherwise a border block's {@code canPlaceAt}
     *                           may force a synchronous neighbour-chunk load
     * @param removeOrphanBlocks whether to run the canPlaceAt sweep
     * @return counts of what was removed
     */
    public static Result sanitizeLoadedChunk(
        ServerWorld world,
        WorldChunk chunk,
        boolean fullChunk,
        boolean removeOrphanBlocks
    ) {
        return sanitize(new WorldChunkTarget(world, chunk, !fullChunk), removeOrphanBlocks);
    }

    private static void runDeferredSanitize(ServerWorld world, ChunkPos pos, boolean removeOrphans) {
        // The chunk may have unloaded since the load event (player left,
        // server flushed the ticket). getWorldChunk never waits on a load:
        // it returns null unless the chunk is fully loaded right now.
        WorldChunk worldChunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        if (worldChunk == null) {
            return;
        }

        Result result = sanitize(new WorldChunkTarget(world, worldChunk), removeOrphans);

        if (result.anyRemoved()) {
            PersonalWorldsMod.LOGGER.info(
                "Sanitized chunk {} in {}: removed {} orphan block entities, {} unsupported blocks, {} malformed items",
                pos, IdentifierCompat.fromKey(world.getRegistryKey()),
                result.orphanBlockEntities(), result.orphanBlocks(), result.orphanItems()
            );
        }
    }
}
