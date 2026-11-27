# Android volume control specification

## Goal

Allow eligible remote volume-up/down presses to adjust Android's media (`STREAM_MUSIC`) volume rather than changing the TV's own volume, when both the key event and the stream-volume operation are supported by that TV box's OS, firmware, and audio route.

This feature cannot promise that every device exposes adjustable Android output volume. In particular, AUX/optical/SPDIF passthrough, HDMI/ARC/eARC, a fixed-volume Android policy, an OEM mixer, and soundbar gain/CEC behavior can vary. Success for a fixed-gain soundbar setup must be demonstrated by listening/measurement with that soundbar's gain unchanged on the target device.

## Repository baseline before implementation

- `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt` receives eligible key events through `AccessibilityService.onKeyEvent` and currently returns early for keys rejected by `KeySignature.isLearnable()`.
- `app/src/main/java/com/androidkumanda/KeyRouter.kt` handles physical down/repeat/up pairing and dispatches a once-per-press action.
- `app/src/main/java/com/androidkumanda/BindingStore.kt` persists local settings in private `SharedPreferences`; it currently has no volume preference.
- `app/src/main/java/com/androidkumanda/MainActivity.kt` is the TV-focused platform-Views settings/help screen and already explains service state and device compatibility.
- `app/src/main/res/xml/accessibility_service_config.xml` requests key-event filtering, without window-content access.
- `app/src/test/java/com/androidkumanda/KeyRouterTest.kt` and `AndroidIntegrationTest.kt` provide JUnit 4.13.2/Robolectric 4.18.1 examples. At planning time, `app/build.gradle.kts` targeted API 36 and had minSdk 21; the implemented project now targets API 37 and retains minSdk 21, with no production third-party dependency.

## User experience and behavior

1. The user opens a distinct **Android media volume** option, learns/chooses the remote's volume-up and volume-down buttons, and explicitly enables this behavior. The setup page distinguishes Android media volume from TV/CEC volume.
2. On a delivered volume-up/down event, the service makes an explicit `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE|ADJUST_LOWER, ...)` request. It does not use a suggested/current stream because that can select a different stream.
3. A volume key is never treated as an ordinary app-launch mapping. Existing reserved-key safeguards remain in place for all unrelated uses; the volume feature handles only its explicitly enabled volume keys.
4. The UI checks `AudioManager.isVolumeFixed()` before offering control. If fixed, it explains that Android reports a fixed-volume policy and leaves the original event unconsumed. If a request produces no observable media-stream index change, report "Android did not change media volume" rather than claiming the route is fixed.
5. On API 23+, connected output device types may be displayed as diagnostic hints using `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` and `AudioDeviceInfo`. They are not represented as proof of the active route for a particular media stream. On API 21-22, route type is unavailable through `AudioDeviceInfo`.
6. The user follows a real-device test: set the soundbar's own gain to a chosen fixed value; play content; adjust the Android media stream with remote keys; repeat with the intended AUX/optical/HDMI route and at least two apps. The app records a local test outcome only if the user explicitly saves it; no audio is recorded.

The product changes Android media-stream volume, not an external device's physical gain, TV volume, HDMI-CEC system-audio setting, or a particular app's private volume. Android's system audio policy and each output path determine whether the request changes sound heard from a connected soundbar.

## Platform and product constraints

- `AudioManager.isVolumeFixed()` reports whether the device implements a fixed-volume policy. When true, app behavior must not imply that stream volume can be changed.
- `adjustStreamVolume` is the explicit stream-scoped API; prefer `STREAM_MUSIC`, not `adjustVolume` or `adjustSuggestedStreamVolume`. The API request is not proof of audible output change.
- `AudioDeviceInfo` is available from API 23. Types include `TYPE_AUX_LINE`, `TYPE_LINE_ANALOG`, `TYPE_LINE_DIGITAL`, `TYPE_HDMI`, `TYPE_HDMI_ARC`, and `TYPE_HDMI_EARC`. A connected-device inventory does not guarantee which route a media player currently uses or whether downstream hardware changes gain.
- HDMI-CEC System Audio Control is mediated by platform services, optional in implementations, and variable across vendors. AOSP documents CEC service access as protected to system components/privileged apps. This APK must not attempt hidden/privileged CEC access.
- AUX/optical digital passthrough may bypass the gain stage controlled by Android, or may respond to a software stream-volume change. The behavior is device/firmware/audio-path dependent and must be measured on each declared compatible setup.
- Key capture has the same AccessibilityService/OS/OEM/other-service limitations as the remapping feature. The app can only act if the physical key event reaches `onKeyEvent`.
- Keep minSdk 21 unless a separately approved product decision changes it. Guard API 23 `AudioDeviceInfo` diagnostics on older devices; do not raise minSdk just to report routes.
- Volume changes must be bounded by Android's own stream policy. Do not emulate a different volume range, persist or restore volume behind the user's back, or write privileged settings.
- Public distribution must satisfy current Android accessibility-service purpose and store-policy requirements. The service must stay transparent, user-enabled, local, and free of window-content inspection.

## Acceptance criteria

- A TV owner can enable Android media-volume mode, map two eligible volume keys, and understand that this controls Android's media stream rather than guaranteeing TV/soundbar volume control.
- For a delivered UP event, a single press changes only `STREAM_MUSIC` by one framework step; repeat DOWN events may request additional one-step adjustments without generating duplicate long-press bursts beyond the hardware repeat stream; UP does not adjust volume.
- Disabled mode, unmapped keys, cancellation, fixed-volume policy, unavailable service, failed/no-change API request, and unrecognized event pairing do not falsely consume a key or claim success.
- Turning the feature off restores normal event pass-through immediately. Existing application mappings and preferences are preserved.
- No service, platform, or diagnostic output claims that AUX, optical, SPDIF, HDMI, ARC/eARC, or a soundbar is controllable without the matching hardware smoke test.
- Real-device acceptance includes a fixed soundbar gain test proving audible output changes via Android media volume, plus a pass-through test when Android reports fixed volume or the output ignores the request.
- Diagnostics identify API-reported fixed volume and connected output type when available, with clear wording that connected-device information does not identify the active playback route.
- Logs and saved results do not contain audio samples, key sequences, foreground-app histories, or network telemetry.

## Compatibility matrix to complete on devices

| Dimension | Cases to test | Required interpretation |
|---|---|---|
| Android | API 21-22; API 23-32; API 33; API 34-37; later supported OS releases | Check service permission, key delivery, `isVolumeFixed`, stream API behavior, and diagnostic availability. `AudioDeviceInfo` diagnostics start at API 23. |
| Remote/key | IR, Bluetooth, USB, integrated remote; volume up/down, long hold/repeat, mute, power, CEC passthrough | Record whether `onKeyEvent` receives down/repeat/up. Mute is not implicitly mapped to volume; unmapped input passes through. |
| Android policy | `isVolumeFixed() == true`; false; API call no index change; audible index change | Fixed or no-change must not claim success. Index change is necessary but not sufficient proof of audible route change. |
| Output | Built-in speaker; AUX/analog line; optical/SPDIF (`TYPE_LINE_DIGITAL` where exposed); HDMI; HDMI ARC/eARC; Bluetooth; USB | For each route, test multiple media apps, route reconnects, and target soundbar gain held constant. API type is a hint, not proof of active track route. |
| Device/OEM | At least one Android TV and one TV box for each claimed family; OEM audio processing/volume overlay; CEC on/off where configurable | Record vendor/model, Android build, connected devices, fixed-volume result, soundbar model/input/gain, and audible outcome. Do not extrapolate between OEMs. |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Remote volume event is consumed by TV/firmware before service | Test event receipt first; show a clear "Android did not receive this key" result; do not promise control. |
| `AudioManager` adjusts another stream or route | Always specify `STREAM_MUSIC`; compare media index before/after; validate actual sound with user-selected playback apps. |
| Device implements fixed volume or ignores adjustment | Check `isVolumeFixed`; pass event through when fixed; report no observable index change without guessing why. |
| AUX/optical/HDMI output bypasses Android gain | Require fixed-soundbar-gain listening test per model/path; label route as unsupported/unverified until evidenced. |
| CEC changes TV/receiver volume unexpectedly | Do not access CEC; document CEC TV controls as a separate path and ask tester to observe both Android and external-device OSD/volume. |
| Key repeats cause rapid jumps or short presses double-adjust | Test initial DOWN, each repeat, matching UP, cancellation, and vendor repeat cadence; define adjustments from the actual event stream only. |
| Volume and app remapping both handle a key | A single shared dispatch priority: enabled volume binding is resolved before general app mapping; reject overlapping bindings and test no double action. |
| User confuses Android volume with soundbar gain | Separate setting label, status, onboarding copy, and test checklist; never say "soundbar volume" without device evidence. |

## Out of scope

- TV volume, external soundbar gain, CEC commands, private system APIs, root, privileged app install, or per-device volume injection.
- Audio capture, signal processing, app-specific internal volume controls, or route forcing.
- Changing arbitrary key codes or the key-remapping feature's persistence/UI design, which has its own specification at `docs/superpowers/specs/2026-11-27-remote-key-remapping.md`.

## Official Android references

- [`AudioManager`](https://developer.android.com/reference/android/media/AudioManager) — stream volume APIs and `isVolumeFixed()`.
- [`AudioDeviceInfo`](https://developer.android.com/reference/android/media/AudioDeviceInfo) — output type constants and API level.
- [`AccessibilityServiceInfo`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) and [`AccessibilityService.onKeyEvent(KeyEvent)`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)) — key-filter request and delivered-event limitations.
- [Android HDMI-CEC control service (AOSP)](https://source.android.com/docs/devices/tv/hdmi-cec) — optional/variable CEC behavior and privileged access.
