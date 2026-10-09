package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionMetadataFile;
import com.wickedsik.personalworlds.platform.RuntimeDimension;
import com.wickedsik.personalworlds.platform.RuntimeDimensions;
import commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime dimensions through Infiniverse.
 * <p>
 * Infiniverse unregisters a level at the end of a later tick: it moves players inside
 * to their respawn point, saves the level and drops it from the server and from the
 * {@code LevelStem} registry (so it is not recreated on the next start). It does not
 * close the level, and the server no longer knows it, so this class closes it once it
 * is gone, and deletes the folder afterwards when asked to. Infiniverse has no delete.
 * <p>
 * Infiniverse cannot take a mark back, and {@code getOrCreateLevel} hands out a marked level
 * as if nothing happened. So releases wait in {@code requested} and are only marked at the
 * start of the tick-end dispatch, before Infiniverse unregisters in the same dispatch: no
 * player can enter in between. {@link #open} withdraws a request that is still waiting, and
 * a level with players inside is never marked.
 * <p>
 * Levels are built with the overworld seed and {@code DerivedLevelData}; the spec's
 * seed is not used, and day time follows the overworld.
 */
final class InfiniverseDimensions implements RuntimeDimensions {

    // Releases not yet passed to Infiniverse; open() withdraws them
    private final Map<ResourceKey<Level>, Release> requested = new LinkedHashMap<>();
    // Levels marked for unregistration, waiting to be closed (and maybe deleted)
    private final Map<ResourceKey<Level>, Release> pending = new LinkedHashMap<>();

    InfiniverseDimensions() {
        // Infiniverse unregisters at NORMAL priority, so HIGHEST marks within the same dispatch
        ForgeEvents.listen(EventPriority.HIGHEST, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END && !requested.isEmpty()) {
                markRequested();
            }
        });
        ForgeEvents.listen(TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END && !pending.isEmpty()) {
                processPending(event.getServer());
            }
        });
        // Levels still registered at shutdown are closed by the server and stay in level.dat
        ForgeEvents.listen(ServerStoppedEvent.class, event -> {
            requested.clear();
            pending.clear();
        });
    }

    @Override
    public RuntimeDimension open(MinecraftServer server, ResourceKey<Level> key, DimensionSpec spec) {
        Holder<DimensionType> dimensionType = server.registryAccess()
            .registryOrThrow(Registries.DIMENSION_TYPE)
            .getHolderOrThrow(spec.dimensionType());

        // Reopened before the mark: keep the level registered
        requested.remove(key);

        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, key,
            () -> new LevelStem(dimensionType, spec.generator()));
        return new InfiniverseDimension(server, key, level);
    }

    private void release(MinecraftServer server, ResourceKey<Level> key, ServerLevel level, boolean deleteFolder) {
        requested.merge(key, new Release(server, level, deleteFolder), Release::merge);
    }

    private void markRequested() {
        Iterator<Map.Entry<ResourceKey<Level>, Release>> iterator = requested.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, Release> entry = iterator.next();
            Release release = entry.getValue();
            if (!release.level().players().isEmpty()) {
                continue; // someone got in without open(); DimensionManager reclaims the level
            }
            iterator.remove();
            InfiniverseAPI.get().markDimensionForUnregistration(release.server(), entry.getKey());
            pending.merge(entry.getKey(), release, Release::merge);
        }
    }

    private void processPending(MinecraftServer server) {
        Iterator<Map.Entry<ResourceKey<Level>, Release>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, Release> entry = iterator.next();
            ResourceKey<Level> key = entry.getKey();
            Release release = entry.getValue();

            ServerLevel current = server.getLevel(key);
            if (current == release.level()) {
                continue; // not unregistered yet
            }
            iterator.remove();

            try {
                release.level().close();
            } catch (IOException e) {
                PersonalWorldsMod.LOGGER.error("Failed to close unregistered dimension {}", key.location(), e);
            }

            if (!release.deleteFolder()) {
                continue;
            }
            if (current != null) {
                // Reopened between unregistration and now: the folder belongs to a live level
                PersonalWorldsMod.LOGGER.warn("Dimension {} was reopened before deletion; keeping its folder", key.location());
                continue;
            }

            Path folder = DimensionType.getStorageFolder(key, server.getWorldPath(LevelResource.ROOT));
            try {
                DimensionMetadataFile.deleteDirectoryRecursively(folder);
                PersonalWorldsMod.LOGGER.info("Deleted dimension folder {}", folder);
            } catch (IOException e) {
                PersonalWorldsMod.LOGGER.error("Failed to delete dimension folder {}", folder, e);
            }
        }
    }

    private record Release(MinecraftServer server, ServerLevel level, boolean deleteFolder) {

        Release merge(Release next) {
            return new Release(server, level, deleteFolder || next.deleteFolder());
        }
    }

    private final class InfiniverseDimension implements RuntimeDimension {

        private final MinecraftServer server;
        private final ResourceKey<Level> key;
        private final ServerLevel level;

        InfiniverseDimension(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
            this.server = server;
            this.key = key;
            this.level = level;
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public void unload() {
            release(server, key, level, false);
        }

        @Override
        public void delete() {
            release(server, key, level, true);
        }
    }
}
