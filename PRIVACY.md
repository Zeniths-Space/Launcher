# Privacy

- **No internet permission and no permissions at all.** The launcher cannot open network
  connections.
- **No telemetry, analytics, ads, or accounts.**
- **What it reads:** the list of apps that have a launcher icon (name, icon), through Android's
  LauncherApps API, to show and start them. It does not read app data, notifications or usage.
- **What it stores:** nothing yet. Search text is kept only while the drawer is open.
- **Backups:** disabled (`allowBackup="false"`).

Features that store data on the device (home screen layout, hidden apps) will be documented here
before release.
