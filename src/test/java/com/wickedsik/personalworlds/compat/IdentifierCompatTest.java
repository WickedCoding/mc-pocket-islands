package com.wickedsik.personalworlds.compat;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IdentifierCompat}.
 * Tests the compatibility layer for Identifier construction across Minecraft versions.
 *
 * Note: These tests work without Bootstrap.initialize() as the Identifier class
 * can be used for basic construction/parsing without full Minecraft initialization.
 */
@DisplayName("IdentifierCompat")
class IdentifierCompatTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("creates identifier with namespace and path")
        void createsIdentifierWithNamespaceAndPath() {
            Identifier id = IdentifierCompat.create("minecraft", "stone");

            assertEquals("minecraft", id.getNamespace());
            assertEquals("stone", id.getPath());
            assertEquals("minecraft:stone", id.toString());
        }

        @Test
        @DisplayName("creates identifier with custom namespace")
        void createsIdentifierWithCustomNamespace() {
            Identifier id = IdentifierCompat.create("mymod", "custom_block");

            assertEquals("mymod", id.getNamespace());
            assertEquals("custom_block", id.getPath());
        }

        @ParameterizedTest
        @CsvSource({
            "minecraft, overworld",
            "minecraft, the_nether",
            "minecraft, the_end",
            "fabric, dimensions/void",
            "personalworlds, pw_12345678-1234-1234-1234-123456789abc"
        })
        @DisplayName("creates valid identifiers for various inputs")
        void createsValidIdentifiersForVariousInputs(String namespace, String path) {
            Identifier id = IdentifierCompat.create(namespace, path);

            assertNotNull(id);
            assertEquals(namespace, id.getNamespace());
            assertEquals(path, id.getPath());
        }

        @Test
        @DisplayName("creates identifier with path containing slashes")
        void createsIdentifierWithPathContainingSlashes() {
            Identifier id = IdentifierCompat.create("minecraft", "textures/block/stone");

            assertEquals("textures/block/stone", id.getPath());
        }

        @Test
        @DisplayName("creates identifier with underscores and numbers")
        void createsIdentifierWithUnderscoresAndNumbers() {
            Identifier id = IdentifierCompat.create("mod_123", "block_456");

            assertEquals("mod_123", id.getNamespace());
            assertEquals("block_456", id.getPath());
        }
    }

    @Nested
    @DisplayName("modId()")
    class ModIdTests {

        @Test
        @DisplayName("uses mod namespace")
        void usesModNamespace() {
            Identifier id = IdentifierCompat.modId("test_resource");

            assertEquals(PersonalWorldsMod.MOD_ID, id.getNamespace());
            assertEquals("test_resource", id.getPath());
        }

        @Test
        @DisplayName("creates personal_portal identifier")
        void createsPersonalPortalIdentifier() {
            Identifier id = IdentifierCompat.modId("personal_portal");

            assertEquals("personalworlds:personal_portal", id.toString());
        }

        @Test
        @DisplayName("creates void_island identifier")
        void createsVoidIslandIdentifier() {
            Identifier id = IdentifierCompat.modId("void_island");

            assertEquals("personalworlds:void_island", id.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "personal_portal",
            "void_island",
            "portal_ownership",
            "dimension_registry",
            "player_data"
        })
        @DisplayName("creates valid mod identifiers for various resources")
        void createsValidModIdentifiersForVariousResources(String path) {
            Identifier id = IdentifierCompat.modId(path);

            assertNotNull(id);
            assertEquals(PersonalWorldsMod.MOD_ID, id.getNamespace());
            assertEquals(path, id.getPath());
        }
    }

    @Nested
    @DisplayName("dimensionId()")
    class DimensionIdTests {

        @Test
        @DisplayName("creates dimension ID from UUID")
        void createsDimensionIdFromUuid() {
            UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            Identifier id = IdentifierCompat.dimensionId(uuid);

            assertEquals(PersonalWorldsMod.MOD_ID, id.getNamespace());
            assertEquals("pw_12345678-1234-1234-1234-123456789abc", id.getPath());
        }

        @Test
        @DisplayName("creates consistent dimension ID for same UUID")
        void createsConsistentDimensionIdForSameUuid() {
            UUID uuid = UUID.randomUUID();

            Identifier id1 = IdentifierCompat.dimensionId(uuid);
            Identifier id2 = IdentifierCompat.dimensionId(uuid);

            assertEquals(id1, id2);
        }

        @Test
        @DisplayName("creates unique dimension IDs for different UUIDs")
        void createsUniqueDimensionIdsForDifferentUuids() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();

            Identifier id1 = IdentifierCompat.dimensionId(uuid1);
            Identifier id2 = IdentifierCompat.dimensionId(uuid2);

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("dimension ID path starts with pw_ prefix")
        void dimensionIdPathStartsWithPwPrefix() {
            UUID uuid = UUID.randomUUID();
            Identifier id = IdentifierCompat.dimensionId(uuid);

            assertTrue(id.getPath().startsWith("pw_"));
        }

        @Test
        @DisplayName("dimension ID contains full UUID string")
        void dimensionIdContainsFullUuidString() {
            UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            Identifier id = IdentifierCompat.dimensionId(uuid);

            assertTrue(id.getPath().contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        }
    }

    @Nested
    @DisplayName("tryParse()")
    class TryParseTests {

        @Test
        @DisplayName("parses valid identifier string")
        void parsesValidIdentifierString() {
            Identifier id = IdentifierCompat.tryParse("minecraft:stone");

            assertNotNull(id);
            assertEquals("minecraft", id.getNamespace());
            assertEquals("stone", id.getPath());
        }

        @Test
        @DisplayName("parses identifier without explicit namespace")
        void parsesIdentifierWithoutExplicitNamespace() {
            Identifier id = IdentifierCompat.tryParse("stone");

            assertNotNull(id);
            assertEquals("minecraft", id.getNamespace()); // default namespace
            assertEquals("stone", id.getPath());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("returns null for null or empty input")
        void returnsNullForNullOrEmptyInput(String input) {
            Identifier id = IdentifierCompat.tryParse(input);

            assertNull(id);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:stone",
            "minecraft:diamond_block",
            "personalworlds:personal_portal",
            "fabric-api:test"
        })
        @DisplayName("parses various valid identifier strings")
        void parsesVariousValidIdentifierStrings(String input) {
            Identifier id = IdentifierCompat.tryParse(input);

            assertNotNull(id);
            assertTrue(input.contains(id.getPath()));
        }

        @Test
        @DisplayName("parses identifier with path containing slashes")
        void parsesIdentifierWithPathContainingSlashes() {
            Identifier id = IdentifierCompat.tryParse("minecraft:textures/block/stone");

            assertNotNull(id);
            assertEquals("textures/block/stone", id.getPath());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "INVALID:UPPERCASE",
            "has spaces:path",
            "namespace:has spaces"
        })
        @DisplayName("returns null for invalid identifier strings")
        void returnsNullForInvalidIdentifierStrings(String input) {
            Identifier id = IdentifierCompat.tryParse(input);

            assertNull(id);
        }
    }

    @Nested
    @DisplayName("fromNbtString()")
    class FromNbtStringTests {

        @Test
        @DisplayName("parses valid NBT identifier string")
        void parsesValidNbtIdentifierString() {
            Identifier id = IdentifierCompat.fromNbtString("minecraft:overworld");

            assertEquals("minecraft", id.getNamespace());
            assertEquals("overworld", id.getPath());
        }

        @Test
        @DisplayName("parses dimension identifier from NBT")
        void parsesDimensionIdentifierFromNbt() {
            Identifier id = IdentifierCompat.fromNbtString("personalworlds:pw_12345678-1234-1234-1234-123456789abc");

            assertEquals("personalworlds", id.getNamespace());
            assertEquals("pw_12345678-1234-1234-1234-123456789abc", id.getPath());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end",
            "personalworlds:pw_test"
        })
        @DisplayName("parses various valid NBT strings")
        void parsesVariousValidNbtStrings(String input) {
            Identifier id = IdentifierCompat.fromNbtString(input);

            assertNotNull(id);
            assertEquals(input, id.toString());
        }

        @Test
        @DisplayName("parses string without namespace using default")
        void parsesStringWithoutNamespaceUsingDefault() {
            Identifier id = IdentifierCompat.fromNbtString("overworld");

            assertEquals("minecraft", id.getNamespace());
            assertEquals("overworld", id.getPath());
        }

        @Test
        @DisplayName("throws exception for invalid NBT string")
        void throwsExceptionForInvalidNbtString() {
            assertThrows(Exception.class, () -> {
                IdentifierCompat.fromNbtString("INVALID IDENTIFIER!");
            });
        }

        @Test
        @DisplayName("throws exception for string with uppercase")
        void throwsExceptionForStringWithUppercase() {
            assertThrows(Exception.class, () -> {
                IdentifierCompat.fromNbtString("Minecraft:Stone");
            });
        }
    }

    @Nested
    @DisplayName("Integration scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("dimension ID round-trip through NBT string")
        void dimensionIdRoundTripThroughNbtString() {
            UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");

            // Create dimension ID
            Identifier original = IdentifierCompat.dimensionId(uuid);

            // Simulate saving to NBT and loading back
            String nbtString = original.toString();
            Identifier restored = IdentifierCompat.fromNbtString(nbtString);

            assertEquals(original, restored);
        }

        @Test
        @DisplayName("mod ID matches expected format")
        void modIdMatchesExpectedFormat() {
            Identifier id = IdentifierCompat.modId("personal_portal");

            // Should match what ModBlocks expects
            assertEquals("personalworlds:personal_portal", id.toString());
        }

        @Test
        @DisplayName("tryParse can recover fromNbtString output")
        void tryParseCanRecoverFromNbtStringOutput() {
            Identifier original = IdentifierCompat.create("minecraft", "diamond_ore");
            String stringForm = original.toString();

            Identifier parsed = IdentifierCompat.tryParse(stringForm);

            assertEquals(original, parsed);
        }
    }
}
