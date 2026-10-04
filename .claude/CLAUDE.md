# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Project Overview

**Pocket Islands** — A Fabric, Forge and NeoForge mod for Minecraft (Fabric 1.20.1, 1.20.4, 1.21.11; Forge 1.20.1; NeoForge 1.21.11) that provides each player
with their own isolated, persistent pocket dimension island. The primary use
case is dimension survival through world resets: when the overworld/nether/end
are deleted and regenerated, each player's pocket island remains intact.

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) for multi-version and
multi-loader support from a single codebase. Each Stonecutter node is `<mc>-<loader>`.

## Build Commands

```bash
# Build ALL nodes at once (RECOMMENDED)
./gradlew chiseledBuild

# IMPORTANT: Do NOT use `./gradlew build` directly - it fails with Stonecutter.
# chiseledBuild/chiseledTest are aggregate tasks (stonecutter.gradle.kts) that run
# build/test for every node; non-active nodes compile from generated sources.

# Build or run one node
./gradlew :1.20.1-forge:build
./gradlew :1.20.1-forge:runServer

# Switch active node (nodes: 1.20.1-fabric, 1.20.1-forge, 1.20.4-fabric, 1.21.11-fabric, 1.21.11-neoforge)
./gradlew "Set active project to 1.21.11-fabric"

# Switch back to the committed node (1.20.1-fabric)
./gradlew "Reset active project"

# Clean build artifacts
./gradlew clean

# Run Minecraft client with the mod loaded (uses active node)
./gradlew runClient

# Run Minecraft server with the mod loaded
./gradlew runServer

# Run tests for all nodes
./gradlew chiseledTest

# Run a single test class
./gradlew test --tests "com.wickedsik.personalworlds.portal.PortalFrameTest"

# Run a single test method
./gradlew test --tests "com.wickedsik.personalworlds.portal.PortalFrameTest.testFrameDetection"

# Generate Minecraft sources for IDE navigation
./gradlew genSources
```

**Output locations (after chiseledBuild):** `versions/<node>/build/libs/pocketislands-<version>.jar`
for every node (e.g. `versions/1.20.1-forge/build/libs/`). On Forge this is the reobfuscated (SRG)
jar; the dev jar is in `build/devlibs/` and only runs in a dev environment.

## Project Structure

One source tree serves every loader. Fabric nodes use split environment source sets:

- **`src/main/java/`** — Server-side and common code
- **`src/client/java/`** — Client-side only code
- **`src/main/resources/`** — Server/common resources (fabric.mod.json, mixins, lang files)
- **`src/client/resources/`** — Client-only resources (textures, client mixins)

### Key Configuration Files

- **`settings.gradle.kts`** — Stonecutter nodes (`match(<mc>, <loaders>…)` → `<mc>-<loader>` with `build.<loader>.gradle.kts`)
- **`stonecutter.gradle.kts`** — Chiseled tasks, active node, loader constants (`//? if forge`) and the 1.21.11 class renames
- **`build.fabric.gradle.kts`** — Fabric nodes: Fabric Loom, Fantasy, Modrinth
- **`build.forge.gradle.kts`** — Forge nodes: ModDevGradle `legacyforge`, Infiniverse + MixinExtras (jarJar), mixin refmap, Modrinth
- **`build.neoforge.gradle.kts`** — NeoForge nodes: ModDevGradle `moddev`, Infiniverse (jarJar), Modrinth. NeoForge ships MixinExtras and runs with Mojang names, so no refmap
- **`buildSrc/`** — `moddev-mutex` plugin: one ModDevGradle `createMinecraftArtifacts` at a time (parallel runs filled the disk)
- **`gradle.properties`** — Shared properties (mod version, loom version)
- **`versions/<node>/gradle.properties`** — Node-specific dependencies
- **`src/main/resources/fabric.mod.json`** — Fabric metadata, entrypoint (`platform.fabric.FabricEntrypoint`)
- **`src/main/resources/META-INF/mods.toml`** — Forge metadata (expanded by `build.forge.gradle.kts`); entrypoint is the `@Mod` class `platform.forge.ForgeEntrypoint`
- **`src/main/resources/META-INF/neoforge.mods.toml`** — NeoForge 1.21.11 metadata (expanded by `build.neoforge.gradle.kts`), including the `[[mixins]]` entry; entrypoint `platform.neoforge.NeoForgeEntrypoint`
- **`src/main/resources/pocketislands.mixins.json`** — Mixin config (per-dimension game rules). Forge's copy gets a `refmap` key at build time; Forge loads it through the `MixinConfigs` manifest attribute

### Package Structure

Under `src/main/java/com/wickedsik/personalworlds/`:

- **`compat/`** — Version-specific API abstraction layer (ResourceLocation, Nbt, PortalInfo, Registry, etc.)
- **`platform/`** — Loader-neutral interfaces (`Platform`, events, registration, runtime dimensions, teleport, permissions)
- **`platform/fabric/`** — Fabric implementations and entrypoint (Fabric API, Fantasy, fabric-permissions-api)
- **`platform/forge/`** — Forge implementations and entrypoint (Forge events, `DeferredRegister`, Infiniverse, PermissionAPI)
- **`platform/neoforge/`** — NeoForge implementations and entrypoint (same shape as `platform/forge/`; 1.21.11 APIs only until 1.20.4-neoforge exists)
- **`mixin/`** — Vanilla mixins shared by all loaders (per-dimension game rules)
- **`dimension/`** — Dimension creation, registry, lifecycle management (through `RuntimeDimensions`)
- **`portal/`** — Portal block, frame detection, activation, teleportation
- **`player/`** — Player data, invitations, return positions (SavedData)
- **`config/`** — Configuration options
- **`registry/`** — Block/item registration
- **`event/`** — Server lifecycle, player events
- **`command/`** — Admin and player commands (`/pi`)

### Platform Layer

Only `platform/<loader>/` may import loader classes (`net.fabricmc.*`, `xyz.nucleoid.fantasy.*`,
`me.lucko.*`, `net.minecraftforge.*`, `net.neoforged.*`, `commoble.*`, `net.commoble.*`). Everything else calls `Platform.get()`. The loader entrypoint installs its
implementation with `Platform.install(...)` and then calls `PersonalWorldsMod.init()`.
Each loader's buildscript excludes the other loaders' `platform/` packages.

Check with:
```bash
grep -rlE "net\.fabricmc|xyz\.nucleoid|me\.lucko|net\.minecraftforge|net\.neoforged|commoble" src/main/java | grep -v /platform/   # must print nothing
```

Registered objects (e.g. `ModBlocks.PERSONAL_PORTAL`) are `Supplier`s: Forge/NeoForge register
after mod construction, so never build or read them in static initializers.

### Dependencies

| Node           | Java | Parchment  | Fabric Loader | Fabric API      | Fantasy         |
|----------------|------|------------|---------------|-----------------|-----------------|
| 1.20.1-fabric  | 17   | 2023.09.03 | 0.16.10       | 0.92.6+1.20.1   | 0.4.11+1.20-rc1 |
| 1.20.4-fabric  | 17   | 2024.04.14 | 0.15.11       | 0.97.0+1.20.4   | 0.5.0+1.20.4    |
| 1.21.11-fabric | 21   | 2025.12.20 | 0.18.4        | 0.141.1+1.21.11 | 0.7.0+1.21.11   |

| Node         | Java | Parchment  | Forge  | ModDevGradle         | Infiniverse | MixinExtras |
|--------------|------|------------|--------|----------------------|-------------|-------------|
| 1.20.1-forge | 17   | 2023.09.03 | 47.4.10 | 2.0.148 (`legacyforge`) | 1.0.0.5     | 0.5.5       |

| Node             | Java | Parchment  | NeoForge | ModDevGradle       | Infiniverse | MixinExtras          |
|------------------|------|------------|----------|--------------------|-------------|----------------------|
| 1.21.11-neoforge | 21   | 2025.12.20 | 21.11.45 | 2.0.148 (`moddev`) | 21.11.1     | 0.5.3 (in NeoForge)  |

ModDevGradle applies Parchment only when it recompiles Minecraft; with `CI=true` it skips
recompilation, so CI builds compile without Parchment names (the build still works).

Mappings are Mojang's official mappings layered with Parchment (parameter names
and Javadoc). The `parchment_version` property lives in `versions/<mc>/gradle.properties`.

- **Fantasy** (Fabric) / **Infiniverse** (Forge, NeoForge) — Runtime dimension creation, bundled in the jar
- **MixinExtras** — Ships with Fabric Loader and NeoForge; bundled with jarJar on Forge 47
- **Fabric Permissions API** / **Forge and NeoForge PermissionAPI** — LuckPerms integration, OP-level fallback

## Multi-Version Support (Stonecutter)

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) 0.9.x for multi-version
management from a single codebase.

### Nodes

| Node           | Status    | Active      |
|----------------|-----------|-------------|
| 1.20.1-fabric  | Supported | ✓ (commit with this active) |
| 1.20.1-forge   | Supported |             |
| 1.20.4-fabric  | Supported |             |
| 1.21.11-fabric | Supported |             |
| 1.21.11-neoforge | Supported |           |

Always switch back to 1.20.1-fabric before committing; `./gradlew "Reset active project"`
does this (it switches to `vcsVersion` = 1.20.1-fabric in `settings.gradle.kts`).

Version predicates (`//? if >=1.21`) compare the node's MC version. Loader code lives in
`platform/<loader>/` packages that each buildscript excludes for the other loaders, so
`//? if forge` constants exist but common code should not need them.

### Versioned Comment Syntax

Stonecutter uses special comments for conditional compilation:

```java
//? if >=1.20.2 {
// Code for MC 1.20.2 and newer (uses SavedData.Factory)
//?}

//? if >=1.20.2 {
return stateManager.computeIfAbsent(TYPE, DATA_NAME);
//?} else {
/*return stateManager.computeIfAbsent(T::fromNbt, T::new, DATA_NAME);*/
//?}
```

**Important:** The `else` branch code must be commented out (`/* */`) when the
active version is >= 1.20.2.

### Version-Specific Code

**1.20.1 vs 1.20.4** (handled by Stonecutter conditionals):
- `DimensionDataStorage.computeIfAbsent()` signature changed in 1.20.2

**Class renames in 1.21.11** (handled by Stonecutter replacements in `stonecutter.gradle.kts`, for every 1.21.11 node):
Mojang renamed `ResourceLocation` → `Identifier`, `PortalInfo` → `TeleportTransition` and
`ResourceLocationException` → `IdentifierException`. Sources always use the pre-1.21.11
names, in code and comments; Stonecutter rewrites them (word-bounded regex) when
switching to 1.21.11. Never write a standalone `Identifier` in sources: switching back
from 1.21.11 would turn it into `ResourceLocation` and leave a diff.

**1.21.x Major API Changes** (handled by Compat package):
The 1.21.x series introduced significant API changes. Rather than adding Stonecutter
conditionals throughout the codebase, all version-specific differences are abstracted
in `src/main/java/com/wickedsik/personalworlds/compat/`:

- **IdentifierCompat.java** — `new ResourceLocation()` → `ResourceLocation.fromNamespaceAndPath()`; `ResourceKey.location()` → `identifier()`
- **NbtCompat.java** — Optional return types, UUID handling (stored as strings), new methods with defaults
- **TeleportCompat.java** — `PortalInfo` (1.20.x) → `TeleportTransition` with 6 parameters (1.21.x)
- **PersistentStateCompat.java** — 1.21.5+ uses Codec-based serialization
- **WorldCompat.java** — `getMaxBuildHeight()`/`getMinBuildHeight()` → `getMinY()` + `getHeight()`
- **EntityCompat.java** — Entity-related API updates
- **CommandCompat.java** — Command registration and feedback changes
- **GameRulesCompat.java** — Builds a pocket dimension's rule set (overworld copy + config overrides)
- **BlockSettingsCompat.java** — Block settings and registration updates
- **RegistryCompat.java** — `Registry.get(id)` → `Registry.getValue(id)`

This abstraction layer allows the core business logic to remain version-agnostic while
containing all version-specific implementation details.

### Adding a New Version

1. Add the node to `settings.gradle.kts`:
   ```kotlin
   match("1.20.1", "fabric", "forge")
   ```
2. Create `versions/<mc>-<loader>/gradle.properties` with dependencies
3. Run `./gradlew chiseledBuild` to generate the new node subproject
4. Check for API differences requiring new Stonecutter conditionals or Compat updates
5. Test with `./gradlew :<mc>-<loader>:runClient`

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

A `DimensionRegistry` (SavedData saved to `world/data/personalworlds_registry.dat`)
tracks all player dimensions for restoration on server start.

### Runtime Dimensions (Fantasy on Fabric, Infiniverse on Forge and NeoForge)

`DimensionManager` opens dimensions through `Platform.get().dimensions()` (`RuntimeDimensions`),
which returns a `RuntimeDimension` handle (`level()`, `unload()`, `delete()`). On Fabric,
`platform/fabric/FantasyDimensions` implements it with Fantasy (`xyz.nucleoid:fantasy`)
persistent worlds. Without Fantasy, Fabric API alone cannot create dimensions at runtime.

On Forge and NeoForge, `platform/<loader>/InfiniverseDimensions` uses Infiniverse (same behaviour in 1.0.0.5 and 21.11.1). `unload()` and `delete()` call
`markDimensionForUnregistration`; Infiniverse unregisters at the end of a later tick (players
inside go to their respawn point, the level is saved and dropped from the `LevelStem` registry,
so it is not recreated on the next start). It never closes the level, so `InfiniverseDimensions`
closes it once it is gone and then deletes the folder for `delete()`. Islands still loaded at
shutdown stay in `level.dat` and vanilla recreates them at the next start. Infiniverse builds
levels with vanilla `DerivedLevelData` and the overworld seed: island day time follows the
overworld, and `DimensionSpec.seed` is ignored.

Because Infiniverse levels are written to `level.dat`, the chunk generator's codec must be the
exact instance registered in `CHUNK_GENERATOR` (`VoidIslandChunkGenerator.MAP_CODEC` on 1.21.x).
A fresh `fieldOf(...)` per call fails the registry lookup, vanilla drops `WorldGenSettings` from
`level.dat`, and the world no longer loads. Fantasy worlds never reach `level.dat`, so Fabric
does not show this; the NeoForge restart harness does.

### Per-Dimension Game Rules

Vanilla gives every level the overworld's game rules. `DimensionManager` registers a rule set
in `DimensionGameRules` (keyed by dimension) **before** opening the level; mixins return it:

- 1.20.x: `LevelMixin` on `Level#getGameRules`, plus `ServerLevelMixin` wrapping the direct
  `levelData.getGameRules()` read in `ServerLevel#tickTime` (daylight cycle)
- 1.21.x: `ServerLevelMixin` on `ServerLevel#getGameRules`
- All: `ServerPlayerMixin` makes `restoreFrom` read `keepInventory` in the level the player
  died in, matching the death-drop decision (otherwise items vanish)

Fantasy's own `setGameRule` is not used. The mixins are common code; Forge loads them through
the `MixinConfigs` manifest attribute with an SRG refmap from the mixin annotation processor;
NeoForge loads them from `[[mixins]]` in `neoforge.mods.toml` (Mojang names, no refmap).
Known limits: on 1.20.x `/gamerule` always reads and
writes the overworld; on 1.21.x it acts on the pocket, but edits are lost when the pocket
reloads (rules are rebuilt from config). Client-side rules sent at login
(`doImmediateRespawn`, `reducedDebugInfo`) follow the overworld.

### Component Dependencies

```
Portal Entry
    ↓
PortalHelper.handlePortalEntry()
    ↓
DimensionManager.getOrCreatePlayerDimension()
    ↓
DimensionGameRules.register()
    ↓
Platform.get().dimensions().open()   (Fantasy on Fabric)
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
dimension in `PlayerDataManager` (SavedData). The return portal must
teleport them back to this exact location, not to spawn or a generic position.

### Portal Collision Detection

The `PersonalPortalBlock` must implement `entityInside()` to detect when
players enter the portal. Use `entity.isOnPortalCooldown()` to prevent rapid
flickering when standing in the portal.

### Invitation System

Players can invite others to visit their island via commands
(`/pi invite <player>`). Invited players can visit by entering the owner's portal.

### Void World Generation

`VoidIslandChunkGenerator` extends `ChunkGenerator` and generates the island from the portal
type's `islandLayers`. It's registered through `PlatformRegistration` (`ModChunkGenerators`)
for use in pocket dimensions.

### Starter Platform

Players arrive at (0, 65, 0). `PortalHelper.getOrCreateSpawnPlatform` builds a 5x5 starter
platform plus an unlit return portal frame only when (0, 64, 0) is air. With island layers the
generator already fills that block, so islands normally have **no** pre-built return frame and
players build their own (verified by the in-game tests, 2026-10-04).

Player data lives in `world/data/personalworlds_player_data.dat` (return positions, invitations,
pocket tracking) and `world/data/personalworlds_portal_ownership.dat`.

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
2. Delete `world/region/`, `world/entities/`, `world/poi/`, `world/DIM-1/`, `world/DIM1/`
   (`entities/` and `poi/` hold overworld mobs and points of interest since 1.17)
3. Start server
4. Verify personal dimensions still exist and are accessible

`./gradlew chiseledHarnessTest` automates this (see In-Game Tests).

### In-Game Tests

```bash
./gradlew chiseledGameTest      # GameTest server per node; fails on any failed test
./gradlew chiseledHarnessTest   # restart + world reset harness per node
./gradlew :1.20.1-forge:runGametest   # one node
```

Code lives in the `gametest` source set (`src/gametest/`), compiled against main and never in
release jars. Scenario bodies are common code; only registration is per loader:

- `gametest/*Scenarios` — portal activation and first entry, return to the stored position,
  invitations, per-dimension rules (pocket clock: Fabric frozen, Forge/NeoForge follow the overworld),
  `keepInventory` across dimensions, void ejection
- `gametest/platform/fabric/FabricGameTests` — `fabric-gametest` entrypoint of the
  `personalworlds-gametest` test mod (`src/gametest/resources/fabric.mod.json`); vanilla
  `@GameTest` on 1.20.x, Fabric's `@GameTest` on 1.21.11
- `gametest/platform/forge/ForgeGameTests` — `@GameTestHolder`, enabled with
  `-Dforge.enabledGameTestNamespaces=personalworlds`; JUnit XML via vanilla `JUnitLikeTestReporter`
- `gametest/platform/neoforge/NeoForgeGameTests` — `@EventBusSubscriber`: each scenario is a
  `Registries.TEST_FUNCTION` entry (`RegisterEvent`) plus a `FunctionGameTestInstance` from
  `RegisterGameTestsEvent`. `GameTestServer` runs every registered test (no namespace filter,
  so vanilla `minecraft:always_pass` runs too); JUnit XML as on Forge
- `gametest/harness/RestartHarness` + `buildSrc/pocketislands-harness.gradle.kts` — dedicated
  server runs `setup` → `verify` → world reset → `verify-reset` in `build/harness/`

Reports: `versions/<node>/build/gametest/junit.xml`, `versions/<node>/build/harness/harness-report.properties`.

Mock players (`MockPlayers`) join through the real player list on an embedded Netty channel.
`ClientInput` feeds their connection the packets a client sends, because portal entry only
behaves like the real game through that path:
- Portal blocks fire inside `handleMovePlayer` on 1.20.x and in the player tick on 1.21.x;
  mock connections are not ticked by the server, so `ClientInput` runs `doTick()` itself
- 1.21.4+ ignores movement until `ServerboundPlayerLoadedPacket`
- Pending teleports must be confirmed with the current id (read by reflection; dev only)

Each GameTest run deletes `build/gametest/world` first. All tests in a batch share one server:
scenarios use their own player names and `TestSupport.ensureConfigured()` pins config values.

### Unit Tests

Run with `./gradlew test`. Tests cover:

- Data record serialization (NBT round-trips)
- Concurrent portal guard logic
- Portal frame detection
- Portal color parsing, display names, and texture name generation
- Data validation and sanitization

## Minecraft Version Notes

The mod targets **Minecraft 1.20.1, 1.20.4, and 1.21.11** using Stonecutter for
multi-version support.

**Java Requirements:**
- MC 1.20.1 & 1.20.4: Java 17
- MC 1.21.11: Java 21

**Mapping Preferences:**
- All versions use Mojang mappings layered with Parchment (Yarn ended at 1.21.11; NeoForge uses Mojang names)
- Cross-dimension teleports go through `Platform.get().teleport()`: Fabric uses `FabricDimensions.teleport()` on 1.20.x and `Entity#teleport(TeleportTransition)` on 1.21.x, as does NeoForge 1.21.11 (portal blocks fire in the player tick there, not in the movement packet); Forge uses `changeDimension` with an `ITeleporter` returning the target (not `teleportTo`: portal entry runs inside the movement packet, and without `isChangingDimension` the handler sends the player back to their old coordinates)

### API Differences Between Supported Versions

**1.20.1 vs 1.20.4** (handled by Stonecutter):

- `DimensionDataStorage.computeIfAbsent()` signature changed in 1.20.2
  - 1.20.1: `computeIfAbsent(fromNbt, constructor, name)`
  - 1.20.4: `computeIfAbsent(SavedData.Factory<T>, name)`

**1.20.x vs 1.21.x** (handled by Compat package):

The 1.21.x series introduced major API changes. All differences are abstracted in the
`compat/` package to keep core code clean and version-agnostic:

- **ResourceLocation**: `new ResourceLocation(namespace, path)` → `Identifier.fromNamespaceAndPath(namespace, path)` (class renamed in 1.21.11)
- **CompoundTag**: Getters return Optional, UUID methods removed (stored as strings), new methods with defaults
- **PortalInfo** → **TeleportTransition**: Constructor signature changed from 4 to 6 parameters with world and callback
- **SavedData**: 1.21.5+ uses Codec-based serialization (`SavedDataType`) instead of a `save` override
- **Block methods**: `entityInside` added InsideBlockEffectApplier param, signature changes
- **Level methods**: `getMaxBuildHeight()`/`getMinBuildHeight()` replaced by `getMinY()` + `getHeight()`
- **Entity methods**: Various API adjustments for entity interaction
- **GameRules**: Complete API overhaul — `GameRules.Key`/`BooleanValue`/`IntegerValue`/`GameRuleTypeVisitor` (1.20.x) → standalone `GameRule<T>`/`GameRuleTypeVisitor` (1.21.x); `GameRules#copy()` takes a `FeatureFlagSet` and values are set with `set(GameRule<T>, T, server)`; game rule names changed from camelCase to snake_case with many renames (e.g., `doMobSpawning` → `spawn_mobs`)

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

1. Builds **all nodes** using `chiseledBuild`
2. Runs tests for all nodes
3. Creates a GitHub Release with JARs for all nodes attached
4. Auto-generates release notes from commits
5. Publishes to **Modrinth** automatically (one version per node)

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

- **GitHub:** `pocketislands-<version>+<node>.jar` (e.g., `pocketislands-0.5.1+1.20.1-forge.jar`)
- **Modrinth:** One version per node: Fabric as `<version>+<mc>` (`0.5.1+1.20.1`), Forge as `<version>+<mc>-forge` (`0.5.1+1.20.1-forge`), NeoForge as `<version>+<mc>-neoforge` (`0.5.1+1.21.11-neoforge`)

### Distribution

| Platform        | URL                                                     |
|-----------------|---------------------------------------------------------|
| GitHub Releases | https://github.com/WickedCoding/mc-pocket-islands/releases |
| Modrinth        | https://modrinth.com/mod/pocket-islands                 |

## Key External Dependencies

- **Fantasy** (`xyz.nucleoid:fantasy`) — Runtime dimension creation on Fabric. Without this, Fabric API alone cannot create dimensions at runtime. See https://github.com/NucleoidMC/fantasy
- **Infiniverse** (`commoble.infiniverse:infiniverse-1.20.1` on Forge, `net.commoble.infiniverse:infiniverse` on NeoForge 1.21.11, maven.commoble.net) — Runtime dimension creation on Forge and NeoForge. See https://github.com/Commoble/infiniverse
- **Fabric Permissions API** — Optional soft dependency for LuckPerms integration; falls back to vanilla OP levels
