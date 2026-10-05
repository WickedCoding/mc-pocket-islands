package com.wickedsik.personalworlds.gametest.platform.neoforge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.gametest.harness.RestartHarness;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hooks the restart harness into server events when a harness phase is requested. */
@EventBusSubscriber(modid = PersonalWorldsMod.MOD_ID)
public final class NeoForgeHarness {

    private NeoForgeHarness() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (RestartHarness.enabled()) {
            RestartHarness.onServerStarted(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RestartHarness.enabled()) {
            RestartHarness.onServerTick(event.getServer());
        }
    }
}
