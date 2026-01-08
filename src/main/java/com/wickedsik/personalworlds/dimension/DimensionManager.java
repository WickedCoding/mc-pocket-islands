package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DimensionManager {

    private static final Map<UUID, RuntimeWorldHandle> activeHandles = new HashMap<>();

    /**
     * Get or create a player's personal dimension.
     * If the dimension doesn't exist, creates it and registers it.
     */
    public static ServerWorld getOrCreatePlayerDimension(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            WorldGenType genType
    ) {
        Fantasy fantasy = Fantasy.get(server);
        Identifier dimId = createDimensionId(playerUuid);

        // Check if already loaded
        if (activeHandles.containsKey(playerUuid)) {
            return activeHandles.get(playerUuid).asWorld();
        }

        // Create world config
        RuntimeWorldConfig config = createWorldConfig(server, genType, playerUuid);

        // Get or create the persistent world
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(dimId, config);
        activeHandles.put(playerUuid, handle);

        PersonalWorldsMod.LOGGER.info("Loaded/created dimension for player: {} ({})",
            playerName, playerUuid);

        // Register in persistent state if new
        DimensionRegistry registry = DimensionRegistry.get(server);
        if (!registry.hasDimension(playerUuid)) {
            PlayerDimensionData data = new PlayerDimensionData(
                playerUuid,
                playerName,
                dimId,
                System.currentTimeMillis(),
                new BlockPos(0, 64, 0), // Default spawn
                genType
            );
            registry.registerDimension(data);
        }

        return handle.asWorld();
    }

    /**
     * Load an existing dimension from registry data.
     * Called during server startup to restore dimensions.
     */
    public static void loadExistingDimension(MinecraftServer server, PlayerDimensionData data) {
        Fantasy fantasy = Fantasy.get(server);

        // Skip if already loaded
        if (activeHandles.containsKey(data.ownerUuid())) {
            return;
        }

        RuntimeWorldConfig config = createWorldConfig(server, data.generatorType(), data.ownerUuid());
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(data.dimensionId(), config);
        activeHandles.put(data.ownerUuid(), handle);

        PersonalWorldsMod.LOGGER.debug("Restored dimension: {}", data.dimensionId());
    }

    /**
     * Unload a dimension if it's empty.
     * Returns true if the dimension was unloaded.
     */
    public static boolean unloadIfEmpty(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        if (handle != null && handle.asWorld().getPlayers().isEmpty()) {
            handle.unload();
            activeHandles.remove(playerUuid);
            PersonalWorldsMod.LOGGER.info("Unloaded empty dimension for player: {}", playerUuid);
            return true;
        }
        return false;
    }

    /**
     * Unload all empty dimensions. Called periodically.
     */
    public static void unloadEmptyDimensions() {
        activeHandles.entrySet().removeIf(entry -> {
            RuntimeWorldHandle handle = entry.getValue();
            if (handle.asWorld().getPlayers().isEmpty()) {
                handle.unload();
                PersonalWorldsMod.LOGGER.debug("Unloaded empty dimension: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Unload all dimensions. Called on server shutdown.
     */
    public static void unloadAll() {
        for (RuntimeWorldHandle handle : activeHandles.values()) {
            handle.unload();
        }
        activeHandles.clear();
        PersonalWorldsMod.LOGGER.info("Unloaded all player dimensions");
    }

    /**
     * Check if a dimension is currently loaded.
     */
    public static boolean isDimensionLoaded(UUID playerUuid) {
        return activeHandles.containsKey(playerUuid);
    }

    /**
     * Get a loaded dimension's world, if available.
     */
    public static ServerWorld getLoadedDimension(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        return handle != null ? handle.asWorld() : null;
    }

    /**
     * Get the number of currently loaded dimensions.
     */
    public static int getLoadedDimensionCount() {
        return activeHandles.size();
    }

    // --- Private Helpers ---

    private static Identifier createDimensionId(UUID playerUuid) {
        // Format: personalworlds:pw_<uuid>
        return new Identifier(
            PersonalWorldsMod.MOD_ID,
            "pw_" + playerUuid.toString().replace("-", "")
        );
    }

    private static RuntimeWorldConfig createWorldConfig(
            MinecraftServer server,
            WorldGenType genType,
            UUID playerUuid
    ) {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
            .setDimensionType(DimensionTypes.OVERWORLD)
            .setSeed(playerUuid.hashCode())
            .setDifficulty(server.getSaveProperties().getDifficulty())
            .setShouldTickTime(true);

        // Set chunk generator based on type
        switch (genType) {
            case OVERWORLD -> config.setGenerator(
                server.getOverworld().getChunkManager().getChunkGenerator()
            );
            case FLAT -> {
                // Use overworld generator for now; flat generator added in later phase
                config.setGenerator(server.getOverworld().getChunkManager().getChunkGenerator());
            }
            case VOID -> {
                // Use overworld generator for now; void generator added in Phase 5
                config.setGenerator(server.getOverworld().getChunkManager().getChunkGenerator());
            }
        }

        return config;
    }
}
