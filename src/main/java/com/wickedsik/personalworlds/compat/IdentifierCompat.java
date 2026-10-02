package com.wickedsik.personalworlds.compat;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compatibility layer for ResourceLocation/ResourceLocation construction.
 * <p>
 * MC 1.20.x uses: new ResourceLocation(namespace, path)
 * MC 1.21.x uses: ResourceLocation.of(namespace, path) or ResourceLocation.fromNamespaceAndPath()
 * <p>
 * This class centralizes all ResourceLocation construction to simplify version migration.
 */
public final class IdentifierCompat {

    private IdentifierCompat() {
        // Utility class
    }

    /**
     * Create an ResourceLocation from namespace and path.
     *
     * @param namespace The namespace (e.g., "minecraft", "personalworlds")
     * @param path      The path (e.g., "overworld", "personal_portal")
     * @return The constructed ResourceLocation
     */
    public static ResourceLocation create(String namespace, String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }

    /**
     * Create an ResourceLocation for a mod resource.
     * Shorthand for create(MOD_ID, path).
     *
     * @param path The resource path
     * @return The mod-namespaced ResourceLocation
     */
    public static ResourceLocation modId(String path) {
        return create(PersonalWorldsMod.MOD_ID, path);
    }

    /**
     * Create a dimension ResourceLocation for a player's pocket dimension.
     *
     * @param playerUuid The player's UUID
     * @return The dimension ResourceLocation (personalworlds:pw_<uuid>)
     */
    public static ResourceLocation dimensionId(UUID playerUuid) {
        return modId("pw_" + playerUuid.toString());
    }

    /**
     * Try to parse an ResourceLocation from a string.
     * Returns null if the string is not a valid ResourceLocation.
     *
     * @param id The string to parse (e.g., "minecraft:stone")
     * @return The parsed ResourceLocation, or null if invalid
     */
    public static @Nullable ResourceLocation tryParse(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        //? if >=1.21 {
        /*return ResourceLocation.tryParse(id);
        *///?} else {
        return ResourceLocation.tryParse(id);
        //?}
    }

    /**
     * Parse an ResourceLocation from an NBT/config string.
     * This is used when reading dimension IDs or block IDs from saved data.
     *
     * @param value The string value (e.g., "minecraft:overworld")
     * @return The parsed ResourceLocation
     * @throws net.minecraft.ResourceLocationException if the string is invalid
     */
    public static ResourceLocation fromNbtString(String value) {
        //? if >=1.21 {
        /*return ResourceLocation.parse(value);
        *///?} else {
        return new ResourceLocation(value);
        //?}
    }

    /**
     * Get the ResourceLocation of a registry key (e.g. a dimension key).
     *
     * @param key The registry key
     * @return The key's ResourceLocation
     */
    public static ResourceLocation fromKey(ResourceKey<?> key) {
        //? if >=1.21 {
        /*return key.identifier();
        *///?} else {
        return key.location();
        //?}
    }
}
