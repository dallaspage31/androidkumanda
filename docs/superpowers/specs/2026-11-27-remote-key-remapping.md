# Remote key remapping specification

## Goal

Let a TV/TV-box owner replace an eligible physical remote key's current app-launch behavior with a chosen installed app, without root or privileged APIs, when Android delivers that key to the app's accessibility key-event filter.

The product must not promise to remap every remote key. A key handled before Android exposes it to the service, consumed by firmware or another system component, protected by the platform, or unavailable on the device cannot be remapped by this APK.

## Repository baseline before implementation

- `app/src/main/AndroidManifest.xml` declares `MainActivity` and `RemoteMapperAccessibilityService`; the latter is system-bound with `BIND_ACCESSIBILITY_SERVICE`.
- `app/src/main/res/xml/accessibility_service_config.xml` enables `canRequestFilterKeyEvents`, requests `flagRequestFilterKeyEvents`, and sets `canRetrieveWindowContent="false"`.
- `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt` requests `FLAG_REQUEST_FILTER_KEY_EVENTS`, handles `onKeyEvent`, learns keys for 15 seconds, and launches a selected app.
- `app/src/main/java/com/androidkumanda/KeyRouter.kt` identifies keys by key code and, when available, scan code; it tracks down/up by device and down time, and launches only once per press.
- `app/src/main/java/com/androidkumanda/BindingStore.kt` stores three fixed `Slot` assignments in private `SharedPreferences` and rejects duplicate or reserved keys.
- `app/src/main/java/com/androidkumanda/MainActivity.kt` builds the TV-focused setup UI in platform Views, exposes learn/choose/test/reset controls, and explains service and firmware limitations.
- `app/src/test/java/com/androidkumanda/KeyRouterTest.kt` tests press/repeat/up handling and reserved keys. `AndroidIntegrationTest.kt` tests persistence, duplicate/reserved keys, and the settings screen.
- At planning time, `app/build.gradle.kts` set `minSdk = 21`, `compileSdk = 36`, and `targetSdk = 36`; tests use JUnit 4.13.2 and Robolectric 4.18.1. The implemented project now targets SDK 37 while retaining minSdk 21.

## User experience and behavior

1. The user explicitly enables the app's accessibility service in Android settings. The app explains why the service is needed, what it observes, and how to turn it off.
2. The user adds a mapping, presses **Learn**, then presses the physical key. Only an event actually received by the service and accepted by the app's allowlist can be learned.
3. The user chooses an installed launchable app, optionally excludes selected foreground apps, and saves. The first release's remap action is app launch; it does not synthesize arbitrary key events.
4. An eligible mapped press requests launch of the chosen app once on the initial DOWN. A held key's repeats do not relaunch it. The matching UP is consumed only when the original press was consumed. Unmapped, excluded, stale-context (when an exclusion depends on it), cancelled, or unsupported events pass through.
5. The user can test the app launch from setup, remove one mapping, clear all mappings, or disable the accessibility service in Settings. The service never attempts to keep itself enabled or restart itself against user/system decisions.
6. If learning times out, the UI says the device may not deliver that key to Android and provides a check list rather than claiming the key is remappable.

The product is a local remote shortcut remapper, not a general system key injector. Re-emitting a different Android `KeyEvent` is out of scope: this app has no supported public path to synthesize arbitrary global key events. Any future action beyond app launch requires its own platform-supported, explicitly bounded contract.

## Platform and product constraints

- Key filtering is an accessibility-service capability requested through `canRequestFilterKeyEvents` and `FLAG_REQUEST_FILTER_KEY_EVENTS`; it is not an unrestricted input hook. The service can act only on events the OS routes to it.
- Android, an app, a TV firmware, IR/CEC hardware, or another accessibility/key-filter service may consume or suppress the key first. Power, home, navigation, volume, input, security, and other system-reserved keys are not guaranteed to be delivered or safe to consume.
- The repository's current `KeySignature.isLearnable()` denies known navigation, typing, volume, power, and other key-code ranges. Any allowlist change must be narrow, reasoned, and tested against the actual target device. A scan code is hardware/driver-specific and is not a portable identifier.
- `AccessibilityService` is a user-enabled system service. OEM background management can stop it; the APK cannot guarantee persistence or silently recover it.
- The app must keep `canRetrieveWindowContent="false"` and must not inspect text, view trees, screenshots, or unrelated accessibility data. If foreground-app exclusions are implemented, observe only `AccessibilityEvent.packageName` from explicitly registered window-state events, and treat unknown/stale foreground state as fail-open/pass-through.
- No synthetic keys are sent, so a remap-to-key loop cannot occur. For the app-launch action, the router consumes a matching DOWN/UP pair only after the launch request is accepted; repeats never relaunch.
- A physical remote may use IR, Bluetooth, USB, vendor firmware, or HDMI-CEC. These are test dimensions, not universal support claims. A physical button that bypasses Android cannot be recovered by `AccessibilityService`.
- Android's accessibility guidance describes accessibility services as services to assist users with disabilities. Before publishing through any store or distribution channel, review its current accessibility-service eligibility and disclosure requirements; sideload availability does not establish store-policy eligibility.

## Acceptance criteria

- A mapping can be added, selected, tested, and removed using D-pad/remote navigation on a real TV/TV box.
- Existing three-slot local assignments are migrated without changing their key signatures or target packages; migration is idempotent and preserves the user's data.
- Duplicate source keys are rejected with a clear UI explanation. A failed launch, unmapped key, excluded app, unknown foreground package, cancellation, or service disconnect does not falsely consume input.
- A held mapped button starts at most one app. The matching UP remains paired to the DOWN even if the mapping is edited during the press; an unrelated device's UP cannot end the press.
- No raw key stream or foreground app history is uploaded or written to logs. Mapping data remains local and is deleted by the relevant reset/clear operation.
- The app never reports a key as supported merely because it was configured. It reports the service state and whether the physical event was observed in the current learning attempt.
- Android/OEM/remote compatibility claims are published only after the device matrix is tested. The supported-device list includes tested model, OS/build, remote transport, key, and outcome.

## Compatibility matrix to complete on devices

| Dimension | Cases to test | Required interpretation |
|---|---|---|
| Android | API 21-22; API 23-32; API 33; API 34-37; later supported OS releases | App minSdk is 21 and targetSdk is 37. Test service enablement, filtering, app launches, and settings recovery for each support band; do not infer OEM behavior from API level alone. |
| Remote transport | IR; Bluetooth; USB; OEM-integrated remote; HDMI-CEC-mediated keys | Record what Android actually delivers. No transport alone proves a key can be intercepted. |
| Key class | Vendor app shortcut; vendor service shortcut; standard navigation; power; input/source; volume; unknown scan-code-only key | Test learning, pass-through, repeat, original OEM action, and reset. Keep reserved/system keys protected unless a device-specific test and platform contract justify otherwise. |
| Foreground app | Home; selected target; excluded app; settings app; unknown/null package | Excluded or unknown foreground context must pass through; no view content may be read. |
| Service state | Enabled; disabled; disconnected/reconnected; stopped by OEM; other key-filter service enabled | Show an actionable status; never promise service survival or bypass the user's choice. |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| OS/OEM does not deliver a button to `onKeyEvent` | Learning timeout and device-specific compatibility results; no universal-key claim. |
| Accessibility settings block enabling or another service owns key filtering | Explain how to inspect settings, disclose service purpose, test with competing services, and provide pass-through when not active. |
| Incorrect event pairing or repeat storms | Pure router tests for DOWN/repeat/UP/cancel/device/down-time behavior; one launch per press; bounded held-state map. |
| App exclusion uses stale package state | Request only window-state events; never query window contents; clear unknown package state and fail open. |
| User gets stuck or target app is removed | Per-row reset, clear-all, service-off recovery instructions, app test action, and visible disconnected status. |
| Users assume all buttons or synthetic key replacement are supported | Explain the exact supported app-launch action and limitations in learn flow, help, settings, and release notes. |
| Mapping data or key events reveal habits | Store only user mappings locally; do not log key-event sequences, foreground history, or upload telemetry. |

## Out of scope

- Root, ADB, privileged/system application installation, firmware modification, IR/CEC transmission, or hardware remotes that Android never reports.
- Arbitrary key-event synthesis, keyboard/macro injection, editing another app's internal hard-coded controls, or overriding protected OS behavior.
- Accessibility tree inspection, screen capture, network telemetry, background scanning, and forced service restart.
- The Android-stream volume-control subsystem, which has a separate specification at `docs/superpowers/specs/2026-11-27-android-volume-control.md`.

## Official Android references

- [`AccessibilityServiceInfo`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) — key-event filtering capability and service configuration.
- [`AccessibilityService.onKeyEvent(KeyEvent)`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent%28android.view.KeyEvent%29) — event callback and service lifecycle.
- [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service) — declared purpose and accessibility-service setup.
- [`KeyEvent`](https://developer.android.com/reference/android/view/KeyEvent) — key codes and event/repeat semantics.
- [Android HDMI-CEC control service (AOSP)](https://source.android.com/docs/devices/tv/hdmi-cec) — CEC support and device/OEM variability; access to the CEC service is protected.
