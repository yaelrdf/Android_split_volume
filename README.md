# Split Volume

[![CI](https://github.com/yaelrdf/split-volume/actions/workflows/ci.yml/badge.svg)](https://github.com/yaelrdf/split-volume/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84)

Separate **L** and **R** volume for one pair of Bluetooth earbuds, so two people can share them at different
loudness. One person wears L, the other wears R, and each side gets its own level.

- Works for **every app**: music, video, games, system sounds.
- Turns on **only for the earbuds you choose**, and turns itself off when they disconnect.
- **Remembers** the last L/R levels for that pair.
- **Persistent notification** with L −/+ and R −/+ buttons. Tap it for real sliders.
- **Volume keys** move both sides together and keep the gap between them.
- **No root, no Shizuku, no adb** needed to use it.

> **Status:** working on an OPPO CPH2531 (ColorOS 15 / Android 15). Other phones are untested, and
> [device reports](https://github.com/yaelrdf/split-volume/issues/new?template=device_report.yml) are welcome.

## How it works (short version)

Android has a built-in **master balance** stage in its audio mixer (AudioFlinger). It applies to every app,
after all effects and after the system **Mono audio** downmix. Split Volume:

1. sets the normal **media volume** from the louder side's slider, and
2. turns the quieter side down with the **master balance**. It sets the balance through a hidden Android call
   that only needs a normal permission.

**Mono audio** matters when each person wears one earbud. Without it, each of you hears only half of the stereo
mix. Android doesn't let apps switch mono on, so you enable it once in system settings (see setup).

The full story, with the evidence for each design decision, is in [docs/audio-pipeline.md](docs/audio-pipeline.md).

## Install

There are no published releases yet, so build it yourself:

```bash
./gradlew assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. It's signed with the debug key so it installs without
extra setup. Copy it to the phone and open it, allowing "install unknown apps". Or install over adb:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Build requirements are in [CONTRIBUTING.md](CONTRIBUTING.md#development-setup).

## One-time setup on the phone

1. Open **Split Volume** and work through the **Setup** card:
   - allow **Bluetooth** and **notifications**,
   - **Choose** your earbuds (or pick them **From paired list**),
   - allow **running in the background**.
2. Turn on **Mono audio**: *Settings → Accessibility → Hearing → Mono audio* (*Audio en mono* in Spanish).
   The Setup card has a button that opens Accessibility. Leave it on. It makes no audible difference on the
   phone speaker.
3. **ColorOS / other aggressive OEMs:** *App info → Battery* → allow **Auto launch** and **Background activity**,
   and lock the app in Recents. Otherwise the OS may kill it.

## Using it

- Connect the earbuds. A notification appears with **L −/+** and **R −/+** (5 % steps). Expand it for level
  bars and **Swap L ⇄ R**, or tap it for full sliders.
- **Volume keys** move both sides together.
- **Ping L / Ping R** in the app plays a short beep. With Mono audio on, it plays in both earbuds, and the
  quieter one is the side you turned down.
- Disconnecting the earbuds restores the phone's original balance.

## Limitations

- Balance is **phone-wide**, not per app. That's fine because it only runs while your earbuds are connected.
- **Mono can't be automated.** Android only accepts `setMasterMono` from system processes.
- Phone calls (SCO) and hardware-offloaded "hi-fi" playback may skip the balance stage on some phones.
- An **equalizer** isn't built yet. See [docs/roadmap.md](docs/roadmap.md).
- It relies on a hidden Android API. A future Android or OEM update could block it, and the app shows a Setup
  warning if that happens.

## Documentation

| Document | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Components, data flow, lifecycle, persistence |
| [docs/audio-pipeline.md](docs/audio-pipeline.md) | Why balance + system mono, what was tried and failed, measured curve |
| [docs/debugging.md](docs/debugging.md) | adb, logcat, dumpsys, the debug-only diagnostic receiver |
| [docs/device-compatibility.md](docs/device-compatibility.md) | Tested phones and how to report yours |
| [docs/roadmap.md](docs/roadmap.md) | Planned work (EQ, etc.) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Dev setup, conventions, PR checklist |
| [AGENTS.md](AGENTS.md) | Onboarding notes for AI coding agents |
| [CHANGELOG.md](CHANGELOG.md) | Release history |

## License

[MIT](LICENSE)
