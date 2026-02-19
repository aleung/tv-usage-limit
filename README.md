# TV Usage Limit

**TV Usage Limit** is an Android TV / Google TV application designed to help families manage screen time effectively. It enables parents to enforce healthy viewing habits through a dual-profile system, daily limits, and mandatory rest periods.

## Key Features

*   **Dual Profile System**: Comes with two built-in profiles:
    *   **Parent**: Unrestricted access with full control.
    *   **Child**: Restricted access with configurable time limits.
*   **Smart Time Management**:
    *   **Daily Usage Limit**: Set the maximum viewing time allowed per day.
    *   **Session Limit**: Limit the duration of a single continuous viewing session.
    *   **Mandatory Rest**: Enforce a "cooldown" break period between sessions.
    *   **App Restrictions**: Block specific apps (e.g., YouTube) for restricted profiles.
*   **Visual Enforcement**:
    *   A persistent "health bar" style overlay appears when time is running low.
    *   Warning messages display progressively as the limit approaches.
*   **Strict Enforcement**:
    *   Automatically puts the TV to sleep if limits are reached and ignored.
    *   Automatically reverts to the **Restricted (Child)** profile whenever the TV screen is turned off, ensuring restrictions are active on the next startup.
*   **Secure Administration**: PIN protection is required to access the Admin/Configuration menu or switch to the Parent profile.

## Usage Guide

### 1. Initial Setup
Upon first launch, the app initializes the default profiles. You will need to grant specific permissions for the app to function correctly (see [Installation](#installation--setup)).

### 2. Configuring Limits
1.  Navigate to the **Admin** dashboard (requires PIN).
2.  Select **Edit Profile**.
3.  Adjust the following specific settings for the **Child** profile:
    *   **Daily Limit**: Total minutes allowed per day (e.g., 120 mins).
    *   **Session Limit**: Max duration for one sitting (e.g., 45 mins).
    *   **Rest Duration**: Minimum break time required before a new session can start (e.g., 15 mins).
    *   **Restricted Apps**: Select specific apps to block completely for this profile.

### 3. Monitoring & Warnings
*   **Green/Yellow/Red Overlay**: A progress bar indicates remaining time.
*   **Time-Left Warning**: The overlay becomes more prominent as the session or daily limit nears zero.
*   **Lockdown**: When time expires, a 10-second countdown begins. If the profile is not switched, the TV screen will turn off.

## Installation & Setup

### Prerequisites
*   Android TV or Google TV device/emulator (API Level 21+ suggested).

### Building the APK
Clone the repository and build the debug APK using Gradle:

```bash
./gradlew assembleDebug
```

### Required Permissions
To ensure the app can properly enforce limits, you must grant the following permissions immediately after installation:

1.  **Display Over Other Apps (Overlay)**: Required to show the time-remaining bar and warning dialogs over other apps (like YouTube or Netflix).
2.  **Device Admin**: Required to lock the screen (put the TV to sleep) when time runs out.
3.  **Usage Access**: Required to detect which app is currently running so restricted apps can be blocked.

## License

This project is licensed under the Apache License, Version 2.0.

```
Copyright 2026 Leo Liang <leoliang@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
