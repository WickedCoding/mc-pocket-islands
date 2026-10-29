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
//? if >=26.3 {
/*import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.WorldClocks;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
*///?} else {
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
//?}

/** Runtime dimensions through Fantasy persistent worlds. */
final class FantasyDimensions implements RuntimeDimensions {

    @Override
    public RuntimeDimension open(MinecraftServer server, ResourceKey<Level> key, DimensionSpec spec) {
        //? if >=26.3 {
        /*// Fantasy 0.8 renamed World to Level; time of day is the overworld clock's state
        RuntimeLevelConfig config = new RuntimeLevelConfig()
            .setDimensionType(spec.dimensionType())
            .setSeed(spec.seed())
            .setDifficulty(server.getWorldData().getDifficulty())
            .setClockTime(WorldClocks.OVERWORLD, new ClockState(server.overworld().getOverworldClockTime(), 0f, 1f, false))
            // Mirroring with no game rules set leaves RuntimeLevel without its own rule set, so
            // getGameRules falls through to ServerLevel#getGameRules and the mixin supplies ours.
            // Not mirroring gives the level a fresh default rule set that the mixin never sees, and
            // any Fantasy rule (setShouldTickTime sets advance_time) makes it wrap the overworld's.
            .setMirrorOverworldGameRules(true)
            .setGenerator(spec.generator());
        *///?} else {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
            .setDimensionType(spec.dimensionType())
            .setSeed(spec.seed())
            .setDifficulty(server.getWorldData().getDifficulty())
            .setShouldTickTime(true)
            .setTimeOfDay(server.overworld().getDayTime())
            .setGenerator(spec.generator());
        //?}

        // Game rules are not set here: DimensionGameRules + mixins supply them on every loader

        Fantasy fantasy = Fantasy.get(server);

        // A world unloaded moments ago stays registered until its chunks are gone, and
        // getOrOpenPersistentWorld hands it back still queued for unloading (fixed in Fantasy 0.7):
        // Fantasy would move every player in it to overworld spawn on the next tick
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            ((FantasyAccessor) (Object) fantasy).pocketislands$getUnloadingQueue().remove(existing);
        }

        //? if >=26.3 {
        /*RuntimeLevelHandle handle = fantasy.getOrOpenPersistentLevel(IdentifierCompat.fromKey(key), config);
        *///?} else {
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(IdentifierCompat.fromKey(key), config);
        //?}
        return new FantasyDimension(handle);
    }

    //? if >=26.3 {
    /*private record FantasyDimension(RuntimeLevelHandle handle) implements RuntimeDimension {

        @Override
        public ServerLevel level() {
            return handle.asLevel();
        }
    *///?} else {
    private record FantasyDimension(RuntimeWorldHandle handle) implements RuntimeDimension {

        @Override
        public ServerLevel level() {
            return handle.asWorld();
        }
    //?}

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
