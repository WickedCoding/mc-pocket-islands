package com.wickedsik.personalworlds.platform.fabric;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.platform.Platform;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint (fabric.mod.json "main"). */
public class FabricEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatform());
        PersonalWorldsMod.init();
    }
}
