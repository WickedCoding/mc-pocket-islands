package com.wickedsik.personalworlds.platform.neoforge;

import com.wickedsik.personalworlds.platform.Platform;
import com.wickedsik.personalworlds.platform.PlatformEvents;
import com.wickedsik.personalworlds.platform.PlatformPermissions;
import com.wickedsik.personalworlds.platform.PlatformRegistration;
import com.wickedsik.personalworlds.platform.PlatformTeleport;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform {

    private final PlatformEvents events = new NeoForgeEvents();
    private final PlatformRegistration registration;
    private final RuntimeDimensions dimensions = new InfiniverseDimensions();
    private final PlatformTeleport teleport = new NeoForgeTeleport();
    private final PlatformPermissions permissions = new NeoForgePermissions();

    /** @param modBus the mod's event bus, where deferred registers attach */
    public NeoForgePlatform(IEventBus modBus) {
        this.registration = new NeoForgeRegistration(modBus);
    }

    @Override
    public PlatformEvents events() {
        return events;
    }

    @Override
    public PlatformRegistration registration() {
        return registration;
    }

    @Override
    public RuntimeDimensions dimensions() {
        return dimensions;
    }

    @Override
    public PlatformTeleport teleport() {
        return teleport;
    }

    @Override
    public PlatformPermissions permissions() {
        return permissions;
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
