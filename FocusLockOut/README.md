# Focus Lock Out

An extreme anti-distraction lock for Android. Pick the apps that eat your day, arm a timed session,
and every time one of them opens it is covered by a full-screen wall. Getting past it means
finishing a deliberately annoying challenge:

| Penalty | What you have to do |
| --- | --- |
| **Typing Penalty** | Type an absurd 80–100 word passage with zero mistakes (case and punctuation count, paste is blocked). |
| **Shake Penalty** | Shake the phone 100 times (accelerometer, with gravity filtering and a refractory window). |
| **Math Wall** | Solve 5 multi-step problems such as `(14 × 7) − 19` in a row; one miss resets the streak. |

Passing grants a 5-minute grace period for that app before the lock re-arms. An **Emergency
Unlock** on the dashboard ends the whole session, after the same challenge. Both paths finish
with a "share your shame" button: *"I lasted 3 minutes before breaking my focus lock on Instagram."*

## Requirements

- A recent Android Studio that supports AGP 8.13, or JDK 17+ with the Android SDK (platform 36).
- Device or emulator running Android 8.0 (API 26) or later.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests for the challenge and session logic
```

Toolchain: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, Compose BOM 2025.10.01 (Material 3 1.4),
compileSdk/targetSdk 36, minSdk 26. Versions live in `gradle/libs.versions.toml`.

## First run on a device

The home screen lists every missing permission with a button that opens the right Settings page:

1. **Accessibility service**: detects the foreground app. On Android 13+ a sideloaded APK may show
   the switch greyed out; open *App info → ⋮ → Allow restricted settings* first.
2. **Display over other apps**: lets the lock wall start on top of a blocked app.
3. **Usage access**: a second, independent foreground-app signal (see below).
4. **Notifications** (optional): shows the live countdown while a session is armed.

Then choose apps, set a duration (10–120 minutes), pick a penalty and tap **ARM LOCK**.

## How it works

```
MainActivity (Compose, Navigation)          OverlayActivity (singleInstance, no Recents)
  ├─ HomeRoute: setup ⇄ live dashboard        └─ LOCKED → CHALLENGE → PASSED
  └─ AppSelectionRoute: block list                 typing / shake / math, shame share
             │                                          ▲
             ▼                                          │ startActivity(NEW_TASK | CLEAR_TOP)
      DataStoreRepository  ◀──────────────  FocusAccessibilityService
      (single source of truth)                ├─ TYPE_WINDOW_STATE_CHANGED → event.packageName
             ▲                                └─ watchdog every 3 s → ForegroundAppTracker (UsageStats)
             │
      LockSessionService (foreground, specialUse): countdown notification, ends the session on time
```

- **State**: `DataStoreRepository` persists `isSessionActive`, `sessionStartTime`, `sessionEndTime`,
  `blockedPackages`, `selectedChallengeType` (`TYPING` / `SHAKE` / `MATH`), the last duration and
  per-app grace expiries. A session counts as engaged only while its end time is in the future, so a
  stale flag can never lock apps forever. State-dependent writes happen inside one `edit` transaction.
- **Detection**: window-state events are instant. The watchdog covers what they miss, such as a
  blocked app still open when its grace period runs out. It only runs while a session is engaged
  and the screen is on and unlocked.
- **No overlay loops**: passing a challenge writes the grace entry *before* the "Continue" button
  enables, the service skips launches while the overlay is resumed, and repeated events are debounced.
- **Safety**: system UI, launchers, the default dialer (emergency calls) and the app itself can never
  be blocked. Apps can be added during a session but not removed, and the penalty can't be switched.
- **Resilience**: the accessibility service restores an armed session after a reboot or process death,
  and the end time is enforced by the foreground service, the dashboard and the accessibility service.

## Play Store notes

- The accessibility service sets `canRetrieveWindowContent="false"`, `isAccessibilityTool="false"`
  and is preceded by an in-app prominent disclosure. Play requires a matching declaration form.
- `FOREGROUND_SERVICE_SPECIAL_USE` needs a justification in Play Console. The manifest's
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` holds the wording. (`systemExempted` is reserved for system and
  device-admin apps, so it isn't used.)
- `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` are special-access permissions the user grants in Settings.

## Project layout

```
app/src/main/java/com/focuslockout/
  FocusLockApp.kt, MainActivity.kt
  data/          DataStoreRepository, LockState, ChallengeType, LockPolicy
  service/       FocusAccessibilityService, LockSessionService, ForegroundAppTracker, ProtectedPackages
  overlay/       OverlayActivity, OverlayViewModel, OverlayScreen, challenge/ (Typing, Shake, Math)
  challenge/     TypingMatcher, MathChallengeGenerator, ShakeDetector (pure Kotlin, unit tested)
  permissions/   PermissionChecker
  apps/          InstalledAppsRepository
  ui/            theme, navigation, home (setup, dashboard, permissions), apps (block list)
```
