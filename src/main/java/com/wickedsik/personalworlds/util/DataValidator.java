package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.player.InvitationData;
import com.wickedsik.personalworlds.player.ReturnData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates persistent data to prevent corruption and ensure consistency.
 */
public final class DataValidator {

    private DataValidator() {} // Utility class

    // --- UUID Validation ---

    /**
     * Validate a UUID string and convert to UUID object.
     *
     * @param uuidString The UUID string to validate
     * @return Optional containing the UUID, or empty if invalid
     */
    public static Optional<UUID> validateUuid(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            PersonalWorldsMod.LOGGER.warn("Invalid UUID string: {}", uuidString);
            return Optional.empty();
        }
    }

    /**
     * Validate a UUID is not null and has valid format.
     *
     * @param uuid The UUID to validate
     * @return true if the UUID is valid
     */
    public static boolean isValidUuid(UUID uuid) {
        return uuid != null;
    }

    // --- Identifier Validation ---

    /**
     * Validate an identifier string.
     *
     * @param namespace The namespace
     * @param path The path
     * @return Optional containing the Identifier, or empty if invalid
     */
    public static Optional<Identifier> validateIdentifier(String namespace, String path) {
        if (namespace == null || path == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(IdentifierCompat.create(namespace, path));
        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.warn("Invalid identifier: {}:{}", namespace, path);
            return Optional.empty();
        }
    }

    // --- BlockPos Validation ---

    /**
     * Validate a block position is within world bounds.
     *
     * @param pos The position to validate
     * @param server The server (unused but kept for API consistency)
     * @return true if the position is valid
     */
    public static boolean isValidBlockPos(BlockPos pos, MinecraftServer server) {
        if (pos == null) {
            return false;
        }

        // Check reasonable bounds
        int maxX = 30_000_000;
        int minY = -64;
        int maxY = 320;

        return pos.getX() >= -maxX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= -maxX && pos.getZ() <= maxX;
    }

    /**
     * Sanitize a block position to safe bounds.
     *
     * @param pos The position to sanitize
     * @return A sanitized position within world bounds
     */
    public static BlockPos sanitizeBlockPos(BlockPos pos) {
        if (pos == null) {
            return new BlockPos(0, 64, 0); // Safe default
        }

        int x = Math.max(-30_000_000, Math.min(30_000_000, pos.getX()));
        int y = Math.max(-64, Math.min(320, pos.getY()));
        int z = Math.max(-30_000_000, Math.min(30_000_000, pos.getZ()));

        return new BlockPos(x, y, z);
    }

    // --- PlayerDimensionData Validation ---

    /**
     * Validate a PlayerDimensionData record.
     *
     * @param data The data to validate
     * @return true if the data is valid
     */
    public static boolean isValidDimensionData(PlayerDimensionData data) {
        if (data == null) {
            return false;
        }

        if (!isValidUuid(data.ownerUuid())) {
            PersonalWorldsMod.LOGGER.warn("Invalid owner UUID in dimension data");
            return false;
        }

        if (data.ownerName() == null || data.ownerName().isEmpty()) {
            PersonalWorldsMod.LOGGER.warn("Empty owner name in dimension data");
            return false;
        }

        if (data.dimensionId() == null) {
            PersonalWorldsMod.LOGGER.warn("Null dimension ID in dimension data");
            return false;
        }

        if (data.spawnPoint() == null) {
            PersonalWorldsMod.LOGGER.warn("Null spawn point in dimension data");
            return false;
        }

        if (data.generatorType() == null) {
            PersonalWorldsMod.LOGGER.warn("Null generator type in dimension data");
            return false;
        }

        if (data.createdAt() < 0) {
            PersonalWorldsMod.LOGGER.warn("Invalid creation timestamp in dimension data");
            return false;
        }

        return true;
    }

    /**
     * Sanitize dimension data with defaults for invalid fields.
     *
     * @param data The data to sanitize
     * @param ownerUuid The owner UUID to use if the data's UUID is invalid
     * @return Sanitized dimension data
     */
    public static PlayerDimensionData sanitizeDimensionData(PlayerDimensionData data, UUID ownerUuid) {
        if (data == null) {
            // Create minimal valid data
            String dimPath = "pw_" + ownerUuid.toString().replace("-", "");
            return new PlayerDimensionData(
                ownerUuid,
                "Unknown (" + ownerUuid.toString().substring(0, 8) + ")",
                IdentifierCompat.modId(dimPath),
                System.currentTimeMillis(),
                new BlockPos(0, 65, 0),
                WorldGenType.VOID,
                0  // Default portal type
            );
        }

        // Sanitize individual fields
        UUID uuid = data.ownerUuid() != null ? data.ownerUuid() : ownerUuid;
        String name = (data.ownerName() != null && !data.ownerName().isEmpty())
            ? data.ownerName()
            : "Unknown";
        Identifier dimId = data.dimensionId() != null
            ? data.dimensionId()
            : IdentifierCompat.modId("pw_" + uuid.toString().replace("-", ""));
        long createdAt = data.createdAt() > 0 ? data.createdAt() : System.currentTimeMillis();
        BlockPos spawn = sanitizeBlockPos(data.spawnPoint());
        WorldGenType genType = data.generatorType() != null ? data.generatorType() : WorldGenType.VOID;
        int portalTypeIndex = data.portalTypeIndex();  // Preserve existing portal type

        return new PlayerDimensionData(uuid, name, dimId, createdAt, spawn, genType, portalTypeIndex);
    }

    // --- ReturnData Validation ---

    /**
     * Validate return data.
     *
     * @param data The data to validate
     * @param server The server for dimension validation
     * @return true if the data is valid
     */
    public static boolean isValidReturnData(ReturnData data, MinecraftServer server) {
        if (data == null) {
            return false;
        }

        if (data.dimension() == null) {
            return false;
        }

        // Check if dimension exists
        if (server.getWorld(data.dimension()) == null) {
            PersonalWorldsMod.LOGGER.debug("Return dimension {} no longer exists",
                data.dimension().getValue());
            return false;
        }

        return isValidBlockPos(data.position(), server);
    }

    // --- InvitationData Validation ---

    /**
     * Validate invitation data.
     *
     * @param data The data to validate
     * @return true if the data is valid
     */
    public static boolean isValidInvitationData(InvitationData data) {
        if (data == null) {
            return false;
        }

        if (!isValidUuid(data.ownerUuid())) {
            return false;
        }

        if (data.ownerName() == null || data.ownerName().isEmpty()) {
            return false;
        }

        return data.invitedAt() > 0;
    }

    // --- String Sanitization ---

    /**
     * Sanitize a player name (remove potentially dangerous characters).
     *
     * @param name The name to sanitize
     * @return Sanitized name
     */
    public static String sanitizePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }

        // Remove any non-printable characters and limit length
        String sanitized = name.replaceAll("[^\\p{Print}]", "");
        return sanitized.substring(0, Math.min(sanitized.length(), 32)).trim();
    }
}
