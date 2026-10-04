package com.wickedsik.personalworlds.gametest.harness;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.gametest.MockPlayers;
import com.wickedsik.personalworlds.gametest.TestSupport;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * What a GameTest server can't cover: state across server restarts and world resets.
 * The Gradle task {@code harnessTest} starts a dedicated server three times in the same
 * directory, choosing the phase with {@code -Dpocketislands.harness}:
 * <ol>
 *   <li>{@code setup}: two players, an island with a marker block, a return position and
 *       an invitation; expectations go to {@code harness-state.properties}</li>
 *   <li>{@code verify}: after a restart, everything is still there</li>
 *   <li>{@code verify-reset}: the same after deleting the overworld, nether and end</li>
 * </ol>
 * Each phase writes {@code harness-report.properties} and stops the server. Files live in
 * the server's working directory. The loader layer forwards server start and tick events.
 */
public final class RestartHarness {

    public static final String PHASE_PROPERTY = "pocketislands.harness";

    private static final Path STATE_FILE = Path.of("harness-state.properties");
    private static final Path REPORT_FILE = Path.of("harness-report.properties");
    // Run a little after start, so the mod's own restore on server start has finished
    private static final int START_DELAY_TICKS = 40;
    private static final BlockPos MARKER = new BlockPos(0, 70, 0);

    private static int ticksSinceStart = -1;

    private RestartHarness() {
    }

    public static boolean enabled() {
        return System.getProperty(PHASE_PROPERTY) != null;
    }

    public static void onServerStarted(MinecraftServer server) {
        ticksSinceStart = 0;
    }

    public static void onServerTick(MinecraftServer server) {
        if (ticksSinceStart < 0 || ++ticksSinceStart != START_DELAY_TICKS) {
            return;
        }
        String phase = System.getProperty(PHASE_PROPERTY);
        List<String> failures = new ArrayList<>();
        try {
            switch (phase) {
                case "setup" -> setup(server, failures);
                case "verify", "verify-reset" -> verify(server, failures);
                default -> failures.add("unknown phase '" + phase + "'");
            }
        } catch (Exception e) {
            PersonalWorldsMod.LOGGER.error("Harness phase {} threw", phase, e);
            failures.add("exception: " + e);
        }
        writeReport(phase, failures);
        PersonalWorldsMod.LOGGER.info("Harness phase {} {}: {}", phase, failures.isEmpty() ? "passed" : "FAILED", failures);
        server.halt(false);
    }

    private static void setup(MinecraftServer server, List<String> failures) throws IOException {
        TestSupport.ensureConfigured();
        ServerPlayer owner = MockPlayers.join(server, "HarnessOwner");
        ServerPlayer guest = MockPlayers.join(server, "HarnessGuest");

        if (!PortalHelper.teleportToDimension(owner, server, owner.getUUID())) {
            failures.add("could not create the owner's island");
            return;
        }
        ServerLevel island = DimensionManager.getLoadedDimension(owner.getUUID());
        if (island == null) {
            failures.add("owner's island is not loaded after creation");
            return;
        }
        island.setBlockAndUpdate(MARKER, Blocks.GOLD_BLOCK.defaultBlockState());
        if (!InvitationManager.invite(server, owner, guest)) {
            failures.add("invite failed");
        }

        Optional<ReturnData> returnData = PlayerDataManager.get(server).getReturnData(owner.getUUID());
        if (returnData.isEmpty()) {
            failures.add("no return position stored for the owner");
            return;
        }

        Properties state = new Properties();
        state.setProperty("owner", owner.getUUID().toString());
        state.setProperty("guest", guest.getUUID().toString());
        state.setProperty("return.dimension", IdentifierCompat.fromKey(returnData.get().dimension()).toString());
        state.setProperty("return.pos", returnData.get().position().toShortString());
        try (OutputStream out = Files.newOutputStream(STATE_FILE)) {
            state.store(out, "Pocket Islands restart harness: expected state");
        }

        MockPlayers.leave(guest);
        MockPlayers.leave(owner);
    }

    private static void verify(MinecraftServer server, List<String> failures) throws IOException {
        Properties state = new Properties();
        try (InputStream in = Files.newInputStream(STATE_FILE)) {
            state.load(in);
        }
        UUID owner = UUID.fromString(state.getProperty("owner"));
        UUID guest = UUID.fromString(state.getProperty("guest"));

        if (DimensionRegistry.get(server).getDimensionData(owner).isEmpty()) {
            failures.add("island missing from the registry");
        }
        ServerLevel island = DimensionManager.getLoadedDimension(owner);
        if (island == null) {
            failures.add("island not restored on start");
        } else if (!island.getBlockState(MARKER).is(Blocks.GOLD_BLOCK)) {
            failures.add("marker block missing on the island: " + island.getBlockState(MARKER));
        }

        PlayerDataManager data = PlayerDataManager.get(server);
        Optional<ReturnData> returnData = data.getReturnData(owner);
        if (returnData.isEmpty()) {
            failures.add("return position lost");
        } else {
            String dimension = IdentifierCompat.fromKey(returnData.get().dimension()).toString();
            String pos = returnData.get().position().toShortString();
            if (!dimension.equals(state.getProperty("return.dimension")) || !pos.equals(state.getProperty("return.pos"))) {
                failures.add("return position changed: " + dimension + " " + pos + ", expected "
                    + state.getProperty("return.dimension") + " " + state.getProperty("return.pos"));
            }
        }
        if (!data.hasInvitationFrom(guest, owner)) {
            failures.add("invitation lost");
        }
    }

    private static void writeReport(String phase, List<String> failures) {
        Properties report = new Properties();
        report.setProperty("phase", String.valueOf(phase));
        report.setProperty("passed", String.valueOf(failures.isEmpty()));
        report.setProperty("failures", String.join(" | ", failures));
        try (OutputStream out = Files.newOutputStream(REPORT_FILE)) {
            report.store(out, "Pocket Islands restart harness");
        } catch (IOException e) {
            PersonalWorldsMod.LOGGER.error("Could not write {}", REPORT_FILE.toAbsolutePath(), e);
        }
    }
}
