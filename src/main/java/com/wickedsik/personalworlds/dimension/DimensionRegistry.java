package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.util.DataValidator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DimensionRegistry extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_registry";

    private static final Type<DimensionRegistry> TYPE = new Type<>(
        DimensionRegistry::new,
        DimensionRegistry::fromNbt,
        null // No DataFixTypes needed
    );

    private final Map<UUID, PlayerDimensionData> dimensions = new HashMap<>();

    public DimensionRegistry() {
        // Default constructor for new registries
    }

    // --- Dimension Registration ---

    public void registerDimension(PlayerDimensionData data) {
        // Validate before storing
        if (!DataValidator.isValidDimensionData(data)) {
            PersonalWorldsMod.LOGGER.error("Attempted to register invalid dimension data for {}",
                data.ownerUuid());
            data = DataValidator.sanitizeDimensionData(data, data.ownerUuid());
        }

        dimensions.put(data.ownerUuid(), data);
        markDirty();
        PersonalWorldsMod.LOGGER.info("Registered dimension for player: {} ({})",
            data.ownerName(), data.ownerUuid());
    }

    public boolean hasDimension(UUID playerUuid) {
        return dimensions.containsKey(playerUuid);
    }

    public Optional<PlayerDimensionData> getDimensionData(UUID playerUuid) {
        return Optional.ofNullable(dimensions.get(playerUuid));
    }

    public Map<UUID, PlayerDimensionData> getAllDimensions() {
        return Map.copyOf(dimensions);
    }

    public void removeDimension(UUID playerUuid) {
        if (dimensions.remove(playerUuid) != null) {
            markDirty();
            PersonalWorldsMod.LOGGER.info("Removed dimension for player: {}", playerUuid);
        }
    }

    // --- Restoration on Server Start ---

    public void restoreAllDimensions(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Restoring {} player dimensions...", dimensions.size());
        for (PlayerDimensionData data : dimensions.values()) {
            try {
                DimensionManager.loadExistingDimension(server, data);
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.error("Failed to restore dimension for {}: {}",
                    data.ownerName(), e.getMessage());
            }
        }
        PersonalWorldsMod.LOGGER.info("Dimension restoration complete!");
    }

    // --- Serialization ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList dimensionList = new NbtList();
        for (PlayerDimensionData data : dimensions.values()) {
            dimensionList.add(data.toNbt());
        }
        nbt.put("Dimensions", dimensionList);
        return nbt;
    }

    public static DimensionRegistry fromNbt(NbtCompound nbt) {
        DimensionRegistry registry = new DimensionRegistry();
        NbtList dimensionList = nbt.getList("Dimensions", NbtElement.COMPOUND_TYPE);
        int skipped = 0;

        for (int i = 0; i < dimensionList.size(); i++) {
            try {
                PlayerDimensionData data = PlayerDimensionData.fromNbt(dimensionList.getCompound(i));

                // Validate loaded data
                if (!DataValidator.isValidDimensionData(data)) {
                    PersonalWorldsMod.LOGGER.warn("Skipping invalid dimension data for {}",
                        data.ownerUuid());
                    skipped++;
                    continue;
                }

                registry.dimensions.put(data.ownerUuid(), data);
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.error("Failed to load dimension data at index {}: {}",
                    i, e.getMessage());
                skipped++;
            }
        }

        PersonalWorldsMod.LOGGER.info("Loaded {} dimensions from registry ({} skipped)",
            registry.dimensions.size(), skipped);
        return registry;
    }

    // --- Static Access ---

    public static DimensionRegistry get(MinecraftServer server) {
        PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
        return stateManager.getOrCreate(TYPE, DATA_NAME);
    }
}
