package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.platform.RuntimeDimension;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
import com.wickedsik.personalworlds.platform.fabric.mixin.FantasyAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

/** Runtime dimensions through Fantasy persistent worlds. */
final class FantasyDimensions implements RuntimeDimensions {

    @Override
    public RuntimeDimension open(MinecraftServer server, ResourceKey<Level> key, DimensionSpec spec) {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
            .setDimensionType(spec.dimensionType())
            .setSeed(spec.seed())
            .setDifficulty(server.getWorldData().getDifficulty())
            .setShouldTickTime(true)
            .setTimeOfDay(server.overworld().getDayTime())
            .setGenerator(spec.generator());

        // Game rules are not set here: DimensionGameRules + mixins supply them on every loader

        Fantasy fantasy = Fantasy.get(server);

        // A world unloaded moments ago stays registered until its chunks are gone, and
        // getOrOpenPersistentWorld hands it back still queued for unloading (fixed in Fantasy 0.7):
        // Fantasy would move every player in it to overworld spawn on the next tick
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            ((FantasyAccessor) (Object) fantasy).pocketislands$getUnloadingQueue().remove(existing);
        }

        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(IdentifierCompat.fromKey(key), config);
        return new FantasyDimension(handle);
    }

    private record FantasyDimension(RuntimeWorldHandle handle) implements RuntimeDimension {

        @Override
        public ServerLevel level() {
            return handle.asWorld();
        }

        @Override
        public void unload() {
            handle.unload();
        }

        // Fantasy ejects players, waits for chunks to unload, saves, then deletes the folder
        @Override
        public void delete() {
            handle.delete();
        }
    }
}
