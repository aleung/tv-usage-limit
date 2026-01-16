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
- Profile is loaded.

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

## 4. Profile Switching & Session Reset

- **Switching**: Handled via `ProfileSelectionActivity` broadcasting `ACTION_PROFILE_CHANGED`.
- **Session Reset**: `currentSessionUsage` is currently reset to 0 upon any profile switch.
- **Screen Off**: When the screen turns off:
    - Tracking stops.
    - Profile automatically reverts to the **Default Profile** ("Child") to ensure restrictions apply on next boot.
    - Overlay is removed.

## 5. Persistence Strategy

- **Database**: `AppDatabase` (Room).
- **Initialization**: Service loads the "Child" profile by default if no active profile state is found.
- **Daily Reset**: Since `UsageLog` uses the `date` string as a key, a new day automatically starts with 0 usage (because no log exists for the new date yet).
