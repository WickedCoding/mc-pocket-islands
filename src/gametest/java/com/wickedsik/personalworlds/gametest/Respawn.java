package com.wickedsik.personalworlds.gametest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21 {
/*import net.minecraft.world.entity.Entity;
*///?}

/** Vanilla respawn after death (what the client's respawn button triggers). */
public final class Respawn {

    private Respawn() {
    }

    public static ServerPlayer respawn(MinecraftServer server, ServerPlayer dead) {
        //? if >=1.21 {
        /*return server.getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED);
        *///?} else {
        return server.getPlayerList().respawn(dead, false);
        //?}
    }
}
