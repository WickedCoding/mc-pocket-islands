package com.wickedsik.personalworlds.gametest.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.gametest.harness.RestartHarness;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the restart harness into server events when a harness phase is requested. */
@Mod.EventBusSubscriber(modid = PersonalWorldsMod.MOD_ID)
public final class ForgeHarness {

    private ForgeHarness() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (RestartHarness.enabled()) {
            RestartHarness.onServerStarted(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (RestartHarness.enabled() && event.phase == TickEvent.Phase.END) {
            RestartHarness.onServerTick(event.getServer());
        }
    }
}
