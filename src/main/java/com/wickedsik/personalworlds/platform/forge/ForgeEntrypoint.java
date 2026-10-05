package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.platform.Platform;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge entrypoint (META-INF/mods.toml). */
@Mod(PersonalWorldsMod.MOD_ID)
public class ForgeEntrypoint {

    public ForgeEntrypoint() {
        Platform.install(new ForgePlatform(FMLJavaModLoadingContext.get().getModEventBus()));
        PersonalWorldsMod.init();
    }
}
