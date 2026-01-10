package com.wickedsik.personalworlds.dimension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorldGenType enum.
 * Tests enum parsing and fallback behavior.
 */
class WorldGenTypeTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

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

        @Test
        @DisplayName("Values are in expected order")
        void values_inOrder() {
            WorldGenType[] values = WorldGenType.values();
            assertEquals(WorldGenType.VOID, values[0]);
            assertEquals(WorldGenType.OVERWORLD, values[1]);
            assertEquals(WorldGenType.FLAT, values[2]);
        }
    }

    @Nested
    @DisplayName("fromString Parsing")
    class FromStringParsing {

        @Test
        @DisplayName("VOID uppercase returns VOID")
        void fromString_voidUppercase_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString("VOID"));
        }

        @Test
        @DisplayName("void lowercase returns VOID")
        void fromString_voidLowercase_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString("void"));
        }

        @Test
        @DisplayName("Void mixed case returns VOID")
        void fromString_voidMixedCase_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString("Void"));
        }

        @Test
        @DisplayName("VoId mixed case returns VOID")
        void fromString_voidMixedCase2_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString("VoId"));
        }

        @Test
        @DisplayName("OVERWORLD uppercase returns OVERWORLD")
        void fromString_overworldUppercase_returnsOverworld() {
            assertEquals(WorldGenType.OVERWORLD, WorldGenType.fromString("OVERWORLD"));
        }

        @Test
        @DisplayName("overworld lowercase returns OVERWORLD")
        void fromString_overworldLowercase_returnsOverworld() {
            assertEquals(WorldGenType.OVERWORLD, WorldGenType.fromString("overworld"));
        }

        @Test
        @DisplayName("Overworld mixed case returns OVERWORLD")
        void fromString_overworldMixedCase_returnsOverworld() {
            assertEquals(WorldGenType.OVERWORLD, WorldGenType.fromString("Overworld"));
        }

        @Test
        @DisplayName("FLAT uppercase returns FLAT")
        void fromString_flatUppercase_returnsFlat() {
            assertEquals(WorldGenType.FLAT, WorldGenType.fromString("FLAT"));
        }

        @Test
        @DisplayName("flat lowercase returns FLAT")
        void fromString_flatLowercase_returnsFlat() {
            assertEquals(WorldGenType.FLAT, WorldGenType.fromString("flat"));
        }

        @Test
        @DisplayName("Flat mixed case returns FLAT")
        void fromString_flatMixedCase_returnsFlat() {
            assertEquals(WorldGenType.FLAT, WorldGenType.fromString("Flat"));
        }
    }

    @Nested
    @DisplayName("fromString Fallback Behavior")
    class FromStringFallback {

        @ParameterizedTest
        @ValueSource(strings = {
            "unknown",
            "invalid",
            "SUPERFLAT",
            "normal",
            "nether",
            "end",
            "custom"
        })
        @DisplayName("Invalid values return VOID as default")
        void fromString_invalidValues_returnsVoid(String input) {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString(input));
        }

        @Test
        @DisplayName("Empty string returns VOID as default")
        void fromString_emptyString_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString(""));
        }

        @Test
        @DisplayName("Whitespace string returns VOID as default")
        void fromString_whitespace_returnsVoid() {
            assertEquals(WorldGenType.VOID, WorldGenType.fromString("   "));
        }

        @Test
        @DisplayName("Null input throws NullPointerException")
        void fromString_null_throwsNpe() {
            // Note: Current implementation does not handle null gracefully.
            // This documents the actual behavior. Consider fixing in production code.
            assertThrows(NullPointerException.class, () ->
                WorldGenType.fromString(null)
            );
        }
    }

    @Nested
    @DisplayName("toString Behavior")
    class ToStringBehavior {

        @Test
        @DisplayName("VOID toString returns 'VOID'")
        void toString_void_returnsVoid() {
            assertEquals("VOID", WorldGenType.VOID.toString());
        }

        @Test
        @DisplayName("OVERWORLD toString returns 'OVERWORLD'")
        void toString_overworld_returnsOverworld() {
            assertEquals("OVERWORLD", WorldGenType.OVERWORLD.toString());
        }

        @Test
        @DisplayName("FLAT toString returns 'FLAT'")
        void toString_flat_returnsFlat() {
            assertEquals("FLAT", WorldGenType.FLAT.toString());
        }
    }

    @Nested
    @DisplayName("name() Behavior")
    class NameBehavior {

        @Test
        @DisplayName("All types have valid names")
        void allTypes_haveValidNames() {
            for (WorldGenType type : WorldGenType.values()) {
                assertNotNull(type.name());
                assertFalse(type.name().isEmpty());
            }
        }

        @Test
        @DisplayName("name() equals toString() for all types")
        void name_equalsToString() {
            for (WorldGenType type : WorldGenType.values()) {
                assertEquals(type.name(), type.toString());
            }
        }
    }

    @Nested
    @DisplayName("ordinal() Values")
    class OrdinalValues {

        @Test
        @DisplayName("VOID ordinal is 0")
        void void_ordinalIsZero() {
            assertEquals(0, WorldGenType.VOID.ordinal());
        }

        @Test
        @DisplayName("OVERWORLD ordinal is 1")
        void overworld_ordinalIsOne() {
            assertEquals(1, WorldGenType.OVERWORLD.ordinal());
        }

        @Test
        @DisplayName("FLAT ordinal is 2")
        void flat_ordinalIsTwo() {
            assertEquals(2, WorldGenType.FLAT.ordinal());
        }
    }
}
