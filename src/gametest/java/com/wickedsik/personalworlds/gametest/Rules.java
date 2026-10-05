package com.wickedsik.personalworlds.gametest;

import net.minecraft.server.level.ServerLevel;
//? if >=1.21 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}

/** Version-neutral reads of the rules the scenarios check. */
public final class Rules {

    private Rules() {
    }

    public static int randomTickSpeed(ServerLevel level) {
        //? if >=1.21 {
        /*return level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        *///?} else {
        return level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        //?}
    }

    public static boolean keepInventory(ServerLevel level) {
        //? if >=1.21 {
        /*return level.getGameRules().get(GameRules.KEEP_INVENTORY);
        *///?} else {
        return level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        //?}
    }
}
