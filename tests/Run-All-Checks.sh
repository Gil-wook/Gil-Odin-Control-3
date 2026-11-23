#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/Run-Checks.sh
python tests/verify_integration.py
