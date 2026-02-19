# TV Usage Limit App - Design Specification

## 1. Use Cases

| ID | Actor | Scenario | Outcome |
| :--- | :--- | :--- | :--- |
| **UC-01** | **Child** | Turns on the TV. | TV boots, app auto-starts. **Default Profile** (e.g., "Child") is automatically active. Usage tracking begins immediately. |
| **UC-02** | **Child** | Watches continuously until **Session Limit** (e.g., 45 mins) is reached. | A "Time Up" warning appears. Child must stop watching. If they don't input a PIN, TV goes to sleep in 10s. |
| **UC-03** | **Child** | Tries to turn TV back on immediately after Session Limit. | TV wakes up, but "Rest Required" warning overlay appears immediately. Cannot dismiss without PIN. |
| **UC-04** | **Child** | Waits for **Rest Duration** (e.g., 15 mins) and turns TV back on. | Warning is gone. New session begins. |
| **UC-05** | **Child** | Reaches **Daily Cumulative Limit** (e.g., 2 hours). | "Time Up" warning appears. No more watching allowed for the day (unless PIN override). |
| **UC-06** | **Parent** | Wants to watch TV (unlimited). | When Warning appears (or via App shortcut), enters **Parent PIN**. Switches profile to "Parent" (Unlimited). |
| **UC-07** | **Parent** | Finished watching, turns off TV. | Next time TV turns on, it reverts to **Default Profile** automatically. |
| **UC-08** | **Parent** | Enters Admin Dashboard to change limits. | Modifies configuration (e.g., Daily Limit) for a profile. Saves changes. Updates apply immediately. |
| **UC-09** | **Child** | Launches a **Restricted App** (e.g., YouTube). | App is immediately blocked. A red "App Blocked" warning screen appears. Child can "Go Back" or "Switch Profile". |

## 2. UX & Workflows

### A. System Boot
1.  **Event**: TV Power On (or Restart).
2.  **Action**: `BootReceiver` silently starts `TimeTrackingService`.
3.  **Feedback**: Brief Toast message: *"TV Limit Active: [Profile Name]"*.
4.  **State**: Usage timer starts counting immediately. NO Main Activity is launched.

### B. App Home Screen (Manual Launch)
1.  **Event**: User launches App from TV Launcher.
2.  **UI**: Main Dashboard.
    - **Status**: Displays **Current Profile Name**.
    - **Controls**:
        - "Switch Profile" Button.
        - "Settings" Button (Admin).
    - **First Run**: Display Default PINs (Admin: `1234`, Child: `0000`).

### C. Usage Warning (The "Limits")
1.  **Trigger**: `Session Limit` or `Daily Limit` reached.
2.  **UI**: **System Overlay** (High Priority Window).
    - Background: Semi-transparent black (dimmed screen).
    - Center Box:
        - Message: "Time is up! Take a break."
        - Timer: "Turning off in 10... 9..."
        - Buttons: "Switch Profile".
3.  **Interaction**:
    - **No Action**: At T=0, call `DevicePolicyManager.lockNow()` (Sleep).
    - **Switch Profile**:
        - Launches `ProfileSelectionActivity` (CLEAR_TOP/NEW_TASK).
        - Overlay remains visible until profile switch is committed and new profile restricts (if applicable).
        - *Note*: If user cancels switch, overlay is still there (since Activity finished).
    - **Back Button**: Disabled.

### D. App Restriction (New)
1.  **Trigger**: User launches an app listed in the current profile's **Restricted Apps**.
2.  **Detection**: Service monitors foreground app via `UsageStatsManager`.
3.  **UI**: **App Blocked Activity**.
    - **Style**: Black background, RED warning icon.
    - **Message**: "APP BLOCKED".
    - **Controls**:
        - "Go Back": Returns to Launcher.
        - "Switch Profile": Launches Profile Selection.

### E. Admin / Settings UI
*Entry Point: "Settings" Button on Home Screen.*
1.  **Auth**: Require **Dedicated Admin Code** (Default: `1234`).
2.  **Dashboard**:
    - Current Usage Stats (Today's viewing time per profile).
    - List of Profiles (Select to Edit).
    - **Edit Profile**:
        - Change PIN, Limits (Daily, Session), Rest Duration.
        - **Manage Restricted Apps** (Select blocklist).
        - Toggle Restricted status.
    - Global Settings (Admin PIN change).

## 3. Configuration Items

### Profile Settings
Each profile (needs at least one "restricted" and one "unrestricted") has:
- **Name**: (e.g., "Child", "Parent")
- **PIN**: 4-digit code (Optional for Child, Mandatory for Parent).
- **Restricted Status**: Boolean.
- **Daily Limit**: (Hours:Minutes) or `Unlimited`.
- **Session Limit**: (Hours:Minutes) or `Unlimited`.
- **Rest Duration**: (Minutes).
- **Restricted Apps**: List of package names to block.

### Global Settings
- **Admin Code**: Dedicated PIN for accessing settings (Default: `1234`).
- **Default Profile**: Which profile loads on Boot/Restart (Default: "Child").
- **Auto-Revert**: Always revert to Default Profile when screen turns off.

## Key Design Decisions for Discussion
1.  **Sleep Mechanism**: We are using `lockNow()` (Screen Off).
    - *Constraint*: If the user presses "Power" immediately again, the overlay must reappear instantly (Service must detect `SCREEN_ON` and re-check limits).
2.  **App Blocking**:
    - **Mechanism**: `UsageStatsManager` polling (1s interval) is used to detect foreground apps due to Android restrictions on background process start.
    - **UX**: A fullscreen Activity is used instead of an Overlay to ensure the blocked app is fully covered and focus is stolen.
3.  **Profile Switching**:
    - Should switching to "Parent" be temporary (next sleep reverts) or permanent (until manual switch)?
    - *Recommendation*: **Temporary**. If Parent forgets to switch back, Child gets unlimited access next morning. Protocol: *Always boot into Restricted Default*.

