package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Falling below Y=0 on an island sends the player back. */
public final class VoidScenarios {

    private VoidScenarios() {
    }

    public static void fallingIntoVoidEjects(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = MockPlayers.join(server, "VoidOwner");

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner, server, owner.getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> TestSupport.moveWithinLevel(owner, 0.5, -5.0, 0.5))
            .thenWaitUntil(() -> helper.assertTrue(!TestSupport.inPocket(owner), "player below Y=0 was not ejected from the island"))
            .thenExecute(() -> MockPlayers.leave(owner))
            .thenSucceed();
    }
}
