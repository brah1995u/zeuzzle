# Build notes

- JDK: 17
- libGDX: 1.13.1
- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21
- Android SDK: compile/target 36, min 24

Run engine tests with `:engine:test`, desktop with `:lwjgl3:run`, and the debug APK with `:android:assembleDebug`.

The Android module has no network permission and forces portrait orientation. Shared runtime art is loaded from the root `assets/` directory for Android and desktop QA builds.
