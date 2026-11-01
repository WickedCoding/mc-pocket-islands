package com.wickedsik.personalworlds.gametest;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
//? if >=1.21.4 {
/*import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
*///?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * Feeds a mock player's connection the packets a real client sends. Portal entry only
 * behaves like the real game this way: it runs inside
 * {@code ServerGamePacketListenerImpl#handleMovePlayer} on 1.20.x and in the player tick
 * on 1.21.x, which is run here too.
 * <p>
 * The pending teleport id is private; tests only run in dev environments, where these
 * Mojang names are the runtime names on every loader.
 */
public final class ClientInput {

    private static final Field AWAITING_TELEPORT = field("awaitingTeleport");
    private static final Field AWAITING_POSITION = field("awaitingPositionFromClient");
    //? if >=26.3 {
    /*private static final Field RECEIVED_POSITION_THIS_TICK = field("receivedPositionThisTick");
    *///?}

    private ClientInput() {
    }

    /** Confirm the server's pending teleport, if any (a client does this on every teleport). */
    public static void acceptPendingTeleport(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        try {
            Vec3 awaiting = (Vec3) AWAITING_POSITION.get(connection);
            if (awaiting == null) {
                return;
            }
            int id = AWAITING_TELEPORT.getInt(connection);
            //? if >=26.3 {
            /*// 26.x clients echo the position they were sent, and the server checks it as a move
            // from the position it reset at its last connection tick. Mock connections are not
            // ticked, so reset here as that tick would; otherwise the confirm "moved too quickly"
            connection.resetPosition();
            connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
                id, awaiting.x, awaiting.y, awaiting.z, player.getYRot(), player.getXRot()));
            *///?} else {
            connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
            //?}
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Move like a client: confirm any pending teleport, send a position packet, then run the
     * player tick the connection would run next. Portal blocks fire inside the packet handler
     * on 1.20.x and in the player tick ({@code LivingEntity#aiStep}) on 1.21.x; mock
     * connections are not ticked by the server, so the tick is run here.
     */
    public static void moveTo(ServerPlayer player, Vec3 target) {
        acceptPendingTeleport(player);
        //? if >=1.21.4 {
        /*// 1.21.4+ ignores movement until the client reports the level as loaded (sent after
        // joining, respawning and changing dimension)
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        *///?}
        //? if >=26.3 {
        /*// 26.3 disconnects a second position packet within one connection tick. The connection
        // tick clears the flag, and mock connections are not ticked, so clear it here
        try {
            RECEIVED_POSITION_THIS_TICK.setBoolean(player.connection, false);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        *///?}
        //? if >=1.21.2 {
        /*player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true, false));
        *///?} else {
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target.x, target.y, target.z, true));
        //?}
        tick(player);
    }

    /** What {@code ServerGamePacketListenerImpl#tick} does for the player each server tick. */
    public static void tick(ServerPlayer player) {
        player.xo = player.getX();
        player.yo = player.getY();
        player.zo = player.getZ();
        player.doTick();
    }

    private static Field field(String name) {
        try {
            Field field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("ServerGamePacketListenerImpl#" + name + " not found", e);
        }
    }
}
