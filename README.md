# Minimalist

A minimalist Android home screen with friction-based app blocking, built for one phone
(Oppo Reno 14, ColorOS 16, Android 16) and sideloaded. No internet permission: everything
stays on the device.

Work happens in phases. **Phase 1 (this build):** home screen, favourites, search, hidden apps,
settings, local storage. Blocking, friction, modes and the setup wizard come in later phases.

## Get the APK

1. Open the repository on GitHub → **Actions** → **Build debug APK**.
2. Open the latest green run and download the `launcher-debug-apk` artifact (a zip).
3. Unzip it to get `app-debug.apk` and copy it to the phone.

Every build is signed with the same debug key (`app/debug.keystore`), so a new APK installs
over the old one and keeps your data.

## Install on ColorOS

1. Open the APK in Files and allow installing from that app when asked.
2. If ColorOS warns about an unknown app, choose to install anyway.
3. Open **Minimalist**, long-press the clock → **Settings** → **Set as Home app**.
   If no dialog appears: Settings → Apps → Default apps → Home app → Minimalist.

## Using it

- Swipe up or tap **Search** to find an app by typing. There is no app drawer.
- Long-press an app in search to add it to favourites (up to 6), hide it, or open app info.
- Favourites change position every day.
- Tap the line under the clock to set today's intention.
- Long-press the clock, or search "launcher settings", to open settings.

## Build locally

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 36).
