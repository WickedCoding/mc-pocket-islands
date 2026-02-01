package com.wickedsik.personalworlds.compat;

//? if >=1.21 {
import net.minecraft.world.rule.GameRules;
//?} else {
/*import net.minecraft.world.GameRules;
*///?}
import xyz.nucleoid.fantasy.RuntimeWorldConfig;

/**
 * Compatibility layer for GameRules access.
 * <p>
 * MC 1.20.x uses: net.minecraft.world.GameRules
 * MC 1.21.x uses: net.minecraft.world.rule.GameRules (package moved)
 * <p>
 * This class centralizes GameRules access to simplify version migration.
 */
public final class GameRulesCompat {

    private GameRulesCompat() {
        // Utility class
    }

    /**
     * Disable mob spawning on a RuntimeWorldConfig.
     *
     * @param config The Fantasy RuntimeWorldConfig to modify
     */
    public static void disableMobSpawning(RuntimeWorldConfig config) {
        //? if >=1.21 {
        config.setGameRule(GameRules.DO_MOB_SPAWNING, false);
        //?} else {
        /*config.setGameRule(GameRules.DO_MOB_SPAWNING, false);
        *///?}
    }
}
