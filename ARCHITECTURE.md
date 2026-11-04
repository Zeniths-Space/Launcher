# Architecture

One Gradle module (`app`), Kotlin and Jetpack Compose, Material 3 with the Zenith's Space look.
No other ecosystem app is needed.

| File | Role |
|---|---|
| `MainActivity.kt` | The home activity: `singleTask`, transparent over the wallpaper. Home pressed while it shows closes the drawer. |
| `LauncherScreen.kt` | Compose UI: home screen with the clock, the app drawer with search, app tiles. |
| `AppRepository.kt` | Apps from `LauncherApps` for every profile, package-change callbacks, icons, launching. |
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
- **Launch animation from the tile.** The tile's window bounds go to `startMainActivity`, so the
  app opens from its icon.
- **Home activity flags.** `singleTask`, `stateNotNeeded`, `clearTaskOnLaunch` and
  `excludeFromRecents`: one fresh home that never appears in Recents. It also keeps a LAUNCHER
  entry so it can be tried from another launcher before being made the default.
- **No navigation library, no database yet.** Two states (home, drawer) in one activity.
