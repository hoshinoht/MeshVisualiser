# MeshVisualiser

An Android AR app that creates a peer-to-peer mesh network between nearby devices and visualizes the topology in augmented reality. Built as an educational tool for learning networking concepts like mesh topologies, leader election, vector clocks, CSMA/CD, TCP/UDP, and more.

## Features

- **P2P Mesh Networking** — device discovery via Google Nearby Connections (P2P_CLUSTER), automatic leader election (Bully Algorithm), real-time pose broadcasting
- **AR Visualization** — connected devices rendered as nodes with mesh links, shared coordinate system via ARCore Cloud Anchors, animated packet movement along edges
- **Vector Clocks** — Lamport-style vector clock tracking with causality arrows, concurrent event badges, and a clock inspector overlay
- **CSMA/CD Simulation** — interactive protocol simulation with collision detection, jam signals, and exponential backoff
- **AI-Powered Quiz** — LLM-generated questions from live mesh state with a 50-question static fallback pool
- **Protocol Narrator** — contextual explanations of networking events as they happen in the mesh
- **TCP/UDP Modes** — visualize acknowledgements, retransmissions, and packet loss across transmission modes
- **Network Condition Simulation** — server-side presets (WiFi, 4G, 3G, satellite, congested) controlling latency, jitter, and packet loss

## Tech Stack

| Component | Technology |
|-|-|
| Language | Kotlin 2.1, JVM 17 |
| UI | Jetpack Compose + Material 3 Expressive |
| AR | ARCore 1.52 + SceneView 2.3.3 |
| P2P | Google Nearby Connections 19.3 |
| Backend | Go (stdlib HTTP server) |
| Build | Gradle 8.10.2, AGP 8.8.0 |
| SDK | Min 24 / Target 35 |

## Getting Started

### Prerequisites

- ARCore-compatible physical device (emulators have limited AR support)
- Google Cloud project with [ARCore API enabled](https://console.cloud.google.com/apis/library/arcorecloudanchor.googleapis.com)
- Multiple devices for mesh networking (minimum 2)
- Go 1.25+ for the backend server

### Setup

```bash
git clone https://github.com/inf2007/inf2007-team07-2026.git
cd inf2007-team07-2026/MeshVisualiser

# Copy and configure local.properties
cp local.properties.example local.properties
# Set sdk.dir and ARCORE_CLOUD_ANCHOR_API_KEY

# Build
./gradlew assembleDebug

# Install on all connected devices
./scripts/install.sh
```

### Running the Backend

```bash
cd server
go run .    # Starts on :8080
```

### Build Commands

```bash
cd MeshVisualiser
./gradlew assembleDebug          # Debug APK
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires device)
./gradlew lint                   # Lint checks
```

## Architecture

Single-module Android app using MVVM with unidirectional data flow:

```
NearbyConnectionsManager (bytes) -> MeshMessage (Gson) -> MeshManager (Bully/routing)
    -> MainViewModel (StateFlow) -> Compose UI + AR Scene
```

Key packages under `com.meshvisualiser`:

| Package | Purpose |
|-|-|
| `ar/` | Cloud Anchors, AR node/session management, pose calculation |
| `mesh/` | Bully Algorithm, vector clock tracking |
| `network/` | Nearby Connections wrapper, byte array pooling |
| `ai/` | LLM client, protocol narrator, session summarizer |
| `quiz/` | Dynamic + static quiz engine |
| `simulation/` | CSMA/CD protocol simulator |
| `ui/` | Screens, components, delegates, Material 3 Expressive theme |

## Team

**INF2007 Team 07** — Singapore Institute of Technology, 2026
