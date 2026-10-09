package com.wickedsik.personalworlds.gametest.platform.fabric;

import com.wickedsik.personalworlds.gametest.GameRuleScenarios;
import com.wickedsik.personalworlds.gametest.InvitationScenarios;
import com.wickedsik.personalworlds.gametest.PortalScenarios;
import com.wickedsik.personalworlds.gametest.UnloadScenarios;
import com.wickedsik.personalworlds.gametest.VoidScenarios;
//? if >=1.21.5 {
/*import net.fabricmc.fabric.api.gametest.v1.GameTest;
*///?} else {
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
//?}
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric registration (fabric-gametest entrypoint); bodies live in the common scenario
 * classes. Fantasy gives each pocket its own clock, so the pocket clock freezes.
 */
//? if >=1.21.5 {
/*public class FabricGameTests {

    private static final String ARENA = "personalworlds:arena";
    private static final int TIMEOUT = 400;

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void portalActivationAndFirstEntry(GameTestHelper helper) {
        PortalScenarios.activationAndFirstEntry(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void returnToStoredPosition(GameTestHelper helper) {
        PortalScenarios.returnToStoredPosition(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void invitations(GameTestHelper helper) {
        InvitationScenarios.invitedVisitorEntersUninvitedDoesNot(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void pocketGameRules(GameTestHelper helper) {
        GameRuleScenarios.pocketOverridesOverworldUnchanged(helper, true);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void pocketDeathKeepsInventory(GameTestHelper helper) {
        GameRuleScenarios.pocketDeathKeepsInventory(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void overworldDeathDropsInventory(GameTestHelper helper) {
        GameRuleScenarios.overworldDeathDropsInventory(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void voidEjection(GameTestHelper helper) {
        VoidScenarios.fallingIntoVoidEjects(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void reenterWhileUnloading(GameTestHelper helper) {
        UnloadScenarios.reenterWhileUnloadingKeepsPlayer(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void rejoinWhileUnloading(GameTestHelper helper) {
        UnloadScenarios.rejoinWhileUnloadingKeepsPlayer(helper);
    }

    @GameTest(structure = ARENA, maxTicks = TIMEOUT)
    public void rejoinAfterUnload(GameTestHelper helper) {
        UnloadScenarios.rejoinAfterUnloadReturnsToIsland(helper);
    }
}
*///?} else {
public class FabricGameTests implements FabricGameTest {

    private static final String ARENA = "personalworlds:arena";
    private static final int TIMEOUT = 400;

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void portalActivationAndFirstEntry(GameTestHelper helper) {
        PortalScenarios.activationAndFirstEntry(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void returnToStoredPosition(GameTestHelper helper) {
        PortalScenarios.returnToStoredPosition(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void invitations(GameTestHelper helper) {
        InvitationScenarios.invitedVisitorEntersUninvitedDoesNot(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void pocketGameRules(GameTestHelper helper) {
        GameRuleScenarios.pocketOverridesOverworldUnchanged(helper, true);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void pocketDeathKeepsInventory(GameTestHelper helper) {
        GameRuleScenarios.pocketDeathKeepsInventory(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void overworldDeathDropsInventory(GameTestHelper helper) {
        GameRuleScenarios.overworldDeathDropsInventory(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void voidEjection(GameTestHelper helper) {
        VoidScenarios.fallingIntoVoidEjects(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void reenterWhileUnloading(GameTestHelper helper) {
        UnloadScenarios.reenterWhileUnloadingKeepsPlayer(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void rejoinWhileUnloading(GameTestHelper helper) {
        UnloadScenarios.rejoinWhileUnloadingKeepsPlayer(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public void rejoinAfterUnload(GameTestHelper helper) {
        UnloadScenarios.rejoinAfterUnloadReturnsToIsland(helper);
    }
}
//?}
