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

## Repository Structure

```
├── MeshVisualiser/          # Android project root
│   ├── app/                 # Main app module (Kotlin, Compose, ARCore)
│   ├── gradle/              # Gradle wrapper
│   ├── scripts/             # Device helper scripts (install, logcat, devices)
│   ├── build.gradle.kts     # Project-level build config
│   └── local.properties     # Local config (API keys, SDK path — not committed)
├── server/                  # Go backend
│   ├── cmd/                 # Server entrypoint
│   ├── internal/            # App logic and platform code
│   ├── deploy/              # Dockerfile and docker-compose.yml
│   ├── scripts/             # Server helper scripts
│   └── .env.example         # Environment variable template
└── .github/workflows/       # CI pipelines
```

## CI / CD

GitHub Actions runs three workflows on push to `main` and on pull requests:

| Workflow | File | What it does |
|-|-|-|
| **Build, Lint & Unit Tests** | `build.yml` | Lints the Android app, builds debug APK, runs unit tests, builds and tests the Go backend |
| **Instrumented Tests** | `instrumented-tests.yml` | Spins up an Android emulator (API 34) and runs `connectedAndroidTest` |
| **Release Tag Tests** | `release.yml` | Triggered on `v*` tags — runs lint and unit tests for release validation |

## Getting Started

### Prerequisites

- Android SDK (or Android Studio) with SDK 35 installed
- ARCore-compatible physical device (emulators have limited AR support)
- Google Cloud project with [ARCore API enabled](https://console.cloud.google.com/apis/library/arcorecloudanchor.googleapis.com)
- Multiple devices for mesh networking (minimum 2)
- Go 1.25+ (for running the backend locally) **or** Docker

### Server Setup

The Go backend manages cloud anchor storage, leader tracking, network condition simulation, and LLM-powered features (quiz generation, protocol narration).

#### LLM Setup (LM Studio)

The server uses a local LLM for quiz generation and protocol narration. We use [LM Studio](https://lmstudio.ai/) to host the model locally.

1. Download and install [LM Studio](https://lmstudio.ai/)
2. In LM Studio, search for and download **`qwen3.5-9b`**
3. Go to the **Developer** tab and start the local server — it runs on `http://localhost:1234` by default
4. The server connects to LM Studio automatically (no API key needed)

> If running the Go server inside Docker, LM Studio on the host is reachable via `http://host.docker.internal:1234` (the default in `.env`).

#### Option A: Docker (recommended)

```bash
cd server

# Configure environment
cp .env.example .env
# Edit .env and set:
#   MESH_API_KEY    — shared secret for authenticating app requests
#   LLM_BASE_URL   — OpenAI-compatible LLM endpoint (default: http://host.docker.internal:1234)
#   LLM_MODEL      — model name (default: qwen3-8b)

# Start the server
docker compose -f deploy/docker-compose.yml up -d --build
```

The server listens on port **8080** by default (override with `HOST_PORT` in `.env`).

To also expose the server via a Cloudflare tunnel:

```bash
# Set CLOUDFLARE_TUNNEL_TOKEN in .env, then:
docker compose -f deploy/docker-compose.yml --profile tunnel up -d --build
```

#### Option B: Run directly with Go

```bash
cd server

# Set required env var
export MESH_API_KEY="your-secret-key"

go run .    # Starts on :8080
```

#### Server Endpoints

| Method | Path | Description |
|-|-|-|
| `GET` | `/health` | Health check |
| `GET` | `/presets` | List network condition presets |
| `GET` | `/rooms` | List active rooms |
| `GET` | `/rooms/{code}` | Get room details |
| `DELETE` | `/rooms/{code}` | Delete a room |
| `GET/PUT` | `/rooms/{code}/anchor` | Cloud Anchor ID for a room |
| `GET/PUT` | `/rooms/{code}/leader` | Leader ID for a room |
| `GET/PUT` | `/rooms/{code}/conditions` | Network condition simulation |

### App Setup

1. **Clone and configure**

   ```bash
   git clone https://github.com/inf2007/inf2007-team07-2026.git
   cd inf2007-team07-2026/MeshVisualiser

   cp local.properties.example local.properties
   ```

2. **Edit `local.properties`** with your values:

   ```properties
   # Android SDK path (auto-set by Android Studio)
   sdk.dir=/path/to/your/Android/sdk

   # ARCore Cloud Anchor API key (from Google Cloud Console → Credentials)
   ARCORE_CLOUD_ANCHOR_API_KEY=your_api_key_here

   # Must match the MESH_API_KEY set on the server
   MESH_SERVER_API_KEY=your_api_key_here
   ```

   To get an ARCore API key:
   1. Go to [Google Cloud Console](https://console.cloud.google.com/)
   2. Create or select a project
   3. Enable the **ARCore API** under APIs & Services
   4. Create an API key under Credentials
   5. (Recommended) Restrict the key to the ARCore API only

3. **Build and install**

   ```bash
   ./gradlew assembleDebug
   ./scripts/install.sh          # Installs on all connected ADB devices
   ```

   Or open the project in Android Studio (`MeshVisualiser/` directory) and run from there.

### Build Commands

```bash
cd MeshVisualiser
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (requires signing config)
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires device)
./gradlew lint                   # Lint checks
```

### Helper Scripts

```bash
cd MeshVisualiser
./scripts/install.sh             # Build + install APK on all connected devices
./scripts/logcat.sh              # Stream logcat from all devices (colored)
./scripts/devices.sh             # List connected devices with model info
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
