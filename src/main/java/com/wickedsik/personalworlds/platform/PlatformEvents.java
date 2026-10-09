package com.wickedsik.personalworlds.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Server, player and chunk events. Handlers run on the server thread.
 */
public interface PlatformEvents {

    /** After the server has fully started and all levels are loaded. */
    void onServerStarted(Consumer<MinecraftServer> handler);

    /** When the server begins shutting down, before levels are saved. */
    void onServerStopping(Consumer<MinecraftServer> handler);

    /** At the end of every server tick. */
    void onServerTickEnd(Consumer<MinecraftServer> handler);

    /** After a joining player is placed in their level and the player list, so handlers may teleport them. */
    void onPlayerJoin(Consumer<ServerPlayer> handler);

    void onPlayerDisconnect(Consumer<ServerPlayer> handler);

    /**
     * When a player right-clicks a block. Returning anything other than
     * {@link InteractionResult#PASS} cancels further processing.
     */
    void onUseBlock(UseBlockHandler handler);

    /**
     * When a chunk is promoted to a full chunk. Fires inside chunk loading on every
     * loader, so handlers must only queue work for a later tick.
     */
    void onChunkLoad(BiConsumer<ServerLevel, LevelChunk> handler);

    /** When the server builds its command tree (startup and {@code /reload}). */
    void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> handler);

    @FunctionalInterface
    interface UseBlockHandler {
        InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult);
    }
}
