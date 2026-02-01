# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Project Overview

**Pocket Islands** — A Fabric mod for Minecraft (1.20.1, 1.20.4, 1.21.11) that provides each player
with their own isolated, persistent pocket dimension island. The primary use
case is dimension survival through world resets: when the overworld/nether/end
are deleted and regenerated, each player's pocket island remains intact.

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) for multi-version
support from a single codebase.

## Build Commands

```bash
# Build ALL versions at once (RECOMMENDED)
./gradlew chiseledBuild

# IMPORTANT: Do NOT use `./gradlew build` directly - it fails with Stonecutter.
# The chiseledBuild task properly coordinates version switching and source processing.

# Switch active version to 1.20.1
./gradlew "Set active project to 1.20.1"

# Switch active version to 1.20.4
./gradlew "Set active project to 1.20.4"

# Switch active version to 1.21.11
./gradlew "Set active project to 1.21.11"

# Clean build artifacts
./gradlew clean

# Run Minecraft client with the mod loaded (uses active version)
./gradlew runClient

# Run Minecraft server with the mod loaded
./gradlew runServer

# Run tests for all versions
./gradlew chiseledTest

# Run a single test class
./gradlew test --tests "com.wickedsik.personalworlds.portal.PortalFrameTest"

# Run a single test method
./gradlew test --tests "com.wickedsik.personalworlds.portal.PortalFrameTest.testFrameDetection"

# Generate Minecraft sources for IDE navigation
./gradlew genSources
```

**Output locations (after chiseledBuild):**
- MC 1.20.1: `versions/1.20.1/build/libs/personalworlds-<version>.jar`
- MC 1.20.4: `versions/1.20.4/build/libs/personalworlds-<version>.jar`
- MC 1.21.11: `versions/1.21.11/build/libs/personalworlds-<version>.jar`

## Project Structure

This is a standard Fabric mod project with split environment source sets:

- **`src/main/java/`** — Server-side and common code
- **`src/client/java/`** — Client-side only code
- **`src/main/resources/`** — Server/common resources (fabric.mod.json, mixins, lang files)
- **`src/client/resources/`** — Client-only resources (textures, client mixins)

### Key Configuration Files

- **`settings.gradle.kts`** — Stonecutter multi-version configuration
- **`stonecutter.gradle.kts`** — Chiseled tasks and active version selection
- **`build.gradle.kts`** — Dependencies, build configuration, uses Fabric Loom
- **`gradle.properties`** — Shared properties (mod version, loom version)
- **`versions/<mc-version>/gradle.properties`** — Version-specific dependencies
- **`src/main/resources/fabric.mod.json`** — Mod metadata, entrypoints, dependencies

### Package Structure

Under `src/main/java/com/wickedsik/personalworlds/`:

- **`compat/`** — Version-specific API abstraction layer (Identifier, Nbt, TeleportTarget, etc.)
- **`dimension/`** — Dimension creation, registry, lifecycle management (uses Fantasy)
- **`portal/`** — Portal block, frame detection, activation, teleportation
- **`player/`** — Player data, invitations, return positions (PersistentState)
- **`config/`** — Configuration options
- **`registry/`** — Block/item registration
- **`event/`** — Server lifecycle, player events
- **`command/`** — Admin and player commands (`/pi`)

### Dependencies

| MC Version | Java | Yarn Mappings | Fabric Loader | Fabric API    | Fantasy         |
|------------|------|---------------|---------------|---------------|-----------------|
| 1.20.1     | 17   | 1.20.1+build.9| 0.15.0+       | 0.92.6+1.20.1 | 0.4.11+1.20-rc1 |
| 1.20.4     | 17   | 1.20.4+build.2| 0.15.0+       | 0.97.0+1.20.4 | 0.5.0+1.20.4    |
| 1.21.11    | 21   | 1.21.11+build.4| 0.18.4+      | 0.141.1+1.21.11 | 0.7.0+1.21.11 |

- **Fantasy** — Required for runtime dimension creation (version varies by MC version)
- **Fabric Permissions API** — Optional soft dependency for LuckPerms integration

## Multi-Version Support (Stonecutter)

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) for multi-version
management from a single codebase.

### Supported Versions

| MC Version | Status    | Active      |
|------------|-----------|-------------|
| 1.20.1     | Supported |             |
| 1.20.4     | Supported |             |
| 1.21.11    | Supported | ✓ (default) |

### Versioned Comment Syntax

Stonecutter uses special comments for conditional compilation:

```java
//? if >=1.20.2 {
// Code for MC 1.20.2 and newer (uses PersistentState.Type)
//?}

//? if >=1.20.2 {
return stateManager.getOrCreate(TYPE, DATA_NAME);
//?} else {
/*return stateManager.getOrCreate(T::fromNbt, T::new, DATA_NAME);*/
//?}
```

**Important:** The `else` branch code must be commented out (`/* */`) when the
active version is >= 1.20.2.

### Version-Specific Code

**1.20.1 vs 1.20.4** (handled by Stonecutter conditionals):
- `PersistentState.getOrCreate()` signature changed in 1.20.2

**1.21.x Major API Changes** (handled by Compat package):
The 1.21.x series introduced significant API changes. Rather than adding Stonecutter
conditionals throughout the codebase, all version-specific differences are abstracted
in `src/main/java/com/wickedsik/personalworlds/compat/`:

- **IdentifierCompat.java** — `new Identifier()` → `Identifier.of()`
- **NbtCompat.java** — Optional return types, UUID handling (stored as strings), new methods with defaults
- **TeleportCompat.java** — `TeleportTarget` constructor changed (4 → 6 parameters)
- **PersistentStateCompat.java** — 1.21.5+ uses Codec-based serialization
- **WorldCompat.java** — `getTopY()` signature changes
- **EntityCompat.java** — Entity-related API updates
- **CommandCompat.java** — Command registration and feedback changes
- **GameRulesCompat.java** — Game rules API adjustments
- **BlockSettingsCompat.java** — Block settings and registration updates

This abstraction layer allows the core business logic to remain version-agnostic while
containing all version-specific implementation details.

### Adding a New Version

1. Add version to `settings.gradle.kts`:
   ```kotlin
   versions("1.20.1", "1.20.4", "1.21.11")
   ```
2. Create `versions/<new-version>/gradle.properties` with dependencies
3. Run `./gradlew chiseledBuild` to generate the new version subproject
4. Check for API differences requiring new Stonecutter conditionals or Compat updates
5. Test with `./gradlew "Set active project to <version>"` + `./gradlew runClient`

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
(`/pi invite <player>`). Invited players can visit by entering the owner's portal.

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

The mod targets **Minecraft 1.20.1, 1.20.4, and 1.21.11** using Stonecutter for
multi-version support.

**Java Requirements:**
- MC 1.20.1 & 1.20.4: Java 17
- MC 1.21.11: Java 21

**Mapping Preferences:**
- All versions use Yarn mappings (not Mojang mappings)
- All versions use `FabricDimensions.teleport()` for cross-dimension teleportation

### API Differences Between Supported Versions

**1.20.1 vs 1.20.4** (handled by Stonecutter):

- `PersistentState.getOrCreate()` signature changed in 1.20.2
  - 1.20.1: `getOrCreate(fromNbt, constructor, name)`
  - 1.20.4: `getOrCreate(Type<T>, name)`

**1.20.x vs 1.21.x** (handled by Compat package):

The 1.21.x series introduced major API changes. All differences are abstracted in the
`compat/` package to keep core code clean and version-agnostic:

- **Identifier**: `new Identifier(namespace, path)` → `Identifier.of(namespace, path)`
- **NbtCompound**: Getters return Optional, UUID methods removed (stored as strings), new methods with defaults
- **TeleportTarget**: Constructor signature changed from 4 to 6 parameters with world and callback
- **PersistentState**: 1.21.5+ uses Codec-based serialization instead of writeNbt override
- **Block methods**: `onEntityCollision` added EntityCollisionHandler param, signature changes
- **World methods**: `getTopY()` signature changed
- **Entity methods**: Various API adjustments for entity interaction

### Future Version Support

When adding support for newer 1.21.x or 1.22+ versions:

1. Add version to `settings.gradle.kts`
2. Create `versions/<new-version>/gradle.properties` with dependencies
3. Update Compat classes for any additional API changes
4. Add Stonecutter conditionals only for subtle breaking changes between 1.20.x versions
5. Test thoroughly with `./gradlew chiseledBuild` and manual client testing

## Releasing

Releases are automated via GitHub Actions when a version tag is pushed:

```bash
# Update mod_version in gradle.properties
# Move CHANGELOG.md [Unreleased] to new version section
# Commit changes
git tag v0.5.1
git push origin main --tags
```

This triggers `.github/workflows/release.yml` which:

1. Builds **all versions** using `chiseledBuild`
2. Runs tests for all versions
3. Creates a GitHub Release with JARs for all MC versions attached
4. Auto-generates release notes from commits
5. Publishes to **Modrinth** automatically (one version per MC version)

### Release Command

Use the `/release` command to automate the release preparation:

```bash
/release           # patch release (0.5.0 → 0.5.1)
/release patch     # patch release (0.5.0 → 0.5.1)
/release minor     # minor release (0.5.0 → 0.6.0)
/release major     # major release (0.5.0 → 1.0.0)
```

The command performs steps 1-3 automatically:
1. Updates `mod_version` in `gradle.properties`
2. Moves `[Unreleased]` content in `CHANGELOG.md` to new version section with date
3. Commits both files: `Chore: Prepare release X.Y.Z`
4. Creates git tag: `vX.Y.Z`

After the command completes, push to trigger the release workflow:
```bash
git push origin main --tags
```

### Release Artifacts

- **GitHub:** `personalworlds-<version>+<mc-version>.jar` (e.g., `personalworlds-0.5.1+1.20.4.jar`)
- **Modrinth:** Three versions published automatically (`0.5.1+1.20.1`, `0.5.1+1.20.4`, `0.5.1+1.21.11`)

### Distribution

| Platform        | URL                                                     |
|-----------------|---------------------------------------------------------|
| GitHub Releases | https://github.com/WickedSik/mc-pocket-islands/releases |
| Modrinth        | https://modrinth.com/mod/pocket-islands                 |

## Key External Dependencies

- **Fantasy** (`xyz.nucleoid:fantasy`) — Runtime dimension creation. Without this, Fabric API alone cannot create dimensions at runtime. See https://github.com/NucleoidMC/fantasy
- **Fabric Permissions API** — Optional soft dependency for LuckPerms integration; falls back to vanilla OP levels
