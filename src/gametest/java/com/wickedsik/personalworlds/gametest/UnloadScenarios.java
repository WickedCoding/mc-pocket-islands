package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An island that was just released for unloading must not eject a player who comes back
 * before it is gone, and a player who returns after it is gone must land on it exactly once.
 */
public final class UnloadScenarios {

    // Long enough for the runtime dimension to act on the release (Fantasy: next tick start;
    // Infiniverse: the next tick end)
    private static final int SETTLE_TICKS = 40;

    private UnloadScenarios() {
    }

    public static void reenterWhileUnloadingKeepsPlayer(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = MockPlayers.join(server, "UnloadReenter");

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner, server, owner.getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                PortalHelper.teleportToReturnPosition(owner, server);
                helper.assertTrue(DimensionManager.unloadIfEmpty(owner.getUUID()), "empty island was not unloaded");
                helper.assertTrue(PortalHelper.teleportToDimension(owner, server, owner.getUUID()), "re-entry failed");
            })
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner was ejected from an island they re-entered while it unloaded"))
            .thenExecute(() -> MockPlayers.leave(owner))
            .thenSucceed();
    }

    public static void rejoinWhileUnloadingKeepsPlayer(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<ServerPlayer> owner = new AtomicReference<>(MockPlayers.join(server, "UnloadRejoin"));

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner.get(), server, owner.get().getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner.get(), owner.get()), "owner did not arrive on their island"))
            .thenExecute(() -> {
                // Log out on the island; the island unloads before the player is back
                MockPlayers.leave(owner.get());
                helper.assertTrue(DimensionManager.unloadIfEmpty(owner.get().getUUID()), "empty island was not unloaded");
                owner.set(MockPlayers.join(server, "UnloadRejoin"));
                helper.assertTrue(TestSupport.inPocketOf(owner.get(), owner.get()), "vanilla did not put the owner back on the island");
            })
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> helper.assertTrue(TestSupport.inPocketOf(owner.get(), owner.get()), "owner was ejected after logging back in while the island unloaded"))
            .thenExecute(() -> MockPlayers.leave(owner.get()))
            .thenSucceed();
    }

    public static void rejoinAfterUnloadReturnsToIsland(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<ServerPlayer> owner = new AtomicReference<>(MockPlayers.join(server, "UnloadedRejoin"));
        AtomicReference<ResourceKey<Level>> island = new AtomicReference<>();

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner.get(), server, owner.get().getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner.get(), owner.get()), "owner did not arrive on their island"))
            .thenExecute(() -> {
                island.set(TestSupport.level(owner.get()).dimension());
                MockPlayers.leave(owner.get());
                helper.assertTrue(DimensionManager.unloadIfEmpty(owner.get().getUUID()), "empty island was not unloaded");
            })
            // Vanilla can no longer put them back: the login lands in the overworld
            .thenWaitUntil(() -> helper.assertTrue(server.getLevel(island.get()) == null, "island did not finish unloading"))
            .thenExecute(() -> owner.set(MockPlayers.join(server, "UnloadedRejoin")))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner.get(), owner.get()), "owner was not restored to their island"))
            .thenExecute(() -> {
                ServerPlayer player = owner.get();
                helper.assertTrue(TestSupport.level(player).players().contains(player), "island does not hold the restored player");
                helper.assertTrue(server.overworld().getEntity(player.getUUID()) == null, "restore left a copy of the player in the overworld");
            })
            .thenExecute(() -> MockPlayers.leave(owner.get()))
            .thenSucceed();
    }
}
