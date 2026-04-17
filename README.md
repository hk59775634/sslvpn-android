# SSL VPN Android (standalone)

This repository is a standalone Android client prototype for the existing Go SSL VPN project.
It is intentionally isolated and does not modify the original repository.

## Current status

- Android app skeleton with:
  - `MainActivity` (server/user/password inputs)
  - `VpnService` foreground service lifecycle (`connect` / `disconnect`)
  - Basic TUN establishment through `VpnService.Builder`
- Gradle Kotlin DSL build configured.

## Build

1. Install Android SDK commandline tools.
2. Set:
   - `ANDROID_SDK_ROOT`
   - `ANDROID_HOME` (optional, usually same as `ANDROID_SDK_ROOT`)
3. Install required SDK packages:
   - `platform-tools`
   - `platforms;android-35`
   - `build-tools;35.0.0`
4. Run:

```bash
./gradlew assembleDebug
```

Expected APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Next milestone

Integrate Go core (`deps/sslcon` compatible logic) via gomobile/JNI and pass TUN fd from `VpnService`.
