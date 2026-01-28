# Changelog

## [0.3.0] - 2026-01-28

### Added

- Support for Datastar form content type in `with-signals` middleware. Requests with `application/x-www-form-urlencoded` or `multipart/form-data` content types now pass through without JSON parsing errors.

## [0.2.1] - 2026-01-17

### Changed

- Upgrade sandestin dependency to v0.4.0

### Documentation

- Add Datastar adapter peer dependency documentation

## [0.2.0] - 2026-01-17

### Changed

- Move sandestin to dev/test peer dependency (no longer a hard runtime dependency)
- Update sandestin to v0.3.0

### Added

- GitHub Actions workflow for running tests on PR/push (Java 11, 17, 21)

## [0.1.0] - 2026-01-12

### Added

- Initial release
- `registry` function for Sandestin-compatible Datastar effects and actions
- `with-datastar` middleware for Ring handlers
- Effects: `patch-elements`, `patch-elements-seq`, `patch-signals`, `execute-script`, `close-sse`
- Automatic signal parsing from Datastar requests
- Hiccup-aware SSE event wrappers
- SDK constant re-exports (patch modes, option keys, CDN URLs)
- Custom serializer support via registry options
