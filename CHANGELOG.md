# Changelog

All notable changes to Pocket Islands will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `/pi portals` command - shows all configured portal types with frame block, activation item, island layers, and portal color, plus the player's current island type

## [0.6.0] - 2026-02-01

### Added
- Minecraft 1.21.11 support - the mod now supports three versions (1.20.1, 1.20.4, 1.21.11) with Java 21 required for 1.21.x
- Compat package for version-specific API abstraction handling NBT, teleportation, identifiers, and persistent state differences between 1.20.x and 1.21.x
- Full 16-color portal palette matching Minecraft's standard dye colors (white, light_gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink)
- Pocket dimension recovery system - players who log out on their island are restored to it even if the dimension was unloaded during their absence

## [0.5.0] - 2026-01-24

### Added
- Portal mounted check - players riding horses, boats, pigs, or other vehicles must dismount before entering portals
- Bed spawn fallback - when exiting pocket islands without a stored return position, players teleport to their bed spawn before falling back to world spawn
- Always Welcome invitations - island owners can mark guests as "always welcome" to allow visits even when offline (requires `enableAlwaysWelcome` config)

### Fixed
- Island-hopping return position - traveling between pocket islands no longer overwrites the original overworld return position

## [0.4.3] - 2026-01-23

### Added
- Void ejection safety system - players falling below Y=0 in pocket islands are automatically teleported back to their return position before taking void damage

### Removed
- `/pi go <player>` command - players must now use physical portals to visit other islands

## [0.4.2] - 2025-01-13

### Fixed
- Compatibility with Xaero's Minimap/World Map mods
  - Removed bundled `fabric-permissions-api` (was compiled against MC 1.21.3, causing crashes on 1.20.x)
  - Permission API now optional: install LuckPerms for permission node support, otherwise falls back to OP levels

## [0.4.1] - 2025-01-13

### Added
- Visit access control system for personal islands
  - Visitors cannot enter when host is offline
  - Configurable `allowVisitWhenHostNotHome` option (default: false - host must be on their island)
  - Admins (OP level 2+) bypass all visit restrictions
  - Host notifications when visitors are denied access

### Fixed
- UUID parsing in dimension ownership check (dashless UUIDs now handled correctly)

## [0.4.0] - 2025-01-13

### Added
- Multi-version support using Stonecutter (1.20.1 and 1.20.4 from single codebase)
- Minecraft 1.20.1 compatibility

### Changed
- Build system converted from Groovy to Kotlin DSL
- GitHub Actions updated for multi-version matrix builds

### Fixed
- Dimension time no longer resets to noon when entering; now syncs with overworld time

## [0.3.0] - 2025-01-11

### Added
- Configurable portal colors per portal type (`portalColor` property)
- `PortalColor` enum with RED and CYAN variants (extensible for future colors)
- Color caching in ModBlocks for performance

### Changed
- Portal block now has `COLOR` state property alongside `AXIS`
- Model files restructured: separate models per color/axis combination
- Blockstate JSON updated to handle (axis, color) variant combinations

### Removed
- Deprecated configuration fields (frameBlock, activationItem, message fields, worldType fields)
- Old portal model files replaced with color-specific variants

## [0.2.0] - 2025-01-10

### Changed
- **Rebrand**: PersonalWorlds → Pocket Islands
- Refactored command system - extracted command executors from monolithic ModCommands class
- Commands now use `/pi` prefix instead of `/pw`

### Added
- Localization system with language file support
- Mod branding and custom portal textures
- Comprehensive unit test suite

### Fixed
- `/pi leave` command now correctly respects stored return position

## [0.1.0] - 2025-01-08

### Added
- Personal dimension creation via portal system
- Void world generation with starter platform
- Invitation system for visiting other players' dimensions
- Return portal for leaving personal dimensions
- Dimension persistence through world resets
- Admin commands for dimension management
- Crash recovery for players in personal dimensions
- Concurrent portal access protection
- Configuration file support
- Dimension registry recovery mechanism

### Technical
- Fantasy library integration for runtime dimensions
- NBT-based persistent state for dimension registry
- Safe spawn finding with fallback strategies
- Performance monitoring utilities
- Data validation for all persistent storage
