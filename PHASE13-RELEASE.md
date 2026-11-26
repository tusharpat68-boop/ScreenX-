# ScreenX AI — Phase 13: Production optimization & release preparation

## What this phase adds

- Version metadata advanced to `0.13.0-phase13` / versionCode `3`.
- Optional Android release signing configured through environment variables.
- A dedicated GitHub Actions release-readiness workflow.
- Unit-test and release-lint gates before release APK assembly.
- APK existence check and SHA-256 checksum artifact.
- A release checklist covering signing, privacy, device validation, and distribution.

## Signing security

No keystore, alias, or password is included in this source archive. Keep the keystore private and backed up securely. Do not commit it to Git, put passwords in source files, or share it in chat.

Gradle reads these environment variables:

- `SCREENX_KEYSTORE_PATH`
- `SCREENX_KEYSTORE_PASSWORD`
- `SCREENX_KEY_ALIAS`
- `SCREENX_KEY_PASSWORD`

If all four are present, `assembleRelease` signs the APK. If any are missing, Gradle creates an **unsigned** release APK. The GitHub Actions workflow accepts the matching repository secrets: `SCREENX_KEYSTORE_BASE64`, `SCREENX_KEYSTORE_PASSWORD`, `SCREENX_KEY_ALIAS`, and `SCREENX_KEY_PASSWORD`. The base64 secret is decoded only into the ephemeral runner's temporary directory.

Create a keystore locally (keep the resulting file private):

```bash
keytool -genkeypair -v \
  -keystore screenx-release.jks \
  -alias screenx \
  -keyalg RSA -keysize 4096 -validity 10000
```

For GitHub Actions, encode it locally and store the output as the `SCREENX_KEYSTORE_BASE64` repository secret:

```bash
base64 -w 0 screenx-release.jks
```

Add the password and alias values as the other three repository secrets. Never paste the actual secrets into project files.

## Local build

```bash
gradle :app:testDebugUnitTest
gradle :app:lintRelease
gradle :app:assembleRelease
```

For a signed local build, export all four `SCREENX_*` variables before running `assembleRelease`. The output is `app/build/outputs/apk/release/app-release.apk`.

## Release readiness checklist

- [ ] GitHub Actions unit tests pass.
- [ ] Release lint passes; review and resolve all actionable findings.
- [ ] Release APK is signed with the owner's private key (unless intentionally building an unsigned test artifact).
- [ ] Verify APK signature with Android `apksigner verify --verbose --print-certs app-release.apk`.
- [ ] Verify SHA-256 checksum independently before distribution.
- [ ] Install and launch on Android API 26 and a current Android version.
- [ ] Test MediaProjection consent, start/pause/resume/stop, notifications, and system-stop behavior.
- [ ] Test microphone/internal audio combinations on supported devices and check A/V sync.
- [ ] Test overlay/camera permission denial and graceful fallback.
- [ ] Test rotations, low-storage stop, long recordings, playback, share, rename, and delete.
- [ ] Confirm privacy disclosures and that recordings remain local unless the user explicitly shares them.
- [ ] Confirm custom folder, watermark, and language limitations are either implemented or disclosed in the app.

## Important: what this phase does not certify

A successful CI build is not proof of production readiness on all Android devices. This environment did not run Android Gradle tasks or hardware recording tests. The project still carries the Phase 12 limitations: selected custom folder is persisted but export uses MediaStore `Movies/ScreenX`; watermark preference is not wired into export; and language localization is limited to the Settings screen. Do not publish as production-ready until the checklist is completed on real devices.
