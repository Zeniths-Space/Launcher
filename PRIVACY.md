# Privacy

- **No internet permission.** The launcher cannot open network connections. Its one permission,
  `EXPAND_STATUS_BAR` (granted at install, no prompt), only lets swipe down open the notification
  panel; it gives no access to your notifications.
- **Zenith gestures (accessibility service), off unless you turn it on.** It exists only so double
  tap can lock the screen (and as a fallback for swipe down), which Android allows a third-party
  launcher only through an accessibility service. It asks Android
  for no accessibility events and cannot read window content, so it does not see what is on your
  screen or what you type. Turn it off any time in Android's accessibility settings.
- **No telemetry, analytics, ads, or accounts.**
- **What it reads:** the list of apps that have a launcher icon (name, icon), through Android's
  LauncherApps API, to show and start them. It does not read app data, notifications or usage.
- **What it stores:** four on/off settings (clock, swipe hint, app names, pure black) and the
  two gesture choices, and which apps are in the dock with its columns, rows and icon size, in the app's private storage. Search text is kept only while the drawer is open.
- **Wallpaper:** the photo you pick in Android's photo picker is shared only with the system's
  wallpaper screen (read access to that one photo). The launcher gets no access to your other
  photos and keeps no copy.
- **Backups:** disabled (`allowBackup="false"`).

Features that store data on the device (home screen layout, hidden apps) will be documented here
before release.
