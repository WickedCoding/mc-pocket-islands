package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.BlockSettingsCompat;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.RegistryCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.portal.PersonalPortalBlock;
import com.wickedsik.personalworlds.portal.PortalColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers all blocks for the PersonalWorlds mod.
 */
public class ModBlocks {

    private static final ResourceLocation PERSONAL_PORTAL_ID = IdentifierCompat.modId("personal_portal");

    /**
     * The personal portal block - similar to nether portal properties.
     * Non-collidable, emits light, unbreakable by hand.
     */
    public static final Block PERSONAL_PORTAL = new PersonalPortalBlock(
        BlockSettingsCompat.create(PERSONAL_PORTAL_ID)
            .mapColor(MapColor.COLOR_CYAN)
            //? if >=1.21 {
            /*.noCollision()
            *///?} else {
            .noCollission()
            //?}
            .strength(-1.0F)
            .sound(SoundType.GLASS)
            .lightLevel(state -> 11)
            .noLootTable()
    );

    /**
     * Cached frame blocks for all portal types.
     * Lazily loaded from config, cleared on config reload.
     */
    private static Block[] cachedFrameBlocks = null;

    /**
     * Cached portal colors for all portal types.
     * Lazily loaded from config, cleared on config reload.
     */
    private static PortalColor[] cachedPortalColors = null;

    /**
     * Register all mod blocks.
     * Must be called during mod initialization BEFORE chunk generators.
     */
    public static void register() {
        Registry.register(
            BuiltInRegistries.BLOCK,
            PERSONAL_PORTAL_ID,
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
                ResourceLocation id = IdentifierCompat.tryParse(blockId);
                Block block = id != null ? RegistryCompat.get(BuiltInRegistries.BLOCK, id) : Blocks.AIR;

                // Validate the block exists (get() returns AIR for unknown IDs)
                if (block == Blocks.AIR && !blockId.equals("minecraft:air")) {
                    PersonalWorldsMod.LOGGER.warn("Invalid frame block '{}' for portal type {}, using nether_bricks",
                        blockId, i);
                    block = Blocks.NETHER_BRICKS;
                }

                cachedFrameBlocks[i] = block;
                PersonalWorldsMod.LOGGER.debug("Portal type {} frame block set to: {}",
                    i, BuiltInRegistries.BLOCK.getKey(block));
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
     * Get the portal color for a specific portal type.
     * Reads from config on first access, with fallback to RED.
     *
     * @param portalTypeIndex Index into ModConfig.portalTypes array
     * @return The portal color for this portal type
     */
    public static PortalColor getPortalColor(int portalTypeIndex) {
        if (cachedPortalColors == null) {
            var configs = ModConfig.get().portalTypes;
            cachedPortalColors = new PortalColor[configs.size()];

            for (int i = 0; i < configs.size(); i++) {
                String colorStr = configs.get(i).portalColor;
                cachedPortalColors[i] = PortalColor.fromString(colorStr);
                PersonalWorldsMod.LOGGER.debug("Portal type {} color set to: {}",
                    i, cachedPortalColors[i].getSerializedName());
            }
        }

        // Bounds check with clamping
        if (portalTypeIndex < 0 || portalTypeIndex >= cachedPortalColors.length) {
            PersonalWorldsMod.LOGGER.warn("Portal type index {} out of bounds (0-{}), using RED",
                portalTypeIndex, cachedPortalColors.length - 1);
            return PortalColor.RED;
        }

        return cachedPortalColors[portalTypeIndex];
    }

    /**
     * Clear all cached data.
     * Called when configuration is reloaded.
     */
    public static void clearCache() {
        cachedFrameBlocks = null;
        cachedPortalColors = null;
        PersonalWorldsMod.LOGGER.debug("Block cache cleared");
    }
}
