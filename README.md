# Pocket Islands

A Fabric mod for Minecraft 1.20.4 that gives each player their own persistent pocket dimension island.

## Features

- **Pocket Islands**: Each player gets their own isolated island dimension
- **Persistence**: Islands survive main world resets
- **Portal-Based Access**: Build a portal frame, activate with emerald
- **Invitation System**: Invite friends to visit your island
- **Void Generation**: Clean slate void worlds with starter platforms

## Requirements

- Minecraft 1.20.4
- Fabric Loader 0.14.22+
- Fabric API

## Installation

1. Install Fabric Loader
2. Download Pocket Islands from releases
3. Place in `mods/` folder
4. Launch Minecraft

## Usage

### Creating a Portal

Build a frame using Nether Bricks (4 wide x 5 tall):

```
N N N N
N     N
N     N
N     N
N N N N
```

Right-click inside the frame with an Emerald to activate.

### Entering Your Island

Walk into the activated portal to enter your pocket island. On first entry, a starter platform with grass blocks and a return portal frame will be created.

### Returning

Enter the portal in your pocket island to return to your original location.

### Commands

**Player commands:**
- `/pi invites` - View your invitations (sent and received)
- `/pi invite <player>` - Invite a player to your island
- `/pi uninvite <player>` - Revoke a player's invitation
- `/pi go <player>` - Visit someone's island (requires invitation)

**Admin commands (op level 2+):**
- `/pi admin list` - List all islands
- `/pi admin info <player>` - View island details
- `/pi admin tp <player>` - Teleport to an island
- `/pi admin delete <player> [confirm]` - Delete an island (op 4)
- `/pi admin reload` - Reload configuration

**Debug commands (op level 4):**
- `/pi debug perf enable` - Enable performance monitoring
- `/pi debug perf disable` - Disable performance monitoring
- `/pi debug perf status` - Show performance status
- `/pi debug perf reset` - Reset performance counters

## Configuration

Config file: `config/personalworlds.json`

```json
{
  "frameBlock": "minecraft:nether_bricks",
  "activationItem": "minecraft:emerald",
  "portalCooldownTicks": 60
}
```

## World Reset Procedure

To reset the main world while preserving pocket islands:

1. Stop the server
2. Delete: `world/region/`, `world/DIM-1/`, `world/DIM1/`
3. Keep: `world/dimensions/`, `world/data/`
4. Start server

Pocket islands are stored in `world/dimensions/personalworlds/` and their registry data is in `world/data/`.

## Technical Details

### Dimension Storage

Each pocket island is stored at:
```
world/dimensions/personalworlds/pw_<uuid>/
```

Where `<uuid>` is the player's UUID without dashes.

### Metadata Recovery

Each dimension folder contains a `.metadata` file that enables recovery if the main registry is corrupted. This file contains:
- Owner UUID
- Owner name
- Dimension ID
- Creation timestamp
- Spawn point
- Generator type

### Edge Cases Handled

- **Player disconnects in pocket island**: Position preserved, permission checked on reconnect
- **Server crashes**: Recovery handler checks permissions and evacuates if needed
- **Two players enter portal simultaneously**: Concurrent portal guard prevents race conditions
- **Return position is blocked**: Safe spawn finder locates nearby safe position
- **Invitation revoked while offline**: Player evacuated on next login

## Performance

The mod is designed to handle 15+ concurrent islands efficiently:

- Islands unload automatically when empty (after 30 second delay)
- Memory-efficient chunk generation for void worlds
- Performance monitoring available via debug commands

## License

MIT License

## Credits

- Built with [Fantasy](https://github.com/NucleoidMC/fantasy) for runtime dimension creation
- Fabric API for mod hooks
