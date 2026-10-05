package com.wickedsik.personalworlds.gametest;

import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Per-dimension game rules, and keepInventory across dimensions. */
public final class GameRuleScenarios {

    private static final int CLOCK_TICKS = 40;
    private static final int DIAMONDS = 5;

    private GameRuleScenarios() {
    }

    /**
     * Pocket overrides apply while the overworld keeps its own values.
     *
     * @param pocketHasOwnClock true where the pocket keeps its own day time (Fantasy on
     *                          Fabric); false where it follows the overworld (Infiniverse)
     */
    public static void pocketOverridesOverworldUnchanged(GameTestHelper helper, boolean pocketHasOwnClock) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = MockPlayers.join(server, "RulesOwner");
        AtomicReference<ServerLevel> pocket = new AtomicReference<>();
        AtomicLong pocketTime = new AtomicLong();
        AtomicLong overworldTime = new AtomicLong();

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner, server, owner.getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                ServerLevel island = TestSupport.level(owner);
                ServerLevel overworld = server.overworld();
                pocket.set(island);
                helper.assertTrue(Rules.randomTickSpeed(island) == TestSupport.POCKET_RANDOM_TICK_SPEED,
                    "pocket randomTickSpeed " + Rules.randomTickSpeed(island));
                helper.assertTrue(Rules.randomTickSpeed(overworld) != TestSupport.POCKET_RANDOM_TICK_SPEED,
                    "overworld randomTickSpeed picked up the pocket override");
                helper.assertTrue(Rules.keepInventory(island), "pocket keepInventory is false");
                helper.assertTrue(!Rules.keepInventory(overworld), "overworld keepInventory is true");
                pocketTime.set(island.getDayTime());
                overworldTime.set(overworld.getDayTime());
            })
            .thenIdle(CLOCK_TICKS)
            .thenExecute(() -> {
                long overworldNow = server.overworld().getDayTime();
                long pocketNow = pocket.get().getDayTime();
                helper.assertTrue(overworldNow > overworldTime.get(), "overworld clock did not advance");
                if (pocketHasOwnClock) {
                    helper.assertTrue(pocketNow == pocketTime.get(), "pocket clock advanced with daylight cycle off: " + pocketTime.get() + " -> " + pocketNow);
                } else {
                    helper.assertTrue(pocketNow == overworldNow, "pocket clock " + pocketNow + " does not follow the overworld " + overworldNow);
                }
                MockPlayers.leave(owner);
            })
            .thenSucceed();
    }

    /** A death on the island (pocket keepInventory=true) keeps the items through an overworld respawn. */
    public static void pocketDeathKeepsInventory(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = MockPlayers.join(server, "KeepOwner");

        helper.startSequence()
            .thenExecute(() -> helper.assertTrue(PortalHelper.teleportToDimension(owner, server, owner.getUUID()), "teleportToDimension failed"))
            .thenWaitUntil(() -> helper.assertTrue(TestSupport.inPocketOf(owner, owner), "owner did not arrive on their island"))
            .thenExecute(() -> {
                owner.getInventory().add(new ItemStack(Items.DIAMOND, DIAMONDS));
                ServerLevel island = TestSupport.level(owner);
                AABB around = owner.getBoundingBox().inflate(8);
                owner.die(owner.damageSources().generic());
                helper.assertTrue(droppedDiamonds(island, around) == 0, "items dropped on the island despite keepInventory");

                ServerPlayer respawned = Respawn.respawn(server, owner);
                helper.assertTrue(!TestSupport.inPocket(respawned), "respawned on the island, expected the overworld");
                int kept = TestSupport.count(respawned.getInventory(), Items.DIAMOND);
                helper.assertTrue(kept == DIAMONDS, "kept " + kept + " of " + DIAMONDS + " diamonds after respawn");
                MockPlayers.leave(respawned);
            })
            .thenSucceed();
    }

    /**
     * A death in the overworld (keepInventory=false there) drops the items. The player dies
     * inside the test arena, whose chunks the framework keeps loaded, a few ticks after
     * joining; drops are counted a tick later.
     */
    public static void overworldDeathDropsInventory(GameTestHelper helper) {
        TestSupport.ensureConfigured();
        MinecraftServer server = helper.getLevel().getServer();
        TestSupport.buildArena(helper);
        ServerPlayer player = MockPlayers.join(server, "DropPlayer");
        AtomicReference<AABB> deathArea = new AtomicReference<>();

        helper.startSequence()
            .thenExecute(() -> {
                Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
                TestSupport.moveWithinLevel(player, spot.x, spot.y, spot.z);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(TestSupport.level(player) == helper.getLevel(), "player is not in the test level");
                player.getInventory().add(new ItemStack(Items.DIAMOND, DIAMONDS));
                deathArea.set(player.getBoundingBox().inflate(8));
                player.die(player.damageSources().generic());
            })
            .thenIdle(1)
            .thenExecute(() -> {
                ServerLevel overworld = helper.getLevel();
                int dropped = droppedDiamonds(overworld, deathArea.get());
                helper.assertTrue(dropped == DIAMONDS, "dropped " + dropped + " of " + DIAMONDS + " diamonds in the overworld");

                ServerPlayer respawned = Respawn.respawn(server, player);
                int kept = TestSupport.count(respawned.getInventory(), Items.DIAMOND);
                helper.assertTrue(kept == 0, "kept " + kept + " diamonds after an overworld death");
                overworld.getEntitiesOfClass(ItemEntity.class, deathArea.get()).forEach(Entity::discard);
                MockPlayers.leave(respawned);
            })
            .thenSucceed();
    }

    private static int droppedDiamonds(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(ItemEntity.class, area).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.getItem() == Items.DIAMOND)
            .mapToInt(ItemStack::getCount)
            .sum();
    }
}
