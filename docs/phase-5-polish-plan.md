# Phase 5: Polish - Implementation Plan

## Overview

This plan implements polish features for PersonalWorlds, including configuration, admin commands with proper permissions, visual feedback, and the void chunk generator option.

**Phase 5 Requirements:**

18. Add configuration file
19. Add starter platform generation
20. Add void chunk generator option
21. Add admin commands
22. Add particles/sounds/visual feedback
23. Test: Full feature set

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Phase 5 Components                              │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ModConfig                  AdminCommands               PermissionHelper │
│  (JSON file)          ->    (privileged ops)       <-   (permission      │
│                                                          checks)         │
│                                                                          │
│  VoidChunkGenerator         StarterPlatform             VisualEffects    │
│  (empty world option)       (configurable spawn)        (particles/      │
│                                                          sounds)         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Permission System

### Option Analysis

| Approach | Dependency | Flexibility | Fallback Behavior |
|----------|------------|-------------|-------------------|
| Vanilla op levels | None | Low (levels 0-4 only) | N/A |
| fabric-permissions-api | ~12KB bundled | High (named nodes) | Falls back to op levels |

### Recommendation: fabric-permissions-api

**Rationale:**
- Tiny footprint (~12KB when bundled)
- No user-visible dependency (bundled in JAR)
- Falls back to vanilla op levels when no permission plugin installed
- Allows LuckPerms/other permission systems to integrate later
- Maintained by lucko (LuckPerms developer)

### Implementation

**build.gradle addition:**

```groovy
dependencies {
    // Bundle fabric-permissions-api within mod JAR
    include(modImplementation('me.lucko:fabric-permissions-api:0.3.3'))
}
```

**Permission nodes:**

| Node | Default Level | Description |
|------|---------------|-------------|
| `personalworlds.admin.list` | 2 | List all player dimensions |
| `personalworlds.admin.delete` | 4 | Delete a player's dimension |
| `personalworlds.admin.teleport` | 2 | Teleport to any dimension |
| `personalworlds.admin.info` | 2 | View dimension info |
| `personalworlds.admin.reload` | 3 | Reload configuration |

**Note:** Player commands (`/pw invite`, `/pw go`, etc.) remain unprivileged (level 0).

---

## New Files to Create

### 1. ModConfig.java

**Path:** `src/main/java/com/wickedsik/personalworlds/config/ModConfig.java`

Configuration manager with JSON persistence:

```java
public class ModConfig {
    private static ModConfig INSTANCE;
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("personalworlds.json");

    // World generation
    public WorldGenType defaultWorldType = WorldGenType.VOID;
    public boolean allowPlayerWorldTypeChoice = false;

    // Portal
    public String frameBlock = "minecraft:crying_obsidian";
    public String activationItem = "minecraft:ender_pearl";
    public boolean consumeActivationItem = false;

    // Starter platform (void worlds)
    public boolean createStarterPlatform = true;
    public int platformRadius = 2;  // 5x5 platform
    public String platformBlock = "minecraft:grass_block";
    public boolean includeReturnPortal = true;
    public boolean includeStarterChest = false;

    // Invitations
    public int maxInvitationsPerPlayer = 20;

    // Performance
    public int unloadEmptyDimensionDelayTicks = 600;  // 30 seconds
    public int pendingSelectionTimeoutTicks = 600;    // 30 seconds

    // Messages (customizable)
    public String messageInviteSent = "Invited %player% to your dimension";
    public String messageInviteReceived = "%player% invited you to their dimension";
    public String messageRevoked = "Revoked %player%'s invitation";
    public String messageEjected = "Your invitation was revoked. Returning to overworld.";

    public enum WorldGenType {
        VOID,       // Empty void with starter platform
        OVERWORLD,  // Full overworld generation
        FLAT        // Superflat
    }

    public static ModConfig get() { ... }
    public void save() { ... }
    public static void load() { ... }
}
```

### 2. PermissionHelper.java

**Path:** `src/main/java/com/wickedsik/personalworlds/util/PermissionHelper.java`

Centralized permission checking:

```java
public final class PermissionHelper {
    public static final String ADMIN_LIST = "personalworlds.admin.list";
    public static final String ADMIN_DELETE = "personalworlds.admin.delete";
    public static final String ADMIN_TELEPORT = "personalworlds.admin.teleport";
    public static final String ADMIN_INFO = "personalworlds.admin.info";
    public static final String ADMIN_RELOAD = "personalworlds.admin.reload";

    /**
     * Check if source has permission with op level fallback.
     */
    public static boolean check(ServerCommandSource source, String permission, int defaultOpLevel) {
        return Permissions.check(source, permission, defaultOpLevel);
    }

    /**
     * Create a requires predicate for command registration.
     */
    public static Predicate<ServerCommandSource> require(String permission, int defaultOpLevel) {
        return Permissions.require(permission, defaultOpLevel);
    }
}
```

### 3. AdminCommands.java

**Path:** `src/main/java/com/wickedsik/personalworlds/command/AdminCommands.java`

Privileged administration commands:

```java
public class AdminCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pw")
                .then(CommandManager.literal("admin")
                    .then(listCommand())
                    .then(infoCommand())
                    .then(deleteCommand())
                    .then(teleportCommand())
                    .then(reloadCommand())
                )
        );
    }
}
```

**Commands:**

- `/pw admin list` - List all registered dimensions
- `/pw admin info <player>` - Show dimension details (size, created, last accessed)
- `/pw admin delete <player>` - Delete a player's dimension (with confirmation)
- `/pw admin tp <player>` - Teleport to a player's dimension
- `/pw admin reload` - Reload configuration file

### 4. StarterPlatformBuilder.java

**Path:** `src/main/java/com/wickedsik/personalworlds/dimension/StarterPlatformBuilder.java`

Configurable spawn platform generation:

```java
public class StarterPlatformBuilder {

    /**
     * Creates the starter platform at spawn in a new void dimension.
     * Called once when dimension is first created.
     */
    public static void build(ServerWorld world, BlockPos center) {
        ModConfig config = ModConfig.get();
        if (!config.createStarterPlatform) return;

        Block platformBlock = Registries.BLOCK.get(new Identifier(config.platformBlock));
        int radius = config.platformRadius;

        // Create platform
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.setBlockState(
                    center.down().add(x, 0, z),
                    platformBlock.getDefaultState(),
                    Block.NOTIFY_LISTENERS
                );
            }
        }

        // Optional return portal
        if (config.includeReturnPortal) {
            buildReturnPortal(world, center.add(radius + 2, 0, 0));
        }

        // Optional starter chest
        if (config.includeStarterChest) {
            placeStarterChest(world, center.add(0, 0, -radius));
        }
    }

    private static void buildReturnPortal(ServerWorld world, BlockPos base) {
        // Build 4x5 portal frame
        Block frameBlock = Registries.BLOCK.get(
            new Identifier(ModConfig.get().frameBlock)
        );
        // ... frame construction logic
    }
}
```

### 5. VisualEffects.java

**Path:** `src/main/java/com/wickedsik/personalworlds/util/VisualEffects.java`

Centralized particle and sound effects:

```java
public final class VisualEffects {

    /**
     * Play portal activation effects.
     */
    public static void portalActivation(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos,
            SoundEvents.BLOCK_END_PORTAL_SPAWN,
            SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Spawn particles around portal frame
        for (ServerPlayerEntity player : world.getPlayers()) {
            world.spawnParticles(player,
                ParticleTypes.REVERSE_PORTAL,
                true, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                50, 0.5, 1.0, 0.5, 0.1);
        }
    }

    /**
     * Play teleportation effects at origin.
     */
    public static void teleportDepart(ServerWorld world, Vec3d pos) {
        world.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * Play teleportation effects at destination.
     */
    public static void teleportArrive(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(
            ParticleTypes.PORTAL,
            player.getX(), player.getY() + 1, player.getZ(),
            30, 0.5, 1.0, 0.5, 0.1);
    }

    /**
     * Play invitation notification sound.
     */
    public static void invitationReceived(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    /**
     * Play ejection warning sound.
     */
    public static void ejectionWarning(ServerPlayerEntity player) {
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
    }
}
```

---

## Files to Modify

### 6. VoidChunkGenerator.java

**Path:** `src/main/java/com/wickedsik/personalworlds/dimension/VoidChunkGenerator.java`

Already exists from Phase 1, but ensure it's properly integrated with configuration.

**Verify:**
- Registered in `ModRegistry`
- Selectable via `ModConfig.defaultWorldType`
- Returns completely empty chunks

### 7. DimensionManager.java

**Path:** `src/main/java/com/wickedsik/personalworlds/dimension/DimensionManager.java`

**Modifications:**

- Read `WorldGenType` from config when creating dimensions
- Call `StarterPlatformBuilder.build()` for new void dimensions
- Add `getDimensionInfo(UUID)` method for admin info command

```java
public static DimensionInfo getDimensionInfo(MinecraftServer server, UUID ownerUuid) {
    DimensionRegistry registry = DimensionRegistry.get(server);
    PlayerDimensionData data = registry.getDimensionData(ownerUuid);
    if (data == null) return null;

    ServerWorld world = getPlayerDimension(server, ownerUuid);
    long sizeBytes = calculateDimensionSize(world);  // Sum region file sizes

    return new DimensionInfo(
        data.ownerUuid(),
        data.ownerName(),
        data.createdAt(),
        data.lastAccessed(),
        sizeBytes,
        world != null && !world.getPlayers().isEmpty()
    );
}

public record DimensionInfo(
    UUID ownerUuid,
    String ownerName,
    long createdAt,
    long lastAccessed,
    long sizeBytes,
    boolean currentlyLoaded
) {}
```

### 8. PortalHelper.java

**Path:** `src/main/java/com/wickedsik/personalworlds/portal/PortalHelper.java`

**Add visual effects:**

```java
public static boolean tryActivatePortal(World world, BlockPos clickedPos, PlayerEntity player) {
    // ... existing frame detection ...

    // Add visual effects on successful activation
    if (world instanceof ServerWorld serverWorld) {
        VisualEffects.portalActivation(serverWorld, clickedPos);
    }

    return true;
}

public static void teleportToDestination(ServerPlayerEntity player, ServerWorld targetWorld, BlockPos targetPos) {
    ServerWorld origin = player.getServerWorld();
    Vec3d originPos = player.getPos();

    // Departure effects
    VisualEffects.teleportDepart(origin, originPos);

    // Teleport
    TeleportTarget target = new TeleportTarget(
        Vec3d.ofCenter(targetPos),
        Vec3d.ZERO,
        player.getYaw(),
        player.getPitch()
    );
    FabricDimensions.teleport(player, targetWorld, target);

    // Arrival effects
    VisualEffects.teleportArrive(player);
}
```

### 9. InvitationManager.java

**Path:** `src/main/java/com/wickedsik/personalworlds/player/InvitationManager.java`

**Add notification effects:**

```java
public static void invite(MinecraftServer server, ServerPlayerEntity owner, ServerPlayerEntity guest) {
    // ... existing invitation logic ...

    // Play notification sound for guest
    VisualEffects.invitationReceived(guest);
}

public static void handleRevocationWhileVisiting(ServerPlayerEntity guest) {
    // Play warning sound before ejection
    VisualEffects.ejectionWarning(guest);

    // ... existing ejection logic ...
}
```

### 10. TestCommands.java

**Path:** `src/main/java/com/wickedsik/personalworlds/command/TestCommands.java`

**Integrate admin commands:**

```java
public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    // Existing player commands (unprivileged)
    // ... /pw invite, /pw uninvite, /pw invites, /pw go ...

    // Admin commands (delegated to AdminCommands)
    AdminCommands.register(dispatcher);
}
```

### 11. PersonalWorldsMod.java

**Path:** `src/main/java/com/wickedsik/personalworlds/PersonalWorldsMod.java`

**Add config loading:**

```java
@Override
public void onInitialize() {
    // Load configuration first
    ModConfig.load();

    // ... existing initialization ...
}
```

### 12. fabric.mod.json

**Path:** `src/main/resources/fabric.mod.json`

**Add permission API dependency:**

```json
{
  "depends": {
    "fabric-permissions-api-v0": "*"
  }
}
```

---

## Implementation Order

1. **ModConfig.java** - Configuration system (enables everything else)
2. **PermissionHelper.java** - Permission abstraction
3. **AdminCommands.java** - Admin command structure
4. **StarterPlatformBuilder.java** - Spawn platform generation
5. **VisualEffects.java** - Particle and sound effects
6. **DimensionManager modifications** - Config integration + admin info
7. **PortalHelper modifications** - Visual effects integration
8. **InvitationManager modifications** - Notification sounds
9. **VoidChunkGenerator verification** - Ensure proper registration
10. **TestCommands.java** - Command registration
11. **build.gradle** - Add permissions dependency
12. **fabric.mod.json** - Declare dependency

---

## Configuration File

**Location:** `config/personalworlds.json`

**Default contents:**

```json
{
  "worldGeneration": {
    "defaultWorldType": "VOID",
    "allowPlayerWorldTypeChoice": false
  },
  "portal": {
    "frameBlock": "minecraft:crying_obsidian",
    "activationItem": "minecraft:ender_pearl",
    "consumeActivationItem": false
  },
  "starterPlatform": {
    "enabled": true,
    "radius": 2,
    "block": "minecraft:grass_block",
    "includeReturnPortal": true,
    "includeStarterChest": false
  },
  "invitations": {
    "maxPerPlayer": 20
  },
  "performance": {
    "unloadEmptyDimensionDelayTicks": 600,
    "pendingSelectionTimeoutTicks": 600
  },
  "messages": {
    "inviteSent": "Invited %player% to your dimension",
    "inviteReceived": "%player% invited you to their dimension",
    "revoked": "Revoked %player%'s invitation",
    "ejected": "Your invitation was revoked. Returning to overworld."
  }
}
```

---

## Admin Command Specifications

### `/pw admin list`

Lists all registered player dimensions.

**Permission:** `personalworlds.admin.list` (default: op level 2)

**Output:**

```
=== Player Dimensions (3 total) ===
 - Steve (loaded, 15.2 MB)
 - Alex (unloaded, 8.7 MB)
 - Notch (unloaded, 42.1 MB)
```

### `/pw admin info <player>`

Shows detailed information about a player's dimension.

**Permission:** `personalworlds.admin.info` (default: op level 2)

**Output:**

```
=== Steve's Dimension ===
Owner: Steve (550e8400-e29b-41d4-a716-446655440000)
Created: 2024-01-15 14:32:00
Last accessed: 2024-01-20 18:45:00
Size: 15.2 MB
Status: Loaded (1 player inside)
World type: VOID
Invited players: 3
```

### `/pw admin delete <player>`

Deletes a player's dimension permanently.

**Permission:** `personalworlds.admin.delete` (default: op level 4)

**Behavior:**
- Requires confirmation (run twice within 30 seconds)
- Ejects all players currently in dimension
- Removes dimension files
- Removes from registry

**Output:**

```
WARNING: This will permanently delete Steve's dimension!
Run the command again within 30 seconds to confirm.
```

Then on confirmation:

```
Deleted Steve's dimension (15.2 MB freed).
```

### `/pw admin tp <player>`

Teleports admin to a player's dimension.

**Permission:** `personalworlds.admin.teleport` (default: op level 2)

**Behavior:**
- Stores admin's return position
- Teleports to dimension spawn point
- Works even if dimension is currently unloaded

### `/pw admin reload`

Reloads configuration from disk.

**Permission:** `personalworlds.admin.reload` (default: op level 3)

**Output:**

```
Configuration reloaded.
```

---

## Edge Case Handling

| Scenario | Handling |
|----------|----------|
| Config file missing | Create default config on first load |
| Config file malformed | Log error, use defaults, don't overwrite |
| Invalid block ID in config | Fall back to default, log warning |
| Admin deletes own dimension while inside | Eject to overworld first |
| Delete dimension with visitors | Eject all visitors before deletion |
| Reload config while dimensions loaded | Apply changes to new dimensions only |

---

## Testing Checklist

### Configuration
- [ ] Config file created on first launch
- [ ] Config values properly loaded
- [ ] Invalid config values handled gracefully
- [ ] `/pw admin reload` applies changes

### Permissions
- [ ] Admin commands require correct permission level
- [ ] Non-ops cannot use admin commands
- [ ] Player commands remain unprivileged
- [ ] Permission nodes work with LuckPerms (if installed)

### Admin Commands
- [ ] `/pw admin list` shows all dimensions
- [ ] `/pw admin info <player>` shows correct details
- [ ] `/pw admin delete <player>` requires confirmation
- [ ] `/pw admin delete <player>` ejects visitors
- [ ] `/pw admin tp <player>` works for unloaded dimensions

### Starter Platform
- [ ] Platform created for new void dimensions
- [ ] Platform size matches config
- [ ] Return portal included when configured
- [ ] Starter chest included when configured
- [ ] No platform for non-void world types

### Visual Effects
- [ ] Portal activation plays sound and particles
- [ ] Teleportation has departure/arrival effects
- [ ] Invitation notification sound plays
- [ ] Ejection warning sound plays

### Void Generator
- [ ] Void worlds generate completely empty
- [ ] Only starter platform exists at spawn
- [ ] No ore, caves, or terrain generation

---

## File Summary

| File | Action | Priority |
|------|--------|----------|
| `config/ModConfig.java` | CREATE | 1 |
| `util/PermissionHelper.java` | CREATE | 2 |
| `command/AdminCommands.java` | CREATE | 3 |
| `dimension/StarterPlatformBuilder.java` | CREATE | 4 |
| `util/VisualEffects.java` | CREATE | 5 |
| `dimension/DimensionManager.java` | MODIFY | 6 |
| `portal/PortalHelper.java` | MODIFY | 7 |
| `player/InvitationManager.java` | MODIFY | 8 |
| `dimension/VoidChunkGenerator.java` | VERIFY | 9 |
| `command/TestCommands.java` | MODIFY | 10 |
| `build.gradle` | MODIFY | 11 |
| `fabric.mod.json` | MODIFY | 12 |

---

## Dependencies

### fabric-permissions-api

**Maven coordinates:** `me.lucko:fabric-permissions-api:0.3.3`

**Why this version:** Compatible with Minecraft 1.20.4 (version 0.3.3 supports 1.21.5 and earlier)

**Bundle approach:** Include in mod JAR (~12KB) so users don't need separate installation

**Fallback behavior:** When no permission plugin installed, falls back to vanilla op levels

---

## Sources

- [fabric-permissions-api GitHub](https://github.com/lucko/fabric-permissions-api)
- [fabric-permissions-api Usage Documentation](https://github.com/lucko/fabric-permissions-api/blob/master/USAGE.md)
