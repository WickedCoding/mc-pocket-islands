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
