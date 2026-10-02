package com.wickedsik.personalworlds.compat;

import net.minecraft.resources.ResourceLocation;

//? if >=1.21.2 {
/*import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
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
    /*public static BlockBehaviour.Properties create(ResourceLocation id) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return BlockBehaviour.Properties.of().setId(key);
    }
    *///?} else {
    public static FabricBlockSettings create(ResourceLocation id) {
        return FabricBlockSettings.create();
    }
    //?}

    /**
     * Create a new block settings instance without registry key.
     * @deprecated Use create(ResourceLocation) instead for 1.21.2+ compatibility.
     */
    //? if >=1.21 {
    /*@Deprecated
    public static BlockBehaviour.Properties create() {
        return BlockBehaviour.Properties.of();
    }
    *///?} else {
    @Deprecated
    public static FabricBlockSettings create() {
        return FabricBlockSettings.create();
    }
    //?}
}
