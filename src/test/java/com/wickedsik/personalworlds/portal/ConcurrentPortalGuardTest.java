package com.wickedsik.personalworlds.portal;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConcurrentPortalGuard.
 * Tests lock acquisition, release, and cleanup logic.
 */
class ConcurrentPortalGuardTest {

    private static final UUID PLAYER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PLAYER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final BlockPos PORTAL_POS = new BlockPos(100, 65, 200);
    private static final BlockPos OTHER_PORTAL_POS = new BlockPos(500, 65, 500);

    @BeforeEach
    void setUp() {
        // Clear all locks before each test for isolation
        ConcurrentPortalGuard.clearAllForTesting();
    }

    @AfterEach
    void tearDown() {
        // Ensure clean state after tests
        ConcurrentPortalGuard.clearAllForTesting();
    }

    @Nested
    @DisplayName("Lock Acquisition")
    class LockAcquisition {

        @Test
        @DisplayName("First acquisition attempt succeeds")
        void tryAcquire_firstAttempt_succeeds() {
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            assertTrue(result);
        }

        @Test
        @DisplayName("Second attempt by same player within cooldown fails")
        void tryAcquire_withinCooldown_fails() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            // Immediate second attempt should fail (within 1 second cooldown)
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            assertFalse(result);
        }

        @Test
        @DisplayName("Same player at different portal within cooldown still fails")
        void tryAcquire_differentPortalWithinCooldown_fails() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            // Same player, different portal, but within cooldown
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_A, OTHER_PORTAL_POS);

            assertFalse(result);
        }

        @Test
        @DisplayName("Different player at different portal succeeds")
        void tryAcquire_differentPlayerDifferentPortal_succeeds() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_B, OTHER_PORTAL_POS);

            assertTrue(result);
        }

        @Test
        @DisplayName("Different player at same portal fails while processing")
        void tryAcquire_samePortalDifferentPlayer_fails() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            // Player A has not released the portal lock

            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS);

            assertFalse(result);
        }

        @Test
        @DisplayName("Multiple acquisitions by different players at different portals all succeed")
        void tryAcquire_multiplePlayersDifferentPortals_allSucceed() {
            boolean resultA = ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            boolean resultB = ConcurrentPortalGuard.tryAcquire(PLAYER_B, OTHER_PORTAL_POS);

            assertTrue(resultA);
            assertTrue(resultB);
        }
    }

    @Nested
    @DisplayName("Lock Release")
    class LockRelease {

        @Test
        @DisplayName("Release clears portal lock for other players")
        void release_allowsOtherPlayers() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.release(PLAYER_A, PORTAL_POS);

            // Player B should now be able to use the portal
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS);

            assertTrue(result);
        }

        @Test
        @DisplayName("Release does not clear player cooldown")
        void release_doesNotClearCooldown() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.release(PLAYER_A, PORTAL_POS);

            // Same player still has cooldown
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            assertFalse(result);
        }

        @Test
        @DisplayName("Force release clears all locks for player")
        void forceRelease_clearsAllLocks() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            ConcurrentPortalGuard.forceRelease(PLAYER_A);

            // Portal should now be available to other players
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS));
        }

        @Test
        @DisplayName("Force release clears player cooldown")
        void forceRelease_clearsCooldown() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            ConcurrentPortalGuard.forceRelease(PLAYER_A);

            // Same player can now acquire again
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS));
        }

        @Test
        @DisplayName("Force release only affects specified player")
        void forceRelease_onlyAffectsSpecifiedPlayer() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.tryAcquire(PLAYER_B, OTHER_PORTAL_POS);

            ConcurrentPortalGuard.forceRelease(PLAYER_A);

            // Player B's lock should still be active
            assertFalse(ConcurrentPortalGuard.tryAcquire(PLAYER_A, OTHER_PORTAL_POS));
        }
    }

    @Nested
    @DisplayName("Position Hashing")
    class PositionHashing {

        @Test
        @DisplayName("Same position produces consistent hash")
        void hashPosition_consistentHashing() {
            long hash1 = ConcurrentPortalGuard.hashPosition(PORTAL_POS);
            long hash2 = ConcurrentPortalGuard.hashPosition(PORTAL_POS);

            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Different positions produce different hashes")
        void hashPosition_differentPositions_differentHashes() {
            long hash1 = ConcurrentPortalGuard.hashPosition(PORTAL_POS);
            long hash2 = ConcurrentPortalGuard.hashPosition(OTHER_PORTAL_POS);

            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Negative coordinates hash correctly")
        void hashPosition_negativeCoordinates() {
            BlockPos negative = new BlockPos(-100, 65, -200);

            long hash = ConcurrentPortalGuard.hashPosition(negative);

            // Should not throw, should produce valid hash
            assertNotEquals(0, hash);
        }

        @Test
        @DisplayName("Zero position produces valid hash")
        void hashPosition_zeroPosition() {
            BlockPos zero = new BlockPos(0, 0, 0);

            long hash = ConcurrentPortalGuard.hashPosition(zero);

            // Zero position should produce hash of 0 (all bits zero)
            assertEquals(0L, hash);
        }

        @Test
        @DisplayName("Maximum coordinates hash without overflow")
        void hashPosition_maxCoordinates() {
            BlockPos max = new BlockPos(30_000_000, 320, 30_000_000);

            // Should not throw
            assertDoesNotThrow(() -> ConcurrentPortalGuard.hashPosition(max));
        }

        @Test
        @DisplayName("Minimum coordinates hash without issues")
        void hashPosition_minCoordinates() {
            BlockPos min = new BlockPos(-30_000_000, -64, -30_000_000);

            // Should not throw
            assertDoesNotThrow(() -> ConcurrentPortalGuard.hashPosition(min));
        }

        @Test
        @DisplayName("Y coordinate only differs produces different hash")
        void hashPosition_yDifference_differentHash() {
            BlockPos pos1 = new BlockPos(100, 64, 100);
            BlockPos pos2 = new BlockPos(100, 65, 100);

            assertNotEquals(
                ConcurrentPortalGuard.hashPosition(pos1),
                ConcurrentPortalGuard.hashPosition(pos2)
            );
        }

        @Test
        @DisplayName("X coordinate only differs produces different hash")
        void hashPosition_xDifference_differentHash() {
            BlockPos pos1 = new BlockPos(100, 64, 100);
            BlockPos pos2 = new BlockPos(101, 64, 100);

            assertNotEquals(
                ConcurrentPortalGuard.hashPosition(pos1),
                ConcurrentPortalGuard.hashPosition(pos2)
            );
        }

        @Test
        @DisplayName("Z coordinate only differs produces different hash")
        void hashPosition_zDifference_differentHash() {
            BlockPos pos1 = new BlockPos(100, 64, 100);
            BlockPos pos2 = new BlockPos(100, 64, 101);

            assertNotEquals(
                ConcurrentPortalGuard.hashPosition(pos1),
                ConcurrentPortalGuard.hashPosition(pos2)
            );
        }
    }

    @Nested
    @DisplayName("Cleanup Operations")
    class CleanupOperations {

        @Test
        @DisplayName("Cleanup does not affect fresh locks")
        void cleanup_preservesFreshLocks() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            ConcurrentPortalGuard.cleanup();

            // Player A should still have cooldown (lock is fresh)
            assertFalse(ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS));
            // Portal should still be locked
            assertFalse(ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS));
        }

        @Test
        @DisplayName("Multiple cleanups are safe")
        void cleanup_multipleCallsSafe() {
            assertDoesNotThrow(() -> {
                ConcurrentPortalGuard.cleanup();
                ConcurrentPortalGuard.cleanup();
                ConcurrentPortalGuard.cleanup();
            });
        }

        @Test
        @DisplayName("Cleanup on empty state is safe")
        void cleanup_emptyState_safe() {
            assertDoesNotThrow(ConcurrentPortalGuard::cleanup);
        }
    }

    @Nested
    @DisplayName("Clear All For Testing")
    class ClearAllForTesting {

        @Test
        @DisplayName("Clear all removes all locks")
        void clearAllForTesting_removesAllLocks() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.tryAcquire(PLAYER_B, OTHER_PORTAL_POS);

            ConcurrentPortalGuard.clearAllForTesting();

            // Both players should be able to acquire again
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS));
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_B, OTHER_PORTAL_POS));
        }

        @Test
        @DisplayName("Clear all on empty state is safe")
        void clearAllForTesting_emptyState_safe() {
            assertDoesNotThrow(ConcurrentPortalGuard::clearAllForTesting);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Force release with unknown UUID is safe")
        void forceRelease_unknownUuid_safe() {
            UUID unknownUuid = UUID.randomUUID();

            assertDoesNotThrow(() -> ConcurrentPortalGuard.forceRelease(unknownUuid));
        }

        @Test
        @DisplayName("Release without prior acquisition is safe")
        void release_withoutAcquisition_safe() {
            assertDoesNotThrow(() ->
                ConcurrentPortalGuard.release(PLAYER_A, PORTAL_POS)
            );
        }

        @Test
        @DisplayName("Acquire after force release allows immediate reacquisition")
        void acquireAfterForceRelease_immediateReacquisition() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.forceRelease(PLAYER_A);

            // Should succeed immediately - no cooldown after force release
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS));
        }
    }
}
