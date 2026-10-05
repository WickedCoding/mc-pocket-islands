package com.wickedsik.personalworlds.platform.forge;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.CommandCompat;
import com.wickedsik.personalworlds.platform.PlatformPermissions;
import com.wickedsik.personalworlds.util.PermissionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.exceptions.UnregisteredPermissionException;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge PermissionAPI (LuckPerms implements it). Nodes must be registered before use,
 * so every node in {@link PermissionHelper#ALL_NODES} is declared at startup.
 * <p>
 * A node's default (when no provider decides) is the OP level the caller passed, as
 * fabric-permissions-api does. Call sites choose that level, so it is remembered per
 * node from the latest check. Console and command blocks have no player and use the
 * OP level directly.
 */
final class ForgePermissions implements PlatformPermissions {

    // Used before any check has run for a node: deny unless fully opped
    private static final int UNKNOWN_FALLBACK_LEVEL = 4;

    private final Map<String, PermissionNode<Boolean>> nodes = new LinkedHashMap<>();
    private final Map<String, Integer> fallbackLevels = new ConcurrentHashMap<>();

    ForgePermissions() {
        for (String name : PermissionHelper.ALL_NODES) {
            int dot = name.indexOf('.');
            nodes.put(name, new PermissionNode<>(name.substring(0, dot), name.substring(dot + 1), PermissionTypes.BOOLEAN,
                (player, playerUuid, context) -> player != null
                    && CommandCompat.hasPermissionLevel(player, fallbackLevels.getOrDefault(name, UNKNOWN_FALLBACK_LEVEL))));
        }
        ForgeEvents.listen(PermissionGatherEvent.Nodes.class,
            event -> event.addNodes(nodes.values().toArray(new PermissionNode<?>[0])));
    }

    @Override
    public boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        fallbackLevels.put(node, fallbackLevel);

        PermissionNode<Boolean> permissionNode = nodes.get(node);
        ServerPlayer player = source.getPlayer();
        if (permissionNode == null || player == null) {
            return CommandCompat.hasPermissionLevel(source, fallbackLevel);
        }

        try {
            return PermissionAPI.getPermission(player, permissionNode);
        } catch (UnregisteredPermissionException e) {
            PersonalWorldsMod.LOGGER.debug("Permission node {} not registered, falling back to OP level", node, e);
            return CommandCompat.hasPermissionLevel(source, fallbackLevel);
        }
    }
}
