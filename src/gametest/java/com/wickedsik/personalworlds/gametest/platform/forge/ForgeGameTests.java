package com.wickedsik.personalworlds.gametest.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.gametest.GameRuleScenarios;
import com.wickedsik.personalworlds.gametest.InvitationScenarios;
import com.wickedsik.personalworlds.gametest.PortalScenarios;
import com.wickedsik.personalworlds.gametest.VoidScenarios;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;

/**
 * Forge registration, found by annotation scan when the namespace is listed in
 * {@code -Dforge.enabledGameTestNamespaces}. Bodies live in the common scenario classes.
 * Infiniverse levels read day time from the overworld, so the pocket clock follows it.
 */
@GameTestHolder(PersonalWorldsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ForgeGameTests {

    private static final String ARENA = "arena";
    private static final int TIMEOUT = 400;

    // Forge has no report switch; the scan loads this class before tests run, and
    // GameTestServer flushes the global reporter when it finishes
    static {
        String reportFile = System.getProperty("pocketislands.gametest.report-file");
        if (reportFile != null) {
            try {
                File file = new File(reportFile);
                file.getParentFile().mkdirs();
                GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(file));
            } catch (Exception e) {
                throw new IllegalStateException("Could not set up the GameTest JUnit report at " + reportFile, e);
            }
        }
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void portalActivationAndFirstEntry(GameTestHelper helper) {
        PortalScenarios.activationAndFirstEntry(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void returnToStoredPosition(GameTestHelper helper) {
        PortalScenarios.returnToStoredPosition(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void invitations(GameTestHelper helper) {
        InvitationScenarios.invitedVisitorEntersUninvitedDoesNot(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void pocketGameRules(GameTestHelper helper) {
        GameRuleScenarios.pocketOverridesOverworldUnchanged(helper, false);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void pocketDeathKeepsInventory(GameTestHelper helper) {
        GameRuleScenarios.pocketDeathKeepsInventory(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void overworldDeathDropsInventory(GameTestHelper helper) {
        GameRuleScenarios.overworldDeathDropsInventory(helper);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void voidEjection(GameTestHelper helper) {
        VoidScenarios.fallingIntoVoidEjects(helper);
    }
}
