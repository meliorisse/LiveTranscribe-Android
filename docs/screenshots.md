# Capturing README screenshots

Use a disposable emulator. The opt-in fixture replaces its app history with sample
English/Vietnamese entries and downloads the two small Vosk speech models if needed.
It renders the actual app and overlay; it does not alter screenshot pixels.

Build with JDK 17 and your Android SDK configured:

```sh
./gradlew assembleGithubDebug assembleGithubDebugAndroidTest
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
adb install -r app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
adb shell pm grant com.charles.livecaptionn android.permission.RECORD_AUDIO
adb shell appops set com.charles.livecaptionn SYSTEM_ALERT_WINDOW allow
adb shell cmd uimode night no
adb shell am instrument -w -e class com.charles.livecaptionn.ReadmeScreenshots -e readmeScreenshots true -e appearance light com.charles.livecaptionn.test/androidx.test.runner.AndroidJUnitRunner
adb shell cmd uimode night yes
adb shell am instrument -w -e class com.charles.livecaptionn.ReadmeScreenshots -e readmeScreenshots true -e appearance dark com.charles.livecaptionn.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.charles.livecaptionn/files/readme/ scratch/readme-screenshots/
```

The fixture is skipped unless explicitly enabled. Inspect the captures before copying
them into docs/assets/screenshots. Current mappings:

| Captured file | Documentation file |
| --- | --- |
| main-light.png | main.png |
| main-dark.png | main_dark.png |
| languages-light.png | languages_light.png |
| languages-dark.png | languages_dark.png |
| history-dark.png | history.png |
| overlay-light.png | overlay_listening.png |
| overlay-home-light.png | overlay_home.png |

The overlay is the actual WindowManager view, populated with sample text and set to
90% opacity. Screenshots show the emulator's genuine hardware compatibility result.
The older demo video and Play Store graphics are historical assets, not screenshots
of the current fork.
