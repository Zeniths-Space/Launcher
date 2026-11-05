# Architecture

One Gradle module (`app`), Kotlin and Jetpack Compose, Material 3 with the Zenith's Space look.
No other ecosystem app is needed.

| File | Role |
|---|---|
| `MainActivity.kt` | The home activity: `singleTask`, transparent over the wallpaper. Home pressed while it shows closes the drawer. |
| `LauncherScreen.kt` | Compose UI: home screen with the clock, the app drawer with search, app tiles. |
| `AppRepository.kt` | Apps from `LauncherApps` for every profile, package-change callbacks, icons, launching. |
| `SettingsActivity.kt` | Settings page (not exported): home app status, home screen and drawer switches, about. |
| `SettingsStore.kt` | `LauncherSettings` in a Preferences DataStore, as a Flow both activities collect. |
| `Wallpaper.kt` | Photo picker, then the system crop-and-set screen. |
| `LaunchZoom.kt` | The zoom from the icon drawn when an app opens. |
| `Icons.kt`, `Shapes.kt` | Vector icons and M3 Expressive shapes (copied from Zenboard). |
| `Apps.kt` | Pure model: `AppEntry`, sorting and search ranking. Unit-tested. |
| `Theme.kt` | Colours, type and motion shared in style with Zenboard (copied, not a shared library). |

## Decisions

- **LauncherApps, not PackageManager queries.** `getActivityList` gives each profile's launchable
  activities with labels and badged icons, and `startMainActivity` starts them in the right
  profile. The manifest's `<queries>` entry (MAIN + LAUNCHER) is the only visibility needed on
  Android 11+; there is no `QUERY_ALL_PACKAGES`.
- **Event-driven, no polling.** A `LauncherApps.Callback` reloads the list on package add, remove,
  change and availability. The clock listens to `ACTION_TIME_TICK`, time and zone changes only
  while composed.
- **Icons on demand.** Each tile asks for its icon at its pixel size; the repository draws it off
  the main thread once and keeps it in memory until the app changes or leaves.
- **Drawer motion follows the finger.** `DrawerMotion` holds one progress value (0 home, 1 open).
  Drags set it directly; a release starts a critically damped spring (stiffness 400) with the
  fling's speed. Every visual (home fade, drawer rise and fade, wallpaper dim) is read from it in
  `graphicsLayer`/`drawBehind`, so a drag redraws without recomposing. Constants are measured from
  One UI Home recordings. The drawer stays composed, parked off screen while closed: building it
  when a swipe began froze the main thread ~270 ms (debug build) and lost the fling. The launcher
  resets to the home screen in `onStop`.
- **Launch animation drawn by the launcher (`LaunchZoom.kt`).** Android ignores launch animations
  requested through `ActivityOptions` (the transition reaches Shell without them; it plays its
  default zoom from the centre), and icon morphs need remote transitions, a system-only
  permission. Instead the launcher grows a rounded rectangle from the icon to the full screen in
  340 ms on Android's standard curve (One UI's own leapt out of the icon, then crawled), in the app's splash or window background colour (read
  from its theme through `createPackageContext`; a launch-screen drawable gives its corner pixel),
  while the launcher icon fades into it. The launcher draws no splash of its own: an earlier
  version matched each app's splash icon from its theme, and stacked with apps that draw their
  own (Nekogram, Kontract, Flutter apps). The app starts once the zoom fills the screen
  (starting it part way, as One UI snaps to full when the app is ready, read as a stutter), with
  `FLAG_ACTIVITY_NO_ANIMATION` and, on Android 13+, `SPLASH_SCREEN_STYLE_ICON` (otherwise a
  launch from a non-home app gets a plain colour splash, a blank screen), so Android shows each
  app's own splash as a home-screen launch would. `makeScaleUpAnimation` through LauncherApps was
  re-tested with Zenith as the default home (2026-11-05): Android faded in a plain window instead; a zero-length `makeCustomAnimation` was
  tried instead of the flag and Android played its own centre zoom. Skipped when animations are off, for other profiles
  (LauncherApps cannot drop the animation) and without a tile. Cleared in `onStop`.
- **Home activity flags.** `singleTask`, `stateNotNeeded`, `clearTaskOnLaunch` and
  `excludeFromRecents`: one fresh home that never appears in Recents. It also keeps a LAUNCHER
  entry so it can be tried from another launcher before being made the default.
- **No navigation library, no database yet.** Two states (home, drawer) in one activity; settings
  are a second activity, so home always returns to a clean home screen (`singleTask` clears it).
- **Settings in DataStore.** One file, read as a Flow: a change in settings shows on the home
  screen without a reload. A damaged file reads as defaults.
- **Wallpaper through the photo picker.** `ACTION_SET_WALLPAPER` on Samsung lists only live
  wallpapers, and the One UI wallpaper app has no public intent. The photo picker plus
  `WallpaperManager.getCropAndSetWallpaperIntent` works on every device (Android uses the
  current home app's cropper if it has one, else the system's); `ACTION_ATTACH_DATA` is the
  fallback.
