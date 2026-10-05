package com.wickedsik.personalworlds.platform.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.wickedsik.personalworlds.platform.PlatformEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Game-bus listeners. Event classes are passed explicitly instead of inferred from lambdas. */
final class NeoForgeEvents implements PlatformEvents {

    @Override
    public void onServerStarted(Consumer<MinecraftServer> handler) {
        listen(ServerStartedEvent.class, event -> handler.accept(event.getServer()));
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> handler) {
        listen(ServerStoppingEvent.class, event -> handler.accept(event.getServer()));
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> handler) {
        listen(ServerTickEvent.Post.class, event -> handler.accept(event.getServer()));
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> handler) {
        listen(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                handler.accept(player);
            }
        });
    }

    @Override
    public void onPlayerDisconnect(Consumer<ServerPlayer> handler) {
        listen(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                handler.accept(player);
            }
        });
    }

    // Fires on both sides, like Fabric's UseBlockCallback; the handler filters the client
    @Override
    public void onUseBlock(UseBlockHandler handler) {
        listen(PlayerInteractEvent.RightClickBlock.class, event -> {
            InteractionResult result = handler.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, LevelChunk> handler) {
        listen(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                handler.accept(level, chunk);
            }
        });
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        listen(RegisterCommandsEvent.class, event -> handler.accept(event.getDispatcher()));
    }

    static <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
    }
}
