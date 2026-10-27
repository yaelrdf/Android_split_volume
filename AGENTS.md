# Notes for AI coding agents

Read this first; it's short. The human docs are [README.md](README.md), [CONTRIBUTING.md](CONTRIBUTING.md) and
[docs/](docs/).

## What this is

An Android app (Kotlin, Compose, single module `:app`, package `dev.yaelrdf.splitvolume`) that gives one pair of
shared Bluetooth earbuds independent **L** and **R** volume. It sets the media volume from the louder side and
turns the quieter side down with Android's **master balance**. It calls the balance through the hidden
`AudioSystem.setMasterBalance` via `org.lsposed.hiddenapibypass`.

## Hard constraints (from the owner; don't violate them)

1. **UI labels are "L" and "R"**, matching the earbud markings. Never "Me/Her/You" or names.
2. **No Shizuku, root or adb required for normal use.** Don't reintroduce Shizuku as a requirement.
3. **Never make permanent changes to a connected phone** while debugging. It's the owner's daily phone, and the
   app also goes on their partner's phone. Read-only `dumpsys`/`logcat` are fine. In-memory audio calls (balance via
   the debug receiver) are fine **if you put them back**. `adb shell settings put`, `pm grant`, toggling system
   settings, or uninstalling are **not** OK without asking.
4. **The equalizer is deferred.** Don't build it unless asked.

## Facts already established (don't re-investigate)

See [docs/audio-pipeline.md](docs/audio-pipeline.md) for the evidence.

- `setMasterBalance` from the app **works**; it applies after effects and after system mono.
- `setMasterMono` from an app is **impossible** (AudioPolicyService rejects non-system UIDs). The user enables
  *Accessibility → Hearing → Mono audio* manually; the app only reads it (`getMasterMono`).
- Session-0 `DynamicsProcessing` is **broken** on the reference phone's vendor HAL. Effects also can't do
  per-side levels with mono on. Don't retry this approach.
- AudioFlinger balance curve: `gain = (x + 5x²)/6`, `x = 1 − |balance|` (measured; pinned in `BalanceMapperTest`).
- Reference phone: OPPO CPH2531, ColorOS 15 / Android 15, MediaTek, STREAM_MUSIC range 0–160.

## Architecture in one paragraph

`BluetoothReceiver` starts `SplitVolumeService` (foreground, `connectedDevice`) when the chosen earbuds connect.
The service waits for the A2DP route and calls `SplitController.activate()`, which saves the original balance to
DataStore and applies levels. Then it collects `ProfileStore.profile` and re-applies every change. UI screens
and notification buttons **only edit the stored profile**; they never call audio APIs. On disconnect the service
calls `restore()`. The full file map is in [docs/architecture.md](docs/architecture.md).

## Commands

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

- No system JDK on the original dev machine. Use Android Studio's JBR:
  `export JAVA_HOME=$(find /var/lib/flatpak/app/com.google.AndroidStudio -maxdepth 6 -name jbr | head -1)`.
- `adb` lives in `~/Android/Sdk/platform-tools/` (not on `PATH` there).
- Debug builds include `DiagReceiver` for driving audio calls from adb. See
  [docs/debugging.md](docs/debugging.md#the-debug-only-diagnostic-receiver).

## Before you finish a change

- Unit tests pass and lint is clean (or the new warnings are justified).
- Audio-affecting changes are verified on a device via `dumpsys media.audio_flinger`, or you say clearly that
  they aren't.
- Docs updated: `README.md` (user-facing), `docs/` (design), `CHANGELOG.md` (**Unreleased**).
