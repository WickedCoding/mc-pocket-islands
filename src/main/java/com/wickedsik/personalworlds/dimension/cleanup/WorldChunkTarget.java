package com.wickedsik.personalworlds.dimension.cleanup;

import com.wickedsik.personalworlds.compat.WorldCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Adapts a live {@link WorldChunk} + {@link ServerWorld} pair to
 * {@link ChunkSanitizer.Target}. This class is intentionally kept as a thin
 * passthrough — all interesting logic lives in {@link ChunkSanitizer}.
 */
final class WorldChunkTarget implements ChunkSanitizer.Target {

    private final ServerWorld world;
    private final WorldChunk chunk;

    WorldChunkTarget(ServerWorld world, WorldChunk chunk) {
        this.world = world;
        this.chunk = chunk;
    }

    @Override
    public Iterable<BlockPos> blockEntityPositions() {
        // Snapshot to a fresh list — the underlying map may be mutated as we
        // remove entities during the sweep.
        return java.util.List.copyOf(chunk.getBlockEntities().keySet());
    }

    @Override
    public boolean isAirAt(BlockPos pos) {
        return chunk.getBlockState(pos).isAir();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        world.removeBlockEntity(pos);
    }

    @Override
    public Iterable<BlockPos> nonAirPositions() {
        int minY = world.getBottomY();
        int maxY = WorldCompat.getTopY(world);
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(startX + x, y, startZ + z);
                    if (!chunk.getBlockState(cursor).isAir()) {
                        positions.add(cursor.toImmutable());
                    }
                }
            }
        }
        return positions;
    }

    @Override
    public boolean canBlockSurviveAt(BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        return state.canPlaceAt(world, pos);
    }

    @Override
    public void setAir(BlockPos pos) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    @Override
    public void markDirty() {
        //? if >=1.21 {
        /*chunk.markNeedsSaving();
        *///?} else {
        chunk.setNeedsSaving(true);
        //?}
    }
}
