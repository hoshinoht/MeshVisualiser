# MeshVisualiser

An Android augmented reality app that creates a peer-to-peer mesh network between nearby devices and visualizes the network topology in AR. Built as an educational tool for learning networking concepts like mesh topologies, leader election, CSMA/CD, and more.

## Features

### Mesh Networking
- **Peer-to-peer discovery** via Google Nearby Connections using the P2P_CLUSTER strategy
- **Automatic leader election** using the Bully Algorithm (ELECTION → OK → COORDINATOR)
- **Real-time pose broadcasting** so each device knows where the others are in physical space
- **RTT (Round-Trip Time) measurement** between connected peers

### Augmented Reality
- **AR mesh visualization** — see connected devices as spheres and network links as cylinders rendered in your camera view
- **Shared coordinate system** using ARCore Cloud Anchors so all devices agree on a common spatial origin
- **Packet animation** — watch data packets travel along mesh links in AR

### CSMA/CD Simulation
- **Interactive simulation** of the Carrier Sense Multiple Access / Collision Detection protocol
- Visualizes the full cycle: sensing → transmitting → collision → jam signal → exponential backoff → retry
- Collision probability scales with the number of connected peers

### Networking Quiz
- **Dynamic questions** generated from live mesh state (who is the leader, how many peers, current RTT, topology type)
- **Concept questions** covering CSMA/CD, TCP/UDP, OSI layers, Bully Algorithm, Cloud Anchors, and more
- 30-second timer per question, score tracking

### Transmission Modes
- **Direct mode** — standard message passing between peers
- **CSMA/CD mode** — messages go through the simulated CSMA/CD protocol before transmission

### Other
- Onboarding flow for first-time users
- Connection management screen with peer list
- 2D topology graph view alongside the AR view
- Dark theme with glassmorphism UI components
- User preferences persisted via DataStore

## Screenshots

_Coming soon_

## Tech Stack

| Component | Technology |
|-|-|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| AR | ARCore 1.52 + SceneView 2.3.0 |
| Networking | Google Nearby Connections 19.3 |
| Architecture | MVVM with StateFlow |
| Serialization | Gson |
| Preferences | DataStore |
| Build | Gradle 8.7, AGP 8.7, JVM 17 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 |

## Prerequisites

- **Android Studio** Ladybug (2024.2) or later
- **JDK 17**
- **ARCore-compatible physical device** — emulators have limited AR support
- **Google Cloud project** with the [ARCore API enabled](https://console.cloud.google.com/apis/library/arcorecloudanchor.googleapis.com) (required for Cloud Anchors)
- **Multiple devices** to test mesh networking (minimum 2)

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/nicholaschua02/inf2007-team07-2026.git
cd inf2007-team07-2026
```

### 2. Open in Android Studio

Open the project root in Android Studio. Gradle sync will download all dependencies automatically.

### 3. Configure Cloud Anchors

Ensure your Google Cloud project has the ARCore Cloud Anchor API enabled. The app uses ARCore's default authentication — no API key file is needed in the project, but the device must be signed in to a Google account.

### 4. Build and run

```bash
./gradlew assembleDebug
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`. Install it on two or more ARCore-compatible devices on the same local network.

### 5. Usage

1. Launch the app on each device and complete the onboarding
2. Tap **Start Mesh** — devices will begin discovering each other via Bluetooth/WiFi
3. Once peers are found, the Bully Algorithm runs automatically to elect a leader
4. The leader's device hosts a Cloud Anchor; other devices resolve it
5. Point your camera at a shared physical space — you'll see peer positions and mesh links rendered in AR
6. Switch between **Direct** and **CSMA/CD** transmission modes
7. Open the **Quiz** to test your networking knowledge based on the live mesh

## Project Structure

```
app/src/main/java/com/example/meshvisualiser/
├── MeshVisualizerApp.kt              # Application class, global constants
├── ar/
│   ├── CloudAnchorManager.kt         # ARCore Cloud Anchor hosting/resolving
│   ├── LineRenderer.kt               # AR mesh lines and peer markers
│   ├── PacketRenderer.kt             # Animated packet visualization
│   └── PoseManager.kt                # Cross-device pose calculation
├── data/
│   └── UserPreferencesRepository.kt  # DataStore preferences
├── mesh/
│   └── MeshManager.kt                # Bully Algorithm leader election
├── models/
│   ├── MeshMessage.kt                # JSON-serialized mesh messages
│   ├── MeshState.kt                  # DISCOVERING → ELECTING → CONNECTED
│   ├── MessageType.kt                # Message type enum
│   ├── PeerInfo.kt                   # Peer metadata
│   └── TransmissionMode.kt           # DIRECT / CSMA_CD
├── navigation/
│   ├── NavGraph.kt                   # Navigation graph
│   └── Routes.kt                     # Route constants
├── network/
│   └── NearbyConnectionsManager.kt   # Google Nearby Connections wrapper
├── quiz/
│   └── QuizEngine.kt                 # Dynamic + concept quiz generation
├── simulation/
│   └── CsmacdSimulator.kt            # CSMA/CD protocol simulation
└── ui/
    ├── MainActivity.kt                # Single activity, AR + Compose
    ├── MainViewModel.kt               # Central ViewModel, mesh lifecycle
    ├── PermissionHelper.kt            # Runtime permission handling
    ├── components/
    │   ├── ConnectionGraphView.kt     # 2D topology graph
    │   ├── CsmacdOverlay.kt           # CSMA/CD simulation UI
    │   ├── GlassSurface.kt            # Glassmorphism composable
    │   ├── ModeSegmentedButton.kt     # Transmission mode selector
    │   ├── QuizOverlay.kt             # Quiz UI overlay
    │   └── TopologyView.kt            # Network topology visualization
    ├── screens/
    │   ├── ConnectionScreen.kt        # Peer connection management
    │   └── OnboardingScreen.kt        # First-launch onboarding
    └── theme/
        ├── Color.kt
        ├── Shape.kt
        ├── Theme.kt
        └── Type.kt
```

## Architecture

The app follows **MVVM** with a unidirectional data flow:

```
NearbyConnectionsManager (bytes in)
        ↓
    MeshMessage (Gson deserialization)
        ↓
    MeshManager (Bully Algorithm / routing)
        ↓
    MainViewModel (state coordination)
        ↓
    StateFlow → Compose UI + AR Scene
```

**Key design decisions:**
- All peer communication goes through `NearbyConnectionsManager` which handles connection lifecycle and message serialization
- `MeshManager` owns the election protocol and is stateless beyond current election round
- `MainViewModel` is the single source of truth — it coordinates all managers and exposes state via `StateFlow`
- AR rendering is decoupled into `LineRenderer`, `PacketRenderer`, and `PoseManager`, each with a single responsibility

## Permissions

The app requires the following runtime permissions:

| Permission | Purpose |
|-|-|
| Camera | ARCore scene rendering |
| Fine Location | Nearby Connections peer discovery |
| Bluetooth Scan/Connect/Advertise | P2P communication |
| Nearby WiFi Devices | WiFi-based peer discovery (Android 13+) |

Internet access is used for Cloud Anchor hosting/resolving.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device)
./gradlew lint                   # Run Android lint checks
```

## Team

**INF2007 Team 07** — Singapore Institute of Technology, 2026

## License

This project is developed for academic purposes as part of the INF2007 module.
