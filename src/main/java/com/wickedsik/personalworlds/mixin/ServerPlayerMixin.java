package com.wickedsik.personalworlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21 {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Death drops read {@code keepInventory} in the level where the player died, but
 * {@code restoreFrom} reads it in the respawn level. With per-dimension rules those
 * differ, and a pocket death with pocket {@code keepInventory=true} and overworld
 * {@code false} would neither drop nor keep the items. Read the rule in the level the
 * old player died in, so both decisions agree.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    //? if >=1.21 {
    /*@WrapOperation(
        method = "restoreFrom",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getGameRules()Lnet/minecraft/world/level/gamerules/GameRules;"
        )
    )
    private GameRules pocketislands$deathLevelGameRules(ServerLevel respawnLevel, Operation<GameRules> original,
                                                         @Local(argsOnly = true) ServerPlayer oldPlayer) {
        return original.call(oldPlayer.level());
    }
    *///?} else {
    @WrapOperation(
        method = "restoreFrom",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getGameRules()Lnet/minecraft/world/level/GameRules;"
        )
    )
    private GameRules pocketislands$deathLevelGameRules(Level respawnLevel, Operation<GameRules> original,
                                                         @Local(argsOnly = true) ServerPlayer oldPlayer) {
        return original.call(oldPlayer.level());
    }
    //?}
}
