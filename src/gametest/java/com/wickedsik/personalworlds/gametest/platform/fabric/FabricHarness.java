package com.wickedsik.personalworlds.gametest.platform.fabric;

import com.wickedsik.personalworlds.gametest.harness.RestartHarness;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Hooks the restart harness into server events when a harness phase is requested. */
public class FabricHarness implements ModInitializer {

    @Override
    public void onInitialize() {
        if (!RestartHarness.enabled()) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(RestartHarness::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(RestartHarness::onServerTick);
    }
}
