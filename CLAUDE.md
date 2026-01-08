# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**PersonalWorlds** — A Fabric mod for Minecraft 1.20.4 that provides each player with their own isolated, persistent dimension. The primary use case is dimension survival through world resets: when the overworld/nether/end are deleted and regenerated, each player's personal world remains intact.

See `docs/per-player-dimensions-mod-plan.md` for the complete architectural specification.

## Build Commands

```bash
# Build the mod JAR
./gradlew build

# Clean build artifacts
./gradlew clean

# Run Minecraft client with the mod loaded
./gradlew runClient

# Run Minecraft server with the mod loaded
./gradlew runServer

# Generate Minecraft sources for IDE navigation
./gradlew genSources
```

The compiled mod JAR will be located at: `build/libs/personalworlds-0.1.0.jar`

## Project Structure

This is a standard Fabric mod project with split environment source sets:

- **`src/main/java/`** — Server-side and common code
- **`src/client/java/`** — Client-side only code
- **`src/main/resources/`** — Server/common resources (fabric.mod.json, mixins)
- **`src/client/resources/`** — Client-only resources (client mixins)

### Key Configuration Files

- **`gradle.properties`** — Minecraft version (1.21.11), Fabric Loader version, mod metadata
- **`build.gradle`** — Dependencies, build configuration, uses Fabric Loom for mod development
- **`src/main/resources/fabric.mod.json`** — Mod metadata, entrypoints, dependencies

### Current State

The project is currently a template. The actual PersonalWorlds implementation follows the architecture in `docs/per-player-dimensions-mod-plan.md`:

- **Fantasy dependency** — Required for runtime dimension creation (`xyz.nucleoid:fantasy:0.5.0+1.20.4`)
  - Must be added to `build.gradle` repositories: `maven { url = 'https://maven.nucleoid.xyz/' }`
  - Must be added to dependencies: `modImplementation include("xyz.nucleoid:fantasy:0.5.0+1.20.4")`

- **Package structure** (under `src/main/java/com/wickedsik/personalworlds/`):
  - `dimension/` — Dimension creation, registry, lifecycle management (uses Fantasy)
  - `portal/` — Portal block, frame detection, activation, teleportation
  - `player/` — Player data, invitations, return positions (PersistentState)
  - `config/` — Configuration options
  - `registry/` — Block/item registration
  - `event/` — Server lifecycle, player events
  - `command/` — Optional admin commands

## Architecture Highlights

### Dimension Persistence Strategy

Player dimensions are stored under `world/dimensions/personalworlds/pw_<uuid>/` and survive world resets because they are separate from the main world folders (`world/region/`, `world/DIM-1/`, `world/DIM1/`).

A `DimensionRegistry` (PersistentState saved to `world/data/personalworlds/registry.dat`) tracks all player dimensions for restoration on server start.

### Fantasy Integration

Fantasy (`xyz.nucleoid:fantasy`) is critical for this mod:

- **`RuntimeWorldConfig`** — Configure dimension type, chunk generator, seed, game rules
- **`fantasy.getOrOpenPersistentWorld()`** — Create or load dimensions that survive restarts
- **`RuntimeWorldHandle`** — Manage dimension lifecycle (unload when empty, delete if needed)

Without Fantasy, Fabric API alone cannot create dimensions at runtime.

### Component Dependencies

```
Portal Entry
    ↓
PortalHelper.handlePortalEntry()
    ↓
DimensionManager.getOrCreatePlayerDimension()
    ↓
Fantasy.getOrOpenPersistentWorld()
    ↓
DimensionRegistry.registerDimension() (if new)
```

### Lifecycle Events

- **Server start** → `DimensionRegistry.restoreAllDimensions()` reloads all registered dimensions
- **Portal entry** → Create/load dimension, store return position, teleport player
- **Portal exit** → Retrieve return position, teleport back, schedule dimension unload check
- **Player disconnect** → If in personal dimension, schedule unload check (delayed to handle reconnects)
- **Server tick** → Every 30 seconds, unload empty dimensions for performance

## Critical Implementation Notes

### Dimension Unloading

Don't unload dimensions immediately when the last player leaves. Use a delay (600 ticks / 30 seconds) to prevent thrashing if a player disconnects and reconnects quickly.

### Return Position Handling

When a player enters their personal dimension, store their exact position and dimension in `PlayerDataManager` (PersistentState). The return portal must teleport them back to this exact location, not to spawn or a generic position.

### Portal Collision Detection

The `PersonalPortalBlock` must implement `onEntityCollision()` to detect when players enter the portal. Use `entity.hasPortalCooldown()` to prevent rapid flickering when standing in the portal.

### Invitation System

Since the mod uses portals (no commands), invitations must be handled via:
- An invitation item (right-click another player to invite them)
- A portal interaction menu (show list of accessible dimensions before teleporting)
- A separate "visit portal" structure

The plan recommends an item-based invitation system as the simplest approach.

### Void World Generation

For void-style personal dimensions, implement a custom `VoidChunkGenerator` that extends `ChunkGenerator` and returns empty chunks. Register it with `Registry.register(Registries.CHUNK_GENERATOR, ...)` so Fantasy can use it.

### Starter Platform

For void worlds, create a small starter platform (5x5 grass) at spawn (0, 64, 0) when the dimension is first created. Include a pre-built return portal frame to prevent players from getting stuck.

## Testing Strategy

### Local Testing

Run the Minecraft server with `./gradlew runServer` and multiple clients with `./gradlew runClient`. Test:

1. First portal entry creates dimension
2. Dimension persists after server restart (`stop` command, then `./gradlew runServer`)
3. Return portal works correctly
4. Invitation system functions (if implemented)

### World Reset Testing

1. Stop server
2. Delete `world/region/`, `world/DIM-1/`, `world/DIM1/`
3. Start server
4. Verify personal dimensions still exist and are accessible

### Performance Testing

Create 15 player dimensions (maximum expected concurrent players) and verify:
- Server performance remains acceptable
- Dimensions unload when empty
- Dimensions reload correctly when accessed

## Minecraft Version Notes

The mod targets **Minecraft 1.20.4** for compatibility with existing modded servers. This version uses:
- **Java 17** (not Java 21)
- **Yarn mappings** (not Mojang mappings)
- **Fantasy 0.5.0+1.20.4** for runtime dimension creation
- **FabricDimensions.teleport()** for cross-dimension teleportation

**Key API differences from 1.21.x:**
- `Identifier` instead of `ResourceLocation`
- `new Identifier(namespace, path)` instead of `Identifier.of()`
- `ServerWorld` instead of `ServerLevel`
- `ServerPlayerEntity` instead of `ServerPlayer`
- `NbtCompound` instead of `CompoundTag`
- `PersistentState` instead of `SavedData`
- `Text.literal()` instead of `Component.literal()`

**If updating Minecraft versions:**
1. Update `minecraft_version` in `gradle.properties`
2. Update `fabric_version` to match (check https://fabricmc.net/develop)
3. Verify Fantasy compatibility (may need version update)
4. Check for API changes in Fabric API dimension/teleportation modules
5. For 1.21+: Switch to Mojang mappings and update class names accordingly

## Reference Documentation

- **Fantasy library:** https://github.com/NucleoidMC/fantasy
- **Fabric Wiki — Dimensions:** https://fabricmc.net/wiki/tutorial:dimension
- **Fabric Wiki — Custom Portals:** https://fabricmc.net/wiki/tutorial:portal
- **Fabric API Javadoc:** https://maven.fabricmc.net/docs/fabric-api-latest/

## Implementation Status

**Current phase:** Template / Pre-Phase 1

The codebase is currently a standard Fabric mod template. The actual PersonalWorlds implementation will follow the phased approach in `docs/per-player-dimensions-mod-plan.md`:

1. Core Infrastructure (DimensionRegistry, DimensionManager)
2. Portal System (blocks, detection, activation, teleportation)
3. Player Data (return positions)
4. Invitations (item or UI-based)
5. Polish (config, starter platform, void generator, admin commands)
6. Hardening (edge cases, performance testing)
