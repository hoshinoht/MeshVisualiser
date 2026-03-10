#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Copy .env.example → .env if missing
if [ ! -f .env ]; then
	echo "No .env found — copying from .env.example"
	cp .env.example .env
	echo "Edit server/.env to set CLOUDFLARE_TUNNEL_TOKEN and MESH_API_KEY, then re-run."
	exit 1
fi

echo "Building mesh-server image..."
docker compose build

echo "Starting mesh-server + cloudflared tunnel..."
docker compose --profile tunnel up -d

echo "Done. View logs with: docker compose logs -f"
