package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.GameRulesCompat;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.RegistryCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.generator.VoidIslandChunkGenerator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.chunk.ChunkGenerator;
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
     *
     * @param server The Minecraft server
     * @param playerUuid The player's UUID
     * @param playerName The player's display name
     * @param genType The world generation type
     * @param portalTypeIndex The portal type index from ModConfig.portalTypes
     * @return The ServerWorld for the player's dimension
     */
    public static ServerLevel getOrCreatePlayerDimension(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            WorldGenType genType,
            int portalTypeIndex
    ) {
        Fantasy fantasy = Fantasy.get(server);
        ResourceLocation dimId = createDimensionId(playerUuid);

        // Check if already loaded
        if (activeHandles.containsKey(playerUuid)) {
            return activeHandles.get(playerUuid).asWorld();
        }

        // Create world config with portal type
        RuntimeWorldConfig config = createWorldConfig(server, genType, playerUuid, portalTypeIndex);

        // Get or create the persistent world
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(dimId, config);
        activeHandles.put(playerUuid, handle);

        PersonalWorldsMod.LOGGER.info("Loaded/created dimension for player: {} ({}) with portal type {}",
            playerName, playerUuid, portalTypeIndex);

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
                genType,
                portalTypeIndex
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
                    data.generatorType(),
                    data.portalTypeIndex()
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

        RuntimeWorldConfig config = createWorldConfig(
            server,
            data.generatorType(),
            data.ownerUuid(),
            data.portalTypeIndex()
        );
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(data.dimensionId(), config);
        activeHandles.put(data.ownerUuid(), handle);

        PersonalWorldsMod.LOGGER.debug("Restored dimension: {} with portal type {}",
            data.dimensionId(), data.portalTypeIndex());
    }

    /**
     * Unload a dimension if it's empty.
     * Returns true if the dimension was unloaded.
     */
    public static boolean unloadIfEmpty(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        if (handle != null && handle.asWorld().players().isEmpty()) {
            handle.unload();
            activeHandles.remove(playerUuid);
            PersonalWorldsMod.LOGGER.info("Unloaded empty dimension for player: {}", playerUuid);
            return true;
        }
        return false;
    }

    /**
     * Delete a dimension, including all stored files.
     *
     * For LOADED dimensions: Uses Fantasy's delete() method which safely handles:
     * - Ejecting any remaining players
     * - Waiting for all chunks to unload
     * - Saving world data
     * - Deleting the dimension folder from disk
     *
     * For UNLOADED dimensions: Deletes the folder directly since Fantasy has
     * no reference to it (no race condition possible).
     *
     * @param server The Minecraft server (needed for unloaded dimension deletion)
     * @param playerUuid The UUID of the dimension owner
     * @return true if deletion was initiated/completed successfully
     */
    public static boolean deleteDimension(MinecraftServer server, UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        if (handle != null) {
            // Dimension is loaded - use Fantasy's safe deletion
            // Fantasy will wait for chunks to unload, save data, then delete folder
            handle.delete();
            activeHandles.remove(playerUuid);
            PersonalWorldsMod.LOGGER.info("Queued loaded dimension for deletion: {}", playerUuid);
            return true;
        } else {
            // Dimension is NOT loaded - Fantasy has no reference to it
            // Safe to delete folder directly (no race condition)
            boolean deleted = DimensionMetadataFile.deleteDimensionFolder(server, playerUuid);
            if (deleted) {
                PersonalWorldsMod.LOGGER.info("Deleted unloaded dimension folder for: {}", playerUuid);
            } else {
                PersonalWorldsMod.LOGGER.warn("No dimension folder found to delete for: {}", playerUuid);
            }
            return deleted;
        }
    }

    /**
     * Unload all empty dimensions. Called periodically.
     */
    public static void unloadEmptyDimensions() {
        activeHandles.entrySet().removeIf(entry -> {
            RuntimeWorldHandle handle = entry.getValue();
            if (handle.asWorld().players().isEmpty()) {
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
    public static ServerLevel getLoadedDimension(UUID playerUuid) {
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

    private static ResourceLocation createDimensionId(UUID playerUuid) {
        // Format: personalworlds:pw_<uuid>
        return IdentifierCompat.modId("pw_" + playerUuid.toString().replace("-", ""));
    }

    private static RuntimeWorldConfig createWorldConfig(
            MinecraftServer server,
            WorldGenType genType,
            UUID playerUuid,
            int portalTypeIndex
    ) {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
            .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
            .setSeed(playerUuid.hashCode())
            .setDifficulty(server.getWorldData().getDifficulty())
            .setShouldTickTime(true)
            .setTimeOfDay(server.overworld().getDayTime());

        // Set chunk generator based on type and portal config
        config.setGenerator(createChunkGenerator(server, genType, portalTypeIndex));

        // Apply game rules: baseline from overworld, then config overrides
        GameRulesCompat.applyGameRules(config, server);

        return config;
    }

    /**
     * Create the appropriate ChunkGenerator for the given world type.
     *
     * @param server The Minecraft server
     * @param genType The world generation type
     * @param portalTypeIndex The portal type index (determines island materials for VOID)
     * @return The configured chunk generator
     */
    private static ChunkGenerator createChunkGenerator(
            MinecraftServer server,
            WorldGenType genType,
            int portalTypeIndex
    ) {
        return switch (genType) {
            case VOID -> {
                // Create VoidIslandChunkGenerator with THE_VOID biome
                // Using THE_VOID prevents structure generation (no villages, etc.)
                //? if >=1.21 {
                /*var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
                Holder<Biome> voidBiome = biomeRegistry.get(Biomes.THE_VOID)
                    .orElseThrow(() -> new IllegalStateException("The Void biome not found"));
                *///?} else {
                var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
                Holder<Biome> voidBiome = biomeRegistry.getHolder(Biomes.THE_VOID)
                    .orElseThrow(() -> new IllegalStateException("The Void biome not found"));
                //?}

                // Convert island layer strings to BlockStates
                BlockState[] islandLayers = convertIslandLayers(portalTypeIndex);

                yield new VoidIslandChunkGenerator(new FixedBiomeSource(voidBiome), islandLayers);
            }
            case OVERWORLD -> server.overworld().getChunkSource().getGenerator();
            case FLAT -> {
                // Use overworld generator for now; flat generator can be added later
                yield server.overworld().getChunkSource().getGenerator();
            }
        };
    }

    /**
     * Convert island layer string IDs to BlockStates.
     *
     * @param portalTypeIndex The portal type index
     * @return Array of BlockStates for island layers
     */
    private static BlockState[] convertIslandLayers(int portalTypeIndex) {
        ModConfig.PortalConfig config = ModConfig.get().portalTypes.get(portalTypeIndex);
        String[] layerIds = config.islandLayers;

        int layerCount = Math.min(layerIds.length, 5); // Max 5 layers

        if (layerCount == 0) {
            // Fallback to grass if no layers specified
            PersonalWorldsMod.LOGGER.warn("Portal type {} has no island layers, using grass",  portalTypeIndex);
            return new BlockState[] { Blocks.GRASS_BLOCK.defaultBlockState() };
        }

        BlockState[] islandLayers = new BlockState[layerCount];

        for (int i = 0; i < layerCount; i++) {
            String blockId = layerIds[i];
            ResourceLocation id = IdentifierCompat.tryParse(blockId);
            Block block = id != null ? RegistryCompat.get(BuiltInRegistries.BLOCK, id) : Blocks.AIR;

            if (block == Blocks.AIR && !blockId.equals("minecraft:air")) {
                PersonalWorldsMod.LOGGER.warn("Invalid island layer block '{}' for portal type {}, using grass_block",
                    blockId, portalTypeIndex);
                block = Blocks.GRASS_BLOCK;
            }

            islandLayers[i] = block.defaultBlockState();
        }

        return islandLayers;
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
