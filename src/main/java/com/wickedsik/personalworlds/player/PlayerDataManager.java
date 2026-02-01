package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.PersistentStateCompat;
import com.wickedsik.personalworlds.util.DataValidator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

/**
 * Manages player-specific data like return positions and invitations.
 * Stored separately from DimensionRegistry for cleaner separation of concerns.
 *
 * Saved to: world/data/personalworlds_player_data.dat
 */
public class PlayerDataManager extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_player_data";

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

    /**
     * Current pocket dimensions: Player UUID -> Dimension RegistryKey
     * Tracks which pocket dimension a player is currently in (null if not in one).
     * Used for recovery when player logs out on island and dimension unloads.
     */
    private final Map<UUID, RegistryKey<World>> currentPocketDimensions = new HashMap<>();

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
     * Add a standard invitation from an owner to a guest.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param ownerName The owner's display name
     * @param guestUuid The invited player's UUID
     * @return true if invitation was added, false if already exists
     */
    public boolean addInvitation(UUID ownerUuid, String ownerName, UUID guestUuid) {
        return addInvitation(ownerUuid, ownerName, guestUuid, false);
    }

    /**
     * Add an invitation from an owner to a guest.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param ownerName The owner's display name
     * @param guestUuid The invited player's UUID
     * @param alwaysWelcome Whether the guest can visit when host is offline/away
     * @return true if invitation was added, false if already exists
     */
    public boolean addInvitation(UUID ownerUuid, String ownerName, UUID guestUuid, boolean alwaysWelcome) {
        // Sanitize owner name
        ownerName = DataValidator.sanitizePlayerName(ownerName);

        // Check if invitation already exists
        if (hasInvitationFrom(guestUuid, ownerUuid)) {
            return false;
        }

        // Add to received invitations
        InvitationData invitation = new InvitationData(ownerUuid, ownerName, System.currentTimeMillis(), alwaysWelcome);
        receivedInvitations.computeIfAbsent(guestUuid, k -> new ArrayList<>()).add(invitation);

        // Add to sent invitations
        sentInvitations.computeIfAbsent(ownerUuid, k -> new HashSet<>()).add(guestUuid);

        markDirty();
        PersonalWorldsMod.LOGGER.debug("Added invitation: {} invited {} to their dimension (alwaysWelcome={})",
            ownerName, guestUuid, alwaysWelcome);
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
        return getInvitationFrom(guestUuid, ownerUuid).isPresent();
    }

    /**
     * Get the invitation from a specific owner to a guest.
     *
     * @param guestUuid The guest player's UUID
     * @param ownerUuid The dimension owner's UUID
     * @return Optional containing the invitation if it exists
     */
    public Optional<InvitationData> getInvitationFrom(UUID guestUuid, UUID ownerUuid) {
        List<InvitationData> guestInvitations = receivedInvitations.get(guestUuid);
        if (guestInvitations == null) {
            return Optional.empty();
        }
        return guestInvitations.stream()
            .filter(inv -> inv.ownerUuid().equals(ownerUuid))
            .findFirst();
    }

    /**
     * Check if an invitation has Always Welcome status.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param guestUuid The guest player's UUID
     * @return true if the invitation exists and has alwaysWelcome enabled
     */
    public boolean isAlwaysWelcome(UUID ownerUuid, UUID guestUuid) {
        return getInvitationFrom(guestUuid, ownerUuid)
            .map(InvitationData::alwaysWelcome)
            .orElse(false);
    }

    /**
     * Toggle the Always Welcome status for an invitation.
     *
     * @param ownerUuid The dimension owner's UUID
     * @param guestUuid The guest player's UUID
     * @return Optional containing the new alwaysWelcome value, empty if invitation doesn't exist
     */
    public Optional<Boolean> toggleAlwaysWelcome(UUID ownerUuid, UUID guestUuid) {
        Optional<InvitationData> current = getInvitationFrom(guestUuid, ownerUuid);
        if (current.isEmpty()) {
            return Optional.empty();
        }

        InvitationData updated = current.get().withToggledAlwaysWelcome();
        updateInvitation(guestUuid, ownerUuid, updated);
        return Optional.of(updated.alwaysWelcome());
    }

    /**
     * Update an existing invitation with new data.
     *
     * @param guestUuid The guest player's UUID
     * @param ownerUuid The dimension owner's UUID
     * @param updated The updated invitation data
     */
    private void updateInvitation(UUID guestUuid, UUID ownerUuid, InvitationData updated) {
        List<InvitationData> guestInvitations = receivedInvitations.get(guestUuid);
        if (guestInvitations == null) {
            return;
        }

        // Replace the old invitation with the updated one
        for (int i = 0; i < guestInvitations.size(); i++) {
            if (guestInvitations.get(i).ownerUuid().equals(ownerUuid)) {
                guestInvitations.set(i, updated);
                markDirty();
                PersonalWorldsMod.LOGGER.debug("Updated invitation from {} to {}: alwaysWelcome={}",
                    ownerUuid, guestUuid, updated.alwaysWelcome());
                return;
            }
        }
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

    // --- Current Pocket Dimension Tracking ---

    /**
     * Set the current pocket dimension for a player.
     * Called when player enters a pocket dimension.
     *
     * @param playerUuid The player's UUID
     * @param dimension The pocket dimension registry key
     */
    public void setCurrentPocketDimension(UUID playerUuid, RegistryKey<World> dimension) {
        if (!DataValidator.isValidUuid(playerUuid) || dimension == null) {
            PersonalWorldsMod.LOGGER.warn("Attempted to set invalid pocket dimension tracking for {}",
                playerUuid);
            return;
        }

        currentPocketDimensions.put(playerUuid, dimension);
        markDirty();
        PersonalWorldsMod.LOGGER.debug("Tracking player {} in pocket dimension: {}",
            playerUuid, dimension.getValue());
    }

    /**
     * Get the current pocket dimension a player is tracked as being in.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the dimension key if player is tracked in a pocket dimension
     */
    public Optional<RegistryKey<World>> getCurrentPocketDimension(UUID playerUuid) {
        return Optional.ofNullable(currentPocketDimensions.get(playerUuid));
    }

    /**
     * Clear the pocket dimension tracking for a player.
     * Called when player exits a pocket dimension.
     *
     * @param playerUuid The player's UUID
     */
    public void clearCurrentPocketDimension(UUID playerUuid) {
        if (currentPocketDimensions.remove(playerUuid) != null) {
            markDirty();
            PersonalWorldsMod.LOGGER.debug("Cleared pocket dimension tracking for player: {}", playerUuid);
        }
    }

    /**
     * Check if a player is tracked as being in a pocket dimension.
     *
     * @param playerUuid The player's UUID
     * @return true if player is tracked in a pocket dimension
     */
    public boolean isInPocketDimension(UUID playerUuid) {
        return currentPocketDimensions.containsKey(playerUuid);
    }

    // --- Serialization ---
    // Note: For 1.21.5+, PersistentState uses Codec-based serialization via PersistentStateCompat

    //? if >=1.21.5 {
    //?} else if >=1.21 {
    /*@Override
    public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
        return writeNbtData(nbt);
    }*/
    //?} else {
    /*@Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        return writeNbtData(nbt);
    }
    *///?}

    private NbtCompound writeNbtData(NbtCompound nbt) {
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
                com.wickedsik.personalworlds.compat.NbtCompat.putUuid(guestNbt, "Uuid", guestUuid);
                guestList.add(guestNbt);
            }
            sentNbt.put(entry.getKey().toString(), guestList);
        }
        nbt.put("SentInvitations", sentNbt);

        // Current pocket dimensions
        NbtCompound pocketDimNbt = new NbtCompound();
        for (Map.Entry<UUID, RegistryKey<World>> entry : currentPocketDimensions.entrySet()) {
            pocketDimNbt.putString(entry.getKey().toString(), entry.getValue().getValue().toString());
        }
        nbt.put("CurrentPocketDimensions", pocketDimNbt);

        return nbt;
    }

    public static PlayerDataManager fromNbt(NbtCompound nbt) {
        PlayerDataManager manager = new PlayerDataManager();

        // Return positions
        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "ReturnPositions", NbtElement.COMPOUND_TYPE)) {
            NbtCompound returnDataNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "ReturnPositions");
            for (String key : returnDataNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ReturnData data = ReturnData.fromNbt(com.wickedsik.personalworlds.compat.NbtCompat.getCompound(returnDataNbt, key));
                    manager.returnPositions.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in return positions: {}", key);
                }
            }
        }

        // Received invitations
        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "ReceivedInvitations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound receivedNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "ReceivedInvitations");
            for (String key : receivedNbt.getKeys()) {
                try {
                    UUID guestUuid = UUID.fromString(key);
                    NbtList invList = com.wickedsik.personalworlds.compat.NbtCompat.getList(receivedNbt, key, NbtElement.COMPOUND_TYPE);
                    List<InvitationData> invitations = new ArrayList<>();
                    for (int i = 0; i < invList.size(); i++) {
                        invitations.add(InvitationData.fromNbt(com.wickedsik.personalworlds.compat.NbtCompat.getCompound(invList, i)));
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
        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "SentInvitations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound sentNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "SentInvitations");
            for (String key : sentNbt.getKeys()) {
                try {
                    UUID ownerUuid = UUID.fromString(key);
                    NbtList guestList = com.wickedsik.personalworlds.compat.NbtCompat.getList(sentNbt, key, NbtElement.COMPOUND_TYPE);
                    Set<UUID> guests = new HashSet<>();
                    for (int i = 0; i < guestList.size(); i++) {
                        NbtCompound guestNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(guestList, i);
                        UUID guestUuid = com.wickedsik.personalworlds.compat.NbtCompat.getUuid(guestNbt, "Uuid");
                        if (guestUuid != null) {
                            guests.add(guestUuid);
                        }
                    }
                    if (!guests.isEmpty()) {
                        manager.sentInvitations.put(ownerUuid, guests);
                    }
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID in sent invitations: {}", key);
                }
            }
        }

        // Current pocket dimensions (backward compatible - missing = empty)
        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "CurrentPocketDimensions", NbtElement.COMPOUND_TYPE)) {
            NbtCompound pocketDimNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "CurrentPocketDimensions");
            for (String key : pocketDimNbt.getKeys()) {
                try {
                    UUID playerUuid = UUID.fromString(key);
                    Identifier dimId = IdentifierCompat.fromNbtString(com.wickedsik.personalworlds.compat.NbtCompat.getString(pocketDimNbt, key, ""));
                    if (dimId != null) {
                        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);
                        manager.currentPocketDimensions.put(playerUuid, dimension);
                    }
                } catch (IllegalArgumentException e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid UUID or dimension in pocket dimensions: {}", key);
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("Loaded {} return positions, {} players with received invitations, {} owners with sent invitations, {} pocket dimension trackings",
            manager.returnPositions.size(),
            manager.receivedInvitations.size(),
            manager.sentInvitations.size(),
            manager.currentPocketDimensions.size());
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
        return PersistentStateCompat.getOrCreate(
            stateManager,
            DATA_NAME,
            PlayerDataManager::new,
            PlayerDataManager::fromNbt
        );
    }
}
