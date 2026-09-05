# QA report — 2026-08-15

## Verified

- `:engine:test` — passed: 2 tests, 0 failures.
- `:core:compileKotlin :lwjgl3:compileKotlin` — passed.
- `:android:assembleDebug` — produced `android/build/outputs/apk/debug/android-debug.apk`.
- APK SHA-256: `ABFF467C99BE1C0714D2A74EC0AD54AA309CC9416641559CA441CB427A5EE675`.
- `design/CAMPAIGN_MANIFEST.csv` has one header plus 90 campaign rows.
- Physical Android device `SM02E4060323025` (ARM64): installed and launched successfully; `studio.cortex.thunderbound/.AndroidLauncher` was the top resumed activity. A missing `libgdx.so` packaging issue was found and corrected by packaging the ARM native libraries.
- Olympus Merge main screen: captured at 432×960 on desktop and at the physical device's 1200×2670 resolution; portrait composition, artwork scaling and viewport framing match the supplied reference.
- Zeus Crystal Storm: launched through the center lightning medallion on the physical device; countdown, crystal spawning, reward HUD and portrait layout verified.
- `:engine:test :android:testDebugUnitTest :android:assembleDebug` — successful after the portrait redesign.

## Not yet verified

Release AAB signing, store metadata and a long-session soak test were not performed. The debug APK and primary interaction paths are verified, but release packaging remains a separate gate.
