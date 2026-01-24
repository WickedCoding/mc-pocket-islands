package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Executor for player-facing commands.
 * Handles invitation system for pocket islands.
 * No permission required for these commands.
 *
 * Commands:
 * - /pi invite <player> - Invite a player to your island
 * - /pi uninvite <player> - Revoke an invitation
 * - /pi invites - Show your invitations
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
     * @param alwaysWelcome If true, guest can visit when owner is offline/away
     * @return Command result (InvitationManager sends messages directly)
     */
    public CommandResult invite(ServerPlayerEntity owner, ServerPlayerEntity guest, boolean alwaysWelcome) {
        InvitationManager.invite(owner.getServer(), owner, guest, alwaysWelcome);
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
     * Toggle the Always Welcome status for an existing invitation.
     *
     * @param owner The island owner
     * @param guestName The name of the invited player
     * @return Command result
     */
    public CommandResult toggleWelcome(ServerPlayerEntity owner, String guestName) {
        MinecraftServer server = owner.getServer();

        // Find the guest in sent invitations
        Optional<PlayerLookupService.PlayerReference> playerRef =
            playerLookup.findInInvitations(server, owner.getUuid(), guestName);

        if (playerRef.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.not_invited_by_you", guestName)
            );
        }

        PlayerLookupService.PlayerReference ref = playerRef.get();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        Optional<Boolean> newValue = dataManager.toggleAlwaysWelcome(owner.getUuid(), ref.uuid());

        if (newValue.isEmpty()) {
            return CommandResult.error(
                Text.translatable("personalworlds.command.error.not_invited_by_you", ref.resolvedName())
            );
        }

        String messageKey = newValue.get()
            ? "personalworlds.command.toggle_welcome_on"
            : "personalworlds.command.toggle_welcome_off";

        return CommandResult.success(Text.translatable(messageKey, ref.resolvedName()));
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
}
