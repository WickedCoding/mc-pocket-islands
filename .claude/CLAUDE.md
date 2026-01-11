# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Project Overview

**Pocket Islands** — A Fabric mod for Minecraft 1.20.4 that provides each player
with their own isolated, persistent pocket dimension island. The primary use
case is dimension survival through world resets: when the overworld/nether/end
are deleted and regenerated, each player's pocket island remains intact.

See `docs/per-player-dimensions-mod-plan.md` for the complete architectural
specification.

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

The compiled mod JAR will be located at: `build/libs/personalworlds-<version>.jar`

## Project Structure

This is a standard Fabric mod project with split environment source sets:

- **`src/main/java/`** — Server-side and common code
- **`src/client/java/`** — Client-side only code
- **`src/main/resources/`** — Server/common resources (fabric.mod.json, mixins, lang files)
- **`src/client/resources/`** — Client-only resources (textures, client mixins)

### Key Configuration Files

- **`gradle.properties`** — Minecraft version (1.20.4), Fabric Loader version, mod metadata
- **`build.gradle`** — Dependencies, build configuration, uses Fabric Loom for mod development
- **`src/main/resources/fabric.mod.json`** — Mod metadata, entrypoints, dependencies

### Package Structure

Under `src/main/java/com/wickedsik/personalworlds/`:

- **`dimension/`** — Dimension creation, registry, lifecycle management (uses Fantasy)
- **`portal/`** — Portal block, frame detection, activation, teleportation
- **`player/`** — Player data, invitations, return positions (PersistentState)
- **`config/`** — Configuration options
- **`registry/`** — Block/item registration
- **`event/`** — Server lifecycle, player events
- **`command/`** — Admin and player commands (`/pi`)

### Dependencies

- **Fantasy** — Required for runtime dimension creation (`xyz.nucleoid:fantasy:0.5.0+1.20.4`)
- **Fabric Permissions API** — Optional soft dependency for LuckPerms integration

## Commit Format

This project uses a structured commit message format:

```
<Type>: <Description>
```

### Components

- **Type** — Category of change (see below)
- **Description** — Brief, imperative description of the change

### Commit Types

| Type       | Description                                |
|------------|--------------------------------------------|
| `Feature`  | New functionality or capability            |
| `Fix`      | Bug fix or correction                      |
| `Refactor` | Code restructuring without behavior change |
| `Docs`     | Documentation updates                      |
| `Test`     | Test additions or modifications            |
| `Chore`    | Build, CI, or maintenance tasks            |

### Examples

```
Feature: Implement localization system with language file support
Fix: /pi leave command now respects stored return position
Refactor: Extract command executors from ModCommands class
Feature: Add configurable island layer composition
Docs: Update README with FAQ section
Chore: Update Fabric API to 0.97.0
```

### Guidelines

- Use imperative mood: "Add feature" not "Added feature"
- Keep the description under 72 characters
- One logical change per commit

## Architecture Highlights

### Dimension Persistence Strategy

Player dimensions are stored under `world/dimensions/personalworlds/pw_<uuid>/`
and survive world resets because they are separate from the main world folders
(`world/region/`, `world/DIM-1/`, `world/DIM1/`).

A `DimensionRegistry` (PersistentState saved to `world/data/personalworlds/registry.dat`)
tracks all player dimensions for restoration on server start.

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

Don't unload dimensions immediately when the last player leaves. Use a delay
(600 ticks / 30 seconds) to prevent thrashing if a player disconnects and 
reconnects quickly.

### Return Position Handling

When a player enters their personal dimension, store their exact position and
dimension in `PlayerDataManager` (PersistentState). The return portal must
teleport them back to this exact location, not to spawn or a generic position.

### Portal Collision Detection

The `PersonalPortalBlock` must implement `onEntityCollision()` to detect when
players enter the portal. Use `entity.hasPortalCooldown()` to prevent rapid
flickering when standing in the portal.

### Invitation System

Players can invite others to visit their island via commands
(`/pi invite <player>`). Invited players can visit using `/pi go <player>` or by
entering the owner's portal.

### Void World Generation

The `VoidChunkGenerator` extends `ChunkGenerator` and returns empty chunks. It's
registered with Fantasy for use in pocket dimensions.

### Starter Platform

For void worlds, a configurable starter platform is created at spawn (0, 64, 0)
when the dimension is first created. A pre-built return portal frame is
included.

## Testing Strategy

### Local Testing

Run the Minecraft server with `./gradlew runServer` and multiple clients with
`./gradlew runClient`. Test:

1. First portal entry creates dimension
2. Dimension persists after server restart (`stop` command, then `./gradlew runServer`)
3. Return portal works correctly
4. Invitation system functions

### World Reset Testing

1. Stop server
2. Delete `world/region/`, `world/DIM-1/`, `world/DIM1/`
3. Start server
4. Verify personal dimensions still exist and are accessible

### Unit Tests

Run with `./gradlew test`. Tests cover:

- Data record serialization (NBT round-trips)
- Concurrent portal guard logic
- Portal frame detection
- Data validation and sanitization

## Minecraft Version Notes

The mod targets **Minecraft 1.20.4** for compatibility with existing modded
servers. This version uses:

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

## Releasing

Releases are automated via GitHub Actions when a version tag is pushed:

```bash
# Update mod_version in gradle.properties
# Commit changes
git tag v0.2.0
git push origin main --tags
```

This triggers `.github/workflows/release.yml` which:

1. Builds the mod
2. Runs tests
3. Creates a GitHub Release with the JAR and sources attached
4. Auto-generates release notes from commits

## Reference Documentation

- **Fantasy library:** https://github.com/NucleoidMC/fantasy
- **Fabric Wiki — Dimensions:** https://fabricmc.net/wiki/tutorial:dimension
- **Fabric Wiki — Custom Portals:** https://fabricmc.net/wiki/tutorial:portal
- **Fabric API Javadoc:** https://maven.fabricmc.net/docs/fabric-api-latest/

## Implementation Status

**Status:** Feature-complete (v0.2.0)

All planned phases have been implemented:

1. ✅ Core Infrastructure (DimensionRegistry, DimensionManager)
2. ✅ Portal System (blocks, detection, activation, teleportation)
3. ✅ Player Data (return positions)
4. ✅ Invitations (command-based)
5. ✅ Polish (config, starter platform, void generator, admin commands)
6. ✅ Hardening (edge cases, crash recovery, performance monitoring)
7. ✅ Localization (language file support)
8. ✅ Unit test suite
