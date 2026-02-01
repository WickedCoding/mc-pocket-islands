package com.wickedsik.personalworlds.compat;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compatibility layer for Identifier/ResourceLocation construction.
 * <p>
 * MC 1.20.x uses: new Identifier(namespace, path)
 * MC 1.21.x uses: Identifier.of(namespace, path) or ResourceLocation.fromNamespaceAndPath()
 * <p>
 * This class centralizes all Identifier construction to simplify version migration.
 */
public final class IdentifierCompat {

    private IdentifierCompat() {
        // Utility class
    }

    /**
     * Create an Identifier from namespace and path.
     *
     * @param namespace The namespace (e.g., "minecraft", "personalworlds")
     * @param path      The path (e.g., "overworld", "personal_portal")
     * @return The constructed Identifier
     */
    public static Identifier create(String namespace, String path) {
        //? if >=1.21 {
        /*return Identifier.of(namespace, path);*/
        //? } else {
        return new Identifier(namespace, path);
        //? }
    }

    /**
     * Create an Identifier for a mod resource.
     * Shorthand for create(MOD_ID, path).
     *
     * @param path The resource path
     * @return The mod-namespaced Identifier
     */
    public static Identifier modId(String path) {
        return create(PersonalWorldsMod.MOD_ID, path);
    }

    /**
     * Create a dimension Identifier for a player's pocket dimension.
     *
     * @param playerUuid The player's UUID
     * @return The dimension Identifier (personalworlds:pw_<uuid>)
     */
    public static Identifier dimensionId(UUID playerUuid) {
        return modId("pw_" + playerUuid.toString());
    }

    /**
     * Try to parse an Identifier from a string.
     * Returns null if the string is not a valid Identifier.
     *
     * @param id The string to parse (e.g., "minecraft:stone")
     * @return The parsed Identifier, or null if invalid
     */
    public static @Nullable Identifier tryParse(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        //? if >=1.21 {
        /*return Identifier.tryParse(id);*/
        //? } else {
        return Identifier.tryParse(id);
        //? }
    }

    /**
     * Parse an Identifier from an NBT/config string.
     * This is used when reading dimension IDs or block IDs from saved data.
     *
     * @param value The string value (e.g., "minecraft:overworld")
     * @return The parsed Identifier
     * @throws net.minecraft.util.InvalidIdentifierException if the string is invalid
     */
    public static Identifier fromNbtString(String value) {
        //? if >=1.21 {
        /*return Identifier.of(value);*/
        //? } else {
        return new Identifier(value);
        //? }
    }
}
