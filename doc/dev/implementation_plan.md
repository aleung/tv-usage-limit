# TV Usage Limit App Implementation Plan

This plan outlines the development of an Android application for Google TV designed to limit TV usage time per profile.

## User Review Required

> [!IMPORTANT]
> **Turning off the TV**: Standard Android apps cannot directly "power off" the TV hardware for security reasons. The closest achievable action for a sideloaded app is to put the device to sleep (turn off screen) using `DevicePolicyManager.lockNow()`. The user will need to grant "Device Admin" privileges to the app manually after installation.

> [!WARNING]
> **Overlay Permission**: The "Warning Window" requires `SYSTEM_ALERT_WINDOW` permission (Display over other apps), which must be granted manually by the user on Android TV settings.

## Proposed Changes

### Project Structure (New)
We will create a standard Android project structure with Gradle.
- `app/src/main/AndroidManifest.xml`: Permissions (Boot, Overlay, Device Admin).
- `app/src/main/java/com/example/tvlimit/`: Source code.
- `app/src/main/res/`: Layouts and resources.

### Core Logic (`com.example.tvlimit`)

#### [NEW] `data` package
- `AppDatabase.kt`: Room database instance.
- `Profile.kt`: Entity for user profiles (name, dailyLimit, sessionLimit, restDuration, pin).
- `UsageLog.kt`: Entity to track daily usage.
- `ProfileDao.kt`: Data access object.

#### [NEW] `service` package
- `TimeTrackingService.kt`: A **Foreground Service** that runs continuously.
  - Monitors screen state (ON/OFF).
  - **On Screen OFF**: Immediately resets `currentProfile` to the **Default Profile**.
  - Tracks accumulated time for the current profile.
  - Checks against `dailyLimit` and `sessionLimit`.
  - Launches `WarningOverlay` when limits are reached.
  - Resets session timers after `restDuration`.
- `BootReceiver.kt`: Listens for `BOOT_COMPLETED` to start `TimeTrackingService` immediately.

#### [NEW] `ui` package
- `WarningOverlay.kt`:
  - Implements a floating window using `WindowManager` (instead of an Activity) for better intrusion.
  - Displays "Time Up!" message and PIN input.
  - **Research Note**: Inspired by `tv-timer-plus` which uses `WindowManager` for overlays. This avoids Activity stack issues and is more persistent.
  - Logic: Starts a 10-second countdown. If no valid action, calls `DevicePolicyManager.lockNow()`.
- `MainActivity.kt`:
  - Configuration screen to create/edit profiles and view stats. (Initially simple).

#### [NEW] `receiver` package
- `AdminReceiver.kt`: Required for Device Admin privileges (to lock screen).

### Resources
- `res/layout/view_warning_overlay.xml`: Layout for the warning popup (Inflated by Service).
- `res/layout/activity_main.xml`: Layout for settings.
- `res/xml/device_admin.xml`: Device admin policy declaration.

## Research Findings (tv-timer-plus)
The researched project `tv-timer-plus` uses a **local ADB server** to send `KEYCODE_SLEEP` to turn off the screen.
- **Pros**: Can send any key event (reboot, power off).
- **Cons**: Extremely complex, requires enabling USB debugging workflow and pairing.
- **Decision**: We will use `DevicePolicyManager.lockNow()` which achieves the "Sleep" effect much more reliably for a standard admin app without ADB hacks.

## Verification Plan

### Automated Tests
- Since this is a UI and Service-heavy Android app, unit tests for the Time Tracking logic (ignoring Android dependencies) can be written later if needed.
- **Lint Check**: Run `./gradlew lintDebug` to check for common issues.

### Manual Verification
1.  **Auto-start**: Reboot the Android TV emulator/device. Verify `TimeTrackingService` starts (check logs `adb logcat | grep TVLimit`).
2.  **Usage Limit**:
    - Set a short limit (e.g., 1 minute).
    - Watch "TV" (stay on home screen or open an app).
    - Verify `WarningActivity` pops up after 1 minute.
3.  **Warning Logic**:
    - Wait 10 seconds on the warning screen. Verify device goes to sleep (screen off).
    - Enter correct PIN. Verify warning disappears and profile switches (or timer resets).
4.  **Profile Limits**:
    - Verify daily limit accumulates correctly across sessions.
    - Verify "Rest Time" enforces a break before next session allowed.
