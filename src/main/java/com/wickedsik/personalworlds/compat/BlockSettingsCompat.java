package com.wickedsik.personalworlds.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
//? if >=1.21.2 {
/*import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
*///?}

/**
 * Compatibility layer for Block settings creation.
 * <p>
 * MC 1.20.x uses: BlockBehaviour.Properties.of()
 * MC 1.21.2+ uses: BlockBehaviour.Properties.of().setId(key) - REQUIRED
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
    public static BlockBehaviour.Properties create(ResourceLocation id) {
        return BlockBehaviour.Properties.of();
    }
    //?}
}
