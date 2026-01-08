package com.wickedsik.personalworlds;

import com.wickedsik.personalworlds.command.TestCommands;
import com.wickedsik.personalworlds.event.ModEventHandlers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonalWorldsMod implements ModInitializer {
    public static final String MOD_ID = "personalworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Personal Worlds initializing...");

        // Register event handlers
        ModEventHandlers.register();

        // Register test commands (temporary for Phase 1)
        TestCommands.register();

        LOGGER.info("Personal Worlds initialized!");
    }
}
