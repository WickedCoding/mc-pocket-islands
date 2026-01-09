package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scans the filesystem for orphaned dimension folders and attempts to recover them.
 * An orphaned dimension is one that exists on disk but is not registered in DimensionRegistry.
 *
 * This can happen if:
 * - The registry file (world/data/personalworlds_registry.dat) is corrupted or deleted
 * - A server crash occurred before the registry could be saved
 * - Manual file manipulation
 *
 * Recovery priority:
 * 1. Read backup metadata from dimension folder (personalworlds_metadata.json)
 * 2. If no metadata, create minimal recovery entry with defaults
 */
public class DimensionRecoveryScanner {

    private static final String FOLDER_PREFIX = "pw_";

    /**
     * Result of a recovery scan operation.
     */
    public record ScanResult(
        int totalFoldersFound,
        int alreadyRegistered,
        int recoveredFromMetadata,
        int recoveredMinimal,
        int failedRecovery
    ) {
        public int totalRecovered() {
            return recoveredFromMetadata + recoveredMinimal;
        }

        public boolean hasRecoveries() {
            return totalRecovered() > 0;
        }
    }

    /**
     * Scan for orphaned dimensions and recover them into the registry.
     * Should be called during server startup, BEFORE restoreAllDimensions().
     *
     * @param server The Minecraft server
     * @return Scan result with counts of found/recovered dimensions
     */
    public static ScanResult scanAndRecover(MinecraftServer server) {
        Path dimensionsRoot = DimensionMetadataFile.getDimensionsRootPath(server);

        // Check if the dimensions folder exists
        if (!Files.exists(dimensionsRoot) || !Files.isDirectory(dimensionsRoot)) {
            PersonalWorldsMod.LOGGER.debug("No personalworlds dimensions folder found at {}", dimensionsRoot);
            return new ScanResult(0, 0, 0, 0, 0);
        }

        // Discover all dimension folders
        List<UUID> discoveredUuids = discoverDimensionFolders(dimensionsRoot);
        if (discoveredUuids.isEmpty()) {
            PersonalWorldsMod.LOGGER.debug("No dimension folders found in {}", dimensionsRoot);
            return new ScanResult(0, 0, 0, 0, 0);
        }

        PersonalWorldsMod.LOGGER.info("Found {} dimension folder(s) on disk, checking registry...",
            discoveredUuids.size());

        // Get the registry to check which are already registered
        DimensionRegistry registry = DimensionRegistry.get(server);

        int alreadyRegistered = 0;
        int recoveredFromMetadata = 0;
        int recoveredMinimal = 0;
        int failedRecovery = 0;

        for (UUID playerUuid : discoveredUuids) {
            if (registry.hasDimension(playerUuid)) {
                // Already registered, nothing to do
                alreadyRegistered++;
                continue;
            }

            // Orphaned dimension found - attempt recovery
            PersonalWorldsMod.LOGGER.info("Found orphaned dimension for UUID: {}", playerUuid);

            RecoveryResult result = recoverDimension(server, registry, playerUuid);
            switch (result) {
                case FROM_METADATA -> recoveredFromMetadata++;
                case MINIMAL -> recoveredMinimal++;
                case FAILED -> failedRecovery++;
            }
        }

        ScanResult scanResult = new ScanResult(
            discoveredUuids.size(),
            alreadyRegistered,
            recoveredFromMetadata,
            recoveredMinimal,
            failedRecovery
        );

        logScanResults(scanResult);
        return scanResult;
    }

    /**
     * Discover all dimension folder UUIDs in the dimensions root.
     *
     * @param dimensionsRoot Path to world/dimensions/personalworlds/
     * @return List of player UUIDs found
     */
    private static List<UUID> discoverDimensionFolders(Path dimensionsRoot) {
        List<UUID> uuids = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimensionsRoot)) {
            for (Path folder : stream) {
                if (!Files.isDirectory(folder)) {
                    continue;
                }

                String folderName = folder.getFileName().toString();
                if (!folderName.startsWith(FOLDER_PREFIX)) {
                    PersonalWorldsMod.LOGGER.debug("Skipping non-dimension folder: {}", folderName);
                    continue;
                }

                Optional<UUID> uuid = parseFolderUuid(folderName);
                if (uuid.isPresent()) {
                    uuids.add(uuid.get());
                } else {
                    PersonalWorldsMod.LOGGER.warn("Could not parse UUID from folder name: {}", folderName);
                }
            }
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to scan dimensions folder: {}", e.getMessage());
        }

        return uuids;
    }

    /**
     * Parse a UUID from a dimension folder name.
     * Folder format: pw_<32-hex-digits>
     * UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     *
     * @param folderName The folder name (e.g., "pw_550e8400e29b41d4a716446655440000")
     * @return Optional containing the parsed UUID, or empty if invalid
     */
    private static Optional<UUID> parseFolderUuid(String folderName) {
        if (!folderName.startsWith(FOLDER_PREFIX)) {
            return Optional.empty();
        }

        String hexPart = folderName.substring(FOLDER_PREFIX.length());

        // UUID hex should be exactly 32 characters
        if (hexPart.length() != 32) {
            return Optional.empty();
        }

        // Validate it's all hex characters
        if (!hexPart.matches("[0-9a-fA-F]+")) {
            return Optional.empty();
        }

        try {
            // Reconstruct UUID with dashes: 8-4-4-4-12
            String uuidString = hexPart.substring(0, 8) + "-" +
                                hexPart.substring(8, 12) + "-" +
                                hexPart.substring(12, 16) + "-" +
                                hexPart.substring(16, 20) + "-" +
                                hexPart.substring(20);
            return Optional.of(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private enum RecoveryResult {
        FROM_METADATA,
        MINIMAL,
        FAILED
    }

    /**
     * Attempt to recover a single orphaned dimension.
     *
     * @param server The Minecraft server
     * @param registry The dimension registry
     * @param playerUuid The UUID of the dimension owner
     * @return The result of the recovery attempt
     */
    private static RecoveryResult recoverDimension(
            MinecraftServer server,
            DimensionRegistry registry,
            UUID playerUuid
    ) {
        // Try to read metadata file first
        Optional<PlayerDimensionData> metadataOpt = DimensionMetadataFile.read(server, playerUuid);

        if (metadataOpt.isPresent()) {
            // Full recovery from metadata
            PlayerDimensionData data = metadataOpt.get();
            registry.registerDimension(data);
            PersonalWorldsMod.LOGGER.info("Recovered dimension for {} ({}) from metadata file",
                data.ownerName(), playerUuid);
            return RecoveryResult.FROM_METADATA;
        }

        // No metadata file - create minimal recovery entry
        PersonalWorldsMod.LOGGER.warn("No metadata file for {}, creating minimal recovery entry", playerUuid);

        try {
            PlayerDimensionData minimalData = createMinimalRecoveryData(server, playerUuid);
            registry.registerDimension(minimalData);
            PersonalWorldsMod.LOGGER.info("Created minimal recovery entry for {} (owner name unknown)",
                playerUuid);
            return RecoveryResult.MINIMAL;
        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.error("Failed to create recovery entry for {}: {}",
                playerUuid, e.getMessage());
            return RecoveryResult.FAILED;
        }
    }

    /**
     * Create a minimal recovery data entry when no metadata is available.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return A PlayerDimensionData with sensible defaults
     */
    private static PlayerDimensionData createMinimalRecoveryData(MinecraftServer server, UUID playerUuid) {
        // Try to get folder creation time as approximate creation date
        long createdAt = getApproximateCreationTime(server, playerUuid);

        // Use first 8 characters of UUID as identifier in name
        String shortUuid = playerUuid.toString().substring(0, 8);
        String unknownName = "Unknown (" + shortUuid + ")";

        // Construct the dimension ID (same format as DimensionManager)
        String dimIdPath = "pw_" + playerUuid.toString().replace("-", "");
        Identifier dimensionId = new Identifier(PersonalWorldsMod.MOD_ID, dimIdPath);

        // Default spawn point (matching void world defaults)
        BlockPos spawnPoint = new BlockPos(0, 65, 0);

        // Default to VOID as the safest assumption
        // This prevents mob spawning in case the original was VOID
        WorldGenType genType = WorldGenType.VOID;

        return new PlayerDimensionData(
            playerUuid,
            unknownName,
            dimensionId,
            createdAt,
            spawnPoint,
            genType
        );
    }

    /**
     * Get the approximate creation time of a dimension folder.
     *
     * @param server The Minecraft server
     * @param playerUuid The UUID of the dimension owner
     * @return Creation timestamp in milliseconds, or current time if unavailable
     */
    private static long getApproximateCreationTime(MinecraftServer server, UUID playerUuid) {
        Path folderPath = DimensionMetadataFile.getDimensionFolderPath(server, playerUuid);

        try {
            BasicFileAttributes attrs = Files.readAttributes(folderPath, BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (IOException e) {
            // Fall back to current time
            return System.currentTimeMillis();
        }
    }

    /**
     * Log the results of a recovery scan.
     */
    private static void logScanResults(ScanResult result) {
        if (result.totalFoldersFound() == 0) {
            return;
        }

        if (result.hasRecoveries()) {
            PersonalWorldsMod.LOGGER.info(
                "Dimension recovery complete: {} recovered ({} from metadata, {} minimal), {} already registered, {} failed",
                result.totalRecovered(),
                result.recoveredFromMetadata(),
                result.recoveredMinimal(),
                result.alreadyRegistered(),
                result.failedRecovery()
            );

            if (result.recoveredMinimal() > 0) {
                PersonalWorldsMod.LOGGER.warn(
                    "{} dimension(s) were recovered with minimal data (owner unknown, defaulted to VOID type). " +
                    "Players may need to reclaim these dimensions.",
                    result.recoveredMinimal()
                );
            }
        } else {
            PersonalWorldsMod.LOGGER.debug(
                "Dimension scan: {} folder(s) found, all {} already registered",
                result.totalFoldersFound(),
                result.alreadyRegistered()
            );
        }
    }
}
