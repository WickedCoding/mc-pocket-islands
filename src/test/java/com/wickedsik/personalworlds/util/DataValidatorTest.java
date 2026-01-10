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

/**
 * Unit tests for DataValidator.
 * Tests pure validation logic without requiring Minecraft runtime.
 */
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
        @DisplayName("Null position returns safe default (0, 64, 0)")
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
        @DisplayName("X coordinate exceeding max (30M) is clamped")
        void sanitizeBlockPos_xTooHigh_clamps() {
            BlockPos input = new BlockPos(50_000_000, 65, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(30_000_000, result.getX());
            assertEquals(65, result.getY());
            assertEquals(0, result.getZ());
        }

        @Test
        @DisplayName("X coordinate below min (-30M) is clamped")
        void sanitizeBlockPos_xTooLow_clamps() {
            BlockPos input = new BlockPos(-50_000_000, 65, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(-30_000_000, result.getX());
        }

        @Test
        @DisplayName("Y coordinate below min (-64) is clamped")
        void sanitizeBlockPos_yTooLow_clamps() {
            BlockPos input = new BlockPos(0, -100, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(-64, result.getY());
        }

        @Test
        @DisplayName("Y coordinate above max (320) is clamped")
        void sanitizeBlockPos_yTooHigh_clamps() {
            BlockPos input = new BlockPos(0, 500, 0);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(320, result.getY());
        }

        @Test
        @DisplayName("Z coordinate exceeding max is clamped")
        void sanitizeBlockPos_zTooHigh_clamps() {
            BlockPos input = new BlockPos(0, 65, 40_000_000);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(30_000_000, result.getZ());
        }

        @Test
        @DisplayName("Z coordinate below min is clamped")
        void sanitizeBlockPos_zTooLow_clamps() {
            BlockPos input = new BlockPos(0, 65, -40_000_000);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(-30_000_000, result.getZ());
        }

        @Test
        @DisplayName("Boundary values are preserved exactly")
        void sanitizeBlockPos_boundaryValues_preserved() {
            BlockPos input = new BlockPos(30_000_000, 320, -30_000_000);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(input, result);
        }

        @Test
        @DisplayName("Minimum boundary values are preserved")
        void sanitizeBlockPos_minBoundary_preserved() {
            BlockPos input = new BlockPos(-30_000_000, -64, -30_000_000);

            BlockPos result = DataValidator.sanitizeBlockPos(input);

            assertEquals(input, result);
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
            assertEquals("A".repeat(32), result);
        }

        @Test
        @DisplayName("Non-printable characters are removed")
        void sanitizePlayerName_nonPrintable_removes() {
            String withControlChars = "Steve\u0000\u0007";

            String result = DataValidator.sanitizePlayerName(withControlChars);

            assertEquals("Steve", result);
        }

        @Test
        @DisplayName("Whitespace is trimmed from ends")
        void sanitizePlayerName_whitespace_trims() {
            String result = DataValidator.sanitizePlayerName("  Steve  ");

            assertEquals("Steve", result);
        }

        @Test
        @DisplayName("Only whitespace returns empty string (not Unknown)")
        void sanitizePlayerName_onlyWhitespace_returnsEmpty() {
            // Note: The current implementation returns "" for whitespace-only
            // because isEmpty() check happens before sanitization
            String result = DataValidator.sanitizePlayerName("   ");

            assertEquals("", result);
        }

        @Test
        @DisplayName("Name with numbers and underscores preserved")
        void sanitizePlayerName_alphanumericWithUnderscore_preserved() {
            String result = DataValidator.sanitizePlayerName("Player_123");

            assertEquals("Player_123", result);
        }

        @Test
        @DisplayName("Name exactly 32 chars is not truncated")
        void sanitizePlayerName_exactly32_notTruncated() {
            String exactName = "A".repeat(32);

            String result = DataValidator.sanitizePlayerName(exactName);

            assertEquals(32, result.length());
            assertEquals(exactName, result);
        }
    }

    @Nested
    @DisplayName("BlockPos Validation")
    class BlockPosValidation {

        @Test
        @DisplayName("Null position is invalid")
        void isValidBlockPos_null_returnsFalse() {
            assertFalse(DataValidator.isValidBlockPos(null, null));
        }

        @Test
        @DisplayName("Valid position within bounds is valid")
        void isValidBlockPos_withinBounds_returnsTrue() {
            BlockPos pos = new BlockPos(100, 65, -200);

            assertTrue(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position at maximum X is valid")
        void isValidBlockPos_maxX_returnsTrue() {
            BlockPos pos = new BlockPos(30_000_000, 65, 0);

            assertTrue(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position exceeding maximum X is invalid")
        void isValidBlockPos_exceedsMaxX_returnsFalse() {
            BlockPos pos = new BlockPos(30_000_001, 65, 0);

            assertFalse(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position at minimum Y (-64) is valid")
        void isValidBlockPos_minY_returnsTrue() {
            BlockPos pos = new BlockPos(0, -64, 0);

            assertTrue(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position below minimum Y is invalid")
        void isValidBlockPos_belowMinY_returnsFalse() {
            BlockPos pos = new BlockPos(0, -65, 0);

            assertFalse(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position at maximum Y (320) is valid")
        void isValidBlockPos_maxY_returnsTrue() {
            BlockPos pos = new BlockPos(0, 320, 0);

            assertTrue(DataValidator.isValidBlockPos(pos, null));
        }

        @Test
        @DisplayName("Position above maximum Y is invalid")
        void isValidBlockPos_aboveMaxY_returnsFalse() {
            BlockPos pos = new BlockPos(0, 321, 0);

            assertFalse(DataValidator.isValidBlockPos(pos, null));
        }
    }
}
