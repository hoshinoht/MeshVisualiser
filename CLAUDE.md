# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android AR mesh networking app that creates a peer-to-peer mesh between devices and visualizes connections in augmented reality. Uses Google Nearby Connections for P2P discovery, Bully Algorithm for leader election, and ARCore Cloud Anchors for shared spatial coordinates.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run Android lint
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

Single-module Android app (`app/`) using Kotlin, Jetpack Compose, and MVVM.

**Package structure** (`app/src/main/java/com/example/meshvisualiser/`):

- `MeshVisualizerApp` — Application class with global constants (service ID, timeouts, broadcast intervals)
- `models/` — Data classes: `MeshState` (enum: DISCOVERING→ELECTING→RESOLVING→CONNECTED), `MeshMessage` (JSON-serialized via Gson), `MessageType`, `PeerInfo`
- `network/NearbyConnectionsManager` — Wraps Google Nearby Connections API with P2P_CLUSTER strategy. Handles discovery, advertising, connection lifecycle, and message routing. Peers identified by endpoint IDs; peer IDs exchanged via handshake messages.
- `mesh/MeshManager` — Implements Bully Algorithm for leader election. Sends ELECTION→OK→COORDINATOR messages. Uses `Handler` for election timeouts.
- `ar/CloudAnchorManager` — Hosts (leader) or resolves (follower) ARCore Cloud Anchors using async futures with polling loops
- `ar/PoseManager` — Calculates device poses relative to shared Cloud Anchor for cross-device coordinate consistency
- `ar/LineRenderer` — Renders AR visualizations using SceneView `CylinderNode` (lines) and `SphereNode` (peer markers)
- `ui/MainViewModel` — Coordinates all managers; exposes state via `StateFlow`. Owns the mesh lifecycle: initialize→startMesh→election→anchor→poseBroadcast
- `ui/MainActivity` — Single activity with Compose UI. Embeds `ARSceneView` via `AndroidView` interop. Handles permissions and AR frame updates.

**Data flow**: NearbyConnectionsManager receives bytes → deserializes to MeshMessage → routes to MeshManager → MeshManager triggers callbacks on MainViewModel → ViewModel updates StateFlows → Compose UI reacts.

**Key dependencies**: ARCore 1.45, SceneView (arsceneview 2.3.0), Google Play Services Nearby 19.3, Gson, Compose BOM 2024.11, Kotlin 2.0, AGP 8.7.

## Tech Stack

- **Language**: Kotlin 2.0, JVM target 17
- **Min SDK**: 24 (Android 7.0), Target/Compile SDK: 35
- **UI**: Jetpack Compose with Material 3, dark theme
- **AR**: ARCore + SceneView (Compose-compatible AR rendering)
- **Networking**: Google Nearby Connections (P2P_CLUSTER)
- **Serialization**: Gson for mesh messages
- **Async**: Kotlin Coroutines + StateFlow

## Requirements for Running

- ARCore-compatible physical device (emulators have limited AR support)
- Google Cloud project with ARCore API enabled (for Cloud Anchors)
- Multiple devices needed to test mesh networking
