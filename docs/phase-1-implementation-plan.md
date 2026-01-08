# Phase 1: Core Infrastructure — Implementation Plan

## Overview

This phase establishes the foundational dimension management system for PersonalWorlds. Upon completion, we will have:

- Fantasy library integrated
- A persistent registry tracking all player dimensions
- A dimension manager capable of creating and loading player dimensions
- A test command to verify the system works

**Estimated Tasks:** 15 discrete implementation steps

---

## Pre-Implementation Checklist

Before we begin, confirm:

- [ ] Java 17 JDK installed
- [ ] Gradle wrapper functional (`./gradlew --version`)
- [ ] IDE configured for Fabric development (optional but recommended)

---

## Step 1: Update Project Metadata

### 1.1 Update `gradle.properties`

Replace the entire file with values for Minecraft 1.20.4:

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true

# Fabric Properties
# check these on https://fabricmc.net/develop
minecraft_version=1.20.4
yarn_mappings=1.20.4+build.3
loader_version=0.15.11
loom_version=1.9-SNAPSHOT

# Mod Properties
mod_version=0.1.0
maven_group=com.wickedsik
archives_base_name=personalworlds

# Dependencies
fabric_version=0.97.0+1.20.4
fantasy_version=0.5.0+1.20.4
```

### 1.2 Update `fabric.mod.json`

Update mod metadata:

```json
{
  "schemaVersion": 1,
  "id": "personalworlds",
  "version": "${version}",
  "name": "Personal Worlds",
  "description": "Per-player persistent dimensions that survive world resets",
  "authors": ["Jurriën"],
  "license": "MIT",
  "icon": "assets/personalworlds/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.wickedsik.personalworlds.PersonalWorldsMod"]
  },
  "mixins": ["personalworlds.mixins.json"],
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": "~1.20.4",
    "java": ">=17",
    "fabric-api": "*"
  }
}
```

---

## Step 2: Add Fantasy Dependency

### 2.1 Update `build.gradle`

Replace the entire `build.gradle` with 1.20.4-compatible configuration:

```groovy
plugins {
    id 'fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    maven {
        name = 'Nucleoid'
        url = 'https://maven.nucleoid.xyz/'
    }
}

loom {
    splitEnvironmentSourceSets()

    mods {
        "personalworlds" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    // Minecraft and mappings
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"

    // Fabric API
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    // Fantasy — runtime dimension creation (included in JAR)
    modImplementation include("xyz.nucleoid:fantasy:${project.fantasy_version}")
}

processResources {
    inputs.property "version", project.version

    filesMatching("fabric.mod.json") {
        expand "version": inputs.properties.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 17
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

jar {
    inputs.property "archivesName", project.base.archivesName

    from("LICENSE") {
        rename { "${it}_${inputs.properties.archivesName}" }
    }
}

publishing {
    publications {
        create("mavenJava", MavenPublication) {
            artifactId = project.archives_base_name
            from components.java
        }
    }
}
```

**Key changes from template:**
- Uses Yarn mappings instead of Mojang mappings (better for 1.20.4 compatibility)
- Java 17 target (1.20.4 requirement)
- Fantasy dependency with `include()` to bundle it in the JAR
- Nucleoid Maven repository for Fantasy

### 2.2 Verify Build

```bash
./gradlew clean build
```

Expected: Build succeeds, Fantasy is resolved from Nucleoid Maven.

---

## Step 3: Create Package Structure

Reorganize from template structure to PersonalWorlds structure:

```
src/main/java/com/wickedsik/personalworlds/
├── PersonalWorldsMod.java              # Mod entrypoint
├── dimension/
│   ├── DimensionManager.java           # Create/load/unload dimensions
│   ├── DimensionRegistry.java          # PersistentState tracking all dims
│   ├── PlayerDimensionData.java        # Per-player dimension metadata
│   └── WorldGenType.java               # Enum for generator types
├── command/
│   └── TestCommands.java               # Temporary test commands
└── event/
    └── ModEventHandlers.java           # Server lifecycle events
```

**Delete template files:**
- `src/main/java/com/example/` (entire directory)
- `src/client/java/com/example/` (entire directory)
- `src/main/java/com/example/mixin/ExampleMixin.java`
- `src/client/java/com/example/mixin/client/ExampleClientMixin.java`

**Note:** We'll keep client source sets minimal for Phase 1 (no client code needed).

---

## Step 4: Create Mod Entrypoint

### 4.1 `PersonalWorldsMod.java`

```java
package com.wickedsik.personalworlds;

import com.wickedsik.personalworlds.command.TestCommands;
import com.wickedsik.personalworlds.event.ModEventHandlers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonalWorldsMod implements ModInitializer {
    public static final String MOD_ID = "personalworlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Personal Worlds initializing...");

        // Register event handlers
        ModEventHandlers.register();

        // Register test commands (temporary for Phase 1)
        TestCommands.register();

        LOGGER.info("Personal Worlds initialized!");
    }
}
```

---

## Step 5: Create WorldGenType Enum

### 5.1 `dimension/WorldGenType.java`

```java
package com.wickedsik.personalworlds.dimension;

public enum WorldGenType {
    VOID,       // Empty void, starter platform only
    OVERWORLD,  // Full overworld generation
    FLAT;       // Superflat

    public static WorldGenType fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return VOID; // Default
        }
    }
}
```

---

## Step 6: Create PlayerDimensionData

### 6.1 `dimension/PlayerDimensionData.java`

This record holds metadata for each player's dimension:

```java
package com.wickedsik.personalworlds.dimension;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public record PlayerDimensionData(
    UUID ownerUuid,
    String ownerName,
    Identifier dimensionId,
    long createdAt,
    BlockPos spawnPoint,
    WorldGenType generatorType
) {

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putString("DimensionId", dimensionId.toString());
        nbt.putLong("CreatedAt", createdAt);
        nbt.putInt("SpawnX", spawnPoint.getX());
        nbt.putInt("SpawnY", spawnPoint.getY());
        nbt.putInt("SpawnZ", spawnPoint.getZ());
        nbt.putString("GeneratorType", generatorType.name());
        return nbt;
    }

    public static PlayerDimensionData fromNbt(NbtCompound nbt) {
        return new PlayerDimensionData(
            nbt.getUuid("OwnerUuid"),
            nbt.getString("OwnerName"),
            new Identifier(nbt.getString("DimensionId")),
            nbt.getLong("CreatedAt"),
            new BlockPos(
                nbt.getInt("SpawnX"),
                nbt.getInt("SpawnY"),
                nbt.getInt("SpawnZ")
            ),
            WorldGenType.fromString(nbt.getString("GeneratorType"))
        );
    }
}
```

---

## Step 7: Create DimensionRegistry (PersistentState)

### 7.1 `dimension/DimensionRegistry.java`

This is the core persistent storage for dimension metadata:

```java
package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DimensionRegistry extends PersistentState {

    private static final String DATA_NAME = PersonalWorldsMod.MOD_ID + "_registry";

    private final Map<UUID, PlayerDimensionData> dimensions = new HashMap<>();

    public DimensionRegistry() {
        // Default constructor for new registries
    }

    // --- Dimension Registration ---

    public void registerDimension(PlayerDimensionData data) {
        dimensions.put(data.ownerUuid(), data);
        markDirty();
        PersonalWorldsMod.LOGGER.info("Registered dimension for player: {} ({})",
            data.ownerName(), data.ownerUuid());
    }

    public boolean hasDimension(UUID playerUuid) {
        return dimensions.containsKey(playerUuid);
    }

    public Optional<PlayerDimensionData> getDimensionData(UUID playerUuid) {
        return Optional.ofNullable(dimensions.get(playerUuid));
    }

    public Map<UUID, PlayerDimensionData> getAllDimensions() {
        return Map.copyOf(dimensions);
    }

    public void removeDimension(UUID playerUuid) {
        if (dimensions.remove(playerUuid) != null) {
            markDirty();
            PersonalWorldsMod.LOGGER.info("Removed dimension for player: {}", playerUuid);
        }
    }

    // --- Restoration on Server Start ---

    public void restoreAllDimensions(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Restoring {} player dimensions...", dimensions.size());
        for (PlayerDimensionData data : dimensions.values()) {
            try {
                DimensionManager.loadExistingDimension(server, data);
            } catch (Exception e) {
                PersonalWorldsMod.LOGGER.error("Failed to restore dimension for {}: {}",
                    data.ownerName(), e.getMessage());
            }
        }
        PersonalWorldsMod.LOGGER.info("Dimension restoration complete!");
    }

    // --- Serialization ---

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList dimensionList = new NbtList();
        for (PlayerDimensionData data : dimensions.values()) {
            dimensionList.add(data.toNbt());
        }
        nbt.put("Dimensions", dimensionList);
        return nbt;
    }

    public static DimensionRegistry fromNbt(NbtCompound nbt) {
        DimensionRegistry registry = new DimensionRegistry();
        NbtList dimensionList = nbt.getList("Dimensions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < dimensionList.size(); i++) {
            PlayerDimensionData data = PlayerDimensionData.fromNbt(dimensionList.getCompound(i));
            registry.dimensions.put(data.ownerUuid(), data);
        }
        PersonalWorldsMod.LOGGER.info("Loaded {} dimensions from registry", registry.dimensions.size());
        return registry;
    }

    // --- Static Access ---

    public static DimensionRegistry get(MinecraftServer server) {
        PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
        return stateManager.getOrCreate(
            DimensionRegistry::fromNbt,
            DimensionRegistry::new,
            DATA_NAME
        );
    }
}
```

---

## Step 8: Create DimensionManager

### 8.1 `dimension/DimensionManager.java`

This manages Fantasy interactions for dimension creation/loading:

```java
package com.wickedsik.personalworlds.dimension;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DimensionManager {

    private static final Map<UUID, RuntimeWorldHandle> activeHandles = new HashMap<>();

    /**
     * Get or create a player's personal dimension.
     * If the dimension doesn't exist, creates it and registers it.
     */
    public static ServerWorld getOrCreatePlayerDimension(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            WorldGenType genType
    ) {
        Fantasy fantasy = Fantasy.get(server);
        Identifier dimId = createDimensionId(playerUuid);

        // Check if already loaded
        if (activeHandles.containsKey(playerUuid)) {
            return activeHandles.get(playerUuid).asWorld();
        }

        // Create world config
        RuntimeWorldConfig config = createWorldConfig(server, genType, playerUuid);

        // Get or create the persistent world
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(dimId, config);
        activeHandles.put(playerUuid, handle);

        PersonalWorldsMod.LOGGER.info("Loaded/created dimension for player: {} ({})",
            playerName, playerUuid);

        // Register in persistent state if new
        DimensionRegistry registry = DimensionRegistry.get(server);
        if (!registry.hasDimension(playerUuid)) {
            PlayerDimensionData data = new PlayerDimensionData(
                playerUuid,
                playerName,
                dimId,
                System.currentTimeMillis(),
                new BlockPos(0, 64, 0), // Default spawn
                genType
            );
            registry.registerDimension(data);
        }

        return handle.asWorld();
    }

    /**
     * Load an existing dimension from registry data.
     * Called during server startup to restore dimensions.
     */
    public static void loadExistingDimension(MinecraftServer server, PlayerDimensionData data) {
        Fantasy fantasy = Fantasy.get(server);

        // Skip if already loaded
        if (activeHandles.containsKey(data.ownerUuid())) {
            return;
        }

        RuntimeWorldConfig config = createWorldConfig(server, data.generatorType(), data.ownerUuid());
        RuntimeWorldHandle handle = fantasy.getOrOpenPersistentWorld(data.dimensionId(), config);
        activeHandles.put(data.ownerUuid(), handle);

        PersonalWorldsMod.LOGGER.debug("Restored dimension: {}", data.dimensionId());
    }

    /**
     * Unload a dimension if it's empty.
     * Returns true if the dimension was unloaded.
     */
    public static boolean unloadIfEmpty(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        if (handle != null && handle.asWorld().getPlayers().isEmpty()) {
            handle.unload();
            activeHandles.remove(playerUuid);
            PersonalWorldsMod.LOGGER.info("Unloaded empty dimension for player: {}", playerUuid);
            return true;
        }
        return false;
    }

    /**
     * Unload all empty dimensions. Called periodically.
     */
    public static void unloadEmptyDimensions() {
        activeHandles.entrySet().removeIf(entry -> {
            RuntimeWorldHandle handle = entry.getValue();
            if (handle.asWorld().getPlayers().isEmpty()) {
                handle.unload();
                PersonalWorldsMod.LOGGER.debug("Unloaded empty dimension: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Unload all dimensions. Called on server shutdown.
     */
    public static void unloadAll() {
        for (RuntimeWorldHandle handle : activeHandles.values()) {
            handle.unload();
        }
        activeHandles.clear();
        PersonalWorldsMod.LOGGER.info("Unloaded all player dimensions");
    }

    /**
     * Check if a dimension is currently loaded.
     */
    public static boolean isDimensionLoaded(UUID playerUuid) {
        return activeHandles.containsKey(playerUuid);
    }

    /**
     * Get a loaded dimension's world, if available.
     */
    public static ServerWorld getLoadedDimension(UUID playerUuid) {
        RuntimeWorldHandle handle = activeHandles.get(playerUuid);
        return handle != null ? handle.asWorld() : null;
    }

    /**
     * Get the number of currently loaded dimensions.
     */
    public static int getLoadedDimensionCount() {
        return activeHandles.size();
    }

    // --- Private Helpers ---

    private static Identifier createDimensionId(UUID playerUuid) {
        // Format: personalworlds:pw_<uuid>
        return new Identifier(
            PersonalWorldsMod.MOD_ID,
            "pw_" + playerUuid.toString().replace("-", "")
        );
    }

    private static RuntimeWorldConfig createWorldConfig(
            MinecraftServer server,
            WorldGenType genType,
            UUID playerUuid
    ) {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
            .setDimensionType(DimensionTypes.OVERWORLD)
            .setSeed(playerUuid.hashCode())
            .setDifficulty(server.getSaveProperties().getDifficulty())
            .setShouldTickTime(true);

        // Set chunk generator based on type
        switch (genType) {
            case OVERWORLD -> config.setGenerator(
                server.getOverworld().getChunkManager().getChunkGenerator()
            );
            case FLAT -> {
                // Use overworld generator for now; flat generator added in later phase
                config.setGenerator(server.getOverworld().getChunkManager().getChunkGenerator());
            }
            case VOID -> {
                // Use overworld generator for now; void generator added in Phase 5
                config.setGenerator(server.getOverworld().getChunkManager().getChunkGenerator());
            }
        }

        return config;
    }
}
```

**Note:** The void chunk generator is deferred to Phase 5. For Phase 1 testing, all world types use the overworld generator.

---

## Step 9: Create Event Handlers

### 9.1 `event/ModEventHandlers.java`

```java
package com.wickedsik.personalworlds.event;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class ModEventHandlers {

    private static int tickCounter = 0;
    private static final int UNLOAD_CHECK_INTERVAL = 600; // 30 seconds

    public static void register() {
        // Server started - restore all dimensions
        ServerLifecycleEvents.SERVER_STARTED.register(ModEventHandlers::onServerStarted);

        // Server stopping - cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(ModEventHandlers::onServerStopping);

        // Periodic tick for unloading empty dimensions
        ServerTickEvents.END_SERVER_TICK.register(ModEventHandlers::onServerTick);

        PersonalWorldsMod.LOGGER.info("Event handlers registered");
    }

    private static void onServerStarted(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server started - restoring player dimensions");
        DimensionRegistry.get(server).restoreAllDimensions(server);
    }

    private static void onServerStopping(MinecraftServer server) {
        PersonalWorldsMod.LOGGER.info("Server stopping - unloading all dimensions");
        DimensionManager.unloadAll();
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= UNLOAD_CHECK_INTERVAL) {
            tickCounter = 0;
            DimensionManager.unloadEmptyDimensions();
        }
    }
}
```

---

## Step 10: Create Test Commands

### 10.1 `command/TestCommands.java`

Temporary commands for Phase 1 verification:

```java
package com.wickedsik.personalworlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import com.wickedsik.personalworlds.dimension.DimensionRegistry;
import com.wickedsik.personalworlds.dimension.PlayerDimensionData;
import com.wickedsik.personalworlds.dimension.WorldGenType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.Map;
import java.util.UUID;

public class TestCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("pw")
                .requires(source -> source.hasPermissionLevel(2)) // OP level 2+

                // /pw create [type] - Create and enter your personal dimension
                .then(CommandManager.literal("create")
                    .executes(ctx -> createDimension(ctx.getSource(), "OVERWORLD"))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> createDimension(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")
                        ))
                    )
                )

                // /pw enter - Enter your personal dimension
                .then(CommandManager.literal("enter")
                    .executes(ctx -> enterDimension(ctx.getSource()))
                )

                // /pw leave - Return to overworld
                .then(CommandManager.literal("leave")
                    .executes(ctx -> leaveDimension(ctx.getSource()))
                )

                // /pw list - List all registered dimensions
                .then(CommandManager.literal("list")
                    .executes(ctx -> listDimensions(ctx.getSource()))
                )

                // /pw info - Show info about current dimension status
                .then(CommandManager.literal("info")
                    .executes(ctx -> showInfo(ctx.getSource()))
                )
        );
    }

    private static int createDimension(ServerCommandSource source, String typeStr) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        WorldGenType type = WorldGenType.fromString(typeStr);
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                source.getServer(),
                playerUuid,
                playerName,
                type
            );

            // Teleport player to the dimension using FabricDimensions
            TeleportTarget target = new TeleportTarget(
                new Vec3d(0.5, 65, 0.5),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch()
            );
            FabricDimensions.teleport(player, dimension, target);

            source.sendFeedback(() -> Text.literal(
                "Created dimension with " + type.name() + " generator. Welcome to your world!"
            ), true);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to create dimension: " + e.getMessage()));
            return 0;
        }
    }

    private static int enterDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        UUID playerUuid = player.getUuid();
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());

        if (!registry.hasDimension(playerUuid)) {
            source.sendError(Text.literal(
                "You don't have a personal dimension. Use /pw create first."
            ));
            return 0;
        }

        PlayerDimensionData data = registry.getDimensionData(playerUuid).orElse(null);
        if (data == null) {
            source.sendError(Text.literal("Failed to load dimension data"));
            return 0;
        }

        try {
            ServerWorld dimension = DimensionManager.getOrCreatePlayerDimension(
                source.getServer(),
                playerUuid,
                player.getName().getString(),
                data.generatorType()
            );

            TeleportTarget target = new TeleportTarget(
                new Vec3d(
                    data.spawnPoint().getX() + 0.5,
                    data.spawnPoint().getY(),
                    data.spawnPoint().getZ() + 0.5
                ),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch()
            );
            FabricDimensions.teleport(player, dimension, target);

            source.sendFeedback(() -> Text.literal("Entered your personal dimension"), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to enter dimension: " + e.getMessage()));
            return 0;
        }
    }

    private static int leaveDimension(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }

        ServerWorld overworld = source.getServer().getOverworld();
        Vec3d spawnPos = Vec3d.ofCenter(overworld.getSpawnPos());

        TeleportTarget target = new TeleportTarget(
            spawnPos,
            Vec3d.ZERO,
            player.getYaw(),
            player.getPitch()
        );
        FabricDimensions.teleport(player, overworld, target);

        source.sendFeedback(() -> Text.literal("Returned to overworld"), true);
        return 1;
    }

    private static int listDimensions(ServerCommandSource source) {
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        Map<UUID, PlayerDimensionData> dimensions = registry.getAllDimensions();

        if (dimensions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No player dimensions registered"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Registered dimensions:\n");
        for (PlayerDimensionData data : dimensions.values()) {
            boolean loaded = DimensionManager.isDimensionLoaded(data.ownerUuid());
            sb.append(String.format("  - %s (%s) [%s]\n",
                data.ownerName(),
                data.generatorType().name(),
                loaded ? "LOADED" : "unloaded"
            ));
        }

        final String message = sb.toString();
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private static int showInfo(ServerCommandSource source) {
        int loaded = DimensionManager.getLoadedDimensionCount();
        DimensionRegistry registry = DimensionRegistry.get(source.getServer());
        int total = registry.getAllDimensions().size();

        String info = String.format(
            "Personal Worlds Status:\n  Total registered: %d\n  Currently loaded: %d",
            total, loaded
        );

        source.sendFeedback(() -> Text.literal(info), false);
        return 1;
    }
}
```

---

## Step 11: Update Mixin Configuration

### 11.1 Update `resources/personalworlds.mixins.json`

Rename from `modid.mixins.json` and update:

```json
{
  "required": true,
  "package": "com.wickedsik.personalworlds.mixin",
  "compatibilityLevel": "JAVA_17",
  "mixins": [],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

**Note:** No mixins needed for Phase 1. The file must exist but can have empty arrays.

### 11.2 Remove client mixin file

Delete `resources/modid.client.mixins.json` (not needed for Phase 1).

---

## Step 12: Create Empty Mixin Package

Create the mixin package directory even if empty:

```
src/main/java/com/wickedsik/personalworlds/mixin/
```

Add a placeholder or leave empty (Gradle will create it).

---

## Step 13: Update Resource Structure

### 13.1 Rename/Create Resource Directories

```
src/main/resources/
├── assets/
│   └── personalworlds/
│       └── icon.png                    # Mod icon (can be placeholder)
├── fabric.mod.json                     # Updated in Step 1
└── personalworlds.mixins.json          # Created in Step 11
```

---

## Step 14: Verification Build & Test

### 14.1 Clean Build

```bash
./gradlew clean build
```

Expected: Build succeeds with no errors.

### 14.2 Run Server Test

```bash
./gradlew runServer
```

Expected output in console:
```
[main/INFO] [personalworlds]: Personal Worlds initializing...
[main/INFO] [personalworlds]: Event handlers registered
[main/INFO] [personalworlds]: Personal Worlds initialized!
```

### 14.3 In-Game Testing

1. Start server, connect with client (`./gradlew runClient` in another terminal)
2. Op yourself: `op <username>` in server console
3. Run `/pw create OVERWORLD`
   - Should create dimension and teleport you
4. Run `/pw leave`
   - Should return to overworld
5. Run `/pw list`
   - Should show your dimension
6. Run `/pw enter`
   - Should teleport back to your dimension
7. Stop server, restart with `./gradlew runServer`
8. Run `/pw list`
   - Should still show your dimension (persistence test)
9. Run `/pw enter`
   - Should load and enter your persisted dimension

---

## Step 15: Persistence Validation

### 15.1 Check File Structure

After creating a dimension, verify the following files exist:

```
run/world/
├── dimensions/
│   └── personalworlds/
│       └── pw_<uuid>/
│           └── region/
│               └── r.0.0.mca (or similar)
└── data/
    └── personalworlds_registry.dat
```

### 15.2 World Reset Test

1. Stop server
2. Delete `run/world/region/`, `run/world/DIM-1/`, `run/world/DIM1/`
3. Keep `run/world/dimensions/` and `run/world/data/`
4. Start server
5. `/pw list` should still show your dimension
6. `/pw enter` should work and your builds should still exist

---

## Summary Checklist

| Step | Description | Status |
|------|-------------|--------|
| 1 | Update project metadata | ☐ |
| 2 | Add Fantasy dependency | ☐ |
| 3 | Create package structure | ☐ |
| 4 | Create mod entrypoint | ☐ |
| 5 | Create WorldGenType enum | ☐ |
| 6 | Create PlayerDimensionData | ☐ |
| 7 | Create DimensionRegistry | ☐ |
| 8 | Create DimensionManager | ☐ |
| 9 | Create event handlers | ☐ |
| 10 | Create test commands | ☐ |
| 11 | Update mixin configuration | ☐ |
| 12 | Create mixin package | ☐ |
| 13 | Update resource structure | ☐ |
| 14 | Verification build & test | ☐ |
| 15 | Persistence validation | ☐ |

---

## Known Limitations (Phase 1)

These will be addressed in later phases:

1. **No void generator** — All world types use overworld generation (Phase 5)
2. **No portal system** — Access via commands only (Phase 2)
3. **No return position storage** — Always returns to world spawn (Phase 3)
4. **No invitations** — Only owner can access their dimension (Phase 4)
5. **No starter platform** — Players spawn in wilderness (Phase 5)

---

## Resources

- [Fantasy Library GitHub](https://github.com/NucleoidMC/fantasy)
- [Fantasy Maven Repository](https://maven.nucleoid.xyz/)
- [Fabric Wiki — Persistent State](https://fabricmc.net/wiki/tutorial:persistent_states)
- [Fabric Wiki — Commands](https://fabricmc.net/wiki/tutorial:commands)
