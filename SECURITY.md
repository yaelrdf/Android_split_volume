# Security policy

## What the app can do

- It needs no internet permission and sends no data anywhere.
- It changes one piece of phone-wide audio state: AudioFlinger's master balance, plus the media volume. It
  saves the original balance and restores it when the earbuds disconnect. A reboot also clears it.
- It uses a hidden Android API (`AudioSystem.setMasterBalance`) through
  [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass). The only permission involved is
  `MODIFY_AUDIO_SETTINGS`, a normal permission.

## Debug builds

Debug builds include `DiagReceiver`, an **exported** broadcast receiver that any app on the phone could use to
change the master balance or create a global audio effect. It exists for adb troubleshooting only and is **not**
in release builds. Don't distribute debug APKs.

## Reporting a vulnerability

Please use GitHub's **private vulnerability reporting** (Security tab → *Report a vulnerability*) instead of
a public issue. You should get a reply within a week.
