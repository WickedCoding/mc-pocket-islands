# Pocket Islands

A Minecraft mod for Fabric, Forge and NeoForge that gives each player their own persistent
pocket dimension island. Build a portal, step through, and the island is yours. When the
server resets the overworld, nether and end, every island stays exactly as it was (see
[World Reset Procedure](#world-reset-procedure)).

**Supported Versions:**

| Minecraft | Fabric | Forge | NeoForge |
|-----------|--------|-------|----------|
| 1.20.1    | ✓      | ✓     |          |
| 1.20.4 ¹  | ✓      |       |          |
| 1.21.11   | ✓      |       | ✓        |
| 26.3      | ✓      |       | ✓        |

¹ Fabric only. There are no plans to add Forge or NeoForge support for 1.20.4.

## Features

- **Pocket Islands**: Each player gets their own isolated island dimension
- **Persistence**: Islands survive main world resets
- **Portal-Based Access**: Build a portal frame, activate with emerald
- **Invitation System**: Invite friends to visit your island
- **Void Generation**: An empty void world with a starter island

## Requirements

- **Fabric:** Fabric API and Fabric Loader 0.16.10+ for 1.20.1, 0.15.11+ for 1.20.4,
  0.18.4+ for 1.21.11, 0.20.1+ for 26.3
- **Forge (1.20.1):** Forge 47 or newer. Nothing else: Infiniverse is bundled in the jar
- **NeoForge (1.21.11, 26.3):** NeoForge 21.11.45 or newer for 1.21.11, 26.3.0.99 or newer for 26.3. Nothing else: Infiniverse is bundled in the jar
- **Java:** 17 for 1.20.x, 21 for 1.21.11, 25 for 26.3
- **Both client and server must have the mod installed** (see [FAQ](#faq))

## Installation

Download the jar for your Minecraft version and loader from
[Releases](https://github.com/WickedCoding/mc-pocket-islands/releases). Jars are named
`pocketislands-<version>+<minecraft>-<loader>.jar`, for example
`pocketislands-1.0.1+1.20.1-forge.jar`.

Install it on the server and on every client:

1. Install Fabric Loader, Forge or NeoForge
2. Place the Pocket Islands jar in the `mods/` folder
    - On Fabric, make sure Fabric API is also installed there
3. Start the server or launch Minecraft

### Loader Differences

Islands work the same on every loader, with these exceptions:

| | Fabric | Forge / NeoForge |
|---|---|---|
| Runtime dimensions | Fantasy (bundled) | Infiniverse (bundled) |
| Permissions | LuckPerms through fabric-permissions-api, else OP levels | LuckPerms through the loader's PermissionAPI, else OP levels |
| Island clock | Own clock; `doDaylightCycle` / `advance_time` set to `false` freezes it | Follows the overworld; `doDaylightCycle` / `advance_time` has no effect on the island |

Console and command blocks always use OP levels for `/pi` permissions.

Moving a whole world between loaders is not supported: switching loaders replaces
the entire modpack. Individual islands can move: copy `world/dimensions/personalworlds/` to
the other server (same Minecraft version) and the islands open without conversion. The
island registry, return positions and invitations stay with each server's own world.

## Usage

### Creating a Portal

With the default config, build a frame from Nether Bricks (4 wide x 5 tall):

```
N N N N
N     N
N     N
N     N
N N N N
```

Right-click the inside of the frame with an Emerald to activate it. Server owners can
change the frame block and activation item in the [configuration](#portal-types-island-customization).

### Entering Your Island

Walk into the activated portal to enter your pocket island. On first entry, your
island is generated from your portal type's island layers. There is no return portal
on a new island.

### Returning

The quickest way back is to jump off your island: below Y=0 you're teleported back to
where you entered, before taking any void damage. Your items stay with you.

To return by portal instead, build a frame on your island and activate it like the
first one (bring the frame blocks and activation item). Entering it takes you back to
your original location.

### Commands

**Player commands:**

- `/pi invites` - View your invitations (sent and received)
- `/pi invite <player>` - Invite a player to your island
- `/pi invite <player> always` - Invite with Always Welcome status (if enabled)
- `/pi uninvite <player>` - Revoke a player's invitation
- `/pi togglewelcome <player>` - Toggle Always Welcome for an existing invitation (if enabled)
- `/pi portals` - View all configured portal types and your island status
- `/pi leave` - Teleport to the spot you entered your pocket island from (your bed or world spawn if there is none). Works from anywhere
- `/pi unstuck` - Leave a pocket island you are stuck in, or clear pocket island data left over after a failed teleport. Sends you to the spot you entered from, your bed, or world spawn. Does nothing when you are already on solid ground

**Admin commands:**

| Command | Description | OP level |
|---------|-------------|----------|
| `/pi admin list` | List all islands | 2 |
| `/pi admin info <player>` | View island details | 2 |
| `/pi admin tp <player>` | Teleport to an island | 2 |
| `/pi admin reload` | Reload `config/pocketislands.json` without a restart | 3 |
| `/pi admin sanitize <player> [radius]` | Load a player's island and run the chunk sanitizer on a chunk radius around spawn (default `4`, max `16`). Runs even when `sanitizeChunksOnLoad` is `false` | 3 |
| `/pi admin delete <player> [confirm]` | Delete an island | 4 |

With LuckPerms installed, permission nodes replace these OP levels. See
[LuckPerms](#luckperms).

**Testing commands (op level 2+):**

- `/pi create [type]` - Create your island without a portal and teleport to it.
  `type` is `OVERWORLD` (default), `FLAT` or `VOID`; an unknown type gives `VOID`.
  The island uses the first portal type in the config
- `/pi enter` - Teleport to your existing island

**Debug commands (op level 4):**

- `/pi debug perf enable` - Enable performance monitoring
- `/pi debug perf disable` - Disable performance monitoring
- `/pi debug perf status` - Show performance status
- `/pi debug perf reset` - Reset performance counters

## LuckPerms

With [LuckPerms](https://luckperms.net/) installed, these permission nodes control the
admin and testing commands. Without it, or when LuckPerms does not set a node, the OP level
applies.

```
pocketislands
├── admin
│   ├── list        /pi admin list         OP 2
│   ├── info        /pi admin info         OP 2
│   ├── teleport    /pi admin tp           OP 2
│   ├── reload      /pi admin reload       OP 3
│   ├── sanitize    /pi admin sanitize     OP 3
│   └── delete      /pi admin delete       OP 4
└── player
    └── create      /pi create, /pi enter  OP 2
```

Grant a node to a group:

```
/lp group moderator permission set pocketislands.admin.reload true
```

- Player commands (`invite`, `uninvite`, `invites`, `togglewelcome`, `portals`, `leave`,
  `unstuck`) need no node: every player can use them
- `pocketislands.player.invite` and `pocketislands.player.visit` are registered but not
  checked yet. Setting them has no effect
- Debug commands (OP 4) and bypassing visit restrictions (OP 2) use OP levels only

## Configuration

**Config file:** `config/pocketislands.json`

You can define several portal types, each building islands from its own layers of
blocks, and tune invitations, unloading and effects.

### Example Configuration

```json
{
    "portalTypes": [
        {
            "frameBlock": "minecraft:nether_bricks",
            "activationItem": "minecraft:emerald",
            "islandLayers": [
                "minecraft:grass_block",
                "minecraft:dirt",
                "minecraft:stone"
            ],
            "portalColor": "red"
        }
    ],
    "consumeActivationItem": false,
    "maxInvitationsPerPlayer": 20,
    "enableAlwaysWelcome": false,
    "allowVisitWhenHostNotHome": false,
    "unloadEmptyDimensionDelayTicks": 600,
    "cleanupIntervalTicks": 600,
    "sanitizeChunksOnLoad": false,
    "sanitizeRemoveOrphanBlocks": false,
    "enableTeleportParticles": true,
    "enableTeleportSounds": true,
    "enablePortalActivationEffects": true,
    "enableInvitationNotifications": true,
    "dimensionGameRules": {
        "doMobSpawning": false
    }
}
```

The mod also writes a `configVersion` field to the file. Do not edit it by hand.

The example shows the 1.20.x default for `dimensionGameRules`. On 1.21.11 and 26.3 the default is
`{"spawn_mobs": false}`. See [Dimension Game Rules](#dimension-game-rules).

### Configuration Options

#### Portal Types (Island Customization)

Define multiple portal types to create islands with different materials:

- **`frameBlock`** — Block used for portal frames (e.g., `"minecraft:nether_bricks"`)
- **`activationItem`** — Item used to activate portals (e.g., `"minecraft:emerald"`)
- **`islandLayers`** — **Customize your starter island platform** (up to 5 layers, top to bottom; extra layers are dropped)
    - Example: `["minecraft:grass_block", "minecraft:dirt", "minecraft:stone"]`
    - Create themed islands: grass/dirt/stone, netherrack/soul_sand/basalt, end_stone, etc.
    - Each portal type creates a unique island composition
    - Each layer is 1 block thick. For 2 layers of dirt, list the block twice
- **`portalColor`** — Color of the portal effect (default: `"red"`)
    - Available colors: `"white"`, `"light_gray"`, `"gray"`, `"black"`, `"brown"`, `"red"`, `"orange"`, `"yellow"`, `"lime"`, `"green"`, `"cyan"`, `"light_blue"`, `"blue"`, `"purple"`, `"magenta"`, `"pink"`
    - Helps visually distinguish different portal types

Islands and portals refer to a portal type by its position in `portalTypes`. Add new
portal types at the end of the list, and do not remove or reorder existing ones.

If two portal types use the same `frameBlock`, the first one in the list is used.

#### Invitations

- **`maxInvitationsPerPlayer`** — Maximum invitations per player (default: `20`, `-1` for unlimited)
- **`enableAlwaysWelcome`** — Enable the [Always Welcome](#always-welcome-feature) feature (default: `false`, requires restart)
- **`allowVisitWhenHostNotHome`** — Let invited visitors enter while the host is online
  but not on their own island (default: `false`: the host must be on their island)

#### Unloading

- **`unloadEmptyDimensionDelayTicks`** — Delay before unloading empty dimensions (default: `600` = 30 seconds)
- **`cleanupIntervalTicks`** — How often to check for empty dimensions (default: `600` = 30 seconds, minimum `20`)

#### Chunk Sanitizer

- **`sanitizeChunksOnLoad`** — Purge orphaned block entities and unknown container items on chunk load (default: `false`; opt-in)
- **`sanitizeRemoveOrphanBlocks`** — Also remove blocks left without valid support (fires, torches, redstone, snow layers) during sanitization (default: `false`; only used when `sanitizeChunksOnLoad` is enabled)

#### Visual Effects

- **`enableTeleportParticles`** — Show particle effects during teleportation (default: `true`)
- **`enableTeleportSounds`** — Play sound effects during teleportation (default: `true`)
- **`enablePortalActivationEffects`** — Show effects when activating portals (default: `true`)
- **`enableInvitationNotifications`** — Play sounds for invitation notifications (default: `true`)

#### Advanced Options

- **`consumeActivationItem`** — Whether the activation item is consumed on portal activation (default: `false`)

### Always Welcome Feature

By default, visitors can only enter your island when you are online and present
on your island (see `allowVisitWhenHostNotHome` to drop the second condition). The
**Always Welcome** feature allows island owners to grant specific guests permanent
access, bypassing these restrictions.

#### Enabling the Feature

Set `enableAlwaysWelcome` to `true` in your config and restart the server:

```json
{
    "enableAlwaysWelcome": true
}
```

This setting requires a server restart to take effect. When disabled, all Always
Welcome commands are hidden and existing flags are ignored.

#### Using Always Welcome

Once enabled, island owners have two ways to grant Always Welcome status:

**Option 1: Invite with Always Welcome**

```
/pi invite Steve always
```

This creates a new invitation with Always Welcome enabled immediately.

**Option 2: Toggle existing invitation**

```
/pi togglewelcome Steve
```

This toggles the Always Welcome status on an existing invitation. Run it again
to disable.

#### Viewing Always Welcome Status

Use `/pi invites` to see your sent invitations. When the feature is enabled,
each invitation shows a clickable star indicator:

```
Sent (players who can visit you):
  - Steve [★] [Revoke]    ← Green star: Always Welcome ON
  - Alex  [☆] [Revoke]    ← Gray star: Always Welcome OFF
```

Click the star to toggle the status directly from the chat.

#### Access Control Behavior

| Invitation Type                                | Host Online + Home | Host Online + Away | Host Offline |
|------------------------------------------------|--------------------|--------------------|--------------|
| Standard                                       | ✓ Allowed          | ✗ Denied           | ✗ Denied     |
| Standard, `allowVisitWhenHostNotHome: true`    | ✓ Allowed          | ✓ Allowed          | ✗ Denied     |
| Always Welcome                                 | ✓ Allowed          | ✓ Allowed          | ✓ Allowed    |

- Admins (OP level 2+) always bypass all visit restrictions
- The island owner can always access their own island
- Always Welcome guests still need an active invitation (uninviting removes access)

### Dimension Game Rules

Pocket dimensions inherit all game rules from the overworld by default. You can override specific rules using the `dimensionGameRules` configuration:

```json
{
    "dimensionGameRules": {
        "keepInventory": true,
        "randomTickSpeed": 3,
        "doMobSpawning": false
    }
}
```

This configuration will:
- Force `keepInventory` to **true** (players keep items on death)
- Force `randomTickSpeed` to **3** (crop and plant growth multiplier)
- Force `doMobSpawning` to **false** (mobs cannot spawn naturally)
- All other rules inherit from the overworld

**Version-Specific Game Rule Names:**

Minecraft 1.21.11 renamed many game rules, and 26.3 uses the same new names. Use the names for your server version:

| Feature                 | 1.20.x (camelCase)   | 1.21.11, 26.3 (snake_case) |
|-------------------------|----------------------|----------------------------|
| Keep inventory on death | `keepInventory`      | `keep_inventory`    |
| Mob spawning            | `doMobSpawning`      | `spawn_mobs`        |
| Crop/plant growth speed | `randomTickSpeed`    | `random_tick_speed` |
| Mob item drops          | `doMobLoot`          | `mob_drops`         |
| Block drops             | `doTileDrops`        | `block_drops`       |
| Mob griefing            | `mobGriefing`        | `mob_griefing`      |
| Immediate respawn       | `doImmediateRespawn` | `immediate_respawn` |
| Daylight cycle          | `doDaylightCycle`    | `advance_time`      |

**Default behavior:**
- 1.20.x: `{"doMobSpawning": false}` — No natural mob spawning on islands
- 1.21.11, 26.3: `{"spawn_mobs": false}` — The same rule under its new name

Islands are void worlds, so mob spawning is off by default. You can turn it on by
setting `doMobSpawning` / `spawn_mobs` to `true`, but this is unsupported: bug reports
about mob spawning on islands will be closed.

To inherit all rules from the overworld without changes, use an empty map:

```json
{
    "dimensionGameRules": {}
}
```

### Creating Multiple Portal Types

You can define multiple portal types to create different island themes. Players
can choose which type of island they want by using different portal materials:

```json
{
    "portalTypes": [
        {
            "frameBlock": "minecraft:nether_bricks",
            "activationItem": "minecraft:emerald",
            "islandLayers": [
                "minecraft:grass_block",
                "minecraft:dirt",
                "minecraft:stone"
            ],
            "portalColor": "red"
        },
        {
            "frameBlock": "minecraft:blackstone",
            "activationItem": "minecraft:nether_star",
            "islandLayers": [
                "minecraft:netherrack",
                "minecraft:soul_sand",
                "minecraft:basalt"
            ],
            "portalColor": "red"
        },
        {
            "frameBlock": "minecraft:end_stone_bricks",
            "activationItem": "minecraft:ender_pearl",
            "islandLayers": [
                "minecraft:end_stone"
            ],
            "portalColor": "cyan"
        }
    ]
}
```

The first portal type a player uses decides their island's layers for good.

### Reloading Configuration

Use `/pi admin reload` to reload the configuration without restarting the
server. Changes to portal types only affect newly created islands.

## Technical Details

### Dimension Storage

Each pocket island is stored at:

```
world/dimensions/personalworlds/pw_<uuid>/
```

Where `<uuid>` is the player's UUID without dashes.

The island registry, return positions, invitations and portal ownership are stored in
`world/data/`: as `personalworlds_*.dat` up to Minecraft 1.21.11, and in
`world/data/personalworlds/` from 26.3.

### World Reset Procedure

To reset the overworld, nether and end but keep every island, stop the server and
delete the vanilla dimension folders:

| Minecraft | Delete | Keep |
|---|---|---|
| 1.20.x, 1.21.11 | `world/region/`, `world/entities/`, `world/poi/`, `world/DIM-1/`, `world/DIM1/` | `world/dimensions/personalworlds/`, `world/data/`, `world/level.dat` |
| 26.3 | `world/dimensions/minecraft/` | `world/dimensions/personalworlds/`, `world/data/`, `world/level.dat` |

Then start the server. Islands, return positions, invitations and portal ownership are
still there.

Upgrading a world from 1.21.11 to 26.3 keeps everything: on the first start the mod moves
its data files from `world/data/` to `world/data/personalworlds/`.

### Metadata Recovery

Each island folder holds a `personalworlds_metadata.json` file with the island's owner,
spawn point and portal type. On every server start, the mod checks for island folders
missing from the island registry and registers them again from this file. If the
registry file is lost or damaged, islands come back on their own.

### What Happens When...

- **You log out on your island**: You log back in on your island, even if it was unloaded while you were away
- **Your island cannot be loaded when you log in**: You are sent to your bed, or to world spawn
- **The server crashes while you are on an island**: At your next login you stay on the island if you are still allowed there. Otherwise you are moved off it
- **Two players walk into a portal at the same time**: Each player is teleported once, and a new island is created only once
- **Your return spot is blocked**: You are placed at the nearest safe spot
- **Your invitation is revoked while you are offline**: At your next login you are moved to where you entered from, your bed, or world spawn
- **A mod was removed and left unknown blocks or items behind** (server owners): Enable `sanitizeChunksOnLoad` to clean chunks as they load, or run `/pi admin sanitize` on one island. This removes leftover block entities whose block is gone, and clears unknown items from containers

## Performance

Pocket Islands is built for small and medium servers. It has not been tested on large
servers with hundreds of players, so expect to tune or test it yourself there.

- Empty islands unload after 30 seconds (see [Unloading](#unloading))
- Performance monitoring is available through the debug commands

## FAQ

### Do I need the mod on both client and server?

**Yes.** Pocket Islands must be installed on both the server and all connecting
clients.

### What happens if a client doesn't have the mod installed?

The client will experience severe rendering issues when looking at a portal.
Chunks will fail to load properly, causing:

- Invisible or corrupted terrain
- Falling through the world
- Visual glitches and flickering
- Potential client crashes

This happens because the client cannot properly load the textures and so cannot
show the chunks properly.

### Can I use this on a vanilla client?

No. The mod registers custom dimension types that vanilla clients cannot
understand. Always ensure clients have the mod installed before they attempt to
use pocket island portals.

### What happens to my island if the main world is reset?

Your island is safe. Pocket islands are stored separately from the main world.
See [World Reset Procedure](#world-reset-procedure) for the folders to delete and keep.

### Can I visit other players' islands?

Yes, if they invite you. Use `/pi invite <player>` to invite someone, then
enter their portal to visit their island.

### How do I get back from my island?

Jump off the edge or build a return portal. See [Returning](#returning).

## License

MIT License

## Credits

> First of all I want to credit my friend, Bktwone, for inspiring this mod as well
> as providing a platform to test this mod.

- [Fantasy](https://github.com/NucleoidMC/fantasy) for runtime dimension creation on Fabric
- [Infiniverse](https://github.com/Commoble/infiniverse) for runtime dimension creation on Forge and NeoForge
- [MixinExtras](https://github.com/LlamaLad7/MixinExtras), bundled in the Forge jar
- [Fabric API](https://github.com/FabricMC/fabric) for mod hooks on Fabric
- [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) for LuckPerms support on Fabric
