package com.wickedsik.personalworlds.dimension.cleanup;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChunkSanitizer} covering the pure logic. The
 * ServerWorld/WorldChunk adapter is verified by cross-version compile only.
 */
class ChunkSanitizerTest {

    private FakeTarget target;

    @BeforeEach
    void setUp() {
        target = new FakeTarget();
    }

    @Test
    @DisplayName("Empty target: no removals, markDirty not invoked")
    void emptyTarget_noWork() {
        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, true);

        assertEquals(0, result.orphanBlockEntities());
        assertEquals(0, result.orphanBlocks());
        assertFalse(result.anyRemoved());
        assertFalse(target.markedDirty);
    }

    @Test
    @DisplayName("Block entity at air position is removed and counted")
    void orphanBlockEntity_removed() {
        BlockPos orphan = new BlockPos(1, 64, 1);
        target.blockEntities.add(orphan);
        target.airPositions.add(orphan);

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, false);

        assertEquals(1, result.orphanBlockEntities());
        assertTrue(target.removedBlockEntities.contains(orphan));
        assertTrue(target.markedDirty);
    }

    @Test
    @DisplayName("Block entity backed by a real block is left alone")
    void backedBlockEntity_preserved() {
        BlockPos backed = new BlockPos(2, 64, 2);
        target.blockEntities.add(backed);
        // Not in airPositions → isAirAt returns false

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, false);

        assertEquals(0, result.orphanBlockEntities());
        assertTrue(target.removedBlockEntities.isEmpty());
        assertFalse(target.markedDirty);
    }

    @Test
    @DisplayName("Block failing canBlockSurviveAt is set to air and counted")
    void unsupportedBlock_removed() {
        BlockPos floatingFire = new BlockPos(3, 65, 3);
        target.nonAirPositions.add(floatingFire);
        target.cannotSurviveAt.add(floatingFire);

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, true);

        assertEquals(1, result.orphanBlocks());
        assertTrue(target.setToAir.contains(floatingFire));
        assertTrue(target.markedDirty);
    }

    @Test
    @DisplayName("Block that can survive is left alone")
    void supportedBlock_preserved() {
        BlockPos stableBlock = new BlockPos(4, 64, 4);
        target.nonAirPositions.add(stableBlock);
        // Not in cannotSurviveAt → canBlockSurviveAt returns true

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, true);

        assertEquals(0, result.orphanBlocks());
        assertTrue(target.setToAir.isEmpty());
        assertFalse(target.markedDirty);
    }

    @Test
    @DisplayName("removeOrphanBlocks=false skips the canPlaceAt sweep entirely")
    void supportSweepGated_skippedWhenFalse() {
        BlockPos floatingFire = new BlockPos(5, 65, 5);
        target.nonAirPositions.add(floatingFire);
        target.cannotSurviveAt.add(floatingFire);

        // Also queue up an orphan BE to prove the BE pass still runs.
        BlockPos orphanBE = new BlockPos(6, 64, 6);
        target.blockEntities.add(orphanBE);
        target.airPositions.add(orphanBE);

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, false);

        assertEquals(1, result.orphanBlockEntities());
        assertEquals(0, result.orphanBlocks(), "support-block sweep must not run when flag is false");
        assertTrue(target.setToAir.isEmpty());
        assertTrue(target.markedDirty);
    }

    @Test
    @DisplayName("Placeholder inventory slot is cleared and counted")
    void placeholderInventorySlot_cleared() {
        FakeInventorySlot bad = new FakeInventorySlot(true);
        target.inventorySlots.add(bad);

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, false);

        assertEquals(1, result.orphanItems());
        assertTrue(bad.cleared);
        assertTrue(target.markedDirty);
    }

    @Test
    @DisplayName("Valid inventory slot is left alone")
    void validInventorySlot_preserved() {
        FakeInventorySlot good = new FakeInventorySlot(false);
        target.inventorySlots.add(good);

        ChunkSanitizer.Result result = ChunkSanitizer.sanitize(target, false);

        assertEquals(0, result.orphanItems());
        assertFalse(good.cleared);
        assertFalse(target.markedDirty);
    }

    private static final class FakeInventorySlot implements ChunkSanitizer.InventorySlot {
        private final boolean placeholder;
        boolean cleared = false;

        FakeInventorySlot(boolean placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        public boolean isPlaceholder() {
            return placeholder;
        }

        @Override
        public void clear() {
            cleared = true;
        }
    }

    /**
     * In-memory fake target. Tests populate sets to describe the initial world
     * state and inspect capture sets to verify removals.
     */
    private static final class FakeTarget implements ChunkSanitizer.Target {
        final Set<BlockPos> blockEntities = new HashSet<>();
        final Set<BlockPos> airPositions = new HashSet<>();
        final Set<BlockPos> nonAirPositions = new HashSet<>();
        final Set<BlockPos> cannotSurviveAt = new HashSet<>();
        final List<ChunkSanitizer.InventorySlot> inventorySlots = new ArrayList<>();

        final Set<BlockPos> removedBlockEntities = new HashSet<>();
        final Set<BlockPos> setToAir = new HashSet<>();
        boolean markedDirty = false;

        @Override
        public Iterable<BlockPos> blockEntityPositions() {
            return new ArrayList<>(blockEntities);
        }

        @Override
        public boolean isAirAt(BlockPos pos) {
            return airPositions.contains(pos);
        }

        @Override
        public void removeBlockEntity(BlockPos pos) {
            removedBlockEntities.add(pos);
        }

        @Override
        public Iterable<BlockPos> nonAirPositions() {
            return new ArrayList<>(nonAirPositions);
        }

        @Override
        public boolean canBlockSurviveAt(BlockPos pos) {
            return !cannotSurviveAt.contains(pos);
        }

        @Override
        public void setAir(BlockPos pos) {
            setToAir.add(pos);
        }

        @Override
        public Iterable<ChunkSanitizer.InventorySlot> inventorySlots() {
            return new ArrayList<>(inventorySlots);
        }

        @Override
        public void markDirty() {
            markedDirty = true;
        }
    }
}
