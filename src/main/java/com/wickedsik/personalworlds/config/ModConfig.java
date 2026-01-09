package com.wickedsik.personalworlds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration manager for PersonalWorlds.
 * Uses JSON persistence with GSON (provided by Minecraft).
 *
 * Configuration file location: config/personalworlds.json
 */
public class ModConfig {

    private static ModConfig INSTANCE;
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("personalworlds.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ==================== Portal Configuration ====================

    /** Block used for portal frames. Default: minecraft:nether_bricks */
    public String frameBlock = "minecraft:nether_bricks";

    /** Item used to activate portal frames. Default: minecraft:emerald */
    public String activationItem = "minecraft:emerald";

    /** Whether the activation item is consumed on use. Default: false */
    public boolean consumeActivationItem = false;

    // ==================== World Generation ====================

    /** Default world generation type for new dimensions. Options: VOID, OVERWORLD, FLAT */
    public String defaultWorldType = "VOID";

    /** Allow players to choose their world type (not yet implemented). Default: false */
    public boolean allowPlayerWorldTypeChoice = false;

    // ==================== Invitations ====================

    /** Maximum number of invitations per player. -1 for unlimited. Default: 20 */
    public int maxInvitationsPerPlayer = 20;

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

    // ==================== Messages (Customizable) ====================

    public String messageInviteSent = "Invited %player% to your dimension";
    public String messageInviteReceived = "%player% invited you to their dimension";
    public String messageRevoked = "Revoked %player%'s invitation";
    public String messageEjected = "Your invitation was revoked. Returning to overworld.";

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
        // Validate world type
        if (!defaultWorldType.equals("VOID") &&
            !defaultWorldType.equals("OVERWORLD") &&
            !defaultWorldType.equals("FLAT")) {
            PersonalWorldsMod.LOGGER.warn("Invalid defaultWorldType '{}', using VOID", defaultWorldType);
            defaultWorldType = "VOID";
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

        // Validate block/item IDs (basic format check)
        if (!frameBlock.contains(":")) {
            PersonalWorldsMod.LOGGER.warn("Invalid frameBlock '{}', using minecraft:nether_bricks", frameBlock);
            frameBlock = "minecraft:nether_bricks";
        }

        if (!activationItem.contains(":")) {
            PersonalWorldsMod.LOGGER.warn("Invalid activationItem '{}', using minecraft:emerald", activationItem);
            activationItem = "minecraft:emerald";
        }
    }

    /**
     * Get the configuration file path (for admin commands).
     */
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
