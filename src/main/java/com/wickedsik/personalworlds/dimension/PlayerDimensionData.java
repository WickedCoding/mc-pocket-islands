package com.wickedsik.personalworlds.dimension;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public record PlayerDimensionData(
    UUID ownerUuid,
    String ownerName,
    Identifier dimensionId,
    long createdAt,
    BlockPos spawnPoint,
    WorldGenType generatorType
) {

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putString("DimensionId", dimensionId.toString());
        nbt.putLong("CreatedAt", createdAt);
        nbt.putInt("SpawnX", spawnPoint.getX());
        nbt.putInt("SpawnY", spawnPoint.getY());
        nbt.putInt("SpawnZ", spawnPoint.getZ());
        nbt.putString("GeneratorType", generatorType.name());
        return nbt;
    }

    public static PlayerDimensionData fromNbt(NbtCompound nbt) {
        return new PlayerDimensionData(
            nbt.getUuid("OwnerUuid"),
            nbt.getString("OwnerName"),
            new Identifier(nbt.getString("DimensionId")),
            nbt.getLong("CreatedAt"),
            new BlockPos(
                nbt.getInt("SpawnX"),
                nbt.getInt("SpawnY"),
                nbt.getInt("SpawnZ")
            ),
            WorldGenType.fromString(nbt.getString("GeneratorType"))
        );
    }
}
