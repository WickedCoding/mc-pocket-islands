package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
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
     * Reference to the frame block (vanilla nether bricks).
     * Using a getter allows for future configurability.
     */
    private static final Block FRAME_BLOCK = Blocks.NETHER_BRICKS;

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
     * Currently returns vanilla nether bricks.
     *
     * @return The frame block
     */
    public static Block getFrameBlock() {
        return FRAME_BLOCK;
    }
}
