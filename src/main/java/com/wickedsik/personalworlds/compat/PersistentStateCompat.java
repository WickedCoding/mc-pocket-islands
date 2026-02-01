package com.wickedsik.personalworlds.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compatibility layer for PersistentState.getOrCreate() API differences.
 * <p>
 * MC 1.20.1 uses: getOrCreate(Function fromNbt, Supplier constructor, String name)
 * MC 1.20.2+ uses: getOrCreate(Type<T> type, String name)
 * MC 1.21.x uses: Similar to 1.20.2 but may have additional changes
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
     * For 1.20.2+, this creates a Type internally and uses the newer API.
     * For 1.20.1, this calls the older getOrCreate method directly.
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
        //? if >=1.20.2 {
        PersistentState.Type<T> type = new PersistentState.Type<>(
            constructor,
            deserializer,
            null  // No DataFixTypes needed for mod data
        );
        return stateManager.getOrCreate(type, name);
        //?} else {
        /*return stateManager.getOrCreate(deserializer, constructor, name);*/
        //? }
    }
}
