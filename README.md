# ATAKScythePlugin

**ATAK CIV Plugin** that interfaces with `rf_scythe_api_server.py` — bringing RF hypergraph intelligence directly into ATAK's tactical map.

---

## Architecture

```
ATAK CIV (host app v4.6.0)
  └── ATAKScythePlugin.apk
        ├── ScytheLifecycle          ← ATAK plugin entry point
        ├── ScytheMapComponent       ← registers layer + receiver
        ├── ScytheDropDownReceiver   ← UI panel (3 tabs)
        ├── api/
        │    ├── ScytheApiClient     ← OkHttp REST → rf_scythe_api_server:8080
        │    └── SseStreamClient     ← SSE /api/entities/stream (live entities)
        ├── layer/
        │    ├── RFSignalLayer       ← ATAK AbstractLayer (node data)
        │    └── GLRFSignalLayer     ← GL renderer (colored circles)
        └── model/
             ├── RFNode              ← RF hypergraph node DTO
             └── ScytheEntity        ← SSE entity DTO
```

## Scythe API Integrations

| Tab | Endpoint | Action |
|---|---|---|
| Connect | `POST /api/operator/register` | Register operator, get session token |
| Connect | `POST /api/operator/login` | Fallback auth |
| RF Intel | `GET /api/rf-hypergraph/visualization` | Pull nodes → map layer |
| RF Intel | `GET /api/tak/cot?format=xml_list` | Export CoT → inject to ATAK natively |
| RF Intel | `POST /api/tak/send` | Push CoT → TAK UDP multicast |
| Missions | `GET /api/missions` | List active missions |
| Missions | `POST /api/missions` | Create new mission |
| Background | `GET /api/entities/stream?token=X` | SSE real-time entity sync |

## ELF 16KB Compliance

This plugin contains **no native `.so` libraries** — eliminating ELF alignment risk entirely.

The ELF compatibility warning in `AndroidAppSceneview` and `WebXRRFVisualization` originates from **bundled TFLite `.so` files** in those projects (compiled without `-Wl,--max-page-size=16384`). Those projects are not part of this plugin.

Precautionary packaging flags are applied regardless:

| Flag | Location | Effect |
|---|---|---|
| `android:extractNativeLibs="false"` | `AndroidManifest.xml` | Keeps embedded .so files page-aligned at install |
| `jniLibs { useLegacyPackaging = false }` | `app/build.gradle` | Prevents 4KB-alignment extraction |
| `android.packagingOptions.jniLibs.useLegacyPackaging=false` | `gradle.properties` | Gradle-level enforcement |

**If native code is added later:**
1. Build with NDK r27+
2. CMakeLists.txt: `target_link_options(mylib PRIVATE -Wl,--max-page-size=16384)`
3. Run `AndroidAppSceneview/verify_16kb_alignment.sh` against the new APK

## Build Instructions

### Prerequisites

1. **ATAK CIV SDK** — obtain from [TAK.gov](https://tak.gov) or build from source:
   ```
   AndroidTacticalAssaultKit-CIV-main/
   ```

2. **ATAK stub JARs** — place in `app/libs/`:
   ```
   app/libs/
   ├── ATAKCivRelease-4.6.0-api.jar   ← ATAK plugin API classes
   └── takkernel.jar                   ← optional: TAK kernel
   ```
   
   > Alternatively, configure `takrepo.url` in `local.properties` if you have access to the TAK Maven repository, and switch `compileOnly fileTree(...)` to `compileOnly 'com.atakmap.app:ATAK-CIV-RELEASE:4.6.0:api@jar'` in `app/build.gradle`.

3. **Android SDK** (compileSdk 34, minSdk 24)

4. **JDK 11+**

### Build

```bash
cd ATAKScythePlugin
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Configure Server Address

Edit `app/build.gradle` before building (or override at runtime via the Connect tab):

```groovy
buildConfigField "String", "DEFAULT_SCYTHE_HOST", '"YOUR_SERVER_IP"'
buildConfigField "int",    "DEFAULT_SCYTHE_PORT", '8080'
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ATAK will discover the plugin on next launch. Tap the RF antenna toolbar button to open the panel.

## Runtime Usage

1. **Connect tab**: Enter Scythe server IP:port and your callsign → tap CONNECT
2. **RF Intel tab**: Auto-populated with RF nodes from the hypergraph. Tap:
   - **REFRESH** — pull latest nodes
   - **INJECT CoT** — add nodes as ATAK markers
   - **PUSH TAK** — tell server to broadcast CoT to TAK network (UDP multicast)
3. **Missions tab**: View and create missions tracked in rf_scythe_api_server

Background SSE stream automatically syncs entity changes in real-time.

## Starting rf_scythe_api_server.py

```bash
cd /home/spectrcyde/NerfEngine
python3 rf_scythe_api_server.py --host 0.0.0.0 --port 8080
```

Ensure your Android device and the server are on the same network.
