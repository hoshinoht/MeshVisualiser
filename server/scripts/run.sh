#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT/deploy/docker-compose.yml"
ENV_FILE="$ROOT/.env"

# Copy .env.example → .env if missing
if [ ! -f "$ENV_FILE" ]; then
	echo "No .env found — copying from .env.example"
	cp "$ROOT/.env.example" "$ENV_FILE"
	echo "Edit server/.env to set CLOUDFLARE_TUNNEL_TOKEN and MESH_API_KEY, then re-run."
	exit 1
fi

echo "Building mesh-server image..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build

echo "Starting mesh-server + cloudflared tunnel..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile tunnel up -d

echo "Done. View logs with: docker compose -f $COMPOSE_FILE logs -f"
