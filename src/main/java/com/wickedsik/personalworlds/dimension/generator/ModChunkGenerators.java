package com.wickedsik.personalworlds.dimension.generator;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class ModChunkGenerators {

    public static final ResourceLocation VOID_ISLAND_ID = IdentifierCompat.modId("void_island");

    /**
     * Register all custom chunk generators.
     * Must be called during mod initialization BEFORE any dimensions are created.
     */
    public static void register() {
        //? if >=1.21 {
        /*// 1.21.x uses MapCodec for chunk generator registry
        Platform.get().registration().register(
            BuiltInRegistries.CHUNK_GENERATOR,
            VOID_ISLAND_ID,
            () -> VoidIslandChunkGenerator.MAP_CODEC
        );
        *///?} else {
        Platform.get().registration().register(
            BuiltInRegistries.CHUNK_GENERATOR,
            VOID_ISLAND_ID,
            () -> VoidIslandChunkGenerator.CODEC
        );
        //?}

        PersonalWorldsMod.LOGGER.info("Registered chunk generators");
    }
}
