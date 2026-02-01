package com.wickedsik.personalworlds.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility for storing dimension metadata as JSON within each dimension's folder.
 * This provides a backup of registry data that survives if the central registry
 * is corrupted or deleted.
 *
 * Storage location: world/dimensions/personalworlds/pw_<uuid>/personalworlds_metadata.json
 */
public class DimensionMetadataFile {

    private static final String FILENAME = "personalworlds_metadata.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int METADATA_VERSION = 1;

    /**
     * JSON-serializable metadata structure.
     * Kept as a simple POJO for GSON compatibility.
     */
    private static class MetadataJson {
        String ownerUuid;
        String ownerName;
        String dimensionId;
        long createdAt;
        int spawnX;
        int spawnY;
        int spawnZ;
        String generatorType;
        Integer portalTypeIndex;  // Integer (nullable) for backward compatibility
        int metadataVersion;

        static MetadataJson fromPlayerData(PlayerDimensionData data) {
            MetadataJson json = new MetadataJson();
            json.ownerUuid = data.ownerUuid().toString();
            json.ownerName = data.ownerName();
            json.dimensionId = data.dimensionId().toString();
            json.createdAt = data.createdAt();
            json.spawnX = data.spawnPoint().getX();
            json.spawnY = data.spawnPoint().getY();
            json.spawnZ = data.spawnPoint().getZ();
            json.generatorType = data.generatorType().name();
            json.portalTypeIndex = data.portalTypeIndex();
            json.metadataVersion = METADATA_VERSION;
            return json;
        }

        PlayerDimensionData toPlayerData() {
            // Backward compatibility: default to portal type 0 if not present
            int portalType = (portalTypeIndex != null) ? portalTypeIndex : 0;

            return new PlayerDimensionData(
                UUID.fromString(ownerUuid),
                ownerName,
                IdentifierCompat.fromNbtString(dimensionId),
                createdAt,
                new BlockPos(spawnX, spawnY, spawnZ),
                WorldGenType.fromString(generatorType),
                portalType
            );
        }
    }

    /**
     * Write metadata to the dimension's folder.
     * Creates the folder if it doesn't exist.
     *
     * @param server The Minecraft server
     * @param data The player dimension data to persist
     */
    public static void write(MinecraftServer server, PlayerDimensionData data) {
        Path metadataPath = getMetadataPath(server, data.ownerUuid());
        if (metadataPath == null) {
            return;
        }

        try {
            // Ensure parent directories exist
            Files.createDirectories(metadataPath.getParent());

            MetadataJson json = MetadataJson.fromPlayerData(data);
            String content = GSON.toJson(json);
            Files.writeString(metadataPath, content);

            PersonalWorldsMod.LOGGER.debug("Wrote dimension metadata for {} to {}",
                data.ownerName(), metadataPath);
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to write dimension metadata for {}: {}",
                data.ownerUuid(), e.getMessage());
        }
    }

    /**
     * Read metadata from a dimension's folder.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return Optional containing the player data if found and valid, empty otherwise
     */
    public static Optional<PlayerDimensionData> read(MinecraftServer server, UUID playerUuid) {
        Path metadataPath = getMetadataPath(server, playerUuid);
        if (metadataPath == null || !Files.exists(metadataPath)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(metadataPath);
            MetadataJson json = GSON.fromJson(content, MetadataJson.class);

            if (json == null || json.ownerUuid == null) {
                PersonalWorldsMod.LOGGER.warn("Metadata file for {} is empty or invalid", playerUuid);
                return Optional.empty();
            }

            PlayerDimensionData data = json.toPlayerData();
            PersonalWorldsMod.LOGGER.debug("Read dimension metadata for {} from {}",
                data.ownerName(), metadataPath);
            return Optional.of(data);

        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to read dimension metadata for {}: {}",
                playerUuid, e.getMessage());
            return Optional.empty();
        } catch (JsonSyntaxException e) {
            PersonalWorldsMod.LOGGER.warn("Corrupted metadata file for {}: {}",
                playerUuid, e.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // UUID parsing or other validation failures
            PersonalWorldsMod.LOGGER.warn("Invalid data in metadata file for {}: {}",
                playerUuid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if a metadata file exists for a given player's dimension.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return true if metadata file exists
     */
    public static boolean exists(MinecraftServer server, UUID playerUuid) {
        Path metadataPath = getMetadataPath(server, playerUuid);
        return metadataPath != null && Files.exists(metadataPath);
    }

    /**
     * Delete the metadata file for a dimension.
     * Called when a dimension is permanently deleted.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     */
    public static void delete(MinecraftServer server, UUID playerUuid) {
        Path metadataPath = getMetadataPath(server, playerUuid);
        if (metadataPath == null) {
            return;
        }

        try {
            if (Files.deleteIfExists(metadataPath)) {
                PersonalWorldsMod.LOGGER.debug("Deleted dimension metadata for {}", playerUuid);
            }
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to delete dimension metadata for {}: {}",
                playerUuid, e.getMessage());
        }
    }

    /**
     * Get the path to the metadata file for a player's dimension.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return Path to the metadata file, or null if path cannot be determined
     */
    private static Path getMetadataPath(MinecraftServer server, UUID playerUuid) {
        try {
            // Get the world root directory
            Path worldRoot = server.getSavePath(WorldSavePath.ROOT);

            // Build path: world/dimensions/personalworlds/pw_<uuid>/personalworlds_metadata.json
            String folderName = "pw_" + playerUuid.toString().replace("-", "");
            return worldRoot
                .resolve("dimensions")
                .resolve(PersonalWorldsMod.MOD_ID)
                .resolve(folderName)
                .resolve(FILENAME);
        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.error("Failed to determine metadata path for {}: {}",
                playerUuid, e.getMessage());
            return null;
        }
    }

    /**
     * Get the path to a dimension's folder (without the metadata filename).
     * Used by the recovery scanner to check folder existence.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return Path to the dimension folder
     */
    public static Path getDimensionFolderPath(MinecraftServer server, UUID playerUuid) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        String folderName = "pw_" + playerUuid.toString().replace("-", "");
        return worldRoot
            .resolve("dimensions")
            .resolve(PersonalWorldsMod.MOD_ID)
            .resolve(folderName);
    }

    /**
     * Get the path to the personalworlds dimensions root folder.
     * Used by the recovery scanner to list all dimension folders.
     *
     * @param server The Minecraft server
     * @return Path to the dimensions folder (world/dimensions/personalworlds/)
     */
    public static Path getDimensionsRootPath(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        return worldRoot
            .resolve("dimensions")
            .resolve(PersonalWorldsMod.MOD_ID);
    }

    /**
     * Permanently delete a dimension's entire folder from disk.
     * This includes all world data, region files, and metadata.
     * CRITICAL: This is irreversible - all dimension data will be lost!
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return true if the folder was successfully deleted, false otherwise
     */
    public static boolean deleteDimensionFolder(MinecraftServer server, UUID playerUuid) {
        Path dimensionFolder = getDimensionFolderPath(server, playerUuid);

        if (!Files.exists(dimensionFolder)) {
            PersonalWorldsMod.LOGGER.debug("Dimension folder does not exist: {}", dimensionFolder);
            return false;
        }

        try {
            // Recursively delete the entire dimension folder
            deleteDirectoryRecursively(dimensionFolder);
            PersonalWorldsMod.LOGGER.info("Permanently deleted dimension folder for {}: {}",
                playerUuid, dimensionFolder);
            return true;
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to delete dimension folder for {}: {}",
                playerUuid, e.getMessage());
            return false;
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     * Uses reverse-ordered stream to delete children before parents.
     *
     * @param directory The directory to delete
     * @throws IOException If deletion fails
     */
    private static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        // Walk the file tree in reverse order (children before parents)
        try (var stream = Files.walk(directory)) {
            stream.sorted((a, b) -> b.compareTo(a))  // Reverse order
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        PersonalWorldsMod.LOGGER.debug("Deleted: {}", path);
                    } catch (IOException e) {
                        PersonalWorldsMod.LOGGER.warn("Failed to delete: {} - {}", path, e.getMessage());
                    }
                });
        }
    }
}
