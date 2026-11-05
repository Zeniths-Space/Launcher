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
- Tap to open: the app zooms out of its icon, as in One UI Home. A rounded rectangle in the app's
  splash colour grows from the icon while the icon grows into the app's splash icon, then the
  app's splash screen takes over at full size.
  Long-press for App info.
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

- **App open and close animation.** One UI Home animates the app's real window out of its icon and
  back into it, through remote app transitions, a permission Android grants only to the system's
  own launcher. Android also drops launch animations requested by other apps (`ActivityOptions`
  scale-up or clip-reveal; verified on a Galaxy S24 Ultra, even as the home app). So Zenith
  Launcher draws the zoom itself and starts the app with the system animation off. The growing
  rectangle shows the first frame of an animated splash icon, not the animation, and never the
  app's content. On Android 12 an app that is not running can show a plain colour splash with no
  icon after the zoom. Apps that draw their own splash (Nekogram, Kontract, Flutter apps)
  are recognised from their theme and get a plain zoom in their colour. An app that draws its own
  splash in its first screen without any sign in its theme still shows the icon first, as
  Android's own splash shows it there too. Closing an app back to home uses the system animation:
  on One UI, Back and Home run one system transition through One UI's recents task, and it
  ignored a `windowAnimationStyle` on the home theme (verified on a Galaxy S24 Ultra).
- Apps from a work profile, and apps opened from the search key, open with the system animation.
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
