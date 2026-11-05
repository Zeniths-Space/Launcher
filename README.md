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
  it, then a page per category: Appearance (wallpaper, pure black in dark mode), Dock (coming
  soon), Home screen (clock, swipe hint), App drawer (app names under icons), Gestures and About.
- Gestures on the home screen: swipe down opens the notification panel (or quick settings, or
  nothing), double tap can lock the screen. They need Zenith gestures, an accessibility service
  you turn on once from Settings > Gestures: Android lets a third-party launcher open the panel or
  lock the screen only that way. It asks for no events and cannot read your screen. On Android 13+
  a sideloaded app's switch may be greyed out until App info > ⋮ > Allow restricted settings.
- Wallpaper: long-press the home screen > Wallpaper. Pick a photo with Android's photo picker,
  then crop and set it with the system's wallpaper screen. No storage permission.

## Try it and make it your home app

The launcher also has an ordinary app icon, so you can try it without changing anything:
open **Zenith Launcher** from your current launcher.

To use it as the home screen, tap the card at the top of its settings, or go to Settings > Apps > Default apps > Home app (Samsung: Settings >
Apps > Choose default apps > Home app) and pick Zenith Launcher. To go back, pick your previous
home app (for example One UI Home) in the same place. Your old launcher stays installed.

## Limitations

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
