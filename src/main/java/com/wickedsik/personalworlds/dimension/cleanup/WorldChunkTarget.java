package com.wickedsik.personalworlds.dimension.cleanup;

import com.wickedsik.personalworlds.compat.WorldCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    private final boolean interiorOnly;

    /**
     * Default constructor: interior-only sweep. Safe to use from any context,
     * including code paths that might race with unloaded neighbour chunks.
     */
    WorldChunkTarget(ServerWorld world, WorldChunk chunk) {
        this(world, chunk, true);
    }

    /**
     * Explicit-scope constructor. Pass {@code interiorOnly = false} only when
     * the caller has already guaranteed that every horizontally adjacent
     * chunk is loaded — for example, an admin-triggered sweep that
     * force-loads a bounded chunk region before iterating it. Passing
     * {@code false} in any other context risks the deadlock the interior
     * filter exists to prevent.
     */
    WorldChunkTarget(ServerWorld world, WorldChunk chunk, boolean interiorOnly) {
        this.world = world;
        this.chunk = chunk;
        this.interiorOnly = interiorOnly;
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

        // Interior-only mode skips the chunk's outer border (localX or
        // localZ in {0, 15}). Vanilla canPlaceAt implementations look at
        // direct horizontal neighbours; on a border block that neighbour
        // lives in an adjacent chunk, which would force a synchronous chunk
        // load and can deadlock the caller when invoked from a chunk-load
        // callback. Full-chunk mode is only safe when the caller has
        // already guaranteed all neighbour chunks are loaded.
        int minLocal = interiorOnly ? 1 : 0;
        int maxLocal = interiorOnly ? 15 : 16;

        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = minY; y < maxY; y++) {
            for (int localX = minLocal; localX < maxLocal; localX++) {
                for (int localZ = minLocal; localZ < maxLocal; localZ++) {
                    cursor.set(startX + localX, y, startZ + localZ);
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
    public Iterable<ChunkSanitizer.InventorySlot> inventorySlots() {
        // Only top-level slots of inventory-bearing BEs. Nested containers
        // (shulker box inside chest, bundle contents) are not recursed into —
        // that would need cross-version handling for NBT sub-tags on 1.20.x
        // vs data components on 1.21.x, and is best left to a dedicated pass.
        java.util.List<ChunkSanitizer.InventorySlot> slots = new java.util.ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Inventory inv) {
                int size = inv.size();
                for (int i = 0; i < size; i++) {
                    slots.add(new InventorySlotHandle(be, inv, i));
                }
            }
        }
        return slots;
    }

    @Override
    public void markDirty() {
        //? if >=1.21 {
        /*chunk.markNeedsSaving();
        *///?} else {
        chunk.setNeedsSaving(true);
        //?}
    }

    /**
     * Detects the fingerprint of a malformed stack: item resolved to AIR but
     * the stored count is non-zero. A well-formed empty slot is
     * {@link ItemStack#EMPTY} with count 0. Anything else with item=AIR
     * indicates a deserialization path that survived vanilla's normal
     * EMPTY-replacement.
     */
    private static final class InventorySlotHandle implements ChunkSanitizer.InventorySlot {
        private final BlockEntity owner;
        private final Inventory inventory;
        private final int slot;

        InventorySlotHandle(BlockEntity owner, Inventory inventory, int slot) {
            this.owner = owner;
            this.inventory = inventory;
            this.slot = slot;
        }

        @Override
        public boolean isPlaceholder() {
            ItemStack stack = inventory.getStack(slot);
            return stack.getItem() == Items.AIR && stack.getCount() > 0;
        }

        @Override
        public void clear() {
            inventory.setStack(slot, ItemStack.EMPTY);
            owner.markDirty();
        }
    }
}
