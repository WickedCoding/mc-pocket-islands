package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.platform.Platform;
import com.wickedsik.personalworlds.platform.PlatformEvents;
import com.wickedsik.personalworlds.platform.PlatformPermissions;
import com.wickedsik.personalworlds.platform.PlatformRegistration;
import com.wickedsik.personalworlds.platform.PlatformTeleport;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricPlatform implements Platform {

    private final PlatformEvents events = new FabricEvents();
    private final PlatformRegistration registration = new FabricRegistration();
    private final RuntimeDimensions dimensions = new FantasyDimensions();
    private final PlatformTeleport teleport = new FabricTeleport();
    private final PlatformPermissions permissions = new FabricPermissions();

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
        return FabricLoader.getInstance().getConfigDir();
    }
}
