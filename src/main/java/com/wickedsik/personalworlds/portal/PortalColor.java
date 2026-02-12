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
    WHITE("white"),
    LIGHT_GRAY("light_gray"),
    GRAY("gray"),
    BLACK("black"),
    BROWN("brown"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    LIME("lime"),
    GREEN("green"),
    CYAN("cyan"),
    LIGHT_BLUE("light_blue"),
    BLUE("blue"),
    PURPLE("purple"),
    MAGENTA("magenta"),
    PINK("pink");

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
     * Get the human-readable display name for this color.
     * Converts snake_case to Title Case (e.g., "light_blue" → "Light Blue").
     *
     * @return capitalized display name
     */
    public String getDisplayName() {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" ");
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
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
