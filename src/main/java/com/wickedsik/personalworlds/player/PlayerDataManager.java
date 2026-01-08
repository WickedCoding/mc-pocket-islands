package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages player-specific data like return positions.
 * Stored separately from DimensionRegistry for cleaner separation of concerns.
 *
 * Saved to: world/data/personalworlds_player_data.dat
 */
public class PlayerDataManager extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_player_data";

    private static final Type<PlayerDataManager> TYPE = new Type<>(
        PlayerDataManager::new,
        PlayerDataManager::fromNbt,
        null // No DataFixTypes needed
    );

    /**
     * Return positions: Player UUID -> ReturnData
     * Stores where each player should return when exiting their personal dimension.
     */
    private final Map<UUID, ReturnData> returnPositions = new HashMap<>();

    public PlayerDataManager() {
        // Default constructor for new state
    }

    // --- Return Position Management ---

    /**
     * Store return data for a player entering their personal dimension.
     * Called before teleportation to preserve the original position.
     *
     * @param playerUuid The player's UUID
     * @param data The return position data
     */
    public void setReturnData(UUID playerUuid, ReturnData data) {
        returnPositions.put(playerUuid, data);
        markDirty();
        PersonalWorldsMod.LOGGER.debug("Stored return data for player: {}", playerUuid);
    }

    /**
     * Get return data for a player exiting their personal dimension.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing return data if present
     */
    public Optional<ReturnData> getReturnData(UUID playerUuid) {
        return Optional.ofNullable(returnPositions.get(playerUuid));
    }

    /**
     * Clear return data after a player has successfully returned.
     *
     * @param playerUuid The player's UUID
     */
    public void clearReturnData(UUID playerUuid) {
        if (returnPositions.remove(playerUuid) != null) {
            markDirty();
            PersonalWorldsMod.LOGGER.debug("Cleared return data for player: {}", playerUuid);
        }
    }

    /**
     * Check if a player has stored return data.
     *
     * @param playerUuid The player's UUID
     * @return true if return data exists
     */
    public boolean hasReturnData(UUID playerUuid) {
        return returnPositions.containsKey(playerUuid);
    }

    // --- Serialization ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound returnDataNbt = new NbtCompound();
        for (Map.Entry<UUID, ReturnData> entry : returnPositions.entrySet()) {
            returnDataNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("ReturnPositions", returnDataNbt);
        return nbt;
    }

    public static PlayerDataManager fromNbt(NbtCompound nbt) {
        PlayerDataManager manager = new PlayerDataManager();

        if (nbt.contains("ReturnPositions", NbtElement.COMPOUND_TYPE)) {
            NbtCompound returnDataNbt = nbt.getCompound("ReturnPositions");
            for (String key : returnDataNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ReturnData data = ReturnData.fromNbt(returnDataNbt.getCompound(key));
                    manager.returnPositions.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in player data: {}", key);
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("Loaded {} return positions from player data",
            manager.returnPositions.size());
        return manager;
    }

    // --- Static Access ---

    /**
     * Get the PlayerDataManager for a server.
     * Creates a new one if none exists.
     *
     * @param server The Minecraft server
     * @return The PlayerDataManager instance
     */
    public static PlayerDataManager get(MinecraftServer server) {
        PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
        return stateManager.getOrCreate(TYPE, DATA_NAME);
    }
}
