#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${SCRIPT_DIR}/.."
MAX_WAIT_SECONDS=120
INSTANCE_PORT="${SERVER_PORT:-8080}"

echo "=== FIS-Engine Startup Script ==="
echo "Instance port: $INSTANCE_PORT"
echo "App directory: $APP_DIR"

wait_for_service() {
    local host="$1"
    local port="$2"
    local service="$3"
    local wait_seconds="${4:-$MAX_WAIT_SECONDS}"
    local elapsed=0

    echo "Waiting for $service at $host:$port..."

    while [ $elapsed -lt $wait_seconds ]; do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo "$service is available ($elapsed seconds)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "ERROR: $service not available after ${wait_seconds} seconds"
    return 1
}

wait_for_url() {
    local url="$1"
    local name="$2"
    local wait_seconds="${3:-$MAX_WAIT_SECONDS}"
    local elapsed=0

    echo "Waiting for $name at $url..."

    while [ $elapsed -lt $wait_seconds ]; do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo "$name is available ($elapsed seconds)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "WARNING: $name not available after ${wait_seconds} seconds"
    return 0
}

echo ""
echo "=== Checking dependencies ==="

if wait_for_service "localhost" "5432" "PostgreSQL" 30; then
    echo "PostgreSQL is ready"
else
    echo "ERROR: PostgreSQL is not available"
    exit 1
fi

if wait_for_service "localhost" "6379" "Redis" 30; then
    echo "Redis is ready"
else
    echo "ERROR: Redis is not available"
    exit 1
fi

if wait_for_service "localhost" "5672" "RabbitMQ" 30; then
    echo "RabbitMQ is ready"
else
    echo "ERROR: RabbitMQ is not available"
    exit 1
fi

echo ""
echo "=== Starting FIS-Engine on port $INSTANCE_PORT ==="

cd "$APP_DIR"

exec ./gradlew bootRun \
    --no-daemon \
    --console=plain \
    -Pspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
    -Pserver.port="$INSTANCE_PORT" \
    -Dserver.port="$INSTANCE_PORT"