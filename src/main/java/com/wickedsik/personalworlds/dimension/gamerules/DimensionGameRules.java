package com.wickedsik.personalworlds.dimension.gamerules;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
//? if >=1.21 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game rules for pocket dimensions, keyed by dimension. Vanilla gives every level the
 * overworld's rules; the mixins in {@code com.wickedsik.personalworlds.mixin} return these
 * instead for registered dimensions.
 * <p>
 * Rules are registered before the level is opened, so the very first tick already uses
 * them. Entries stay until the server stops: an unload or delete may finish ticks later,
 * and the level must keep its rules until then. Reopening a dimension overwrites its entry.
 */
public final class DimensionGameRules {

    // Read from any thread that asks a level for its rules
    private static final Map<ResourceKey<Level>, GameRules> RULES = new ConcurrentHashMap<>();

    private DimensionGameRules() {
    }

    public static void register(ResourceKey<Level> dimension, GameRules rules) {
        RULES.put(dimension, rules);
    }

    /** The rules for {@code dimension}, or null if it uses vanilla rules. */
    public static @Nullable GameRules get(ResourceKey<Level> dimension) {
        return RULES.get(dimension);
    }

    public static void clear() {
        RULES.clear();
    }
}
