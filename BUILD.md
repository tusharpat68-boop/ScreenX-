# Building ScreenX AI (from your phone)

Nothing here was built or run by the author's tools, so treat the first build as the real test.
If Gradle prints an error, copy the first `error:` line and send it back.

## Which way should I build?

| Way | Needs | Reliability |
|---|---|---|
| **A. GitHub Actions** (recommended) | A free GitHub account and a browser | High |
| B. A computer with Android Studio | A PC/Mac | High |
| C. Termux on the phone | ARM64 Android SDK + aapt2 override | Experimental |

## A. GitHub Actions (no SDK on your phone)

1. Create a new **private** repository on github.com.
2. Put the project files in it. Easiest from Termux:
   ```
   pkg install -y git
   cd ~/ScreenXAI            # the unzipped project folder (contains settings.gradle.kts)
   git init -b main
   git add .
   git commit -m "ScreenX AI"
   git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
   git push -u origin main    # use a personal access token as the password
   ```
3. Open the repo in a browser: **Actions > Build debug APK**. It runs on every push and also
   runs the unit tests.
4. When it turns green, open the run and download the artifact **screenx-debug-apk**
   (a zip). Unzip it and you have `app-debug.apk`.
5. If it fails, open the red step, copy the first `error:` line, and send it to me.
   The `test-and-build-reports` artifact has the full reports.

### Install the APK on your phone
1. Open `app-debug.apk` from your Files app (or `termux-open app-debug.apk`).
2. Android asks to allow installing from that app: allow it once.
3. Tap **Install**. The debug app is called `com.screenx.ai.debug` and can sit next to a release build.

Or with a computer: `adb install -r app-debug.apk` (USB debugging on).

## Release APK

### Option 1: quick test build (signed with the debug key)
GitHub: **Actions > Build release APK > Run workflow**. With no secrets set it signs with the
debug key, so it installs fine. Download **screenx-release-apk**.
Local: `gradle :app:assembleRelease -Pscreenx.debugSignRelease=true`

### Option 2: your own signing key (for sharing or the Play Store)
1. Make a key once and **back it up**. If you lose it you cannot update the app:
   ```
   pkg install -y openjdk-17
   keytool -genkeypair -v -keystore screenx-release.jks -alias screenx \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Local build: create `keystore.properties` next to `settings.gradle.kts` (it is git-ignored):
   ```
   storeFile=/full/path/to/screenx-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=screenx
   keyPassword=YOUR_KEY_PASSWORD
   ```
   then `gradle :app:assembleRelease`.
3. GitHub build: in the repo go to **Settings > Secrets and variables > Actions** and add
   `SCREENX_KEYSTORE_B64` (output of `base64 -w0 screenx-release.jks`), `SCREENX_STORE_PASSWORD`,
   `SCREENX_KEY_ALIAS`, `SCREENX_KEY_PASSWORD`. Then run **Build release APK**.
4. Never commit the `.jks` file or the passwords.

### Where the APKs end up
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`
  (`app-release-unsigned.apk` means no key was found. Use one of the options above.)

## B. Computer with Android Studio
Open the project folder, let it sync, press **Build > Build APK(s)**. Or in a terminal with
JDK 17 and Gradle 8.9: `gradle :app:assembleDebug`. If you want the wrapper:
`gradle wrapper --gradle-version 8.9`, then use `./gradlew`.

## C. Termux (experimental)
Official Android SDK build tools are x86-64 and do not run on an ARM phone.
1. `pkg update && pkg upgrade -y && pkg install -y git openjdk-17 gradle aapt2 wget unzip`
2. Put an ARM64 Android SDK (platform `android-35`, build-tools 34.x) in `~/android-sdk`.
3. `echo "sdk.dir=$HOME/android-sdk" > local.properties`
4. Add to `~/.gradle/gradle.properties`:
   `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`
5. `gradle :app:testDebugUnitTest :app:assembleDebug`

If step 2 or 5 fights you, use way A. It is the reliable one.

## What the build needs
JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35,
min SDK 26 (Android 8.0). Dependencies are listed in `gradle/libs.versions.toml`.
