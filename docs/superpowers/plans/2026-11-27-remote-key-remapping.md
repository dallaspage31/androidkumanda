# Remote key remapping Implementation Plan

> **Execution:** Use `executing-plans` in the same session. Use `subagent-driven-development` only if explicitly selected and permitted. Track steps with checkboxes (`- [ ]`).

**Goal:** Replace fixed brand-key slots with user-managed, device-tested mappings from eligible physical remote keys to chosen installed apps, without claiming control of events Android does not deliver.

**Architecture:** Keep the existing single app module, `com.androidkumanda` package, platform Views UI, `AccessibilityService` key filter, and private `SharedPreferences` store. Evolve `BindingStore` to persist stable mapping entries and migrate the current Netflix/Disney/Prime slots; evolve `KeyRouter` to make app-launch decisions once per eligible physical press, and keep `RemoteMapperAccessibilityService` limited to key events plus package-name-only context for opt-out apps. Do not synthesize key events; the independent Android-volume feature owns volume actions and shares a single non-overlapping dispatch rule.

**Tech Stack:** Kotlin/JVM 17, Kotlin Gradle Plugin 2.4.21 with AGP built-in Kotlin, Android Gradle Plugin 9.5.3, Gradle 9.7.0, Android API 21 minimum / compile and target SDK 37, Android platform Views, `AccessibilityService`, `SharedPreferences`, JUnit 4.13.2, Robolectric 4.18.1.

**Spec:** `docs/superpowers/specs/2026-11-27-remote-key-remapping.md`

## Global Constraints

- Keep `minSdk = 21`; use stable `compileSdk = 37` and `targetSdk = 37`.
- Use AGP 9.5.3's built-in Kotlin instead of applying `org.jetbrains.kotlin.android`; align KGP 2.4.21 by declaring `org.jetbrains.kotlin.jvm` in the root plugin block, as documented for built-in Kotlin.
- Keep Gradle 9.7.0, KGP 2.4.21's documented maximum fully supported Gradle version. Gradle 9.9.2 is newer but outside that support ceiling; AGP 9.6 remains alpha.
- Keep `android:canRetrieveWindowContent="false"`; do not read window text, node trees, screenshots, or unrelated accessibility data.
- Remap only physical key events received by `RemoteMapperAccessibilityService.onKeyEvent`; never promise to capture firmware-consumed, protected, or undelivered keys.
- First-release action is launching a chosen installed app; never inject or synthesize arbitrary `KeyEvent` objects.
- Preserve `KeyRouter`'s per-device/key/down-time press pairing and the pass-through behavior on failure, exclusion, unknown context, or service unavailability.
- Persist mappings locally in private `SharedPreferences`; no network, analytics, raw key-event logging, or forced service restart.
- Keep existing user mappings through an idempotent migration from the current three `Slot` records.
- Validate accessibility-service distribution eligibility against the current channel's policy before publishing; sideloading is not proof of store eligibility.

---

## Repository map and implementation boundary

Confirmed current implementation files:

- `app/src/main/java/com/androidkumanda/BindingStore.kt` — legacy `Slot` migration, `Binding`, and private preference reads/writes; use this as the source of truth.
- `app/src/main/java/com/androidkumanda/KeyRouter.kt` — `KeySignature`, `KeyStroke`, held-key pairing, and one-launch behavior; retain the existing file and tests.
- `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt` — key filter lifecycle, learning, routing, and app launch; register only the minimum new window-state event type.
- `app/src/main/java/com/androidkumanda/MainActivity.kt` — current setup screen, app picker, status/help, test and reset flows; continue using platform Views.
- `app/src/main/res/xml/accessibility_service_config.xml` and `app/src/main/AndroidManifest.xml` — accessibility capability and service declaration, only if the exact configuration needs updating.
- `app/src/test/java/com/androidkumanda/KeyRouterTest.kt` and `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt` — existing test locations for routing, persistence, migration, and TV UI.
- `app/src/main/java/com/androidkumanda/AppCatalog.kt` — confirmed installed-app catalog and launch helper; reuse it rather than creating a second picker.

The new source model, if separated from existing classes, belongs in `app/src/main/java/com/androidkumanda/KeyBinding.kt`, matching the verified package/class-per-file pattern shown by `KeyRouter.kt`, `BindingStore.kt`, and `AppCatalog.kt`. Prefer keeping the model next to `BindingStore` if no independent behavior justifies another file.

## First product slice and earliest real-device check

**First user-visible artifact:** a TV-navigable screen with **Add mapping → Learn key → Choose app → Test → Save**, plus service status and a per-mapping reset. The user can replace one observed brand key with an installed app before bulk management or diagnostic polish is added.

**Earliest hardware smoke check:** after Task 3, install the debug APK on one real target TV box, enable the service, learn a brand shortcut key, select a different installed app, return to the launcher, and press the key once and held. Pass only if the selected app opens once, the original app does not open, repeats do not relaunch it, and navigation/OK/back/power/volume still behave as documented. Record model, Android build, remote transport, key name, service status, and pass/fail. Stop the product claim at "not delivered" if the service never observes the key.

**Execution status:** The mapping and recovery implementation, automated tests, lint, and release build are complete. No physical TV/box or remote was available, so hardware smoke and device-matrix acceptance remain unverified.

## Task 1: Model arbitrary app-launch mappings and migrate existing slots

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/BindingStore.kt`
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`
- Optional create only if a separate model is needed: `app/src/main/java/com/androidkumanda/KeyBinding.kt`

**Interfaces:**
- `data class KeyBinding(val id: String, val key: KeySignature, val targetPackage: String, val excludedPackages: Set<String>)`
- `enum class SaveBindingResult { SAVED, DUPLICATE_KEY, INVALID_KEY, INVALID_TARGET }`
- `BindingStore.bindings(): List<KeyBinding>`
- `BindingStore.save(binding: KeyBinding): SaveBindingResult`
- `BindingStore.remove(id: String): Boolean`
- `BindingStore.clearAll()`
- Legacy `Slot` read support is migration-only; the current UI must not add new slot-specific mappings.

- [ ] **Step 1: Add failing persistence/migration tests.** In `AndroidIntegrationTest`, write tests that save two distinct entries, reload them from a new store, reject a duplicate `KeySignature`, and migrate existing `NETFLIX`, `DISNEY`, and `PRIME` keys/targets once without changing their values.

```kotlin
@Test fun `legacy slots migrate once and retain their key and target`() {
    val prefs = BindingStore(app).preferences
    prefs.edit().putInt("NETFLIX.code", 200).putInt("NETFLIX.scan", 500)
        .putString("NETFLIX.target", "tv.example.player").commit()

    val migrated = BindingStore(app).bindings()
    assertEquals(1, migrated.size)
    assertEquals(KeySignature(200, 500), migrated.single().key)
    assertEquals("tv.example.player", migrated.single().targetPackage)
    assertEquals(migrated, BindingStore(app).bindings())
}
```

- [ ] **Step 2: Run the focused test and confirm it fails** because the list-based API/migration is not present.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest.legacy slots migrate once and retain their key and target"`

Expected: FAIL at compile time for the new API or at the migration assertions; do not continue until the test demonstrates the missing behavior.

- [ ] **Step 3: Implement the model/store minimally.** Keep using the existing private `bindings` SharedPreferences file, prefix new entry keys with `v2.`, and store a schema-version marker. Use stable mapping IDs. Migrate valid legacy slot records and the version marker in one editor operation so migration is atomic and idempotent; ignore invalid legacy signatures and preserve valid target packages. Each later mapping save is one editor operation. Reject non-learnable keys, empty IDs/packages, and keys already assigned to another mapping.

- [ ] **Step 4: Run persistence and migration tests.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS for round-trip persistence, duplicate rejection, malformed legacy record handling, idempotent migration, and removal of one mapping without affecting the others. Update obsolete slot-only assertions to the new mapping API.

## Task 2: Route one eligible app action per physical press

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/KeyRouter.kt`
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Test: `app/src/test/java/com/androidkumanda/KeyRouterTest.kt`

**Interfaces:**
- `KeyRouter.route(event: KeyStroke, binding: KeyBinding?, foregroundPackage: String?, launch: (String) -> Boolean): Boolean`
- `KeyRouter` keeps the existing `Press(device, key)` and `Held(downTime, consumed)` concept; an event is eligible only when `action == KeyEvent.ACTION_DOWN`, `repeatCount == 0`, and `isCanceled == false`.
- `RemoteMapperAccessibilityService` matches a learnable `KeySignature` to at most one `KeyBinding`, then passes that binding and the cached package name to `KeyRouter`.

- [ ] **Step 1: Add failing unit cases** for a mapped press launching once, same-downTime repeat not launching twice, matching UP following the original consumed decision, failed launch passing the stream through, excluded package passing through, null foreground package passing through when exclusions are configured, and a mismatched device UP not ending the held state.

```kotlin
@Test fun `repeat and up do not relaunch a matching binding`() {
    val router = KeyRouter()
    val binding = KeyBinding("one", key, "tv.example.player", emptySet())
    var launches = 0
    val launch: (String) -> Boolean = { launches++; it == binding.targetPackage }

    assertTrue(router.route(press(0), binding, "tv.example.home", launch))
    assertTrue(router.route(press(0, repeat = 1), binding, "tv.example.home", launch))
    assertTrue(router.route(press(1), binding, "tv.example.home", launch))
    assertEquals(1, launches)
}
```

- [ ] **Step 2: Run only `KeyRouterTest` and confirm new cases fail** before changing routing.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest"`

Expected: FAIL on the missing route signature or new behavior.

- [ ] **Step 3: Implement the route decision.** A binding with an exclusion for the known foreground package passes through. If exclusions exist and foreground package is null/unknown, pass through. Launch only on the first DOWN. Store the consumed decision until the matching UP. Keep state bounded and do not dispatch a synthetic key. Do not change the existing reserved-key list without a separate requirement and device proof.

- [ ] **Step 4: Re-run router tests and add regression assertions** for cancelled DOWN, unsupported action, held-map bound, lost UP followed by a new downTime, and `KeySignature` code/scan matching.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest"`

Expected: PASS; each physical press launches zero or one app, never more.

## Task 3: Deliver the first user-visible add/learn/choose/test/save flow

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/java/com/androidkumanda/AppCatalog.kt` only if an existing launchable-app query needs a narrow extension
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- Add mapping begins with `BindingStore`'s validated entry creation, `RemoteMapperAccessibilityService.beginLearning(mappingId: String, callback: (KeySignature?) -> Unit)`, and existing `AppCatalog.list()` / `AppCatalog.launch(packageName: String)`.
- Learning window remains 15 seconds. Cancel on dialog dismissal, activity pause, service interruption, or timeout.
- Existing `MainActivity` status/help and app catalog remain the only setup/navigation entry points.

- [ ] **Step 1: Add failing UI tests** asserting the new screen exposes **Add mapping**, **Learn key**, **Choose app**, **Test**, **Reset**, service status, and a clear indication that only received eligible keys can be learned. Verify D-pad-focusable controls and saved mappings survive activity recreation.

```kotlin
@Test fun `mapping setup exposes learn choose test and reset controls`() {
    Robolectric.buildActivity(MainActivity::class.java).use { controller ->
        val activity = controller.setup().get()
        val labels = descend(activity.window.decorView).filterIsInstance<Button>()
            .map { it.text.toString() }
        assertTrue(labels.contains("Yeni tuş eşlemesi"))
        assertTrue(labels.contains("Tuşu öğret"))
        assertTrue(labels.contains("Uygulama seç"))
        assertTrue(labels.contains("Dene"))
        assertTrue(labels.contains("Sıfırla"))
    }
}
```

- [ ] **Step 2: Run the one UI integration test and confirm it fails** because only fixed slot controls exist.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest.mapping setup exposes learn choose test and reset controls"`

Expected: FAIL on absent control or behavior.

- [ ] **Step 3: Implement the first end-to-end UI slice.** Render each stored mapping as a focusable row; add one row through learn → app choice → test → save. Show key learned only after a real service callback. Preserve the current TV-sized focus targets and app-picker behavior. If launch test fails, show a specific failure and do not save a success-shaped state.

- [ ] **Step 4: Run the integration tests, then perform the earliest hardware smoke check** described above before adding exclusions or expanded diagnostics.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS for the setup flow and existing catalog behavior. Hardware result must be written into the release/device test record; emulator success is not hardware evidence.

## Task 4: Add opt-out apps using package-name-only accessibility events

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `app/src/main/java/com/androidkumanda/BindingStore.kt`
- Modify: `app/src/main/res/xml/accessibility_service_config.xml` only if static event metadata requires it
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`
- Test: `app/src/test/java/com/androidkumanda/KeyRouterTest.kt`

**Interfaces:**
- `BindingStore.save` persists `excludedPackages: Set<String>` with the mapping.
- `RemoteMapperAccessibilityService.onAccessibilityEvent(event)` updates `foregroundPackage: String?` only for `TYPE_WINDOW_STATE_CHANGED`, using `event.packageName`; it never calls `getSource()`, reads nodes, or stores a history.
- Unknown/null package state is cleared and fails open for mappings that have exclusions.

- [ ] **Step 1: Add failing tests** for excluded package pass-through, non-excluded package app launch, null package pass-through, and persistence of the exclusion set.
- [ ] **Step 2: Run the focused router/store tests** and verify they fail before implementation.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest" --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: FAIL for missing exclusion behavior.

- [ ] **Step 3: Add the minimal package-name context flow.** Register only `TYPE_WINDOW_STATE_CHANGED` in `serviceInfo.eventTypes`; keep `canRetrieveWindowContent=false`. Add TV-focusable package exclusions from the existing app catalog. Invalidate context on null event package and service disconnect. Exclusions default empty, so mappings without exclusions do not depend on window events.
- [ ] **Step 4: Re-run focused tests and check the accessibility configuration** for no window-content capability.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest" --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; exclusion data round-trips and event route fails open for uncertain context.

## Task 5: Complete reset, service recovery, diagnostics, and privacy disclosure

**Files:**
- Modify: `app/src/main/java/com/androidkumanda/BindingStore.kt`
- Modify: `app/src/main/java/com/androidkumanda/MainActivity.kt`
- Modify: `app/src/main/java/com/androidkumanda/RemoteMapperAccessibilityService.kt`
- Modify: `app/src/main/res/values/strings.xml` only if new string resources are required by the existing resource convention
- Modify: `README.md` for user-facing behavior and device smoke procedure
- Test: `app/src/test/java/com/androidkumanda/AndroidIntegrationTest.kt`

**Interfaces:**
- `BindingStore.clearAll()` clears mapping entries and migration metadata safely; it does not alter Android accessibility settings.
- Main screen status distinguishes service disabled, enabled-but-not-connected, and connected.
- Diagnostics may show service state, last user-initiated learn outcome, and non-identifying aggregate counts only. No raw key history, foreground-package history, or external upload.

- [ ] **Step 1: Add failing tests** for clear-one, clear-all, service status wording, mappings preserved when the service is disabled, and no fabricated learned-key success on timeout.
- [ ] **Step 2: Run the targeted integration tests** and confirm the missing recovery/reset cases fail.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: FAIL for newly asserted clear-all/status behavior.

- [ ] **Step 3: Implement user-controlled recovery.** Add confirm-before-clear-all; keep Android Settings as the only service enable/disable control; show a stable status and retry guidance on resume; make learn timeout state explicit. Do not launch an auto-restart loop or infer that app launch proves the key was captured.
- [ ] **Step 4: Update README install/troubleshooting/privacy/device-test sections** with service permission, limitations, per-device evidence, and pass-through guarantees.
- [ ] **Step 5: Re-run focused integration tests.**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; settings controls are TV navigable and every reset/recovery operation states its scope.

## Task 6: Verify compatibility claims and acceptance criteria

**Files:**
- Modify: `README.md` only for verified support claims and actual smoke-test procedure.
- Test: existing `KeyRouterTest.kt` and `AndroidIntegrationTest.kt`.
- Device evidence: tracked outside runtime telemetry; record model/build/remote/key/result in the maintainer's release checklist.

- [ ] **Step 1: Review each spec matrix dimension**: Android API band, IR/Bluetooth/USB/vendor/CEC remote, app-shortcut vs protected/system key, repeats, app exclusion, competing key filters, service off/reconnect/OEM stop.
- [ ] **Step 2: Run the focused test suite** covering routing and Android integration.

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.androidkumanda.KeyRouterTest" --tests "com.androidkumanda.AndroidIntegrationTest"`

Expected: PASS; no test is treated as proof of physical remote/OEM behavior.

- [ ] **Step 3: Check the release wording against the recorded hardware matrix.** List only tested model/build/remote/key combinations. Explicitly label undelivered, reserved, protected, and untested keys unsupported or unknown.
- [ ] **Step 4: Verify reset and recovery on hardware** by clearing a mapping, disabling the service through Settings, and confirming standard navigation still works.

Expected: Each supported claim has device evidence; no blanket "any Android TV key" promise remains.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Accessibility filter is unavailable or another service conflicts | User-led setup, truthful status, real device check, no attempts to bypass system ownership. |
| OEM shortcut launches before the event reaches this APK | Learning timeout text explains non-delivery; do not broaden claims or use privileged/firmware workarounds. |
| New mapping format loses existing user state | Versioned idempotent migration tests before replacing the UI. |
| Key repeat/down/up pairing bug launches multiple apps | Router unit tests for event ordering, device ID, down time, cancellation, and bounded held state. |
| Foreground app exclusion observes private content | Listen only to package-name state events; keep `canRetrieveWindowContent=false`; no event log. |
| Unclear accessibility-service store policy | Check distribution policy before publication; document service purpose and consent before enabling. |

## Accessibility, privacy, and recovery checklist

- Every control is D-pad focusable with a visible focus state; dialogs have cancel/back behavior; labels explain source key, target app, exclusion, and reset impact.
- Service enablement is an explicit user action in Android Settings. Setup explains key-only observation and no window-content reading.
- App launch and key capture are reported separately. An app picker **Test** result cannot be presented as proof that a remote key is interceptable.
- Single-row reset, clear-all, service-off instructions, timeout, app-removed state, and disconnected-service status are all user-visible.
- No code logs complete `KeyStroke`, scan code, foreground package history, or mapping use.

## Scope and implementation order

Implement Tasks 1 → 2 → 3 and perform the first real-device smoke at the end of Task 3. Then add Task 4 exclusions, Task 5 recovery/privacy, and Task 6 compatibility evidence. The Android-volume-control feature is a separate plan and must resolve any shared key event before generic app mapping; do not merge both feature contracts into this data model.
