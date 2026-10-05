package com.wickedsik.personalworlds.platform;

import java.nio.file.Path;

/**
 * Loader-specific services. Each loader's entrypoint (in {@code platform/<loader>/})
 * installs its implementation before calling {@code PersonalWorldsMod.init()}.
 * <p>
 * Code outside {@code platform/} reaches the loader only through this interface.
 */
public interface Platform {

    PlatformEvents events();

    PlatformRegistration registration();

    RuntimeDimensions dimensions();

    PlatformTeleport teleport();

    PlatformPermissions permissions();

    /** The loader's config directory. */
    Path configDir();

    /**
     * Install the loader's implementation. Called once, from the loader entrypoint.
     *
     * @throws IllegalStateException if a platform is already installed
     */
    static void install(Platform platform) {
        PlatformHolder.install(platform);
    }

    /**
     * @throws IllegalStateException if no loader entrypoint has installed a platform yet
     */
    static Platform get() {
        return PlatformHolder.get();
    }
}
