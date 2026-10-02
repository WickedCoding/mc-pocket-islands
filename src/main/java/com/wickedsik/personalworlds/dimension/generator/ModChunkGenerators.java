package com.wickedsik.personalworlds.dimension.generator;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
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
        Registry.register(
            BuiltInRegistries.CHUNK_GENERATOR,
            VOID_ISLAND_ID,
            VoidIslandChunkGenerator.CODEC.fieldOf("void_island")
        );
        *///?} else {
        Registry.register(
            BuiltInRegistries.CHUNK_GENERATOR,
            VOID_ISLAND_ID,
            VoidIslandChunkGenerator.CODEC
        );
        //?}

        PersonalWorldsMod.LOGGER.info("Registered chunk generators");
    }
}
