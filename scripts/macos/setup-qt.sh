#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "$(uname -s)" == Darwin && "$(uname -m)" == arm64 ]] || { echo 'Apple Silicon macOS required' >&2; exit 1; }
QT_VERSION="$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).findtext("{*}properties/{*}qtjambi.version"))' "$ROOT/pom.xml")"
echo "[macOS] Installing Qt $QT_VERSION in the project tools directory..."
python3 -m venv "$ROOT/.tools/macos-venv"
"$ROOT/.tools/macos-venv/bin/python" -m pip install aqtinstall==3.3.0
"$ROOT/.tools/macos-venv/bin/python" -m aqt install-qt mac desktop "$QT_VERSION" clang_64 --archives qtbase -O "$ROOT/.tools/Qt"
