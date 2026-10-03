package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.compat.EntityCompat;
import com.wickedsik.personalworlds.compat.IdentifierCompat;
import com.wickedsik.personalworlds.compat.RegistryCompat;
import com.wickedsik.personalworlds.compat.TeleportCompat;
import com.wickedsik.personalworlds.compat.WorldCompat;
import com.wickedsik.personalworlds.config.ModConfig;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import com.wickedsik.personalworlds.player.InvitationManager;
import com.wickedsik.personalworlds.player.PlayerDataManager;
import com.wickedsik.personalworlds.player.ReturnData;
import com.wickedsik.personalworlds.player.VisitDenialReason;
import com.wickedsik.personalworlds.registry.ModBlocks;
import com.wickedsik.personalworlds.registry.ModItems;
import com.wickedsik.personalworlds.util.SafeSpawnFinder;
import com.wickedsik.personalworlds.util.VisualEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper class for portal operations including:
 * - Frame detection and validation
 * - Portal activation (filling frame with portal blocks)
 * - Portal ownership registration
 * - Permission-based teleportation between overworld and personal dimensions
 * - Starter platform creation for void worlds
 */
public class PortalHelper {

    // Portal frame dimensions (interior size)
    private static final int PORTAL_WIDTH = 2;   // Interior width
    private static final int PORTAL_HEIGHT = 3;  // Interior height

    // Spawn platform dimensions
    private static final int PLATFORM_RADIUS = 2;  // 5x5 platform
    private static final int PLATFORM_Y = 64;

    // --- Portal Activation ---

    /**
     * Attempt to activate a portal by detecting the frame and filling with portal blocks.
     * Also registers portal ownership and portal type for the activating player.
     *
     * @param world The world where the portal is being activated
     * @param clickedPos The position that was clicked (should be air inside frame)
     * @param player The player activating the portal
     * @param activationItem The item used to activate the portal
     * @return true if portal was successfully activated
     */
    public static boolean tryActivatePortal(Level world, BlockPos clickedPos, ServerPlayer player, Item activationItem) {
        if (world.isClientSide()) {
            return false;
        }

        MinecraftServer server = EntityCompat.getServer(player);
        if (server == null) {
            return false;
        }

        // Detect which portal type is being activated
        Optional<Integer> portalTypeOpt = detectPortalType(world, clickedPos, activationItem);
        if (portalTypeOpt.isEmpty()) {
            return false;
        }

        int portalTypeIndex = portalTypeOpt.get();

        // Get frame for this portal type
        Optional<PortalFrame> frame = detectFrame(world, clickedPos, portalTypeIndex);
        if (frame.isEmpty()) {
            return false;
        }

        PortalFrame portalFrame = frame.get();

        // Fill interior with portal blocks with the correct color
        PortalColor color = ModBlocks.getPortalColor(portalTypeIndex);
        BlockState portalState = ModBlocks.PERSONAL_PORTAL.get().defaultBlockState()
            .setValue(PersonalPortalBlock.AXIS, portalFrame.axis())
            .setValue(PersonalPortalBlock.COLOR, color);

        for (BlockPos pos : portalFrame.getInteriorPositions()) {
            world.setBlockAndUpdate(pos, portalState);
        }

        // Register portal ownership AND portal type for all portal blocks
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
        for (BlockPos pos : portalFrame.getInteriorPositions()) {
            ownershipManager.registerPortal(world, pos, player.getUUID(), portalTypeIndex);
        }

        // Play activation sound
        world.playSound(
            null,
            portalFrame.getCenter(),
            SoundEvents.END_PORTAL_SPAWN,
            SoundSource.BLOCKS,
            1.0f,
            1.0f
        );

        // Play particle effects
        VisualEffects.playPortalActivationEffects(world, portalFrame.getCenter());

        PersonalWorldsMod.LOGGER.info("Portal type {} activated at {} by {} (ownership registered)",
            portalTypeIndex, clickedPos, player.getName().getString());

        return true;
    }

    // --- Teleportation ---

    /**
     * Handle a player entering the portal.
     * Determines destination based on current location and permission checks.
     *
     * @param player The player entering the portal
     * @param portalPos The position of the portal block
     */
    public static void handlePortalEntry(ServerPlayer player, BlockPos portalPos) {
        MinecraftServer server = EntityCompat.getServer(player);
        if (server == null) {
            return;
        }

        // Acquire teleport lock to prevent race conditions
        if (!ConcurrentPortalGuard.tryAcquire(player, portalPos)) {
            // Already processing or on cooldown
            return;
        }

        try {
            ServerLevel currentWorld = EntityCompat.getServerWorld(player);

            if (isInPersonalDimension(currentWorld)) {
                // Going back to overworld (or original dimension)
                teleportToReturnPosition(player, server);
            } else {
                // Going to personal dimension - check permission first
                handleForwardPortalEntry(player, server, currentWorld, portalPos);
            }
        } finally {
            ConcurrentPortalGuard.release(player, portalPos);
        }
    }

    /**
     * Handle forward portal entry (overworld -> personal dimension).
     * Looks up portal owner and checks permission before teleporting.
     *
     * @param player The player entering the portal
     * @param server The Minecraft server
     * @param fromWorld The world the player is leaving
     * @param portalPos The position of the portal block
     */
    private static void handleForwardPortalEntry(
            ServerPlayer player,
            MinecraftServer server,
            ServerLevel fromWorld,
            BlockPos portalPos
    ) {
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
        Optional<UUID> portalOwnerOpt = ownershipManager.getOwner(fromWorld, portalPos);

        if (portalOwnerOpt.isEmpty()) {
            // Unclaimed portal - auto-claim for the entering player
            // Default to portal type 0 for auto-claimed portals
            PersonalWorldsMod.LOGGER.warn("Unclaimed portal at {} - auto-claiming for {} with default portal type",
                portalPos, player.getName().getString());
            ownershipManager.registerPortal(fromWorld, portalPos, player.getUUID(), 0);
            teleportToOwnerDimension(player, server, fromWorld, player.getUUID(), 0);
            return;
        }

        UUID portalOwner = portalOwnerOpt.get();

        // Get portal type from ownership manager
        int portalTypeIndex = ownershipManager.getPortalType(fromWorld, portalPos).orElse(0);

        // Full access control check (admin bypass, online/home checks)
        VisitDenialReason denialReason = InvitationManager.checkVisitAccess(server, player, portalOwner);

        if (denialReason.isAllowed()) {
            teleportToOwnerDimension(player, server, fromWorld, portalOwner, portalTypeIndex);
        } else {
            String ownerName = ownershipManager.getOwnerName(server, portalOwner);

            // Notify host if they're online but not home
            InvitationManager.notifyHostOfVisitAttempt(
                server, portalOwner, player.getName().getString(), denialReason
            );

            // Send appropriate denial message to visitor
            Component denialMessage = switch (denialReason) {
                case NOT_INVITED -> Component.translatable("pocketislands.command.error.not_invited", ownerName);
                case HOST_OFFLINE -> Component.translatable("pocketislands.visit.denied.offline", ownerName);
                case HOST_NOT_HOME -> Component.translatable("pocketislands.visit.denied.not_home", ownerName);
                case ALLOWED -> Component.empty(); // Should never happen
            };

            player.displayClientMessage(denialMessage.copy().withStyle(ChatFormatting.RED), false);
            PersonalWorldsMod.LOGGER.debug("{} denied entry to {}'s portal at {} (reason: {})",
                player.getName().getString(), ownerName, portalPos, denialReason);
        }
    }

    /**
     * Teleport player to an owner's personal dimension.
     * Stores return position and creates dimension if needed.
     *
     * @param player The player being teleported
     * @param server The Minecraft server
     * @param fromWorld The world the player is leaving
     * @param ownerUuid The UUID of the dimension owner
     * @param portalTypeIndex The portal type index (determines island materials)
     * @return true if teleportation succeeded, false if it failed
     */
    private static boolean teleportToOwnerDimension(
            ServerPlayer player,
            MinecraftServer server,
            ServerLevel fromWorld,
            UUID ownerUuid,
            int portalTypeIndex
    ) {
        UUID playerUuid = player.getUUID();
        boolean isOwnDimension = playerUuid.equals(ownerUuid);

        // Get dimension data for the owner
        DimensionRegistry registry = DimensionRegistry.get(server);
        Optional<PlayerDimensionData> dimDataOpt = registry.getDimensionData(ownerUuid);

        String ownerName;
        WorldGenType genType;

        if (dimDataOpt.isPresent()) {
            // Dimension exists in registry
            PlayerDimensionData dimData = dimDataOpt.get();
            ownerName = dimData.ownerName();
            genType = dimData.generatorType();
        } else if (isOwnDimension) {
            // Player's own dimension not yet created - allow first-time creation
            ownerName = player.getName().getString();
            genType = WorldGenType.VOID;
        } else {
            // Visitor trying to access a dimension that doesn't exist
            // This means the dimension was deleted - don't recreate it!
            PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
            String deletedOwnerName = ownershipManager.getOwnerName(server, ownerUuid);
            player.displayClientMessage(
                Component.literal("This portal's dimension no longer exists. ")
                    .append(Component.literal(deletedOwnerName).withStyle(ChatFormatting.YELLOW))
                    .append("'s world was deleted.")
                    .withStyle(ChatFormatting.RED),
                false
            );
            PersonalWorldsMod.LOGGER.info("Player {} tried to enter deleted dimension of {}",
                player.getName().getString(), deletedOwnerName);
            return false;
        }

        // Only store return position if coming from a NON-personal dimension
        // This preserves the original overworld return when island-hopping
        PlayerDataManager dataManager = PlayerDataManager.get(server);
        if (!isInPersonalDimension(fromWorld)) {
            // Offset 1 block backward from facing direction to avoid landing inside portal
            BlockPos returnPos = player.blockPosition().relative(player.getDirection().getOpposite());
            ReturnData returnData = new ReturnData(
                fromWorld.dimension(),
                returnPos,
                player.getYRot(),
                player.getXRot()
            );
            dataManager.setReturnData(playerUuid, returnData);
        }
        // If coming from a personal dimension, preserve existing return data (overworld position)

        // Get or create the owner's dimension
        ServerLevel targetWorld = DimensionManager.getOrCreatePlayerDimension(
            server, ownerUuid, ownerName, genType, portalTypeIndex
        );

        // Find destination and teleport
        BlockPos destinationPos = findExistingPortal(targetWorld)
            .map(portalPos -> findSafePositionNearPortal(targetWorld, portalPos))
            .orElseGet(() -> getOrCreateSpawnPlatform(targetWorld, genType, portalTypeIndex));

        // Track that player is now in this pocket dimension (for recovery if they log out)
        dataManager.setCurrentPocketDimension(playerUuid, targetWorld.dimension());

        // Play departure effects
        VisualEffects.playTeleportDepartureEffects(player);

        TeleportCompat.teleportToBlockPreserveRotation(player, targetWorld, destinationPos);

        // Play arrival effects and dimension entry sound
        VisualEffects.playTeleportArrivalEffects(player);
        VisualEffects.playDimensionEntryEffect(player);

        // Send appropriate message
        if (isOwnDimension) {
            player.displayClientMessage(Component.literal("Welcome to your pocket island!"), true);
            PersonalWorldsMod.LOGGER.info("Player {} entered their personal dimension",
                player.getName().getString());
        } else {
            player.displayClientMessage(Component.literal("Entering ")
                .append(Component.literal(ownerName).withStyle(ChatFormatting.YELLOW))
                .append("'s island"), true);
            PersonalWorldsMod.LOGGER.info("Player {} entered {}'s personal dimension",
                player.getName().getString(), ownerName);
        }

        return true;
    }

    /**
     * Get a player's display name by UUID.
     */
    private static String getPlayerName(MinecraftServer server, UUID playerUuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            return player.getName().getString();
        }
        return playerUuid.toString().substring(0, 8);
    }

    /**
     * Teleport player back to their stored return position.
     * Public to allow usage by commands (/pw leave) and portal exits.
     */
    public static void teleportToReturnPosition(ServerPlayer player, MinecraftServer server) {
        UUID playerUuid = player.getUUID();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(playerUuid);

        ServerLevel targetWorld;
        Vec3 targetPos;
        float yaw, pitch;

        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getLevel(returnData.dimension());

            if (targetWorld == null) {
                // Dimension deleted - use overworld
                PersonalWorldsMod.LOGGER.warn("Return dimension not found for player {}, using overworld",
                    player.getName().getString());
                targetWorld = server.overworld();
                targetPos = Vec3.atCenterOf(SafeSpawnFinder.findSafePosition(
                    targetWorld, WorldCompat.getSpawnPos(targetWorld)));
                yaw = player.getYRot();
                pitch = player.getXRot();
            } else if (!SafeSpawnFinder.isSafeSpawn(targetWorld, returnData.position())) {
                // Position no longer safe - find nearby safe spot
                BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, returnData.position());
                targetPos = Vec3.atCenterOf(safePos);
                yaw = returnData.yaw();
                pitch = returnData.pitch();
                PersonalWorldsMod.LOGGER.info("Return position unsafe, relocated player {} to {}",
                    player.getName().getString(), safePos);
            } else {
                targetPos = Vec3.atCenterOf(returnData.position());
                yaw = returnData.yaw();
                pitch = returnData.pitch();
            }

            // Clear return data after use
            dataManager.clearReturnData(playerUuid);
        } else {
            // No return data - try bed spawn first
            BlockPos bedPos = EntityCompat.getSpawnPointPosition(player);
            ServerLevel bedWorld = null;

            if (bedPos != null) {
                bedWorld = server.getLevel(EntityCompat.getSpawnPointDimension(player));
            }

            if (bedWorld != null) {
                // Use bed spawn
                BlockPos safePos = SafeSpawnFinder.findSafePosition(bedWorld, bedPos);
                targetWorld = bedWorld;
                targetPos = Vec3.atCenterOf(safePos);
                yaw = player.getYRot();
                pitch = player.getXRot();
                PersonalWorldsMod.LOGGER.debug("No return data for player {}, using bed spawn at {}",
                    player.getName().getString(), safePos);
            } else {
                // Fallback: overworld world spawn
                PersonalWorldsMod.LOGGER.debug("No return data for player {}, using overworld spawn",
                    player.getName().getString());
                targetWorld = server.overworld();
                targetPos = Vec3.atCenterOf(SafeSpawnFinder.findSafePosition(
                    targetWorld, WorldCompat.getSpawnPos(targetWorld)));
                yaw = player.getYRot();
                pitch = player.getXRot();
            }
        }

        // Clear pocket dimension tracking (player is leaving the pocket dimension)
        dataManager.clearCurrentPocketDimension(playerUuid);

        // Play departure effects and dimension exit sound
        VisualEffects.playTeleportDepartureEffects(player);
        VisualEffects.playDimensionExitEffect(player);

        TeleportCompat.teleport(player, targetWorld, targetPos, yaw, pitch);

        // Play arrival effects
        VisualEffects.playTeleportArrivalEffects(player);

        player.displayClientMessage(Component.literal("Returned to the overworld"), true);
        PersonalWorldsMod.LOGGER.info("Player {} left personal dimension", player.getName().getString());
    }

    // --- Direct Teleport (for /pw go command) ---

    /**
     * Teleport a player directly to another player's dimension.
     * Used by the /pw go command. Requires permission check before calling.
     *
     * @param player The player being teleported
     * @param server The Minecraft server
     * @param ownerUuid The UUID of the dimension owner
     * @return true if teleport was successful
     */
    public static boolean teleportToDimension(ServerPlayer player, MinecraftServer server, UUID ownerUuid) {
        // Check if player is already in the target dimension
        ServerLevel currentWorld = EntityCompat.getServerWorld(player);
        if (isInPersonalDimension(currentWorld)) {
            String dimPath = IdentifierCompat.fromKey(currentWorld.dimension()).getPath();
            String targetPath = "pw_" + ownerUuid.toString();
            if (dimPath.equals(targetPath)) {
                player.displayClientMessage(Component.literal("You are already in this dimension!").withStyle(ChatFormatting.RED), false);
                return false;
            }
        }

        // Get portal type from dimension registry (default to 0 if not found)
        DimensionRegistry registry = DimensionRegistry.get(server);
        int portalTypeIndex = registry.getDimensionData(ownerUuid)
            .map(PlayerDimensionData::portalTypeIndex)
            .orElse(0);

        // Use current world as "from" world for return data
        return teleportToOwnerDimension(player, server, currentWorld, ownerUuid, portalTypeIndex);
    }

    // --- Dimension Utilities ---

    /**
     * Check if a world is a personal dimension.
     *
     * @param world The world to check
     * @return true if this is a personal dimension
     */
    public static boolean isInPersonalDimension(ServerLevel world) {
        String namespace = IdentifierCompat.fromKey(world.dimension()).getNamespace();
        String path = IdentifierCompat.fromKey(world.dimension()).getPath();
        return PersonalWorldsMod.MOD_ID.equals(namespace) && path.startsWith("pw_");
    }

    /**
     * Get the owner UUID of a personal dimension from its world.
     *
     * @param world The personal dimension world
     * @return Optional containing the owner UUID, or empty if not a personal dimension
     */
    public static Optional<UUID> getDimensionOwner(ServerLevel world) {
        if (!isInPersonalDimension(world)) {
            return Optional.empty();
        }

        String path = IdentifierCompat.fromKey(world.dimension()).getPath();
        String uuidStr = path.substring(3); // Remove "pw_" prefix

        // Dimension IDs store UUIDs without dashes (e.g., "e8823481a39c3659a564a28f5ed6f193")
        // We need to insert dashes for UUID.fromString() which requires format: 8-4-4-4-12
        if (uuidStr.length() == 32 && !uuidStr.contains("-")) {
            uuidStr = uuidStr.substring(0, 8) + "-" +
                      uuidStr.substring(8, 12) + "-" +
                      uuidStr.substring(12, 16) + "-" +
                      uuidStr.substring(16, 20) + "-" +
                      uuidStr.substring(20);
        }

        try {
            return Optional.of(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            PersonalWorldsMod.LOGGER.warn("Invalid UUID in dimension path: {}", path);
            return Optional.empty();
        }
    }

    /**
     * Get the owner UUID from a dimension registry key (for stored keys, not loaded worlds).
     * Used for recovery when restoring players to unloaded dimensions.
     *
     * @param dimensionKey The dimension registry key
     * @return Optional containing the owner UUID, or empty if not a personal dimension
     */
    public static Optional<UUID> getDimensionOwner(ResourceKey<Level> dimensionKey) {
        // Check namespace
        if (!IdentifierCompat.fromKey(dimensionKey).getNamespace().equals(PersonalWorldsMod.MOD_ID)) {
            return Optional.empty();
        }

        String path = IdentifierCompat.fromKey(dimensionKey).getPath();
        if (!path.startsWith("pw_")) {
            return Optional.empty();
        }

        String uuidStr = path.substring(3); // Remove "pw_" prefix

        // Dimension IDs store UUIDs without dashes (e.g., "e8823481a39c3659a564a28f5ed6f193")
        // Insert dashes for UUID.fromString() format: 8-4-4-4-12
        if (uuidStr.length() == 32 && !uuidStr.contains("-")) {
            uuidStr = uuidStr.substring(0, 8) + "-" +
                      uuidStr.substring(8, 12) + "-" +
                      uuidStr.substring(12, 16) + "-" +
                      uuidStr.substring(16, 20) + "-" +
                      uuidStr.substring(20);
        }

        try {
            return Optional.of(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            PersonalWorldsMod.LOGGER.warn("Invalid UUID in dimension key path: {}", path);
            return Optional.empty();
        }
    }

    // --- Portal Search ---

    /**
     * Search radius for finding existing portals (in blocks).
     */
    private static final int PORTAL_SEARCH_RADIUS = 128;

    /**
     * Find an existing personal portal in the given world.
     * Searches within a radius around the world origin.
     *
     * @param world The world to search in
     * @return Optional containing the position of a portal block, or empty if none found
     */
    private static Optional<BlockPos> findExistingPortal(ServerLevel world) {
        BlockPos center = new BlockPos(0, PLATFORM_Y, 0);

        // Search for portal blocks in a cube around the center
        for (int y = -PORTAL_SEARCH_RADIUS; y <= PORTAL_SEARCH_RADIUS; y++) {
            for (int x = -PORTAL_SEARCH_RADIUS; x <= PORTAL_SEARCH_RADIUS; x++) {
                for (int z = -PORTAL_SEARCH_RADIUS; z <= PORTAL_SEARCH_RADIUS; z++) {
                    BlockPos checkPos = center.offset(x, y, z);

                    // Ensure Y is within valid range
                    if (checkPos.getY() < WorldCompat.getBottomY(world) || checkPos.getY() >= WorldCompat.getTopY(world)) {
                        continue;
                    }

                    if (world.getBlockState(checkPos).getBlock() == ModBlocks.PERSONAL_PORTAL.get()) {
                        PersonalWorldsMod.LOGGER.debug("Found existing portal at {}", checkPos);
                        return Optional.of(checkPos);
                    }
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("No existing portal found in {}", IdentifierCompat.fromKey(world.dimension()));
        return Optional.empty();
    }

    /**
     * Find a safe position to teleport to near a portal.
     * Looks for a solid block to stand on adjacent to the portal.
     *
     * @param world The world
     * @param portalPos Position of a portal block
     * @return A safe position to teleport to (one block above ground)
     */
    private static BlockPos findSafePositionNearPortal(ServerLevel world, BlockPos portalPos) {
        // Get the portal axis to determine which directions to check
        BlockState portalState = world.getBlockState(portalPos);
        Direction.Axis axis = portalState.getValue(PersonalPortalBlock.AXIS);

        // Check positions perpendicular to the portal
        Direction[] checkDirections;
        if (axis == Direction.Axis.X) {
            // Portal faces north/south, check east/west
            checkDirections = new Direction[] { Direction.NORTH, Direction.SOUTH };
        } else {
            // Portal faces east/west, check north/south
            checkDirections = new Direction[] { Direction.EAST, Direction.WEST };
        }

        // Find the bottom of the portal (search down)
        BlockPos bottomPortal = portalPos;
        while (world.getBlockState(bottomPortal.below()).getBlock() == ModBlocks.PERSONAL_PORTAL.get()) {
            bottomPortal = bottomPortal.below();
        }

        // Check each direction for a safe landing spot
        for (Direction dir : checkDirections) {
            BlockPos sidePos = bottomPortal.relative(dir);

            // Look for solid ground below
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                BlockPos groundCheck = sidePos.offset(0, yOffset - 1, 0);
                BlockPos feetPos = sidePos.offset(0, yOffset, 0);
                BlockPos headPos = sidePos.offset(0, yOffset + 1, 0);

                // Check: solid ground, empty feet space, empty head space
                if (!world.getBlockState(groundCheck).isAir() &&
                    world.getBlockState(feetPos).isAir() &&
                    world.getBlockState(headPos).isAir()) {
                    PersonalWorldsMod.LOGGER.debug("Found safe position near portal: {}", feetPos);
                    return feetPos;
                }
            }
        }

        // Fallback: return position at the portal level
        PersonalWorldsMod.LOGGER.debug("No safe position found near portal, using portal position");
        return bottomPortal;
    }

    // --- Spawn Platform ---

    /**
     * Get or create the spawn platform for a personal dimension.
     * For void worlds, creates a platform with configurable materials if none exists.
     *
     * @param world The personal dimension world
     * @param genType The generation type of the world
     * @param portalTypeIndex The portal type index (determines island materials)
     * @return The spawn position (one block above platform)
     */
    private static BlockPos getOrCreateSpawnPlatform(ServerLevel world, WorldGenType genType, int portalTypeIndex) {
        BlockPos spawnPos = new BlockPos(0, PLATFORM_Y + 1, 0);

        // For void worlds, check if platform exists
        if (genType == WorldGenType.VOID) {
            BlockPos groundCheck = spawnPos.below();
            if (world.getBlockState(groundCheck).isAir()) {
                // Create starter platform with portal type materials
                createStarterPlatform(world, new BlockPos(0, PLATFORM_Y, 0), portalTypeIndex);
            }
        }

        return spawnPos;
    }

    /**
     * Create a starter platform with configurable materials and a return portal frame.
     * Uses the first island layer material from the portal type configuration.
     *
     * @param world The world to create the platform in
     * @param center The center position of the platform (Y = platform level)
     * @param portalTypeIndex The portal type index (determines platform material)
     */
    private static void createStarterPlatform(ServerLevel world, BlockPos center, int portalTypeIndex) {
        PersonalWorldsMod.LOGGER.info("Creating starter platform at {} with portal type {}", center, portalTypeIndex);

        // Get platform material from portal config (first island layer)
        BlockState platformMaterial = Blocks.GRASS_BLOCK.defaultBlockState(); // Fallback

        ModConfig.PortalConfig config = ModConfig.get().portalTypes.get(portalTypeIndex);
        if (config.islandLayers.length > 0) {
            String blockId = config.islandLayers[0];
            ResourceLocation id = IdentifierCompat.tryParse(blockId);
            Block block = id != null ? RegistryCompat.get(BuiltInRegistries.BLOCK, id) : Blocks.GRASS_BLOCK;

            if (block != Blocks.AIR || blockId.equals("minecraft:air")) {
                platformMaterial = block.defaultBlockState();
            }
        }

        // Create 5x5 platform
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos pos = center.offset(x, 0, z);
                world.setBlockAndUpdate(pos, platformMaterial);
            }
        }

        // Create return portal frame (offset from center)
        createReturnPortalFrame(world, center.offset(4, 1, 0));
    }

    /**
     * Create a portal frame structure for returning.
     * Frame is built facing the spawn point (Z-axis orientation).
     * Return portal can be any type - uses first portal type by default.
     *
     * @param world The world to create the frame in
     * @param bottomLeft The bottom-left position of the frame
     */
    private static void createReturnPortalFrame(ServerLevel world, BlockPos bottomLeft) {
        // Return portal uses default portal type (index 0)
        Block frameBlock = ModBlocks.getFrameBlock(0);
        BlockState frameState = frameBlock.defaultBlockState();

        // Build 4-wide x 5-tall frame (same as standard portal)
        int frameWidth = PORTAL_WIDTH + 2;  // 4
        int frameHeight = PORTAL_HEIGHT + 2; // 5

        // Bottom row
        for (int x = 0; x < frameWidth; x++) {
            world.setBlockAndUpdate(bottomLeft.offset(x, 0, 0), frameState);
        }

        // Top row
        for (int x = 0; x < frameWidth; x++) {
            world.setBlockAndUpdate(bottomLeft.offset(x, frameHeight - 1, 0), frameState);
        }

        // Left column (excluding corners)
        for (int y = 1; y < frameHeight - 1; y++) {
            world.setBlockAndUpdate(bottomLeft.offset(0, y, 0), frameState);
        }

        // Right column (excluding corners)
        for (int y = 1; y < frameHeight - 1; y++) {
            world.setBlockAndUpdate(bottomLeft.offset(frameWidth - 1, y, 0), frameState);
        }

        PersonalWorldsMod.LOGGER.debug("Created return portal frame at {}", bottomLeft);
    }

    // --- Frame Detection ---

    /**
     * Detect which portal type the player is activating.
     * Returns the index in ModConfig.portalTypes array.
     *
     * First-match wins: checks portal types in array order.
     *
     * @param world The world
     * @param clickedPos Position clicked by player
     * @param activationItem The item used to activate
     * @return Optional containing portal type index, or empty if no valid frame
     */
    private static Optional<Integer> detectPortalType(
            Level world,
            BlockPos clickedPos,
            Item activationItem
    ) {
        List<ModConfig.PortalConfig> portalTypes = ModConfig.get().portalTypes;

        for (int i = 0; i < portalTypes.size(); i++) {
            // Check if activation item matches
            Item configItem = ModItems.getActivationItem(i);
            if (configItem != activationItem) {
                continue;
            }

            // Check if frame matches (try both axes)
            Block configFrame = ModBlocks.getFrameBlock(i);
            Optional<PortalFrame> frame = detectFrameForAxis(world, clickedPos, configFrame, Direction.Axis.X);
            if (frame.isEmpty()) {
                frame = detectFrameForAxis(world, clickedPos, configFrame, Direction.Axis.Z);
            }

            if (frame.isPresent()) {
                return Optional.of(i); // Found matching portal type
            }
        }

        return Optional.empty();
    }

    /**
     * Detect a valid portal frame around the given position for a specific portal type.
     * Tries both X and Z axis orientations.
     *
     * @param world The world to search in
     * @param clickedPos The position clicked by the player
     * @param portalTypeIndex The portal type index
     * @return Optional containing the detected frame, or empty if none found
     */
    public static Optional<PortalFrame> detectFrame(Level world, BlockPos clickedPos, int portalTypeIndex) {
        Block frameBlock = ModBlocks.getFrameBlock(portalTypeIndex);

        // Try X-axis orientation first
        Optional<PortalFrame> xFrame = detectFrameForAxis(world, clickedPos, frameBlock, Direction.Axis.X);
        if (xFrame.isPresent()) {
            return xFrame;
        }

        // Try Z-axis orientation
        return detectFrameForAxis(world, clickedPos, frameBlock, Direction.Axis.Z);
    }

    /**
     * Detect a portal frame for a specific axis orientation.
     *
     * @param world The world to search in
     * @param clickedPos The position clicked by the player
     * @param frameBlock The block type used for the frame
     * @param axis The axis to check (X or Z)
     * @return Optional containing the detected frame, or empty if none found
     */
    private static Optional<PortalFrame> detectFrameForAxis(
            Level world,
            BlockPos clickedPos,
            Block frameBlock,
            Direction.Axis axis
    ) {
        // Direction to search for bottom-left corner
        Direction horizontal = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;

        // Start at clicked position and search for the interior boundaries
        BlockPos searchPos = clickedPos;

        // Go left/north until we hit a frame block or search limit
        for (int i = 0; i < PORTAL_WIDTH + 1; i++) {
            BlockPos nextPos = searchPos.relative(horizontal);
            if (world.getBlockState(nextPos).getBlock() == frameBlock) {
                break;
            }
            searchPos = nextPos;
        }

        // Go down until we hit a frame block or search limit
        for (int i = 0; i < PORTAL_HEIGHT + 1; i++) {
            BlockPos downPos = searchPos.below();
            if (world.getBlockState(downPos).getBlock() == frameBlock) {
                break;
            }
            searchPos = downPos;
        }

        // Now searchPos should be the bottom-left interior block
        // The actual bottom-left frame block is one step left/north and one step down
        BlockPos bottomLeftFrame = searchPos.relative(horizontal).below();

        // Create frame and validate
        PortalFrame frame = new PortalFrame(bottomLeftFrame, PORTAL_WIDTH, PORTAL_HEIGHT, axis);

        if (isValidFrame(world, frame, frameBlock)) {
            return Optional.of(frame);
        }

        return Optional.empty();
    }

    /**
     * Validate that a frame structure is complete and interior is clear.
     *
     * @param world The world to check
     * @param frame The frame to validate
     * @param frameBlock The block type expected for the frame
     * @return true if the frame is valid
     */
    private static boolean isValidFrame(Level world, PortalFrame frame, Block frameBlock) {
        // Check all frame positions have the correct block
        for (BlockPos pos : frame.getFramePositions()) {
            if (world.getBlockState(pos).getBlock() != frameBlock) {
                return false;
            }
        }

        // Check interior is empty (air or already portal blocks)
        for (BlockPos pos : frame.getInteriorPositions()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getBlock() != ModBlocks.PERSONAL_PORTAL.get()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if the frame is still valid for an existing portal block.
     * Used by PersonalPortalBlock.neighborChanged() to determine if portal should break.
     *
     * Checks all portal types - if ANY portal type has a valid frame, the portal is valid.
     *
     * @param world The world to check
     * @param portalPos The position of the portal block
     * @param axis The axis of the portal
     * @return true if frame is still valid
     */
    public static boolean isFrameValidForPortal(Level world, BlockPos portalPos, Direction.Axis axis) {
        // Check all portal types - portal is valid if ANY type has a valid frame
        for (int i = 0; i < ModConfig.get().portalTypes.size(); i++) {
            Block frameBlock = ModBlocks.getFrameBlock(i);
            Optional<PortalFrame> frame = detectFrameForAxis(world, portalPos, frameBlock, axis);
            if (frame.isPresent()) {
                return true;  // Found a valid frame for this portal type
            }
        }

        return false;  // No valid frame found for any portal type
    }
}
