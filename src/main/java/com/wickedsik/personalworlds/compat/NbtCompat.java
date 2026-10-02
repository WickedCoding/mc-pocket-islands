package com.wickedsik.personalworlds.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Compatibility layer for CompoundTag API differences.
 * <p>
 * MC 1.20.x uses:
 * - getInt(key) returns int
 * - putUUID(key, uuid) / getUUID(key) for UUID storage
 * - contains(key, type) for type-checked containment
 * <p>
 * MC 1.21.x uses:
 * - getInt(key) returns Optional, getInt(key, fallback) returns int
 * - No putUUID/getUUID - must use string conversion
 * - contains(key) without type check
 * <p>
 * This class centralizes NBT access to simplify version migration.
 */
public final class NbtCompat {

    private NbtCompat() {
        // Utility class
    }

    // ==================== Primitive Getters ====================

    /**
     * Get an int value from NBT with a default fallback.
     */
    public static int getInt(CompoundTag nbt, String key, int defaultValue) {
        //? if >=1.21 {
        /*return nbt.getIntOr(key, defaultValue);
        *///?} else {
        return nbt.contains(key, Tag.TAG_INT) ? nbt.getInt(key) : defaultValue;
        //?}
    }

    /**
     * Get a string value from NBT with a default fallback.
     */
    public static String getString(CompoundTag nbt, String key, String defaultValue) {
        //? if >=1.21 {
        /*return nbt.getStringOr(key, defaultValue);
        *///?} else {
        return nbt.contains(key, Tag.TAG_STRING) ? nbt.getString(key) : defaultValue;
        //?}
    }

    /**
     * Get a float value from NBT with a default fallback.
     */
    public static float getFloat(CompoundTag nbt, String key, float defaultValue) {
        //? if >=1.21 {
        /*return nbt.getFloatOr(key, defaultValue);
        *///?} else {
        return nbt.contains(key, Tag.TAG_FLOAT) ? nbt.getFloat(key) : defaultValue;
        //?}
    }

    /**
     * Get a boolean value from NBT with a default fallback.
     */
    public static boolean getBoolean(CompoundTag nbt, String key, boolean defaultValue) {
        //? if >=1.21 {
        /*return nbt.getBooleanOr(key, defaultValue);
        *///?} else {
        return nbt.contains(key, Tag.TAG_BYTE) ? nbt.getBoolean(key) : defaultValue;
        //?}
    }

    // ==================== UUID Handling ====================

    /**
     * Store a UUID in NBT.
     * In 1.20.x uses putUUID, in 1.21.x stores as string.
     */
    public static void putUuid(CompoundTag nbt, String key, UUID uuid) {
        //? if >=1.21 {
        /*nbt.putString(key, uuid.toString());
        *///?} else {
        nbt.putUUID(key, uuid);
        //?}
    }

    /**
     * Get a UUID from NBT.
     * In 1.20.x uses getUUID, in 1.21.x parses from string.
     *
     * @return The UUID, or null if not found or invalid
     */
    public static @Nullable UUID getUuid(CompoundTag nbt, String key) {
        //? if >=1.21 {
        /*String uuidStr = nbt.getStringOr(key, "");
        if (uuidStr.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        *///?} else {
        return nbt.hasUUID(key) ? nbt.getUUID(key) : null;
        //?}
    }

    /**
     * Check if NBT contains a valid UUID at the given key.
     */
    public static boolean containsUuid(CompoundTag nbt, String key) {
        //? if >=1.21 {
        /*String uuidStr = nbt.getStringOr(key, "");
        if (uuidStr.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(uuidStr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
        *///?} else {
        return nbt.hasUUID(key);
        //?}
    }

    // ==================== Type-checked Contains ====================

    /**
     * Check if NBT contains a key with a specific NBT element type.
     */
    public static boolean contains(CompoundTag nbt, String key, int type) {
        //? if >=1.21 {
        /*// In 1.21.x, we need to check if the key exists and then verify type
        if (!nbt.contains(key)) {
            return false;
        }
        Tag element = nbt.get(key);
        return element != null && element.getId() == type;
        *///?} else {
        return nbt.contains(key, type);
        //?}
    }

    // ==================== Compound Getters ====================

    /**
     * Get a compound from NBT, returning empty compound if not found.
     */
    public static CompoundTag getCompound(CompoundTag nbt, String key) {
        //? if >=1.21 {
        /*return nbt.getCompound(key).orElse(new CompoundTag());
        *///?} else {
        return nbt.getCompound(key);
        //?}
    }

    /**
     * Get a long value from NBT with a default fallback.
     */
    public static long getLong(CompoundTag nbt, String key, long defaultValue) {
        //? if >=1.21 {
        /*return nbt.getLongOr(key, defaultValue);
        *///?} else {
        return nbt.contains(key, Tag.TAG_LONG) ? nbt.getLong(key) : defaultValue;
        //?}
    }

    // ==================== List Getters ====================

    /**
     * Get a list from NBT by key and element type.
     */
    public static net.minecraft.nbt.ListTag getList(CompoundTag nbt, String key, int type) {
        //? if >=1.21 {
        /*return nbt.getList(key).orElse(new net.minecraft.nbt.ListTag());
        *///?} else {
        return nbt.getList(key, type);
        //?}
    }

    /**
     * Get a compound from a ListTag by index.
     */
    public static CompoundTag getCompound(net.minecraft.nbt.ListTag list, int index) {
        //? if >=1.21 {
        /*return list.getCompound(index).orElse(new CompoundTag());
        *///?} else {
        return list.getCompound(index);
        //?}
    }

    /**
     * Get all keys of an NBT compound.
     */
    public static Set<String> getKeys(CompoundTag nbt) {
        //? if >=1.21 {
        /*return nbt.keySet();
        *///?} else {
        return nbt.getAllKeys();
        //?}
    }
}
