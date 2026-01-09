package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PersonalPortalBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registers all blocks for the PersonalWorlds mod.
 */
public class ModBlocks {

    /**
     * The personal portal block - similar to nether portal properties.
     * Non-collidable, emits light, unbreakable by hand.
     */
    public static final Block PERSONAL_PORTAL = new PersonalPortalBlock(
        FabricBlockSettings.create()
            .mapColor(MapColor.CYAN)
            .noCollision()
            .strength(-1.0F)
            .sounds(BlockSoundGroup.GLASS)
            .luminance(state -> 11)
            .dropsNothing()
    );

    /**
     * Cached frame block reference.
     * Lazily loaded from config, cleared on config reload.
     */
    private static Block cachedFrameBlock = null;

    /**
     * Register all mod blocks.
     * Must be called during mod initialization BEFORE chunk generators.
     */
    public static void register() {
        Registry.register(
            Registries.BLOCK,
            new Identifier(PersonalWorldsMod.MOD_ID, "personal_portal"),
            PERSONAL_PORTAL
        );

        PersonalWorldsMod.LOGGER.info("Registered blocks");
    }

    /**
     * Get the block used for portal frames.
     * Reads from config on first access, with fallback to nether bricks.
     *
     * @return The frame block
     */
    public static Block getFrameBlock() {
        if (cachedFrameBlock == null) {
            String blockId = ModConfig.get().frameBlock;
            Identifier id = new Identifier(blockId);
            cachedFrameBlock = Registries.BLOCK.get(id);

            // Validate the block exists (get() returns AIR for unknown IDs)
            if (cachedFrameBlock == Blocks.AIR && !blockId.equals("minecraft:air")) {
                PersonalWorldsMod.LOGGER.warn("Invalid frame block '{}', using nether_bricks", blockId);
                cachedFrameBlock = Blocks.NETHER_BRICKS;
            }

            PersonalWorldsMod.LOGGER.debug("Frame block set to: {}", Registries.BLOCK.getId(cachedFrameBlock));
        }
        return cachedFrameBlock;
    }

    /**
     * Clear the cached frame block.
     * Called when configuration is reloaded.
     */
    public static void clearCache() {
        cachedFrameBlock = null;
        PersonalWorldsMod.LOGGER.debug("Block cache cleared");
    }
}
