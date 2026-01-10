package com.wickedsik.personalworlds.command.service;

import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for resolving player names to UUIDs.
 * Consolidates the duplicate player lookup logic scattered across commands.
 */
public class PlayerLookupService {

    /**
     * Reference to a player, whether online or offline.
     *
     * @param uuid The player's UUID
     * @param resolvedName The player's name (may be case-corrected)
     * @param online Whether the player is currently online
     */
    public record PlayerReference(UUID uuid, String resolvedName, boolean online) {}

    /**
     * Find a player by name, checking online players first, then dimension registry.
     *
     * @param server The Minecraft server
     * @param name The player name to search for (case-insensitive)
     * @return Optional containing the player reference if found
     */
    public Optional<PlayerReference> findByName(MinecraftServer server, String name) {
        // Check online players first
        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(name);
        if (onlinePlayer != null) {
            return Optional.of(new PlayerReference(
                onlinePlayer.getUuid(),
                onlinePlayer.getName().getString(),
                true
            ));
        }

        // Search dimension registry for offline players
        DimensionRegistry registry = DimensionRegistry.get(server);
        for (PlayerDimensionData data : registry.getAllDimensions().values()) {
            if (data.ownerName().equalsIgnoreCase(name)) {
                return Optional.of(new PlayerReference(
                    data.ownerUuid(),
                    data.ownerName(),
                    false
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Find a player by name within a specific owner's invitation list.
     * Used for uninvite command to find invited players who may be offline.
     *
     * @param server The Minecraft server
     * @param ownerUuid The dimension owner whose invitations to search
     * @param name The player name to search for (case-insensitive)
     * @return Optional containing the player reference if found
     */
    public Optional<PlayerReference> findInInvitations(MinecraftServer server, UUID ownerUuid, String name) {
        // Check online players first
        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(name);
        if (onlinePlayer != null) {
            return Optional.of(new PlayerReference(
                onlinePlayer.getUuid(),
                onlinePlayer.getName().getString(),
                true
            ));
        }

        // Search through owner's sent invitations
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Set<UUID> sentInvites = dataManager.getSentInvitations(ownerUuid);
        DimensionRegistry registry = DimensionRegistry.get(server);

        for (UUID uuid : sentInvites) {
            Optional<PlayerDimensionData> data = registry.getDimensionData(uuid);
            if (data.isPresent() && data.get().ownerName().equalsIgnoreCase(name)) {
                return Optional.of(new PlayerReference(
                    uuid,
                    data.get().ownerName(),
                    false
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Find dimension data by owner name.
     *
     * @param server The Minecraft server
     * @param ownerName The dimension owner's name (case-insensitive)
     * @return Optional containing the dimension data if found
     */
    public Optional<PlayerDimensionData> findDimensionByOwnerName(MinecraftServer server, String ownerName) {
        DimensionRegistry registry = DimensionRegistry.get(server);
        for (PlayerDimensionData data : registry.getAllDimensions().values()) {
            if (data.ownerName().equalsIgnoreCase(ownerName)) {
                return Optional.of(data);
            }
        }
        return Optional.empty();
    }
}
