# Audio pipeline: what works, what doesn't, and why

This document records the investigation behind the current design, so nobody has to repeat it. Every claim marked
**verified** was observed on an OPPO CPH2531 (ColorOS 15, Android 15, MediaTek) over adb on 2026-10-20.

## The requirement

Two people share one pair of Bluetooth earbuds, one earbud each. They need:

1. **Mono first.** Each earbud must play the whole mix. Otherwise each person misses whatever is panned to the
   other side.
2. **Then a different level per side.**

The order matters. If per-side attenuation happens *before* the mono downmix, the downmix averages the two sides
back together and the difference disappears.

## Android's playback path (simplified)

```
app track ─┐
app track ─┼─► mixer ─► effect chains (incl. session 0) ─► mono blend ─► master balance ─► HAL ─► A2DP
app track ─┘                                                 (system)      (system)
```

AudioFlinger's playback thread applies mono blend after the effect chains, and balance after mono. That makes
**system mono + master balance** the only pair that does the right thing for every app.

## Option 1: system master balance ✅ (used)

- The hidden `AudioSystem.setMasterBalance(float)` goes to `AudioFlinger::setMasterBalance`, which only checks
  `MODIFY_AUDIO_SETTINGS`. That's a normal permission, granted at install.
- The hidden-API block is bypassed with `org.lsposed.hiddenapibypass`.
- **Verified:** returns status 0, and `dumpsys media.audio_flinger` shows the value on the A2DP output thread:

  ```
  Output thread …, name AudioOut_17D, type 0 (MIXER):
    Output devices: 0x80 (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP)
    Master balance: 0.500000 (balance 0.5 channelCount 2 volumes: 0.291667 1)
  ```
- Not persisted: a reboot or an audioserver restart falls back to the system setting (normally 0).

### Measured balance curve

AudioFlinger doesn't attenuate linearly. **Verified** points (quiet-side volume vs balance):

| balance | quiet-side volume |
|---|---|
| 0.2 | 0.666667 |
| 0.5 | 0.291667 |
| 0.8 | 0.066667 |

These fit `gain = (x + 5x²) / 6` exactly, with `x = 1 − |balance|`. `BalanceMapper.balanceForGain()` inverts it,
and `BalanceMapperTest.balanceMatchesMeasuredAudioFlingerCurve` pins it. A different Android build could use a
different curve. If one does, re-measure (see [debugging.md](debugging.md#measuring-the-balance-curve)).

## Option 2: setting mono from the app ❌ (blocked everywhere)

- `AudioSystem.setMasterMono(boolean)` goes to `AudioPolicyService`, which restricts that transaction to system
  UIDs. **Verified:**

  ```
  AudioPolicyService: onTransact: transaction 68 received from PID 12607 unauthorized UID 10418
  AudioSystem-JNI: Command failed for android_media_AudioSystem_setMasterMono: -38
  ```
- This is AOSP behaviour, not an OEM quirk, so no app can set mono.
- The persisted setting `Settings.System.master_mono` is a hidden *System* setting. `SettingsProvider` rejects
  writes to it from non-system apps that target API 23+, even with `WRITE_SECURE_SETTINGS`.
- **Solution:** the user turns on the system switch once. ColorOS has it under **Accessibility → Hearing →
  Mono audio** (the Settings key is `toggle_master_mono` in `accessibility_hearing_tab_settings.xml`). The same
  page also has a "Channel volume balancing" slider, `seekbar_master_balance`. The app reads mono state through
  the hidden `AudioSystem.getMasterMono()`, which works.

## Option 3: global audio effect ❌ (broken on the test phone, removed)

- Public API: a `DynamicsProcessing` effect on session 0 (the output mix) with per-channel input gain.
- Even when it works, it runs *before* mono blend, so it's useless when sharing with mono on.
- **Verified broken:** AudioFlinger attaches it to the A2DP thread, but the vendor AIDL effect HAL drops every
  parameter:

  ```
  AudioFlinger: createEffect_l() thread 0xb400007c3fab10c0 effect DynamicsProcessing on session 0
  AHAL_DynamicsProcessingLibEffects: setParameterSpecific: line277 return with expr !mContext
  ```

## Option 4: Shizuku + `settings put` ❌ (rejected by design)

An earlier version wrote `master_mono` / `master_balance` with `settings put` through Shizuku (shell UID). It
works technically, and it's the only way to automate mono. It was rejected because Shizuku must be re-started
after every reboot without root, which is maintenance the users didn't want.

If automated mono ever becomes a must-have, an *optional* Shizuku path is the only known route. See
[roadmap.md](roadmap.md).

## Other notes

- **Media volume** on ColorOS 15 has the range 0–160 (`getStreamMaxVolume`). `setStreamVolume` from the app works.
  **Verified:** 80 → 128 when the louder side went from 50 % to 80 %.
- The A2DP output on the test phone is a normal `MIXER` thread with `AUDIO_OUTPUT_FLAG_NONE`, not offloaded. On
  phones where Bluetooth audio is **offloaded** to a DSP, master balance/mono may not apply. That's the first
  thing to check if a device report says "nothing changes".
- Phone calls use SCO/HFP, which can bypass these stages.
