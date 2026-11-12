# Elara Launcher

A private, offline Android home screen. Part of the Zenith's Space app collection.
Application ID `com.elara.launcher`.

## Features

- First run: a short setup over the home screen (welcome, make Elara your home app, pick a
  wallpaper, and the swipe up and long-press gestures), each step skippable. Shown once.
- Home screen over your wallpaper. There is no built-in clock: add a clock widget instead.
- Widgets: long-press the home screen, then Widgets, for every app's widgets with a preview and
  size; tap one to add it. Android asks once whether Elara may show widget content, and a widget
  with a setup screen opens it. A widget goes in the first free spot of the page shown (else
  another page, else a new one), at least as big as its smallest size. Long-press a widget for its
  name, App info, Resize and Remove; keep holding and drag to move it to free cells (an outline
  shows where it lands). Resize shows a frame with a handle on each side it can grow from; let go
  and it snaps to the cells. Apps never move into a widget's cells. Taps reach the widget; swipes
  that start on it still turn pages and open the drawer, except up and down on a widget with a
  list, which scrolls it. Widgets update only while the home screen is started.
- Swipe up anywhere for the app drawer: every app with a launcher icon, alphabetical, work-profile
  apps included (with their badge). The drawer follows your finger, with the same timing as One UI
  Home (measured from screen recordings): home fades first, then the drawer rises over the dimmed
  wallpaper, and on release it settles without bounce. Pull down from the top of the list to close
  it the same way.
- The drawer opens under an Apps title with search and settings buttons. Search swaps the title
  for a search field with the keyboard up; its close button or Back swaps it back. Matches the start of the name first, then the start of a word,
  then anywhere; case and accents are ignored. The keyboard's Go key opens the best match.
- The drawer's ⋮ button opens a sheet from the bottom with its options: two switches (Search bar
  at bottom, Horizontal scroll), Sort, Hidden apps and All settings.
- Horizontal scroll (the ⋮ sheet): apps
  sit in pages swiped sideways, as many rows as fit, with dots for the pages. Search results are
  still one list.
- Sort (the ⋮ sheet): Alphabetical, or Most used, which lists the apps you open most
  from Elara first. Openings are counted on the device, and only while Most used is chosen.
- Search bar at bottom (the ⋮ sheet; off by default): the search button becomes a bar
  at the bottom of the drawer, in reach of the thumb. Apps that scroll behind it dim more the
  lower they are, as in One UI.
- Sums typed into the search are answered on the phone, above the apps: + − × ÷ ^, brackets
  and percent as a calculator reads them (4500 + 12% is 5,040; "12% of 4500" is 540, with 4,500
  less and plus 12% under it). Decimal arithmetic, so 0.1 + 0.2 is 0.3; long results shrink to
  one line. Copy (or the Go key) puts the result on the clipboard; Open in Calculator shows when a
  calculator app is installed.
- Units in search: "5 km in mi", "100 f to c", or "5 km" alone for the usual other unit, with
  more units under the card and a swap button. Length, weight, temperature, volume, speed and
  area, with Nepal's tola and land units (ropani, aana, bigha, kattha).
- Dates in search, AD and BS (Nepali calendar): "today" (or aaja), "tomorrow" (bholi),
  "yesterday" (hijo), "in 10 days", "2 weeks ago", "25 dec 2026" or "2026-12-25" give the BS date
  (also in Devanagari); "25 kartik 2083" or "2083-07-25" give the AD date. A four-digit year from
  2070 is read as BS. The BS table is Elara Calendar's (from NepDate, MIT).
- Money in search: "100 usd", "$50", "5000 rs in inr". Converts to the currency of the country the
  phone is in (from the mobile network) unless another is named. The launcher stays offline, so it
  asks once for each rate ("1 USD = ___ NPR"), keeps it on the phone and uses it both ways; Change
  rate updates it. INR to NPR uses the fixed 1.6 rate.
- Android settings in search: about 35 Settings pages by name or common word ("wifi", "hotspot",
  "brightness", "dnd", "usb debugging"). The best match goes on a card (when no app name starts
  with the query) and Go opens it; others are listed under the apps with the part of Settings
  they're in. Android has no way for apps to search Settings, so it's a fixed list of pages with a
  public way in; pages the phone lacks are left out.
- Contacts in search (Settings > App drawer, on by default): after two letters the search shows
  a "Search contacts too" row; Allow asks Android for contacts access (read only), the cross turns
  the feature off. Once allowed, matching contacts show: the best one on a card above the apps
  (when no app name starts with what you typed) with Call and Message, the rest under the apps.
  Go and Call open the dialer with the number ready (no call permission); tap a contact to open
  it in your contacts app. Refusing access turns the setting off; a contact's details never
  leave Android's contacts.
- App shortcuts in search: after two letters, the actions apps offer in their long-press menu
  ("New private tab", "New conversation") show under the apps, up to five, with each shortcut's
  icon and its app's name; tap one to run it (Go does, when no app matches). Read once each time
  the search opens. Android lets only the home app read them, so they show while Elara is the home
  app; hidden apps' shortcuts stay out.
- Tap to open, with Android's own open animation and the app's own splash screen.
  Long-press for the app's menu, above the icon: its icon, name and App info, then the app's own
  shortcuts (up to four, such as New tab, with their icons; tap one to run it), then a list of Rename, Add to or Remove from Home,
  Hide, and Uninstall (Android asks to confirm; not shown for apps that came with the device), each
  with its icon on a small purple cookie. Apps go into the dock by dragging. A locked layout hides
  the Home action. The menu grows out of the
  app and shrinks back into it.
- Rename, in every app's menu (drawer, home screen, dock, folders), gives the app a name of your own,
  used everywhere in the launcher, search and drawer order included; saving it empty gives back the
  app's own name. Names stay on the device with the other settings.
- Change icon, in every app's menu, gives one app an icon of your choice from any installed icon
  pack: a sheet with the pack's icons and a search by name, for apps the pack in use has no icon
  for. "Use its usual icon" there takes it back. Picked icons are kept with the settings and backups.
- Hide, in the drawer's app menu, takes an app out of the drawer and its search; a copy on the home
  screen or dock stays. Hidden apps, in the drawer's ⋮ sheet, lists them, each with Unhide.
- Back, home or pulling down from the top of the list closes the drawer.
- Blur (App drawer settings, on by default): the drawer sits over a blur of the wallpaper instead of
  the plain dim, in a chosen style: Gaussian (even, heavy), Background (light, under a darker veil),
  Glass (the default: frosted, colour-rich, with a light sheen), Radial (clear in the middle, blurring
  to the edges) or Gradient (clear at the top, blurring to the bottom). Each style is worked out once
  per wallpaper or style change into one small picture, so all cost the same to draw. Android lets an app read the wallpaper
  only with all-files access, so a wallpaper set through Elara is blurred from its photo with no
  permission; for any other wallpaper the drawer asks once ("Tap to allow (All files access)"), and
  with access the blur follows every wallpaper change on its own. Refused, the drawer keeps the dim
  and does not ask again (allow it later in Android's app settings). Live wallpapers get the dim.
  A change of wallpaper is noticed from its colours, so two wallpapers with the same main colours
  count as one.
- The list updates when apps are installed, removed or changed, from Android's package events,
  never by polling.
- Home screen apps: pages of 1 to 20 columns and rows (4 by 5 to start), swiped sideways, with
  dots for the pages (a setting). Long-press an app in the drawer to add it to the home screen, or keep holding and drag it out: the drawer
  closes and you drop it onto a home screen slot (the others make room) or into the dock. Long-press a
  home screen app for the same menu as in the drawer (Remove from Home, Uninstall), or keep holding and drag it to any slot: the others
  make room as you go. Hold it at a side edge to go to the page there, or a new page past the
  last. A page stays until you remove it. Screen readers get Move left, Move right and Remove.
- Folders on the home screen: drag an app onto another and hold it over the middle for a moment
  (it lights up), then let go; drop more apps onto the folder the same way. The others only make
  room once the finger rests, so passing over an app on the way doesn't make a folder. Tap a
  folder to open it: tap its name to rename it, tap an app to open it, long-press an app for
  Remove from folder (back to a free slot) and Uninstall. A folder left with one app turns back
  into that app. Long-press a folder to drag it, or for Rename, Customize and Remove (it and its apps
  leave the home screen; they stay installed). Customize opens a sheet from the bottom (swipe down or tap outside to close; the folder stays in view, so each change shows at once) that sets, per folder: Small, 2 × 2 or 3 × 3
  cells (a larger one needs the cells to its right and below it free; its apps show three by three from the
  top-left and open with a tap, and with more than nine the last place shows the rest), Apps or Folder icon (small
  app icons, or a folder symbol), the tile colour (the default tint or one of nine colours) and a
  transparent background. A large folder of app icons also has a layout: Grid (rows of three,
  the default), Hexagon (one app in the middle, six round it) or Circle (up to eight evenly round
  a ring). Long-press shows the menu while the finger is still down, for apps and folders alike; the item
  only lifts (grows a little) once you start dragging it.
  A large folder drags on its own to free cells; drop an app anywhere over its middle to add it. Remove from Home in the drawer also takes an app out of its folder.
- Long-press an empty spot on the home screen for a sheet from the bottom: Add app (the drawer
  opens; tap an app to put it on the page you were on), Add page, Remove page (the page shown,
  with its apps; they stay installed) and Widgets, a lock that keeps home
  screen and dock apps from being moved, added or removed, and the first settings categories
  (Appearance, Dock, Home screen, App drawer). The sheet follows your finger: pull it up and the
  same list goes on with Gestures, About and the home app card. A category slides its page in
  over the sheet; Back returns to it. Drag it down, tap outside, or press Back or Home to close it.
- Settings (also the gear in the drawer): whether this is your home app, with a shortcut to change
  it, then a page per category: Appearance (wallpaper: pick a photo, move and zoom it under a faded dock, then set it on the home screen, lock screen or both; icon pack: a sheet of the installed packs (any made for Nova, ADW and similar launchers, read from their appfilter; apps a pack has no icon for keep their own, and the pack's icons are drawn as made, unless adaptive, which take the icon shape); icon shape: a sheet of 16 outlines for every app icon (system, One UI, circle, rice balls, squircle, rounded square, square, teardrop, arch, hexagon, octagon, triangle, heart, cookie, flower, clover); font: a sheet to choose the system font or a custom TTF or OTF file, used for every label and screen in the launcher; pure black in dark mode), Dock (on or
  off, background bar, columns, rows, icon size, row and column spacing), Home screen (app names, page indicator, columns, rows, icon size), App drawer (app names, remember scroll position (off: it opens at the top), columns 1–10, icon size, row spacing, side padding, with a live preview), Gestures and About.
- Dock: apps at the bottom of the home screen, icons only, in a grid of 1 to 20 columns and 1 to
  20 rows (5 by 1 to start). Each app keeps its own slot and slots can stay empty. Icon size is a setting (32 to 96 dp); icons
  shrink below it when the columns are too narrow. Row spacing (0 to 40 dp) sets the gap between rows. A background bar is a setting. Making the
  grid smaller hides apps past it without forgetting them. It starts with your default Phone, Messages, Browser and Camera apps. Long-press an
  app in the drawer to add or remove it; long-press a dock app for the same menu (Remove from Dock, Uninstall), or keep
  holding and drag it to any slot: the other apps make room as you go, and an empty slot just takes it.
  Changing the column count rewraps the slots in reading order. Screen readers get Move left and Move right.
- Gestures on the home screen: swipe down opens the notification panel (or quick settings, or
  nothing), with nothing to set up. Double tap can lock the screen; that one needs Elara
  gestures, an accessibility service you turn on once from Settings > Gestures, since Android has
  no other way for a third-party launcher to lock the screen. It asks for no events and cannot
  read your screen. On Android 13+ a sideloaded app's switch may be greyed out until App info >
  ⋮ > Allow restricted settings.
- Spaces (Settings > Spaces): home setups to switch between, such as Work or Night, each with
  its own home pages (widgets too), dock and hidden apps; grid, icons and other settings are shared.
  New space (an Elara form sheet) takes a name, an icon, a start (a copy of the current space
  without its widgets, or empty pages with the current dock and hidden apps) and a schedule: days
  and a from–to time, which may run past midnight. The first space (Personal, the home as it was)
  is on whenever no other's schedule is. With two or more, the long-press home sheet shows them as
  pills: tap one to switch, held until the next schedule change. Switch by schedule (on by
  default) is checked each time you go home and with one timer to the next change while the home
  screen is up: no alarms, nothing in the background. Show the space's name puts it at the top of
  the home screen for a moment when it changes. Deleting the active space goes back to the
  default; its widgets are freed on the next start. Backups keep the spaces, without widget ids.
- Backup (Settings > Backup): Back up layout saves the home screen, dock, folders, app names, hidden
  apps and settings as a readable text file wherever you choose, through Android's file picker (no
  storage permission). Restore layout asks first, then replaces them with the file's. Widgets (their
  ids belong to this install), the custom font and first-run state are not in it; apps in a backup
  that aren't installed don't show.
- Wallpaper: long-press the home screen > Wallpaper. Pick a photo with Android's photo picker,
  then crop and set it with the system's wallpaper screen. No storage permission.

## Try it and make it your home app

The launcher also has an ordinary app icon, so you can try it without changing anything:
open **Elara Launcher** from your current launcher.

To use it as the home screen, tap the card at the top of its settings, or go to Settings > Apps > Default apps > Home app (Samsung: Settings >
Apps > Choose default apps > Home app) and pick Elara Launcher. To go back, pick your previous
home app (for example One UI Home) in the same place. Your old launcher stays installed.

## Limitations

- **Swipe down uses a hidden Android method.** The notification panel and quick settings open
  through `StatusBarManager`'s expand methods, which are not in the public SDK but are allowed
  for apps (the same route Niagara, Nova and Lawnchair use), with the install-time
  `EXPAND_STATUS_BAR` permission. If a future Android blocks them, swipe down falls back to the
  Elara gestures accessibility service and asks for it.

- **App open and close animation.** One UI Home animates the app's real window out of its icon and
  back into it, through remote app transitions, a permission Android grants only to the system's
  own launcher. Android also drops launch animations requested by other apps (`ActivityOptions`
  scale-up or clip-reveal; verified on a Galaxy S24 Ultra, even as the home app), so opening and
  closing use the system's animation. On One UI, Back and the Home button run one system
  transition through One UI's recents task, and the Home gesture is animated by One UI Home's
  recents component even when another home app is the default.
- Returning from an app can show the drawer's last frame for a moment before the home screen,
  since Android animates the launcher's last drawn frame.
- **Widgets, first version.** Previews are the image an app ships (or its icon); Android 12's
  live preview layouts aren't drawn yet. A widget can't be dragged to another page, only within
  its own. Making the home grid smaller can leave a widget over apps until one is moved. A widget
  whose app is removed shows Android's "problem loading widget" box until you remove it.
- **Folders, first version.** Home screen only; the dock has none by design. Hexagon shows seven
  apps and Circle eight; open the folder for the rest. Apps can't be dragged out of an open
  folder or reordered in it; use Remove from folder. A folder can't be dropped into another. Tiles
  are rounded squares only. A large folder too close to the grid's right or bottom edge to fit shows small.

## Build

Requirements: JDK 17 and the Android SDK with platform 37.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android Lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the application ID `com.elara.launcher.dev` and show as "Elara Launcher Dev",
so they install beside the release without a signature clash.

### Signed release build

Put a `keystore.properties` file (storeFile, storePassword, keyAlias, keyPassword) outside the
repository and point the Gradle property `zenithLauncherSigning` at it, for example in
`~/.gradle/gradle.properties`. Without it, release builds are left unsigned.

### Publishing a release

`.github/workflows/release.yml` runs when a tag `launcher-v<versionName>` is pushed (for example
`launcher-v1.5.0`). It checks the app ID and that the tag matches `versionName`, runs the unit
tests and lint, builds the signed release, and publishes it as a GitHub release with the APK and
its SHA-256 checksum. It needs four repository secrets:

| Secret | Value |
|---|---|
| `LAUNCHER_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 release.jks`) |
| `LAUNCHER_STORE_PASSWORD` | The keystore's password |
| `LAUNCHER_KEY_ALIAS` | The key's alias |
| `LAUNCHER_KEY_PASSWORD` | The key's password |

Use the same key as your local release builds, or phones with the app installed can't update.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [PRIVACY.md](PRIVACY.md)
- [SECURITY.md](SECURITY.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [ROADMAP.md](ROADMAP.md)

## License

Apache License 2.0; see [LICENSE](LICENSE). Third-party data and its licences are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
