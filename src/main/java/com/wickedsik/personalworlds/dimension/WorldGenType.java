package com.wickedsik.personalworlds.dimension;

public enum WorldGenType {
    VOID,       // Empty void, starter platform only
    OVERWORLD,  // Full overworld generation
    FLAT;       // Superflat

    public static WorldGenType fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return VOID; // Default
        }
    }
}
