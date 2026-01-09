# PersonalWorlds

A Fabric mod for Minecraft 1.20.4 that gives each player their own persistent dimension.

## Features

- **Personal Dimensions**: Each player gets their own isolated world
- **Persistence**: Dimensions survive main world resets
- **Portal-Based Access**: Build a portal frame, activate with emerald
- **Invitation System**: Invite friends to visit your dimension
- **Void Generation**: Clean slate void worlds with starter platforms

## Requirements

- Minecraft 1.20.4
- Fabric Loader 0.14.22+
- Fabric API

## Installation

1. Install Fabric Loader
2. Download PersonalWorlds from releases
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

### Entering Your Dimension

Walk into the activated portal to enter your personal dimension. On first entry, a starter platform with grass blocks and a return portal frame will be created.

### Returning

Enter the portal in your personal dimension to return to your original location.

### Commands

**Player commands:**
- `/pw invites` - View your invitations (sent and received)
- `/pw invite <player>` - Invite a player to your dimension
- `/pw uninvite <player>` - Revoke a player's invitation
- `/pw go <player>` - Visit someone's dimension (requires invitation)

**Admin commands (op level 2+):**
- `/pw admin list` - List all dimensions
- `/pw admin info <player>` - View dimension details
- `/pw admin tp <player>` - Teleport to a dimension
- `/pw admin delete <player> [confirm]` - Delete a dimension (op 4)
- `/pw admin reload` - Reload configuration

**Debug commands (op level 4):**
- `/pw debug perf enable` - Enable performance monitoring
- `/pw debug perf disable` - Disable performance monitoring
- `/pw debug perf status` - Show performance status
- `/pw debug perf reset` - Reset performance counters

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

To reset the main world while preserving player dimensions:

1. Stop the server
2. Delete: `world/region/`, `world/DIM-1/`, `world/DIM1/`
3. Keep: `world/dimensions/`, `world/data/`
4. Start server

Player dimensions are stored in `world/dimensions/personalworlds/` and their registry data is in `world/data/`.

## Technical Details

### Dimension Storage

Each player dimension is stored at:
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

- **Player disconnects in personal dimension**: Position preserved, permission checked on reconnect
- **Server crashes**: Recovery handler checks permissions and evacuates if needed
- **Two players enter portal simultaneously**: Concurrent portal guard prevents race conditions
- **Return position is blocked**: Safe spawn finder locates nearby safe position
- **Invitation revoked while offline**: Player evacuated on next login

## Performance

The mod is designed to handle 15+ concurrent dimensions efficiently:

- Dimensions unload automatically when empty (after 30 second delay)
- Memory-efficient chunk generation for void worlds
- Performance monitoring available via debug commands

## License

MIT License

## Credits

- Built with [Fantasy](https://github.com/NucleoidMC/fantasy) for runtime dimension creation
- Fabric API for mod hooks
