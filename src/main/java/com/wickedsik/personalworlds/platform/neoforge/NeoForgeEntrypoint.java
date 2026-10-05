package com.wickedsik.personalworlds.platform.neoforge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint (META-INF/neoforge.mods.toml). */
@Mod(PersonalWorldsMod.MOD_ID)
public class NeoForgeEntrypoint {

    public NeoForgeEntrypoint(IEventBus modBus) {
        Platform.install(new NeoForgePlatform(modBus));
        PersonalWorldsMod.init();
    }
}
