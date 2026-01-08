package com.wickedsik.personalworlds.registry;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Registers all items for the PersonalWorlds mod.
 * Currently only references vanilla items used for portal activation.
 */
public class ModItems {

    /**
     * Reference to the activation item (vanilla emerald).
     * Using a getter allows for future configurability.
     */
    private static final Item ACTIVATION_ITEM = Items.EMERALD;

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
     * Currently returns vanilla emerald.
     *
     * @return The activation item
     */
    public static Item getActivationItem() {
        return ACTIVATION_ITEM;
    }
}
