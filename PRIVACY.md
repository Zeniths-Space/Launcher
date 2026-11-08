# Privacy

- **No internet permission.** The launcher cannot open network connections. Its two permissions are
  granted at install, with no prompt. `EXPAND_STATUS_BAR` only lets swipe down open the notification
  panel; it gives no access to your notifications. `REQUEST_DELETE_PACKAGES` only lets an app's
  menu open Android's uninstall prompt; nothing is removed unless you confirm there.
- **Elara gestures (accessibility service), off unless you turn it on.** It exists only so double
  tap can lock the screen (and as a fallback for swipe down), which Android allows a third-party
  launcher only through an accessibility service. It asks Android
  for no accessibility events and cannot read window content, so it does not see what is on your
  screen or what you type. Turn it off any time in Android's accessibility settings.
- **No telemetry, analytics, ads, or accounts.**
- **What it reads:** the list of apps that have a launcher icon (name, icon), through Android's
  LauncherApps API, to show and start them. It does not read app data, notifications or usage.
- **What it stores:** on/off settings (app names in the drawer and on the
  home screen, page indicator, pure black, layout lock, remember drawer scroll position, first-run setup finished), the icon shape, the two gesture choices, which apps are on the home screen pages and
  in the dock, folders and their names, each widget's place and size on the home screen, the grid sizes of both, the app drawer layout, and a copy of the font file you pick (deleted when you go back to the system font), in the app's private storage. Search text is kept only while the drawer is open.
- **Widgets:** a widget shows content from its own app. Android asks before Elara may host
  widgets; once allowed, Elara draws what widgets send, like any home app, and keeps none of it.
- **Wallpaper:** the photo you pick in Android's photo picker is shared only with the system's
  wallpaper screen (read access to that one photo). The launcher gets no access to your other
  photos and keeps no copy of it.
- **Glass blur:** for the app drawer's blur, Elara keeps a small, heavily blurred copy of the home
  wallpaper (144 px wide) in its private storage. It is made from the photo you set through Elara or,
  if you allow it, from the wallpaper Android holds. Android grants that only through **All files
  access** (`MANAGE_EXTERNAL_STORAGE`; storage read before Android 11), which would let an app read
  every file on shared storage. Elara asks for it once, from the drawer, and uses it only to read the
  wallpaper; it opens no other file, and has no internet permission to send anything anywhere.
  Refusing it changes nothing else.
- **Backups:** disabled (`allowBackup="false"`).

Features that store data on the device (home screen layout, hidden apps) will be documented here
before release.
