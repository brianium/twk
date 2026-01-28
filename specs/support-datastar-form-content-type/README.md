---
title: "Support Datastar Form Content Type"
status: completed
date: 2026-01-27
priority: 10
---

# Support Datastar Form Content Type

## Overview

Enable TWK to handle Datastar requests that use `{contentType: 'form'}` without throwing JSON parse errors. This allows using native HTML form patterns with Datastar where form state lives in the DOM rather than in signals.

## Goals

- Handle `application/x-www-form-urlencoded` and `multipart/form-data` requests gracefully
- Allow standard Ring form params to flow through for form-based requests
- Maintain backwards compatibility with existing JSON signal parsing

## Non-Goals

- Automatically parsing form params into a signals-like structure
- Adding special handling for multipart file uploads beyond content-type detection

## Key Decisions

See [research.md](research.md) for full analysis.

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Detection method | Content-Type header check | Aligns with Datastar's behavior (no signals sent with form content type) |
| Signals value for form requests | `nil` | Matches Datastar's documented behavior |

## Implementation Status

See [implementation-plan.md](implementation-plan.md) for detailed task breakdown.

- [x] Phase 1: Core implementation (commit: e8c28a8)
- [x] Phase 2: Testing (commit: e8c28a8)
- [x] Phase 3: Documentation (commit: e8c28a8)
