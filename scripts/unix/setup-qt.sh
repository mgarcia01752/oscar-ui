#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'Linux x86_64 required' >&2; exit 1; }
QT_VERSION="$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).findtext("{*}properties/{*}qtjambi.version"))' "$ROOT/pom.xml")"
python3 -m venv "${ROOT}/.tools/venv"
"${ROOT}/.tools/venv/bin/python" -m pip install -r "$ROOT/scripts/build/qt-tools-requirements.txt"
"${ROOT}/.tools/venv/bin/python" -m aqt install-qt linux desktop "$QT_VERSION" linux_gcc_64 --archives qtbase icu -O "${ROOT}/.tools/Qt"
