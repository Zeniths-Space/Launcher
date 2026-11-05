# Privacy

- **No internet permission and no permissions at all.** The launcher cannot open network
  connections.
- **Zenith gestures (accessibility service), off unless you turn it on.** It exists only so home
  screen gestures can open the notification panel or quick settings and lock the screen, which
  Android allows a third-party launcher only through an accessibility service. It asks Android
  for no accessibility events and cannot read window content, so it does not see what is on your
  screen or what you type. Turn it off any time in Android's accessibility settings.
- **No telemetry, analytics, ads, or accounts.**
- **What it reads:** the list of apps that have a launcher icon (name, icon), through Android's
  LauncherApps API, to show and start them. It does not read app data, notifications or usage.
- **What it stores:** four on/off settings (clock, swipe hint, app names, pure black) and the
  two gesture choices in the app's private storage. Search text is kept only while the drawer is open.
- **Wallpaper:** the photo you pick in Android's photo picker is shared only with the system's
  wallpaper screen (read access to that one photo). The launcher gets no access to your other
  photos and keeps no copy.
- **Backups:** disabled (`allowBackup="false"`).

Features that store data on the device (home screen layout, hidden apps) will be documented here
before release.
