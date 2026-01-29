# AR Mesh Visualizer

An Android app that creates a peer-to-peer mesh network between devices and visualizes the connections in augmented reality.

## Features

- **P2P Mesh Networking**: Uses Google Nearby Connections API with P2P_CLUSTER strategy
- **Leader Election**: Bully Algorithm ensures one device hosts the shared anchor
- **Shared AR Space**: ARCore Cloud Anchors create a common coordinate system
- **AR Visualization**: Lines connect devices in the AR view

## Requirements

- Android 7.0+ (API 24)
- ARCore-compatible device
- Google Cloud project with ARCore API enabled (for Cloud Anchors)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

```
models/         → Data classes (MeshState, MessageType, MeshMessage, PeerInfo)
network/        → NearbyConnectionsManager (P2P discovery & messaging)
mesh/           → MeshManager (Bully Algorithm leader election)
ar/             → CloudAnchorManager, PoseManager, LineRenderer
ui/             → MainActivity (Jetpack Compose), MainViewModel
```

## How It Works

1. **Discovery**: Devices find each other via Nearby Connections
2. **Election**: Highest ID device becomes leader (Bully Algorithm)
3. **Anchoring**: Leader hosts Cloud Anchor, shares ID with followers
4. **Sync**: All devices resolve anchor, broadcast relative poses
5. **Visualization**: AR lines drawn between connected devices

## Permissions Required

- Camera (ARCore)
- Location (Nearby Connections)
- Bluetooth (Nearby Connections)
- WiFi (Nearby Connections)

## License

MIT
