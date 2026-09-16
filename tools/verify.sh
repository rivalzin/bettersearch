#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/source_audit.py
./gradlew dist "$@"
python3 tools/source_audit.py --jars
