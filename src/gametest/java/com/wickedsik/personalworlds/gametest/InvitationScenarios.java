package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.VisitDenialReason;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** An uninvited player is denied, an invited one gets in. */
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

        helper.startSequence()
            .thenExecute(() -> {
                TestSupport.standInFrontOfArenaPortal(helper, owner);
                TestSupport.activateArenaPortal(helper, owner);
                TestSupport.walkIntoArenaPortal(helper, owner);
            })
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.arrivedOnIslandOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                helper.assertTrue(InvitationManager.checkVisitAccess(server, guest, owner.getUUID()) == VisitDenialReason.NOT_INVITED,
                    "uninvited guest was not denied with NOT_INVITED");
                TestSupport.standInFrontOfArenaPortal(helper, guest);
                TestSupport.walkIntoArenaPortal(helper, guest);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(!TestSupport.inPocket(guest), "uninvited guest got onto the island");
                helper.assertTrue(InvitationManager.invite(server, owner, guest), "invite failed");
            })
            .thenIdle(PORTAL_COOLDOWN_TICKS)
            .thenExecute(() -> {
                TestSupport.standInFrontOfArenaPortal(helper, guest);
                TestSupport.walkIntoArenaPortal(helper, guest);
            })
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.arrivedOnIslandOf(guest, owner), "invited guest did not arrive on the owner's island"))
            .thenExecute(() -> {
                MockPlayers.leave(guest);
                MockPlayers.leave(owner);
            })
            .thenSucceed();
    }
}
