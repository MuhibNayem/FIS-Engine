#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${SCRIPT_DIR}/.."

echo "=== Stopping FIS-Engine Instances ==="

pkill -f "fis-process.*server.port" 2>/dev/null || true

pkill -f "GradleDaemon.*fis-process" 2>/dev/null || true

pkill -f "bootRun" 2>/dev/null || true

sleep 2

if pgrep -f "fis-process\|bootRun" > /dev/null 2>&1; then
    echo "Waiting for processes to terminate..."
    sleep 3
fi

pkill -9 -f "fis-process\|bootRun" 2>/dev/null || true

echo "=== All FIS-Engine instances stopped ==="