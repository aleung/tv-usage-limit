# TV Usage Limit App - Walkthrough

## Project Overview
I have implemented the **TV Usage Limit** Android application.
- **Goal**: Limit TV watching time for specific profiles (e.g., Child) and enforce breaks.
- **Key Features**: Auto-start, Overlay Warning, PIN Override, Auto-Shutdown (Sleep).

## Implementation Details
- **Architecture**: MVVM (lite), Room Database, Foreground Service.
- **TimeTrackingService**: Logic hub. Tracks usage, checks limits, manages `WindowManager` overlay.
- **AdminReceiver**: Used to turn off the screen (`lockNow()`).
- **Data**: Pre-populated with "Child" (Restricted) and "Parent" (Unrestricted) profiles.

## How to Build & Run
The project uses Gradle Wrapper and can be built via command line or Android Studio.

### Command Line
1.  **Build Debug APK**:
    ```bash
    ./gradlew assembleDebug
    ```
2.  **Output**:
    The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`
3.  **Install via ADB**:
    ```bash
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ```

### Android Studio
1.  Open the project folder `/home/leoliang/projects/tv-usage-limit` in Android Studio.
2.  Select `app` configuration and click **Run**.


## Verification Steps (Manual)

### 1. Permissions Setup (First Run)
- Upon launch, click **Grant Overlay Permission**.
- Click **Grant Admin Permission** and click "Activate".
- Click **Start Service Manually** (or reboot device).

### 2. Testing Limits
- **Default Profile**: The app starts in "Child" profile.
- **Session Limit**: Defaults to 45 mins. To test quickly, you can edit `AppDatabase.kt` line 48 to a smaller value (e.g., 1 minute) and re-install.
- **Warning**: When time is up, a black overlay appears.
- **Action**:
    - Wait 10s -> Screen should turn off (Sleep).
    - Enter `1234` (Parent PIN) -> Overlay disappears, profile switches to Parent.
    - Enter `0000` (Child PIN) -> Overlay disappears (if limits allow, otherwise reappears).

### 3. Screen Off Logic
- Turn off the screen (or let it sleep).
- Turn it back on.
- **Verify**: App logs should show "Reset to Child Profile". Usage tracking restarts for Child.
