package com.wickedsik.personalworlds.player;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.CommandCompat;
import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.compat.TeleportCompat;
import com.wickedsik.personalworlds.compat.TextCompat;
import com.wickedsik.personalworlds.compat.WorldCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.portal.PortalHelper;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

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

    /**
     * Check if a visitor can access an owner's dimension with full access control.
     * Implements the following rules (in order):
     * 1. Admins (OP level 2+) can always visit
     * 2. Owner can always access their own dimension
     * 3. Visitor must have an invitation
     * 4. If Always Welcome feature enabled and invitation has alwaysWelcome flag, allow visit
     * 5. Host must be online
     * 6. If config.allowVisitWhenHostNotHome is false, host must be on their own island
     *
     * @param server The Minecraft server
     * @param visitor The visiting player entity (needed for admin check)
     * @param ownerUuid The UUID of the dimension owner
     * @return VisitDenialReason indicating whether visit is allowed or why it's denied
     */
    public static VisitDenialReason checkVisitAccess(MinecraftServer server, ServerPlayerEntity visitor, UUID ownerUuid) {
        UUID visitorUuid = visitor.getUuid();

        // 1. Admin bypass (OP level 2+)
        if (CommandCompat.hasPermissionLevel(visitor, 2)) {
            return VisitDenialReason.ALLOWED;
        }

        // 2. Owner can always access their own dimension
        if (visitorUuid.equals(ownerUuid)) {
            return VisitDenialReason.ALLOWED;
        }

        // 3. Check for invitation
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        if (!dataManager.hasInvitationFrom(visitorUuid, ownerUuid)) {
            return VisitDenialReason.NOT_INVITED;
        }

        // 4. Always Welcome bypass (if feature enabled)
        if (ModConfig.get().enableAlwaysWelcome) {
            if (dataManager.isAlwaysWelcome(ownerUuid, visitorUuid)) {
                return VisitDenialReason.ALLOWED;
            }
        }

        // 5. Check if host is online
        ServerPlayerEntity host = server.getPlayerManager().getPlayer(ownerUuid);
        if (host == null) {
            return VisitDenialReason.HOST_OFFLINE;
        }

        // 6. Check if host is "home" (on their own island) - if config requires it
        if (!ModConfig.get().allowVisitWhenHostNotHome) {
            if (!isPlayerHome(host, ownerUuid)) {
                return VisitDenialReason.HOST_NOT_HOME;
            }
        }

        // 7. All checks passed
        return VisitDenialReason.ALLOWED;
    }

    /**
     * Check if a player is "home" - on their own personal island.
     *
     * @param player The player to check
     * @param playerUuid The player's UUID (for dimension ownership check)
     * @return true if the player is in their own personal dimension
     */
    private static boolean isPlayerHome(ServerPlayerEntity player, UUID playerUuid) {
        ServerWorld world = EntityCompat.getServerWorld(player);

        // Check if player is in any personal dimension
        if (!PortalHelper.isInPersonalDimension(world)) {
            return false;
        }

        // Check if it's THEIR OWN dimension (not visiting someone else's)
        Optional<UUID> dimensionOwner = PortalHelper.getDimensionOwner(world);
        return dimensionOwner.isPresent() && dimensionOwner.get().equals(playerUuid);
    }

    /**
     * Notify the host that someone tried to visit their island but was denied.
     * Only notifies if the host is online.
     *
     * @param server The Minecraft server
     * @param ownerUuid The UUID of the dimension owner
     * @param visitorName The name of the visitor who was denied
     * @param reason The reason for denial (only HOST_NOT_HOME triggers notification)
     */
    public static void notifyHostOfVisitAttempt(MinecraftServer server, UUID ownerUuid, String visitorName, VisitDenialReason reason) {
        // Only notify for HOST_NOT_HOME (host is online but not home)
        // Don't notify for HOST_OFFLINE (they're not there to receive it)
        if (reason != VisitDenialReason.HOST_NOT_HOME) {
            return;
        }

        ServerPlayerEntity host = server.getPlayerManager().getPlayer(ownerUuid);
        if (host != null) {
            host.sendMessage(
                Text.translatable("pocketislands.visit.attempted.not_home", visitorName)
                    .formatted(Formatting.GRAY),
                false
            );
        }
    }

    // --- Invitation Operations ---

    /**
     * Invite a player to the owner's dimension (standard invitation).
     *
     * @param server The Minecraft server
     * @param owner The dimension owner
     * @param guest The player being invited
     * @return true if invitation was successful
     */
    public static boolean invite(MinecraftServer server, ServerPlayerEntity owner, ServerPlayerEntity guest) {
        return invite(server, owner, guest, false);
    }

    /**
     * Invite a player to the owner's dimension.
     *
     * @param server The Minecraft server
     * @param owner The dimension owner
     * @param guest The player being invited
     * @param alwaysWelcome If true, guest can visit when host is offline/away
     * @return true if invitation was successful
     */
    public static boolean invite(MinecraftServer server, ServerPlayerEntity owner, ServerPlayerEntity guest, boolean alwaysWelcome) {
        UUID ownerUuid = owner.getUuid();
        UUID guestUuid = guest.getUuid();

        // Validation
        if (ownerUuid.equals(guestUuid)) {
            owner.sendMessage(Text.translatable("pocketislands.message.cannot_invite_self").formatted(Formatting.RED), false);
            return false;
        }

        String ownerName = owner.getName().getString();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        boolean added = dataManager.addInvitation(ownerUuid, ownerName, guestUuid, alwaysWelcome);

        if (added) {
            // Send appropriate message based on invitation type
            String messageKey = alwaysWelcome
                ? "pocketislands.command.invited_always_welcome"
                : "pocketislands.message.invite_sent";
            owner.sendMessage(Text.translatable(messageKey, guest.getName().getString()), false);
            guest.sendMessage(Text.translatable("pocketislands.message.invite_received", ownerName), false);

            // Play notification sounds
            VisualEffects.playInvitationSentEffect(owner);
            VisualEffects.playInvitationReceivedEffect(guest);

            PersonalWorldsMod.LOGGER.info("{} invited {} to their dimension (alwaysWelcome={})",
                ownerName, guest.getName().getString(), alwaysWelcome);
        } else {
            owner.sendMessage(Text.translatable("pocketislands.message.already_invited", guest.getName().getString()), false);
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
            owner.sendMessage(Text.translatable("pocketislands.message.invite_revoked", guestName), false);

            // Check if guest is online and eject if in owner's dimension
            ServerPlayerEntity guest = server.getPlayerManager().getPlayer(guestUuid);
            if (guest != null) {
                handleRevocationWhileVisiting(server, owner, guest);
            }

            PersonalWorldsMod.LOGGER.info("{} revoked {}'s invitation",
                owner.getName().getString(), guestName);
        } else {
            owner.sendMessage(Text.translatable("pocketislands.message.not_invited", guestName), false);
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
        ServerWorld guestWorld = EntityCompat.getServerWorld(guest);

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

        guest.sendMessage(Text.translatable("pocketislands.message.ejected")
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
                targetPos = Vec3d.ofCenter(WorldCompat.getSpawnPos(targetWorld));
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
            targetPos = Vec3d.ofCenter(WorldCompat.getSpawnPos(targetWorld));
            yaw = guest.getYaw();
            pitch = guest.getPitch();
        }

        TeleportCompat.teleport(guest, targetWorld, targetPos, yaw, pitch);

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
        MinecraftServer server = EntityCompat.getServer(player);
        if (server == null) return;

        PlayerDataManager dataManager = PlayerDataManager.get(server);
        UUID playerUuid = player.getUuid();

        player.sendMessage(Text.translatable("pocketislands.invitations.header").formatted(Formatting.GOLD), false);

        // Sent invitations (players who can visit you)
        Set<UUID> sent = dataManager.getSentInvitations(playerUuid);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.translatable("pocketislands.invitations.sent.header").formatted(Formatting.GREEN), false);

        if (sent.isEmpty()) {
            player.sendMessage(Text.translatable("pocketislands.invitations.sent.none").formatted(Formatting.GRAY), false);
        } else {
            boolean alwaysWelcomeEnabled = ModConfig.get().enableAlwaysWelcome;

            for (UUID guestUuid : sent) {
                String guestName = getPlayerName(server, guestUuid);

                // Build the entry text
                MutableText entryText = Text.literal(guestName).formatted(Formatting.YELLOW);

                // Add Always Welcome toggle button if feature is enabled
                if (alwaysWelcomeEnabled) {
                    boolean isAlwaysWelcome = dataManager.isAlwaysWelcome(playerUuid, guestUuid);

                    String toggleIcon = isAlwaysWelcome ? "★" : "☆";
                    Formatting toggleColor = isAlwaysWelcome ? Formatting.GREEN : Formatting.GRAY;
                    String toggleTooltipKey = isAlwaysWelcome
                        ? "pocketislands.invitations.sent.toggle_off_tooltip"
                        : "pocketislands.invitations.sent.toggle_on_tooltip";

                    MutableText toggleButton = Text.literal("[" + toggleIcon + "]")
                        .formatted(toggleColor)
                        .styled(style -> style
                            .withClickEvent(TextCompat.runCommand("/pi togglewelcome " + guestName))
                            .withHoverEvent(TextCompat.showText(Text.translatable(toggleTooltipKey)))
                        );

                    entryText = entryText.append(" ").append(toggleButton);
                }

                // Create clickable [Revoke] button
                MutableText revokeButton = Text.translatable("pocketislands.invitations.sent.revoke_button")
                    .formatted(Formatting.RED)
                    .styled(style -> style
                        .withClickEvent(TextCompat.runCommand("/pw uninvite " + guestName))
                        .withHoverEvent(TextCompat.showText(Text.translatable("pocketislands.invitations.sent.revoke_tooltip")))
                    );

                entryText = entryText.append(" ").append(revokeButton);

                player.sendMessage(Text.translatable("pocketislands.invitations.sent.entry", entryText), false);
            }
        }

        // Received invitations (dimensions you can visit)
        List<InvitationData> received = dataManager.getReceivedInvitations(playerUuid);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.translatable("pocketislands.invitations.received.header").formatted(Formatting.AQUA), false);

        if (received.isEmpty()) {
            player.sendMessage(Text.translatable("pocketislands.invitations.received.none").formatted(Formatting.GRAY), false);
        } else {
            for (InvitationData inv : received) {
                // Create clickable world name to teleport there
                MutableText worldLink = Text.translatable("pocketislands.invitations.received.world_name", inv.ownerName())
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style
                        .withClickEvent(TextCompat.runCommand("/pw go " + inv.ownerName()))
                        .withHoverEvent(TextCompat.showText(Text.translatable("pocketislands.invitations.received.visit_tooltip", inv.ownerName())))
                    );

                player.sendMessage(Text.translatable("pocketislands.invitations.received.entry", worldLink), false);
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
