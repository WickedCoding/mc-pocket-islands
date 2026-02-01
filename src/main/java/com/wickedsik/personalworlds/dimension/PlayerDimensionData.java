package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.compat.IdentifierCompat;
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
    WorldGenType generatorType,
    int portalTypeIndex  // Index into ModConfig.portalTypes array
) {

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        com.wickedsik.personalworlds.compat.NbtCompat.putUuid(nbt, "OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putString("DimensionId", dimensionId.toString());
        nbt.putLong("CreatedAt", createdAt);
        nbt.putInt("SpawnX", spawnPoint.getX());
        nbt.putInt("SpawnY", spawnPoint.getY());
        nbt.putInt("SpawnZ", spawnPoint.getZ());
        nbt.putString("GeneratorType", generatorType.name());
        nbt.putInt("PortalTypeIndex", portalTypeIndex);
        return nbt;
    }

    public static PlayerDimensionData fromNbt(NbtCompound nbt) {
        // Backward compatibility: default to portal type 0 if not present
        int portalTypeIndex = com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "PortalTypeIndex", 0);

        return new PlayerDimensionData(
            com.wickedsik.personalworlds.compat.NbtCompat.getUuid(nbt, "OwnerUuid"),
            com.wickedsik.personalworlds.compat.NbtCompat.getString(nbt, "OwnerName", "Unknown"),
            IdentifierCompat.fromNbtString(com.wickedsik.personalworlds.compat.NbtCompat.getString(nbt, "DimensionId", "")),
            com.wickedsik.personalworlds.compat.NbtCompat.getLong(nbt, "CreatedAt", 0L),
            new BlockPos(
                com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "SpawnX", 0),
                com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "SpawnY", 64),
                com.wickedsik.personalworlds.compat.NbtCompat.getInt(nbt, "SpawnZ", 0)
            ),
            WorldGenType.fromString(com.wickedsik.personalworlds.compat.NbtCompat.getString(nbt, "GeneratorType", "VOID")),
            portalTypeIndex
        );
    }
}
