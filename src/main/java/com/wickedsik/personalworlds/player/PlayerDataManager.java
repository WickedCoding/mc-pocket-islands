package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.util.DataValidator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

/**
 * Manages player-specific data like return positions and invitations.
 * Stored separately from DimensionRegistry for cleaner separation of concerns.
 *
 * Saved to: world/data/personalworlds_player_data.dat
 */
public class PlayerDataManager extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_player_data";

    //? if >=1.20.2 {
    private static final Type<PlayerDataManager> TYPE = new Type<>(
        PlayerDataManager::new,
        PlayerDataManager::fromNbt,
        null // No DataFixTypes needed
    );
    //?}

    /**
     * Return positions: Player UUID -> ReturnData
     * Stores where each player should return when exiting their personal dimension.
     */
    private final Map<UUID, ReturnData> returnPositions = new HashMap<>();

    /**
     * Received invitations: Guest UUID -> List<InvitationData>
     * Stores all invitations a player has received from dimension owners.
     */
    private final Map<UUID, List<InvitationData>> receivedInvitations = new HashMap<>();

    /**
     * Sent invitations: Owner UUID -> Set<Guest UUIDs>
     * Tracks which players each owner has invited.
     */
    private final Map<UUID, Set<UUID>> sentInvitations = new HashMap<>();

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
        if (data == null || !DataValidator.isValidUuid(playerUuid)) {
            PersonalWorldsMod.LOGGER.warn("Attempted to set invalid return data for {}",
                playerUuid);
            return;
        }

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

    // --- Invitation Management ---

    /**
     * Add an invitation from an owner to a guest.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param ownerName The owner's display name
     * @param guestUuid The invited player's UUID
     * @return true if invitation was added, false if already exists
     */
    public boolean addInvitation(UUID ownerUuid, String ownerName, UUID guestUuid) {
        // Sanitize owner name
        ownerName = DataValidator.sanitizePlayerName(ownerName);

        // Check if invitation already exists
        if (hasInvitationFrom(guestUuid, ownerUuid)) {
            return false;
        }

        // Add to received invitations
        InvitationData invitation = new InvitationData(ownerUuid, ownerName, System.currentTimeMillis());
        receivedInvitations.computeIfAbsent(guestUuid, k -> new ArrayList<>()).add(invitation);

        // Add to sent invitations
        sentInvitations.computeIfAbsent(ownerUuid, k -> new HashSet<>()).add(guestUuid);

        markDirty();
        PersonalWorldsMod.LOGGER.debug("Added invitation: {} invited {} to their dimension",
            ownerName, guestUuid);
        return true;
    }

    /**
     * Remove an invitation from an owner to a guest.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param guestUuid The guest player's UUID
     * @return true if invitation was removed, false if it didn't exist
     */
    public boolean removeInvitation(UUID ownerUuid, UUID guestUuid) {
        boolean removed = false;

        // Remove from received invitations
        List<InvitationData> guestInvitations = receivedInvitations.get(guestUuid);
        if (guestInvitations != null) {
            removed = guestInvitations.removeIf(inv -> inv.ownerUuid().equals(ownerUuid));
            if (guestInvitations.isEmpty()) {
                receivedInvitations.remove(guestUuid);
            }
        }

        // Remove from sent invitations
        Set<UUID> ownerSent = sentInvitations.get(ownerUuid);
        if (ownerSent != null) {
            ownerSent.remove(guestUuid);
            if (ownerSent.isEmpty()) {
                sentInvitations.remove(ownerUuid);
            }
        }

        if (removed) {
            markDirty();
            PersonalWorldsMod.LOGGER.debug("Removed invitation: {} revoked invitation to {}",
                ownerUuid, guestUuid);
        }

        return removed;
    }

    /**
     * Check if a guest has an invitation from a specific owner.
     *
     * @param guestUuid The guest player's UUID
     * @param ownerUuid The dimension owner's UUID
     * @return true if the guest has an invitation from the owner
     */
    public boolean hasInvitationFrom(UUID guestUuid, UUID ownerUuid) {
        List<InvitationData> guestInvitations = receivedInvitations.get(guestUuid);
        if (guestInvitations == null) {
            return false;
        }
        return guestInvitations.stream()
            .anyMatch(inv -> inv.ownerUuid().equals(ownerUuid));
    }

    /**
     * Get all invitations received by a player.
     *
     * @param guestUuid The guest player's UUID
     * @return List of invitations (may be empty, never null)
     */
    public List<InvitationData> getReceivedInvitations(UUID guestUuid) {
        return receivedInvitations.getOrDefault(guestUuid, Collections.emptyList());
    }

    /**
     * Get all players invited by an owner.
     *
     * @param ownerUuid The dimension owner's UUID
     * @return Set of invited player UUIDs (may be empty, never null)
     */
    public Set<UUID> getSentInvitations(UUID ownerUuid) {
        return sentInvitations.getOrDefault(ownerUuid, Collections.emptySet());
    }

    /**
     * Clear all invitations involving a player (both as owner and as guest).
     * Used when deleting a player's dimension via admin command.
     *
     * @param playerUuid The player's UUID
     */
    public void clearAllInvitationsFor(UUID playerUuid) {
        boolean changed = false;

        // Clear all invitations sent BY this player (as dimension owner)
        Set<UUID> guests = sentInvitations.remove(playerUuid);
        if (guests != null && !guests.isEmpty()) {
            for (UUID guestUuid : guests) {
                List<InvitationData> guestInvitations = receivedInvitations.get(guestUuid);
                if (guestInvitations != null) {
                    guestInvitations.removeIf(inv -> inv.ownerUuid().equals(playerUuid));
                    if (guestInvitations.isEmpty()) {
                        receivedInvitations.remove(guestUuid);
                    }
                }
            }
            changed = true;
            PersonalWorldsMod.LOGGER.debug("Cleared {} sent invitations for {}", guests.size(), playerUuid);
        }

        // Clear all invitations received BY this player (as guest)
        List<InvitationData> received = receivedInvitations.remove(playerUuid);
        if (received != null && !received.isEmpty()) {
            // Also remove from owners' sent lists
            for (InvitationData inv : received) {
                Set<UUID> ownerSent = sentInvitations.get(inv.ownerUuid());
                if (ownerSent != null) {
                    ownerSent.remove(playerUuid);
                    if (ownerSent.isEmpty()) {
                        sentInvitations.remove(inv.ownerUuid());
                    }
                }
            }
            changed = true;
            PersonalWorldsMod.LOGGER.debug("Cleared {} received invitations for {}", received.size(), playerUuid);
        }

        if (changed) {
            markDirty();
        }
    }

    // --- Serialization ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Return positions
        NbtCompound returnDataNbt = new NbtCompound();
        for (Map.Entry<UUID, ReturnData> entry : returnPositions.entrySet()) {
            returnDataNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("ReturnPositions", returnDataNbt);

        // Received invitations
        NbtCompound receivedNbt = new NbtCompound();
        for (Map.Entry<UUID, List<InvitationData>> entry : receivedInvitations.entrySet()) {
            NbtList invList = new NbtList();
            for (InvitationData inv : entry.getValue()) {
                invList.add(inv.toNbt());
            }
            receivedNbt.put(entry.getKey().toString(), invList);
        }
        nbt.put("ReceivedInvitations", receivedNbt);

        // Sent invitations
        NbtCompound sentNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<UUID>> entry : sentInvitations.entrySet()) {
            NbtList guestList = new NbtList();
            for (UUID guestUuid : entry.getValue()) {
                NbtCompound guestNbt = new NbtCompound();
                guestNbt.putUuid("Uuid", guestUuid);
                guestList.add(guestNbt);
            }
            sentNbt.put(entry.getKey().toString(), guestList);
        }
        nbt.put("SentInvitations", sentNbt);

        return nbt;
    }

    public static PlayerDataManager fromNbt(NbtCompound nbt) {
        PlayerDataManager manager = new PlayerDataManager();

        // Return positions
        if (nbt.contains("ReturnPositions", NbtElement.COMPOUND_TYPE)) {
            NbtCompound returnDataNbt = nbt.getCompound("ReturnPositions");
            for (String key : returnDataNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ReturnData data = ReturnData.fromNbt(returnDataNbt.getCompound(key));
                    manager.returnPositions.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in return positions: {}", key);
                }
            }
        }

        // Received invitations
        if (nbt.contains("ReceivedInvitations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound receivedNbt = nbt.getCompound("ReceivedInvitations");
            for (String key : receivedNbt.getKeys()) {
                try {
                    UUID guestUuid = UUID.fromString(key);
                    NbtList invList = receivedNbt.getList(key, NbtElement.COMPOUND_TYPE);
                    List<InvitationData> invitations = new ArrayList<>();
                    for (int i = 0; i < invList.size(); i++) {
                        invitations.add(InvitationData.fromNbt(invList.getCompound(i)));
                    }
                    if (!invitations.isEmpty()) {
                        manager.receivedInvitations.put(guestUuid, invitations);
                    }
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in received invitations: {}", key);
                }
            }
        }

        // Sent invitations
        if (nbt.contains("SentInvitations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound sentNbt = nbt.getCompound("SentInvitations");
            for (String key : sentNbt.getKeys()) {
                try {
                    UUID ownerUuid = UUID.fromString(key);
                    NbtList guestList = sentNbt.getList(key, NbtElement.COMPOUND_TYPE);
                    Set<UUID> guests = new HashSet<>();
                    for (int i = 0; i < guestList.size(); i++) {
                        NbtCompound guestNbt = guestList.getCompound(i);
                        guests.add(guestNbt.getUuid("Uuid"));
                    }
                    if (!guests.isEmpty()) {
                        manager.sentInvitations.put(ownerUuid, guests);
                    }
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in sent invitations: {}", key);
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("Loaded {} return positions, {} players with received invitations, {} owners with sent invitations",
            manager.returnPositions.size(),
            manager.receivedInvitations.size(),
            manager.sentInvitations.size());
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
        //? if >=1.20.2 {
        return stateManager.getOrCreate(TYPE, DATA_NAME);
        //?} else {
        /*return stateManager.getOrCreate(PlayerDataManager::fromNbt, PlayerDataManager::new, DATA_NAME);
        *///?}
    }
}
