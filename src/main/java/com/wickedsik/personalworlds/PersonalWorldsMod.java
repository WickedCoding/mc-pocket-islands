package com.wickedsik.personalworlds;

import com.wickedsik.personalworlds.command.ModCommands;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.generator.ModChunkGenerators;
import com.wickedsik.personalworlds.event.ModEventHandlers;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-neutral mod initialization. Each loader's entrypoint in {@code platform/<loader>/}
 * installs its {@link com.wickedsik.personalworlds.platform.Platform} and then calls {@link #init()}.
 */
public final class PersonalWorldsMod {
    public static final String MOD_ID = "personalworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger("PocketIslands");

    private PersonalWorldsMod() {
    }

    public static void init() {
        LOGGER.info("Pocket Islands initializing...");

        // Load configuration FIRST (other components may depend on it)
        ModConfig.load();

        // Register blocks and items (before anything that might reference them)
        ModBlocks.register();
        ModItems.register();

        // Register chunk generators (before dimensions can be created)
        ModChunkGenerators.register();

        // Register event handlers (includes portal activation callback)
        ModEventHandlers.register();

        // Register commands
        ModCommands.register();

        LOGGER.info("Pocket Islands initialized!");
    }
}
