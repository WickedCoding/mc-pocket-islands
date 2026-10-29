package com.wickedsik.personalworlds.dimension.generator;

import com.wickedsik.personalworlds.compat.WorldCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import java.util.Set;
*///?}

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VoidIslandChunkGenerator extends ChunkGenerator {

    // ==================== CODEC ====================

    public static final Codec<VoidIslandChunkGenerator> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            BlockState.CODEC.listOf()
                .xmap(
                    list -> list.toArray(new BlockState[0]),
                    Arrays::asList
                )
                .fieldOf("island_layers")
                .forGetter(generator -> generator.islandLayers)
        ).apply(instance, VoidIslandChunkGenerator::new)
    );

    //? if >=1.21 {
    /*// One instance for the registry and codec(): the registry finds codecs by identity,
    // and saving a level stem to level.dat (Infiniverse levels) needs that lookup
    public static final com.mojang.serialization.MapCodec<VoidIslandChunkGenerator> MAP_CODEC = CODEC.fieldOf("void_island");
    *///?}

    // ==================== ISLAND CONSTANTS ====================

    // Island dimensions: 8x8 chunks = 128x128 blocks
    // Chunks -4 to +3 inclusive = 8 chunks per axis
    private static final int ISLAND_MIN_CHUNK = -4;
    private static final int ISLAND_MAX_CHUNK = 3;

    // Island Y level (top layer starts here)
    private static final int ISLAND_Y = 64;

    // Island layer materials (top-to-bottom: Y=64, 63, 62, 61, 60)
    private final BlockState[] islandLayers;

    // ==================== CONSTRUCTOR ====================

    public VoidIslandChunkGenerator(BiomeSource biomeSource, BlockState[] islandLayers) {
        super(biomeSource);
        this.islandLayers = islandLayers;
    }

    // ==================== CODEC METHOD ====================

    //? if >=1.21 {
    /*@Override
    public com.mojang.serialization.MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }
    *///?} else {
    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
    //?}

    // ==================== CORE GENERATION METHODS ====================

    /**
     * Main terrain generation. For void world, we only place blocks in island chunks.
     * 26.3 merged noise, surface and carvers into buildTerrain; before that the void
     * world fills the island here and leaves buildSurface and applyCarvers empty.
     */
    //? if >=26.3 {
    /*@Override
    public CompletableFuture<ChunkAccess> buildTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState noiseConfig,
            StructureManager structureAccessor,
            BiomeManager biomeAccess,
            WorldGenRegion region,
            Set<Holder<Biome>> possibleBiomes
    ) {
        return CompletableFuture.completedFuture(fillIsland(chunk));
    }
    *///?} else if >=1.21 {
    /*@Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState noiseConfig,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(fillIsland(chunk));
    }
    *///?} else {
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor,
            Blender blender,
            RandomState noiseConfig,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(fillIsland(chunk));
    }
    //?}

    private ChunkAccess fillIsland(ChunkAccess chunk) {
        int chunkX = WorldCompat.chunkX(chunk.getPos());
        int chunkZ = WorldCompat.chunkZ(chunk.getPos());

        // Only generate island in the designated chunk range
        if (isIslandChunk(chunkX, chunkZ)) {
            generateIslandSection(chunk);
        }
        // All other chunks remain empty (void)

        return chunk;
    }

    /**
     * Check if this chunk is part of the 8x8 island area.
     */
    private boolean isIslandChunk(int chunkX, int chunkZ) {
        return chunkX >= ISLAND_MIN_CHUNK && chunkX <= ISLAND_MAX_CHUNK
            && chunkZ >= ISLAND_MIN_CHUNK && chunkZ <= ISLAND_MAX_CHUNK;
    }

    /**
     * Generate the island platform section for this chunk.
     * Generates layers top-to-bottom: Y=64 (first layer), Y=63 (second), etc.
     * Each chunk gets a full 16x16 section of each layer.
     */
    private void generateIslandSection(ChunkAccess chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Generate layers top-to-bottom
                for (int layerIdx = 0; layerIdx < islandLayers.length; layerIdx++) {
                    BlockPos pos = new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        ISLAND_Y - layerIdx,  // Y=64, 63, 62, 61, 60
                        chunk.getPos().getMinBlockZ() + z
                    );
                    //? if >=1.21 {
                    /*chunk.setBlockState(pos, islandLayers[layerIdx], 0);
                    *///?} else {
                    chunk.setBlockState(pos, islandLayers[layerIdx], false);
                    //?}
                }
            }
        }
    }

    // ==================== SURFACE & CARVING (NO-OP) ====================

    //? if <26.3 {
    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structures,
            RandomState noiseConfig,
            ChunkAccess chunk
    ) {
        // No surface generation for void world
    }
    //?}

    //? if >=1.21 && <26.3 {
    /*@Override
    public void applyCarvers(
            WorldGenRegion chunkRegion,
            long seed,
            RandomState noiseConfig,
            BiomeManager biomeAccess,
            StructureManager structureAccessor,
            ChunkAccess chunk
    ) {
        // No carving for void world
    }
    *///?} else if <1.21 {
    @Override
    public void applyCarvers(
            WorldGenRegion chunkRegion,
            long seed,
            RandomState noiseConfig,
            BiomeManager biomeAccess,
            StructureManager structureAccessor,
            ChunkAccess chunk,
            GenerationStep.Carving carverStep
    ) {
        // No carving for void world
    }
    //?}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No natural entity spawning
    }

    // ==================== HEIGHT SAMPLING ====================

    /**
     * Returns the height at the given position for heightmap calculations.
     * For island chunks, return Y=65 (one above the top layer).
     * For void chunks, return minimum Y.
     */
    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmap,
            LevelHeightAccessor world,
            RandomState noiseConfig
    ) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (isIslandChunk(chunkX, chunkZ)) {
            // Height is one above the top layer (Y=64 -> height Y=65)
            return ISLAND_Y + 1;
        }
        return WorldCompat.getBottomY(world);
    }

    /**
     * Returns a vertical sample of blocks at the given position.
     * Includes all island layers for proper heightmap calculations.
     */
    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor world,
            RandomState noiseConfig
    ) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        int height = world.getHeight();
        int bottomY = WorldCompat.getBottomY(world);
        BlockState[] states = new BlockState[height];

        // Fill with air by default
        for (int i = 0; i < height; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }

        // Add island layers top-to-bottom if in island area
        if (isIslandChunk(chunkX, chunkZ)) {
            for (int layerIdx = 0; layerIdx < islandLayers.length; layerIdx++) {
                int y = ISLAND_Y - layerIdx;  // Y=64, 63, 62, 61, 60
                int stateIndex = y - bottomY;
                if (stateIndex >= 0 && stateIndex < height) {
                    states[stateIndex] = islandLayers[layerIdx];
                }
            }
        }

        return new NoiseColumn(bottomY, states);
    }

    // ==================== WORLD DIMENSIONS ====================

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    // ==================== DEBUG ====================

    //? if >=26.3 {
    /*@Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos, SamplerContext context) {
        text.add("VoidIsland Generator");
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        text.add("Island chunk: " + isIslandChunk(chunkX, chunkZ));
    }
    *///?} else {
    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos) {
        text.add("VoidIsland Generator");
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        text.add("Island chunk: " + isIslandChunk(chunkX, chunkZ));
    }
    //?}
}
