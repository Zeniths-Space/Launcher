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
- Tap to open; long-press for App info. The launcher asks Android to grow the app out of its icon,
  but Samsung's system uses its own short cross-fade (see Limitations).
- Back, home or pulling down from the top of the list closes the drawer.
- The list updates when apps are installed, removed or changed, from Android's package events,
  never by polling.
- Settings: long-press the home screen (or the gear in the drawer). Shows whether this is your
  home app, with a shortcut to change it; wallpaper; clock and swipe hint on or off; app names
  under icons; pure black in dark mode.
- Wallpaper: long-press the home screen > Wallpaper. Pick a photo with Android's photo picker,
  then crop and set it with the system's wallpaper screen. No storage permission.

## Try it and make it your home app

The launcher also has an ordinary app icon, so you can try it without changing anything:
open **Zenith Launcher** from your current launcher.

To use it as the home screen, tap the card at the top of its settings, or go to Settings > Apps > Default apps > Home app (Samsung: Settings >
Apps > Choose default apps > Home app) and pick Zenith Launcher. To go back, pick your previous
home app (for example One UI Home) in the same place. Your old launcher stays installed.

## Limitations

- **App open and close animation.** One UI Home morphs an app's window out of its icon and back
  into it. That uses remote app transitions, which need a permission Android grants only to the
  system's own launcher (`CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`). No installable launcher can
  do it. Zenith Launcher passes a clip-reveal from the icon (`ActivityOptions`). On a Galaxy S24
  Ultra (One UI, Android 16) the system ignores it and cross-fades, even when Zenith is the home
  app. Other devices may honour it.
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
