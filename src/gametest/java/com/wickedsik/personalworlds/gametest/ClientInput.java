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
 * Feeds a mock player's connection the packets a real client sends, so movement runs
 * through {@code ServerGamePacketListenerImpl#handleMovePlayer}. Portal entry happens
 * inside that handler (move -> entityInside -> teleport), and bugs that only show there
 * (Forge's "moved wrongly" teleport) stay invisible when the test calls the portal code
 * directly. 1.21.x applies block effects in the player tick instead, which is run too.
 * <p>
 * The pending teleport id is private; tests only run in dev environments, where these
 * Mojang names are the runtime names on every loader.
 */
public final class ClientInput {

    private static final Field AWAITING_TELEPORT = field("awaitingTeleport");
    private static final Field AWAITING_POSITION = field("awaitingPositionFromClient");

    private ClientInput() {
    }

    /** Confirm the server's pending teleport, if any (a client does this on every teleport). */
    public static void acceptPendingTeleport(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        try {
            if (AWAITING_POSITION.get(connection) == null) {
                return;
            }
            int id = AWAITING_TELEPORT.getInt(connection);
            connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
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
