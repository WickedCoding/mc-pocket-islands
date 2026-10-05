package com.wickedsik.personalworlds.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Registry access that tolerates deferred registration. Fabric registers at once;
 * Forge/NeoForge freeze vanilla registries and register in a later event, so the
 * value may only be read through the returned supplier, after registration.
 */
public interface PlatformRegistration {

    /**
     * Register a value built by {@code factory}.
     *
     * @return a supplier for the registered value; it must not be called before the
     *         loader has run registration
     */
    <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<? extends T> factory);
}
