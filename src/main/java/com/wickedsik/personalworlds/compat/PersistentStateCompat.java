package com.wickedsik.personalworlds.compat;

//? if >=1.21 {
/*import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
*///?} else {
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
//?}

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compatibility layer for PersistentState.getOrCreate() API differences.
 * <p>
 * MC 1.20.1 uses: getOrCreate(Function fromNbt, Supplier constructor, String name)
 * MC 1.20.2-1.20.6 uses: getOrCreate(Type<T> type, String name)
 * MC 1.21.x uses: getOrCreate(PersistentStateType<T> type) with Codec-based serialization
 * <p>
 * This class centralizes the version-specific PersistentState access pattern.
 */
public final class PersistentStateCompat {

    private PersistentStateCompat() {
        // Utility class
    }

    /**
     * Get or create a PersistentState with version-appropriate API.
     * <p>
     * Note: In 1.21+, the PersistentState subclass MUST implement save() with proper signature.
     *
     * @param stateManager The PersistentStateManager from the world
     * @param name         The data file name (without .dat extension)
     * @param constructor  Supplier that creates a new empty state
     * @param deserializer Function that deserializes state from NBT
     * @param <T>          The PersistentState subtype
     * @return The loaded or newly created state
     */
    public static <T extends PersistentState> T getOrCreate(
            PersistentStateManager stateManager,
            String name,
            Supplier<T> constructor,
            Function<NbtCompound, T> deserializer
    ) {
        //? if >=1.21 {
        /*// 1.21.x uses PersistentStateType with Codec
        // Create a codec that wraps the NBT serialization
        // Subclasses must implement save(NbtCompound, WrapperLookup) for serialization
        Codec<T> codec = NbtCompound.CODEC.xmap(
            deserializer::apply,
            state -> {
                NbtCompound nbt = new NbtCompound();
                // Use reflection-free approach: subclasses are expected to implement a toNbt-like pattern
                // The actual serialization happens in the subclass's save() method which Minecraft calls
                // For the codec, we need the serialized form - call writeNbtData if available
                try {
                    // Try to call writeNbtData which our subclasses implement
                    java.lang.reflect.Method method = state.getClass().getDeclaredMethod("writeNbtData", NbtCompound.class);
                    method.setAccessible(true);
                    return (NbtCompound) method.invoke(state, nbt);
                } catch (Exception e) {
                    // Fallback: return empty NBT (will trigger save on next markDirty)
                    return nbt;
                }
            }
        );
        PersistentStateType<T> type = new PersistentStateType<>(
            name,
            constructor,
            codec,
            null  // No DataFixTypes needed for mod data
        );
        return stateManager.getOrCreate(type);
        *///?} else if >=1.20.2 {
        /*PersistentState.Type<T> type = new PersistentState.Type<>(
            constructor,
            deserializer,
            null  // No DataFixTypes needed for mod data
        );
        return stateManager.getOrCreate(type, name);
        *///?} else {
        return stateManager.getOrCreate(deserializer, constructor, name);
        //?}
    }
}
