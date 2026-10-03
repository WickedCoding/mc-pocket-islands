package com.wickedsik.personalworlds.platform;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Creates and opens persistent dimensions at runtime (Fantasy on Fabric).
 */
public interface RuntimeDimensions {

    /**
     * Open the persistent dimension {@code key}, creating it on first use. The key is
     * known before the level exists, so per-dimension state (such as game rules) can
     * be registered first.
     */
    RuntimeDimension open(MinecraftServer server, ResourceKey<Level> key, DimensionSpec spec);

    /** What a runtime dimension is built from. */
    record DimensionSpec(ResourceKey<DimensionType> dimensionType, ChunkGenerator generator, long seed) {
    }
}
