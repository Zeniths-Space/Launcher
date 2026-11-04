# Security

## Reporting a vulnerability

Please report security issues privately through GitHub Security Advisories
("Report a vulnerability" on the repository's Security tab). Do not open a public issue.

## Design

- The app requests no permissions.
- The home activity is exported, as Android requires for a home app, and accepts only MAIN
  (HOME, LAUNCHER) intents; it reads no data from them.
- Apps are started only through LauncherApps, for activities the system lists as launchable.
- Release signing keys and passwords are never committed. They live in protected CI secrets.
