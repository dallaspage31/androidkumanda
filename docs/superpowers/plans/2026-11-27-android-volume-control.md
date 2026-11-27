# Android volume control Implementation Plan

> **Execution:** Use `executing-plans` in the same session. Use `subagent-driven-development` only if explicitly selected and permitted. Track steps with checkboxes (`- [ ]`).

**Goal:** Let a user direct eligible remote volume-up/down events to Android's media stream and prove on the target setup whether the intended output actually changes at a fixed soundbar gain.

**Architecture:** Add a small, injectable adapter around `AudioManager` and keep physical input handling in the existing `RemoteMapperAccessibilityService`/`KeyRouter` path. Resolve the explicit volume-mode binding before app-launch mappings, use `STREAM_MUSIC` only, preserve down/repeat/up pairing, and pass events through whenever the mode is off, the policy is fixed, or the handler cannot safely act. Route enumeration on API 23+ is diagnostic context only; only a real soundbar test establishes audible success.

**Tech Stack:** Kotlin/JVM 17, Kotlin Gradle Plugin 2.4.21 with AGP built-in Kotlin, Android Gradle Plugin 9.5.3, Gradle 9.7.0, Android API 21 minimum / compile and target SDK 37, `AudioManager`, `AudioDeviceInfo` on API 23+, platform Views, JUnit 4.13.2, Robolectric 4.18.1.

**Spec:** `docs/superpowers/specs/2026-11-27-android-volume-control.md`

## Global Constraints

- Keep `minSdk = 21`; use stable `compileSdk = 37` and `targetSdk = 37`.
- Use AGP 9.5.3's built-in Kotlin instead of applying `org.jetbrains.kotlin.android`; align KGP 2.4.21 by declaring `org.jetbrains.kotlin.jvm` in the root plugin block, as documented for built-in Kotlin.
- Keep Gradle 9.7.0, KGP 2.4.21's documented maximum fully supported Gradle version. Gradle 9.9.2 is newer but outside that support ceiling; AGP 9.6 remains alpha.
- Only `AudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, ...)` adjusts Android volume; never use suggested/current stream selection.
- Check `AudioManager.isVolumeFixed()` and pass through rather than claim control when policy is fixed.
- `AudioDeviceInfo` route diagnostics are guarded at API 23+ and shown as connected-output hints, never proof of a playing track's route.
- Do not access hidden/privileged CEC APIs, change TV/soundbar gain, force an audio route, or claim AUX/optical/SPDIF/HDMI behavior without a matching hardware test.
- A volume event is handled only when the AccessibilityService receives it. An API request or stream-index change alone does not prove audible sound change.
- Keep the service user-enabled and transparent, preserve key pass-through for disabled/failed/unrecognized cases, and do not inspect window content or log raw key streams.
- Store volume-mode state locally; no background restore, telemetry, audio capture, or forced service restart.

---

## Repository map and implementation boundary

Confirmed current files:

- `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt` — existing key callback and service lifecycle; add the dedicated volume dispatch before generic learnable-key filtering.
- `app/src/main/java/com/androidkumanda/KeyRouter.kt` — event signature and held DOWN/UP pairing; extend or reuse the tested event lifecycle rather than creating a competing listener.
- `app/src/main/java/com/androidkumanda/BindingStore.kt` — local private preferences; store only enabled state and selected volume key signatures here.
- `app/src/main/java/com/androidkumanda/MainActivity.kt` — platform Views setup/help, focus and service status; add a separate volume setting and compatibility flow.
- `app/src/main/res/values/strings.xml` — existing resource location if new UI text should be localized through resources.
- `app/src/test/java/com/androidkumanda/KeyRouterTest.kt` and `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt` — current JUnit/Robolectric test locations.
- `app/build.gradle.kts` — API 21 min / API 37 compile and target; no production dependencies are required.

Create `app/src/main/java/com/androidkumanda/AndroidVolumeController.kt` if the controller needs a separate file; the repository uses one Kotlin class per file in `com.androidkumanda` (`KeyRouter.kt`, `BindingStore.kt`, `AppCatalog.kt`). Keep the platform port and adapter in that package; do not create a new Gradle module.

## First product slice and earliest real-device check

**First user-visible artifact:** a TV-navigable **Android media volume** setting with a clear on/off switch, separate volume-up/down learning, fixed-volume status, and a guided test that says "Android media volume" rather than "TV/soundbar volume."

**Earliest hardware smoke check:** after Task 3, connect the actual target soundbar using the user's intended AUX or optical/SPDIF path, set the soundbar gain to a documented fixed value, play media from one app, enable the feature, and press remote volume-up/down. Verify all three independently: the service received the key, Android `STREAM_MUSIC` index changed, and audible output changed while soundbar gain stayed fixed. Repeat in a second media app and after route reconnect. If any layer fails, show exactly which layer is unverified and do not call the setup supported. Also test `isVolumeFixed()==true` or a no-change device and confirm the original event remains pass-through.

**Execution status:** The volume controller, key routing, settings, automated tests, lint, and release build are complete. No physical remote or TV/box/soundbar was available, so the required audible-output smoke test and device matrix remain unverified.

## Task 1: Define the Android stream adapter and fixed/no-change outcomes

**Files:**
- Create: `app/src/main/java/com/androidkumanda/AndroidVolumeController.kt`
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- `enum class VolumeDirection { RAISE, LOWER }`
- `enum class VolumeAdjustmentResult { INDEX_CHANGED, FIXED_POLICY, NO_INDEX_CHANGE }`
- `interface VolumePlatform { fun isVolumeFixed(): Boolean; fun musicVolumeIndex(): Int; fun adjustMusic(direction: VolumeDirection) }`
- `class AndroidVolumeController(private val platform: VolumePlatform)` with `fun adjust(direction: VolumeDirection): VolumeAdjustmentResult`
- Android adapter uses `AudioManager.isVolumeFixed()`, `getStreamVolume(STREAM_MUSIC)`, and `adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE|ADJUST_LOWER, FLAG_SHOW_UI)`.

- [ ] **Step 1: Add failing adapter tests** for raise/lower issuing only media stream operations, fixed policy issuing no adjustment, and unchanged media index returning `NO_INDEX_CHANGE`.

```kotlin
@Test fun `fixed-volume policy does not issue stream adjustment`() {
    var calls = 0
    val platform = object : VolumePlatform {
        override fun isVolumeFixed() = true
        override fun musicVolumeIndex() = 8
        override fun adjustMusic(direction: VolumeDirection) { calls++ }
    }
    assertEquals(VolumeAdjustmentResult.FIXED_POLICY,
        AndroidVolumeController(platform).adjust(VolumeDirection.RAISE))
    assertEquals(0, calls)
}
```

- [ ] **Step 2: Run the focused integration test and confirm it fails** because the controller API is not present.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest.fixed-volume policy does not issue stream adjustment"`

Expected: FAIL at compile time or the expected result assertion.

- [ ] **Step 3: Implement the small controller.** Check fixed policy first; read the media index; issue exactly one explicit `STREAM_MUSIC` adjustment; read the index again and return `INDEX_CHANGED` only when it changed, otherwise return `NO_INDEX_CHANGE`. The adapter must not claim the route was audible.

- [ ] **Step 4: Re-run the controller tests** including both directions and fixed/no-change cases.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; no non-media stream API is called and fixed devices issue no adjustment.

## Task 2: Persist independent volume-key configuration and reject conflicts

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/BindingStore.kt`
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- `data class VolumeKeyBinding(val enabled: Boolean, val raiseKey: KeySignature?, val lowerKey: KeySignature?)`
- `BindingStore.volumeKeyBinding(): VolumeKeyBinding`
- `BindingStore.saveVolumeKeyBinding(binding: VolumeKeyBinding): Boolean`
- Store values in a separate preference namespace/keys from app-launch mappings; do not change the remapping migration data.

- [ ] **Step 1: Add failing persistence tests** for disabled defaults, round-trip after store recreation, distinct raise/lower keys, rejection of duplicate up/down keys, and conflicts with existing app mappings.
- [ ] **Step 2: Run the focused integration tests** and verify the missing API/validation fails.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: FAIL for the absent volume configuration contract.

- [ ] **Step 3: Implement the separate preference and validation.** Reject a reserved/unlearnable signature for learning only when it is not one of the explicitly handled Android volume keys. When a key is assigned to volume mode, reject its simultaneous assignment to app launch; never permit two actions for one source key.
- [ ] **Step 4: Re-run integration tests** and verify ordinary app mappings and legacy migration are unchanged.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; volume preferences are independent and overlapping actions cannot be saved.

## Task 3: Connect physical DOWN/repeat/UP to Android media volume

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/java/com/androidkumanda/KeyRouter.kt` only if the current held-event contract needs an explicit action callback
- Modify: `app/src/main/java/com/androidkumanda/AndroidVolumeController.kt`
- Test: `app/src/test/java/com/androidkumanda/KeyRouterTest.kt`
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- `RemoteMapperAccessibilityService` handles an enabled configured volume key before `KeySignature.isLearnable()` rejects normal volume codes.
- `KeyRouter.routeVolume(event: KeyStroke, direction: VolumeDirection?, adjust: (VolumeDirection) -> VolumeAdjustmentResult): Boolean` returns true only for the matching consumed DOWN/repeats/UP. The first valid DOWN decides whether the press is consumed; only `INDEX_CHANGED` consumes it.
- Initial DOWN performs one adjustment; each same-downTime hardware repeat performs one further adjustment; matching UP performs none. Once the first DOWN was consumed, the remaining repeats and matching UP stay consumed even if a later repeat reaches a limit. Unknown, cancelled, mismatched, fixed-policy, or no-index-change initial DOWN events pass through.

- [ ] **Step 1: Add failing router/service tests** for enabled volume UP, disabled volume pass-through, first DOWN one step, repeated DOWN one step each, matching UP no step, unmatched UP no step, and fixed/no-change pass-through.

```kotlin
@Test fun `volume repeat adjusts once per received repeat but up never adjusts`() {
    val router = KeyRouter()
    val stroke = press(action = 0)
    var adjustments = 0
    val adjust: (VolumeDirection) -> VolumeAdjustmentResult = {
        adjustments++
        VolumeAdjustmentResult.INDEX_CHANGED
    }
    assertTrue(router.routeVolume(stroke, VolumeDirection.RAISE, adjust))
    assertTrue(router.routeVolume(stroke.copy(repeat = 1), VolumeDirection.RAISE, adjust))
    assertTrue(router.routeVolume(stroke.copy(action = 1), VolumeDirection.RAISE, adjust))
    assertEquals(2, adjustments)
}
```

- [ ] **Step 2: Run focused router tests and confirm they fail** before adding the route.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest"`

Expected: FAIL because `routeVolume` and the event-state behavior are absent.

- [ ] **Step 3: Implement the dedicated volume branch.** During volume-key learning, accept only the two explicit volume key codes despite the general `isLearnable()` denylist; keep every other reserved key denied. Resolve one configured key to one direction, call `AndroidVolumeController` on initial DOWN and each received repeat, and preserve the initial consumed result through matching UP. If the first adjustment is fixed/no-change, return false so Android/OEM handling remains available. Do not apply a second volume action from app remapping.

- [ ] **Step 4: Run router and persistence tests.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest" --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; exactly one action per delivered DOWN or repeat, no action on UP, and no double-binding.

## Task 4: Build a TV-friendly setup and truthful compatibility feedback

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/res/values/strings.xml` if the user-facing strings use resources
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- Add a separate volume section that learns UP/DOWN keys, toggles Android media-volume control, and shows statuses `DISABLED`, `SERVICE_UNAVAILABLE`, `FIXED_POLICY`, `KEY_NOT_LEARNED`, `NO_INDEX_CHANGE`, and `READY_TO_TEST`.
- The existing system Accessibility Settings screen remains the enable/disable authority.
- A user-initiated test returns volume-policy/index evidence; it does not claim audible success without the hardware checklist.

- [ ] **Step 1: Add failing UI tests** for focusable volume controls, separate Android-vs-TV wording, service-off guidance, fixed policy messaging, and persisted enabled state.
- [ ] **Step 2: Run the one integration test and confirm the new controls/status cases fail.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: FAIL because the volume section is absent.

- [ ] **Step 3: Implement the first user-visible slice.** Add TV-focusable controls and a short onboarding message that says Android's media volume is adjusted only when the key is delivered and the device allows it. Show fixed/no-change distinctly. Include the precise fixed-soundbar-gain test steps and a reversible off switch.
- [ ] **Step 4: Perform the earliest real-device smoke check** described above; capture Android key receipt, before/after media index, route type if available, soundbar gain, apps tested, and audible result separately.
- [ ] **Step 5: Run the focused integration tests.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; UI never equates command acceptance/index change with soundbar volume change.

## Task 5: Add guarded output hints and fixed-soundbar acceptance evidence

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/AndroidVolumeController.kt`
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`
- Modify: `README.md`

**Interfaces:**
- `AndroidVolumeController.connectedOutputTypes(): Set<Int>?` returns `null` below API 23 and API-reported connected output types on API 23+; it does not return a "current media route" claim.
- `AudioDeviceInfo` references are guarded by `Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`.
- Diagnostic labels map known type constants to names such as analog line, digital line, HDMI, HDMI ARC, and HDMI eARC; unknown values are shown as unknown, not guessed.

- [ ] **Step 1: Add failing tests** for API-21/22 route-unavailable behavior, API-23+ output type mapping, and copy that calls output types "connected devices" rather than "active stream route".
- [ ] **Step 2: Run focused integration tests and confirm the route-gating cases fail.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: FAIL because route diagnostics are not implemented.

- [ ] **Step 3: Add the API-guarded device inventory.** Read `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` only when supported. Do not persist device IDs/product names or infer track routing from connection state. Keep route changes reactive by re-reading only when the user opens diagnostics or starts a test.
- [ ] **Step 4: Update README** with Android TV/box, key delivery, fixed policy, HDMI/CEC, AUX/optical/SPDIF, and soundbar gain limitations; clearly distinguish API evidence from physical listening evidence.
- [ ] **Step 5: Run integration tests and perform the matrix smoke checks** for one target analog/AUX path, one digital optical/SPDIF path, and HDMI only where available.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS. A route is called supported for fixed soundbar gain only if audible output changed with soundbar gain unchanged on that exact tested device/route/app combination.

## Task 6: Verify lifecycle, privacy, compatibility, and recovery

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `README.md`
- Test: existing `KeyRouterTest.kt` and `AndroidIntegrationTest.kt`

- [ ] **Step 1: Test disabled mode and lifecycle recovery.** Disable the feature; confirm volume events pass through. Disable/re-enable the accessibility service in Settings; confirm the app shows service state and saved volume keys remain intact without auto-restarting the service.
- [ ] **Step 2: Run focused regression tests.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest" --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; app mappings, migration, repeat handling, fixed policy, and volume mode remain independent.

- [ ] **Step 3: Complete the device matrix** for OS/API, OEM, remote transport, key delivery, `isVolumeFixed`, stream index before/after, output types, soundbar make/input/fixed gain, media apps, audible outcome, and CEC state where user-configurable.
- [ ] **Step 4: Update support claims** to list only verified combinations; label other routes "not tested" or "device may not allow Android volume control." Do not infer universal AUX/optical control from one device.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Key never reaches accessibility callback | Verify learning/key receipt first; distinguish input delivery from API and audible-output stages. |
| Fixed Android volume policy | Check `isVolumeFixed` before adjusting; pass through and explain. |
| API accepts request but stream or audible output does not change | Compare media index and separately test with fixed soundbar gain; return no-change/untested status otherwise. |
| AUX, optical, SPDIF, ARC/eARC route bypasses software gain | Require per-device/per-route smoke test; do not assume port naming implies gain behavior. |
| CEC changes an external device instead | Do not access CEC; record CEC/OSD behavior in hardware test and explain external volume is outside the feature. |
| Repeat cadence too fast or inconsistent | Use the actual delivered repeat sequence; test each supported remote. Do not add timers that manufacture volume events. |
| App-key mapping and volume mapping overlap | Validate uniqueness in `BindingStore`; route volume first, and test zero double-dispatch. |
| Downstream service stops | Keep a visible service-state check and recovery steps; user/system remain in control. |

## Accessibility, onboarding, recovery, diagnostics, and privacy

- Use large D-pad-focusable controls and explicit "Android media volume" labels. Explain that this is not a promise to alter TV, soundbar gain, or CEC.
- Require the user to enable the service and volume mode separately; expose a one-step off switch and retain only the user's key selection.
- When system reports fixed volume, no stream index change, unknown route, disabled service, or no delivered key, state which layer failed instead of presenting a green success state.
- Do not auto-rewrite volume after startup, route change, or disabling the mode. Do not ask for audio recording or microphone permission.
- Do not save raw key events, key sequences, playback history, sound samples, stable audio device IDs, or external diagnostics. A saved hardware result is user-entered and local only.

## Scope and implementation order

Implement Tasks 1 → 2 → 3 → 4, then do the first real hardware check at Task 4 before route polish. Add Task 5 output hints and device evidence, then Task 6 regression/lifecycle checks. This subsystem is independent of general key remapping at the product level, but both must use the same event dispatcher and reject overlapping source-key bindings; do not duplicate accessibility services or input filters.
