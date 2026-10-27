# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-20

### Added
- Independent **L** and **R** volume for one chosen pair of Bluetooth earbuds, for all apps, using Android's
  master balance (no root, Shizuku or adb needed).
- Media volume follows the louder side; the quieter side is attenuated using the measured AudioFlinger curve.
- Starts automatically when the chosen earbuds connect, and restores the original balance on disconnect (also
  after the app was killed).
- Persistent notification with L −/+, R −/+ and Swap L ⇄ R; quick-controls dialog with sliders.
- Volume keys move both sides together and keep their ratio.
- Levels remembered per device.
- Setup card: permissions, device choice (companion device or paired list), system Mono audio status with a
  shortcut to Accessibility, background-running guidance.
- Ping L / Ping R test beep.
- Debug-only `DiagReceiver` for driving audio calls from adb.

### Removed
- Shizuku-based implementation (needed restarting after every reboot).
- App-controlled mono (Android blocks `setMasterMono` for apps) and the session-0 audio-effect fallback (broken
  in the reference phone's vendor HAL).

[Unreleased]: https://github.com/yaelrdf/split-volume/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/yaelrdf/split-volume/releases/tag/v1.0.0
