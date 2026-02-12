package com.wickedsik.personalworlds.dimension.generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

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
    public com.mojang.serialization.MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC.fieldOf("void_island");
    }
    *///?} else {
    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
    //?}

    // ==================== CORE GENERATION METHODS ====================

    /**
     * Main terrain generation. For void world, we only place blocks in island chunks.
     */
    //? if >=1.21 {
    /*@Override
    public CompletableFuture<Chunk> populateNoise(
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        // Only generate island in the designated chunk range
        if (isIslandChunk(chunkX, chunkZ)) {
            generateIslandSection(chunk);
        }
        // All other chunks remain empty (void)

        return CompletableFuture.completedFuture(chunk);
    }
    *///?} else {
    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor,
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        // Only generate island in the designated chunk range
        if (isIslandChunk(chunkX, chunkZ)) {
            generateIslandSection(chunk);
        }
        // All other chunks remain empty (void)

        return CompletableFuture.completedFuture(chunk);
    }
    //?}

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
    private void generateIslandSection(Chunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Generate layers top-to-bottom
                for (int layerIdx = 0; layerIdx < islandLayers.length; layerIdx++) {
                    BlockPos pos = new BlockPos(
                        chunk.getPos().getStartX() + x,
                        ISLAND_Y - layerIdx,  // Y=64, 63, 62, 61, 60
                        chunk.getPos().getStartZ() + z
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

    @Override
    public void buildSurface(
            ChunkRegion region,
            StructureAccessor structures,
            NoiseConfig noiseConfig,
            Chunk chunk
    ) {
        // No surface generation for void world
    }

    //? if >=1.21 {
    /*@Override
    public void carve(
            ChunkRegion chunkRegion,
            long seed,
            NoiseConfig noiseConfig,
            BiomeAccess biomeAccess,
            StructureAccessor structureAccessor,
            Chunk chunk
    ) {
        // No carving for void world
    }
    *///?} else {
    @Override
    public void carve(
            ChunkRegion chunkRegion,
            long seed,
            NoiseConfig noiseConfig,
            BiomeAccess biomeAccess,
            StructureAccessor structureAccessor,
            Chunk chunk,
            GenerationStep.Carver carverStep
    ) {
        // No carving for void world
    }
    //?}

    @Override
    public void populateEntities(ChunkRegion region) {
        // No natural entity spawning
    }

    // ==================== HEIGHT SAMPLING ====================

    /**
     * Returns the height at the given position for heightmap calculations.
     * For island chunks, return Y=65 (one above the top layer).
     * For void chunks, return minimum Y.
     */
    @Override
    public int getHeight(
            int x,
            int z,
            Heightmap.Type heightmap,
            HeightLimitView world,
            NoiseConfig noiseConfig
    ) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (isIslandChunk(chunkX, chunkZ)) {
            // Height is one above the top layer (Y=64 -> height Y=65)
            return ISLAND_Y + 1;
        }
        return world.getBottomY();
    }

    /**
     * Returns a vertical sample of blocks at the given position.
     * Includes all island layers for proper heightmap calculations.
     */
    @Override
    public VerticalBlockSample getColumnSample(
            int x,
            int z,
            HeightLimitView world,
            NoiseConfig noiseConfig
    ) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        int height = world.getHeight();
        int bottomY = world.getBottomY();
        BlockState[] states = new BlockState[height];

        // Fill with air by default
        for (int i = 0; i < height; i++) {
            states[i] = Blocks.AIR.getDefaultState();
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

        return new VerticalBlockSample(bottomY, states);
    }

    // ==================== WORLD DIMENSIONS ====================

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    // ==================== DEBUG ====================

    //? if >=1.21 {
    /*@Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        text.add("VoidIsland Generator");
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        text.add("Island chunk: " + isIslandChunk(chunkX, chunkZ));
    }
    *///?} else {
    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        text.add("VoidIsland Generator");
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        text.add("Island chunk: " + isIslandChunk(chunkX, chunkZ));
    }
    //?}
}
