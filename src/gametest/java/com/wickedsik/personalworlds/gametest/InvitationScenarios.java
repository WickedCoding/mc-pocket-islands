package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.VisitDenialReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 2.5.3.3: an uninvited player is denied, an invited one gets in. */
public final class InvitationScenarios {

    private static final int PORTAL_COOLDOWN_TICKS = 30;

    private InvitationScenarios() {
    }

    public static void invitedVisitorEntersUninvitedDoesNot(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.buildArena(helper);
        ServerPlayer owner = MockPlayers.join(server, "InviteOwner");
        ServerPlayer guest = MockPlayers.join(server, "InviteGuest");
        BlockPos portal = helper.absolutePos(TestSupport.PORTAL_INTERIOR);

        helper.startSequence()
            .thenExecute(() -> {
                TestSupport.standInArenaPortal(helper, owner);
                TestSupport.activateArenaPortal(helper, owner);
                TestSupport.enterPortal(owner, portal);
            })
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                helper.assertTrue(InvitationManager.checkVisitAccess(server, guest, owner.getUUID()) == VisitDenialReason.NOT_INVITED,
                    "uninvited guest was not denied with NOT_INVITED");
                TestSupport.standInArenaPortal(helper, guest);
                TestSupport.enterPortal(guest, portal);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(!TestSupport.inPocket(guest), "uninvited guest got onto the island");
                helper.assertTrue(InvitationManager.invite(server, owner, guest), "invite failed");
            })
            .thenIdle(PORTAL_COOLDOWN_TICKS)
            .thenExecute(() -> TestSupport.enterPortal(guest, portal))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(guest, owner), "invited guest did not arrive on the owner's island"))
            .thenExecute(() -> {
                MockPlayers.leave(guest);
                MockPlayers.leave(owner);
            })
            .thenSucceed();
    }
}
