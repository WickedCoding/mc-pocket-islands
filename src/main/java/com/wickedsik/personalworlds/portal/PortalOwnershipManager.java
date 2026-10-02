package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.PersistentStateCompat;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which player owns which portal and which portal type was used.
 * Ownership is established when a player activates a portal.
 *
 * Portals are identified by a compound key: "worldId:x,y,z"
 * This allows portals in different dimensions to be tracked independently.
 *
 * Saved to: world/data/personalworlds_portal_ownership.dat
 */
public class PortalOwnershipManager extends SavedData {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_portal_ownership";

    /**
     * Portal ownership data: stores owner UUID and portal type index.
     */
    private static class PortalOwnershipData {
        UUID ownerUuid;
        int portalTypeIndex;

        PortalOwnershipData(UUID ownerUuid, int portalTypeIndex) {
            this.ownerUuid = ownerUuid;
            this.portalTypeIndex = portalTypeIndex;
        }
    }

    /**
     * Portal ownership map: "worldId:x,y,z" -> PortalOwnershipData
     */
    private final Map<String, PortalOwnershipData> portalOwners = new HashMap<>();

    public PortalOwnershipManager() {
        // Default constructor for new state
    }

    // --- Portal Registration ---

    /**
     * Register a portal as owned by a player with a specific portal type.
     * Called when a player activates a portal frame.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     * @param ownerUuid The UUID of the owning player
     * @param portalTypeIndex The portal type index from ModConfig.portalTypes array
     */
    public void registerPortal(Level world, BlockPos pos, UUID ownerUuid, int portalTypeIndex) {
        String key = makeKey(world, pos);
        portalOwners.put(key, new PortalOwnershipData(ownerUuid, portalTypeIndex));
        setDirty();
        PersonalWorldsMod.LOGGER.debug("Registered portal type {} at {} owned by {}",
            portalTypeIndex, key, ownerUuid);
    }

    /**
     * Get the owner of a portal.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     * @return Optional containing owner UUID, or empty if unowned
     */
    public Optional<UUID> getOwner(Level world, BlockPos pos) {
        String key = makeKey(world, pos);
        PortalOwnershipData data = portalOwners.get(key);
        return data != null ? Optional.of(data.ownerUuid) : Optional.empty();
    }

    /**
     * Get the portal type index for a portal.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     * @return Optional containing portal type index, or empty if unowned
     */
    public Optional<Integer> getPortalType(Level world, BlockPos pos) {
        String key = makeKey(world, pos);
        PortalOwnershipData data = portalOwners.get(key);
        return data != null ? Optional.of(data.portalTypeIndex) : Optional.empty();
    }

    /**
     * Remove ownership record for a portal.
     * Called when a portal is destroyed.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     */
    public void removePortal(Level world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (portalOwners.remove(key) != null) {
            setDirty();
            PersonalWorldsMod.LOGGER.debug("Removed portal ownership at {}", key);
        }
    }

    /**
     * Check if a portal has an owner.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     * @return true if the portal has a registered owner
     */
    public boolean hasOwner(Level world, BlockPos pos) {
        String key = makeKey(world, pos);
        return portalOwners.containsKey(key);
    }

    /**
     * Remove all portal ownership records for a specific owner.
     * Called when a dimension is deleted via admin command.
     *
     * @param ownerUuid The owner's UUID whose portals should be cleared
     * @return The number of portals cleared
     */
    public int clearPortalsOwnedBy(UUID ownerUuid) {
        int removed = 0;
        var iterator = portalOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().ownerUuid.equals(ownerUuid)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            setDirty();
            PersonalWorldsMod.LOGGER.info("Cleared {} portal ownership records for {}", removed, ownerUuid);
        }
        return removed;
    }

    // --- Owner Name Lookup ---

    /**
     * Get the display name of a portal owner.
     * First tries to get the name from an online player,
     * then falls back to the DimensionRegistry,
     * finally uses a truncated UUID if all else fails.
     *
     * @param server The Minecraft server
     * @param ownerUuid The owner's UUID
     * @return The owner's display name
     */
    public String getOwnerName(MinecraftServer server, UUID ownerUuid) {
        // Try online player first
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player != null) {
            return player.getName().getString();
        }

        // Try DimensionRegistry
        DimensionRegistry registry = DimensionRegistry.get(server);
        Optional<PlayerDimensionData> data = registry.getDimensionData(ownerUuid);
        if (data.isPresent()) {
            return data.get().ownerName();
        }

        // Fallback to truncated UUID
        return ownerUuid.toString().substring(0, 8);
    }

    // --- Key Generation ---

    /**
     * Create a unique key for a portal position.
     *
     * @param world The world containing the portal
     * @param pos The portal block position
     * @return A string key in format "namespace:path:x,y,z"
     */
    private String makeKey(Level world, BlockPos pos) {
        return IdentifierCompat.fromKey(world.dimension()).toString() +
            ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // --- Serialization ---

    //? if >=1.21.5 {
    /*// In 1.21.5+, PersistentState uses Codec-based serialization - no override needed
    // The Codec in PersistentStateCompat calls writeNbtData() via reflection
    public CompoundTag writeNbtData(CompoundTag nbt) {
        CompoundTag portalsNbt = new CompoundTag();
        for (Map.Entry<String, PortalOwnershipData> entry : portalOwners.entrySet()) {
            CompoundTag portalData = new CompoundTag();
            com.wickedsik.personalworlds.compat.NbtCompat.putUuid(portalData, "OwnerUuid", entry.getValue().ownerUuid);
            portalData.putInt("PortalTypeIndex", entry.getValue().portalTypeIndex);
            portalsNbt.put(entry.getKey(), portalData);
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }
    *///?} else {
    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag portalsNbt = new CompoundTag();
        for (Map.Entry<String, PortalOwnershipData> entry : portalOwners.entrySet()) {
            CompoundTag portalData = new CompoundTag();
            portalData.putUUID("OwnerUuid", entry.getValue().ownerUuid);
            portalData.putInt("PortalTypeIndex", entry.getValue().portalTypeIndex);
            portalsNbt.put(entry.getKey(), portalData);
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }
    //?}

    public static PortalOwnershipManager fromNbt(CompoundTag nbt) {
        PortalOwnershipManager manager = new PortalOwnershipManager();

        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "PortalOwners", Tag.TAG_COMPOUND)) {
            CompoundTag portalsNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "PortalOwners");
            for (String key : com.wickedsik.personalworlds.compat.NbtCompat.getKeys(portalsNbt)) {
                try {
                    Tag element = portalsNbt.get(key);

                    // Backward compatibility: check if old format (UUID) or new format (Compound)
                    if (element instanceof CompoundTag) {
                        // New format: portal data with UUID and portal type index
                        CompoundTag portalData = (CompoundTag) element;
                        UUID uuid = com.wickedsik.personalworlds.compat.NbtCompat.getUuid(portalData, "OwnerUuid");
                        int portalTypeIndex = com.wickedsik.personalworlds.compat.NbtCompat.getInt(portalData, "PortalTypeIndex", 0);
                        if (uuid != null) {
                            manager.portalOwners.put(key, new PortalOwnershipData(uuid, portalTypeIndex));
                        }
                    } else {
                        // Old format: just UUID - migrate to new format with default portal type
                        UUID uuid = com.wickedsik.personalworlds.compat.NbtCompat.getUuid(portalsNbt, key);
                        if (uuid != null) {
                            manager.portalOwners.put(key, new PortalOwnershipData(uuid, 0));
                            PersonalWorldsMod.LOGGER.debug("Migrated old portal ownership data for key: {}", key);
                        }
                    }
                } catch (Exception e) {
                    PersonalWorldsMod.LOGGER.warn("Invalid portal ownership data for key: {}", key);
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("Loaded {} portal ownership records", manager.portalOwners.size());
        return manager;
    }

    // --- Static Access ---

    /**
     * Get the PortalOwnershipManager for a server.
     * Creates a new one if none exists.
     *
     * @param server The Minecraft server
     * @return The PortalOwnershipManager instance
     */
    public static PortalOwnershipManager get(MinecraftServer server) {
        DimensionDataStorage stateManager = server.overworld().getDataStorage();
        return PersistentStateCompat.getOrCreate(
            stateManager,
            DATA_NAME,
            PortalOwnershipManager::new,
            PortalOwnershipManager::fromNbt
        );
    }
}
