package com.wickedsik.personalworlds.platform.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xyz.nucleoid.fantasy.Fantasy;

import java.util.Set;

/** Fantasy's queue of worlds waiting for their chunks to unload before removal. */
@Mixin(value = Fantasy.class, remap = false)
public interface FantasyAccessor {

    @Accessor("unloadingQueue")
    Set<ServerLevel> pocketislands$getUnloadingQueue();
}
