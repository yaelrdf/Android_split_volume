# Contributing

Thanks for helping! This is a small, focused app. Please read this page before opening a PR.

## Development setup

| Tool | Version |
|---|---|
| JDK | 17 or newer. Android Studio's bundled JBR works, and CI uses Temurin 21. |
| Android SDK | Platform 37 (compileSdk), build-tools 36+ |
| Gradle | Via the wrapper (`./gradlew`, Gradle 9.8.1). Don't install it separately. |
| AGP / Kotlin | Pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) |

1. Clone the repo and open it in Android Studio, **or** use the command line.
2. Point Gradle at your SDK: Android Studio writes `local.properties` (`sdk.dir=…`) for you. On the command line,
   set `ANDROID_HOME` or create `local.properties` yourself. It's git-ignored.
3. If `java` isn't on your `PATH`, point `JAVA_HOME` at a JDK. For example, with the Android Studio flatpak on
   Linux:

   ```bash
   export JAVA_HOME=$(find /var/lib/flatpak/app/com.google.AndroidStudio -maxdepth 6 -name jbr | head -1)
   ```

## Everyday commands

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug
```

```bash
./gradlew lintDebug
```

```bash
./gradlew installDebug
```

The debug APK includes the diagnostic receiver described in [docs/debugging.md](docs/debugging.md). Use
`assembleRelease` for an APK to hand to someone.

## Project layout

```
app/src/main/java/dev/yaelrdf/splitvolume/   app code (see docs/architecture.md for a file-by-file map)
app/src/main/java/.../ui/                    Compose screens
app/src/main/res/                            notification layouts, icons, themes
app/src/debug/                               debug-only code (DiagReceiver)
app/src/test/                                JVM unit tests
docs/                                        design notes, debugging, compatibility, roadmap
```

## Conventions

- **Kotlin style:** official Kotlin conventions, 4-space indent, max line length 120 (`.editorconfig`). Match the
  surrounding code: KDoc on classes and non-obvious functions, comments that explain *why*, not *what*.
- **Sides are "L" and "R"** everywhere in the UI, matching the markings on the earbuds. Never label them by
  person ("Me", "Her", "You", …). This is a user requirement.
- **One writer of audio state.** The UI and the notification only edit the stored `Profile`. Only
  `SplitController` (driven by `SplitVolumeService`) calls audio APIs. Keep it that way.
- **Pure logic goes in pure objects** (like `BalanceMapper`) with JVM unit tests. Android-dependent code stays thin.
- **Restore what you change.** Any new audio-state change must be undone on disconnect and survive the app being
  killed. See "If the OS kills the app" in [docs/architecture.md](docs/architecture.md).
- **No new hard dependencies on root or Shizuku.** Optional paths are open for discussion; see
  [docs/roadmap.md](docs/roadmap.md).
- **Debug-only tooling** goes in `app/src/debug/`, never in `main`.

## Testing

- Add or update unit tests in `app/src/test/` for any change to level/balance math.
- Audio behaviour can't be unit-tested meaningfully. For audio changes, **verify on a real phone** and say in
  the PR which phone and how (logcat / `dumpsys media.audio_flinger` output is ideal). The recipes are in
  [docs/debugging.md](docs/debugging.md).
- If you test on someone else's phone, don't leave lasting changes on it (no `settings put`).

## Pull requests

1. Branch off `main`. Keep each PR to one topic.
2. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` locally. CI runs the same.
3. Update docs when behaviour changes: README for user-facing changes, `docs/` for design, and `CHANGELOG.md`
   under **Unreleased**.
4. Fill in the PR template, including the device-testing section.

Commit messages: short imperative subject (≤ 72 chars, e.g. `Restore balance when the service is killed`),
blank line, then the *why* if it isn't obvious.

## Reporting bugs and devices

Use the issue templates. For "doesn't work on my phone", the
[device report](https://github.com/yaelrdf/split-volume/issues/new?template=device_report.yml) asks for exactly
the logs that help.
