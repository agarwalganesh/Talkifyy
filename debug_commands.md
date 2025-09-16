# Debug Commands for Talkifyy

## If the app crashes during your presentation, use these commands:

### 1. View Live Logs (Run this in terminal/command prompt)
```bash
adb logcat | findstr "Talkifyy\|AndroidRuntime\|FATAL"
```

### 2. Clear App Data (Reset app to fresh state)
```bash
adb shell pm clear com.example.talkifyy
```

### 3. Check if App is Installed
```bash
adb shell pm list packages | findstr talkifyy
```

### 4. Force Stop and Restart App
```bash
adb shell am force-stop com.example.talkifyy
adb shell monkey -p com.example.talkifyy 1
```

### 5. Install Fresh APK
```bash
.\gradlew.bat installDebug
```

### 6. Check Device Connection
```bash
adb devices
```

## Quick Recovery Steps:
1. Run `adb shell pm clear com.example.talkifyy` to reset the app
2. Run `.\gradlew.bat installDebug` to reinstall
3. Launch the app

## Emergency Backup Plan:
If the app keeps crashing, you can:
1. Show the build process working
2. Explain the features using the code
3. Use an Android emulator instead of physical device