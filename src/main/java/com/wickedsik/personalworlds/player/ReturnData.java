package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.compat.IdentifierCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Stores the return position for a player who entered their personal dimension.
 * This data is saved before teleportation and restored when the player exits.
 *
 * @param dimension The dimension the player came from
 * @param position The block position to return to
 * @param yaw The player's horizontal rotation
 * @param pitch The player's vertical rotation
 */
public record ReturnData(
    ResourceKey<Level> dimension,
    BlockPos position,
    float yaw,
    float pitch
) {

    /**
     * Serialize this return data to NBT.
     *
     * @return NBT compound containing all return data
     */
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Dimension", IdentifierCompat.fromKey(dimension).toString());
        nbt.putInt("X", position.getX());
        nbt.putInt("Y", position.getY());
        nbt.putInt("Z", position.getZ());
        nbt.putFloat("Yaw", yaw);
        nbt.putFloat("Pitch", pitch);
        return nbt;
    }

    /**
     * Deserialize return data from NBT.
     *
     * @param nbt NBT compound containing return data
     * @return Deserialized ReturnData
     */
    public static ReturnData fromNbt(CompoundTag nbt) {
        ResourceLocation dimId = IdentifierCompat.fromNbtString(
            com.wickedsik.personalworlds.compat.NbtCompat.getString(nbt, "Dimension", "minecraft:overworld")
        );
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimId);

        BlockPos position = new BlockPos(
            com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "X", 0),
            com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "Y", 64),
            com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "Z", 0)
        );

        float yaw = com.wickedsik.personalworlds.compat.NbtCompat.getFloat(nbt, "Yaw", 0.0f);
        float pitch = com.wickedsik.personalworlds.compat.NbtCompat.getFloat(nbt, "Pitch", 0.0f);

        return new ReturnData(dimension, position, yaw, pitch);
    }
}
