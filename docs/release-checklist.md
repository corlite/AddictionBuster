# Release Checklist

This checklist keeps release work reproducible without committing APK files or signing secrets.

## Version

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update the current version and release link in `README.md` and `README.zh-CN.md`.
3. Make sure the release tag matches the app version, for example `v0.3.1`.

## Local Signing

Release signing is configured through local-only values. Do not commit keystores, passwords, or generated APKs.

Use either environment variables or `local.properties`:

```properties
ADDICTIONBUSTER_RELEASE_STORE_FILE=release.keystore
ADDICTIONBUSTER_RELEASE_STORE_PASSWORD=change-me
ADDICTIONBUSTER_RELEASE_KEY_ALIAS=addictionbuster
ADDICTIONBUSTER_RELEASE_KEY_PASSWORD=change-me
```

If these values are absent, Gradle can still build debug APKs and unsigned release APKs. Signed release builds require all four values.

## Verify

Run the local checks before publishing:

```powershell
.\gradlew.bat lint
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

For a signed release build:

```powershell
.\gradlew.bat assembleRelease
```

## Package

Copy the APK into a local ignored release workspace:

```powershell
New-Item -ItemType Directory -Force release-assets
Copy-Item app\build\outputs\apk\release\app-release.apk release-assets\AddictionBuster-vX.Y.Z.apk
Get-FileHash -Algorithm SHA256 release-assets\AddictionBuster-vX.Y.Z.apk
```

Write the hash to `release-assets/SHA256SUMS.txt`.

## Publish

Create the GitHub Release from local ignored files:

```powershell
gh release create vX.Y.Z release-assets\AddictionBuster-vX.Y.Z.apk release-assets\SHA256SUMS.txt --repo corlite/AddictionBuster --target main --title AddictionBuster-vX.Y.Z --notes-file release-assets\RELEASE_NOTES-vX.Y.Z.md --latest
```

After publishing, verify that:

- The release page contains the APK and `SHA256SUMS.txt`.
- The README download link points to the latest release.
- Android CI passes on `main`.
- APK files are not committed to the repository root.
