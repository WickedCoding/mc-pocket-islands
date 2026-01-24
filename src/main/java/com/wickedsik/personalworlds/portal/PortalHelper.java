package com.wickedsik.personalworlds.portal;

import com.wickedsik.personalworlds.PersonalWorldsMod;
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
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

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
    public static boolean tryActivatePortal(World world, BlockPos clickedPos, ServerPlayerEntity player, Item activationItem) {
        if (world.isClient()) {
            return false;
        }

        MinecraftServer server = player.getServer();
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
        BlockState portalState = ModBlocks.PERSONAL_PORTAL.getDefaultState()
            .with(PersonalPortalBlock.AXIS, portalFrame.axis())
            .with(PersonalPortalBlock.COLOR, color);

        for (BlockPos pos : portalFrame.getInteriorPositions()) {
            world.setBlockState(pos, portalState);
        }

        // Register portal ownership AND portal type for all portal blocks
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
        for (BlockPos pos : portalFrame.getInteriorPositions()) {
            ownershipManager.registerPortal(world, pos, player.getUuid(), portalTypeIndex);
        }

        // Play activation sound
        world.playSound(
            null,
            portalFrame.getCenter(),
            SoundEvents.BLOCK_END_PORTAL_SPAWN,
            SoundCategory.BLOCKS,
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
    public static void handlePortalEntry(ServerPlayerEntity player, BlockPos portalPos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // Acquire teleport lock to prevent race conditions
        if (!ConcurrentPortalGuard.tryAcquire(player, portalPos)) {
            // Already processing or on cooldown
            return;
        }

        try {
            ServerWorld currentWorld = player.getServerWorld();

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
            ServerPlayerEntity player,
            MinecraftServer server,
            ServerWorld fromWorld,
            BlockPos portalPos
    ) {
        PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
        Optional<UUID> portalOwnerOpt = ownershipManager.getOwner(fromWorld, portalPos);

        if (portalOwnerOpt.isEmpty()) {
            // Unclaimed portal - auto-claim for the entering player
            // Default to portal type 0 for auto-claimed portals
            PersonalWorldsMod.LOGGER.warn("Unclaimed portal at {} - auto-claiming for {} with default portal type",
                portalPos, player.getName().getString());
            ownershipManager.registerPortal(fromWorld, portalPos, player.getUuid(), 0);
            teleportToOwnerDimension(player, server, fromWorld, player.getUuid(), 0);
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
            Text denialMessage = switch (denialReason) {
                case NOT_INVITED -> Text.translatable("personalworlds.command.error.not_invited", ownerName);
                case HOST_OFFLINE -> Text.translatable("personalworlds.visit.denied.offline", ownerName);
                case HOST_NOT_HOME -> Text.translatable("personalworlds.visit.denied.not_home", ownerName);
                case ALLOWED -> Text.empty(); // Should never happen
            };

            player.sendMessage(denialMessage.copy().formatted(Formatting.RED), false);
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
            ServerPlayerEntity player,
            MinecraftServer server,
            ServerWorld fromWorld,
            UUID ownerUuid,
            int portalTypeIndex
    ) {
        UUID playerUuid = player.getUuid();
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
            player.sendMessage(
                Text.literal("This portal's dimension no longer exists. ")
                    .append(Text.literal(deletedOwnerName).formatted(Formatting.YELLOW))
                    .append("'s world was deleted.")
                    .formatted(Formatting.RED),
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
            BlockPos returnPos = player.getBlockPos().offset(player.getHorizontalFacing().getOpposite());
            ReturnData returnData = new ReturnData(
                fromWorld.getRegistryKey(),
                returnPos,
                player.getYaw(),
                player.getPitch()
            );
            dataManager.setReturnData(playerUuid, returnData);
        }
        // If coming from a personal dimension, preserve existing return data (overworld position)

        // Get or create the owner's dimension
        ServerWorld targetWorld = DimensionManager.getOrCreatePlayerDimension(
            server, ownerUuid, ownerName, genType, portalTypeIndex
        );

        // Find destination and teleport
        BlockPos destinationPos = findExistingPortal(targetWorld)
            .map(portalPos -> findSafePositionNearPortal(targetWorld, portalPos))
            .orElseGet(() -> getOrCreateSpawnPlatform(targetWorld, genType, portalTypeIndex));

        // Play departure effects
        VisualEffects.playTeleportDepartureEffects(player);

        TeleportTarget target = new TeleportTarget(
            Vec3d.ofCenter(destinationPos),
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        FabricDimensions.teleport(player, targetWorld, target);

        // Play arrival effects and dimension entry sound
        VisualEffects.playTeleportArrivalEffects(player);
        VisualEffects.playDimensionEntryEffect(player);

        // Send appropriate message
        if (isOwnDimension) {
            player.sendMessage(Text.literal("Welcome to your pocket island!"), true);
            PersonalWorldsMod.LOGGER.info("Player {} entered their personal dimension",
                player.getName().getString());
        } else {
            player.sendMessage(Text.literal("Entering ")
                .append(Text.literal(ownerName).formatted(Formatting.YELLOW))
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
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            return player.getName().getString();
        }
        return playerUuid.toString().substring(0, 8);
    }

    /**
     * Teleport player back to their stored return position.
     * Public to allow usage by commands (/pw leave) and portal exits.
     */
    public static void teleportToReturnPosition(ServerPlayerEntity player, MinecraftServer server) {
        UUID playerUuid = player.getUuid();
        PlayerDataManager dataManager = PlayerDataManager.get(server);

        Optional<ReturnData> returnDataOpt = dataManager.getReturnData(playerUuid);

        ServerWorld targetWorld;
        Vec3d targetPos;
        float yaw, pitch;

        if (returnDataOpt.isPresent()) {
            ReturnData returnData = returnDataOpt.get();
            targetWorld = server.getWorld(returnData.dimension());

            if (targetWorld == null) {
                // Dimension deleted - use overworld
                PersonalWorldsMod.LOGGER.warn("Return dimension not found for player {}, using overworld",
                    player.getName().getString());
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(
                    targetWorld, targetWorld.getSpawnPos()));
                yaw = player.getYaw();
                pitch = player.getPitch();
            } else if (!SafeSpawnFinder.isSafeSpawn(targetWorld, returnData.position())) {
                // Position no longer safe - find nearby safe spot
                BlockPos safePos = SafeSpawnFinder.findSafePosition(targetWorld, returnData.position());
                targetPos = Vec3d.ofCenter(safePos);
                yaw = returnData.yaw();
                pitch = returnData.pitch();
                PersonalWorldsMod.LOGGER.info("Return position unsafe, relocated player {} to {}",
                    player.getName().getString(), safePos);
            } else {
                targetPos = Vec3d.ofCenter(returnData.position());
                yaw = returnData.yaw();
                pitch = returnData.pitch();
            }

            // Clear return data after use
            dataManager.clearReturnData(playerUuid);
        } else {
            // No return data - try bed spawn first
            BlockPos bedPos = player.getSpawnPointPosition();
            ServerWorld bedWorld = null;

            if (bedPos != null) {
                bedWorld = server.getWorld(player.getSpawnPointDimension());
            }

            if (bedWorld != null) {
                // Use bed spawn
                BlockPos safePos = SafeSpawnFinder.findSafePosition(bedWorld, bedPos);
                targetWorld = bedWorld;
                targetPos = Vec3d.ofCenter(safePos);
                yaw = player.getYaw();
                pitch = player.getPitch();
                PersonalWorldsMod.LOGGER.debug("No return data for player {}, using bed spawn at {}",
                    player.getName().getString(), safePos);
            } else {
                // Fallback: overworld world spawn
                PersonalWorldsMod.LOGGER.debug("No return data for player {}, using overworld spawn",
                    player.getName().getString());
                targetWorld = server.getOverworld();
                targetPos = Vec3d.ofCenter(SafeSpawnFinder.findSafePosition(
                    targetWorld, targetWorld.getSpawnPos()));
                yaw = player.getYaw();
                pitch = player.getPitch();
            }
        }

        // Play departure effects and dimension exit sound
        VisualEffects.playTeleportDepartureEffects(player);
        VisualEffects.playDimensionExitEffect(player);

        TeleportTarget target = new TeleportTarget(targetPos, Vec3d.ZERO, yaw, pitch);
        FabricDimensions.teleport(player, targetWorld, target);

        // Play arrival effects
        VisualEffects.playTeleportArrivalEffects(player);

        player.sendMessage(Text.literal("Returned to the overworld"), true);
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
    public static boolean teleportToDimension(ServerPlayerEntity player, MinecraftServer server, UUID ownerUuid) {
        // Check if player is already in the target dimension
        ServerWorld currentWorld = player.getServerWorld();
        if (isInPersonalDimension(currentWorld)) {
            String dimPath = currentWorld.getRegistryKey().getValue().getPath();
            String targetPath = "pw_" + ownerUuid.toString();
            if (dimPath.equals(targetPath)) {
                player.sendMessage(Text.literal("You are already in this dimension!").formatted(Formatting.RED), false);
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
    public static boolean isInPersonalDimension(ServerWorld world) {
        String namespace = world.getRegistryKey().getValue().getNamespace();
        String path = world.getRegistryKey().getValue().getPath();
        return PersonalWorldsMod.MOD_ID.equals(namespace) && path.startsWith("pw_");
    }

    /**
     * Get the owner UUID of a personal dimension from its world.
     *
     * @param world The personal dimension world
     * @return Optional containing the owner UUID, or empty if not a personal dimension
     */
    public static Optional<UUID> getDimensionOwner(ServerWorld world) {
        if (!isInPersonalDimension(world)) {
            return Optional.empty();
        }

        String path = world.getRegistryKey().getValue().getPath();
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
    private static Optional<BlockPos> findExistingPortal(ServerWorld world) {
        BlockPos center = new BlockPos(0, PLATFORM_Y, 0);

        // Search for portal blocks in a cube around the center
        for (int y = -PORTAL_SEARCH_RADIUS; y <= PORTAL_SEARCH_RADIUS; y++) {
            for (int x = -PORTAL_SEARCH_RADIUS; x <= PORTAL_SEARCH_RADIUS; x++) {
                for (int z = -PORTAL_SEARCH_RADIUS; z <= PORTAL_SEARCH_RADIUS; z++) {
                    BlockPos checkPos = center.add(x, y, z);

                    // Ensure Y is within valid range
                    if (checkPos.getY() < world.getBottomY() || checkPos.getY() >= world.getTopY()) {
                        continue;
                    }

                    if (world.getBlockState(checkPos).getBlock() == ModBlocks.PERSONAL_PORTAL) {
                        PersonalWorldsMod.LOGGER.debug("Found existing portal at {}", checkPos);
                        return Optional.of(checkPos);
                    }
                }
            }
        }

        PersonalWorldsMod.LOGGER.debug("No existing portal found in {}", world.getRegistryKey().getValue());
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
    private static BlockPos findSafePositionNearPortal(ServerWorld world, BlockPos portalPos) {
        // Get the portal axis to determine which directions to check
        BlockState portalState = world.getBlockState(portalPos);
        Direction.Axis axis = portalState.get(PersonalPortalBlock.AXIS);

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
        while (world.getBlockState(bottomPortal.down()).getBlock() == ModBlocks.PERSONAL_PORTAL) {
            bottomPortal = bottomPortal.down();
        }

        // Check each direction for a safe landing spot
        for (Direction dir : checkDirections) {
            BlockPos sidePos = bottomPortal.offset(dir);

            // Look for solid ground below
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                BlockPos groundCheck = sidePos.add(0, yOffset - 1, 0);
                BlockPos feetPos = sidePos.add(0, yOffset, 0);
                BlockPos headPos = sidePos.add(0, yOffset + 1, 0);

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
    private static BlockPos getOrCreateSpawnPlatform(ServerWorld world, WorldGenType genType, int portalTypeIndex) {
        BlockPos spawnPos = new BlockPos(0, PLATFORM_Y + 1, 0);

        // For void worlds, check if platform exists
        if (genType == WorldGenType.VOID) {
            BlockPos groundCheck = spawnPos.down();
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
    private static void createStarterPlatform(ServerWorld world, BlockPos center, int portalTypeIndex) {
        PersonalWorldsMod.LOGGER.info("Creating starter platform at {} with portal type {}", center, portalTypeIndex);

        // Get platform material from portal config (first island layer)
        BlockState platformMaterial = Blocks.GRASS_BLOCK.getDefaultState(); // Fallback

        ModConfig.PortalConfig config = ModConfig.get().portalTypes.get(portalTypeIndex);
        if (config.islandLayers.length > 0) {
            String blockId = config.islandLayers[0];
            Identifier id = Identifier.tryParse(blockId);
            Block block = id != null ? Registries.BLOCK.get(id) : Blocks.GRASS_BLOCK;

            if (block != Blocks.AIR || blockId.equals("minecraft:air")) {
                platformMaterial = block.getDefaultState();
            }
        }

        // Create 5x5 platform
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                BlockPos pos = center.add(x, 0, z);
                world.setBlockState(pos, platformMaterial);
            }
        }

        // Create return portal frame (offset from center)
        createReturnPortalFrame(world, center.add(4, 1, 0));
    }

    /**
     * Create a portal frame structure for returning.
     * Frame is built facing the spawn point (Z-axis orientation).
     * Return portal can be any type - uses first portal type by default.
     *
     * @param world The world to create the frame in
     * @param bottomLeft The bottom-left position of the frame
     */
    private static void createReturnPortalFrame(ServerWorld world, BlockPos bottomLeft) {
        // Return portal uses default portal type (index 0)
        Block frameBlock = ModBlocks.getFrameBlock(0);
        BlockState frameState = frameBlock.getDefaultState();

        // Build 4-wide x 5-tall frame (same as standard portal)
        int frameWidth = PORTAL_WIDTH + 2;  // 4
        int frameHeight = PORTAL_HEIGHT + 2; // 5

        // Bottom row
        for (int x = 0; x < frameWidth; x++) {
            world.setBlockState(bottomLeft.add(x, 0, 0), frameState);
        }

        // Top row
        for (int x = 0; x < frameWidth; x++) {
            world.setBlockState(bottomLeft.add(x, frameHeight - 1, 0), frameState);
        }

        // Left column (excluding corners)
        for (int y = 1; y < frameHeight - 1; y++) {
            world.setBlockState(bottomLeft.add(0, y, 0), frameState);
        }

        // Right column (excluding corners)
        for (int y = 1; y < frameHeight - 1; y++) {
            world.setBlockState(bottomLeft.add(frameWidth - 1, y, 0), frameState);
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
            World world,
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
    public static Optional<PortalFrame> detectFrame(World world, BlockPos clickedPos, int portalTypeIndex) {
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
            World world,
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
            BlockPos nextPos = searchPos.offset(horizontal);
            if (world.getBlockState(nextPos).getBlock() == frameBlock) {
                break;
            }
            searchPos = nextPos;
        }

        // Go down until we hit a frame block or search limit
        for (int i = 0; i < PORTAL_HEIGHT + 1; i++) {
            BlockPos downPos = searchPos.down();
            if (world.getBlockState(downPos).getBlock() == frameBlock) {
                break;
            }
            searchPos = downPos;
        }

        // Now searchPos should be the bottom-left interior block
        // The actual bottom-left frame block is one step left/north and one step down
        BlockPos bottomLeftFrame = searchPos.offset(horizontal).down();

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
    private static boolean isValidFrame(World world, PortalFrame frame, Block frameBlock) {
        // Check all frame positions have the correct block
        for (BlockPos pos : frame.getFramePositions()) {
            if (world.getBlockState(pos).getBlock() != frameBlock) {
                return false;
            }
        }

        // Check interior is empty (air or already portal blocks)
        for (BlockPos pos : frame.getInteriorPositions()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getBlock() != ModBlocks.PERSONAL_PORTAL) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if the frame is still valid for an existing portal block.
     * Used by PersonalPortalBlock.neighborUpdate() to determine if portal should break.
     *
     * Checks all portal types - if ANY portal type has a valid frame, the portal is valid.
     *
     * @param world The world to check
     * @param portalPos The position of the portal block
     * @param axis The axis of the portal
     * @return true if frame is still valid
     */
    public static boolean isFrameValidForPortal(World world, BlockPos portalPos, Direction.Axis axis) {
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
