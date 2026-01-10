# Unit Testing Plan - Phases 1 & 2

## Overview

This plan establishes unit testing infrastructure for PersonalWorlds, focusing on classes with pure logic that can be tested without Minecraft runtime dependencies.

**Testing Phases:**

- **Phase 1**: Pure logic classes (no mocks required)
- **Phase 2**: Data record classes (NBT serialization round-trips)
- **Phase 3**: Classes requiring mocks (deferred)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          Unit Testing Architecture                            │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Phase 1: Pure Logic                    Phase 2: Data Records                │
│  ┌─────────────────────┐                ┌─────────────────────┐              │
│  │ DataValidator       │                │ PlayerDimensionData │              │
│  │ - UUID validation   │                │ - NBT round-trip    │              │
│  │ - Position bounds   │                │ - Field validation  │              │
│  │ - Name sanitization │                │ - Immutability      │              │
│  └─────────────────────┘                └─────────────────────┘              │
│  ┌─────────────────────┐                ┌─────────────────────┐              │
│  │ ConcurrentPortalGuard│               │ ReturnData          │              │
│  │ - Lock acquisition  │                │ - NBT round-trip    │              │
│  │ - Cooldown logic    │                │ - Position storage  │              │
│  │ - Position hashing  │                │ - Dimension keys    │              │
│  └─────────────────────┘                └─────────────────────┘              │
│  ┌─────────────────────┐                ┌─────────────────────┐              │
│  │ PortalFrame         │                │ InvitationData      │              │
│  │ - Interior positions│                │ - NBT round-trip    │              │
│  │ - Frame positions   │                │ - Timestamp handling│              │
│  │ - Center calculation│                │ - Owner tracking    │              │
│  └─────────────────────┘                └─────────────────────┘              │
│  ┌─────────────────────┐                                                     │
│  │ WorldGenType        │                                                     │
│  │ - Enum parsing      │                                                     │
│  │ - Fallback behavior │                                                     │
│  └─────────────────────┘                                                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Test Framework Setup

### Dependencies

**build.gradle additions:**

```groovy
dependencies {
    // Existing dependencies...

    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
    testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()

    testLogging {
        events "passed", "skipped", "failed"
        showStandardStreams = true
    }
}
```

### Directory Structure

```
src/
├── main/java/com/wickedsik/personalworlds/
│   └── ... (existing code)
└── test/java/com/wickedsik/personalworlds/
    ├── util/
    │   └── DataValidatorTest.java
    ├── portal/
    │   ├── ConcurrentPortalGuardTest.java
    │   └── PortalFrameTest.java
    ├── dimension/
    │   ├── WorldGenTypeTest.java
    │   ├── PlayerDimensionDataTest.java
    │   └── ReturnDataTest.java
    └── player/
        └── InvitationDataTest.java
```

---

## Phase 1: Pure Logic Tests

### Test Class 1: DataValidatorTest

**Target:** `src/main/java/com/wickedsik/personalworlds/util/DataValidator.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/util/DataValidatorTest.java`

#### Test Cases

| Test Method | Description | Input | Expected Output |
|-------------|-------------|-------|-----------------|
| `validateUuid_validUuid_returnsPresent` | Valid UUID string | `"550e8400-e29b-41d4-a716-446655440000"` | `Optional.of(UUID)` |
| `validateUuid_nullInput_returnsEmpty` | Null input | `null` | `Optional.empty()` |
| `validateUuid_emptyString_returnsEmpty` | Empty string | `""` | `Optional.empty()` |
| `validateUuid_malformedUuid_returnsEmpty` | Invalid format | `"not-a-uuid"` | `Optional.empty()` |
| `validateUuid_shortUuid_returnsEmpty` | Truncated UUID | `"550e8400-e29b"` | `Optional.empty()` |
| `isValidUuid_nonNull_returnsTrue` | Valid UUID object | `UUID.randomUUID()` | `true` |
| `isValidUuid_null_returnsFalse` | Null UUID | `null` | `false` |
| `sanitizeBlockPos_null_returnsDefault` | Null position | `null` | `BlockPos(0, 64, 0)` |
| `sanitizeBlockPos_withinBounds_returnsOriginal` | Valid position | `BlockPos(100, 65, -200)` | Same position |
| `sanitizeBlockPos_xTooHigh_clamps` | X exceeds max | `BlockPos(50_000_000, 65, 0)` | `BlockPos(30_000_000, 65, 0)` |
| `sanitizeBlockPos_yTooLow_clamps` | Y below minimum | `BlockPos(0, -100, 0)` | `BlockPos(0, -64, 0)` |
| `sanitizeBlockPos_yTooHigh_clamps` | Y above maximum | `BlockPos(0, 500, 0)` | `BlockPos(0, 320, 0)` |
| `sanitizePlayerName_null_returnsUnknown` | Null name | `null` | `"Unknown"` |
| `sanitizePlayerName_empty_returnsUnknown` | Empty string | `""` | `"Unknown"` |
| `sanitizePlayerName_valid_returnsOriginal` | Normal name | `"Steve"` | `"Steve"` |
| `sanitizePlayerName_tooLong_truncates` | 50 char name | `"A".repeat(50)` | 32 char string |
| `sanitizePlayerName_nonPrintable_removes` | Contains control chars | `"Steve\u0000"` | `"Steve"` |
| `sanitizePlayerName_whitespace_trims` | Leading/trailing spaces | `"  Steve  "` | `"Steve"` |

#### Implementation

```java
package com.wickedsik.personalworlds.util;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DataValidatorTest {

    @Nested
    @DisplayName("UUID Validation")
    class UuidValidation {

        @Test
        @DisplayName("Valid UUID string returns present Optional")
        void validateUuid_validUuid_returnsPresent() {
            String validUuid = "550e8400-e29b-41d4-a716-446655440000";

            Optional<UUID> result = DataValidator.validateUuid(validUuid);

            assertTrue(result.isPresent());
            assertEquals(UUID.fromString(validUuid), result.get());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null or empty string returns empty Optional")
        void validateUuid_nullOrEmpty_returnsEmpty(String input) {
            Optional<UUID> result = DataValidator.validateUuid(input);

            assertTrue(result.isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "not-a-uuid",
            "550e8400-e29b",
            "550e8400-e29b-41d4-a716-44665544000g",
            "550e8400e29b41d4a716446655440000"
        })
        @DisplayName("Malformed UUID strings return empty Optional")
        void validateUuid_malformed_returnsEmpty(String input) {
            Optional<UUID> result = DataValidator.validateUuid(input);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Non-null UUID is valid")
        void isValidUuid_nonNull_returnsTrue() {
            assertTrue(DataValidator.isValidUuid(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Null UUID is invalid")
        void isValidUuid_null_returnsFalse() {
            assertFalse(DataValidator.isValidUuid(null));
        }
    }

    @Nested
    @DisplayName("BlockPos Sanitization")
    class BlockPosSanitization {

        @Test
        @DisplayName("Null position returns safe default")
        void sanitizeBlockPos_null_returnsDefault() {
            BlockPos result = DataValidator.sanitizeBlockPos(null);

            assertEquals(new BlockPos(0, 64, 0), result);
        }

        @Test
        @DisplayName("Valid position within bounds returns original")
        void sanitizeBlockPos_withinBounds_returnsOriginal() {
            BlockPos input = new BlockPos(100, 65, -200);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(input, result);
        }

        @Test
        @DisplayName("X coordinate exceeding max is clamped")
        void sanitizeBlockPos_xTooHigh_clamps() {
            BlockPos input = new BlockPos(50_000_000, 65, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(30_000_000, result.getX());
            assertEquals(65, result.getY());
            assertEquals(0, result.getZ());
        }

        @Test
        @DisplayName("X coordinate below min is clamped")
        void sanitizeBlockPos_xTooLow_clamps() {
            BlockPos input = new BlockPos(-50_000_000, 65, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(-30_000_000, result.getX());
        }

        @Test
        @DisplayName("Y coordinate below min is clamped to -64")
        void sanitizeBlockPos_yTooLow_clamps() {
            BlockPos input = new BlockPos(0, -100, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(-64, result.getY());
        }

        @Test
        @DisplayName("Y coordinate above max is clamped to 320")
        void sanitizeBlockPos_yTooHigh_clamps() {
            BlockPos input = new BlockPos(0, 500, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(320, result.getY());
        }

        @Test
        @DisplayName("Z coordinate is clamped within bounds")
        void sanitizeBlockPos_zOutOfBounds_clamps() {
            BlockPos input = new BlockPos(0, 65, 40_000_000);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(30_000_000, result.getZ());
        }
    }

    @Nested
    @DisplayName("Player Name Sanitization")
    class PlayerNameSanitization {

        @Test
        @DisplayName("Null name returns 'Unknown'")
        void sanitizePlayerName_null_returnsUnknown() {
            assertEquals("Unknown", DataValidator.sanitizePlayerName(null));
        }

        @Test
        @DisplayName("Empty string returns 'Unknown'")
        void sanitizePlayerName_empty_returnsUnknown() {
            assertEquals("Unknown", DataValidator.sanitizePlayerName(""));
        }

        @Test
        @DisplayName("Valid name returns original")
        void sanitizePlayerName_valid_returnsOriginal() {
            assertEquals("Steve", DataValidator.sanitizePlayerName("Steve"));
        }

        @Test
        @DisplayName("Name exceeding 32 chars is truncated")
        void sanitizePlayerName_tooLong_truncates() {
            String longName = "A".repeat(50);

            String result = DataValidator.sanitizePlayerName(longName);

            assertEquals(32, result.length());
        }

        @Test
        @DisplayName("Non-printable characters are removed")
        void sanitizePlayerName_nonPrintable_removes() {
            String withControlChars = "Steve\u0000\u0007";

            String result = DataValidator.sanitizePlayerName(withControlChars);

            assertEquals("Steve", result);
        }

        @Test
        @DisplayName("Whitespace is trimmed")
        void sanitizePlayerName_whitespace_trims() {
            String result = DataValidator.sanitizePlayerName("  Steve  ");

            assertEquals("Steve", result);
        }

        @Test
        @DisplayName("Only whitespace returns 'Unknown'")
        void sanitizePlayerName_onlyWhitespace_returnsUnknown() {
            String result = DataValidator.sanitizePlayerName("   ");

            assertEquals("Unknown", result);
        }
    }
}
```

---

### Test Class 2: ConcurrentPortalGuardTest

**Target:** `src/main/java/com/wickedsik/personalworlds/portal/ConcurrentPortalGuard.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/portal/ConcurrentPortalGuardTest.java`

#### Test Cases

| Test Method | Description | Setup | Expected |
|-------------|-------------|-------|----------|
| `tryAcquire_firstAttempt_succeeds` | First lock attempt | Fresh state | `true` |
| `tryAcquire_withinCooldown_fails` | Second attempt within 1s | Prior acquisition | `false` |
| `tryAcquire_afterCooldown_succeeds` | Attempt after cooldown | Prior acquisition + wait | `true` |
| `tryAcquire_differentPlayer_succeeds` | Different player same portal | Player A locked | `true` (Player B) |
| `tryAcquire_samePortalDifferentPlayer_fails` | Same portal, different player, no release | Player A processing | `false` |
| `release_clearsPortalLock` | Portal released | Acquired lock | Can re-acquire |
| `forceRelease_clearsAllLocks` | Disconnect cleanup | Multiple locks | All cleared |
| `cleanup_removesStaleEntries` | Stale lock cleanup | 6+ second old locks | Locks removed |
| `hashPosition_consistentHashing` | Same position same hash | Any BlockPos | Consistent result |
| `hashPosition_differentPositions_differentHashes` | Unique hashes | Different positions | Different hashes |

#### Implementation

```java
package com.wickedsik.personalworlds.portal;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentPortalGuardTest {

    private static final UUID PLAYER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PLAYER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final BlockPos PORTAL_POS = new BlockPos(100, 65, 200);
    private static final BlockPos OTHER_PORTAL_POS = new BlockPos(500, 65, 500);

    @BeforeEach
    void setUp() {
        // Clear all locks before each test
        ConcurrentPortalGuard.forceRelease(PLAYER_A);
        ConcurrentPortalGuard.forceRelease(PLAYER_B);
        ConcurrentPortalGuard.cleanup();
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
        @DisplayName("Second attempt within cooldown fails")
        void tryAcquire_withinCooldown_fails() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);

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
        @DisplayName("Same player at different portal succeeds")
        void tryAcquire_samePlayerDifferentPortal_afterRelease() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.release(PLAYER_A, PORTAL_POS);

            // Note: Player cooldown still applies, so this tests portal lock, not player cooldown
            // The player cooldown will still block, so we test with a different player
            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS);

            assertTrue(result);
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

            boolean result = ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS);

            assertTrue(result);
        }

        @Test
        @DisplayName("Force release clears all locks for player")
        void forceRelease_clearsAllLocks() {
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS);
            ConcurrentPortalGuard.tryAcquire(PLAYER_A, OTHER_PORTAL_POS);

            ConcurrentPortalGuard.forceRelease(PLAYER_A);

            // Both portals should now be available to other players
            assertTrue(ConcurrentPortalGuard.tryAcquire(PLAYER_B, PORTAL_POS));
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
        @DisplayName("Edge case coordinates hash without overflow")
        void hashPosition_edgeCoordinates() {
            BlockPos edge = new BlockPos(30_000_000, 320, 30_000_000);

            // Should not throw
            assertDoesNotThrow(() -> ConcurrentPortalGuard.hashPosition(edge));
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

            // Player A should still have cooldown
            assertFalse(ConcurrentPortalGuard.tryAcquire(PLAYER_A, PORTAL_POS));
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
    }
}
```

**Note:** Some tests (like cooldown expiry) require either:
1. A test-friendly API to inject timestamps
2. Actual waiting (not recommended for unit tests)
3. Refactoring to accept a `Clock` parameter

We'll add a static method for testing purposes:

```java
// Add to ConcurrentPortalGuard.java for testing
@VisibleForTesting
static void clearAllForTesting() {
    playersInTransit.clear();
    portalsProcessing.clear();
}

@VisibleForTesting
static long hashPosition(BlockPos pos) {
    // Make this public for testing
    return ((long) pos.getX() & 0x3FFFFFFL) << 38 |
           ((long) pos.getY() & 0xFFFFL) << 20 |
           ((long) pos.getZ() & 0xFFFFFFL);
}
```

---

### Test Class 3: PortalFrameTest

**Target:** `src/main/java/com/wickedsik/personalworlds/portal/PortalFrame.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/portal/PortalFrameTest.java`

#### Test Cases

| Test Method | Description | Input | Expected |
|-------------|-------------|-------|----------|
| `getInteriorPositions_3x4frame_returns6positions` | Standard frame interior | 3 wide x 4 tall | 6 positions |
| `getInteriorPositions_minFrame_returns2positions` | Minimum valid frame | 2 wide x 3 tall | 2 positions |
| `getFramePositions_3x4frame_returns10positions` | Frame outline | 3 wide x 4 tall | 10 positions |
| `getCenter_evenWidth_returnsCenterBlock` | Center calculation | Even dimensions | Center BlockPos |
| `getCenter_oddWidth_returnsCenterBlock` | Center calculation | Odd dimensions | Center BlockPos |
| `constructor_zeroWidth_throwsException` | Invalid dimensions | Width 0 | IllegalArgumentException |
| `constructor_negativeHeight_throwsException` | Invalid dimensions | Height -1 | IllegalArgumentException |
| `getInteriorPositions_xAxis_correctOrientation` | X-axis portal | Axis.X | Correct X/Y positions |
| `getInteriorPositions_zAxis_correctOrientation` | Z-axis portal | Axis.Z | Correct Z/Y positions |

#### Implementation

```java
package com.wickedsik.personalworlds.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalFrameTest {

    @Nested
    @DisplayName("Interior Position Calculation")
    class InteriorPositions {

        @Test
        @DisplayName("3x4 frame returns 6 interior positions (3 wide x 2 tall inside)")
        void getInteriorPositions_3x4frame_returns6positions() {
            // Frame is 3 wide x 4 tall, interior is 1 wide x 2 tall
            // Actually for a 4x5 frame (standard nether portal size):
            // The interior is 2 wide x 3 tall = 6 positions
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                4, // width including frame
                5  // height including frame
            );

            List<BlockPos> interior = frame.getInteriorPositions();

            // Interior is (width-2) x (height-2) = 2 x 3 = 6
            assertEquals(6, interior.size());
        }

        @Test
        @DisplayName("Minimum valid frame (3x3) returns 1 interior position")
        void getInteriorPositions_minFrame_returns1position() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                3, // minimum width
                3  // minimum height
            );

            List<BlockPos> interior = frame.getInteriorPositions();

            // Interior is 1 x 1 = 1 position
            assertEquals(1, interior.size());
        }

        @Test
        @DisplayName("Interior positions are within frame bounds")
        void getInteriorPositions_withinBounds() {
            BlockPos corner = new BlockPos(10, 64, 20);
            PortalFrame frame = new PortalFrame(corner, Direction.Axis.X, 4, 5);

            List<BlockPos> interior = frame.getInteriorPositions();

            for (BlockPos pos : interior) {
                // Interior should be at least 1 block from corner in all directions
                assertTrue(pos.getX() > corner.getX());
                assertTrue(pos.getY() > corner.getY());
                // For X-axis portal, Z stays same as corner
            }
        }
    }

    @Nested
    @DisplayName("Frame Position Calculation")
    class FramePositions {

        @Test
        @DisplayName("4x5 frame returns correct number of frame blocks")
        void getFramePositions_4x5frame_correctCount() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                4,
                5
            );

            List<BlockPos> frameBlocks = frame.getFramePositions();

            // Frame perimeter = 2*(width + height) - 4 (corners counted once)
            // = 2*(4+5) - 4 = 14
            assertEquals(14, frameBlocks.size());
        }

        @Test
        @DisplayName("Frame positions form complete outline")
        void getFramePositions_formsOutline() {
            BlockPos corner = new BlockPos(0, 0, 0);
            PortalFrame frame = new PortalFrame(corner, Direction.Axis.X, 4, 5);

            List<BlockPos> frameBlocks = frame.getFramePositions();

            // Check corners are included
            assertTrue(frameBlocks.contains(corner)); // bottom-left
            assertTrue(frameBlocks.contains(corner.add(3, 0, 0))); // bottom-right
            assertTrue(frameBlocks.contains(corner.add(0, 4, 0))); // top-left
            assertTrue(frameBlocks.contains(corner.add(3, 4, 0))); // top-right
        }
    }

    @Nested
    @DisplayName("Center Calculation")
    class CenterCalculation {

        @Test
        @DisplayName("Even width frame returns center block")
        void getCenter_evenWidth_returnsCenter() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                4,
                5
            );

            BlockPos center = frame.getCenter();

            // Center of 4-wide is between 1 and 2 (floor to 1)
            // Center of 5-tall is 2
            assertEquals(new BlockPos(1, 2, 0), center);
        }

        @Test
        @DisplayName("Odd width frame returns center block")
        void getCenter_oddWidth_returnsCenter() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                5,
                5
            );

            BlockPos center = frame.getCenter();

            // Center of 5-wide is 2, center of 5-tall is 2
            assertEquals(new BlockPos(2, 2, 0), center);
        }

        @Test
        @DisplayName("Center respects corner offset")
        void getCenter_withOffset_correctPosition() {
            BlockPos corner = new BlockPos(100, 64, 200);
            PortalFrame frame = new PortalFrame(corner, Direction.Axis.X, 4, 5);

            BlockPos center = frame.getCenter();

            assertEquals(100 + 1, center.getX());
            assertEquals(64 + 2, center.getY());
            assertEquals(200, center.getZ()); // Z unchanged for X-axis portal
        }
    }

    @Nested
    @DisplayName("Axis Orientation")
    class AxisOrientation {

        @Test
        @DisplayName("X-axis portal extends along X dimension")
        void getInteriorPositions_xAxis_extendsAlongX() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.X,
                4,
                5
            );

            List<BlockPos> interior = frame.getInteriorPositions();

            // All interior blocks should have same Z
            int expectedZ = 0;
            assertTrue(interior.stream().allMatch(pos -> pos.getZ() == expectedZ));

            // X values should vary
            long uniqueX = interior.stream().map(BlockPos::getX).distinct().count();
            assertTrue(uniqueX > 1 || frame.getWidth() == 3);
        }

        @Test
        @DisplayName("Z-axis portal extends along Z dimension")
        void getInteriorPositions_zAxis_extendsAlongZ() {
            PortalFrame frame = new PortalFrame(
                new BlockPos(0, 0, 0),
                Direction.Axis.Z,
                4,
                5
            );

            List<BlockPos> interior = frame.getInteriorPositions();

            // All interior blocks should have same X
            int expectedX = 0;
            assertTrue(interior.stream().allMatch(pos -> pos.getX() == expectedX));

            // Z values should vary
            long uniqueZ = interior.stream().map(BlockPos::getZ).distinct().count();
            assertTrue(uniqueZ > 1 || frame.getWidth() == 3);
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @ParameterizedTest
        @CsvSource({
            "0, 5",
            "-1, 5",
            "4, 0",
            "4, -1",
            "2, 5",  // width too small
            "4, 2"   // height too small
        })
        @DisplayName("Invalid dimensions throw IllegalArgumentException")
        void constructor_invalidDimensions_throws(int width, int height) {
            assertThrows(IllegalArgumentException.class, () ->
                new PortalFrame(new BlockPos(0, 0, 0), Direction.Axis.X, width, height)
            );
        }

        @Test
        @DisplayName("Null corner throws NullPointerException")
        void constructor_nullCorner_throws() {
            assertThrows(NullPointerException.class, () ->
                new PortalFrame(null, Direction.Axis.X, 4, 5)
            );
        }

        @Test
        @DisplayName("Null axis throws NullPointerException")
        void constructor_nullAxis_throws() {
            assertThrows(NullPointerException.class, () ->
                new PortalFrame(new BlockPos(0, 0, 0), null, 4, 5)
            );
        }
    }
}
```

---

### Test Class 4: WorldGenTypeTest

**Target:** `src/main/java/com/wickedsik/personalworlds/dimension/WorldGenType.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/dimension/WorldGenTypeTest.java`

#### Test Cases

| Test Method | Description | Input | Expected |
|-------------|-------------|-------|----------|
| `fromString_void_returnsVoid` | Exact match | `"VOID"` | `WorldGenType.VOID` |
| `fromString_lowercase_returnsCorrect` | Case insensitive | `"void"` | `WorldGenType.VOID` |
| `fromString_mixedCase_returnsCorrect` | Mixed case | `"VoId"` | `WorldGenType.VOID` |
| `fromString_overworld_returnsOverworld` | Overworld type | `"OVERWORLD"` | `WorldGenType.OVERWORLD` |
| `fromString_flat_returnsFlat` | Flat type | `"FLAT"` | `WorldGenType.FLAT` |
| `fromString_invalid_returnsDefault` | Unknown string | `"unknown"` | `WorldGenType.VOID` |
| `fromString_null_returnsDefault` | Null input | `null` | `WorldGenType.VOID` |
| `fromString_empty_returnsDefault` | Empty string | `""` | `WorldGenType.VOID` |
| `values_containsAllTypes` | Enum completeness | - | 3 values |

#### Implementation

```java
package com.wickedsik.personalworlds.dimension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class WorldGenTypeTest {

    @Test
    @DisplayName("VOID type exists")
    void voidType_exists() {
        assertNotNull(WorldGenType.VOID);
    }

    @Test
    @DisplayName("OVERWORLD type exists")
    void overworldType_exists() {
        assertNotNull(WorldGenType.OVERWORLD);
    }

    @Test
    @DisplayName("FLAT type exists")
    void flatType_exists() {
        assertNotNull(WorldGenType.FLAT);
    }

    @Test
    @DisplayName("Enum contains exactly 3 values")
    void values_containsAllTypes() {
        assertEquals(3, WorldGenType.values().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VOID", "void", "Void", "VoId", "VOID "})
    @DisplayName("fromString handles VOID variants")
    void fromString_voidVariants_returnsVoid(String input) {
        assertEquals(WorldGenType.VOID, WorldGenType.fromString(input.trim()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OVERWORLD", "overworld", "Overworld"})
    @DisplayName("fromString handles OVERWORLD variants")
    void fromString_overworldVariants_returnsOverworld(String input) {
        assertEquals(WorldGenType.OVERWORLD, WorldGenType.fromString(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FLAT", "flat", "Flat"})
    @DisplayName("fromString handles FLAT variants")
    void fromString_flatVariants_returnsFlat(String input) {
        assertEquals(WorldGenType.FLAT, WorldGenType.fromString(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("fromString returns default for null or empty")
    void fromString_nullOrEmpty_returnsDefault(String input) {
        assertEquals(WorldGenType.VOID, WorldGenType.fromString(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "invalid", "SUPERFLAT", "normal", "  "})
    @DisplayName("fromString returns default for invalid values")
    void fromString_invalid_returnsDefault(String input) {
        assertEquals(WorldGenType.VOID, WorldGenType.fromString(input));
    }

    @Test
    @DisplayName("Each type has valid name")
    void allTypes_haveValidNames() {
        for (WorldGenType type : WorldGenType.values()) {
            assertNotNull(type.name());
            assertFalse(type.name().isEmpty());
        }
    }

    @Test
    @DisplayName("toString returns name")
    void toString_returnsName() {
        assertEquals("VOID", WorldGenType.VOID.toString());
        assertEquals("OVERWORLD", WorldGenType.OVERWORLD.toString());
        assertEquals("FLAT", WorldGenType.FLAT.toString());
    }
}
```

---

## Phase 2: Data Record Tests

### Test Class 5: PlayerDimensionDataTest

**Target:** `src/main/java/com/wickedsik/personalworlds/dimension/PlayerDimensionData.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/dimension/PlayerDimensionDataTest.java`

#### Test Cases

| Test Method | Description | Scenario | Expected |
|-------------|-------------|----------|----------|
| `constructor_allFields_createsRecord` | Valid construction | All valid fields | Record created |
| `toNbt_validData_createsCompound` | NBT serialization | Valid record | NbtCompound with all fields |
| `fromNbt_validCompound_createsRecord` | NBT deserialization | Valid NBT | Matching record |
| `roundTrip_preservesAllFields` | Serialize/deserialize | Any valid data | Identical record |
| `fromNbt_missingField_returnsNull` | Missing required field | Incomplete NBT | `null` |
| `immutability_cannotModifyFields` | Record immutability | After creation | Fields unchanged |
| `equals_sameValues_areEqual` | Equality check | Identical values | `true` |
| `equals_differentUuid_areNotEqual` | Equality check | Different UUID | `false` |

#### Implementation

```java
package com.wickedsik.personalworlds.dimension;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDimensionDataTest {

    private UUID testUuid;
    private String testName;
    private Identifier testDimensionId;
    private long testCreatedAt;
    private BlockPos testSpawnPoint;
    private WorldGenType testGenType;

    @BeforeEach
    void setUp() {
        testUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testName = "TestPlayer";
        testDimensionId = new Identifier("personalworlds", "pw_test");
        testCreatedAt = System.currentTimeMillis();
        testSpawnPoint = new BlockPos(0, 65, 0);
        testGenType = WorldGenType.VOID;
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            assertNotNull(data);
            assertEquals(testUuid, data.ownerUuid());
            assertEquals(testName, data.ownerName());
            assertEquals(testDimensionId, data.dimensionId());
            assertEquals(testCreatedAt, data.createdAt());
            assertEquals(testSpawnPoint, data.spawnPoint());
            assertEquals(testGenType, data.generatorType());
        }

        @Test
        @DisplayName("Record is immutable")
        void immutability_fieldsCannotChange() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            // Records are inherently immutable - this test documents the expectation
            assertEquals(testName, data.ownerName());
            // No setter exists - compile-time guarantee
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            PlayerDimensionData data = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.containsUuid("ownerUuid"));
            assertTrue(nbt.contains("ownerName"));
            assertTrue(nbt.contains("dimensionId"));
            assertTrue(nbt.contains("createdAt"));
            assertTrue(nbt.contains("spawnX"));
            assertTrue(nbt.contains("spawnY"));
            assertTrue(nbt.contains("spawnZ"));
            assertTrue(nbt.contains("generatorType"));
        }

        @Test
        @DisplayName("fromNbt with valid compound creates record")
        void fromNbt_validCompound_createsRecord() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("ownerUuid", testUuid);
            nbt.putString("ownerName", testName);
            nbt.putString("dimensionId", testDimensionId.toString());
            nbt.putLong("createdAt", testCreatedAt);
            nbt.putInt("spawnX", testSpawnPoint.getX());
            nbt.putInt("spawnY", testSpawnPoint.getY());
            nbt.putInt("spawnZ", testSpawnPoint.getZ());
            nbt.putString("generatorType", testGenType.name());

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(testUuid, data.ownerUuid());
            assertEquals(testName, data.ownerName());
            assertEquals(testDimensionId, data.dimensionId());
            assertEquals(testCreatedAt, data.createdAt());
            assertEquals(testSpawnPoint, data.spawnPoint());
            assertEquals(testGenType, data.generatorType());
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            PlayerDimensionData original = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            NbtCompound nbt = original.toNbt();
            PlayerDimensionData restored = PlayerDimensionData.fromNbt(nbt);

            assertEquals(original, restored);
        }

        @Test
        @DisplayName("fromNbt with missing required field returns null")
        void fromNbt_missingField_returnsNull() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("ownerUuid", testUuid);
            // Missing ownerName and other required fields

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertNull(data);
        }

        @Test
        @DisplayName("fromNbt handles unknown generator type gracefully")
        void fromNbt_unknownGenType_usesDefault() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("ownerUuid", testUuid);
            nbt.putString("ownerName", testName);
            nbt.putString("dimensionId", testDimensionId.toString());
            nbt.putLong("createdAt", testCreatedAt);
            nbt.putInt("spawnX", 0);
            nbt.putInt("spawnY", 65);
            nbt.putInt("spawnZ", 0);
            nbt.putString("generatorType", "INVALID_TYPE");

            PlayerDimensionData data = PlayerDimensionData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(WorldGenType.VOID, data.generatorType()); // Fallback to default
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_areEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );
            PlayerDimensionData data2 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different UUID means not equal")
        void equals_differentUuid_notEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );
            PlayerDimensionData data2 = new PlayerDimensionData(
                UUID.randomUUID(), testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different spawn point means not equal")
        void equals_differentSpawn_notEqual() {
            PlayerDimensionData data1 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, testSpawnPoint, testGenType
            );
            PlayerDimensionData data2 = new PlayerDimensionData(
                testUuid, testName, testDimensionId, testCreatedAt, new BlockPos(100, 65, 100), testGenType
            );

            assertNotEquals(data1, data2);
        }
    }
}
```

---

### Test Class 6: ReturnDataTest

**Target:** `src/main/java/com/wickedsik/personalworlds/player/ReturnData.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/player/ReturnDataTest.java`

#### Implementation

```java
package com.wickedsik.personalworlds.player;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReturnDataTest {

    private RegistryKey<World> testDimension;
    private BlockPos testPosition;
    private float testYaw;
    private float testPitch;

    @BeforeEach
    void setUp() {
        testDimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "overworld"));
        testPosition = new BlockPos(100, 65, -200);
        testYaw = 90.0f;
        testPitch = -15.0f;
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            assertNotNull(data);
            assertEquals(testDimension, data.dimension());
            assertEquals(testPosition, data.position());
            assertEquals(testYaw, data.yaw());
            assertEquals(testPitch, data.pitch());
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            ReturnData data = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.contains("dimension"));
            assertTrue(nbt.contains("x"));
            assertTrue(nbt.contains("y"));
            assertTrue(nbt.contains("z"));
            assertTrue(nbt.contains("yaw"));
            assertTrue(nbt.contains("pitch"));
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            ReturnData original = new ReturnData(testDimension, testPosition, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(original.dimension().getValue(), restored.dimension().getValue());
            assertEquals(original.position(), restored.position());
            assertEquals(original.yaw(), restored.yaw(), 0.001f);
            assertEquals(original.pitch(), restored.pitch(), 0.001f);
        }

        @Test
        @DisplayName("fromNbt with missing dimension returns null")
        void fromNbt_missingDimension_returnsNull() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("x", 100);
            nbt.putInt("y", 65);
            nbt.putInt("z", -200);

            ReturnData data = ReturnData.fromNbt(nbt);

            assertNull(data);
        }

        @Test
        @DisplayName("Negative coordinates serialize correctly")
        void roundTrip_negativeCoordinates() {
            BlockPos negative = new BlockPos(-500, -60, -1000);
            ReturnData original = new ReturnData(testDimension, negative, testYaw, testPitch);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(negative, restored.position());
        }

        @Test
        @DisplayName("Extreme yaw/pitch values serialize correctly")
        void roundTrip_extremeRotation() {
            ReturnData original = new ReturnData(testDimension, testPosition, 359.9f, -89.9f);

            NbtCompound nbt = original.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals(359.9f, restored.yaw(), 0.001f);
            assertEquals(-89.9f, restored.pitch(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Dimension Keys")
    class DimensionKeys {

        @Test
        @DisplayName("Overworld dimension key serializes correctly")
        void overworldDimension_serializesCorrectly() {
            RegistryKey<World> overworld = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "overworld"));
            ReturnData data = new ReturnData(overworld, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:overworld", nbt.getString("dimension"));
        }

        @Test
        @DisplayName("Nether dimension key serializes correctly")
        void netherDimension_serializesCorrectly() {
            RegistryKey<World> nether = RegistryKey.of(RegistryKeys.WORLD, new Identifier("minecraft", "the_nether"));
            ReturnData data = new ReturnData(nether, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();

            assertEquals("minecraft:the_nether", nbt.getString("dimension"));
        }

        @Test
        @DisplayName("Custom dimension key serializes correctly")
        void customDimension_serializesCorrectly() {
            RegistryKey<World> custom = RegistryKey.of(RegistryKeys.WORLD, new Identifier("personalworlds", "pw_test"));
            ReturnData data = new ReturnData(custom, testPosition, testYaw, testPitch);

            NbtCompound nbt = data.toNbt();
            ReturnData restored = ReturnData.fromNbt(nbt);

            assertEquals("personalworlds", restored.dimension().getValue().getNamespace());
            assertEquals("pw_test", restored.dimension().getValue().getPath());
        }
    }
}
```

---

### Test Class 7: InvitationDataTest

**Target:** `src/main/java/com/wickedsik/personalworlds/player/InvitationData.java`

**Test File:** `src/test/java/com/wickedsik/personalworlds/player/InvitationDataTest.java`

#### Implementation

```java
package com.wickedsik.personalworlds.player;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvitationDataTest {

    private UUID testOwnerUuid;
    private String testOwnerName;
    private long testInvitedAt;

    @BeforeEach
    void setUp() {
        testOwnerUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testOwnerName = "DimensionOwner";
        testInvitedAt = System.currentTimeMillis();
    }

    @Nested
    @DisplayName("Record Construction")
    class Construction {

        @Test
        @DisplayName("Constructor with all fields creates valid record")
        void constructor_allFields_createsRecord() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            assertNotNull(data);
            assertEquals(testOwnerUuid, data.ownerUuid());
            assertEquals(testOwnerName, data.ownerName());
            assertEquals(testInvitedAt, data.invitedAt());
        }

        @Test
        @DisplayName("Record is immutable")
        void immutability_verified() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            // Values cannot change after construction (records are immutable)
            assertEquals(testOwnerName, data.ownerName());
        }
    }

    @Nested
    @DisplayName("NBT Serialization")
    class NbtSerialization {

        @Test
        @DisplayName("toNbt creates compound with all fields")
        void toNbt_validData_createsCompound() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            NbtCompound nbt = data.toNbt();

            assertNotNull(nbt);
            assertTrue(nbt.containsUuid("ownerUuid"));
            assertTrue(nbt.contains("ownerName"));
            assertTrue(nbt.contains("invitedAt"));
        }

        @Test
        @DisplayName("fromNbt with valid compound creates record")
        void fromNbt_validCompound_createsRecord() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("ownerUuid", testOwnerUuid);
            nbt.putString("ownerName", testOwnerName);
            nbt.putLong("invitedAt", testInvitedAt);

            InvitationData data = InvitationData.fromNbt(nbt);

            assertNotNull(data);
            assertEquals(testOwnerUuid, data.ownerUuid());
            assertEquals(testOwnerName, data.ownerName());
            assertEquals(testInvitedAt, data.invitedAt());
        }

        @Test
        @DisplayName("Round-trip preserves all fields")
        void roundTrip_preservesAllFields() {
            InvitationData original = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(original, restored);
        }

        @Test
        @DisplayName("fromNbt with missing ownerUuid returns null")
        void fromNbt_missingUuid_returnsNull() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("ownerName", testOwnerName);
            nbt.putLong("invitedAt", testInvitedAt);

            InvitationData data = InvitationData.fromNbt(nbt);

            assertNull(data);
        }

        @Test
        @DisplayName("fromNbt with missing ownerName returns null")
        void fromNbt_missingName_returnsNull() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("ownerUuid", testOwnerUuid);
            nbt.putLong("invitedAt", testInvitedAt);

            InvitationData data = InvitationData.fromNbt(nbt);

            assertNull(data);
        }
    }

    @Nested
    @DisplayName("Timestamp Handling")
    class TimestampHandling {

        @Test
        @DisplayName("Zero timestamp is valid")
        void zeroTimestamp_isValid() {
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, 0L);

            assertEquals(0L, data.invitedAt());
        }

        @Test
        @DisplayName("Future timestamp is valid")
        void futureTimestamp_isValid() {
            long futureTime = System.currentTimeMillis() + 86400000; // +1 day
            InvitationData data = new InvitationData(testOwnerUuid, testOwnerName, futureTime);

            assertEquals(futureTime, data.invitedAt());
        }

        @Test
        @DisplayName("Timestamp round-trip preserves precision")
        void timestampPrecision_preserved() {
            long preciseTime = 1704067200123L; // Specific millisecond
            InvitationData original = new InvitationData(testOwnerUuid, testOwnerName, preciseTime);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(preciseTime, restored.invitedAt());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Same values are equal")
        void equals_sameValues_areEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);

            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different owner UUID means not equal")
        void equals_differentUuid_notEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(UUID.randomUUID(), testOwnerName, testInvitedAt);

            assertNotEquals(data1, data2);
        }

        @Test
        @DisplayName("Different timestamp means not equal")
        void equals_differentTimestamp_notEqual() {
            InvitationData data1 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt);
            InvitationData data2 = new InvitationData(testOwnerUuid, testOwnerName, testInvitedAt + 1000);

            assertNotEquals(data1, data2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Unicode owner name serializes correctly")
        void unicodeName_serializesCorrectly() {
            String unicodeName = "プレイヤー123";
            InvitationData original = new InvitationData(testOwnerUuid, unicodeName, testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals(unicodeName, restored.ownerName());
        }

        @Test
        @DisplayName("Empty owner name serializes correctly")
        void emptyName_serializesCorrectly() {
            InvitationData original = new InvitationData(testOwnerUuid, "", testInvitedAt);

            NbtCompound nbt = original.toNbt();
            InvitationData restored = InvitationData.fromNbt(nbt);

            assertEquals("", restored.ownerName());
        }
    }
}
```

---

## Implementation Order

| Priority | Test Class | Target Class | Complexity |
|----------|------------|--------------|------------|
| 1 | `DataValidatorTest` | `DataValidator` | Low |
| 2 | `WorldGenTypeTest` | `WorldGenType` | Low |
| 3 | `ConcurrentPortalGuardTest` | `ConcurrentPortalGuard` | Medium |
| 4 | `PortalFrameTest` | `PortalFrame` | Medium |
| 5 | `InvitationDataTest` | `InvitationData` | Low |
| 6 | `ReturnDataTest` | `ReturnData` | Medium |
| 7 | `PlayerDimensionDataTest` | `PlayerDimensionData` | Medium |

---

## Required Modifications to Production Code

### 1. ConcurrentPortalGuard.java

Add test-friendly methods:

```java
/**
 * Expose hash function for testing.
 * Package-private visibility for test access.
 */
static long hashPosition(BlockPos pos) {
    return ((long) pos.getX() & 0x3FFFFFFL) << 38 |
           ((long) pos.getY() & 0xFFFFL) << 20 |
           ((long) pos.getZ() & 0xFFFFFFL);
}

/**
 * Clear all locks. For testing only.
 */
static void clearAllForTesting() {
    playersInTransit.clear();
    portalsProcessing.clear();
}
```

### 2. PortalFrame.java

Add validation in constructor:

```java
public PortalFrame(BlockPos corner, Direction.Axis axis, int width, int height) {
    Objects.requireNonNull(corner, "corner cannot be null");
    Objects.requireNonNull(axis, "axis cannot be null");
    if (width < 3) throw new IllegalArgumentException("width must be at least 3");
    if (height < 3) throw new IllegalArgumentException("height must be at least 3");

    this.corner = corner;
    this.axis = axis;
    this.width = width;
    this.height = height;
}
```

### 3. WorldGenType.java

Ensure fromString handles edge cases:

```java
public static WorldGenType fromString(String value) {
    if (value == null || value.trim().isEmpty()) {
        return VOID; // Default
    }
    try {
        return valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
        return VOID; // Fallback for unknown values
    }
}
```

---

## Testing Checklist

### Phase 1: Pure Logic Tests

- [ ] `DataValidatorTest` - All test cases pass
  - [ ] UUID validation (valid, null, empty, malformed)
  - [ ] BlockPos sanitization (bounds, null handling)
  - [ ] Player name sanitization (length, special chars)
- [ ] `ConcurrentPortalGuardTest` - All test cases pass
  - [ ] Lock acquisition logic
  - [ ] Cooldown behavior
  - [ ] Position hashing
  - [ ] Cleanup operations
- [ ] `PortalFrameTest` - All test cases pass
  - [ ] Interior position calculation
  - [ ] Frame position calculation
  - [ ] Center calculation
  - [ ] Axis orientation
  - [ ] Constructor validation
- [ ] `WorldGenTypeTest` - All test cases pass
  - [ ] Enum parsing (case insensitive)
  - [ ] Fallback behavior
  - [ ] All types present

### Phase 2: Data Record Tests

- [ ] `PlayerDimensionDataTest` - All test cases pass
  - [ ] NBT serialization round-trip
  - [ ] Missing field handling
  - [ ] Equality checks
- [ ] `ReturnDataTest` - All test cases pass
  - [ ] NBT serialization round-trip
  - [ ] Dimension key handling
  - [ ] Coordinate precision
- [ ] `InvitationDataTest` - All test cases pass
  - [ ] NBT serialization round-trip
  - [ ] Timestamp precision
  - [ ] Unicode name handling

---

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.wickedsik.personalworlds.util.DataValidatorTest"

# Run with verbose output
./gradlew test --info

# Generate test report
./gradlew test jacocoTestReport
```

---

## File Summary

| File | Action | Phase |
|------|--------|-------|
| `build.gradle` | MODIFY (add test deps) | Setup |
| `src/test/java/.../util/DataValidatorTest.java` | CREATE | 1 |
| `src/test/java/.../portal/ConcurrentPortalGuardTest.java` | CREATE | 1 |
| `src/test/java/.../portal/PortalFrameTest.java` | CREATE | 1 |
| `src/test/java/.../dimension/WorldGenTypeTest.java` | CREATE | 1 |
| `src/test/java/.../dimension/PlayerDimensionDataTest.java` | CREATE | 2 |
| `src/test/java/.../player/ReturnDataTest.java` | CREATE | 2 |
| `src/test/java/.../player/InvitationDataTest.java` | CREATE | 2 |
| `ConcurrentPortalGuard.java` | MODIFY (test methods) | 1 |
| `PortalFrame.java` | MODIFY (validation) | 1 |
| `WorldGenType.java` | MODIFY (edge cases) | 1 |

---

## Notes

### Minecraft Dependencies in Tests

Phase 1 and 2 tests use Minecraft classes (`BlockPos`, `NbtCompound`, `Identifier`, etc.) but do **not** require a running Minecraft server. These classes are available in the test classpath through the Fabric Loom gradle plugin.

If tests fail to find Minecraft classes, ensure:
1. `./gradlew genSources` has been run
2. Test source set is properly configured in `build.gradle`

### NBT Testing Considerations

NBT classes (`NbtCompound`) are Minecraft classes that can be instantiated and used without a server. However, they do not support all Java serialization patterns. Tests should focus on:
- Field presence (`contains()`, `containsUuid()`)
- Value correctness (`getString()`, `getInt()`, etc.)
- Round-trip integrity

### Test Isolation

Each test class uses `@BeforeEach` to reset state. For `ConcurrentPortalGuard`, the `clearAllForTesting()` method must be called to ensure clean state between tests.
