package com.wickedsik.personalworlds.dimension.cleanup;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
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
     * pocket-dimension scoping and config flags, then defers the actual
     * sanitize pass to the next server tick.
     *
     * The deferral is required to avoid a server-thread deadlock: running
     * inline during the chunk-load callback is unsafe because if any block's
     * {@code canPlaceAt}
     * walks into an unloaded neighbour chunk, {@code getBlockState} recurses
     * into {@code getChunkBlocking} while still inside the outer chunk-load
     * task pump, and the watchdog eventually kills the thread. Posting the
     * work with {@link MinecraftServer#execute} guarantees it runs outside
     * that pump on a subsequent tick.
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        ModConfig config = ModConfig.get();
        if (!config.sanitizeChunksOnLoad) {
            return;
        }
        if (!PortalHelper.isInPersonalDimension(world)) {
            return;
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        boolean removeOrphans = config.sanitizeRemoveOrphanBlocks;
        server.execute(() -> runDeferredSanitize(world, pos, removeOrphans));
    }

    private static void runDeferredSanitize(ServerWorld world, ChunkPos pos, boolean removeOrphans) {
        // The chunk may have unloaded between the load event and this tick
        // (player left, server flushed the ticket). Fetch without forcing a
        // reload and bail if it's gone.
        Chunk current = world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (!(current instanceof WorldChunk worldChunk)) {
            return;
        }

        Result result = sanitize(new WorldChunkTarget(world, worldChunk), removeOrphans);

        if (result.anyRemoved()) {
            PersonalWorldsMod.LOGGER.info(
                "Sanitized chunk {} in {}: removed {} orphan block entities, {} unsupported blocks, {} malformed items",
                pos, world.getRegistryKey().getValue(),
                result.orphanBlockEntities(), result.orphanBlocks(), result.orphanItems()
            );
        }
    }
}
