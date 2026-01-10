package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic layer for invitation management.
 * Provides high-level operations for inviting, uninviting, and permission checking.
 */
public class InvitationManager {

    // --- Permission Checks ---

    /**
     * Check if a visitor can access an owner's dimension.
     * A visitor can access if they are the owner OR have a valid invitation.
     *
     * @param server The Minecraft server
     * @param visitorUuid The UUID of the visiting player
     * @param ownerUuid The UUID of the dimension owner
     * @return true if the visitor has permission to enter
     */
    public static boolean canVisit(MinecraftServer server, UUID visitorUuid, UUID ownerUuid) {
        // Owner can always access their own dimension
        if (visitorUuid.equals(ownerUuid)) {
            return true;
        }

        // Check for invitation
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        return dataManager.hasInvitationFrom(visitorUuid, ownerUuid);
    }

    // --- Invitation Operations ---

    /**
     * Invite a player to the owner's dimension.
     *
     * @param server The Minecraft server
     * @param owner The dimension owner
     * @param guest The player being invited
     * @return true if invitation was successful
     */
    public static boolean invite(MinecraftServer server, ServerPlayerEntity owner, ServerPlayerEntity guest) {
        UUID ownerUuid = owner.getUuid();
        UUID guestUuid = guest.getUuid();

        // Validation
        if (ownerUuid.equals(guestUuid)) {
            owner.sendMessage(Text.translatable("personalworlds.message.cannot_invite_self").formatted(Formatting.RED), false);
            return false;
        }

        String ownerName = owner.getName().getString();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        boolean added = dataManager.addInvitation(ownerUuid, ownerName, guestUuid);

        if (added) {
            owner.sendMessage(Text.translatable("personalworlds.message.invite_sent", guest.getName().getString()), false);
            guest.sendMessage(Text.translatable("personalworlds.message.invite_received", ownerName), false);

            // Play notification sounds
            VisualEffects.playInvitationSentEffect(owner);
            VisualEffects.playInvitationReceivedEffect(guest);

            PersonalWorldsMod.LOGGER.info("{} invited {} to their dimension",
                ownerName, guest.getName().getString());
        } else {
            owner.sendMessage(Text.translatable("personalworlds.message.already_invited", guest.getName().getString()), false);
        }

        return added;
    }

    /**
     * Revoke an invitation from a guest.
     * If the guest is currently in the owner's dimension, they will be ejected.
     *
     * @param server The Minecraft server
     * @param owner The dimension owner
     * @param guestUuid The UUID of the player being uninvited
     * @param guestName The name of the guest (for messaging)
     * @return true if the invitation was revoked
     */
    public static boolean uninvite(MinecraftServer server, ServerPlayerEntity owner, UUID guestUuid, String guestName) {
        UUID ownerUuid = owner.getUuid();

        PlayerDataManager dataManager = PlayerDataManager.get(server);
        boolean removed = dataManager.removeInvitation(ownerUuid, guestUuid);

        if (removed) {
            owner.sendMessage(Text.translatable("personalworlds.message.invite_revoked", guestName), false);

            // Check if guest is online and eject if in owner's dimension
            ServerPlayerEntity guest = server.getPlayerManager().getPlayer(guestUuid);
            if (guest != null) {
                handleRevocationWhileVisiting(server, owner, guest);
            }

            PersonalWorldsMod.LOGGER.info("{} revoked {}'s invitation",
                owner.getName().getString(), guestName);
        } else {
            owner.sendMessage(Text.translatable("personalworlds.message.not_invited", guestName), false);
        }

        return removed;
    }

    /**
     * Check if a guest is in the owner's dimension and eject them if so.
     *
     * @param server The Minecraft server
     * @param owner The dimension owner
     * @param guest The guest player to check and possibly eject
     */
    private static void handleRevocationWhileVisiting(MinecraftServer server, ServerPlayerEntity owner, ServerPlayerEntity guest) {
        ServerWorld guestWorld = guest.getServerWorld();

        // Check if guest is in a personal dimension
        if (!PortalHelper.isInPersonalDimension(guestWorld)) {
            return;
        }

        // Check if it's the owner's dimension
        String dimPath = guestWorld.getRegistryKey().getValue().getPath();
        String ownerDimPath = "pw_" + owner.getUuid().toString();

        if (!dimPath.equals(ownerDimPath)) {
            return;
        }

        // Guest is in owner's dimension - eject them
        // Play warning sound before ejection
        VisualEffects.playInvitationRevokedEffect(guest);

        guest.sendMessage(Text.translatable("personalworlds.message.ejected")
            .formatted(Formatting.GOLD), false);

        // Try to return to stored position, fallback to overworld spawn
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(guest.getUuid());

        ServerWorld targetWorld;
        Vec3d targetPos;
        float yaw, pitch;

        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getWorld(returnData.dimension());

            if (targetWorld == null) {
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(targetWorld.getSpawnPos());
                yaw = guest.getYaw();
                pitch = guest.getPitch();
            } else {
                targetPos = Vec3d.ofCenter(returnData.position());
                yaw = returnData.yaw();
                pitch = returnData.pitch();
            }

            dataManager.clearReturnData(guest.getUuid());
        } else {
            targetWorld = server.getOverworld();
            targetPos = Vec3d.ofCenter(targetWorld.getSpawnPos());
            yaw = guest.getYaw();
            pitch = guest.getPitch();
        }

        TeleportTarget target = new TeleportTarget(targetPos, Vec3d.ZERO, yaw, pitch);
        FabricDimensions.teleport(guest, targetWorld, target);

        PersonalWorldsMod.LOGGER.info("Ejected {} from {}'s dimension due to revoked invitation",
            guest.getName().getString(), owner.getName().getString());
    }

    // --- Display Invitations ---

    /**
     * Show all invitations for a player (both sent and received).
     *
     * @param player The player viewing their invitations
     */
    public static void showInvitations(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerDataManager dataManager = PlayerDataManager.get(server);
        UUID playerUuid = player.getUuid();

        player.sendMessage(Text.translatable("personalworlds.invitations.header").formatted(Formatting.GOLD), false);

        // Sent invitations (players who can visit you)
        Set<UUID> sent = dataManager.getSentInvitations(playerUuid);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.translatable("personalworlds.invitations.sent.header").formatted(Formatting.GREEN), false);

        if (sent.isEmpty()) {
            player.sendMessage(Text.translatable("personalworlds.invitations.sent.none").formatted(Formatting.GRAY), false);
        } else {
            for (UUID guestUuid : sent) {
                String guestName = getPlayerName(server, guestUuid);

                // Create clickable [Revoke] button
                MutableText revokeButton = Text.translatable("personalworlds.invitations.sent.revoke_button")
                    .formatted(Formatting.RED)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pw uninvite " + guestName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Text.translatable("personalworlds.invitations.sent.revoke_tooltip")))
                    );

                player.sendMessage(Text.translatable("personalworlds.invitations.sent.entry",
                    Text.literal(guestName).formatted(Formatting.YELLOW)
                        .append(" ")
                        .append(revokeButton)), false);
            }
        }

        // Received invitations (dimensions you can visit)
        List<InvitationData> received = dataManager.getReceivedInvitations(playerUuid);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.translatable("personalworlds.invitations.received.header").formatted(Formatting.AQUA), false);

        if (received.isEmpty()) {
            player.sendMessage(Text.translatable("personalworlds.invitations.received.none").formatted(Formatting.GRAY), false);
        } else {
            for (InvitationData inv : received) {
                // Create clickable world name to teleport there
                MutableText worldLink = Text.translatable("personalworlds.invitations.received.world_name", inv.ownerName())
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pw go " + inv.ownerName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Text.translatable("personalworlds.invitations.received.visit_tooltip", inv.ownerName())))
                    );

                player.sendMessage(Text.translatable("personalworlds.invitations.received.entry", worldLink), false);
            }
        }
    }

    /**
     * Get a player's display name by UUID.
     * Tries online player first, then DimensionRegistry, then truncated UUID.
     */
    private static String getPlayerName(MinecraftServer server, UUID playerUuid) {
        // Try online player
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            return player.getName().getString();
        }

        // Try dimension registry
        DimensionRegistry registry = DimensionRegistry.get(server);
        Optional<PlayerDimensionData> data = registry.getDimensionData(playerUuid);
        if (data.isPresent()) {
            return data.get().ownerName();
        }

        // Fallback to truncated UUID
        return playerUuid.toString().substring(0, 8);
    }
}
