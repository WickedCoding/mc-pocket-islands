# Phase 6: Hardening - Implementation Plan

## Overview

This plan finalizes PersonalWorlds for release by addressing edge cases, implementing comprehensive error handling, performance testing for 15 concurrent dimensions, and preparing release documentation.

**Phase 6 Requirements:**

24. Handle all edge cases from checklist
25. Performance testing with 15 dimensions
26. Documentation (README, wiki)
27. Release

---

## Architecture

```
+------------------------------------------------------------------------------+
|                          Phase 6 Components                                   |
+------------------------------------------------------------------------------+
|                                                                              |
|  Edge Case Handlers          Performance Testing        Release Preparation  |
|  +-----------------+         +-----------------+        +-----------------+  |
|  | Disconnect      |         | Load Tester     |        | README.md       |  |
|  | Handler         |         | (15 dimensions) |        | CHANGELOG.md    |  |
|  +-----------------+         +-----------------+        | Wiki pages      |  |
|  | Crash Recovery  |         | Memory Profiler |        +-----------------+  |
|  | Handler         |         +-----------------+                             |
|  +-----------------+         | Concurrent      |        Data Validation      |
|  | Concurrent      |         | Access Tests    |        +-----------------+  |
|  | Portal Guard    |         +-----------------+        | Input Sanitizer |  |
|  +-----------------+                                    | UUID Validator  |  |
|  | Return Position |         Error Boundaries           | NBT Validator   |  |
|  | Fallback        |         +-----------------+        +-----------------+  |
|  +-----------------+         | Graceful        |                             |
|                              | Degradation     |                             |
|                              | Handlers        |                             |
|                              +-----------------+                             |
|                                                                              |
+------------------------------------------------------------------------------+
```

---

## Edge Cases Checklist

From `docs/per-player-dimensions-mod-plan.md`:

| Edge Case | Current Status | Action Required |
|-----------|----------------|-----------------|
| Player disconnects while in personal dimension | Partial | Handle cleanup, preserve return data |
| Server crashes while player in personal dimension | Partial | Implement crash recovery on next login |
| Two players enter portal simultaneously | Not handled | Add synchronization guard |
| Player's return position is in deleted chunk | Partial | Implement safe spawn fallback |
| Player invited to dimension that doesn't exist yet | Handled | Verify behavior, add test |

---

## New Files to Create

### 1. CrashRecoveryHandler.java

**Path:** `src/main/java/com/wickedsik/personalworlds/recovery/CrashRecoveryHandler.java`

Handles recovery when a player logs in after a crash/disconnect while in a personal dimension.

```java
package com.wickedsik.personalworlds.recovery;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles crash recovery for players who were in personal dimensions.
 *
 * When a player disconnects or server crashes while in a personal dimension,
 * their position is saved in that dimension. On next login, they might:
 * 1. Spawn correctly in the dimension (Fantasy preserved their data)
 * 2. Need to be teleported back to their return position
 * 3. Need emergency teleport if return position is invalid
 */
public class CrashRecoveryHandler {

    /**
     * Check and handle crash recovery for a joining player.
     * Called from ServerPlayConnectionEvents.JOIN.
     *
     * @param player The player who just joined
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerUuid = player.getUuid();
        ServerWorld currentWorld = player.getServerWorld();

        // Case 1: Player logged in inside a personal dimension
        if (PortalHelper.isInPersonalDimension(currentWorld)) {
            handleLoginInPersonalDimension(player, server, currentWorld);
            return;
        }

        // Case 2: Player has orphaned return data (was in dimension, crashed, respawned elsewhere)
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        if (dataManager.hasReturnData(playerUuid)) {
            handleOrphanedReturnData(player, server, dataManager);
        }
    }

    /**
     * Handle a player who logged in while inside a personal dimension.
     * Verify they have permission to be there.
     */
    private static void handleLoginInPersonalDimension(
            ServerPlayerEntity player,
            MinecraftServer server,
            ServerWorld personalWorld
    ) {
        UUID playerUuid = player.getUuid();
        Optional<UUID> ownerOpt = PortalHelper.getDimensionOwner(personalWorld);

        if (ownerOpt.isEmpty()) {
            // Invalid personal dimension - emergency evacuation
            PersonalWorldsMod.LOGGER.warn("Player {} in invalid personal dimension, evacuating",
                player.getName().getString());
            emergencyEvacuate(player, server, "Invalid dimension detected");
            return;
        }

        UUID ownerUuid = ownerOpt.get();

        // Check permission
        if (!playerUuid.equals(ownerUuid) &&
            !InvitationManager.canVisit(server, playerUuid, ownerUuid)) {
            // No longer has permission - invitation was revoked while offline
            PersonalWorldsMod.LOGGER.info("Player {} no longer has permission to {}'s dimension, evacuating",
                player.getName().getString(), ownerUuid);

            player.sendMessage(Text.literal("Your invitation was revoked while you were offline.")
                .formatted(Formatting.GOLD), false);

            evacuateToReturnPosition(player, server);
        }

        // Otherwise: player has permission, let them stay
        PersonalWorldsMod.LOGGER.debug("Player {} logged in to personal dimension with valid permission",
            player.getName().getString());
    }

    /**
     * Handle orphaned return data - player has return data but isn't in a personal dimension.
     * This indicates they crashed/disconnected and respawned at world spawn.
     */
    private static void handleOrphanedReturnData(
            ServerPlayerEntity player,
            MinecraftServer server,
            PlayerDataManager dataManager
    ) {
        UUID playerUuid = player.getUuid();

        // Check if the return data is stale (more than 24 hours old)
        // For now, just clear it - player can re-enter their dimension via portal
        dataManager.clearReturnData(playerUuid);

        PersonalWorldsMod.LOGGER.debug("Cleared orphaned return data for player {}",
            player.getName().getString());
    }

    /**
     * Evacuate player to their stored return position, or overworld spawn as fallback.
     */
    private static void evacuateToReturnPosition(ServerPlayerEntity player, MinecraftServer server) {
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(player.getUuid());

        ServerWorld targetWorld;
        Vec3d targetPos;
        float yaw, pitch;

        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getWorld(returnData.dimension());

            if (targetWorld != null && isPositionSafe(targetWorld, returnData.position())) {
                targetPos = Vec3d.ofCenter(returnData.position());
                yaw = returnData.yaw();
                pitch = returnData.pitch();
            } else {
                // Return position invalid - use overworld spawn
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(findSafeSpawn(targetWorld));
                yaw = player.getYaw();
                pitch = player.getPitch();
            }

            dataManager.clearReturnData(player.getUuid());
        } else {
            // No return data - overworld spawn
            targetWorld = server.getOverworld();
            targetPos = Vec3d.ofCenter(findSafeSpawn(targetWorld));
            yaw = player.getYaw();
            pitch = player.getPitch();
        }

        TeleportTarget target = new TeleportTarget(targetPos, Vec3d.ZERO, yaw, pitch);
        FabricDimensions.teleport(player, targetWorld, target);

        player.sendMessage(Text.literal("Returned to the overworld."), true);
    }

    /**
     * Emergency evacuation when something is seriously wrong.
     */
    private static void emergencyEvacuate(ServerPlayerEntity player, MinecraftServer server, String reason) {
        ServerWorld overworld = server.getOverworld();
        Vec3d spawnPos = Vec3d.ofCenter(findSafeSpawn(overworld));

        TeleportTarget target = new TeleportTarget(spawnPos, Vec3d.ZERO, player.getYaw(), player.getPitch());
        FabricDimensions.teleport(player, overworld, target);

        player.sendMessage(Text.literal("Emergency teleport: " + reason)
            .formatted(Formatting.RED), false);

        // Clear any corrupt return data
        PlayerDataManager.get(server).clearReturnData(player.getUuid());
    }

    /**
     * Check if a position is safe (has solid ground below and air to breathe).
     */
    private static boolean isPositionSafe(ServerWorld world, BlockPos pos) {
        // Check if the chunk is loaded/loadable
        if (!world.isChunkLoaded(pos)) {
            // Try to load the chunk
            world.getChunk(pos);
        }

        // Check for solid ground and breathable space
        BlockPos groundPos = pos.down();
        BlockPos headPos = pos.up();

        return !world.getBlockState(groundPos).isAir() && // Ground exists
               world.getBlockState(pos).isAir() &&        // Feet space clear
               world.getBlockState(headPos).isAir();      // Head space clear
    }

    /**
     * Find a safe spawn point in the given world.
     */
    private static BlockPos findSafeSpawn(ServerWorld world) {
        BlockPos spawnPos = world.getSpawnPos();

        // Ensure spawn chunk is loaded
        world.getChunk(spawnPos);

        // Find a safe Y level
        for (int y = world.getTopY() - 1; y > world.getBottomY(); y--) {
            BlockPos check = new BlockPos(spawnPos.getX(), y, spawnPos.getZ());
            if (isPositionSafe(world, check)) {
                return check;
            }
        }

        // Fallback to spawn pos (shouldn't happen in normal worlds)
        return spawnPos;
    }
}
```

### 2. ConcurrentPortalGuard.java

**Path:** `src/main/java/com/wickedsik/personalworlds/portal/ConcurrentPortalGuard.java`

Prevents race conditions when multiple players enter a portal simultaneously.

```java
package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards against race conditions in portal teleportation.
 *
 * Scenarios handled:
 * 1. Same player triggering portal twice in quick succession
 * 2. Multiple players entering the same portal simultaneously
 * 3. Dimension creation race when multiple players enter new dimension
 */
public class ConcurrentPortalGuard {

    /**
     * Players currently in teleport transition.
     * Map: Player UUID -> Teleport start timestamp
     */
    private static final Map<UUID, Long> playersInTransit = new ConcurrentHashMap<>();

    /**
     * Portals currently being processed.
     * Map: Portal position hash -> Processing player UUID
     */
    private static final Map<Long, UUID> portalsProcessing = new ConcurrentHashMap<>();

    /**
     * Minimum time between teleports for the same player (milliseconds).
     */
    private static final long TELEPORT_COOLDOWN_MS = 1000;

    /**
     * Maximum time a teleport lock can be held (milliseconds).
     */
    private static final long MAX_LOCK_TIME_MS = 5000;

    /**
     * Attempt to acquire a teleport lock for a player.
     * Returns true if the player can proceed with teleportation.
     *
     * @param player The player attempting to teleport
     * @param portalPos The portal position
     * @return true if teleport can proceed
     */
    public static boolean tryAcquire(ServerPlayerEntity player, BlockPos portalPos) {
        UUID playerUuid = player.getUuid();
        long now = System.currentTimeMillis();
        long portalHash = hashPosition(portalPos);

        // Check if player is already in transit
        Long lastTeleport = playersInTransit.get(playerUuid);
        if (lastTeleport != null) {
            if (now - lastTeleport < TELEPORT_COOLDOWN_MS) {
                PersonalWorldsMod.LOGGER.debug("Player {} blocked: teleport cooldown active",
                    player.getName().getString());
                return false;
            }
            // Stale entry - allow override
            if (now - lastTeleport > MAX_LOCK_TIME_MS) {
                PersonalWorldsMod.LOGGER.warn("Clearing stale transit lock for player {}",
                    player.getName().getString());
            }
        }

        // Check if portal is being processed by another player
        UUID processingPlayer = portalsProcessing.get(portalHash);
        if (processingPlayer != null && !processingPlayer.equals(playerUuid)) {
            Long processingTime = playersInTransit.get(processingPlayer);
            if (processingTime != null && now - processingTime < MAX_LOCK_TIME_MS) {
                PersonalWorldsMod.LOGGER.debug("Portal at {} being processed by another player",
                    portalPos);
                return false;
            }
            // Stale entry - allow override
        }

        // Acquire locks
        playersInTransit.put(playerUuid, now);
        portalsProcessing.put(portalHash, playerUuid);

        PersonalWorldsMod.LOGGER.debug("Acquired teleport lock for player {} at portal {}",
            player.getName().getString(), portalPos);
        return true;
    }

    /**
     * Release teleport locks after teleportation completes.
     *
     * @param player The player who completed teleportation
     * @param portalPos The portal position
     */
    public static void release(ServerPlayerEntity player, BlockPos portalPos) {
        UUID playerUuid = player.getUuid();
        long portalHash = hashPosition(portalPos);

        // Release portal lock only if we own it
        portalsProcessing.remove(portalHash, playerUuid);

        // Note: We keep the player transit entry for cooldown purposes
        // It will be cleared on next successful acquisition after cooldown

        PersonalWorldsMod.LOGGER.debug("Released portal lock for player {} at {}",
            player.getName().getString(), portalPos);
    }

    /**
     * Force release all locks for a player (e.g., on disconnect).
     *
     * @param playerUuid The player's UUID
     */
    public static void forceRelease(UUID playerUuid) {
        playersInTransit.remove(playerUuid);
        portalsProcessing.values().removeIf(uuid -> uuid.equals(playerUuid));

        PersonalWorldsMod.LOGGER.debug("Force released all locks for player {}", playerUuid);
    }

    /**
     * Cleanup stale entries. Called periodically from server tick.
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        long staleThreshold = now - MAX_LOCK_TIME_MS;

        playersInTransit.entrySet().removeIf(entry -> entry.getValue() < staleThreshold);

        // Portal entries reference player UUIDs, so they're cleaned up when we remove stale players
        portalsProcessing.entrySet().removeIf(entry ->
            !playersInTransit.containsKey(entry.getValue()));
    }

    /**
     * Create a hash for a block position (for use as map key).
     */
    private static long hashPosition(BlockPos pos) {
        return ((long) pos.getX() & 0x3FFFFFFL) << 38 |
               ((long) pos.getY() & 0xFFFFL) << 20 |
               ((long) pos.getZ() & 0xFFFFFFL);
    }
}
```

### 3. SafeSpawnFinder.java

**Path:** `src/main/java/com/wickedsik/personalworlds/util/SafeSpawnFinder.java`

Robust safe spawn location finder with multiple fallback strategies.

```java
package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Finds safe spawn locations with multiple fallback strategies.
 *
 * Used when:
 * - Return position is in deleted/unloaded chunk
 * - Return position is now inside a solid block
 * - Emergency evacuation needed
 */
public class SafeSpawnFinder {

    private static final int SEARCH_RADIUS = 16;
    private static final int MAX_Y_SEARCH = 32;

    /**
     * Find a safe spawn position near the target position.
     * Implements multiple fallback strategies.
     *
     * @param world The world to search in
     * @param target The desired position
     * @return A safe spawn position
     */
    public static BlockPos findSafePosition(ServerWorld world, BlockPos target) {
        // Strategy 1: Target position is already safe
        if (isSafeSpawn(world, target)) {
            return target;
        }

        // Strategy 2: Search vertically at target X/Z
        BlockPos vertical = searchVertically(world, target);
        if (vertical != null) {
            return vertical;
        }

        // Strategy 3: Search in expanding spiral around target
        BlockPos spiral = searchSpiral(world, target);
        if (spiral != null) {
            return spiral;
        }

        // Strategy 4: Use world spawn
        BlockPos worldSpawn = findSafeNearSpawn(world);
        if (worldSpawn != null) {
            PersonalWorldsMod.LOGGER.warn("Using world spawn as fallback for {}",
                target);
            return worldSpawn;
        }

        // Strategy 5: Emergency spawn (above void)
        PersonalWorldsMod.LOGGER.error("No safe spawn found near {}, using emergency position",
            target);
        return new BlockPos(0, 100, 0);
    }

    /**
     * Check if a position is safe for spawning.
     */
    public static boolean isSafeSpawn(ServerWorld world, BlockPos pos) {
        // Must have solid ground below
        BlockState ground = world.getBlockState(pos.down());
        if (!ground.isSolidBlock(world, pos.down()) && !ground.isFullCube(world, pos.down())) {
            return false;
        }

        // Must have air at feet and head level
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        // Not in lava, water, or other hazards
        if (ground.getFluidState().isStill()) {
            return false;
        }

        return true;
    }

    /**
     * Search vertically for a safe position.
     */
    private static BlockPos searchVertically(ServerWorld world, BlockPos target) {
        // Search upward first (safer)
        for (int dy = 0; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.up(dy);
            if (check.getY() < world.getTopY() && isSafeSpawn(world, check)) {
                return check;
            }
        }

        // Search downward
        for (int dy = 1; dy <= MAX_Y_SEARCH; dy++) {
            BlockPos check = target.down(dy);
            if (check.getY() > world.getBottomY() && isSafeSpawn(world, check)) {
                return check;
            }
        }

        return null;
    }

    /**
     * Search in an expanding spiral pattern.
     */
    private static BlockPos searchSpiral(ServerWorld world, BlockPos target) {
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the outer ring of the current radius
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos horizontal = target.add(dx, 0, dz);

                    // Try using heightmap for faster search
                    int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING,
                        horizontal.getX(), horizontal.getZ());
                    BlockPos surface = new BlockPos(horizontal.getX(), surfaceY, horizontal.getZ());

                    if (isSafeSpawn(world, surface)) {
                        return surface;
                    }

                    // Fallback to vertical search at this position
                    BlockPos vertical = searchVertically(world, horizontal);
                    if (vertical != null) {
                        return vertical;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find a safe position near world spawn.
     */
    private static BlockPos findSafeNearSpawn(ServerWorld world) {
        BlockPos spawn = world.getSpawnPos();

        // Try spawn directly
        if (isSafeSpawn(world, spawn)) {
            return spawn;
        }

        // Search near spawn
        return searchSpiral(world, spawn);
    }
}
```

### 4. DataValidator.java

**Path:** `src/main/java/com/wickedsik/personalworlds/util/DataValidator.java`

Validates and sanitizes all persistent data to prevent corruption.

```java
package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.player.InvitationData;
import com.wickedsik.personalworlds.player.ReturnData;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
     */
    public static boolean isValidUuid(UUID uuid) {
        return uuid != null;
    }

    // --- Identifier Validation ---

    /**
     * Validate an identifier string.
     */
    public static Optional<Identifier> validateIdentifier(String namespace, String path) {
        if (namespace == null || path == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new Identifier(namespace, path));
        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.warn("Invalid identifier: {}:{}", namespace, path);
            return Optional.empty();
        }
    }

    // --- BlockPos Validation ---

    /**
     * Validate a block position is within world bounds.
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
     */
    public static PlayerDimensionData sanitizeDimensionData(PlayerDimensionData data, UUID ownerUuid) {
        if (data == null) {
            // Create minimal valid data
            String dimPath = "pw_" + ownerUuid.toString().replace("-", "");
            return new PlayerDimensionData(
                ownerUuid,
                "Unknown (" + ownerUuid.toString().substring(0, 8) + ")",
                new Identifier(PersonalWorldsMod.MOD_ID, dimPath),
                System.currentTimeMillis(),
                new BlockPos(0, 65, 0),
                WorldGenType.VOID
            );
        }

        // Sanitize individual fields
        UUID uuid = data.ownerUuid() != null ? data.ownerUuid() : ownerUuid;
        String name = (data.ownerName() != null && !data.ownerName().isEmpty())
            ? data.ownerName()
            : "Unknown";
        Identifier dimId = data.dimensionId() != null
            ? data.dimensionId()
            : new Identifier(PersonalWorldsMod.MOD_ID, "pw_" + uuid.toString().replace("-", ""));
        long createdAt = data.createdAt() > 0 ? data.createdAt() : System.currentTimeMillis();
        BlockPos spawn = sanitizeBlockPos(data.spawnPoint());
        WorldGenType genType = data.generatorType() != null ? data.generatorType() : WorldGenType.VOID;

        return new PlayerDimensionData(uuid, name, dimId, createdAt, spawn, genType);
    }

    // --- ReturnData Validation ---

    /**
     * Validate return data.
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
     */
    public static String sanitizePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }

        // Remove any non-printable characters and limit length
        return name.replaceAll("[^\\p{Print}]", "")
                   .substring(0, Math.min(name.length(), 32))
                   .trim();
    }
}
```

### 5. PerformanceMonitor.java

**Path:** `src/main/java/com/wickedsik/personalworlds/util/PerformanceMonitor.java`

Runtime performance monitoring for debugging and testing.

```java
package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring utilities for development and testing.
 *
 * Tracks:
 * - Dimension load times
 * - Teleportation latency
 * - Memory usage trends
 * - Active dimension count
 */
public class PerformanceMonitor {

    private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> timers = new ConcurrentHashMap<>();

    private static boolean enabled = false;

    /**
     * Enable performance monitoring (for debugging/testing).
     */
    public static void enable() {
        enabled = true;
        PersonalWorldsMod.LOGGER.info("Performance monitoring ENABLED");
    }

    /**
     * Disable performance monitoring.
     */
    public static void disable() {
        enabled = false;
        counters.clear();
        timers.clear();
        PersonalWorldsMod.LOGGER.info("Performance monitoring DISABLED");
    }

    /**
     * Start a timer for an operation.
     */
    public static void startTimer(String operation) {
        if (!enabled) return;
        timers.put(operation, System.nanoTime());
    }

    /**
     * Stop a timer and log the elapsed time.
     */
    public static long stopTimer(String operation) {
        if (!enabled) return 0;

        Long start = timers.remove(operation);
        if (start == null) return 0;

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        PersonalWorldsMod.LOGGER.info("[PERF] {}: {}ms", operation, elapsedMs);

        return elapsedMs;
    }

    /**
     * Increment a counter.
     */
    public static void increment(String counter) {
        if (!enabled) return;
        counters.computeIfAbsent(counter, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Log current status.
     */
    public static void logStatus(MinecraftServer server) {
        if (!enabled) return;

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        int loadedDimensions = DimensionManager.getLoadedDimensionCount();
        int onlinePlayers = server.getCurrentPlayerCount();

        PersonalWorldsMod.LOGGER.info("[PERF] Status: {} dims loaded, {} players, heap {}/{}MB",
            loadedDimensions, onlinePlayers, heapUsed, heapMax);

        // Log counters
        counters.forEach((name, count) ->
            PersonalWorldsMod.LOGGER.info("[PERF] Counter {}: {}", name, count.get()));
    }

    /**
     * Check if memory usage is concerning.
     */
    public static boolean isMemoryPressureHigh() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();

        double usage = (double) heapUsed / heapMax;
        return usage > 0.85; // 85% threshold
    }
}
```

---

## Files to Modify

### 6. ModEventHandlers.java

**Add crash recovery and concurrent guard integration:**

```java
// Add to register():
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
    CrashRecoveryHandler.onPlayerJoin(handler.getPlayer()));

ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
    ConcurrentPortalGuard.forceRelease(handler.getPlayer().getUuid()));

// Add periodic cleanup to onServerTick():
private static int guardCleanupCounter = 0;
private static final int GUARD_CLEANUP_INTERVAL = 200; // 10 seconds

private static void onServerTick(MinecraftServer server) {
    // ... existing unload check ...

    // Cleanup concurrent portal guard
    guardCleanupCounter++;
    if (guardCleanupCounter >= GUARD_CLEANUP_INTERVAL) {
        guardCleanupCounter = 0;
        ConcurrentPortalGuard.cleanup();
    }

    // Performance monitoring (if enabled)
    if (tickCounter == 0) { // When unload check runs
        PerformanceMonitor.logStatus(server);
    }
}
```

### 7. PortalHelper.java

**Integrate concurrent portal guard:**

```java
public static void handlePortalEntry(ServerPlayerEntity player, BlockPos portalPos) {
    // Add at the beginning:
    if (!ConcurrentPortalGuard.tryAcquire(player, portalPos)) {
        // Already processing or on cooldown
        return;
    }

    try {
        // ... existing teleportation logic ...
    } finally {
        ConcurrentPortalGuard.release(player, portalPos);
    }
}
```

**Integrate safe spawn finder for return positions:**

```java
private static void teleportToReturnPosition(ServerPlayerEntity player, MinecraftServer server) {
    // Replace existing position validation with SafeSpawnFinder:

    if (returnDataOpt.isPresent()) {
        ReturnData returnData = returnDataOpt.get();
        targetWorld = server.getWorld(returnData.dimension());

        if (targetWorld == null) {
            // Dimension deleted - use overworld
            targetWorld = server.getOverworld();
            targetPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(
                targetWorld, targetWorld.getSpawnPos()));
        } else if (!SafeSpawnFinder.isSafeSpawn(targetWorld, returnData.position())) {
            // Position no longer safe - find nearby safe spot
            BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, returnData.position());
            targetPos = Vec3d.ofCenter(safePos);
            PersonalWorldsMod.LOGGER.info("Return position unsafe, relocated player {} to {}",
                player.getName().getString(), safePos);
        } else {
            targetPos = Vec3d.ofCenter(returnData.position());
        }
        // ...
    }
}
```

### 8. DimensionRegistry.java

**Add data validation:**

```java
public void registerDimension(PlayerDimensionData data) {
    // Validate before storing
    if (!DataValidator.isValidDimensionData(data)) {
        PersonalWorldsMod.LOGGER.error("Attempted to register invalid dimension data for {}",
            data.ownerUuid());
        data = DataValidator.sanitizeDimensionData(data, data.ownerUuid());
    }

    dimensions.put(data.ownerUuid(), data);
    markDirty();
    // ...
}

// Add validation during NBT deserialization:
public static DimensionRegistry fromNbt(NbtCompound nbt) {
    // ... existing code ...

    // After loading each entry:
    if (!DataValidator.isValidDimensionData(data)) {
        PersonalWorldsMod.LOGGER.warn("Skipping invalid dimension data for {}",
            ownerUuid);
        continue;
    }
    // ...
}
```

### 9. PlayerDataManager.java

**Add data validation:**

```java
public void setReturnData(UUID playerUuid, ReturnData data) {
    if (data == null || !DataValidator.isValidUuid(playerUuid)) {
        PersonalWorldsMod.LOGGER.warn("Attempted to set invalid return data for {}",
            playerUuid);
        return;
    }

    returnPositions.put(playerUuid, data);
    markDirty();
    // ...
}

public boolean addInvitation(UUID ownerUuid, String ownerName, UUID guestUuid) {
    // Sanitize owner name
    ownerName = DataValidator.sanitizePlayerName(ownerName);

    // ... existing logic ...
}
```

### 10. PersonalWorldsMod.java

**Add performance monitoring command for testing:**

```java
// In onInitialize(), after command registration:
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    // ... existing commands ...

    // Debug/testing commands (op level 4)
    dispatcher.register(
        CommandManager.literal("pw")
            .then(CommandManager.literal("debug")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("perf")
                    .then(CommandManager.literal("enable")
                        .executes(ctx -> {
                            PerformanceMonitor.enable();
                            ctx.getSource().sendFeedback(() -> Text.literal("Performance monitoring enabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("disable")
                        .executes(ctx -> {
                            PerformanceMonitor.disable();
                            ctx.getSource().sendFeedback(() -> Text.literal("Performance monitoring disabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("status")
                        .executes(ctx -> {
                            PerformanceMonitor.logStatus(ctx.getSource().getServer());
                            return 1;
                        }))
                )
            )
    );
});
```

---

## Performance Testing Plan

### Test Environment Setup

Create test infrastructure for validating performance with 15 concurrent dimensions.

### 1. Load Test Script

**File:** `test/load_test.sh` (for reference, not committed)

```bash
#!/bin/bash
# Simulate 15 players creating and entering dimensions

# This script coordinates multiple MC clients for load testing
# Each client will:
# 1. Connect to server
# 2. Build and activate a portal
# 3. Enter their personal dimension
# 4. Perform various actions (place blocks, move around)
# 5. Exit and re-enter periodically

echo "PersonalWorlds Load Test - 15 Dimensions"
echo "Ensure server is running with debug logging enabled"
```

### 2. Test Scenarios

| Scenario | Description | Success Criteria |
|----------|-------------|------------------|
| Sequential Creation | Create 15 dimensions one by one | All created within 30s total |
| Concurrent Creation | 5 players create dimensions simultaneously | No race conditions, all succeed |
| Rapid Entry/Exit | Players enter/exit in quick succession | No teleport failures |
| Memory Stability | Run 15 dimensions for 30 minutes | Memory < 80% max heap |
| Chunk Loading | Players explore personal dimensions | No chunk loading errors |
| Unload Behavior | All players leave dimensions | All unload within 60s |
| Recovery Test | Kill server with players in dimensions | All recover on restart |

### 3. Performance Metrics

Track these metrics during testing:

| Metric | Target | Warning Threshold |
|--------|--------|-------------------|
| Dimension creation time | < 500ms | > 1000ms |
| Teleportation latency | < 100ms | > 500ms |
| Memory per dimension | < 50MB | > 100MB |
| TPS impact | > 19.5 | < 19.0 |
| Dimension unload time | < 1s | > 5s |

### 4. Stress Test Checklist

- [ ] Create 15 dimensions sequentially
- [ ] Create 5 dimensions concurrently
- [ ] Have all 15 players online simultaneously
- [ ] Have all 15 players in their dimensions simultaneously
- [ ] Run for 30 minutes with active gameplay
- [ ] Simulate player disconnects and reconnects
- [ ] Force server restart with players in dimensions
- [ ] Delete overworld while preserving dimensions
- [ ] Measure memory usage over time
- [ ] Monitor TPS during peak load

---

## Release Documentation

### 1. README.md

**Path:** `README.md`

```markdown
# PersonalWorlds

A Fabric mod for Minecraft 1.20.4 that gives each player their own persistent dimension.

## Features

- **Personal Dimensions**: Each player gets their own isolated world
- **Persistence**: Dimensions survive main world resets
- **Portal-Based Access**: Build a portal frame, activate with emerald
- **Invitation System**: Invite friends to visit your dimension
- **Void Generation**: Clean slate void worlds with starter platforms

## Requirements

- Minecraft 1.20.4
- Fabric Loader 0.14.22+
- Fabric API

## Installation

1. Install Fabric Loader
2. Download PersonalWorlds from releases
3. Place in `mods/` folder
4. Launch Minecraft

## Usage

### Creating a Portal

Build a frame using Nether Bricks (4 wide x 5 tall):

```
N N N N
N     N
N     N
N     N
N N N N
```

Right-click inside the frame with an Emerald to activate.

### Commands

Player commands:
- `/pw invites` - View your invitations
- `/pw invite <player>` - Invite a player
- `/pw uninvite <player>` - Revoke invitation
- `/pw go <player>` - Visit someone's dimension

Admin commands (op level 2+):
- `/pw admin list` - List all dimensions
- `/pw admin info <player>` - Dimension details
- `/pw admin tp <player>` - Teleport to dimension
- `/pw admin delete <player>` - Delete dimension (op 4)

## Configuration

Config file: `config/personalworlds.json`

## World Reset Procedure

To reset the main world while preserving player dimensions:

1. Stop the server
2. Delete: `world/region/`, `world/DIM-1/`, `world/DIM1/`
3. Keep: `world/dimensions/`, `world/data/`
4. Start server

## License

MIT License
```

### 2. CHANGELOG.md

**Path:** `CHANGELOG.md`

```markdown
# Changelog

## [0.1.0] - 2024-XX-XX

### Added
- Personal dimension creation via portal system
- Void world generation with starter platform
- Invitation system for visiting other players' dimensions
- Return portal for leaving personal dimensions
- Dimension persistence through world resets
- Admin commands for dimension management
- Crash recovery for players in personal dimensions
- Concurrent portal access protection
- Configuration file support

### Technical
- Fantasy library integration for runtime dimensions
- NBT-based persistent state for dimension registry
- Safe spawn finding with fallback strategies
- Performance monitoring utilities
- Data validation for all persistent storage
```

---

## Implementation Order

1. **CrashRecoveryHandler.java** - Player join recovery
2. **ConcurrentPortalGuard.java** - Race condition prevention
3. **SafeSpawnFinder.java** - Safe position utilities
4. **DataValidator.java** - Data validation utilities
5. **PerformanceMonitor.java** - Testing utilities
6. **ModEventHandlers.java** - Integrate recovery/guards
7. **PortalHelper.java** - Integrate guards/safe spawn
8. **DimensionRegistry.java** - Add validation
9. **PlayerDataManager.java** - Add validation
10. **PersonalWorldsMod.java** - Debug commands
11. **Performance testing** - Run all test scenarios
12. **README.md** - User documentation
13. **CHANGELOG.md** - Release notes

---

## Testing Checklist

### Edge Cases

- [ ] Player disconnects while in personal dimension
  - [ ] Logs back in - still in dimension with permission
  - [ ] Logs back in - invitation revoked while offline
  - [ ] Dimension was deleted while offline
- [ ] Server crashes while player in personal dimension
  - [ ] Player position preserved
  - [ ] Return data preserved
  - [ ] Graceful recovery on restart
- [ ] Two players enter portal simultaneously
  - [ ] Both teleport successfully (if to different dimensions)
  - [ ] No duplicate dimension creation
  - [ ] No race condition errors
- [ ] Player's return position is in deleted chunk
  - [ ] Finds nearby safe position
  - [ ] Falls back to spawn if needed
  - [ ] Never spawns inside blocks
- [ ] Player invited to dimension that doesn't exist yet
  - [ ] Graceful error message
  - [ ] Portal doesn't recreate deleted dimensions

### Data Validation

- [ ] Invalid UUID in registry is skipped
- [ ] Corrupt NBT data is recovered where possible
- [ ] Player names are sanitized
- [ ] Block positions are bounded

### Performance

- [ ] 15 dimensions created without error
- [ ] Memory usage remains stable
- [ ] TPS stays above 19.5
- [ ] Dimensions unload when empty
- [ ] No memory leaks over time

### Documentation

- [ ] README covers all features
- [ ] Installation instructions clear
- [ ] Commands documented
- [ ] Configuration explained
- [ ] World reset procedure included

---

## File Summary

| File | Action | Priority |
|------|--------|----------|
| `recovery/CrashRecoveryHandler.java` | CREATE | 1 |
| `portal/ConcurrentPortalGuard.java` | CREATE | 2 |
| `util/SafeSpawnFinder.java` | CREATE | 3 |
| `util/DataValidator.java` | CREATE | 4 |
| `util/PerformanceMonitor.java` | CREATE | 5 |
| `event/ModEventHandlers.java` | MODIFY | 6 |
| `portal/PortalHelper.java` | MODIFY | 7 |
| `dimension/DimensionRegistry.java` | MODIFY | 8 |
| `player/PlayerDataManager.java` | MODIFY | 9 |
| `PersonalWorldsMod.java` | MODIFY | 10 |
| `README.md` | CREATE | 11 |
| `CHANGELOG.md` | CREATE | 12 |

---

## Post-Release Considerations

### Monitoring

After release, monitor for:
- Crash reports related to dimension creation/teleportation
- Memory issues with many dimensions
- Data corruption reports
- Edge cases not covered in testing

### Future Improvements (Phase 7+)

- Different portal styles for different world types
- Pet/animal portal travel
- Personal dimension restrictions (per-mod allowances)
- Detailed configuration GUI
- Backup/export dimension functionality
