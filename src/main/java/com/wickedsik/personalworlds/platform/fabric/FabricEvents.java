package com.wickedsik.personalworlds.platform.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.wickedsik.personalworlds.platform.PlatformEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class FabricEvents implements PlatformEvents {

    @Override
    public void onServerStarted(Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STARTED.register(handler::accept);
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> handler) {
        ServerLifecycleEvents.SERVER_STOPPING.register(handler::accept);
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> handler) {
        ServerTickEvents.END_SERVER_TICK.register(handler::accept);
    }

    // JOIN fires inside PlayerList#placeNewPlayer, before the player is added to their level:
    // a teleport there leaves a second copy of the player in the level they logged in to.
    // Handlers run at the end of the tick instead, once the player is placed, which is
    // where Forge and NeoForge fire PlayerLoggedInEvent
    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> handler) {
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> joined.add(listener.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joined.isEmpty()) {
                return;
            }
            List<ServerPlayer> placed = new ArrayList<>(joined);
            joined.clear();
            for (ServerPlayer player : placed) {
                // Skip players who already left (and were maybe replaced by a new login)
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    handler.accept(player);
                }
            }
        });
    }

    @Override
    public void onPlayerDisconnect(Consumer<ServerPlayer> handler) {
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> handler.accept(listener.getPlayer()));
    }

    @Override
    public void onUseBlock(UseBlockHandler handler) {
        UseBlockCallback.EVENT.register(handler::onUseBlock);
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, LevelChunk> handler) {
        ServerChunkEvents.CHUNK_LOAD.register(handler::accept);
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> handler.accept(dispatcher));
    }
}
