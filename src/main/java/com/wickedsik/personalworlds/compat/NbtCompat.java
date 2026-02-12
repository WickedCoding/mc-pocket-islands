package com.wickedsik.personalworlds.compat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compatibility layer for NbtCompound API differences.
 * <p>
 * MC 1.20.x uses:
 * - getInt(key) returns int
 * - putUuid(key, uuid) / getUuid(key) for UUID storage
 * - contains(key, type) for type-checked containment
 * <p>
 * MC 1.21.x uses:
 * - getInt(key) returns Optional, getInt(key, fallback) returns int
 * - No putUuid/getUuid - must use string conversion
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
    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
        //? if >=1.21 {
        /*return nbt.getInt(key, defaultValue);
        *///?} else {
        return nbt.contains(key, NbtElement.INT_TYPE) ? nbt.getInt(key) : defaultValue;
        //?}
    }

    /**
     * Get a string value from NBT with a default fallback.
     */
    public static String getString(NbtCompound nbt, String key, String defaultValue) {
        //? if >=1.21 {
        /*return nbt.getString(key, defaultValue);
        *///?} else {
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : defaultValue;
        //?}
    }

    /**
     * Get a float value from NBT with a default fallback.
     */
    public static float getFloat(NbtCompound nbt, String key, float defaultValue) {
        //? if >=1.21 {
        /*return nbt.getFloat(key, defaultValue);
        *///?} else {
        return nbt.contains(key, NbtElement.FLOAT_TYPE) ? nbt.getFloat(key) : defaultValue;
        //?}
    }

    /**
     * Get a boolean value from NBT with a default fallback.
     */
    public static boolean getBoolean(NbtCompound nbt, String key, boolean defaultValue) {
        //? if >=1.21 {
        /*return nbt.getBoolean(key, defaultValue);
        *///?} else {
        return nbt.contains(key, NbtElement.BYTE_TYPE) ? nbt.getBoolean(key) : defaultValue;
        //?}
    }

    // ==================== UUID Handling ====================

    /**
     * Store a UUID in NBT.
     * In 1.20.x uses putUuid, in 1.21.x stores as string.
     */
    public static void putUuid(NbtCompound nbt, String key, UUID uuid) {
        //? if >=1.21 {
        /*nbt.putString(key, uuid.toString());
        *///?} else {
        nbt.putUuid(key, uuid);
        //?}
    }

    /**
     * Get a UUID from NBT.
     * In 1.20.x uses getUuid, in 1.21.x parses from string.
     *
     * @return The UUID, or null if not found or invalid
     */
    public static @Nullable UUID getUuid(NbtCompound nbt, String key) {
        //? if >=1.21 {
        /*String uuidStr = nbt.getString(key, "");
        if (uuidStr.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        *///?} else {
        return nbt.containsUuid(key) ? nbt.getUuid(key) : null;
        //?}
    }

    /**
     * Check if NBT contains a valid UUID at the given key.
     */
    public static boolean containsUuid(NbtCompound nbt, String key) {
        //? if >=1.21 {
        /*String uuidStr = nbt.getString(key, "");
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
        return nbt.containsUuid(key);
        //?}
    }

    // ==================== Type-checked Contains ====================

    /**
     * Check if NBT contains a key with a specific NBT element type.
     */
    public static boolean contains(NbtCompound nbt, String key, int type) {
        //? if >=1.21 {
        /*// In 1.21.x, we need to check if the key exists and then verify type
        if (!nbt.contains(key)) {
            return false;
        }
        NbtElement element = nbt.get(key);
        return element != null && element.getType() == type;
        *///?} else {
        return nbt.contains(key, type);
        //?}
    }

    // ==================== Compound Getters ====================

    /**
     * Get a compound from NBT, returning empty compound if not found.
     */
    public static NbtCompound getCompound(NbtCompound nbt, String key) {
        //? if >=1.21 {
        /*return nbt.getCompound(key).orElse(new NbtCompound());
        *///?} else {
        return nbt.getCompound(key);
        //?}
    }

    /**
     * Get a long value from NBT with a default fallback.
     */
    public static long getLong(NbtCompound nbt, String key, long defaultValue) {
        //? if >=1.21 {
        /*return nbt.getLong(key, defaultValue);
        *///?} else {
        return nbt.contains(key, NbtElement.LONG_TYPE) ? nbt.getLong(key) : defaultValue;
        //?}
    }

    // ==================== List Getters ====================

    /**
     * Get a list from NBT by key and element type.
     */
    public static net.minecraft.nbt.NbtList getList(NbtCompound nbt, String key, int type) {
        //? if >=1.21 {
        /*return nbt.getList(key).orElse(new net.minecraft.nbt.NbtList());
        *///?} else {
        return nbt.getList(key, type);
        //?}
    }

    /**
     * Get a compound from an NbtList by index.
     */
    public static NbtCompound getCompound(net.minecraft.nbt.NbtList list, int index) {
        //? if >=1.21 {
        /*return list.getCompound(index).orElse(new NbtCompound());
        *///?} else {
        return list.getCompound(index);
        //?}
    }
}
