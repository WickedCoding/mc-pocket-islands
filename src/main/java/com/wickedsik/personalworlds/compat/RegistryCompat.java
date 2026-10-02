package com.wickedsik.personalworlds.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility layer for registry lookups.
 * <p>
 * Registry lookup by ResourceLocation is renamed between versions under Mojang mappings,
 * so all lookups go through this class.
 */
public final class RegistryCompat {

    private RegistryCompat() {
        // Utility class
    }

    /**
     * Look up a registry entry by ResourceLocation, returning the registry's default entry if absent.
     *
     * @param registry The registry (e.g. BuiltInRegistries.BLOCK)
     * @param id       The entry ResourceLocation
     * @return The registered entry
     */
    public static <T> T get(Registry<T> registry, ResourceLocation id) {
        //? if >=1.21 {
        /*return registry.getValue(id);
        *///?} else {
        return registry.get(id);
        //?}
    }
}
