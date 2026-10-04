package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalOwnershipManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Portal activation, first entry and the return trip. */
public final class PortalScenarios {

    // ConcurrentPortalGuard keeps a player on cooldown for a second after each portal use
    private static final int PORTAL_COOLDOWN_TICKS = 30;
    private static final BlockPos ISLAND_ARRIVAL = new BlockPos(0, 65, 0);

    private PortalScenarios() {
    }

    /** 2.5.3.1: frame + activation item lights the portal; the first entry creates the island. */
    public static void activationAndFirstEntry(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.buildArena(helper);
        ServerPlayer owner = MockPlayers.join(server, "ActivationOwner");
        BlockPos portal = helper.absolutePos(TestSupport.PORTAL_INTERIOR);

        helper.startSequence()
            .thenExecute(() -> {
                TestSupport.standInFrontOfArenaPortal(helper, owner);
                TestSupport.activateArenaPortal(helper, owner);
                helper.assertTrue(TestSupport.isPortal(helper.getLevel(), portal), "frame was not lit by the activation item");
                Optional<java.util.UUID> claimedBy = PortalOwnershipManager.get(server).getOwner(helper.getLevel(), portal);
                helper.assertTrue(claimedBy.map(owner.getUUID()::equals).orElse(false), "portal not owned by the activating player");
                helper.assertTrue(DimensionRegistry.get(server).getDimensionData(owner.getUUID()).isEmpty(), "island exists before first entry");
                TestSupport.walkIntoArenaPortal(helper, owner);
            })
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.arrivedOnIslandOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                helper.assertTrue(DimensionRegistry.get(server).getDimensionData(owner.getUUID()).isPresent(), "island not registered");
                // New islands receive players at (0, 65, 0); arriving anywhere else means the
                // teleport target was lost on the way (Forge "moved wrongly" bug)
                helper.assertTrue(owner.blockPosition().closerThan(ISLAND_ARRIVAL, 3),
                    "arrived at " + owner.blockPosition() + ", expected near " + ISLAND_ARRIVAL);
            })
            .thenExecute(() -> MockPlayers.leave(owner))
            .thenSucceed();
    }

    /** 2.5.3.2: the island's return portal puts the player back at the stored position and dimension. */
    public static void returnToStoredPosition(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.buildArena(helper);
        ServerPlayer owner = MockPlayers.join(server, "ReturnOwner");
        BlockPos portal = helper.absolutePos(TestSupport.PORTAL_INTERIOR);
        AtomicReference<ReturnData> stored = new AtomicReference<>();

        helper.startSequence()
            .thenExecute(() -> {
                TestSupport.standInFrontOfArenaPortal(helper, owner);
                TestSupport.activateArenaPortal(helper, owner);
                TestSupport.walkIntoArenaPortal(helper, owner);
            })
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.arrivedOnIslandOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                Optional<ReturnData> data = PlayerDataManager.get(server).getReturnData(owner.getUUID());
                helper.assertTrue(data.isPresent(), "no return position stored on entry");
                stored.set(data.get());
                TestSupport.buildAndActivateIslandReturnPortal(owner);
                helper.assertTrue(TestSupport.isPortal(TestSupport.level(owner), TestSupport.islandReturnPortal()), "return frame on the island was not lit");
            })
            .thenIdle(PORTAL_COOLDOWN_TICKS)
            .thenExecute(() -> TestSupport.walkIntoIslandReturnPortal(owner))
            .thenWaitUntil(() -> {
                ClientInput.acceptPendingTeleport(owner);
                helper.assertTrue(!TestSupport.inPocket(owner), "owner is still on the island");
            })
            .thenExecute(() -> {
                ReturnData expected = stored.get();
                helper.assertTrue(TestSupport.level(owner).dimension().equals(expected.dimension()),
                    "returned to " + TestSupport.level(owner).dimension() + ", expected " + expected.dimension());
                helper.assertTrue(owner.blockPosition().equals(expected.position()),
                    "returned to " + owner.blockPosition() + ", expected " + expected.position());
                MockPlayers.leave(owner);
            })
            .thenSucceed();
    }
}
