package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.PersistentStateCompat;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

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
public class PortalOwnershipManager extends PersistentState {

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
    public void registerPortal(World world, BlockPos pos, UUID ownerUuid, int portalTypeIndex) {
        String key = makeKey(world, pos);
        portalOwners.put(key, new PortalOwnershipData(ownerUuid, portalTypeIndex));
        markDirty();
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
    public Optional<UUID> getOwner(World world, BlockPos pos) {
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
    public Optional<Integer> getPortalType(World world, BlockPos pos) {
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
    public void removePortal(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        if (portalOwners.remove(key) != null) {
            markDirty();
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
    public boolean hasOwner(World world, BlockPos pos) {
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
            markDirty();
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
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerUuid);
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
    private String makeKey(World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() +
            ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // --- Serialization ---

    //? if >=1.21.5 {
    // In 1.21.5+, PersistentState uses Codec-based serialization - no override needed
    // The Codec in PersistentStateCompat calls writeNbtData() via reflection
    public NbtCompound writeNbtData(NbtCompound nbt) {
        NbtCompound portalsNbt = new NbtCompound();
        for (Map.Entry<String, PortalOwnershipData> entry : portalOwners.entrySet()) {
            NbtCompound portalData = new NbtCompound();
            com.wickedsik.personalworlds.compat.NbtCompat.putUuid(portalData, "OwnerUuid", entry.getValue().ownerUuid);
            portalData.putInt("PortalTypeIndex", entry.getValue().portalTypeIndex);
            portalsNbt.put(entry.getKey(), portalData);
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }
    //?} else if >=1.21 {
    /*@Override
    public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound portalsNbt = new NbtCompound();
        for (Map.Entry<String, PortalOwnershipData> entry : portalOwners.entrySet()) {
            NbtCompound portalData = new NbtCompound();
            com.wickedsik.personalworlds.compat.NbtCompat.putUuid(portalData, "OwnerUuid", entry.getValue().ownerUuid);
            portalData.putInt("PortalTypeIndex", entry.getValue().portalTypeIndex);
            portalsNbt.put(entry.getKey(), portalData);
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }*/
    //?} else {
    /*@Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound portalsNbt = new NbtCompound();
        for (Map.Entry<String, PortalOwnershipData> entry : portalOwners.entrySet()) {
            NbtCompound portalData = new NbtCompound();
            portalData.putUuid("OwnerUuid", entry.getValue().ownerUuid);
            portalData.putInt("PortalTypeIndex", entry.getValue().portalTypeIndex);
            portalsNbt.put(entry.getKey(), portalData);
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }
    *///?}

    public static PortalOwnershipManager fromNbt(NbtCompound nbt) {
        PortalOwnershipManager manager = new PortalOwnershipManager();

        if (com.wickedsik.personalworlds.compat.NbtCompat.contains(nbt, "PortalOwners", NbtElement.COMPOUND_TYPE)) {
            NbtCompound portalsNbt = com.wickedsik.personalworlds.compat.NbtCompat.getCompound(nbt, "PortalOwners");
            for (String key : portalsNbt.getKeys()) {
                try {
                    NbtElement element = portalsNbt.get(key);

                    // Backward compatibility: check if old format (UUID) or new format (Compound)
                    if (element instanceof NbtCompound) {
                        // New format: portal data with UUID and portal type index
                        NbtCompound portalData = (NbtCompound) element;
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
        PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
        return PersistentStateCompat.getOrCreate(
            stateManager,
            DATA_NAME,
            PortalOwnershipManager::new,
            PortalOwnershipManager::fromNbt
        );
    }
}
