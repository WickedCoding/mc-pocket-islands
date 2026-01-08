# Per-Player Dimensions Mod — Development Plan

## Overview

A Fabric mod for Minecraft 1.20.1+ that provides each player with their own isolated, persistent dimension. Players access their dimension through a custom portal structure, and can invite other players to visit.

**Primary use case:** Player dimensions survive world resets. When the overworld/nether/end are deleted and regenerated (due to mod updates, corruption, fresh start), each player's personal world remains intact.

---

## Requirements Summary

| Requirement       | Specification                                            |
|-------------------|----------------------------------------------------------|
| Minecraft version | 1.20.1+                                                  |
| Mod loader        | Fabric                                                   |
| Access method     | Portal (no commands)                                     |
| World type        | Configurable (void/overworld-style)                      |
| Privacy           | Private by default, owner can invite others              |
| Scale             | ~15 concurrent players maximum                           |
| Persistence       | Dimensions survive server restarts and main world resets |

---

## Architecture

### Dimension Storage Structure

```
world/
├── region/                         ← Main world (deletable)
├── DIM-1/                          ← Nether (deletable)
├── DIM1/                           ← End (deletable)
├── data/
│   └── personalworlds/
│       └── registry.dat            ← Tracks all player dimensions
└── dimensions/
    └── personalworlds/
        ├── pw_<uuid1>/             ← Player 1's dimension (persistent)
        │   └── region/
        ├── pw_<uuid2>/             ← Player 2's dimension (persistent)
        └── pw_<uuid3>/             ← Player 3's dimension (persistent)
```

### Component Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        PersonalWorlds Mod                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Portal Block   │  │ Dimension       │  │ Player Data     │ │
│  │  & Structure    │  │ Manager         │  │ Manager         │ │
│  │                 │  │                 │  │                 │ │
│  │ - Detection     │  │ - Create        │  │ - Invitations   │ │
│  │ - Activation    │  │ - Load/Unload   │  │ - Return pos    │ │
│  │ - Rendering     │  │ - Delete        │  │ - Preferences   │ │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘ │
│           │                    │                    │          │
│           └────────────────────┼────────────────────┘          │
│                                │                               │
│                    ┌───────────▼───────────┐                   │
│                    │   Dimension Registry  │                   │
│                    │   (PersistentState)   │                   │
│                    └───────────────────────┘                   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  External Dependencies                                          │
│  • Fantasy (xyz.nucleoid:fantasy) — Runtime dimension creation  │
│  • Fabric API — Events, networking, registry                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Dependencies

### build.gradle

```groovy
repositories {
    maven { url = 'https://maven.nucleoid.xyz/' }
}

dependencies {
    minecraft "com.mojang:minecraft:1.20.1"
    mappings "net.fabricmc:yarn:1.20.1+build.10:v2"
    modImplementation "net.fabricmc:fabric-loader:0.14.22"

    // Fabric API modules needed
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.87.0+1.20.1"

    // Fantasy — runtime dimension creation
    modImplementation include("xyz.nucleoid:fantasy:0.4.10+1.20")
}
```

### Why Fantasy?

Fabric API's `fabric-dimensions-v1` only handles teleportation between existing dimensions. It cannot create dimensions at runtime. Fantasy provides:

- `RuntimeWorldConfig` — Configure dimension type, generator, seed, game rules
- `getOrOpenPersistentWorld()` — Create/load dimensions that survive restarts
- `RuntimeWorldHandle` — Manage lifecycle (unload, delete)
- Automatic dimension folder management

---

## Package Structure

```
src/main/java/com/yourname/personalworlds/
├── PersonalWorldsMod.java              # Mod entrypoint
├── config/
│   └── ModConfig.java                  # Configuration options
├── dimension/
│   ├── DimensionManager.java           # Create/load/unload dimensions
│   ├── DimensionRegistry.java          # PersistentState tracking all dimensions
│   ├── PlayerDimensionData.java        # Per-player dimension metadata
│   └── VoidChunkGenerator.java         # Optional void world generator
├── portal/
│   ├── PersonalPortalBlock.java        # The portal block itself
│   ├── PersonalPortalFrameBlock.java   # Frame block (like obsidian for nether)
│   └── PortalHelper.java               # Structure detection, teleportation
├── player/
│   ├── PlayerDataManager.java          # Invitations, return positions
│   └── InvitationData.java             # Who can visit whose dimension
├── command/
│   └── AdminCommands.java              # Optional admin commands (delete, list)
├── event/
│   └── ModEventHandlers.java           # Server start/stop, player join/leave
└── registry/
    └── ModRegistry.java                # Block/item registration
```

---

## Detailed Component Specifications

### 1. Dimension Registry (PersistentState)

Tracks all player dimensions so they can be restored after server restart.

```java
public class DimensionRegistry extends PersistentState {
    // Map: Player UUID -> Dimension metadata
    private final Map<UUID, PlayerDimensionData> dimensions = new HashMap<>();

    public static class PlayerDimensionData {
        UUID ownerUuid;
        String ownerName;              // For display purposes
        Identifier dimensionId;        // personalworlds:pw_<uuid>
        long createdAt;
        BlockPos spawnPoint;
        WorldGenType generatorType;    // VOID, OVERWORLD, FLAT
    }

    // Called on server start to restore all dimensions
    public void restoreAllDimensions(MinecraftServer server) {
        for (PlayerDimensionData data : dimensions.values()) {
            DimensionManager.loadOrCreate(server, data);
        }
    }
}
```

**Serialization:** NBT format, saved to `world/data/personalworlds/registry.dat`

### 2. Dimension Manager

Handles all Fantasy interactions.

```java
public class DimensionManager {
    private static final Map<UUID, RuntimeWorldHandle> activeHandles = new HashMap<>();

    public static ServerWorld getOrCreatePlayerDimension(
            MinecraftServer server,
            UUID playerUuid,
            WorldGenType genType
    ) {
        Fantasy fantasy = Fantasy.get(server);
        Identifier dimId = Identifier.of("personalworlds", "pw_" + playerUuid.toString());

        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(DimensionTypes.OVERWORLD)
                .setGenerator(createGenerator(server, genType))
                .setSeed(playerUuid.hashCode())
                .setDifficulty(server.getSaveProperties().getDifficulty())
                .setShouldTickTime(true);

        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(dimId, config);
        activeHandles.put(playerUuid, handle);

        // Register in persistent state if new
        DimensionRegistry registry = DimensionRegistry.get(server);
        if (!registry.hasDimension(playerUuid)) {
            registry.registerDimension(playerUuid, dimId, genType);
        }

        return handle.asWorld();
    }

    public static void unloadIfEmpty(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        if (handle != null && handle.asWorld().getPlayers().isEmpty()) {
            handle.unload();
            activeHandles.remove(playerUuid);
        }
    }

    private static ChunkGenerator createGenerator(MinecraftServer server, WorldGenType type) {
        return switch (type) {
            case VOID -> new VoidChunkGenerator(/* biome source */);
            case OVERWORLD -> server.getOverworld().getChunkManager().getChunkGenerator();
            case FLAT -> createFlatGenerator(server);
        };
    }
}
```

**Lifecycle events:**

- Server start → Restore all registered dimensions via `restoreAllDimensions()`
- Player enters portal → `getOrCreatePlayerDimension()`
- Player leaves dimension → Check if empty, call `unloadIfEmpty()`
- Server stop → All handles auto-saved by Fantasy

### 3. Portal System

#### 3.1 Portal Frame Block

A special block used to construct the portal frame (like crying obsidian or a custom block).

```java
public class PersonalPortalFrameBlock extends Block {
    // Standard block, nothing special
    // Could add particle effects, custom sounds
}
```

#### 3.2 Portal Block

The actual portal surface (like the purple swirly nether portal block).

```java
public class PersonalPortalBlock extends Block implements Portal {
    // Handles collision detection for teleportation
    // Renders with custom texture/particles

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity player)) return;
        if (entity.hasPortalCooldown()) return;

        PortalHelper.handlePortalEntry(player, pos);
        entity.setPortalCooldown();
    }
}
```

#### 3.3 Portal Structure & Activation

**Portal frame pattern** (configurable, example uses 4x5 like nether portal):

```
F F F F
F . . F
F . . F
F . . F
F F F F

F = Frame block
. = Air (becomes portal block on activation)
```

**Activation item:** Ender Pearl, custom item, or right-click with empty hand

```java
public class PortalHelper {

    public static boolean tryActivatePortal(World world, BlockPos clickedPos, PlayerEntity player) {
        // 1. Detect valid frame structure around clickedPos
        Optional<PortalFrame> frame = detectFrame(world, clickedPos);
        if (frame.isEmpty()) return false;

        // 2. Fill interior with portal blocks
        for (BlockPos interior : frame.get().getInteriorPositions()) {
            world.setBlockState(interior, ModRegistry.PERSONAL_PORTAL.getDefaultState());
        }

        // 3. Play activation sound/particles
        world.playSound(null, clickedPos, SoundEvents.BLOCK_END_PORTAL_SPAWN,
                SoundCategory.BLOCKS, 1.0f, 1.0f);

        return true;
    }

    public static void handlePortalEntry(ServerPlayerEntity player, BlockPos portalPos) {
        MinecraftServer server = player.getServer();
        UUID playerUuid = player.getUuid();

        // Determine destination
        ServerWorld currentWorld = player.getServerWorld();
        ServerWorld targetWorld;
        BlockPos targetPos;

        if (isInPersonalDimension(currentWorld)) {
            // Going back to overworld
            PlayerDataManager data = PlayerDataManager.get(server);
            ReturnData returnData = data.getReturnData(playerUuid);

            targetWorld = server.getWorld(returnData.dimension());
            targetPos = returnData.position();
        } else {
            // Going to personal dimension
            // First, store return position
            PlayerDataManager data = PlayerDataManager.get(server);
            data.setReturnData(playerUuid, currentWorld.getRegistryKey(), player.getBlockPos());

            // Get or create the player's dimension
            targetWorld = DimensionManager.getOrCreatePlayerDimension(server, playerUuid, WorldGenType.VOID);
            targetPos = getOrCreateSpawnPlatform(targetWorld);
        }

        // Teleport
        TeleportTarget target = new TeleportTarget(
                Vec3d.ofCenter(targetPos),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch()
        );
        FabricDimensions.teleport(player, targetWorld, target);
    }

    private static BlockPos getOrCreateSpawnPlatform(ServerWorld world) {
        BlockPos spawn = new BlockPos(0, 64, 0);

        // Check if platform exists
        if (world.getBlockState(spawn.down()).isAir()) {
            // Create a small starter platform
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.setBlockState(spawn.down().add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
                }
            }
            // Add a return portal frame
            createReturnPortal(world, spawn.add(3, 0, 0));

            // Optional: starter chest, tree, etc.
        }

        return spawn;
    }
}
```

### 4. Player Data Manager

Handles invitations and return positions.

```java
public class PlayerDataManager extends PersistentState {

    // Return position when player entered their dimension
    private final Map<UUID, ReturnData> returnPositions = new HashMap<>();

    // Invitation lists: Owner UUID -> Set of invited player UUIDs
    private final Map<UUID, Set<UUID>> invitations = new HashMap<>();

    public record ReturnData(RegistryKey<World> dimension, BlockPos position) {
    }

    // --- Invitations ---

    public void invite(UUID owner, UUID guest) {
        invitations.computeIfAbsent(owner, k -> new HashSet<>()).add(guest);
        markDirty();
    }

    public void uninvite(UUID owner, UUID guest) {
        Set<UUID> guests = invitations.get(owner);
        if (guests != null) {
            guests.remove(guest);
            markDirty();
        }
    }

    public boolean canVisit(UUID visitor, UUID owner) {
        if (visitor.equals(owner)) return true;
        Set<UUID> guests = invitations.get(owner);
        return guests != null && guests.contains(visitor);
    }

    public Set<UUID> getInvitedPlayers(UUID owner) {
        return invitations.getOrDefault(owner, Set.of());
    }
}
```

### 5. Invitation UI

Since you want portal-based access (no commands), invitations need an in-game mechanism.

**Option A: Item-based invitation**

```java
public class InvitationItem extends Item {
    // Right-click on another player to invite them
    // Or: Right-click to open a GUI listing online players

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof ServerPlayerEntity target && user instanceof ServerPlayerEntity owner) {
            PlayerDataManager.get(owner.getServer()).invite(owner.getUuid(), target.getUuid());
            owner.sendMessage(Text.literal("Invited " + target.getName().getString()));
            target.sendMessage(Text.literal(owner.getName().getString() + " invited you to their world"));
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
```

**Option B: Portal interaction menu**

When a player enters a portal, show a screen:

- "Enter your world"
- "Visit [PlayerName]'s world" (for each dimension they're invited to)

```java
// Server sends available destinations
// Client shows selection screen
// Client sends choice back
// Server teleports player
```

**Option C: Separate "Visit Portal"**

A different portal type specifically for visiting. Requires selecting destination before entering.

**Recommendation:** Option A (item-based) is simplest and fits "no commands" requirement while remaining intuitive.

### 6. Void Chunk Generator

For true void worlds (skyblock-style):

```java
public class VoidChunkGenerator extends ChunkGenerator {
    public static final Codec<VoidChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, VoidChunkGenerator::new)
    );

    public VoidChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor, Blender blender, NoiseConfig noiseConfig,
            StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.completedFuture(chunk); // Empty chunks
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return new VerticalBlockSample(0, new BlockState[0]);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig,
                      BiomeAccess biomeAccess, StructureAccessor structureAccessor,
                      Chunk chunk, GenerationStep.Carver carverStep) {
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures,
                             NoiseConfig noiseConfig, Chunk chunk) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }
}
```

Register in mod initializer:

```java
Registry.register(Registries.CHUNK_GENERATOR,
                  Identifier.of("personalworlds", "void"),

VoidChunkGenerator.CODEC);
```

### 7. Configuration

```java
public class ModConfig {
    // World generation
    public WorldGenType defaultWorldType = WorldGenType.VOID;
    public boolean allowPlayerToChooseWorldType = true;

    // Portal
    public String frameBlock = "minecraft:crying_obsidian";
    public String activationItem = "minecraft:ender_pearl";
    public boolean consumeActivationItem = false;

    // Starter platform (for void worlds)
    public boolean createStarterPlatform = true;
    public boolean includeStarterChest = true;
    public boolean includeReturnPortal = true;

    // Invitations
    public int maxInvitationsPerPlayer = 10;
    public boolean invitationsPersistAcrossRelog = true;

    // Performance
    public int unloadEmptyDimensionDelayTicks = 600; // 30 seconds

    public enum WorldGenType {
        VOID,       // Empty void, starter platform only
        OVERWORLD,  // Full overworld generation
        FLAT,       // Superflat
        SKYBLOCK    // Void with classic skyblock island
    }
}
```

---

## Event Handling

```java
public class ModEventHandlers implements ModInitializer {

    @Override
    public void onInitialize() {
        // Server lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Player events
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnect);

        // Tick events for dimension unloading
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerStarted(MinecraftServer server) {
        // Restore all player dimensions from registry
        DimensionRegistry.get(server).restoreAllDimensions(server);
    }

    private void onServerStopping(MinecraftServer server) {
        // Fantasy handles saving automatically, but we can do cleanup here
        DimensionManager.unloadAll();
    }

    private void onPlayerDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        ServerPlayerEntity player = handler.getPlayer();

        // If player was in a personal dimension, check if it should unload
        if (isInPersonalDimension(player.getServerWorld())) {
            UUID ownerUuid = extractOwnerUuid(player.getServerWorld());
            // Schedule unload check (don't unload immediately in case of reconnect)
            scheduleUnloadCheck(ownerUuid, server);
        }
    }

    private int tickCounter = 0;

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= 600) { // Every 30 seconds
            tickCounter = 0;
            DimensionManager.unloadEmptyDimensions();
        }
    }
}
```

---

## Admin Commands (Optional)

Even though players use portals, admins might need commands:

```java
public class AdminCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("personalworlds")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.literal("list")
                                .executes(AdminCommands::listDimensions))
                        .then(CommandManager.literal("delete")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(AdminCommands::deleteDimension)))
                        .then(CommandManager.literal("tp")
                                .then(CommandManager.argument("owner", EntityArgumentType.player())
                                        .executes(AdminCommands::teleportToDimension)))
        );
    }
}
```

---

## Testing Checklist

### Dimension Lifecycle

- [ ] First portal entry creates dimension correctly
- [ ] Dimension persists after server restart
- [ ] Dimension persists after deleting overworld folder
- [ ] Empty dimensions unload after delay
- [ ] Unloaded dimensions reload when player enters

### Portal Mechanics

- [ ] Frame detection works for all valid orientations
- [ ] Invalid frames don't activate
- [ ] Portal teleports to personal dimension
- [ ] Return portal teleports back to original position
- [ ] Portal cooldown prevents rapid flickering

### Invitations

- [ ] Owner can invite players
- [ ] Invited players can enter owner's dimension
- [ ] Non-invited players cannot enter
- [ ] Owner can revoke invitations
- [ ] Revoked players are ejected (or blocked on next entry)

### Edge Cases

- [ ] Player disconnects while in personal dimension
- [ ] Server crashes while player in personal dimension
- [ ] Two players enter portal simultaneously
- [ ] Player's return position is in deleted chunk
- [ ] Player invited to dimension that doesn't exist yet

---

## Implementation Order

### Phase 1: Core Infrastructure

1. Set up project with Fabric and Fantasy dependencies
2. Implement `DimensionRegistry` (PersistentState)
3. Implement `DimensionManager` with basic create/load
4. Test: Manually create dimension via temporary command

### Phase 2: Portal System

5. Create and register portal frame block
6. Create and register portal block
7. Implement frame detection algorithm
8. Implement portal activation
9. Implement basic teleportation (personal dim ↔ overworld)
10. Test: Full portal flow works

### Phase 3: Player Data

11. Implement `PlayerDataManager` (return positions)
12. Implement return portal functionality
13. Test: Round-trip teleportation preserves position

### Phase 4: Invitations

14. Add invitation storage to `PlayerDataManager`
15. Create invitation item or UI
16. Add permission check to portal entry
17. Test: Invitation flow works

### Phase 5: Polish

18. Add configuration file
19. Add starter platform generation
20. Add void chunk generator option
21. Add admin commands
22. Add particles/sounds/visual feedback
23. Test: Full feature set

### Phase 6: Hardening

24. Handle all edge cases from checklist
25. Performance testing with 15 dimensions
26. Documentation (README, wiki)
27. Release

---

## Resources

- **Fantasy documentation:** https://github.com/NucleoidMC/fantasy
- **Fabric Wiki — Dimensions:** https://fabricmc.net/wiki/tutorial:dimension
- **Fabric Wiki — Custom Portals:** https://fabricmc.net/wiki/tutorial:portal
- **Fabric API Javadoc:** https://maven.fabricmc.net/docs/fabric-api-latest/

---

## Notes & Considerations

### World Reset Procedure

When resetting the main world, the admin should:

1. Stop the server
2. Delete: `world/region/`, `world/DIM-1/`, `world/DIM1/`, `world/poi/`, `world/entities/`
3. Keep: `world/dimensions/personalworlds/`, `world/data/`
4. Optionally delete `world/playerdata/` if players should start fresh (but keep dimension access)
5. Start server

Could add an admin command or script to automate this.

### Alternative: Template Worlds

Instead of void generation, could support "template" starter islands:

- Admin builds a structure
- Save as structure NBT file
- Load and paste when creating new dimension
- Allows custom skyblock islands, themed starters, etc.

### Nether/End in Personal Dimensions

Current design: Personal dimensions are standalone, no nether/end portals work.

Options if desired:

1. Per-player nether/end as sub-dimensions (complex)
2. Shared nether/end that all personal dimensions connect to (simpler but less isolated)
3. Disable nether/end portals in personal dimensions entirely (simplest)

Recommend option 3 for initial implementation.
