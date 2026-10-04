package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraftforge.fml.common.Mod;

/** Forge entrypoint (META-INF/mods.toml). */
@Mod(PersonalWorldsMod.MOD_ID)
public class ForgeEntrypoint {

    public ForgeEntrypoint() {
        PersonalWorldsMod.LOGGER.info("Pocket Islands Forge entrypoint loaded");
    }
}
