package com.wickedsik.personalworlds.portal;

import net.minecraft.util.StringIdentifiable;

/**
 * Enum representing available portal colors.
 * Implements StringIdentifiable for use as a block state property.
 *
 * To add new colors:
 * 1. Add enum value (e.g., PURPLE("purple"))
 * 2. Create texture: personal_portal_purple.png + .mcmeta
 * 3. Create models: personal_portal_ns_purple.json, personal_portal_ew_purple.json
 * 4. Add blockstate entries for color=purple
 */
public enum PortalColor implements StringIdentifiable {
    RED("red"),
    CYAN("cyan");

    private final String name;

    PortalColor(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }

    /**
     * Get the texture name for this color (without path prefix).
     * @return texture name like "personal_portal_red"
     */
    public String getTextureName() {
        return "personal_portal_" + name;
    }

    /**
     * Parse a color from string, with fallback to RED for invalid values.
     * Case-insensitive matching.
     *
     * @param name the color name to parse
     * @return the matching PortalColor, or RED if not found
     */
    public static PortalColor fromString(String name) {
        if (name == null || name.isEmpty()) {
            return RED;
        }
        for (PortalColor color : values()) {
            if (color.name.equalsIgnoreCase(name)) {
                return color;
            }
        }
        return RED; // Default fallback
    }
}
