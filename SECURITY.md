# Security Policy

AddictionBuster uses sensitive Android capabilities such as Accessibility Service, overlay windows, notification access, and foreground services. Security reports are taken seriously.

For the current Android permission and exported-component review, see [docs/security-audit.md](docs/security-audit.md).

## Supported Versions

The project is pre-1.0. Security fixes are generally made on the `main` branch and included in the next release.

## Reporting A Vulnerability

Please do not open a public GitHub issue for sensitive security reports.

Until a dedicated security contact is published, contact the maintainer through the GitHub profile for [@corlite](https://github.com/corlite), or use GitHub's private vulnerability reporting if it is enabled for this repository.

Useful report details:

- Affected version or commit.
- Android version and device model.
- Steps to reproduce.
- Expected and actual behavior.
- Whether the issue involves Accessibility Service, notification access, overlays, exported components, local files, or logs.

## Security Review Focus

Changes touching these areas should be reviewed carefully:

- Accessibility Service behavior.
- Overlay display and dismissal logic.
- Notification listener and media-session handling.
- Exported Android components and intent handling.
- Local file and URI permissions.
- Diagnostic logs and accidentally sensitive data.
- Release APK signing and checksums.
