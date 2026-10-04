package com.wickedsik.personalworlds.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import com.wickedsik.personalworlds.compat.EntityCompat;
import net.minecraft.network.Connection;
//? if >=1.20.2 && <1.21 {
/*import net.minecraft.network.ConnectionProtocol;
*///?}
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.2 {
/*import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
*///?}
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Survival players without a network connection, joined through the real player list
 * so join events fire on every loader. Like vanilla's makeMockServerPlayerInLevel,
 * which is forced to creative and always uses the same name.
 */
public final class MockPlayers {

    private MockPlayers() {
    }

    public static ServerPlayer join(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("pocketislands-test:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);

        //? if >=1.20.2 {
        /*ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        *///?} else {
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        //?}

        // An embedded channel gives packets somewhere to go (1.21.x writes straight to the
        // channel); vanilla's own mock player does the same on 1.21.11
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        //? if >=1.20.2 && <1.21 {
        /*// 1.20.2-1.20.6 read the protocol from the channel when the listener is set (as vanilla's mock does)
        channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
        *///?}
        //? if >=1.21 {
        /*server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        *///?} else if >=1.20.2 {
        /*server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile));
        *///?} else {
        server.getPlayerList().placeNewPlayer(connection, player);
        //?}

        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    public static void leave(ServerPlayer player) {
        EntityCompat.getServer(player).getPlayerList().remove(player);
    }
}
