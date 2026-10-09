package com.wickedsik.personalworlds.gametest.platform.neoforge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.gametest.GameRuleScenarios;
import com.wickedsik.personalworlds.gametest.InvitationScenarios;
import com.wickedsik.personalworlds.gametest.PortalScenarios;
import com.wickedsik.personalworlds.gametest.UnloadScenarios;
import com.wickedsik.personalworlds.gametest.VoidScenarios;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NeoForge registration. Since 1.21.5 tests are registry entries: each scenario is a
 * test function ({@code Registries.TEST_FUNCTION}) plus a test instance that points at it
 * ({@code RegisterGameTestsEvent}). {@code GameTestServer} runs every registered instance.
 * Bodies live in the common scenario classes. Infiniverse levels read day time from the
 * overworld, so the pocket clock follows it.
 */
@EventBusSubscriber(modid = PersonalWorldsMod.MOD_ID)
public final class NeoForgeGameTests {

    private static final ResourceLocation ARENA = IdentifierCompat.modId("arena");
    private static final int TIMEOUT = 400;

    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("portal_activation_and_first_entry", PortalScenarios::activationAndFirstEntry);
        TESTS.put("return_to_stored_position", PortalScenarios::returnToStoredPosition);
        TESTS.put("invitations", InvitationScenarios::invitedVisitorEntersUninvitedDoesNot);
        TESTS.put("pocket_game_rules", helper -> GameRuleScenarios.pocketOverridesOverworldUnchanged(helper, false));
        TESTS.put("pocket_death_keeps_inventory", GameRuleScenarios::pocketDeathKeepsInventory);
        TESTS.put("overworld_death_drops_inventory", GameRuleScenarios::overworldDeathDropsInventory);
        TESTS.put("void_ejection", VoidScenarios::fallingIntoVoidEjects);
        TESTS.put("reenter_while_unloading", UnloadScenarios::reenterWhileUnloadingKeepsPlayer);
        TESTS.put("rejoin_while_unloading", UnloadScenarios::rejoinWhileUnloadingKeepsPlayer);
        TESTS.put("rejoin_after_unload", UnloadScenarios::rejoinAfterUnloadReturnsToIsland);

        // NeoForge has no report switch; GameTestServer flushes the global reporter when it finishes
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

    private NeoForgeGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper ->
            TESTS.forEach((name, body) -> helper.register(IdentifierCompat.modId(name), body)));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(IdentifierCompat.modId("default"));
        TESTS.keySet().forEach(name -> {
            ResourceLocation id = IdentifierCompat.modId(name);
            event.registerTest(id, new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id),
                new TestData<>(environment, ARENA, TIMEOUT, 0, true, Rotation.NONE)));
        });
    }
}
