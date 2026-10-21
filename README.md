# DriveScan Studio — Kotlin Multiplatform (full native app, v1.1.0)

## v1.1.0

Smart auto-sync (toggle in Settings; syncs on local change, network return and
app start), dashboard "+" FAB (scan / upload images & PDFs), long-press
multi-select with "Combine into one PDF", page reorder/delete in the viewer,
themed scanner (follows the app light/dark palette), ID-card scan mode
(front + back composed onto one A4 page), auto edge detection + auto-capture
(pure-Kotlin `scan/DocDetector`, detected corners seed the editor), a
signature stamp tool in the editor, password-protected PDF sharing
(`scan/PdfEncrypt` — PDF Standard Security Handler, RC4-128, implemented
in-repo and verified against pikepdf), and on-device OCR (below).

### On-device OCR — vendored ML Kit

ML Kit Text Recognition v2 with **bundled** models (Latin + Devanagari,
fully offline) is integrated without Gradle: the AARs were resolved from
Google Maven (`com.google.mlkit:text-recognition:16.0.1`,
`text-recognition-devanagari:16.0.1` and their POM dependency closure —
mlkit common/vision-common, play-services base/basement/tasks,
firebase-components/encoders, datatransport, odml-image, androidx
annotation/collection/core/appcompat/exifinterface/lifecycle-common,
javax.inject, kotlinx-coroutines) and vendored under `mlkit-libs/`:

- `mlkit-libs/jars/` — each AAR's `classes.jar`, on the kotlinc classpath
  and in the d8 inputs (`build-apk.sh`)
- `mlkit-libs/lib/{arm64-v8a,armeabi-v7a}/` — `libmlkit_google_ocr_pipeline.so`
  (x86/x86_64 emulator ABIs intentionally not packaged, to keep the APK lean)
- `mlkit-libs/assets/mlkit-google-ocr-models/` — the TFLite OCR models

The ML Kit/GMS manifest components (MlKitInitProvider,
MlKitComponentDiscoveryService with all registrars, GoogleApiActivity,
datatransport services, `com.google.android.gms.version` meta-data) are
merged by hand into `androidApp/src/main/AndroidManifest.xml` +
`res/values/google_play_services.xml`, matching what the manifest merger
would produce. Recognized text is stored per document (`ocrText` in the
index) and searched from the dashboard; exported PDFs get an invisible
text layer at the recognized block positions.

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
builds are signed locally by `build-apk.sh`. Likewise, the Desktop OAuth
client's `client_secret` (required by Google in token requests for this
client type) is never committed: `build-apk.sh` reads it from a local,
gitignored `client-secret.txt` and compiles it in as a generated source
file. Without that file the build still succeeds but Drive sign-in will
fail with "client_secret is missing".
