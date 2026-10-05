package com.wickedsik.personalworlds.mixin;

//? if >=1.21 {
/*import com.wickedsik.personalworlds.dimension.gamerules.DimensionGameRules;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.WritableLevelData;
//?}
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    //? if >=1.21 {
    /*// Return the pocket dimension's own rules instead of the overworld's
    @Inject(method = "getGameRules", at = @At("HEAD"), cancellable = true)
    private void pocketislands$dimensionGameRules(CallbackInfoReturnable<GameRules> cir) {
        GameRules rules = DimensionGameRules.get(((ServerLevel) (Object) this).dimension());
        if (rules != null) {
            cir.setReturnValue(rules);
        }
    }
    *///?} else {
    // tickTime() reads doDaylightCycle from levelData directly, bypassing getGameRules();
    // without this the pocket clock ignores the pocket's doDaylightCycle
    @WrapOperation(
        method = "tickTime",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/WritableLevelData;getGameRules()Lnet/minecraft/world/level/GameRules;"
        )
    )
    private GameRules pocketislands$tickTimeGameRules(WritableLevelData levelData, Operation<GameRules> original) {
        return ((ServerLevel) (Object) this).getGameRules();
    }
    //?}
}
