package com.wickedsik.personalworlds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.portal.PortalColor;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration manager for PersonalWorlds.
 * Uses JSON persistence with GSON (provided by Minecraft).
 *
 * Configuration file location: config/personalworlds.json
 */
public class ModConfig {

    /**
     * Portal configuration defining frame material, activation item, island composition, and portal color.
     */
    public static class PortalConfig {
        /** Block used for portal frames (e.g., "minecraft:nether_bricks") */
        public String frameBlock;

        /** Item used to activate portal frames (e.g., "minecraft:emerald") */
        public String activationItem;

        /** Island layer materials from top to bottom (max 5) */
        public String[] islandLayers;

        /** Portal block color (e.g., "red", "cyan"). See PortalColor enum for valid values. */
        public String portalColor;

        /** Default constructor for GSON */
        public PortalConfig() {
            this.frameBlock = "minecraft:nether_bricks";
            this.activationItem = "minecraft:emerald";
            this.islandLayers = new String[]{
                "minecraft:grass_block",
                "minecraft:dirt",
                "minecraft:stone"
            };
            this.portalColor = "red";
        }

        /** Full constructor for programmatic creation */
        public PortalConfig(String frameBlock, String activationItem, String[] islandLayers, String portalColor) {
            this.frameBlock = frameBlock;
            this.activationItem = activationItem;
            this.islandLayers = islandLayers;
            this.portalColor = portalColor;
        }
    }

    private static ModConfig INSTANCE;
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("personalworlds.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ==================== Portal Configuration ====================

    /**
     * Array of portal type configurations.
     * Each portal type defines frame material, activation item, island layers, and portal color.
     */
    public List<PortalConfig> portalTypes = new ArrayList<>();

    /** Whether the activation item is consumed on use. Default: false */
    public boolean consumeActivationItem = false;

    // ==================== Invitations ====================

    /** Maximum number of invitations per player. -1 for unlimited. Default: 20 */
    public int maxInvitationsPerPlayer = 20;

    /**
     * Enable "Always Welcome" feature for invitations.
     * When enabled, island owners can mark specific guests as "Always Welcome",
     * allowing them to visit even when the host is offline or away from their island.
     * Requires server restart to change.
     * Default: false (disabled)
     */
    public boolean enableAlwaysWelcome = false;

    // ==================== Visit Access Control ====================

    /**
     * Whether visitors can enter an island when the host is online but not on their own island.
     * When false: visitors can only enter when the host is physically present on their island.
     * When true: visitors can enter anytime the host is online (original behavior).
     * Note: Admins (OP level 2+) bypass this restriction.
     * Default: false (restrictive - host must be home)
     */
    public boolean allowVisitWhenHostNotHome = false;

    // ==================== Performance ====================

    /** Delay in ticks before unloading an empty dimension. Default: 600 (30 seconds) */
    public int unloadEmptyDimensionDelayTicks = 600;

    /** Cleanup check interval in ticks. Default: 600 (30 seconds) */
    public int cleanupIntervalTicks = 600;

    // ==================== Visual Effects ====================

    /** Enable particle effects during teleportation. Default: true */
    public boolean enableTeleportParticles = true;

    /** Enable sound effects during teleportation. Default: true */
    public boolean enableTeleportSounds = true;

    /** Enable portal activation visual effects. Default: true */
    public boolean enablePortalActivationEffects = true;

    /** Enable invitation notification sounds. Default: true */
    public boolean enableInvitationNotifications = true;

    // ==================== Static Access ====================

    /**
     * Get the current configuration instance.
     * Loads from disk if not already loaded.
     */
    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    /**
     * Load configuration from disk.
     * Creates default config if file doesn't exist.
     */
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, ModConfig.class);

                if (INSTANCE == null) {
                    PersonalWorldsMod.LOGGER.warn("Config file was empty, using defaults");
                    INSTANCE = new ModConfig();
                    save();
                }

                // Ensure at least one portal type exists
                if (INSTANCE.portalTypes.isEmpty()) {
                    PersonalWorldsMod.LOGGER.info("No portal types defined, adding default");
                    INSTANCE.portalTypes.add(new PortalConfig());
                    save();
                }

                PersonalWorldsMod.LOGGER.info("Configuration loaded from {}", CONFIG_PATH);
            } catch (IOException e) {
                PersonalWorldsMod.LOGGER.error("Failed to load configuration, using defaults", e);
                INSTANCE = new ModConfig();
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.error("Configuration file malformed, using defaults", e);
                INSTANCE = new ModConfig();
            }
        } else {
            PersonalWorldsMod.LOGGER.info("No configuration file found, creating default at {}", CONFIG_PATH);
            INSTANCE = new ModConfig();
            // Add default portal type
            INSTANCE.portalTypes.add(new PortalConfig());
            save();
        }

        // Validate loaded values
        INSTANCE.validate();
    }

    /**
     * Save current configuration to disk.
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(INSTANCE);
            Files.writeString(CONFIG_PATH, json);
            PersonalWorldsMod.LOGGER.debug("Configuration saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to save configuration", e);
        }
    }

    /**
     * Reload configuration from disk.
     * Used by the admin reload command.
     */
    public static void reload() {
        INSTANCE = null;
        load();
        PersonalWorldsMod.LOGGER.info("Configuration reloaded");
    }

    // ==================== Validation ====================

    /**
     * Validate configuration values and apply corrections.
     */
    private void validate() {
        // Validate portal types
        if (portalTypes.isEmpty()) {
            PersonalWorldsMod.LOGGER.warn("No portal types defined, adding default");
            portalTypes.add(new PortalConfig());
        }

        for (int i = 0; i < portalTypes.size(); i++) {
            PortalConfig portal = portalTypes.get(i);

            // Validate frame block ID format
            if (portal.frameBlock == null || !portal.frameBlock.contains(":")) {
                PersonalWorldsMod.LOGGER.warn("Portal type {} has invalid frameBlock '{}', using minecraft:nether_bricks",
                    i, portal.frameBlock);
                portal.frameBlock = "minecraft:nether_bricks";
            }

            // Validate activation item ID format
            if (portal.activationItem == null || !portal.activationItem.contains(":")) {
                PersonalWorldsMod.LOGGER.warn("Portal type {} has invalid activationItem '{}', using minecraft:emerald",
                    i, portal.activationItem);
                portal.activationItem = "minecraft:emerald";
            }

            // Validate island layers
            if (portal.islandLayers == null || portal.islandLayers.length == 0) {
                PersonalWorldsMod.LOGGER.warn("Portal type {} has no island layers, using default",  i);
                portal.islandLayers = new String[]{"minecraft:grass_block", "minecraft:dirt", "minecraft:stone"};
            }

            // Limit island layers to max 5
            if (portal.islandLayers.length > 5) {
                PersonalWorldsMod.LOGGER.warn("Portal type {} has {} island layers (max 5), truncating",
                    i, portal.islandLayers.length);
                String[] truncated = new String[5];
                System.arraycopy(portal.islandLayers, 0, truncated, 0, 5);
                portal.islandLayers = truncated;
            }

            // Validate each island layer ID format
            for (int j = 0; j < portal.islandLayers.length; j++) {
                if (portal.islandLayers[j] == null || !portal.islandLayers[j].contains(":")) {
                    PersonalWorldsMod.LOGGER.warn("Portal type {} layer {} has invalid block ID '{}', using minecraft:grass_block",
                        i, j, portal.islandLayers[j]);
                    portal.islandLayers[j] = "minecraft:grass_block";
                }
            }

            // Validate portal color (migration for old configs without portalColor)
            if (portal.portalColor == null || portal.portalColor.isEmpty()) {
                PersonalWorldsMod.LOGGER.info("Portal type {} has no color specified, using 'red'", i);
                portal.portalColor = "red";
            } else {
                // Validate color is a known enum value
                PortalColor parsedColor = PortalColor.fromString(portal.portalColor);
                if (!parsedColor.asString().equalsIgnoreCase(portal.portalColor)) {
                    PersonalWorldsMod.LOGGER.warn("Portal type {} has invalid color '{}', using '{}' instead",
                        i, portal.portalColor, parsedColor.asString());
                }
            }
        }

        // Check for duplicate frame blocks (warn only, first-match wins)
        for (int i = 0; i < portalTypes.size(); i++) {
            for (int j = i + 1; j < portalTypes.size(); j++) {
                if (portalTypes.get(i).frameBlock.equals(portalTypes.get(j).frameBlock)) {
                    PersonalWorldsMod.LOGGER.warn("Portal types {} and {} both use frame block '{}' - first match will be used",
                        i, j, portalTypes.get(i).frameBlock);
                }
            }
        }

        // Validate invitation limit
        if (maxInvitationsPerPlayer < -1) {
            PersonalWorldsMod.LOGGER.warn("Invalid maxInvitationsPerPlayer {}, using 20", maxInvitationsPerPlayer);
            maxInvitationsPerPlayer = 20;
        }

        // Validate timing values
        if (unloadEmptyDimensionDelayTicks < 0) {
            PersonalWorldsMod.LOGGER.warn("Invalid unloadEmptyDimensionDelayTicks {}, using 600", unloadEmptyDimensionDelayTicks);
            unloadEmptyDimensionDelayTicks = 600;
        }

        if (cleanupIntervalTicks < 20) {
            PersonalWorldsMod.LOGGER.warn("cleanupIntervalTicks {} too low, using minimum 20", cleanupIntervalTicks);
            cleanupIntervalTicks = 20;
        }
    }

    /**
     * Get the configuration file path (for admin commands).
     */
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
