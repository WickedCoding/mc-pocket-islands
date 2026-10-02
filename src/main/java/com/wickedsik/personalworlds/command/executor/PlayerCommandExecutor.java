package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.command.service.PlayerLookupService;
import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.RegistryCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.portal.PortalColor;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Executor for player-facing commands.
 * Handles invitation system and portal information for pocket islands.
 * No permission required for these commands.
 *
 * Commands:
 * - /pi invite <player> - Invite a player to your island
 * - /pi uninvite <player> - Revoke an invitation
 * - /pi invites - Show your invitations
 * - /pi portals - Show all portal types and your island status
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
    public CommandResult invite(ServerPlayer owner, ServerPlayer guest, boolean alwaysWelcome) {
        InvitationManager.invite(EntityCompat.getServer(owner), owner, guest, alwaysWelcome);
        return CommandResult.silent();
    }

    /**
     * Revoke an invitation from a player.
     *
     * @param owner The island owner revoking the invite
     * @param guestName The name of the player to uninvite
     * @return Command result
     */
    public CommandResult uninvite(ServerPlayer owner, String guestName) {
        MinecraftServer server = EntityCompat.getServer(owner);

        Optional<PlayerLookupService.PlayerReference> playerRef =
            playerLookup.findInInvitations(server, owner.getUUID(), guestName);

        if (playerRef.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.player_not_found", guestName)
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
    public CommandResult toggleWelcome(ServerPlayer owner, String guestName) {
        MinecraftServer server = EntityCompat.getServer(owner);

        // Find the guest in sent invitations
        Optional<PlayerLookupService.PlayerReference> playerRef =
            playerLookup.findInInvitations(server, owner.getUUID(), guestName);

        if (playerRef.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.not_invited_by_you", guestName)
            );
        }

        PlayerLookupService.PlayerReference ref = playerRef.get();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        Optional<Boolean> newValue = dataManager.toggleAlwaysWelcome(owner.getUUID(), ref.uuid());

        if (newValue.isEmpty()) {
            return CommandResult.error(
                Component.translatable("pocketislands.command.error.not_invited_by_you", ref.resolvedName())
            );
        }

        String messageKey = newValue.get()
            ? "pocketislands.command.toggle_welcome_on"
            : "pocketislands.command.toggle_welcome_off";

        return CommandResult.success(Component.translatable(messageKey, ref.resolvedName()));
    }

    /**
     * Show the player's sent and received invitations.
     *
     * @param player The player viewing invitations
     * @return Command result (InvitationManager sends messages directly)
     */
    public CommandResult showInvitations(ServerPlayer player) {
        InvitationManager.showInvitations(player);
        return CommandResult.silent();
    }

    /**
     * Show all configured portal types and the player's island status.
     * Sends multi-line feedback directly to the command source.
     *
     * @param player The player requesting info (for island status lookup)
     * @param source Command source for sending feedback
     */
    public void showPortals(ServerPlayer player, CommandSourceStack source) {
        List<ModConfig.PortalConfig> portalTypes = ModConfig.get().portalTypes;

        // === Portal Types Header ===
        source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.header")
            .withStyle(ChatFormatting.GOLD), false);

        if (portalTypes.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.no_portals")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (int i = 0; i < portalTypes.size(); i++) {
                sendPortalTypeInfo(source, i, portalTypes.get(i));
            }
        }

        // === Your Island Section ===
        source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.your_island_header")
            .withStyle(ChatFormatting.GOLD), false);

        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        Optional<PlayerDimensionData> islandData = registry.getDimensionData(player.getUUID());

        if (islandData.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.no_island")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            PlayerDimensionData data = islandData.get();
            int typeIndex = data.portalTypeIndex();
            List<ModConfig.PortalConfig> currentTypes = ModConfig.get().portalTypes;

            if (typeIndex >= 0 && typeIndex < currentTypes.size()) {
                Block frameBlock = ModBlocks.getFrameBlock(typeIndex);
                Component frameName = Component.translatable(frameBlock.getDescriptionId());
                PortalColor color = ModBlocks.getPortalColor(typeIndex);
                String colorName = formatColorName(color);

                source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.your_type",
                    frameName, colorName).withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.your_type_unknown")
                    .withStyle(ChatFormatting.RED), false);
            }
        }
    }

    /**
     * Send formatted info lines for a single portal type.
     */
    private void sendPortalTypeInfo(CommandSourceStack source, int index, ModConfig.PortalConfig config) {
        // Resolve frame block name
        Block frameBlock = ModBlocks.getFrameBlock(index);
        Component frameName = Component.translatable(frameBlock.getDescriptionId());

        // Resolve color display name
        PortalColor color = ModBlocks.getPortalColor(index);
        String colorName = formatColorName(color);

        // Type header: [1] Nether Bricks (Red)
        source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.type_header",
            index + 1, frameName, colorName).withStyle(ChatFormatting.YELLOW), false);

        // Activation item
        Item activationItem = ModItems.getActivationItem(index);
        Component itemName = Component.translatable(activationItem.getDescriptionId());
        source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.activate",
            itemName).withStyle(ChatFormatting.GRAY), false);

        // Island layers
        Component layersText = buildLayersText(config.islandLayers);
        source.sendSuccess(() -> Component.translatable("pocketislands.command.portals.layers",
            layersText).withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * Build a comma-separated Text of human-readable block names from layer block IDs.
     * Falls back to raw block ID string for unresolvable entries.
     */
    private static Component buildLayersText(String[] layers) {
        if (layers == null || layers.length == 0) {
            return Component.literal("None");
        }

        MutableComponent result = Component.empty();
        for (int i = 0; i < layers.length; i++) {
            if (i > 0) {
                result.append(", ");
            }

            ResourceLocation id = IdentifierCompat.tryParse(layers[i]);
            if (id != null) {
                Block block = RegistryCompat.get(BuiltInRegistries.BLOCK, id);
                if (block != Blocks.AIR || "minecraft:air".equals(layers[i])) {
                    result.append(Component.translatable(block.getDescriptionId()));
                } else {
                    result.append(Component.literal(layers[i]));
                }
            } else {
                result.append(Component.literal(layers[i]));
            }
        }
        return result;
    }

    /**
     * Format a PortalColor enum value as a capitalized display name.
     * Delegates to {@link PortalColor#getDisplayName()}.
     */
    private static String formatColorName(PortalColor color) {
        return color.getDisplayName();
    }
}
