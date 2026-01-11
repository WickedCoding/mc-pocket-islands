# Changelog

All notable changes to Pocket Islands will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
