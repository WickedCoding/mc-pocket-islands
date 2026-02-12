package com.wickedsik.personalworlds.compat;

import net.minecraft.util.Identifier;

//? if >=1.21.2 {
/*import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
*///?} else if >=1.21 {
/*import net.minecraft.block.AbstractBlock;
*///?} else {
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
//?}

/**
 * Compatibility layer for Block settings creation.
 * <p>
 * MC 1.20.x uses: FabricBlockSettings.create()
 * MC 1.21.0-1.21.1 uses: AbstractBlock.Settings.create()
 * MC 1.21.2+ uses: AbstractBlock.Settings.create().registryKey(key) - REQUIRED
 * <p>
 * This class centralizes block settings creation to simplify version migration.
 */
public final class BlockSettingsCompat {

    private BlockSettingsCompat() {
        // Utility class
    }

    /**
     * Create a new block settings instance with registry key (required for 1.21.2+).
     *
     * @param id The block identifier for registry key creation
     * @return A new block settings builder
     */
    //? if >=1.21.2 {
    /*public static AbstractBlock.Settings create(Identifier id) {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
        return AbstractBlock.Settings.create().registryKey(key);
    }
    *///?} else if >=1.21 {
    /*public static AbstractBlock.Settings create(Identifier id) {
        return AbstractBlock.Settings.create();
    }*/
    //?} else {
    public static FabricBlockSettings create(Identifier id) {
        return FabricBlockSettings.create();
    }
    //?}

    /**
     * Create a new block settings instance without registry key.
     * @deprecated Use create(Identifier) instead for 1.21.2+ compatibility.
     */
    //? if >=1.21 {
    /*@Deprecated
    public static AbstractBlock.Settings create() {
        return AbstractBlock.Settings.create();
    }
    *///?} else {
    @Deprecated
    public static FabricBlockSettings create() {
        return FabricBlockSettings.create();
    }
    //?}
}
