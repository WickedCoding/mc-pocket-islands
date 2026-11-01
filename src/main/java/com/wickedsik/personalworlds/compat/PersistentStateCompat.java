package com.wickedsik.personalworlds.compat;

//? if >=26.1 {
/*import com.mojang.serialization.Codec;
import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
*///?} else if >=1.21 {
/*import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?} else {
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
//?}

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compatibility layer for SavedData lookup (DimensionDataStorage.computeIfAbsent()) API differences.
 * <p>
 * MC 1.20.1 uses: computeIfAbsent(Function fromNbt, Supplier constructor, String name)
 * MC 1.20.2-1.20.6 uses: computeIfAbsent(Factory<T> type, String name)
 * MC 1.21.x uses: computeIfAbsent(SavedDataType<T> type) with Codec-based serialization
 * MC 26.1+ uses: SavedDataStorage with a ResourceLocation id. Each level has its own storage under
 * dimensions/&lt;namespace&gt;/&lt;path&gt;/data/, so mod data uses the server's storage (world/data/):
 * the overworld's would be deleted with the overworld on a world reset.
 * <p>
 * This class centralizes the version-specific SavedData access pattern.
 */
public final class PersistentStateCompat {

    private PersistentStateCompat() {
        // Utility class
    }

    /**
     * Get or create a SavedData with version-appropriate API.
     * <p>
     * Note: In 1.21+, the SavedData subclass MUST implement save() with proper signature.
     *
     * @param server       The Minecraft server
     * @param name         The data file name (without .dat extension)
     * @param constructor  Supplier that creates a new empty state
     * @param deserializer Function that deserializes state from NBT
     * @param <T>          The SavedData subtype
     * @return The loaded or newly created state
     */
    public static <T extends SavedData> T getOrCreate(
            MinecraftServer server,
            String name,
            Supplier<T> constructor,
            Function<CompoundTag, T> deserializer
    ) {
        //? if >=1.21 {
        /*// 1.21.x uses SavedDataType with Codec
        // Create a codec that wraps the NBT serialization
        // Subclasses must implement save(CompoundTag, HolderLookup.Provider) for serialization
        Codec<T> codec = CompoundTag.CODEC.xmap(
            deserializer::apply,
            state -> {
                CompoundTag nbt = new CompoundTag();
                // Use reflection-free approach: subclasses are expected to implement a toNbt-like pattern
                // The actual serialization happens in the subclass's save() method which Minecraft calls
                // For the codec, we need the serialized form - call writeNbtData if available
                try {
                    // Try to call writeNbtData which our subclasses implement
                    java.lang.reflect.Method method = state.getClass().getDeclaredMethod("writeNbtData", CompoundTag.class);
                    method.setAccessible(true);
                    return (CompoundTag) method.invoke(state, nbt);
                } catch (Exception e) {
                    // Fallback: return empty NBT (will trigger save on next setDirty)
                    return nbt;
                }
            }
        );
        *///?}
        //? if >=26.1 {
        /*// Stored as world/data/personalworlds/<name>.dat
        migrateLegacyFile(server, name);
        SavedDataType<T> type = new SavedDataType<>(
            IdentifierCompat.modId(name),
            constructor,
            codec,
            null  // No DataFixTypes needed for mod data
        );
        return server.getDataStorage().computeIfAbsent(type);
        *///?} else if >=1.21 {
        /*SavedDataType<T> type = new SavedDataType<>(
            name,
            constructor,
            codec,
            null  // No DataFixTypes needed for mod data
        );
        return server.overworld().getDataStorage().computeIfAbsent(type);
        *///?} else if >=1.20.2 {
        /*SavedData.Factory<T> type = new SavedData.Factory<>(
            constructor,
            deserializer,
            null  // No DataFixTypes needed for mod data
        );
        return server.overworld().getDataStorage().computeIfAbsent(type, name);
        *///?} else {
        DimensionDataStorage stateManager = server.overworld().getDataStorage();
        return stateManager.computeIfAbsent(deserializer, constructor, name);
        //?}
    }

    //? if >=26.1 {
    /*// Names already checked per server: the storage caches a missing file, so the move must
    // happen before the first lookup of each name, and only that lookup needs the disk check
    private static final Map<MinecraftServer, Set<String>> MIGRATION_CHECKED = new WeakHashMap<>();

    // Move mod data written before 26.1 to its 26.1 location. Up to 1.21.11 the data was
    // world/data/<name>.dat. Upgrading a world to 26.1 or newer leaves those files in place
    // (vanilla's file fixer only moves its own files), while 26.1 reads
    // world/data/personalworlds/<name>.dat. Both are in the server's storage, so the file
    // moves unchanged.
    private static void migrateLegacyFile(MinecraftServer server, String name) {
        synchronized (MIGRATION_CHECKED) {
            if (!MIGRATION_CHECKED.computeIfAbsent(server, s -> new HashSet<>()).add(name)) {
                return;
            }
        }
        Path dataDir = server.getWorldPath(LevelResource.DATA);
        Path legacy = dataDir.resolve(name + ".dat");
        Path current = dataDir.resolve(PersonalWorldsMod.MOD_ID).resolve(name + ".dat");
        if (!Files.exists(legacy) || Files.exists(current)) {
            return;
        }
        try {
            Files.createDirectories(current.getParent());
            Files.move(legacy, current);
            PersonalWorldsMod.LOGGER.info("Moved {} to {} (world upgraded from an older Minecraft version)", legacy, current);
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Failed to move {} to {}; starting with empty data", legacy, current, e);
        }
    }
    *///?}
}
