---
title: "Implementation Plan: Upgrade Sandestin to v0.4.0"
parent: "Upgrade Sandestin to v0.4.0"
---

# Implementation Plan: Upgrade Sandestin to v0.4.0

## Prerequisites

- [ ] Confirm v0.4.0 git sha: `7d29c81`

## Phase 1: Update Dependencies

### Tasks

- [x] Update `:dev` alias in deps.edn (commit: ce2a778)
- [x] Update `:test` alias in deps.edn (commit: ce2a778)

## Phase 2: Update Documentation

### Tasks

- [x] Update README.md installation instructions (commit: ce2a778)

## Phase 3: Verification

### Tasks

- [x] Run tests to verify compatibility (commit: ce2a778)

## Phase 4: Optional Updates (Skipped)

### Tasks

- [ ] Update .claude/skills/fx-explore/SKILL.md if needed
- [ ] Update .claude/skills/fx-registry/SKILL.md if needed

**Note:** Skills file updates were deemed optional and skipped.

## Verification Command

```bash
clj -X:test
```

All existing tests should pass after the upgrade.
