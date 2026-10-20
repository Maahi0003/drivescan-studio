# DriveScan Studio — Kotlin Multiplatform (full native app, v1.0.0)

Native Android app + shared codebase for desktop. Local-first: every scan
lives on the phone first; Google Drive is a sync layer on top. No paid
backend, no embedded credentials.

## Modules

| Module | What |
|---|---|
| `shared/` | Shared logic + shared Compose UI (Gradle path): models, 10 vault templates, PBKDF2 PIN hasher (expect/actual), `ScanStore` + in-memory impl, `DriveApi` pure request builders/parsers, `DriveScanApp` composable |
| `androidApp/` | The full Android app (applicationId `com.drivescan.kmp`): dashboard, Camera2 scanner + perspective editor, vault (biometric/PIN), bin (30-day), settings, Drive OAuth+PKCE and REST sync, PDF/share via a custom ContentProvider |
| `desktopApp/` | Desktop entry point sharing the same module (Swing window; headless = console self-test) |

## Android app structure

- `MainActivity` — dashboard: folders, thumbnails, search, doc actions, Drive chip, bin auto-purge
- `ScannerActivity` — Camera2 preview, document/ID-card guides, torch, gallery import, multi-page
- `EditorActivity` + `scan/ImageOps` — draggable corners, `setPolyToPoly` warp, filters, rotate, PDF/JPG save sheet
- `VaultActivity` — BiometricPrompt or PBKDF2 PIN; the 10 template slots
- `BinActivity` — 30-day retention, restore / delete forever (Drive trash queued for next sync)
- `SettingsActivity` — dark mode, Drive client ID + connect/disconnect/sync, storage, about
- `auth/` — OAuth 2.0 + PKCE via browser redirect to `AuthActivity` (reversed client-ID scheme of the Desktop OAuth client: `com.googleusercontent.apps.<id>:/oauth2redirect` — Google rejects package-name schemes for this flow)
- `drive/DriveSync` — Drive REST over HttpURLConnection: workspace folders, multipart upload, move, pull-merge, trash queue
- `data/PersistentStore` — files under `filesDir/scans/<docId>/` + hand-rolled JSON index (no libraries)
- `ShareProvider` — dependency-free FileProvider replacement (serves only `filesDir/scans`)

## Build

Normal machine / Android Studio:

```
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
```

This sandbox (Gradle daemon cannot run here) uses the manual pipeline,
which is the same steps AGP performs:

```
./build-apk.sh            # -> build-manual/drivescan-studio-final.apk
./build-apk.sh 3 1.1.0    # versionCode / versionName
```

Builds are signed with the shared debug keystore
(`~/workspace/drivescan-apk/debug.keystore`) so updates install cleanly.

## Drive sign-in setup (one-time, by the app owner)

1. Google Cloud Console → APIs & Services → Credentials → Create credentials
   → **OAuth client ID** → type **Android**.
2. Package name: `com.drivescan.kmp`; SHA-1 certificate fingerprint: the debug
   keystore's SHA-1 (ask for the current value — it ships with the build notes).
3. Create the client, copy the **Client ID**, paste it in the app under
   Settings → Google Drive → Client ID, then tap **Connect Google Drive**.

The vault lock is an **app lock** (PBKDF2-hashed PIN + biometrics), not file
encryption — same honest scope as the web app.

## App updates (GitHub-based)

The app checks `version.json` at the repo root (raw URL) for updates:
`https://raw.githubusercontent.com/Maahi0003/drivescan-studio/main/version.json`
Set that URL in the app under Settings → App Update. Each release bumps the
numbers in `version.json` and attaches the APK to a GitHub Release whose tag
matches `versionName` (the `apkUrl` field points at the release asset).

Note: the Android signing keystore is intentionally NOT in this repo —
builds are signed locally by `build-apk.sh`.
