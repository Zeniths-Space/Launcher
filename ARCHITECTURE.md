# Architecture

One Gradle module (`app`), Kotlin and Jetpack Compose, Material 3 with the Zenith's Space look.
No other ecosystem app is needed.

| File | Role |
|---|---|
| `MainActivity.kt` | The home activity: `singleTask`, transparent over the wallpaper. Home pressed while it shows closes the drawer. |
| `LauncherScreen.kt` | Compose UI: home screen, the app drawer with search, app tiles, the home sheet. |
| `Widgets.kt` | `WidgetHost` (Android's `AppWidgetHost`, binding, setup screens), widget views and their touch hand-off, widget menu and resize frame. |
| `WidgetPicker.kt` | The widget picker and how many cells a new widget takes. |
| `HomeWidgets.kt` | Widgets as cell rectangles per page: stored form, fitting, placing, page removal (unit tested). |
| `AppMenu.kt` | The long-press menu popup shared by drawer, home, dock apps and widgets. |
| `AppRepository.kt` | Apps from `LauncherApps` for every profile, package-change callbacks, icons, launching. |
| `SettingsActivity.kt` | Settings (not exported): home app status and category list, one page per category. |
| `SettingsStore.kt` | `LauncherSettings` in a Preferences DataStore, as a Flow both activities collect. |
| `Dock.kt` | The dock grid: tap to open, long-press menu, drag to rearrange. |
| `HomeGrid.kt` | Home screen pages of apps in a `HorizontalPager`: tap, long-press menu, drag across slots and pages. |
| `HomePages.kt` | Home pages as slot lists: stored form, moving with room made, adding, removing (unit tested). |
| `GestureService.kt` | `GestureAction`, `Gestures` (status bar expand, service fallback) and the accessibility service for locking. |
| `Wallpaper.kt` | Photo picker, then the system crop-and-set screen. |
| `Icons.kt`, `Shapes.kt` | Vector icons and M3 Expressive shapes (copied from Zenboard). |
| `Apps.kt` | Pure model: `AppEntry`, sorting and search ranking. Unit-tested. |
| `Theme.kt` | Colours, type and motion shared in style with Zenboard (copied, not a shared library). |

## Decisions

- **LauncherApps, not PackageManager queries.** `getActivityList` gives each profile's launchable
  activities with labels and badged icons, and `startMainActivity` starts them in the right
  profile. The manifest's `<queries>` entry (MAIN + LAUNCHER) is the only visibility needed on
  Android 11+; there is no `QUERY_ALL_PACKAGES`.
- **Event-driven, no polling.** A `LauncherApps.Callback` reloads the list on package add, remove,
  change and availability. Widgets update through Android's widget host, listening only
  between the home activity's onStart and onStop.
- **Icons on demand.** Each tile asks for its icon at its pixel size; the repository draws it off
  the main thread once and keeps it in memory until the app changes or leaves.
  Legacy (non-adaptive) icons are wrapped, as Launcher3 does, at 70% on white inside an
  `AdaptiveIconDrawable`, so every icon gets the system icon shape; the work badge goes on after
  (`getUserBadgedIcon`).
- **Drawer motion follows the finger.** `DrawerMotion` holds one progress value (0 home, 1 open).
  Drags set it directly; a release starts a critically damped spring (stiffness 400) with the
  fling's speed. Every visual (home fade, drawer rise and fade, wallpaper dim) is read from it in
  `graphicsLayer`/`drawBehind`, so a drag redraws without recomposing. Constants are measured from
  One UI Home recordings. The drawer stays composed, parked off screen while closed: building it
  when a swipe began froze the main thread ~270 ms (debug build) and lost the fling. The launcher
  resets to the home screen in `onStop`.
- **Launch animation is the system's.** Apps start through `LauncherApps.startMainActivity` with
  the icon's bounds and, on Android 13+, `SPLASH_SCREEN_STYLE_ICON` (otherwise Android gives a
  launch it does not count as from the home app a plain colour splash), and Android animates
  the open and shows each app's own splash. A launcher-drawn zoom from the icon was tried and
  removed (2026-11-05): Android ignores launch animations requested through `ActivityOptions`
  (scale-up, clip-reveal, a zero-length custom animation; re-tested with Zenith as the default
  home), an icon morph of the real window needs remote transitions (a system-only permission),
  and a drawn imitation never handed over to the app's splash cleanly.
- **Home activity flags.** `singleTask`, `stateNotNeeded`, `clearTaskOnLaunch` and
  `excludeFromRecents`: one fresh home that never appears in Recents. It also keeps a LAUNCHER
  entry so it can be tried from another launcher before being made the default.
- **No navigation library, no database yet.** Two states (home, drawer) in one activity; settings
  are a second activity, so home always returns to a clean home screen (`singleTask` clears it).
  Its category pages are a saved page value in that activity, with a shared-axis slide and Back
  returning to the list.
- **Gestures.** The notification panel and quick settings open through `StatusBarManager`'s
  hidden `expandNotificationsPanel` / `expandSettingsPanel` by reflection, with the install-time
  `EXPAND_STATUS_BAR` permission, as other third-party launchers do: no setup for the user
  (checked on a Galaxy S24 Ultra, One UI, 2026-11-05). This breaks the no-hidden-API rule, by
  the owner's choice. Locking the screen has no such route, so it uses
  `AccessibilityService.performGlobalAction` (Android 9+), which is also the fallback for the panel
  if those methods go. The service requests no event types and `canRetrieveWindowContent="false"`,
  holds itself only as a weak reference while bound, and does nothing between gestures; Settings
  asks for it only when a chosen gesture needs it. A swipe down counts when it starts on the home
  screen with the drawer shut and goes 48 dp or 600 dp/s; double tap is only listened for when
  it is set.
- **Dock.** App keys by slot in reading order, "" for an empty slot, in the settings DataStore
  (`DockSlots` does the edits); an app that is uninstalled leaves its slot empty. Columns, rows,
  icon size and row spacing are stored with it; only the first columns times rows slots show, and the rest
  stay stored. The grid is
  always columns by rows high; each app is placed at its slot's centre, so it needs no lazy layout. Seeded once (a flag in the same edit) from the apps that resolve `DIAL`, `SENDTO
  smsto:`, `VIEW https` and `STILL_IMAGE_CAMERA`, which the manifest's `<queries>` make visible.
  It sits outside the home screen's long-press area, so holding a dock app does not open the home
  menu, but inside the swipe-up area. Drag after long-press targets the nearest slot centre; `DockSlots.place` puts the app
  there, pushing the apps from that slot one back toward the slot it left, up to the first gap.
  That layout shows live and is the one saved when the finger lifts; callbacks go
  through `rememberUpdatedState`, as the gesture outlives the composition it started in.
- **Home screen grid.** Pages of app keys by slot, "" for empty, stored as one string (slots by
  line break, pages by "|"); empty pages are kept until removed from the home sheet. The grid reads its gestures on the
  container, not the icons, so a drag survives its page being swiped away: long-press finds the
  slot under the finger, an app starts a drag (its icon drawn over the pager) and an empty slot
  opens the home menu. The pager's own swiping is off while dragging, and a drag adds a page past
  the last to drop on; holding 500 ms at a side edge scrolls to the next page. The page dots row
  always keeps its height, and cell sizes are read without restarting the gesture, so the grid
  never resizes or drops a drag midway. Icons take taps themselves, so a double-tap gesture on the
  wallpaper does not delay opening apps.
- **Home sheet.** The long-press menu is drawn in the home screen, not M3's `ModalBottomSheet`,
  whose half-open stop is fixed at half the screen. One list: it stops where the first settings
  categories end and a drag or fling carries it on to the full list (a critically damped spring,
  as the drawer); a nested-scroll connection moves the sheet before the list scrolls. A category's
  page is the settings screen composable over the sheet.
- **Settings in DataStore.** One file, read as a Flow: a change in settings shows on the home
  screen without a reload. A damaged file reads as defaults.
- **Wallpaper through the photo picker.** `ACTION_SET_WALLPAPER` on Samsung lists only live
  wallpapers, and the One UI wallpaper app has no public intent. The photo picker plus
  `WallpaperManager.getCropAndSetWallpaperIntent` works on every device (Android uses the
  current home app's cropper if it has one, else the system's); `ACTION_ATTACH_DATA` is the
  fallback.
- **Widgets beside apps, not in the slot lists.** Apps stay as reading-order slots per page; widgets
  are stored separately as cell rectangles. App moves skip the cells under widgets (the slots are
  compressed past them), so the existing make-room logic still applies, and a widget only lands on
  free cells. Compose sees every touch on a widget first and hands it to the widget's view without
  consuming it: taps reach the widget, a hold becomes the home screen's menu or drag, and swipes
  stay the home screen's unless the widget has a list and the swipe is vertical.
