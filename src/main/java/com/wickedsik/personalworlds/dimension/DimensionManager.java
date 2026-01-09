package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.generator.VoidIslandChunkGenerator;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
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

        // Register in persistent state if new, or get existing data
        DimensionRegistry registry = DimensionRegistry.get(server);
        PlayerDimensionData data;

        if (!registry.hasDimension(playerUuid)) {
            // New dimension - create and register
            data = new PlayerDimensionData(
                playerUuid,
                playerName,
                dimId,
                System.currentTimeMillis(),
                getSpawnPoint(genType),
                genType
            );
            registry.registerDimension(data);
        } else {
            // Existing dimension - get data and potentially update owner name
            data = registry.getDimensionData(playerUuid).orElseThrow();

            // Update owner name if it changed (player renamed)
            if (!data.ownerName().equals(playerName)) {
                data = new PlayerDimensionData(
                    data.ownerUuid(),
                    playerName,  // Updated name
                    data.dimensionId(),
                    data.createdAt(),
                    data.spawnPoint(),
                    data.generatorType()
                );
                registry.registerDimension(data);  // Re-register with updated name
                PersonalWorldsMod.LOGGER.info("Updated owner name for dimension: {} -> {}",
                    data.ownerUuid(), playerName);
            }
        }

        // Write backup metadata to dimension folder (for recovery purposes)
        DimensionMetadataFile.write(server, data);

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
        config.setGenerator(createChunkGenerator(server, genType));

        // For void worlds, disable mob spawning to keep the dimension pristine
        if (genType == WorldGenType.VOID) {
            config.setGameRule(GameRules.DO_MOB_SPAWNING, false);
        }

        return config;
    }

    /**
     * Create the appropriate ChunkGenerator for the given world type.
     */
    private static ChunkGenerator createChunkGenerator(MinecraftServer server, WorldGenType genType) {
        return switch (genType) {
            case VOID -> {
                // Create VoidIslandChunkGenerator with THE_VOID biome
                // Using THE_VOID prevents structure generation (no villages, etc.)
                var biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
                RegistryEntry<Biome> voidBiome = biomeRegistry.getEntry(BiomeKeys.THE_VOID)
                    .orElseThrow(() -> new IllegalStateException("The Void biome not found"));
                yield new VoidIslandChunkGenerator(new FixedBiomeSource(voidBiome));
            }
            case OVERWORLD -> server.getOverworld().getChunkManager().getChunkGenerator();
            case FLAT -> {
                // Use overworld generator for now; flat generator can be added later
                yield server.getOverworld().getChunkManager().getChunkGenerator();
            }
        };
    }

    /**
     * Get the spawn point for a given world type.
     */
    private static BlockPos getSpawnPoint(WorldGenType genType) {
        return switch (genType) {
            case VOID -> new BlockPos(0, 65, 0);  // Center of island, one block above grass
            case OVERWORLD, FLAT -> new BlockPos(0, 64, 0);
        };
    }
}
