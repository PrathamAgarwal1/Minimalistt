# Quiet

A minimalist Android home screen with strong, friction-based app and website blocking, built
for ColorOS 16 on Android 16 and sideloaded. No internet permission: everything stays on the
device.

All nine build phases are done:

1. Home screen, favourites, search, hidden apps, settings
2. Blocking: durations, schedules, daily limits, block screen, urge wave
3. Friction (countdown, reason, session length) and wanting vs liking
4. Put away mode
5. Held notifications, batching, VIPs, urgent WhatsApp and quick reply
6. Website blocking and the single allowed browser
7. Unlock challenges, 24-hour cooldowns, letters, emergency unlock, Settings guard, Device Admin;
   night from bedtime until the phone opens, wind-down, the morning screen; appearance options
8. Earned screen time, stats, CSV export
9. First-run setup, the status screen, this README

## Get the APK

On the phone, open one of these links. They download the newest build straight away, with no zip:

- **Everyday:** https://github.com/PrathamAgarwal1/Quiet/releases/download/latest-debug/quiet.apk
- **Testing (test mode, adb reset):** https://github.com/PrathamAgarwal1/Quiet/releases/download/latest-debug/quiet-debug.apk

Every push to GitHub rebuilds both, about four minutes later, at the same links. The release
page shows which build they are: https://github.com/PrathamAgarwal1/Quiet/releases/tag/latest-debug

Each GitHub Actions run (**Actions → Build debug APK**) also keeps both APKs as the
`launcher-debug-apk` artifact, a zip.

Both builds are signed with the same key (`app/debug.keystore`), so either installs over the
other and over older builds, keeping your data.

## Install on ColorOS

1. Open the APK from the download notification or Files, and allow installs from that app.
2. If ColorOS warns about an unknown app, choose **Install anyway**.
3. Open **Quiet**. The setup screen walks through every permission.

### "This app can request access to sensitive data…"

Google Play Protect blocks sideloaded apps that use accessibility or notification access when
they come from a browser, chat app or file manager. The dialog has no install button. Either:

- **Pause Play Protect:** Play Store → profile picture → Play Protect → gear icon → turn off
  *Scan apps with Play Protect*, install the APK, then turn it back on. If Play Protect later
  offers to remove Quiet, keep it. Repeat for each update.
- **Use adb (Play Protect stays on):** enable Developer options (Settings → About device →
  Version → tap Build number 7 times), turn on USB debugging, then run
  `adb install -r quiet.apk`. `-r` updates in place and keeps your data. Apps installed this
  way are also spared the restricted-settings step below.

### Allow restricted settings (Android 16)

Sideloaded apps can't switch on accessibility or notification access until you allow it:

1. Try to switch on **Quiet blocking** in Accessibility once and accept the refusal.
2. Settings → Apps → Quiet (or search "Quiet") → three-dot menu (top right) →
   **Allow restricted settings** → confirm.
3. Switch on **Quiet blocking** (Accessibility) and **Quiet notifications**
   (Notification access).

### One-time adb command for grayscale

Wind-down and night can turn the screen grey. That needs a permission only adb can grant:

```
adb shell pm grant com.minimalist.launcher android.permission.WRITE_SECURE_SETTINGS
```

Without it everything else works; the screen just stays in colour.

### ColorOS background limits

Settings → Setup and status has buttons for each: allow **auto-launch**, set battery to
**Allow background activity**, **lock Quiet in recent apps** (open recents, three dots on
Quiet, Lock) and make it the **default Home app** (Settings → Apps → Default apps → Home app).

## Using it

- **Home:** time, date and a quiet line (screen time, next alarm, battery: your choice), up to 6
  text-only favourites that change places every day (an app with a daily limit shows what's
  left, e.g. "12m left"), and Put away, Search and Urgent message. There is no app drawer: swipe
  up or tap Search and type.
- **Long-press** an app on the home screen or in search to block it, add it to favourites, hide
  it, or open app info. Long-press the clock for a quick menu: block an app, Put away, Stats,
  Settings, Setup and status.
- **Search** finds Quiet's own pages too (in the accent colour): type "block", "stats",
  "weekly", "night", "emergency" and so on.
- **Settings** is grouped into Focus (blocking, websites, earned time), Modes (Put away, night,
  notifications), You (stats, weekly review, emergency and letters) and Phone (Home screen,
  appearance, setup). Each row says in one line how things stand. Home app, favourites and hidden
  apps live under Home screen.
- **Blocking** (Settings → Blocking): block an app for hours to months, schedules (e.g.
  22:00–08:00, or class hours on weekdays), daily limits on time or opens, and friction for apps
  that are allowed but slowed down. Phone, emergency calls, SMS, the clock, the keyboard, this
  launcher and the Play Store can never be blocked.
- **Parts of apps** (Settings → Blocking): block Instagram Reels and YouTube Shorts and keep the
  rest of each app. Opening one steps back with a short note; going straight back in within a few
  seconds sends you home. Unblocking a part waits out the cooldown. If an app update breaks it,
  tap the part to teach Quiet again: the app opens, you go to Reels or Shorts and press Home, and
  Quiet keeps the screen names that only appeared there (only ones that look like reels, clips or
  shorts, so it can't block an ordinary screen).
- **Still worth it?** (Settings → Blocking): after 20 minutes in one app without a break (10, 30,
  45 or off), Quiet asks whether it's still worth it. Rate it and stop, or go back in for another
  stretch. Answers show up in wanting vs liking. Essentials are never asked about; asking less
  often or switching it off waits out the cooldown.
- **If blocking gets switched off** (ColorOS sometimes does this), the home screen says so in the
  accent colour and a notification appears; both lead to Setup and status to turn it back on.
- **Websites** (Settings → Websites and browsers): a blocked app's site is blocked with it in
  Chrome, Brave, Edge and Firefox (incognito too); add your own; choose the one allowed browser.
  Only real browsers count: apps that just open some links, like WPS Office, aren't blocked.
- **Put away:** only calls, messages, the clock and your essentials work. Extend, never end early.
  It can start itself after unplugging, during study hours, or after too many pickups. A Put away
  run to its full focus length counts as a focus session.
- **Night** (Settings → Put away and night): from bedtime until the phone opens, with separate
  weekend times. The wind-down before it turns on grayscale and a red tint and doubles friction
  countdowns. Only essentials, the clock, and one audio and one notes app work.
- **Morning screen:** once when the phone opens, your habits and what came in overnight.
- **Notifications** (Settings → Notifications and VIPs): blocked apps' notifications are saved,
  others wait during modes or until batch times; calls, SMS and VIPs always come through.
  Urgent message: pick a VIP, type, WhatsApp opens on their chat for a minute past any block;
  quick replies go through WhatsApp's own notification. There's no limit on how many you send.
- **Earned screen time** (Settings → Earned screen time): chosen apps open only on time earned
  by steps and focus sessions. Every rule takes a preset or any number you type: steps per
  reward, minutes earned, focus session length, minutes per session, free minutes to start each
  day, and the most you can earn in a day.
- **Stats** (Settings → Stats and export): today and the last 7 days, and an export of everything
  as CSV files in one zip, saved wherever you choose.
- **Weekly review:** on Sunday from 18:00, once: this week's screen time against last week's, the
  best and worst day, urges resisted, Put away time and early unlocks, and the app you want most
  but enjoy least, with "Block it for a week". Also under Stats.
- **Appearance:** text size, accent colour, clock weight, centred or left-aligned home.

## Getting in early, and loosening things

- **Unlock challenges:** a blocked app, site, Put away or night can be opened early only by
  solving problems: mental arithmetic, sequences, equations and exact typing. More and harder the
  longer the block has left, plus two for each early unlock this week. A wrong answer or timeout
  adds one. Leaving the screen starts over. You get a 5, 15 or 30-minute pass; the block keeps its
  end time. At night the challenge grows with how late it is, and in the last two hours before
  opening, solving it opens the phone for the morning.
- **Cooldown:** removing or loosening anything waits 24 hours, after one of your letters to
  yourself is shown. Adding or extending applies at once. Settings → Protection lists what's
  waiting.
- **Emergency unlock:** lifts everything for 15 minutes, logged. Three a calendar month are free;
  each one after that needs a challenge of 15 hard problems and a typing passage, two more
  problems for every extra one already used that month.
- **Changing the clock** doesn't end anything early: blocks, passes, modes and cooldowns run on
  time measured since the phone started, and a clock moved by more than a few seconds is
  ignored. Restarting the phone accepts its clock again, but only forwards from the last time
  Quiet saw.
- **Settings guard:** while anything is protected, Android Settings stays usable except
  Quiet's own pages, accessibility, notification access, Device Admin, uninstalling,
  Date & time and Developer options. During modes and challenges all of Settings is closed.
- **Uninstall protection:** Settings → Protection (Device Admin, with no other powers).

Known gaps: safe mode turns every downloaded app off, and the testing build's adb reset clears
everything. Use the everyday build when you mean it.

## Debug tools (testing build only)

Clear every block, schedule, limit, friction app, mode, pass and waiting change:

```
adb shell am broadcast -a com.minimalist.launcher.DEBUG_RESET
```

If the app isn't running, address it directly:

```
adb shell am broadcast -a com.minimalist.launcher.DEBUG_RESET -p com.minimalist.launcher
```

**Test mode** (Settings → Blocking → Debug build) shrinks everything: an hour of a block becomes
a second; minutes of limits, sessions, Put away and the emergency unlock become seconds; passes
last six seconds per minute; challenges are one easy problem; the cooldown is a minute; the
free emergency unlocks reset every hour and an extra one is one easy problem. Turning test mode on is itself a loosening
change, so run the reset first to switch it on straight away. Neither exists in the everyday build.

## Build locally

```
./gradlew assembleDebug assembleRelease testDebugUnitTest
```

Requires JDK 17+ and the Android SDK (compileSdk 36). CI does the same on every push and also
checks that the APK has no internet permission and that the everyday build has no debug reset.
