# Memoly

Memoly is an offline-first Android note and capture app built with Kotlin and Jetpack Compose.

## Features

- Save notes, links, images, screenshots, and files
- Quick capture flow
- Read-only detail page with edit option
- Reminder scheduling and reminder notifications
- Favorites, pinning, tags, and search
- Screenshot detection and OCR support

## Requirements

- Android Studio
- JDK 11
- Android SDK installed through Android Studio
- Android device or emulator
- Minimum Android version: Android 8.0 (API 26)

## Open The Project

1. Clone the repository.
2. Open the project in Android Studio.
3. Wait for Gradle sync to finish.
4. Let Android Studio install any missing SDK components if prompted.

## Run The App

### Android Studio

1. Connect a physical device or start an emulator.
2. Select the `app` configuration.
3. Click `Run`.

### Terminal

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Compile Kotlin only:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Windows helper script:

```powershell
.\launch-wireless.ps1
```

## Permissions

Depending on the features you use, the app may request:

- notification permission for reminders
- media access for screenshots and images
- accessibility permission for quick capture gestures

## Project Structure

```text
app/src/main/java/com/memoly/dock/
  data/
  domain/
  receivers/
  services/
  settings/
  ui/
  utils/
  workers/
```

## Connect A Physical Device With ADB

### USB Debugging

1. On the phone, enable Developer options.
2. In Developer options, enable `USB debugging`.
3. Connect the phone to the computer with a USB cable.
4. Accept the RSA debugging prompt on the phone.
5. Check that ADB can see the device:

```powershell
adb devices
```

6. If the device appears as `device`, you are ready to run the app.

### Wireless Debugging

For Android 11 or newer:

1. Connect the phone and computer to the same Wi-Fi network.
2. On the phone, open Developer options.
3. Enable `Wireless debugging`.
4. Open `Wireless debugging` and choose `Pair device with pairing code`.
5. Pair from the computer:

```powershell
adb pair IP_ADDRESS:PAIR_PORT
```

6. Enter the pairing code shown on the phone.
7. Connect to the device:

```powershell
adb connect IP_ADDRESS:CONNECT_PORT
```

8. Confirm the device is available:

```powershell
adb devices
```

If you want the included helper script to detect and launch on available devices:

```powershell
.\launch-wireless.ps1
```
