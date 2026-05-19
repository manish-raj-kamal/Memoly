# Memoly

Memoly is an offline-first Android note and capture app built with Kotlin, Jetpack Compose, Room, DataStore, WorkManager, and ML Kit OCR.

It supports:

- Timeline-based note browsing
- Quick capture overlay
- Share-to-app text, links, images, and files
- Read-only detail view with edit flow
- Reminder scheduling with notification actions
- Screenshot detection and OCR
- Favorites, pinning, tags, and search

## Tech Stack

- Kotlin
- Jetpack Compose
- AndroidX Navigation
- Room
- DataStore
- WorkManager
- Coil
- ML Kit Text Recognition

## Requirements

- Windows, macOS, or Linux
- Android Studio with Android SDK installed
- JDK 11
- Android device or emulator
- Minimum Android version: Android 8.0 (API 26)

## Clone And Open

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Gradle sync completely.
4. If prompted, install the required Android SDK/platform tools from Android Studio.

## Run In Android Studio

1. Open the project.
2. Wait for Gradle sync to finish.
3. Connect a physical Android device with USB debugging enabled, or start an emulator.
4. Select the `app` run configuration.
5. Click `Run`.

## Run From Terminal

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Compile Kotlin only:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

There is also a helper script for Windows:

```powershell
.\launch-wireless.ps1
```

That script can:

- detect connected devices
- detect wireless-debug targets
- start an emulator
- build the app if sources changed
- install and launch the debug APK

## First-Run Permissions

Depending on Android version and which features you use, Memoly may ask for:

- `POST_NOTIFICATIONS` for reminder notifications
- `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE` for screenshot detection and image access
- Accessibility permission for the quick capture gesture service

## Reminder Behavior

- Reminders are stored locally in Room.
- Notifications are scheduled through WorkManager.
- Reboot recovery is handled by `BootReceiver`.
- Reminder notifications support direct `Reschedule` and `Mark Done` actions.
- `Reschedule` currently snoozes the reminder by 1 hour without opening the app.

## Project Structure

```text
app/src/main/java/com/memoly/dock/
  data/         Room entities, DAO, repository
  domain/       domain models and reminder parsing
  receivers/    boot and reminder notification receivers
  services/     accessibility, screenshot observer, OCR, QS tile
  settings/     DataStore preferences
  ui/           Compose screens, components, navigation, view models
  utils/        helpers for formatting, attachments, and file handling
  workers/      WorkManager reminder worker
```

## Files That Should Not Be Committed

Keep these local and out of Git:

- `local.properties`
- signing keystores
- `.idea/`
- build outputs
- device-specific or user-specific SDK paths

The current `.gitignore` already excludes the important local Android files, especially `local.properties`.

## GitHub Safety Check

I checked the tracked files in this repository and did not find obvious secrets like:

- API keys
- private tokens
- passwords
- keystore credentials
- committed `local.properties`

The current tracked files look generally safe to publish. A few notes:

- `launch-wireless.ps1` is safe; it reads local SDK paths at runtime but does not store secrets.
- `.vscode/tasks.json` is safe; it only launches the helper script.
- `.idea/` is currently untracked and should stay untracked.
- If you ever add signing configs, Firebase files, or API-backed features later, do not commit those credentials.

## Suggested Pre-Push Checklist

Before pushing new changes, quickly verify:

```powershell
git status --short
git ls-files | Select-String "local.properties|keystore|google-services|\.env"
```

And optionally scan for obvious secrets:

```powershell
rg -n "API_KEY|SECRET|TOKEN|PASSWORD|BEGIN PRIVATE KEY|ghp_|github_pat_|AIza" .
```

## Notes

- This project currently targets Android SDK 36 and uses recent AndroidX/Compose versions, so a fairly recent Android Studio install is recommended.
- Room schema history is stored under `app/schemas/`.
- `Academic_Project_Report.md` is part of the repository and appears to be documentation, not runtime-sensitive app data.
