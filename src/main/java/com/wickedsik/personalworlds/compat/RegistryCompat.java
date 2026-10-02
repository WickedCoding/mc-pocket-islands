package com.wickedsik.personalworlds.compat;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Compatibility layer for registry lookups.
 * <p>
 * Registry lookup by Identifier is renamed between versions under Mojang mappings,
 * so all lookups go through this class.
 */
public final class RegistryCompat {

    private RegistryCompat() {
        // Utility class
    }

    /**
     * Look up a registry entry by Identifier, returning the registry's default entry if absent.
     *
     * @param registry The registry (e.g. Registries.BLOCK)
     * @param id       The entry Identifier
     * @return The registered entry
     */
    public static <T> T get(Registry<T> registry, Identifier id) {
        return registry.get(id);
    }
}
