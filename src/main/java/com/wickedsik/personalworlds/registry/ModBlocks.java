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
     * Cached frame blocks for all portal types.
     * Lazily loaded from config, cleared on config reload.
     */
    private static Block[] cachedFrameBlocks = null;

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
     * Get the block used for portal frames for a specific portal type.
     * Reads from config on first access, with fallback to nether bricks.
     *
     * @param portalTypeIndex Index into ModConfig.portalTypes array
     * @return The frame block for this portal type
     */
    public static Block getFrameBlock(int portalTypeIndex) {
        if (cachedFrameBlocks == null) {
            var configs = ModConfig.get().portalTypes;
            cachedFrameBlocks = new Block[configs.size()];

            for (int i = 0; i < configs.size(); i++) {
                String blockId = configs.get(i).frameBlock;
                Identifier id = Identifier.tryParse(blockId);
                Block block = id != null ? Registries.BLOCK.get(id) : Blocks.AIR;

                // Validate the block exists (get() returns AIR for unknown IDs)
                if (block == Blocks.AIR && !blockId.equals("minecraft:air")) {
                    PersonalWorldsMod.LOGGER.warn("Invalid frame block '{}' for portal type {}, using nether_bricks",
                        blockId, i);
                    block = Blocks.NETHER_BRICKS;
                }

                cachedFrameBlocks[i] = block;
                PersonalWorldsMod.LOGGER.debug("Portal type {} frame block set to: {}",
                    i, Registries.BLOCK.getId(block));
            }
        }

        // Bounds check with clamping
        if (portalTypeIndex < 0 || portalTypeIndex >= cachedFrameBlocks.length) {
            PersonalWorldsMod.LOGGER.warn("Portal type index {} out of bounds (0-{}), using 0",
                portalTypeIndex, cachedFrameBlocks.length - 1);
            return cachedFrameBlocks[0];
        }

        return cachedFrameBlocks[portalTypeIndex];
    }

    /**
     * Clear the cached frame blocks.
     * Called when configuration is reloaded.
     */
    public static void clearCache() {
        cachedFrameBlocks = null;
        PersonalWorldsMod.LOGGER.debug("Block cache cleared");
    }
}
