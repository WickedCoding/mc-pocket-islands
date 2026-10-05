package com.wickedsik.personalworlds.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModConfig#migrate}: the v0 → v1 step turns the chunk
 * sanitizer off once and touches nothing else.
 */
class ConfigMigrationTest {

    private static final Gson GSON = new Gson();

    // Shape of a config file written by 0.7.1-0.7.3: no configVersion key,
    // sanitizer flags written as true, plus customised unrelated values.
    private static final String PRE_VERSIONING_JSON = """
        {
          "portalTypes": [
            {
              "frameBlock": "minecraft:stone_bricks",
              "activationItem": "minecraft:flint",
              "islandLayers": ["minecraft:grass_block", "minecraft:dirt"],
              "portalColor": "green"
            }
          ],
          "maxInvitationsPerPlayer": 7,
          "enableAlwaysWelcome": true,
          "sanitizeChunksOnLoad": true,
          "sanitizeRemoveOrphanBlocks": true,
          "enableTeleportSounds": false
        }
        """;

    @Test
    @DisplayName("File without configVersion loads as version 0")
    void missingVersion_loadsAsZero() {
        ModConfig config = GSON.fromJson(PRE_VERSIONING_JSON, ModConfig.class);

        assertEquals(0, config.configVersion);
    }

    @Test
    @DisplayName("v0 migration turns both sanitizer flags off and bumps the version")
    void v0_resetsSanitizerFlags() {
        ModConfig config = GSON.fromJson(PRE_VERSIONING_JSON, ModConfig.class);

        assertTrue(ModConfig.migrate(config));

        assertFalse(config.sanitizeChunksOnLoad);
        assertFalse(config.sanitizeRemoveOrphanBlocks);
        assertEquals(ModConfig.CURRENT_CONFIG_VERSION, config.configVersion);
    }

    @Test
    @DisplayName("v0 migration leaves every other value as loaded")
    void v0_keepsOtherValues() {
        ModConfig config = GSON.fromJson(PRE_VERSIONING_JSON, ModConfig.class);
        ModConfig untouched = GSON.fromJson(PRE_VERSIONING_JSON, ModConfig.class);

        ModConfig.migrate(config);

        // Align the fields the migration is allowed to change, then compare
        // the full serialised form.
        untouched.configVersion = config.configVersion;
        untouched.sanitizeChunksOnLoad = config.sanitizeChunksOnLoad;
        untouched.sanitizeRemoveOrphanBlocks = config.sanitizeRemoveOrphanBlocks;
        assertEquals(GSON.toJson(untouched), GSON.toJson(config));

        assertEquals(7, config.maxInvitationsPerPlayer);
        assertTrue(config.enableAlwaysWelcome);
        assertFalse(config.enableTeleportSounds);
        assertEquals("minecraft:stone_bricks", config.portalTypes.get(0).frameBlock);
    }

    @Test
    @DisplayName("Current-version config keeps a manual sanitizer opt-in")
    void currentVersion_keepsManualOptIn() {
        ModConfig config = GSON.fromJson(
            "{\"configVersion\": 1, \"sanitizeChunksOnLoad\": true, \"sanitizeRemoveOrphanBlocks\": true}",
            ModConfig.class);

        assertFalse(ModConfig.migrate(config));

        assertTrue(config.sanitizeChunksOnLoad);
        assertTrue(config.sanitizeRemoveOrphanBlocks);
    }

    @Test
    @DisplayName("Fresh default config is current and has the sanitizer off")
    void createDefault_isCurrentAndOptOut() {
        ModConfig config = ModConfig.createDefault();

        assertEquals(ModConfig.CURRENT_CONFIG_VERSION, config.configVersion);
        assertFalse(config.sanitizeChunksOnLoad);
        assertFalse(config.sanitizeRemoveOrphanBlocks);
        assertFalse(ModConfig.migrate(config));
    }
}
