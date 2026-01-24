---
description: Release a new version with semantic versioning (major.minor.patch)
args:
  - name: type
    description: Release type - patch (default), minor, or major
    required: false
---

You are executing a release workflow for Pocket Islands. This command updates the version, changelog, commits the changes, and creates a git tag.

**Release Type**: `{{type}}` (defaults to `patch` if empty or not specified)

## Execution Steps

### 1. Determine Release Type

Parse the release type from `{{type}}`:
- If empty, blank, or not specified: use `patch`
- Valid values: `patch`, `minor`, `major`
- If invalid value provided: abort with error message

### 2. Read Current Version

Read `gradle.properties` and extract the current version from the `mod_version` line.

Expected format: `mod_version=X.Y.Z` (e.g., `mod_version=0.4.3`)

Parse into components:
- MAJOR = X
- MINOR = Y
- PATCH = Z

### 3. Calculate New Version

Apply semantic versioning rules based on release type:

| Type | Operation | Example |
|------|-----------|---------|
| `patch` | Increment PATCH | 0.4.3 → 0.4.4 |
| `minor` | Increment MINOR, reset PATCH to 0 | 0.4.3 → 0.5.0 |
| `major` | Increment MAJOR, reset MINOR and PATCH to 0 | 0.4.3 → 1.0.0 |

### 4. Update gradle.properties

Replace the `mod_version` line with the new version:
```
mod_version=X.Y.Z
```

### 5. Update CHANGELOG.md

Transform the `## [Unreleased]` section:

**Before:**
```markdown
## [Unreleased]

### Added
- New feature description

### Fixed
- Bug fix description
```

**After:**
```markdown
## [Unreleased]

## [X.Y.Z] - YYYY-MM-DD

### Added
- New feature description

### Fixed
- Bug fix description
```

Rules:
- Insert the new version header immediately after `## [Unreleased]`
- Add one blank line between `## [Unreleased]` and the new version header
- Use today's date in `YYYY-MM-DD` format
- Preserve all content that was under `[Unreleased]`
- The `[Unreleased]` section remains but becomes empty (ready for next development cycle)

### 6. Stage and Commit

Stage the modified files:
```bash
git add gradle.properties CHANGELOG.md
```

Create commit with message:
```
Chore: Prepare release X.Y.Z
```

### 7. Create Git Tag

Create an annotated tag:
```bash
git tag vX.Y.Z
```

**Do NOT push** - the user will push manually when ready.

## Output

After successful completion, display:

```
Release X.Y.Z prepared successfully!

Updated files:
  - gradle.properties (mod_version=X.Y.Z)
  - CHANGELOG.md ([Unreleased] → [X.Y.Z] - YYYY-MM-DD)

Git status:
  - Commit: "Chore: Prepare release X.Y.Z"
  - Tag: vX.Y.Z

Next steps:
  git push origin main --tags
```

## Error Handling

- **Invalid release type**: Display valid options and abort
- **Version parse failure**: Display current gradle.properties content and abort
- **Git operations fail**: Display error and suggest manual resolution
- **Files not found**: Display expected paths and abort
