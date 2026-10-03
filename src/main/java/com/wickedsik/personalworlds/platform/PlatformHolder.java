package com.wickedsik.personalworlds.platform;

import java.util.Objects;

/** Backing field for {@link Platform#install} and {@link Platform#get}. */
final class PlatformHolder {

    private static Platform instance;

    private PlatformHolder() {
    }

    static void install(Platform platform) {
        Objects.requireNonNull(platform, "platform");
        if (instance != null) {
            throw new IllegalStateException("Platform already installed: " + instance.getClass().getName());
        }
        instance = platform;
    }

    static Platform get() {
        if (instance == null) {
            throw new IllegalStateException("No platform installed; the loader entrypoint must call Platform.install() first");
        }
        return instance;
    }

    /** Test hook: forget the installed platform. */
    static void reset() {
        instance = null;
    }
}
