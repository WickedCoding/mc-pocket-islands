package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.compat.GameRulesCompat;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.platform.RuntimeDimension;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
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

        // Apply game rules: baseline from overworld, then config overrides
        GameRulesCompat.applyGameRules(config, server);

        RuntimeWorldHandle handle = Fantasy.get(server).getOrOpenPersistentWorld(IdentifierCompat.fromKey(key), config);
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
