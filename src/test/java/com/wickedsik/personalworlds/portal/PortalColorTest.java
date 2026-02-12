package com.wickedsik.personalworlds.portal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalColor enum.
 * Tests string parsing, display name formatting, texture name generation, and fallback behavior.
 */
class PortalColorTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("Enum contains exactly 16 colors")
        void values_contains16Colors() {
            assertEquals(16, PortalColor.values().length);
        }

        @Test
        @DisplayName("Values are in expected order")
        void values_inExpectedOrder() {
            PortalColor[] values = PortalColor.values();
            assertEquals(PortalColor.WHITE, values[0]);
            assertEquals(PortalColor.LIGHT_GRAY, values[1]);
            assertEquals(PortalColor.GRAY, values[2]);
            assertEquals(PortalColor.BLACK, values[3]);
            assertEquals(PortalColor.BROWN, values[4]);
            assertEquals(PortalColor.RED, values[5]);
            assertEquals(PortalColor.ORANGE, values[6]);
            assertEquals(PortalColor.YELLOW, values[7]);
            assertEquals(PortalColor.LIME, values[8]);
            assertEquals(PortalColor.GREEN, values[9]);
            assertEquals(PortalColor.CYAN, values[10]);
            assertEquals(PortalColor.LIGHT_BLUE, values[11]);
            assertEquals(PortalColor.BLUE, values[12]);
            assertEquals(PortalColor.PURPLE, values[13]);
            assertEquals(PortalColor.MAGENTA, values[14]);
            assertEquals(PortalColor.PINK, values[15]);
        }
    }

    @Nested
    @DisplayName("asString()")
    class AsString {

        @ParameterizedTest
        @CsvSource({
            "WHITE, white",
            "LIGHT_GRAY, light_gray",
            "GRAY, gray",
            "BLACK, black",
            "BROWN, brown",
            "RED, red",
            "ORANGE, orange",
            "YELLOW, yellow",
            "LIME, lime",
            "GREEN, green",
            "CYAN, cyan",
            "LIGHT_BLUE, light_blue",
            "BLUE, blue",
            "PURPLE, purple",
            "MAGENTA, magenta",
            "PINK, pink"
        })
        @DisplayName("asString returns lowercase snake_case name")
        void asString_returnsExpectedValue(String enumName, String expected) {
            PortalColor color = PortalColor.valueOf(enumName);
            assertEquals(expected, color.asString());
        }
    }

    @Nested
    @DisplayName("getDisplayName()")
    class GetDisplayName {

        @ParameterizedTest
        @CsvSource({
            "WHITE, White",
            "LIGHT_GRAY, Light Gray",
            "GRAY, Gray",
            "BLACK, Black",
            "BROWN, Brown",
            "RED, Red",
            "ORANGE, Orange",
            "YELLOW, Yellow",
            "LIME, Lime",
            "GREEN, Green",
            "CYAN, Cyan",
            "LIGHT_BLUE, Light Blue",
            "BLUE, Blue",
            "PURPLE, Purple",
            "MAGENTA, Magenta",
            "PINK, Pink"
        })
        @DisplayName("getDisplayName returns Title Case name")
        void getDisplayName_returnsTitleCase(String enumName, String expected) {
            PortalColor color = PortalColor.valueOf(enumName);
            assertEquals(expected, color.getDisplayName());
        }

        @Test
        @DisplayName("All colors produce non-empty display names")
        void getDisplayName_allNonEmpty() {
            for (PortalColor color : PortalColor.values()) {
                assertFalse(color.getDisplayName().isEmpty(),
                    color.name() + " has empty display name");
            }
        }

        @Test
        @DisplayName("Display names start with uppercase letter")
        void getDisplayName_startsWithUppercase() {
            for (PortalColor color : PortalColor.values()) {
                char first = color.getDisplayName().charAt(0);
                assertTrue(Character.isUpperCase(first),
                    color.name() + " display name starts with '" + first + "'");
            }
        }

        @Test
        @DisplayName("Display names contain no underscores")
        void getDisplayName_noUnderscores() {
            for (PortalColor color : PortalColor.values()) {
                assertFalse(color.getDisplayName().contains("_"),
                    color.name() + " display name contains underscore");
            }
        }
    }

    @Nested
    @DisplayName("getTextureName()")
    class GetTextureName {

        @ParameterizedTest
        @CsvSource({
            "RED, personal_portal_red",
            "CYAN, personal_portal_cyan",
            "LIGHT_BLUE, personal_portal_light_blue",
            "WHITE, personal_portal_white"
        })
        @DisplayName("getTextureName returns prefixed snake_case name")
        void getTextureName_returnsExpectedValue(String enumName, String expected) {
            PortalColor color = PortalColor.valueOf(enumName);
            assertEquals(expected, color.getTextureName());
        }

        @Test
        @DisplayName("All texture names start with personal_portal_ prefix")
        void getTextureName_allHavePrefix() {
            for (PortalColor color : PortalColor.values()) {
                assertTrue(color.getTextureName().startsWith("personal_portal_"),
                    color.name() + " texture name missing prefix");
            }
        }
    }

    @Nested
    @DisplayName("fromString() Parsing")
    class FromStringParsing {

        @ParameterizedTest
        @CsvSource({
            "red, RED",
            "RED, RED",
            "Red, RED",
            "cyan, CYAN",
            "CYAN, CYAN",
            "light_blue, LIGHT_BLUE",
            "LIGHT_BLUE, LIGHT_BLUE",
            "Light_Blue, LIGHT_BLUE",
            "white, WHITE",
            "pink, PINK"
        })
        @DisplayName("fromString parses valid color names (case-insensitive)")
        void fromString_validNames_returnsCorrectColor(String input, String expectedEnum) {
            PortalColor expected = PortalColor.valueOf(expectedEnum);
            assertEquals(expected, PortalColor.fromString(input));
        }

        @Test
        @DisplayName("All colors round-trip through fromString(asString())")
        void fromString_roundTrip_allColors() {
            for (PortalColor color : PortalColor.values()) {
                assertEquals(color, PortalColor.fromString(color.asString()),
                    color.name() + " failed round-trip");
            }
        }
    }

    @Nested
    @DisplayName("fromString() Fallback Behavior")
    class FromStringFallback {

        @ParameterizedTest
        @ValueSource(strings = {
            "unknown",
            "invalid",
            "rainbow",
            "dark_red",
            "transparent",
            "nether"
        })
        @DisplayName("Invalid values return RED as default")
        void fromString_invalidValues_returnsRed(String input) {
            assertEquals(PortalColor.RED, PortalColor.fromString(input));
        }

        @Test
        @DisplayName("Empty string returns RED as default")
        void fromString_emptyString_returnsRed() {
            assertEquals(PortalColor.RED, PortalColor.fromString(""));
        }

        @Test
        @DisplayName("Null returns RED as default")
        void fromString_null_returnsRed() {
            assertEquals(PortalColor.RED, PortalColor.fromString(null));
        }
    }
}
