# Zenith Launcher

A private, offline Android home screen. Part of the Zenith's Space app collection.
Application ID `app.zenith.launcher`.

## Features

- Home screen over your wallpaper with the time and date. The clock updates on Android's minute
  tick while the home screen is showing; the launcher runs no timer of its own.
- Swipe up anywhere for the app drawer: every app with a launcher icon, alphabetical, work-profile
  apps included (with their badge). The drawer follows your finger, with the same timing as One UI
  Home (measured from screen recordings): home fades first, then the drawer rises over the dimmed
  wallpaper, and on release it settles without bounce. Pull down from the top of the list to close
  it the same way.
- Search at the top of the drawer. Matches the start of the name first, then the start of a word,
  then anywhere; case and accents are ignored. The keyboard's Go key opens the best match.
- Tap to open, with Android's own open animation and the app's own splash screen.
  Long-press for App info.
- Back, home or pulling down from the top of the list closes the drawer.
- The list updates when apps are installed, removed or changed, from Android's package events,
  never by polling.
- Long-press the home screen for a menu at your finger: Wallpaper and Settings.
- Settings (also the gear in the drawer): whether this is your home app, with a shortcut to change
  it, then a page per category: Appearance (wallpaper, pure black in dark mode), Dock (on or
  off, background bar, columns, rows, icon size, row spacing), Home screen (clock, swipe hint), App drawer (app names under icons), Gestures and About.
- Dock: apps at the bottom of the home screen, icons only, in a grid of 1 to 20 columns and 1 to
  20 rows (5 by 1 to start). Each app keeps its own slot and slots can stay empty. Icon size is a setting (32 to 96 dp); icons
  shrink below it when the columns are too narrow. Row spacing (0 to 40 dp) sets the gap between rows. A background bar is a setting. Making the
  grid smaller hides apps past it without forgetting them. It starts with your default Phone, Messages, Browser and Camera apps. Long-press an
  app in the drawer to add or remove it; long-press a dock app for Remove and App info, or keep
  holding and drag it to any slot: the other apps make room as you go, and an empty slot just takes it.
  Changing the column count rewraps the slots in reading order. Screen readers get Move left and Move right.
- Gestures on the home screen: swipe down opens the notification panel (or quick settings, or
  nothing), with nothing to set up. Double tap can lock the screen; that one needs Zenith
  gestures, an accessibility service you turn on once from Settings > Gestures, since Android has
  no other way for a third-party launcher to lock the screen. It asks for no events and cannot
  read your screen. On Android 13+ a sideloaded app's switch may be greyed out until App info >
  ⋮ > Allow restricted settings.
- Wallpaper: long-press the home screen > Wallpaper. Pick a photo with Android's photo picker,
  then crop and set it with the system's wallpaper screen. No storage permission.

## Try it and make it your home app

The launcher also has an ordinary app icon, so you can try it without changing anything:
open **Zenith Launcher** from your current launcher.

To use it as the home screen, tap the card at the top of its settings, or go to Settings > Apps > Default apps > Home app (Samsung: Settings >
Apps > Choose default apps > Home app) and pick Zenith Launcher. To go back, pick your previous
home app (for example One UI Home) in the same place. Your old launcher stays installed.

## Limitations

- **Swipe down uses a hidden Android method.** The notification panel and quick settings open
  through `StatusBarManager`'s expand methods, which are not in the public SDK but are allowed
  for apps (the same route Niagara, Nova and Lawnchair use), with the install-time
  `EXPAND_STATUS_BAR` permission. If a future Android blocks them, swipe down falls back to the
  Zenith gestures accessibility service and asks for it.

- **App open and close animation.** One UI Home animates the app's real window out of its icon and
  back into it, through remote app transitions, a permission Android grants only to the system's
  own launcher. Android also drops launch animations requested by other apps (`ActivityOptions`
  scale-up or clip-reveal; verified on a Galaxy S24 Ultra, even as the home app), so opening and
  closing use the system's animation. On One UI, Back and the Home button run one system
  transition through One UI's recents task, and the Home gesture is animated by One UI Home's
  recents component even when another home app is the default.
- Returning from an app can show the drawer's last frame for a moment before the home screen,
  since Android animates the launcher's last drawn frame.

## Build

Requirements: JDK 17 and the Android SDK with platform 37.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android Lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the application ID `app.zenith.launcher.dev` and show as "Zenith Launcher Dev",
so they install beside the release without a signature clash.

### Signed release build

Put a `keystore.properties` file (storeFile, storePassword, keyAlias, keyPassword) outside the
repository and point the Gradle property `zenithLauncherSigning` at it, for example in
`~/.gradle/gradle.properties`. Without it, release builds are left unsigned.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [PRIVACY.md](PRIVACY.md)
- [SECURITY.md](SECURITY.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [ROADMAP.md](ROADMAP.md)

## License

Not yet selected. It will be chosen before the first public release.
