package com.wickedsik.personalworlds.platform;

import net.minecraft.server.level.ServerLevel;

/** An open runtime dimension. */
public interface RuntimeDimension {

    ServerLevel level();

    /** Save and unload the level. Files stay on disk. */
    void unload();

    /**
     * Unload the level and delete its folder. May finish on a later tick, after
     * players are moved out and chunks are unloaded.
     */
    void delete();
}
