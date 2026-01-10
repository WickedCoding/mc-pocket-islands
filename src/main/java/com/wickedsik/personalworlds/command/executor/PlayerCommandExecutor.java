package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.portal.PortalHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Executor for player-facing commands.
 * Handles invitation system and visiting other players' islands.
 * No permission required for these commands.
 *
 * Commands:
 * - /pi invite <player> - Invite a player to your island
 * - /pi uninvite <player> - Revoke an invitation
 * - /pi invites - Show your invitations
 * - /pi go <player> - Visit a player's island (if invited)
 */
public class PlayerCommandExecutor {

    private final PlayerLookupService playerLookup;

    public PlayerCommandExecutor(PlayerLookupService playerLookup) {
        this.playerLookup = playerLookup;
    }

    /**
     * Invite another player to visit your pocket island.
     *
     * @param owner The island owner sending the invite
     * @param guest The player being invited
     * @return Command result (InvitationManager sends messages directly)
     */
    public CommandResult invite(ServerPlayerEntity owner, ServerPlayerEntity guest) {
        InvitationManager.invite(owner.getServer(), owner, guest);
        return CommandResult.silent();
    }

    /**
     * Revoke an invitation from a player.
     *
     * @param owner The island owner revoking the invite
     * @param guestName The name of the player to uninvite
     * @return Command result
     */
    public CommandResult uninvite(ServerPlayerEntity owner, String guestName) {
        MinecraftServer server = owner.getServer();

        Optional<PlayerLookupService.PlayerReference> playerRef =
            playerLookup.findInInvitations(server, owner.getUuid(), guestName);

        if (playerRef.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.player_not_found", guestName)
            );
        }

        PlayerLookupService.PlayerReference ref = playerRef.get();
        InvitationManager.uninvite(server, owner, ref.uuid(), ref.resolvedName());
        return CommandResult.silent();
    }

    /**
     * Show the player's sent and received invitations.
     *
     * @param player The player viewing invitations
     * @return Command result (InvitationManager sends messages directly)
     */
    public CommandResult showInvitations(ServerPlayerEntity player) {
        InvitationManager.showInvitations(player);
        return CommandResult.silent();
    }

    /**
     * Visit another player's pocket island.
     *
     * @param player The player who wants to visit
     * @param targetName The name of the island owner to visit
     * @return Command result
     */
    public CommandResult goToPlayer(ServerPlayerEntity player, String targetName) {
        MinecraftServer server = player.getServer();

        Optional<PlayerLookupService.PlayerReference> targetRef =
            playerLookup.findByName(server, targetName);

        if (targetRef.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.player_not_found", targetName)
            );
        }

        PlayerLookupService.PlayerReference ref = targetRef.get();

        if (!InvitationManager.canVisit(server, player.getUuid(), ref.uuid())) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.not_invited", ref.resolvedName())
            );
        }

        boolean success = PortalHelper.teleportToDimension(player, server, ref.uuid());
        return success ? CommandResult.silent() : CommandResult.error(
            Text.translatable("personalworlds.command.error.teleport_failed")
        );
    }
}
