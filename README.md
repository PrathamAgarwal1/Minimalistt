# Minimalist

A minimalist Android home screen with friction-based app blocking, built for one phone
(Oppo Reno 14, ColorOS 16, Android 16) and sideloaded. No internet permission: everything
stays on the device.

Work happens in phases. **Done:**

1. Home screen, favourites, search, hidden apps, settings
2. Blocking: durations, schedules, daily limits, block screen, urge wave
3. Friction (countdown, reason, session length) and wanting vs liking
4. Put away, morning and night modes
5. Held notifications, batching, VIPs, urgent WhatsApp and quick reply
6. Website blocking and the single allowed browser
7a. Unlock challenges, 24-hour cooldowns, letters, emergency unlock, Settings guard, Device Admin

Next: the night and morning rework (7b), earned screen time and stats, the setup wizard.

## Get the APK

On the phone, open one of these links. They download the newest build straight away, with no zip:

- **Everyday:** https://github.com/PrathamAgarwal1/Minimalistt/releases/download/latest-debug/minimalist.apk
- **Testing (test mode, adb reset):** https://github.com/PrathamAgarwal1/Minimalistt/releases/download/latest-debug/minimalist-debug.apk

Both are signed with the same key, so either installs over the other and keeps your data.

Bookmark it or add it to the home screen of your browser. Every push replaces the file at the
same link, about four minutes after the push. The release page shows which build it is:
https://github.com/PrathamAgarwal1/Minimalistt/releases/tag/latest-debug

The same APK is also attached to each run under **Actions → Build debug APK** as the
`launcher-debug-apk` artifact (that one is a zip).

Every build is signed with the same debug key (`app/debug.keystore`), so a new APK installs
over the old one and keeps your data.

## Install on ColorOS

1. Open the APK in Files and allow installing from that app when asked.
2. If ColorOS warns about an unknown app, choose to install anyway.

### "This app can request access to sensitive data…"

Google Play Protect's fraud protection blocks sideloaded apps that use an accessibility
service when they are installed from a browser, chat app or file manager. The dialog has no
install button. Two ways round it:

- **Pause Play Protect:** Play Store → profile picture → Play Protect → gear icon → turn off
  *Scan apps with Play Protect*, install the APK, then turn it back on. If Play Protect later
  offers to remove Minimalist, keep it. Repeat for each update.
- **Use adb (Play Protect stays on):** enable Developer options (Settings → About device →
  Version → tap Build number 7 times), turn on USB debugging, then run
  `adb install -r minimalist-debug.apk`. `-r` updates in place and keeps your data.
3. Open **Minimalist**, long-press the clock → **Settings** → **Set as Home app**.
   If no dialog appears: Settings → Apps → Default apps → Home app → Minimalist.

## Turn on blocking

Blocking runs through an accessibility service. Because the APK is sideloaded, Android 16 may
grey it out as a "Restricted setting" the first time:

1. Settings → Apps → Minimalist → three-dot menu (top right) → **Allow restricted settings**.
2. Settings → Accessibility → Downloaded apps (or Installed apps) → **Minimalist blocking** → on.

Launcher settings → Blocking shows whether the service is on and has buttons for both pages.

Phone, emergency calls, messages, the clock and this launcher can never be blocked. The Play
Store is never blocked or watched.

## Other permissions

All of them are optional; each feature says what it needs on its settings page.

- **Notification access** (Settings → Notifications and VIPs): holds and saves notifications.
  Sideloaded, so it may need *Allow restricted settings* first, like the accessibility service.
- **Post notifications**: for the summaries of held notifications.
- **Display over other apps** (Settings → Put away, morning and night): the red tint at night.
- **ColorOS background limits**: Settings → Apps → Minimalist → allow auto-launch and
  *Allow background activity* in Battery, so the guard service isn't stopped.

## Getting in early, and loosening things

- **Unlock challenges:** a blocked app, site, Put away or a mode can be opened early only by
  solving problems (more and harder the longer the block has left). You get a 5, 15 or 30-minute
  pass; the block keeps its end time.
- **Cooldown:** removing or loosening anything waits 24 hours, after one of your letters to
  yourself is shown. Adding or extending applies at once. Settings → Protection lists what's
  waiting.
- **Emergency unlock:** lifts everything for 15 minutes, once a calendar month.
- **Settings guard:** while anything is protected, Android Settings stays usable except
  Minimalist's own pages, accessibility, notification access, Device Admin, uninstalling,
  Date & time and Developer options. During modes and challenges all of Settings is closed.
- **Uninstall protection:** Settings → Protection → turn on (Device Admin).

Known gaps: safe mode turns every downloaded app off, and the debug build's adb reset clears
everything. Use the everyday build when you mean it.

## Debug tools (debug builds only)

Clear every block, schedule, limit and today's counters:

```
adb shell am broadcast -a com.minimalist.launcher.DEBUG_RESET
```

If the app isn't running, address it directly:

```
adb shell am broadcast -a com.minimalist.launcher.DEBUG_RESET -p com.minimalist.launcher
```

**Test mode** (Settings → Blocking → Debug build) shrinks durations: an hour of a new block
becomes a second (a day 24 seconds, a month 12 minutes); minutes of daily limits, friction
sessions, Put away and morning mode become seconds; friction countdowns are a fifth as long.
Neither exists in release builds. The reset also ends Put away and morning mode.

## Using it

- Swipe up or tap **Search** to find an app by typing. There is no app drawer.
- Long-press an app in search to add it to favourites (up to 6), hide it, or open app info.
- Favourites change position every day.
- Tap the line under the clock to set today's intention.
- Long-press the clock, or search "launcher settings", to open settings.
- Settings → Blocking: block an app for hours to months, add schedules (e.g. 22:00–08:00, or
  class hours on weekdays), set daily limits on time or opens, and slow apps down with friction.
- **Put away** on the home screen: only essentials work until the time is up.
- Settings → Put away, morning and night: automatic Put away, morning and night times, habits,
  and which apps keep working in modes.
- Settings → Notifications and VIPs: batching times, VIP contacts, urgent messages per session.
- Settings → Websites and browsers: the one allowed browser and extra blocked sites.

## Build locally

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 36).
