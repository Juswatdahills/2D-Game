# Tiny Quest

A small landscape Android 2D RPG prototype built with Kotlin and a custom Canvas game view.

## Gameplay
- Move around the village using the left side of the screen.
- Get close to a slime and tap **ATTACK** on the right.
- Defeating slimes grants XP and coins.
- Defeat all three slimes to complete the prototype quest.

## Build
Open this repository in Android Studio, sync Gradle, then run the app on an Android device or emulator.

A GitHub Actions workflow builds a debug APK on every push to `main`. The APK is uploaded as the `tiny-quest-debug-apk` workflow artifact when the build succeeds.

Package: `com.juswatdahills.tinyquest`
