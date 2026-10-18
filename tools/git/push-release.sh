#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$SCRIPT_DIR/push-release.py" "$@"
