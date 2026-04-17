# SSL VPN Android (standalone)

Android client for **Cisco AnyConnect–compatible** gateways using **OpenConnect / libopenconnect** (same stack as the [OpenConnect for Android](https://f-droid.org/packages/net.openconnect_vpn.android/) app on F-Droid).

## What works

- Full client path: **parse URL → cookie / auth forms → CSTP → `VpnService` TUN (`setupTunFD`) → DTLS → `mainloop`**, aligned with the flow in classic `ics-openconnect`.
- **Username / password / optional group** from the UI; **CSD** helper + **curl** binary are unpacked from app assets at runtime (files come from the bootstrap step below).
- **Server TLS** is accepted without interactive pinning (suitable for labs; tighten `onValidatePeerCert` for production).

## Build requirements

1. **JDK 17** for Gradle (Android Gradle Plugin 8.x).  
   - This repo includes `gradle/gradle-daemon-jvm.properties` so Gradle prefers Java 17 when it is installed.
2. **Android SDK** (`ANDROID_SDK_ROOT` / `ANDROID_HOME`) with API **35** and build-tools as before.
3. **Network on first build**: `preBuild` depends on `:app:syncOpenConnectBootstrap`, which downloads the F-Droid OpenConnect APK and extracts `libopenconnect.so`, `libstoken.so`, and `curl-bin` into `app/src/main/jniLibs` and `app/src/main/assets/raw/…` (see `legal/bootstrap-source.txt`).

```bash
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

### CI / prebuilt APK

GitHub Actions workflow **build-apk** uploads the debug APK as an artifact on each push / pull request (Temurin 17 + `android-actions/setup-android`).

## Mock engine (UI / TUN only)

In `app/build.gradle.kts`, set `USE_MOCK_ENGINE` to `true` to skip libopenconnect and only exercise the foreground service + placeholder TUN.

## Third-party code

- `org.infradead.libopenconnect.LibOpenConnect` — upstream OpenConnect (LGPL-2.1).
- Bootstrap native binaries — extracted from F-Droid `net.openconnect_vpn.android` build **1120** (LGPL; source offer in `legal/bootstrap-source.txt`).
