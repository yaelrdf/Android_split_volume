# Device compatibility

Split Volume depends on OEM audio behaviour, so every phone tested is worth recording here. To add one, open a
[device report](https://github.com/yaelrdf/split-volume/issues/new?template=device_report.yml) or send a PR
editing this table.

| Phone | OS | SoC | Balance | Mono (system switch) | Notes |
|---|---|---|---|---|---|
| OPPO CPH2531 | ColorOS 15 / Android 15 | MediaTek | ✅ | ✅ Accessibility → Hearing → Mono audio | A2DP is a `MIXER` thread; volume range 0–160; session-0 `DynamicsProcessing` broken in the vendor HAL |

## What to include in a report

Run these with the earbuds connected and something playing:

```bash
adb shell getprop ro.product.model
```

```bash
adb shell getprop ro.build.display.id
```

```bash
adb shell dumpsys media.audio_flinger | grep -E "^Output thread|Output devices|AudioStreamOut|Master mono|Master balance"
```

```bash
adb logcat -d | grep -E "SystemBalance|SplitVolumeService|AudioPolicyService|AudioSystem-JNI"
```

Then say whether moving the sliders changes the loudness of each earbud, and where the phone's Mono audio switch
lives.

## Requirements

- Android 13 (API 33) or newer.
- A2DP output through a mixer thread. Phones that offload Bluetooth audio to a DSP may ignore master balance.
