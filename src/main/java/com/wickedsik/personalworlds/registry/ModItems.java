package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
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
     * Cached activation item reference.
     * Lazily loaded from config, cleared on config reload.
     */
    private static Item cachedActivationItem = null;

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
     * Get the item used to activate portal frames.
     * Reads from config on first access, with fallback to emerald.
     *
     * @return The activation item
     */
    public static Item getActivationItem() {
        if (cachedActivationItem == null) {
            String itemId = ModConfig.get().activationItem;
            Identifier id = new Identifier(itemId);
            cachedActivationItem = Registries.ITEM.get(id);

            // Validate the item exists (get() returns AIR for unknown IDs)
            if (cachedActivationItem == Items.AIR && !itemId.equals("minecraft:air")) {
                PersonalWorldsMod.LOGGER.warn("Invalid activation item '{}', using emerald", itemId);
                cachedActivationItem = Items.EMERALD;
            }

            PersonalWorldsMod.LOGGER.debug("Activation item set to: {}", Registries.ITEM.getId(cachedActivationItem));
        }
        return cachedActivationItem;
    }

    /**
     * Clear the cached activation item.
     * Called when configuration is reloaded.
     */
    public static void clearCache() {
        cachedActivationItem = null;
        PersonalWorldsMod.LOGGER.debug("Item cache cleared");
    }
}
