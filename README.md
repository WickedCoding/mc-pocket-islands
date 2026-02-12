# Pocket Islands

A Fabric mod for Minecraft that gives each player their own persistent
pocket dimension island.

**Supported Versions:** Minecraft 1.20.1, 1.20.4, 1.21.11

## Features

- **Pocket Islands**: Each player gets their own isolated island dimension
- **Persistence**: Islands survive main world resets
- **Portal-Based Access**: Build a portal frame, activate with emerald
- **Invitation System**: Invite friends to visit your island
- **Void Generation**: Clean slate void worlds with starter platforms

## Requirements

- Minecraft 1.20.1, 1.20.4, or 1.21.11
- Fabric Loader (0.15.0+ for 1.20.x, 0.18.4+ for 1.21.x)
- Fabric API
- **Both client and server must have the mod installed**

## Installation

Download the version matching your Minecraft version from
[Releases](https://github.com/wickedsik/pocket-islands/releases):

- `personalworlds-X.X.X+1.20.1.jar` for Minecraft 1.20.1
- `personalworlds-X.X.X+1.20.4.jar` for Minecraft 1.20.4
- `personalworlds-X.X.X+1.21.11.jar` for Minecraft 1.21.11

### Server

1. Install Fabric Loader on your server
2. Download the correct Pocket Islands JAR for your MC version
3. Place in the server's `mods/` folder
    - Make sure Fabric API is also installed here
4. Start the server

### Client

1. Install Fabric Loader
2. Download the correct Pocket Islands JAR for your MC version
3. Place in your `mods/` folder
    - Make sure Fabric API is also installed here
4. Launch Minecraft

**Important:** The mod must be installed on both client and server. See
[FAQ](#faq) for details.

## Usage

### Creating a Portal

> This can be configured, this explanation uses the default configuration.

Build a frame using Nether Bricks (4 wide x 5 tall):

```
N N N N
N     N
N     N
N     N
N N N N
```

Right-click the inside of the frame with an Emerald to activate.

### Entering Your Island

Walk into the activated portal to enter your pocket island. On first entry, a
starter platform with grass blocks and a return portal frame will be created.

### Returning

Enter the portal on your pocket island to return to your original location.

**Important:** Bring enough materials to build a return portal!

### Commands

**Player commands:**

- `/pi invites` - View your invitations (sent and received)
- `/pi invite <player>` - Invite a player to your island
- `/pi invite <player> always` - Invite with Always Welcome status (if enabled)
- `/pi uninvite <player>` - Revoke a player's invitation
- `/pi togglewelcome <player>` - Toggle Always Welcome for an existing invitation (if enabled)
- `/pi portals` - View all configured portal types and your island status

**Admin commands (op level 2+):**

- `/pi admin list` - List all islands
- `/pi admin info <player>` - View island details
- `/pi admin tp <player>` - Teleport to an island
- `/pi admin delete <player> [confirm]` - Delete an island (op 4)

**Debug commands (op level 4):**

- `/pi debug perf enable` - Enable performance monitoring
- `/pi debug perf disable` - Disable performance monitoring
- `/pi debug perf status` - Show performance status
- `/pi debug perf reset` - Reset performance counters

**Note:** You should not need the debug commands, but they may be useful for
checking.

## Configuration

**Config file:** `config/personalworlds.json`

Pocket Islands offers extensive customization options, particularly for **island
composition**. You can define multiple portal types, each creating islands with
different materials and properties.

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
    "unloadEmptyDimensionDelayTicks": 600,
    "cleanupIntervalTicks": 600,
    "enableTeleportParticles": true,
    "enableTeleportSounds": true,
    "enablePortalActivationEffects": true,
    "enableInvitationNotifications": true
}
```

### Configuration Options

#### Portal Types (Island Customization)

Define multiple portal types to create islands with different materials:

- **`frameBlock`** — Block used for portal frames (e.g., `"minecraft:nether_bricks"`)
- **`activationItem`** — Item used to activate portals (e.g., `"minecraft:emerald"`)
- **`islandLayers`** — **Customize your starter island platform** (up to 5 layers, top to bottom)
    - Example: `["minecraft:grass_block", "minecraft:dirt", "minecraft:stone"]`
    - Create themed islands: grass/dirt/stone, netherrack/soul_sand/basalt, end_stone, etc.
    - Each portal type creates a unique island composition
    - Each layer is 1 block thick, for 2 dirt block layers, repeat the material in the array
- **`portalColor`** — Color of the portal effect (default: `"red"`)
    - Available colors: `"white"`, `"light_gray"`, `"gray"`, `"black"`, `"brown"`, `"red"`, `"orange"`, `"yellow"`, `"lime"`, `"green"`, `"cyan"`, `"light_blue"`, `"blue"`, `"purple"`, `"magenta"`, `"pink"`
    - Helps visually distinguish different portal types

#### Invitations

- **`maxInvitationsPerPlayer`** — Maximum invitations per player (`-1` for unlimited)
- **`enableAlwaysWelcome`** — Enable the Always Welcome feature (default: `false`, requires restart)

### Always Welcome Feature

By default, visitors can only enter your island when you are online and present
on your island. The **Always Welcome** feature allows island owners to grant
specific guests permanent access, bypassing these restrictions.

#### Enabling the Feature

Set `enableAlwaysWelcome` to `true` in your config and restart the server:

```json
{
    "enableAlwaysWelcome": true
}
```

**Note:** This setting requires a server restart to take effect. When disabled,
all Always Welcome commands are hidden and existing flags are ignored.

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

| Invitation Type | Host Online + Home | Host Online + Away | Host Offline |
|-----------------|--------------------|--------------------|--------------|
| Standard        | ✓ Allowed          | ✗ Denied           | ✗ Denied     |
| Always Welcome  | ✓ Allowed          | ✓ Allowed          | ✓ Allowed    |

**Notes:**

- Admins (OP level 2+) always bypass all visit restrictions
- The island owner can always access their own island
- Always Welcome guests still need an active invitation (uninviting removes access)

#### Performance

- **`unloadEmptyDimensionDelayTicks`** — Delay before unloading empty dimensions (default: `600` = 30 seconds)
- **`cleanupIntervalTicks`** — How often to check for empty dimensions (default: `600` = 30 seconds)

#### Visual Effects

- **`enableTeleportParticles`** — Show particle effects during teleportation
- **`enableTeleportSounds`** — Play sound effects during teleportation
- **`enablePortalActivationEffects`** — Show effects when activating portals
- **`enableInvitationNotifications`** — Play sounds for invitation notifications

#### Advanced Options

- **`consumeActivationItem`** — Whether the activation item is consumed on portal activation (default: `false`)

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

**Note:** The first portal type a player uses determines their island
composition permanently.

### Reloading Configuration

Use `/pi admin reload` to reload the configuration without restarting the
server. Changes to portal types only affect newly created islands.

## Translations

Pocket Islands uses Fabric's standard language file system for all user-facing
messages. This allows server admins and modpack creators to customize messages
or add translations for different languages.

### Language File Location

Language files are located at:

```
src/main/resources/assets/personalworlds/lang/
```

The mod includes an English (US) translation by default:

```
lang/en_us.json
```

### Adding Custom Languages

To add support for another language (e.g., German, Dutch, Spanish):

1. Create a new language file with the appropriate locale code:
    - `de_de.json` for German
    - `nl_nl.json` for Dutch
    - `es_es.json` for Spanish
    - See [Minecraft Wiki - Language](https://minecraft.wiki/w/Language) for all locale codes

2. Copy the contents of `en_us.json` as a template

3. Translate the message values (keep the keys unchanged):

```json lines
{
    "personalworlds.message.invite_sent": "Eingeladen %s zu deiner Dimension",
    "personalworlds.message.invite_received": "%s hat dich zu ihrer Dimension eingeladen",
    // ...
}
```

4. Place the file in `assets/personalworlds/lang/` in your resource pack or mod JAR

### Customizing Messages

To customize messages while keeping the English language:

1. Create a resource pack or edit `assets/personalworlds/lang/en_us.json` directly
2. Modify the translation values (right side of each line)
3. Keep translation keys (left side) unchanged
4. Use `%s` for string parameters, `%d` for numbers

**Example:**

```json
{
    "personalworlds.message.invite_sent": "You invited %s to your island!",
    "personalworlds.message.invite_received": "%s wants you to visit their island!"
}
```

### Available Translation Keys

All translation keys are documented in `lang/en_us.json`. Key categories include:

- **`personalworlds.message.*`** — Player-facing messages (invitations, ejections, teleports)
- **`personalworlds.command.*`** — Command feedback and info
- **`personalworlds.command.error.*`** — Error messages
- **`personalworlds.invitations.*`** — Invitation list UI text
- **`personalworlds.command.perf.*`** — Performance monitoring messages
- **`personalworlds.command.list.*`** — Admin list command output
- **`personalworlds.command.info.*`** — Admin info command output
- **`personalworlds.command.delete.*`** — Admin delete command warnings
- **`command.personalworlds.portals.*`** — Portal types listing and island status

## Technical Details

### Dimension Storage

Each pocket island is stored at:

```
world/dimensions/personalworlds/pw_<uuid>/
```

Where `<uuid>` is the player's UUID without dashes.

### Metadata Recovery

Each dimension folder contains a `.metadata` file that enables recovery if the
main registry is corrupted. This file contains:

- Owner UUID
- Owner name
- Dimension ID
- Creation timestamp
- Spawn point
- Generator type

### Edge Cases Handled

- **Player disconnects in pocket island**: Position preserved, dimension restored on reconnect even if unloaded
- **Dimension unloaded while offline**: Player is restored to their island with fallback to bed spawn or world spawn if restoration fails
- **Server crashes**: Recovery handler checks permissions and evacuates if needed
- **Two players enter portal simultaneously**: Concurrent portal guard prevents race conditions
- **Return position is blocked**: Safe spawn finder locates nearby safe position
- **Invitation revoked while offline**: Player evacuated on next login with fallback chain (return data → bed → spawn)
- **Player falls off island**: Automatically teleported back before taking void damage

## Performance

The mod is designed to handle 15+ concurrent islands efficiently:

- Islands unload automatically when empty (after 30 second delay)
- Memory-efficient chunk generation for void worlds
- Performance monitoring available via debug commands

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

This happens because the client can not properly load the textures and thus can
not show the chunks properly.

### Can I use this on a vanilla client?

No. The mod registers custom dimension types that vanilla clients cannot
understand. Always ensure clients have the mod installed before they attempt to
use pocket island portals.

### What happens to my island if the main world is reset?

Your island is safe. Pocket islands are stored separately from the main world.
See [World Reset Procedure](#world-reset-procedure) for details on which
folders to keep.

### Can I visit other players' islands?

Yes, if they invite you. Use `/pi invite <player>` to invite someone, then
enter their portal to visit their island.

### How do I get back from my island?

Enter the return portal on your island. It teleports you back to the exact
location you entered from.

### What happens if I fall off my island?

You're safe. When you fall below Y=0, you're automatically teleported back to
your original entry location before taking any void damage. Your items and
progress are preserved.

## License

MIT License

## Credits

- Built with [Fantasy](https://github.com/NucleoidMC/fantasy) for runtime dimension creation
- Fabric API for mod hooks
