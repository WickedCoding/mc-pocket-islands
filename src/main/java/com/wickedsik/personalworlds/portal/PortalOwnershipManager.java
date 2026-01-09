package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
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
 * Tracks which player owns which portal.
 * Ownership is established when a player activates a portal.
 *
 * Portals are identified by a compound key: "worldId:x,y,z"
 * This allows portals in different dimensions to be tracked independently.
 *
 * Saved to: world/data/personalworlds_portal_ownership.dat
 */
public class PortalOwnershipManager extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_portal_ownership";

    private static final Type<PortalOwnershipManager> TYPE = new Type<>(
        PortalOwnershipManager::new,
        PortalOwnershipManager::fromNbt,
        null // No DataFixTypes needed
    );

    /**
     * Portal ownership map: "worldId:x,y,z" -> Owner UUID
     */
    private final Map<String, UUID> portalOwners = new HashMap<>();

    public PortalOwnershipManager() {
        // Default constructor for new state
    }

    // --- Portal Registration ---

    /**
     * Register a portal as owned by a player.
     * Called when a player activates a portal frame.
     *
     * @param world The world containing the portal
     * @param pos The position of the portal block
     * @param ownerUuid The UUID of the owning player
     */
    public void registerPortal(World world, BlockPos pos, UUID ownerUuid) {
        String key = makeKey(world, pos);
        portalOwners.put(key, ownerUuid);
        markDirty();
        PersonalWorldsMod.LOGGER.debug("Registered portal at {} owned by {}", key, ownerUuid);
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
        return Optional.ofNullable(portalOwners.get(key));
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

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound portalsNbt = new NbtCompound();
        for (Map.Entry<String, UUID> entry : portalOwners.entrySet()) {
            portalsNbt.putUuid(entry.getKey(), entry.getValue());
        }
        nbt.put("PortalOwners", portalsNbt);
        return nbt;
    }

    public static PortalOwnershipManager fromNbt(NbtCompound nbt) {
        PortalOwnershipManager manager = new PortalOwnershipManager();

        if (nbt.contains("PortalOwners", NbtElement.COMPOUND_TYPE)) {
            NbtCompound portalsNbt = nbt.getCompound("PortalOwners");
            for (String key : portalsNbt.getKeys()) {
                try {
                    UUID uuid = portalsNbt.getUuid(key);
                    manager.portalOwners.put(key, uuid);
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
        return stateManager.getOrCreate(TYPE, DATA_NAME);
    }
}
