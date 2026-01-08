package com.wickedsik.personalworlds.event;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class ModEventHandlers {

    private static int tickCounter = 0;
    private static final int UNLOAD_CHECK_INTERVAL = 600; // 30 seconds

    public static void register() {
        // Server started - restore all dimensions
        ServerLifecycleEvents.SERVER_STARTED.register(ModEventHandlers::onServerStarted);

        // Server stopping - cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(ModEventHandlers::onServerStopping);

        // Periodic tick for unloading empty dimensions
        ServerTickEvents.END_SERVER_TICK.register(ModEventHandlers::onServerTick);

        PersonalWorldsMod.LOGGER.info("Event handlers registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server started - restoring player dimensions");
        DimensionRegistry.get(server).restoreAllDimensions(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server stopping - unloading all dimensions");
        DimensionManager.unloadAll();
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= UNLOAD_CHECK_INTERVAL) {
            tickCounter = 0;
            DimensionManager.unloadEmptyDimensions();
        }
    }
}
