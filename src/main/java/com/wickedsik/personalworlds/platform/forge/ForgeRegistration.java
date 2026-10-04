package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.platform.PlatformRegistration;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Forge freezes vanilla registries before mods construct, so values go through one
 * {@link DeferredRegister} per registry, attached to the mod bus on first use.
 */
final class ForgeRegistration implements PlatformRegistration {

    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    ForgeRegistration(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    public <T> Supplier<T> register(Registry<T> registry, ResourceLocation id, Supplier<? extends T> factory) {
        if (!id.getNamespace().equals(PersonalWorldsMod.MOD_ID)) {
            throw new IllegalArgumentException("Deferred registers only accept " + PersonalWorldsMod.MOD_ID + " ids: " + id);
        }
        return registerFor(registry).register(id.getPath(), factory);
    }

    @SuppressWarnings("unchecked")
    private <T> DeferredRegister<T> registerFor(Registry<T> registry) {
        return (DeferredRegister<T>) registers.computeIfAbsent(registry.key(), key -> {
            DeferredRegister<T> register = DeferredRegister.create(registry.key(), PersonalWorldsMod.MOD_ID);
            register.register(modBus);
            return register;
        });
    }
}
