#!/usr/bin/env bash
set -euo pipefail

LOAD_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$LOAD_DIR/purchase-load.sh" db
