# Phase 4: Invitations System - Implementation Plan

## Overview

This plan implements the invitation system for PersonalWorlds, allowing dimension owners to invite other players to visit their personal dimensions.

**Phase 4 Requirements:**

14. Add invitation storage to `PlayerDataManager`
15. Create commands for invitation management
16. Add permission check to portal entry
17. Test: Invitation flow works

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Portal Ownership & Invitation Flow                   │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Portal Activation              Portal Entry                             │
│  ─────────────────              ────────────                             │
│  Player activates portal   →    Lookup portal owner                      │
│  Store: portalPos → UUID        Check: visitor == owner?                 │
│                                       OR hasInvitation(visitor, owner)?  │
│                                 Yes → Teleport to owner's dimension      │
│                                 No  → "You have not been invited by X"   │
│                                                                          │
│  Commands                       InvitationManager                        │
│  /pw invite <player>      →     (stores invitations)                     │
│  /pw uninvite <player>                                                   │
│  /pw invites                                                             │
│  /pw go <player>          →     (direct teleport, requires permission)   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key Design Decisions:**

- **Portal ownership by activation**: Whoever lights a portal owns it permanently
- **No destination selection menu**: Portals are bound to specific owners, not chooseable
- **Nether portal-like behavior**: Return through any portal in personal dimension returns to entry point
- Command-based invitation system (no special items required)
- All invitation commands are unprivileged (any player can use them)

---

## New Files to Create

### 1. InvitationData.java

**Path:** `src/main/java/com/wickedsik/personalworlds/player/InvitationData.java`

Record storing invitation metadata:

```java
public record InvitationData(
        UUID ownerUuid,      // Dimension owner
        String ownerName,    // Display name for UI
        long invitedAt       // Timestamp
)
```

- NBT serialization via `toNbt()` / `fromNbt()`
- Pattern matches existing `ReturnData.java`

### 2. InvitationManager.java

**Path:** `src/main/java/com/wickedsik/personalworlds/player/InvitationManager.java`

Static helper class for invitation operations:

- `invite(server, owner, guest)` - Add invitation
- `uninvite(server, ownerUuid, guestUuid)` - Remove invitation + eject if visiting
- `canVisit(visitorUuid, ownerUuid)` - Permission check (owner OR has invitation)
- `showReceivedInvitations(player)` - Display invitations received
- `showSentInvitations(player)` - Display invitations sent (with clickable [Revoke])
- `handleRevocationWhileVisiting()` - Eject guest if invitation revoked mid-visit

### 3. PortalOwnershipManager.java

**Path:** `src/main/java/com/wickedsik/personalworlds/portal/PortalOwnershipManager.java`

PersistentState tracking portal ownership:

```java
// BlockPos (serialized) -> Owner UUID
private final Map<BlockPos, UUID> portalOwners = new HashMap<>();
```

**Methods:**

- `registerPortal(world, portalPos, ownerUuid)` - Called when portal activated
- `getOwner(world, portalPos)` - Lookup owner for permission check
- `removePortal(world, portalPos)` - Called when portal breaks (optional cleanup)
- `getOwnerName(server, ownerUuid)` - Get display name for messages

**Storage:** Saved per-world in `world/data/personalworlds/portal_ownership.dat`

---

## Files to Modify

### 4. PlayerDataManager.java

**Path:** `src/main/java/com/wickedsik/personalworlds/player/PlayerDataManager.java`

**Add fields:**

```java
// Guest UUID -> List<InvitationData> (who invited them)
private final Map<UUID, List<InvitationData>> receivedInvitations = new HashMap<>();

// Owner UUID -> Set<UUID> (who they invited)
private final Map<UUID, Set<UUID>> sentInvitations = new HashMap<>();
```

**Add methods:**

- `addInvitation(ownerUuid, ownerName, guestUuid)`
- `removeInvitation(ownerUuid, guestUuid)`
- `hasInvitationFrom(guestUuid, ownerUuid)`
- `getReceivedInvitations(guestUuid)`
- `getSentInvitations(ownerUuid)`

**Update serialization:**

- Add `SentInvitations` and `ReceivedInvitations` to `writeNbt()`
- Add corresponding deserialization in `fromNbt()`

### 5. PortalHelper.java

**Path:** `src/main/java/com/wickedsik/personalworlds/portal/PortalHelper.java`

**Modify `tryActivatePortal()`:**

After successful portal activation, register ownership:

```java
// After filling portal blocks...
PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
for(BlockPos pos :portalFrame.getInteriorPositions()) {
    ownershipManager.registerPortal(world, pos, player.getUuid());
}
```

**Modify `handlePortalEntry()`:**

Replace current logic with permission-based routing:

```java
public static void handlePortalEntry(ServerPlayerEntity player, BlockPos portalPos) {
    MinecraftServer server = player.getServer();
    ServerWorld currentWorld = player.getServerWorld();

    if (isInPersonalDimension(currentWorld)) {
        // Exiting personal dimension → return to entry point
        teleportToReturnPosition(player, server);
    } else {
        // Entering portal in overworld → check permission
        handleForwardPortalEntry(player, server, currentWorld, portalPos);
    }
}
```

**Add `handleForwardPortalEntry()`:**

```java
private static void handleForwardPortalEntry(
        ServerPlayerEntity player,
        MinecraftServer server,
        ServerWorld fromWorld,
        BlockPos portalPos
) {
    PortalOwnershipManager ownershipManager = PortalOwnershipManager.get(server);
    UUID portalOwner = ownershipManager.getOwner(fromWorld, portalPos);

    if (portalOwner == null) {
        // Unclaimed portal - should not happen, but handle gracefully
        // Option: Auto-claim for current player, or reject
        player.sendMessage(Text.literal("This portal has no owner."), false);
        return;
    }

    UUID visitorUuid = player.getUuid();

    // Permission check: owner OR has invitation
    if (visitorUuid.equals(portalOwner) ||
            InvitationManager.canVisit(server, visitorUuid, portalOwner)) {
        teleportToOwnerDimension(player, server, fromWorld, portalOwner);
    } else {
        String ownerName = ownershipManager.getOwnerName(server, portalOwner);
        player.sendMessage(
                Text.literal("You have not been invited by " + ownerName),
                false
        );
    }
}
```

**Add `teleportToOwnerDimension()`:**

Similar to existing `teleportToPersonalDimension()`, but accepts target owner UUID:

```java
private static void teleportToOwnerDimension(
        ServerPlayerEntity player,
        MinecraftServer server,
        ServerWorld fromWorld,
        UUID ownerUuid
) {
    // Store return position BEFORE teleporting
    PlayerDataManager dataManager = PlayerDataManager.get(server);
    ReturnData returnData = new ReturnData(
            fromWorld.getRegistryKey(),
            player.getBlockPos(),
            player.getYaw(),
            player.getPitch()
    );
    dataManager.setReturnData(player.getUuid(), returnData);

    // Get or create the owner's dimension
    DimensionRegistry registry = DimensionRegistry.get(server);
    String ownerName = /* lookup from registry or cache */;
    WorldGenType genType = registry.getDimensionData(ownerUuid)
            .map(PlayerDimensionData::generatorType)
            .orElse(WorldGenType.VOID);

    ServerWorld targetWorld = DimensionManager.getOrCreatePlayerDimension(
            server, ownerUuid, ownerName, genType
    );

    // Find destination and teleport
    BlockPos destinationPos = findExistingPortal(targetWorld)
            .map(pos -> findSafePositionNearPortal(targetWorld, pos))
            .orElseGet(() -> getOrCreateSpawnPlatform(targetWorld, genType));

    TeleportTarget target = new TeleportTarget(
            Vec3d.ofCenter(destinationPos),
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
    );
    FabricDimensions.teleport(player, targetWorld, target);

    boolean isOwnDimension = player.getUuid().equals(ownerUuid);
    if (isOwnDimension) {
        player.sendMessage(Text.literal("Welcome to your personal world!"), true);
    } else {
        player.sendMessage(Text.literal("Entering " + ownerName + "'s world"), true);
    }
}
```

### 6. TestCommands.java

**Path:** `src/main/java/com/wickedsik/personalworlds/command/TestCommands.java`

**Add commands (all unprivileged):**

- `/pw invite <player>` - Invite a player to your dimension
- `/pw uninvite <player>` - Revoke invitation (also triggered by clickable [Revoke])
- `/pw invites` - Show all invitations (sent and received)
- `/pw go <player>` - Direct teleport (requires permission: owner OR invitation)

---

## Implementation Order

1. **InvitationData.java** - Data model (simple record)
2. **PortalOwnershipManager.java** - Portal ownership tracking
3. **PlayerDataManager additions** - Invitation storage layer
4. **InvitationManager.java** - Business logic
5. **PortalHelper modifications** - Permission checks on entry
6. **TestCommands.java** - Command interface

---

## Edge Case Handling

| Scenario                            | Handling                                               |
|-------------------------------------|--------------------------------------------------------|
| Enter own portal                    | Always allowed, teleport to own dimension              |
| Enter other's portal with invite    | Allowed, teleport to owner's dimension                 |
| Enter other's portal without invite | Denied with message: "You have not been invited by X"  |
| Invited to non-existent dimension   | Dimension created on-demand when visited               |
| Revoked while visiting              | Guest teleported to return position or overworld spawn |
| Portal has no owner (edge case)     | Show error message, do not teleport                    |
| Inviting offline player             | Error message: player must be online                   |
| Inviting yourself                   | Error message: cannot invite yourself                  |
| Portal destroyed                    | Ownership data can be cleaned up (optional)            |

---

## Detailed Component Specifications

### InvitationData Record

```java
package com.wickedsik.personalworlds.player;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Represents a single invitation from owner to guest.
 * Stored in PlayerDataManager.
 */
public record InvitationData(
        UUID ownerUuid,      // Who owns the dimension
        String ownerName,    // Display name (for UI)
        long invitedAt       // Timestamp for potential expiration
) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putLong("InvitedAt", invitedAt);
        return nbt;
    }

    public static InvitationData fromNbt(NbtCompound nbt) {
        return new InvitationData(
                nbt.getUuid("OwnerUuid"),
                nbt.getString("OwnerName"),
                nbt.getLong("InvitedAt")
        );
    }
}
```

### PortalOwnershipManager

```java
package com.wickedsik.personalworlds.portal;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which player owns which portal.
 * Ownership is established when a player activates a portal.
 */
public class PortalOwnershipManager extends PersistentState {
    private static final String DATA_NAME = "personalworlds_portal_ownership";

    // Compound key: "worldId:x,y,z" -> Owner UUID
    private final Map<String, UUID> portalOwners = new HashMap<>();

    public void registerPortal(World world, BlockPos pos, UUID ownerUuid) {
        String key = makeKey(world, pos);
        portalOwners.put(key, ownerUuid);
        markDirty();
    }

    public UUID getOwner(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        return portalOwners.get(key);
    }

    public void removePortal(World world, BlockPos pos) {
        String key = makeKey(world, pos);
        portalOwners.remove(key);
        markDirty();
    }

    public String getOwnerName(MinecraftServer server, UUID ownerUuid) {
        // Try to get from online player first
        var player = server.getPlayerManager().getPlayer(ownerUuid);
        if (player != null) {
            return player.getName().getString();
        }
        // Fallback: lookup from DimensionRegistry or return UUID string
        // Implementation depends on how player names are cached
        return ownerUuid.toString().substring(0, 8);
    }

    private String makeKey(World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() +
                ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // NBT serialization methods...

    public static PortalOwnershipManager get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(
                        PortalOwnershipManager::fromNbt,
                        PortalOwnershipManager::new,
                        DATA_NAME
                );
    }
}
```

### Portal Entry Permission Flow

When a player collides with a portal block in the overworld:

```
1. Lookup portal owner from PortalOwnershipManager
   └─ If no owner → error message, abort

2. Check permission:
   └─ visitor == owner? → ALLOWED
   └─ hasInvitationFrom(visitor, owner)? → ALLOWED
   └─ Otherwise → DENIED ("You have not been invited by X")

3. If ALLOWED:
   └─ Store return position
   └─ Get/create owner's dimension
   └─ Teleport to dimension
   └─ Show appropriate message
```

### Return Behavior (Nether Portal-like)

When a player uses ANY portal inside a personal dimension:

1. Retrieve stored return position from `PlayerDataManager`
2. Teleport to return position (exact entry point)
3. Clear return data
4. Show "Returned to the overworld" message

This matches nether portal behavior where you exit at your entry point.

### Revocation While Visiting

When an owner revokes an invitation while the guest is in their dimension:

1. Check if revoked guest is currently in owner's dimension
2. Retrieve guest's stored return position from `PlayerDataManager`
3. If return position exists, teleport there; otherwise, overworld spawn
4. Clear return data
5. Send message: "Your invitation was revoked. Returning to overworld."

---

## Command Specifications

### `/pw invite <player>`

Invites an online player to visit your personal dimension.

**Behavior:**

- Player argument uses Minecraft's player selector (tab-completable online players)
- Cannot invite yourself
- Cannot invite the same player twice (idempotent - shows "already invited")
- Sends confirmation to inviter: "Invited <player> to your dimension"
- Sends notification to invitee: "<player> invited you to their dimension"

### `/pw uninvite <player>`

Revokes an invitation from a player.

**Behavior:**

- Player argument uses Minecraft's player selector
- If invitee is currently in your dimension, eject them
- Sends confirmation: "Revoked <player>'s invitation"
- If player was visiting, they receive: "Your invitation was revoked. Returning to overworld."

### `/pw invites`

Shows all invitation information for the executing player.

**Output format:**

```
=== Invitations ===

Sent (players who can visit you):
 - PlayerA [Revoke]
 - PlayerB [Revoke]

Received (dimensions you can visit):
 - PlayerC's World
 - PlayerD's World
```

[Revoke] is a clickable action that runs `/pw uninvite <player>`.

### `/pw go <player>`

Teleports directly to a player's dimension (requires permission).

**Behavior:**

- Permission check: must be owner OR have invitation
- Stores return position before teleporting
- Creates dimension on-demand if not yet generated
- If no permission: "You have not been invited by <player>"
- Can be used to enter your own dimension without a portal

---

## Verification Plan

### Manual Testing

1. **Portal ownership:**
    - Player A builds and lights a portal
    - Player B tries to enter → should be denied
    - Player A invites Player B
    - Player B enters → should teleport to A's dimension

2. **Own portal access:**
    - Player creates and lights their own portal
    - Player enters their own portal → always allowed

3. **Revocation flow:**
    - Invite player, they enter your dimension
    - Use `/pw invites` and click [Revoke]
    - Verify guest is ejected to overworld

4. **Persistence test:**
    - Activate portal, send invitations
    - Restart server
    - Verify portal ownership and invitations still exist

5. **Permission enforcement:**
    - Try to enter uninvited player's portal → denied
    - Try `/pw go <player>` without invitation → denied

### Commands for Testing

```
/pw invite <player>     # Invite player
/pw invites             # List all invitations
/pw uninvite <player>   # Revoke invitation
/pw go <player>         # Direct teleport (requires permission)
```

---

## Testing Checklist

- [ ] Portal activation registers ownership correctly
- [ ] Owner can always enter their own portal
- [ ] Non-owner without invitation is denied with message
- [ ] Non-owner with invitation can enter portal
- [ ] Owner can invite online player via `/pw invite <player>`
- [ ] `/pw invites` shows sent invitations with clickable [Revoke]
- [ ] `/pw invites` shows received invitations
- [ ] Owner can revoke invitation via `/pw uninvite <player>`
- [ ] Owner can revoke invitation via clickable [Revoke] button
- [ ] Revoked player is ejected if currently visiting
- [ ] Invitations persist across server restart
- [ ] Portal ownership persists across server restart
- [ ] Return through any portal in personal dimension returns to entry point
- [ ] Cannot invite yourself (error message)
- [ ] Cannot invite offline player (error message)
- [ ] `/pw go <player>` respects permission check

---

## File Summary

| File                                 | Action | Priority |
|--------------------------------------|--------|----------|
| `player/InvitationData.java`         | CREATE | 1        |
| `portal/PortalOwnershipManager.java` | CREATE | 2        |
| `player/PlayerDataManager.java`      | MODIFY | 3        |
| `player/InvitationManager.java`      | CREATE | 4        |
| `portal/PortalHelper.java`           | MODIFY | 5        |
| `command/TestCommands.java`          | MODIFY | 6        |
