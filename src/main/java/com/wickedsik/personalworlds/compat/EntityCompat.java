package com.wickedsik.personalworlds.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility layer for Entity/Player method access.
 * <p>
 * MC 1.20.x uses: player.getServer(), player.serverLevel(), player.position()
 * MC 1.21.x uses: player.level().getServer(), player.level(), player.position()
 * <p>
 * Spawn point access:
 * MC 1.20.x: player.getRespawnPosition(), player.getRespawnDimension()
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
    public static MinecraftServer getServer(ServerPlayer player) {
        //? if >=1.21 {
        /*return player.level().getServer();
        *///?} else {
        return player.getServer();
        //?}
    }

    /**
     * Get the ServerLevel the player is currently in.
     *
     * @param player The server player
     * @return The ServerLevel the player is in
     */
    public static ServerLevel getServerWorld(ServerPlayer player) {
        //? if >=1.21 {
        /*return (ServerLevel) player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    /**
     * Get the player's position as Vec3.
     *
     * @param player The server player
     * @return The player's position
     */
    public static Vec3 getPos(ServerPlayer player) {
        //? if >=1.21 {
        /*return player.position();
        *///?} else {
        return player.position();
        //?}
    }

    /**
     * Get the player's spawn point position (bed/respawn anchor location).
     *
     * @param player The server player
     * @return The spawn point position, or null if none set
     */
    public static @Nullable BlockPos getSpawnPointPosition(ServerPlayer player) {
        //? if >=1.21 {
        /*ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn == null) {
            return null;
        }
        // In 1.21.x, Respawn.respawnData() returns SpawnPoint which has getPos()
        return respawn.respawnData().pos();
        *///?} else {
        return player.getRespawnPosition();
        //?}
    }

    /**
     * Get the player's spawn point dimension.
     *
     * @param player The server player
     * @return The spawn point dimension, or null if none set
     */
    public static @Nullable ResourceKey<Level> getSpawnPointDimension(ServerPlayer player) {
        //? if >=1.21 {
        /*ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn == null) {
            return null;
        }
        // In 1.21.x, SpawnPoint has getDimension()
        return respawn.respawnData().dimension();
        *///?} else {
        return player.getRespawnDimension();
        //?}
    }
}
