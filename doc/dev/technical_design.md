# TV Usage Limit App - Technical Design Details

This document outlines the internal design and implementation details of the TV Usage Limit application, specifically focusing on data models, time tracking mechanics, and enforcement logic.

## 1. Data Models

The application uses Google Room Database for persistence.

### 1.1 Profile (`profiles` table)
Represents a user profile with specific restrictions.

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Int` | Primary Key (Auto-generate). |
| `name` | `String` | Display name (e.g., "Child", "Parent"). |
| `isRestricted` | `Boolean` | `true` if time limits apply; `false` for unlimited access. |
| `pin` | `String?` | 4-digit PIN for access/switching. |
| `dailyLimitMinutes` | `Int` | Max allowed minutes per day. 0 or -1 indicates unlimited. |
| `sessionLimitMinutes` | `Int` | Max allowed minutes per continuous session. |
| `restDurationMinutes` | `Int` | Required break time between sessions. |

### 1.2 UsageLog (`usage_logs` table)
Tracks daily usage accumulation per profile.

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `Int` | Primary Key (Auto-generate). |
| `profileId` | `Int` | Foreign Key to `Profile.id`. |
| `date` | `String` | Date of usage (ISO-8601 `YYYY-MM-DD`). |
| `totalUsageMinutes` | `Int` | Accumulated watching time for that day. |

## 2. Time Tracking Mechanism

The core logic resides in `TimeTrackingService`, a **Foreground Service** that runs continuously to monitor TV usage.

### 2.1 State Tracking
The service maintains several state variables:
- **Current Profile**: Loaded from DB on initialization or switch.
- **Accumulated Usage**: `accumulatedDailyUsage` (synced with DB) and `currentSessionUsage` (in-memory).
- **Screen State**: Monitors `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF`.
- **App Foreground State**: Detects if the app UI is visible (via `ActivityLifecycleCallbacks`) to pause tracking during configuration.

### 2.2 The Tracking Loop
A Coroutine loop runs every minute (`CHECK_INTERVAL_MS = 60000L`) when tracking is active.
**Condition for Tracking:**
- Screen is ON.
- App is NOT in foreground (User is watching TV, not configuring the app).
- App is NOT in foreground (User is watching TV, not configuring the app).
- Profile is loaded.

**Logical Day Tracking:**
- The service maintains a `currentLogDate` state (initialized on load).
- If the user watches TV past midnight without turning it off, usage is still recorded under the *start date* of the session (`currentLogDate`), penalizing the "night session" rather than the "next morning".
- **Reset Trigger**: When the screen turns ON (or profile switches), the service checks if `today != currentLogDate`. If true, usage stats are reloaded (starting fresh for the new day).

**In each tick:**
1.  Increments `accumulatedDailyUsage` and `currentSessionUsage`.
2.  Persists update to `UsageLog` in Room DB.
3.  Broadcasts `ACTION_USAGE_UPDATE` to update any visible UI.
4.  Checks if limits are reached (`isLimitReached()`).

### 2.3 Limit Logic
`isLimitReached()` returns `true` if `isRestricted` is true AND:
- `dailyLimitMinutes` is exceeded, OR
- `sessionLimitMinutes` is exceeded.

## 3. Enforcement & Overlay

When limits are reached:

1.  **Overlay**: The service uses `WindowManager` to add a `TYPE_APPLICATION_OVERLAY` view.
    - This view covers the screen, effectively blocking usage.
    - It is `FLAG_NOT_FOCUSABLE` (mostly) but captures clicks for the "Switch Profile" button.
2.  **Countdown**: A `CountDownTimer` starts (10 seconds).
3.  **Lock**: If the timer finishes, `DevicePolicyManager.lockNow()` is called to put the TV to sleep.

## 4. Profile Switching & Session Persistence

- **Switching**: Handled via `ProfileSelectionActivity` broadcasting `ACTION_PROFILE_CHANGED`.
- **Session Persistence**:
    - The service saves `currentSessionUsage` and `lastSessionEndTime` locally for **each profile** independently.
    - **Resume Logic**:
        - If `CurrentTime - LastEndTime < restDuration` -> Session Resumes (usage count continues).
        - If `CurrentTime - LastEndTime >= restDuration` -> Session Resets (usage count = 0).
    - **Logic Refinement**: If `RestDuration` is met (elapsed > required), `currentSessionUsage` is actively reset to 0 in memory to ensure a fresh session starts even if the previous session hadn't hit the limit.
- **Screen Off**: When the screen turns off:
    - Tracking stops.
    - Current Profile state is saved.
    - Profile automatically reverts to the **Default Profile** ("Child") to ensure restrictions apply on next boot.
    - Overlay is removed.

## 5. Persistence Strategy

- **Database**: `AppDatabase` (Room).
- **Initialization**: Service loads the "Child" profile by default if no active profile state is found.
- **Daily Reset**:
    - **Physical Day**: Handled by Room DB (new row per date).
    - **Logical Day**: Handled by `TimeTrackingService`.
        - In-memory `accumulatedDailyUsage` persists across midnight if the session is continuous.
        - reset occurs only when `currentLogDate` mismatches `today` during `startTracking` (Power On / Profile Switch).

## 6. UI Architecture & Display Logic (MainActivity)

The `MainActivity` serves as the primary dashboard and feedback mechanism for the user. It relies on `BroadcastReceiver` to update its UI in real-time without polling.

### 6.1 Displayed Information
- **Current Profile**: derived from `Service` broadcast (primary) or `SharedPreferences` (fallback/init).
- **Today's Usage**: Daily accumulated minutes from `TimeTrackingService`.
- **Time Until Rest**: Calculated as `SessionLimit - CurrentSessionUsage`. Displays "Unlimited" if no session limit is active.

### 6.2 Updates Mechanism
- **Profile Updates**: Listens for `ACTION_PROFILE_CHANGED`.
- **Usage Updates**: Listens for `ACTION_USAGE_UPDATE`, payload includes:
    - `daily_usage`
    - `session_limit`
    - `session_usage`
    - `profile_name`

## 7. Operational States Summary

### 7.1 Tracking State
Active when:
1.  Screen is **ON**.
2.  App is in **Background**.
3.  Profile is valid.

### 7.2 Paused State
Active when:
1.  Screen is **OFF**.
2.  **OR** App is in **Foreground**.

### 7.3 Restricted State
Active when `isLimitReached()` returns true. Triggers the Overlay and Sleep Timer.
