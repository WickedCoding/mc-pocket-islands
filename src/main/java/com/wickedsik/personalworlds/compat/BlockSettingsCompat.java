package com.wickedsik.personalworlds.compat;

//? if >=1.21 {
import net.minecraft.block.AbstractBlock;
//?} else {
/*import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
*///?}

/**
 * Compatibility layer for Block settings creation.
 * <p>
 * MC 1.20.x uses: FabricBlockSettings.create()
 * MC 1.21.x uses: AbstractBlock.Settings.create() (FabricBlockSettings was removed)
 * <p>
 * This class centralizes block settings creation to simplify version migration.
 */
public final class BlockSettingsCompat {

    private BlockSettingsCompat() {
        // Utility class
    }

    /**
     * Create a new block settings instance.
     * Returns FabricBlockSettings on 1.20.x, AbstractBlock.Settings on 1.21.x.
     * Both support the same chaining methods (mapColor, noCollision, strength, etc.)
     *
     * @return A new block settings builder
     */
    //? if >=1.21 {
    public static AbstractBlock.Settings create() {
        return AbstractBlock.Settings.create();
    }
    //?} else {
    /*public static FabricBlockSettings create() {
        return FabricBlockSettings.create();
    }
    *///?}
}
