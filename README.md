# REMOTE MAGIC – Universal Smart TV Remote

Android (Kotlin, Jetpack Compose, MVVM) remote for smart TVs. Arabic, French and English, with RTL.

- Changes: [CHANGELOG.md](CHANGELOG.md)
- دليل الاستعمال: [USER_GUIDE_AR.md](USER_GUIDE_AR.md)
- Guide d'utilisation : [USER_GUIDE_FR.md](USER_GUIDE_FR.md)

## Supported protocols

| Target | Protocol | Notes |
|---|---|---|
| Android TV / Google TV | Android TV Remote v2 (TLS 6466/6467) | pairing code |
| Samsung Tizen (2016+) | WebSocket 8002 | accept on TV |
| LG webOS | SSAP 3001/3000 | accept on TV; screenshot if the TV allows it |
| Roku | External Control Protocol 8060 | |
| TVs / TV boxes accepting a Bluetooth keyboard | Bluetooth Classic HID (Android 9+) | mouse and keyboard; no app launching |

**Not supported:** Apple TV / iOS, infrared, BLE remote control, Wake-on-Bluetooth, DLNA control.

LG and Samsung are controlled over Wi-Fi; their Bluetooth use depends on the TV accepting a HID keyboard.

**Testing status:** unit tests cover message encoding, discovery parsing, Roku HTTP (mock server), connection/reconnection logic and voice parsing. **No protocol has been verified on a physical TV**, and there are no instrumented UI, Bluetooth radio or database tests. Treat each brand as untested until you try it.

## Build

JDK 17 and Android SDK 35:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleRelease bundleRelease
```

Outputs are under `app/build/outputs/`. GitHub Actions builds the same on every push to `main`, manually, and attaches the files to a release for `v*` tags.

**Release signing:** set `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the release APK/AAB are signed with a debug key: fine for side-loading, **not** accepted by Google Play. The workflow uses that debug key.

## Structure

`app/src/main/java/com/remotemagic/app/`: `data`, `domain`, `bluetooth`, `wifi`, `discovery`, `protocols`, `voice`, `remote`, `ui`, `navigation`, `settings`, `sharing`, `utils`.
