package com.wickedsik.personalworlds;

import com.wickedsik.personalworlds.command.TestCommands;
import com.wickedsik.personalworlds.dimension.generator.ModChunkGenerators;
import com.wickedsik.personalworlds.event.ModEventHandlers;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonalWorldsMod implements ModInitializer {
    public static final String MOD_ID = "personalworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Personal Worlds initializing...");

        // Register blocks and items FIRST (before anything that might reference them)
        ModBlocks.register();
        ModItems.register();

        // Register chunk generators (before dimensions can be created)
        ModChunkGenerators.register();

        // Register event handlers (includes portal activation callback)
        ModEventHandlers.register();

        // Register test commands (temporary for development)
        TestCommands.register();

        LOGGER.info("Personal Worlds initialized!");
    }
}
