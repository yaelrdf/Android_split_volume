# Roadmap

Ideas and planned work, roughly in priority order. Open an issue before starting a large one.

## Planned

- **Equalizer** *(deferred by the original users: "nice to have, not a must")*.
  - A shared EQ (same curve for both earbuds) could be a session-0 effect, since effects run before mono and
    balance. Note that session-0 `DynamicsProcessing` is broken on the reference phone (see
    [audio-pipeline.md](audio-pipeline.md#option-3-global-audio-effect--broken-on-the-test-phone-removed)), so
    try the `Equalizer` effect first.
  - A **per-side** EQ isn't possible together with system mono, because mono runs after effects. It could only
    work in stereo mode, where each person hears half the mix.
- **GitHub releases**: a signed release APK built by CI, which needs a real signing key kept in repository secrets.
- **Quick Settings tile** showing L/R and toggling the session.

## Maybe

- **Optional Shizuku path** to automate *Mono audio* (write `Settings.System.master_mono` as the shell user).
  It was rejected as the default because of per-reboot maintenance. See
  [audio-pipeline.md](audio-pipeline.md#option-4-shizuku--settings-put--rejected-by-design).
- Per-app exclusions (e.g. keep calls centred). Limited by balance being phone-wide.
- Translations (the reference users speak Spanish). All UI strings are currently hardcoded in Kotlin and would
  need to move to `strings.xml` first.

## Won't do

- Root-only features.
- Anything that permanently changes system settings without the user explicitly doing it.
