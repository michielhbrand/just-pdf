# JustPdf

A private-use, sideloaded Android PDF viewer. Its sole purpose is to be the **default PDF viewer** on the device and to **share opened PDFs** with other apps (Mail, WhatsApp, Google Drive, etc.).

No Play Store. No tracking. No ads. No accounts.

---

## Features

- Open PDFs from any app via **"Open with"** or **"Share"**
- Browse and open PDFs from the device file system
- Continuous vertical scroll through pages
- Pinch-to-zoom and double-tap zoom *(via RecyclerView + ImageView)*
- Portrait and landscape support with automatic rotation handling
- Minimal auto-hiding toolbar: **Share** button + **page counter**
- Share the currently open PDF to Mail, WhatsApp, Drive, etc.
- Fully stateless — no history, no bookmarks, no database

---

## Tech Stack

| Component | Choice | Reason |
|---|---|---|
| Language | Kotlin | Official Android language, null safety, coroutines |
| PDF rendering | Android built-in `PdfRenderer` (API 21+) | Zero external dependencies, maximum robustness |
| Scroll view | `RecyclerView` + `LinearLayoutManager` | Lazy page rendering, smooth continuous scroll |
| UI toolkit | View system (XML) | Mature, stable, no Compose overhead for a single screen |
| File access | Storage Access Framework (`ACTION_OPEN_DOCUMENT`) | No storage permission needed |
| Share-out | `FileProvider` + `ACTION_SEND` | Required for `content://` URIs since API 24 |
| Build | Gradle 8.6, Kotlin DSL, AGP 8.4 | Standard Android toolchain |
| Min SDK | 21 (Android 5.0 Lollipop) | Broad device support |
| Target SDK | 34 (Android 14) | Latest stable |

---

## Project Structure

```
JustPdf/
├── app/
│   └── src/main/
│       ├── java/com/justpdf/
│       │   ├── MainActivity.kt       # All app logic
│       │   └── FileUtils.kt          # URI resolution + FileProvider share URI
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml # RecyclerView + floating toolbar
│       │   │   └── item_pdf_page.xml # Single page ImageView
│       │   ├── drawable/             # Vector icons + toolbar background
│       │   ├── values/               # strings, colors, themes
│       │   └── xml/file_paths.xml    # FileProvider paths
│       └── AndroidManifest.xml
├── gradle/
│   ├── libs.versions.toml            # Version catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── plans/justpdf-plan.md             # Architecture plan
```

---

## Build

### Prerequisites

- JDK 11+ (Amazon Corretto 23 tested)
- Android SDK platform 34 + build-tools 34.0.0
  ```bash
  sdkmanager --sdk_root="$HOME/Library/Android/sdk" \
    "platforms;android-34" "build-tools;34.0.0" "platform-tools"
  ```

### Debug build (sideload)

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release build

```bash
./gradlew assembleRelease
# Requires a signing keystore — see Signing section below
```

### Signing (release)

1. Generate a keystore (one-time):
   ```bash
   keytool -genkeypair -v -keystore justpdf.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias justpdf
   ```
2. Add to `app/build.gradle.kts` under `signingConfigs` (do **not** commit the keystore).

---

## Sideload & Set as Default

1. Enable **Install unknown apps** on the device:
   - Settings → Apps → Special app access → Install unknown apps → allow your file manager
2. Transfer `app-debug.apk` to the device (USB, ADB, or file share)
3. Tap the APK to install
4. Open any PDF → tap **"Open with"** → select **JustPdf** → **"Always"**

---

## Corporate / Proxy Networks (Zscaler)

If building behind a Zscaler or SSL-inspecting proxy, the JVM truststore needs the Zscaler CA. A helper truststore is configured in [`gradle.properties`](gradle.properties):

```properties
org.gradle.jvmargs=... -Djavax.net.ssl.trustStore=/tmp/gradle-cacerts.jks ...
```

To regenerate it on a new machine:

```bash
# Export Zscaler cert from the connection
echo | openssl s_client -connect repo.maven.apache.org:443 -showcerts 2>&1 \
  | awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/' \
  | awk 'BEGIN{n=0} /-----BEGIN CERTIFICATE-----/{n++; file="/tmp/zscaler-cert-"n".pem"} {print > file}'

# Copy JVM truststore and import certs
JAVA_HOME=$(/usr/libexec/java_home)
cp "$JAVA_HOME/lib/security/cacerts" /tmp/gradle-cacerts.jks
for i in 1 2 3; do
  "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
    -alias "zscaler-cert-$i" -file "/tmp/zscaler-cert-$i.pem" \
    -keystore /tmp/gradle-cacerts.jks -storepass changeit
done
```

---

## Versioning

This project uses **[Semantic Versioning](https://semver.org/)**: `MAJOR.MINOR.PATCH`

| Increment | When |
|---|---|
| `PATCH` | Bug fixes, no new features |
| `MINOR` | New features, backwards compatible |
| `MAJOR` | Breaking changes or major redesign |

Version is set in [`app/build.gradle.kts`](app/build.gradle.kts):
```kotlin
versionCode = 1      // integer, increment on every release
versionName = "1.0.0"
```

See [`CHANGELOG.md`](CHANGELOG.md) for release history.

---

## Licence

Private use only. Not for distribution.
