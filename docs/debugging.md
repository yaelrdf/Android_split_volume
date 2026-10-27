# Debugging on a real phone

Most bugs in this app only show up on real hardware: OEM audio stacks differ a lot. This page covers how to see
what's actually happening.

> **Ground rule:** don't leave lasting changes on a phone you're debugging, since it may be someone's daily
> driver. Everything below is either read-only or in-memory audio state that the app restores and a reboot
> clears. **Never** use `adb shell settings put …` on a user's phone without asking, because it persists.

## Connect over adb

USB, or wireless debugging (Android 11+, *Developer options → Wireless debugging → Pair device with pairing code*):

```bash
adb pair <ip>:<pairing-port>
```

```bash
adb connect <ip>:<port>
```

```bash
adb devices -l
```

`adb` is in `$ANDROID_HOME/platform-tools/`. It's often not on `PATH`.

## Read-only inspection

**App logs** (tags used by the app):

```bash
adb logcat -s SplitVolumeService BluetoothReceiver ConnectionSync SystemBalance SplitDiag AndroidRuntime
```

**Did the audio server reject a call?**

```bash
adb logcat -d | grep -E "AudioPolicyService|AudioSystem-JNI|AudioFlinger: createEffect|AHAL_"
```

**Master balance / mono on each output thread.** Look for the `BLUETOOTH_A2DP` thread:

```bash
adb shell dumpsys media.audio_flinger | grep -E "^Output thread|Output devices|AudioStreamOut|Master mono|Master balance"
```

The bracketed `volumes: <L> <R>` only updates while audio is playing on that thread (`Standby: no`).

**Media volume per device** (the `bt_a2dp` entry):

```bash
adb shell dumpsys audio | grep -A6 "^- STREAM_MUSIC"
```

**Is the session service running, and what does the notification say?**

```bash
adb shell dumpsys activity services dev.yaelrdf.splitvolume
```

```bash
adb shell dumpsys notification --noredact | grep -A30 "NotificationRecord.*splitvolume" | grep -E "android.title|android.text"
```

**Stored settings** (debug builds only, since `run-as` needs a debuggable app):

```bash
adb shell run-as dev.yaelrdf.splitvolume cat files/datastore/split_volume.preferences_pb | strings
```

## The debug-only diagnostic receiver

Debug builds include `DiagReceiver` (`app/src/debug/`), an **exported** broadcast receiver that runs one audio
call at a time and returns the result. It's **not** in release builds; check with
`aapt dump xmltree app-release.apk AndroidManifest.xml | grep DiagReceiver`.

```bash
adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd get
```

| `cmd` | `arg` | Does |
|---|---|---|
| `get` | – | Reads master mono + master balance |
| `balance` | `0.5` | `setMasterBalance(0.5)` → status (0 = OK) |
| `mono` | `1` / `0` | `setMasterMono(…)` → status (expected to fail for apps) |
| `vol` | *(optional)* `128` | Reads (and optionally sets) STREAM_MUSIC index and range |
| `levels` | `20,80` | Stores L/R levels, the same path as the sliders. A running session applies them. |
| `effect` | `-20,0` | Creates/updates a session-0 `DynamicsProcessing` with per-channel gain in dB |
| `effect_off` | – | Releases that effect |

The result is in the broadcast's `data=` and in logcat under `SplitDiag`. Always put things back afterwards:

```bash
adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd balance --es arg 0
```

```bash
adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd effect_off
```

### End-to-end check of the slider path

With the earbuds connected and the app opened once (so the session runs):

```bash
adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd levels --es arg 20,80
```

Then the A2DP thread should show `Master balance: 0.808452`, and `vol` should report 128 of 0..160. Set
`50,50` afterwards to go back to neutral.

### Measuring the balance curve

If a phone attenuates differently, measure it. For each balance `b`, play audio on the earbuds, set the
balance, and read the quiet-side volume from the A2DP thread:

```bash
adb shell am broadcast -n dev.yaelrdf.splitvolume/.DiagReceiver --es cmd balance --es arg 0.5
```

```bash
adb shell dumpsys media.audio_flinger | awk '/BLUETOOTH_A2DP/{p=1} p && /Master balance/{print; exit}'
```

Then fit the points and update `BalanceMapper.balanceForGain()` and its test.

## Common failures

| Symptom | Likely cause | Check |
|---|---|---|
| Sliders do nothing, Setup shows "This phone doesn't let apps change the audio balance" | Hidden API blocked, or AudioFlinger refused | `SystemBalance` warning in logcat |
| Balance is set but not audible | Bluetooth audio offloaded (not a `MIXER` thread), or nothing playing | `dumpsys media.audio_flinger` A2DP thread type and flags |
| Each person only hears half the music | System Mono audio is off | Setup card, or `cmd get` |
| Session never starts when earbuds connect | OEM killed or froze the app; background start refused | logcat `ActivityManager` "Background started FGS"; battery settings |
| Phone speaker stays lopsided after disconnect | App killed before restore | Open the app once (it restores), or reboot |
