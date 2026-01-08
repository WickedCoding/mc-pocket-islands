package com.wickedsik.personalworlds.dimension.generator;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModChunkGenerators {

    public static final Identifier VOID_ISLAND_ID = new Identifier(
        PersonalWorldsMod.MOD_ID,
        "void_island"
    );

    /**
     * Register all custom chunk generators.
     * Must be called during mod initialization BEFORE any dimensions are created.
     */
    public static void register() {
        Registry.register(
            Registries.CHUNK_GENERATOR,
            VOID_ISLAND_ID,
            VoidIslandChunkGenerator.CODEC
        );

        PersonalWorldsMod.LOGGER.info("Registered chunk generators");
    }
}
