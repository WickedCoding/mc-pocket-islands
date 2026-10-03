package com.wickedsik.personalworlds.platform;

import net.minecraft.commands.CommandSourceStack;

/** Permission checks through the loader's permission API, if any. */
public interface PlatformPermissions {

    /**
     * @param node          permission node, e.g. {@code pocketislands.admin.delete}
     * @param fallbackLevel OP level required when no permission provider decides
     */
    boolean check(CommandSourceStack source, String node, int fallbackLevel);
}
