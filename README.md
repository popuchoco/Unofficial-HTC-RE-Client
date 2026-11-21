# Unofficial HTC RE Client — RE Lens

[![Android CI](https://github.com/popuchoco/Unofficial-HTC-RE-Client/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/popuchoco/Unofficial-HTC-RE-Client/actions/workflows/android-ci.yml)

**English** · [繁體中文](README-zh-tw.md)

RE Lens is an unofficial Android client for the HTC RE camera. It features a Material 3 interface and is designed around modern Android permissions and component lifecycles. This project is not affiliated with HTC Corporation.

## Features

> **Current version: 0.6.3.** Connection-flow recovery was improved so the app creates a fresh Wi-Fi Direct group and retries once when the first IP wait times out or the camera reports an A304 failure. Replies from an older session cannot affect the new attempt. This fix still needs verification on HTC RE hardware.

Wi-Fi Direct group setup uses asynchronous group information queries. After Android accepts `createGroup()`, the app waits until the owner group's SSID and passphrase are available before starting the BLE bootstrap.

Version `0.6.3` no longer retries indefinitely with the same owner group after an IP timeout. The app recreates the group once; if the second attempt also fails, it stops and marks the next connection to force a group recreation, avoiding a stale or failed group.

- A leftmost **Connection** screen for BLE scanning and connection, Wi-Fi Direct groups, background connection, disconnect alerts, and Bluetooth/Wi-Fi status.
- A serialized GATT command queue sends the Wi-Fi Direct SSID, passphrase, and station configuration. Each packet waits for its characteristic callback before the next is sent.
- Parses the RE Wi-Fi configuration status and camera IPv4 address for GC1 sockets and RTSP.
- Native A000/GC1 socket operations for photo capture, video recording, firmware version and storage queries, album listing, deletion, and chunked original-file downloads.
- Live preview is planned. The streaming screen currently provides YouTube Live controls; the RTSP-to-RTMP media relay is not yet implemented.
- Device information, dark/light/system themes, a debug console, and operation-log export.

## Platform Requirements

| Setting | Value |
| --- | ---: |
| `minSdk` | 26 |
| `targetSdk` | 32 |
| `compileSdk` | 35 |
| Java | 17 |
| Application ID | `tw.xiaoxin.relens` |

Target SDK 32 is a deliberate compatibility decision: it supports the Android 12+ `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permission model while deferring the target 33+ `NEARBY_WIFI_DEVICES` path. `compileSdk` 35 allows the project to build against a newer SDK; the HTC RE hardware compatibility matrix must be completed before raising the target SDK.

The manifest retains `usesCleartextTraffic=true` for local HTTP used by other camera profiles. The verified A000 capture, device, and album operations currently use GC1 sockets. YouTube API traffic uses HTTPS.

## Connection Overview

For the first connection, the phone establishes a control channel with the RE camera over BLE, then creates a Wi-Fi Direct group. The app sends the group's SSID and passphrase to the camera sequentially over GATT. The camera joins as a station and reports its IPv4 address. GC1 control, file downloads, and RTSP preview then use that IP network.

Follow the [connection guide](docs/CONNECTION_GUIDE.md) for first-time setup, reconnecting, and reporting issues. Protocol handshakes and queue behavior are documented in [BLE and Wi-Fi Direct bootstrap](docs/CONNECTION_BOOTSTRAP.md); connection state and test gates are documented in the [A000 connection-flow audit](docs/A000_CONNECTION_FLOW_AUDIT.md).

## Troubleshooting and Bug Reports

If Android intermittently rejects a GATT operation, the control channel does not finish subscribing, or Wi-Fi bootstrap does not continue, keep the RE camera powered on and nearby. On the **Connection** screen, tap Wi-Fi Direct connect once and wait for the flow to finish; do not tap repeatedly in quick succession. If retrying does not recover, enable debugging on the **Device** screen, export the operation log, and open a [GitHub issue](https://github.com/popuchoco/Unofficial-HTC-RE-Client/issues/new) with:

- RE Lens version, phone model, and Android version.
- Steps taken before the problem and any on-screen messages.
- The exported log (check that it does not contain passwords you entered or other personal information before uploading).

## Downloaded Media Location

On Android 10 and later, files are saved through MediaStore instead of the app-specific `Android/data` directory. Photos and videos appear in:

```text
Pictures/RE Lens/
Movies/RE Lens/
```

They can be opened in Samsung Gallery, Google Photos, or Samsung My Files, and are not deleted when the app is uninstalled. Android 8–9 use the same public folders and require storage permission on first use.

## Releases

The current GitHub release is [RE Lens v0.6.3](https://github.com/popuchoco/Unofficial-HTC-RE-Client/releases/tag/v0.6.3). Download the signed APK and its SHA-256 checksum from the release page. See the [release workflow](.github/workflows/android-release.yml) and [release process documentation (Traditional Chinese)](docs/RELEASE.md) for CI, signing, and publishing details.

## Documentation

- [Documentation index and status](docs/README.md)
- [Product specification](docs/SPECIFICATION.md)
- [Software design](docs/SOFTWARE_DESIGN.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Android compatibility](docs/ANDROID_COMPATIBILITY.md)
- [Protocol notes](docs/PROTOCOL_NOTES.md)
- [BLE and Wi-Fi Direct connection bootstrap](docs/CONNECTION_BOOTSTRAP.md)
- [Connection guide](docs/CONNECTION_GUIDE.md)
- [A000 connection-flow audit](docs/A000_CONNECTION_FLOW_AUDIT.md)
- [Code review response](docs/CODE_REVIEW_RESPONSE.md)
- [YouTube Live — phase 3](docs/YOUTUBE_LIVE.md)
- [Test plan](docs/TEST_PLAN.md)

## Build

Install Android SDK 35 and JDK 17. Use the Gradle Wrapper included in the repository:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated under `app/build/outputs/apk/debug/`. For CI, APK releases, and Android signing-key setup, see the [release process documentation](docs/RELEASE.md).

## Third-Party Reference

The YouTube Live use case was informed by [JALsnipe/HTC-RE-YouTube-Live-Android](https://github.com/JALsnipe/HTC-RE-YouTube-Live-Android). That project does not provide a clearly identifiable license, so no code, assets, or native libraries were imported. The control surface was implemented independently using Google's current authorization and the YouTube Data API.

## Trademark Notice

HTC and HTC RE are trademarks of their respective owners. This community compatibility client is not affiliated with, endorsed by, or supported by HTC.
