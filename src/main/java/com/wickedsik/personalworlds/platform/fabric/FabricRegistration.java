package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.platform.PlatformRegistration;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/** Registers immediately; Fabric leaves vanilla registries open during mod init. */
final class FabricRegistration implements PlatformRegistration {

    @Override
    public <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<? extends T> factory) {
        T value = Registry.register(registry, id, factory.get());
        return () -> value;
    }
}
