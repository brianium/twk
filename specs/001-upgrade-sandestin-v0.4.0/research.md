---
title: "Research: Upgrade Sandestin to v0.4.0"
parent: "Upgrade Sandestin to v0.4.0"
---

# Research: Upgrade Sandestin to v0.4.0

## Problem Statement

The project uses sandestin v0.3.0, which needs to be upgraded to v0.4.0.

## Requirements

### Functional Requirements

- All sandestin dependency declarations must use v0.4.0
- Documentation must reflect the updated version
- Existing functionality must continue to work

### Non-Functional Requirements

- Tests must pass after upgrade

## Current State Analysis

sandestin v0.3.0 was declared in two places in `deps.edn`:
- `:dev` alias (line 11)
- `:test` alias (line 25)

Additionally, the README.md referenced v0.3.0 in the installation instructions.

## Target State

Update all references to use:
```clojure
io.github.brianium/sandestin {:git/tag "v0.4.0" :git/sha "7d29c81"}
```

## Files Affected

| File | Change |
|------|--------|
| deps.edn | Update sandestin coordinates in :dev and :test aliases |
| README.md | Update installation example |
| .claude/skills/fx-explore/SKILL.md | Update version references (optional) |
| .claude/skills/fx-registry/SKILL.md | Update version references (optional) |

## Recommendation

Direct in-place upgrade of all dependency declarations and documentation references.
