# 001: Upgrade Sandestin to v0.4.0

**Status:** Complete

## Summary

Upgrade the sandestin dependency from v0.3.0 to v0.4.0.

## Current State

sandestin v0.3.0 is declared in two places in `deps.edn`:
- `:dev` alias (line 11)
- `:test` alias (line 25)

Additionally, the README.md references v0.3.0 in the installation instructions.

## Target State

Update all references to use:
```clojure
io.github.brianium/sandestin {:git/tag "v0.4.0" :git/sha "7d29c81"}
```

## Tasks

- [x] Update `:dev` alias in deps.edn
- [x] Update `:test` alias in deps.edn
- [x] Update README.md installation instructions
- [x] Run tests to verify compatibility
- [ ] Update any .claude skills files if needed (skipped - optional)

## Files to Modify

| File | Change |
|------|--------|
| deps.edn | Update sandestin coordinates in :dev and :test aliases |
| README.md | Update installation example |
| .claude/skills/fx-explore/SKILL.md | Update version references (optional) |
| .claude/skills/fx-registry/SKILL.md | Update version references (optional) |

## Verification

```bash
clj -X:test
```

All existing tests should pass after the upgrade.
