package com.wickedsik.personalworlds.compat;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility layer for Entity/Player method access.
 * <p>
 * MC 1.20.x uses: player.getServer(), player.getServerWorld(), player.getPos()
 * MC 1.21.x uses: player.getEntityWorld().getServer(), player.getEntityWorld(), player.getPos()
 * <p>
 * Spawn point access:
 * MC 1.20.x: player.getSpawnPointPosition(), player.getSpawnPointDimension()
 * MC 1.21.x: player.getRespawn().respawnData().getPos(), Respawn.getDimension()
 * <p>
 * This class centralizes all entity-related method access to simplify version migration.
 */
public final class EntityCompat {

    private EntityCompat() {
        // Utility class
    }

    /**
     * Get the MinecraftServer from a player.
     *
     * @param player The server player
     * @return The MinecraftServer instance
     */
    public static MinecraftServer getServer(ServerPlayerEntity player) {
        //? if >=1.21 {
        /*return player.getEntityWorld().getServer();
        *///?} else {
        return player.getServer();
        //?}
    }

    /**
     * Get the ServerWorld the player is currently in.
     *
     * @param player The server player
     * @return The ServerWorld the player is in
     */
    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        //? if >=1.21 {
        /*return (ServerWorld) player.getEntityWorld();
        *///?} else {
        return player.getServerWorld();
        //?}
    }

    /**
     * Get the player's position as Vec3d.
     *
     * @param player The server player
     * @return The player's position
     */
    public static Vec3d getPos(ServerPlayerEntity player) {
        //? if >=1.21 {
        /*return player.getEntityPos();
        *///?} else {
        return player.getPos();
        //?}
    }

    /**
     * Get the player's spawn point position (bed/respawn anchor location).
     *
     * @param player The server player
     * @return The spawn point position, or null if none set
     */
    public static @Nullable BlockPos getSpawnPointPosition(ServerPlayerEntity player) {
        //? if >=1.21 {
        /*ServerPlayerEntity.Respawn respawn = player.getRespawn();
        if (respawn == null) {
            return null;
        }
        // In 1.21.x, Respawn.respawnData() returns SpawnPoint which has getPos()
        return respawn.respawnData().getPos();
        *///?} else {
        return player.getSpawnPointPosition();
        //?}
    }

    /**
     * Get the player's spawn point dimension.
     *
     * @param player The server player
     * @return The spawn point dimension, or null if none set
     */
    public static @Nullable RegistryKey<World> getSpawnPointDimension(ServerPlayerEntity player) {
        //? if >=1.21 {
        /*ServerPlayerEntity.Respawn respawn = player.getRespawn();
        if (respawn == null) {
            return null;
        }
        // In 1.21.x, SpawnPoint has getDimension()
        return respawn.respawnData().getDimension();
        *///?} else {
        return player.getSpawnPointDimension();
        //?}
    }
}
