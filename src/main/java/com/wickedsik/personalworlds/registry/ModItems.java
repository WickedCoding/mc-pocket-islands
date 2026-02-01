package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Registers all items for the PersonalWorlds mod.
 * Currently only references vanilla items used for portal activation.
 */
public class ModItems {

    /**
     * Cached activation items for all portal types.
     * Lazily loaded from config, cleared on config reload.
     */
    private static Item[] cachedActivationItems = null;

    /**
     * Register all mod items.
     * Must be called during mod initialization.
     *
     * Note: The portal block intentionally has no BlockItem -
     * players cannot obtain or place it directly.
     */
    public static void register() {
        // No custom items needed for Phase 2
        // Portal block has no BlockItem (can't be placed by player)

        PersonalWorldsMod.LOGGER.info("Registered items");
    }

    /**
     * Get the item used to activate portal frames for a specific portal type.
     * Reads from config on first access, with fallback to emerald.
     *
     * @param portalTypeIndex Index into ModConfig.portalTypes array
     * @return The activation item for this portal type
     */
    public static Item getActivationItem(int portalTypeIndex) {
        if (cachedActivationItems == null) {
            var configs = ModConfig.get().portalTypes;
            cachedActivationItems = new Item[configs.size()];

            for (int i = 0; i < configs.size(); i++) {
                String itemId = configs.get(i).activationItem;
                Identifier id = IdentifierCompat.tryParse(itemId);
                Item item = id != null ? Registries.ITEM.get(id) : Items.AIR;

                // Validate the item exists (get() returns AIR for unknown IDs)
                if (item == Items.AIR && !itemId.equals("minecraft:air")) {
                    PersonalWorldsMod.LOGGER.warn("Invalid activation item '{}' for portal type {}, using emerald",
                        itemId, i);
                    item = Items.EMERALD;
                }

                cachedActivationItems[i] = item;
                PersonalWorldsMod.LOGGER.debug("Portal type {} activation item set to: {}",
                    i, Registries.ITEM.getId(item));
            }
        }

        // Bounds check with clamping
        if (portalTypeIndex < 0 || portalTypeIndex >= cachedActivationItems.length) {
            PersonalWorldsMod.LOGGER.warn("Portal type index {} out of bounds (0-{}), using 0",
                portalTypeIndex, cachedActivationItems.length - 1);
            return cachedActivationItems[0];
        }

        return cachedActivationItems[portalTypeIndex];
    }

    /**
     * Clear the cached activation items.
     * Called when configuration is reloaded.
     */
    public static void clearCache() {
        cachedActivationItems = null;
        PersonalWorldsMod.LOGGER.debug("Item cache cleared");
    }
}
