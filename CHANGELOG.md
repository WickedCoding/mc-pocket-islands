# Changelog

All notable changes to Pocket Islands will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] - 2026-10-09

### Added
- `/pi unstuck`: a player command that gets you out of a pocket island and clears the pocket island data that decides where you end up (return position, island tracking, portal lock). It sends you to the spot you entered from, your bed, or world spawn. It only works while you are in a pocket island or such data is left over, so it is not a free teleport to spawn.

### Changed
- `/pi leave` no longer requires a permission, so every player can use it. It teleports you to the spot you entered your pocket island from, or to your bed or world spawn if there is none, from anywhere.

### Fixed
- Logging back in after your island had unloaded (more than 30 seconds after logging out on it) said "Restored connection to your pocket dimension" but left you half in the overworld: the server kept a second copy of you there, the island never finished loading for your client, and every relog repeated it. On Fabric the restore ran before the server had finished placing the joining player; it now runs once they are placed.
- Players were sent to world spawn when they entered, left or logged back in to an island that had just been unloaded but was not gone yet. An island that is reopened while it unloads now stays loaded. Affected Fabric 1.20.1 and 1.20.4 (Fantasy before 0.7) and, for one tick, Forge 1.20.1.
- A player found in an island that can no longer stay loaded is moved to their return position, bed or world spawn instead of being ejected by the dimension library.
- "Restored connection to your pocket dimension" is only shown when the teleport back to the island succeeded.
- Recovery teleports no longer use a bed that is inside a pocket island.

## [1.0.0] - 2026-10-05

### Added
- Forge 1.20.1 support. Runtime dimensions come from Infiniverse, which is bundled in the jar along with MixinExtras. Permissions go through Forge's PermissionAPI, so LuckPerms works. See "Loader Differences" in the README for what behaves differently on Forge.
- NeoForge 1.21.11 support (NeoForge 21.11.45 or newer). Infiniverse is bundled in the jar, and permissions go through NeoForge's PermissionAPI, so LuckPerms works. Behaves like the Forge build: the island clock follows the overworld, and `OVERWORLD`/`FLAT` islands use the overworld's seed.
- Modrinth versions per loader: Fabric builds keep `<version>+<mc>`, Forge and NeoForge builds are published as `<version>+<mc>-forge` and `<version>+<mc>-neoforge`.

### Changed
- Release jars carry the loader in their name: `pocketislands-<version>+<mc>-<loader>.jar` (for example `pocketislands-1.0.0+1.20.1-fabric.jar`).
- Chunk sanitizer is now opt-in: `sanitizeChunksOnLoad` and `sanitizeRemoveOrphanBlocks` default to `false`. Earlier versions wrote `true` for both into every new config file, so existing configs are migrated once on startup: both flags are set to `false`, all other settings are kept, and the original file is saved as `pocketislands.json.v0.bak`. A warning in the log says what changed. To keep the sanitizer, set the flags back to `true` after upgrading. `/pi admin sanitize` is unaffected.
- Config files now carry a `configVersion` field. Do not edit it by hand.
- Per-dimension game rules (`dimensionGameRules`) are applied by the mod itself on every loader instead of through Fantasy. Configured values behave as before.

### Fixed
- Dying on an island with `keepInventory` enabled there but disabled in the overworld no longer deletes the inventory. The death kept the items (island rule), but the respawn in the overworld read the overworld rule and did not copy them over, so they vanished. The respawn now uses the rule of the dimension the player died in; dying in the overworld still drops the items.
- `/pi admin list` shows the `[LOADED]` / `[unloaded]` brackets again (the opening bracket went missing when the messages were made translatable).
- Chunk sanitizer no longer crashes the server on chunk load. The 0.7.3 fix deferred the sweep with `MinecraftServer#execute`, but that runs the task immediately when called on the server thread, so the sanitizer still waited on the chunk that was loading and the watchdog killed the server. Loaded chunks are now queued and sanitized at the end of the server tick.
- 1.21.11: entering a portal no longer disconnects the player with "Internal server error". The `/pi` permission check cast the player's permissions to `LeveledPermissionPredicate`, which fails for other `PermissionPredicate` implementations when the command tree is resent after a teleport.

## [0.7.3] - 2026-08-16

### Added
- `/pi admin sanitize <player> [radius]` — force-loads a player's pocket dimension and runs the chunk sanitizer across a chunk radius around spawn (default 4, max 16). Bypasses the `sanitizeChunksOnLoad` / `sanitizeRemoveOrphanBlocks` config gates and sweeps the full 16×16 chunk footprint since all neighbours are force-loaded. Reports per-invocation totals for orphan block entities, unsupported blocks, and malformed inventory stacks removed. Requires permission `pocketislands.admin.sanitize` (OP fallback 3).

### Fixed
- Chunk sanitizer no longer deadlocks the server thread — the orphan-support sweep is now deferred off the chunk-load callback via `MinecraftServer#execute`, and only interior positions (localX/localZ ∈ [1..14]) are checked so `canPlaceAt` neighbour lookups can never cross into an unloaded chunk. Fixes a watchdog crash reproducible by walking a portal in a dimension containing bamboo (or any block whose `canPlaceAt` reads a neighbour block state).

## [0.7.2] - 2026-08-16

### Added
- Chunk sanitizer inventory sweep — malformed inventory stacks (item resolved to AIR but count > 0) inside container block entities are cleared on chunk load, complementing the existing orphan block entity and unsupported block passes

## [0.7.1] - 2026-08-16

### Added
- Chunk sanitizer for pocket dimensions — orphaned block entities and unsupported blocks (e.g., floating fires left after a mod is removed from the modpack) are purged on chunk load, with toggles via `sanitizeChunksOnLoad` and `sanitizeRemoveOrphanBlocks` config

## [0.7.0] - 2026-03-09

### Added
- Configurable dimension game rules — pocket dimensions now inherit all overworld game rules by default, with per-rule overrides via `dimensionGameRules` config (e.g., `keepInventory`, `randomTickSpeed`) (#1)

### Fixed
- Permission check crash on 1.21.11 when teleporting to pocket dimensions (ClassCastException in CommandCompat)

### Changed (BREAKING)
- Translation keys renamed from `personalworlds.*` to `pocketislands.*` — resource packs and custom language files must update key names
- Permission nodes renamed from `personalworlds.admin.*` / `personalworlds.player.*` to `pocketislands.admin.*` / `pocketislands.player.*` — LuckPerms configurations must be updated
- Config file renamed from `config/personalworlds.json` to `config/pocketislands.json` — existing config is auto-migrated on first load
- Mixin config renamed from `personalworlds.mixins.json` to `pocketislands.mixins.json`
- JAR output renamed from `personalworlds-*.jar` to `pocketislands-*.jar`

## [0.6.1] - 2026-02-12

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
