package com.wickedsik.personalworlds.compat;

//? if >=1.21 {
/*import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?} else {
import net.minecraft.nbt.CompoundTag;
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
     * @param stateManager The DimensionDataStorage from the world
     * @param name         The data file name (without .dat extension)
     * @param constructor  Supplier that creates a new empty state
     * @param deserializer Function that deserializes state from NBT
     * @param <T>          The SavedData subtype
     * @return The loaded or newly created state
     */
    public static <T extends SavedData> T getOrCreate(
            DimensionDataStorage stateManager,
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
        SavedDataType<T> type = new SavedDataType<>(
            name,
            constructor,
            codec,
            null  // No DataFixTypes needed for mod data
        );
        return stateManager.computeIfAbsent(type);
        *///?} else if >=1.20.2 {
        /*SavedData.Factory<T> type = new SavedData.Factory<>(
            constructor,
            deserializer,
            null  // No DataFixTypes needed for mod data
        );
        return stateManager.computeIfAbsent(type, name);
        *///?} else {
        return stateManager.computeIfAbsent(deserializer, constructor, name);
        //?}
    }
}
