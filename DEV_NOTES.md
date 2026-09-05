# Olympus Merge 2048: Zeus Crystal Storm

An offline portrait merge game for Android and desktop, built around the supplied Olympus visual design. Swipe the board to build combos, use divine boosters, or enter the playable Zeus Crystal Storm bonus mode to shatter gems into coins.

## Run locally

Use JDK 17 and Gradle 8.11+:

```powershell
$env:JAVA_HOME='F:\jdk17'
$env:Path='F:\jdk17\bin;' + $env:Path
gradle :lwjgl3:run
```

The desktop launcher is the fastest way to play. Android builds use `:android:assembleDebug`.

## Current playable slice

- Pixel-faithful 9:20 Olympus Merge presentation based on the approved reference screen.
- Swipe interactions, combo/score/glory progression and undo/hammer/shuffle controls.
- Playable 30-second Zeus Crystal Storm bonus game with tappable crystals, lightning strikes, coins, gems and persisted rewards.
- Full-screen portrait Android runtime with an equivalent 432×960 desktop QA window.

All gameplay remains fully offline; progress and rewards are stored only on the device.
