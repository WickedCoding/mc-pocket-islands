package com.wickedsik.personalworlds.mixin;

//? if <1.21 {
import com.wickedsik.personalworlds.dimension.gamerules.DimensionGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.20.x: {@code ServerLevel} inherits {@code getGameRules()} from {@code Level}, which
 * returns the overworld's rules. Return the pocket dimension's own rules instead.
 * <p>
 * 1.21.x: {@code ServerLevel} declares {@code getGameRules()} itself, so
 * {@link ServerLevelMixin} handles it and this mixin is empty.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    //? if <1.21 {
    @Inject(method = "getGameRules", at = @At("HEAD"), cancellable = true)
    private void pocketislands$dimensionGameRules(CallbackInfoReturnable<GameRules> cir) {
        if ((Object) this instanceof ServerLevel level) {
            GameRules rules = DimensionGameRules.get(level.dimension());
            if (rules != null) {
                cir.setReturnValue(rules);
            }
        }
    }
    //?}
}
