package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.platform.Platform;
import com.wickedsik.personalworlds.platform.PlatformEvents;
import com.wickedsik.personalworlds.platform.PlatformPermissions;
import com.wickedsik.personalworlds.platform.PlatformRegistration;
import com.wickedsik.personalworlds.platform.PlatformTeleport;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgePlatform implements Platform {

    private final PlatformEvents events = new ForgeEvents();
    private final PlatformRegistration registration;
    private final RuntimeDimensions dimensions = new InfiniverseDimensions();
    private final PlatformTeleport teleport = new ForgeTeleport();
    private final PlatformPermissions permissions = new ForgePermissions();

    /** @param modBus the mod's event bus, where deferred registers attach */
    public ForgePlatform(IEventBus modBus) {
        this.registration = new ForgeRegistration(modBus);
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
