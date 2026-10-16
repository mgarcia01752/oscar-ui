#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'Linux x86_64 required' >&2; exit 1; }
python3 -m venv "${ROOT}/.tools/venv"
"${ROOT}/.tools/venv/bin/python" -m pip install aqtinstall==3.3.0
"${ROOT}/.tools/venv/bin/python" -m aqt install-qt linux desktop 6.8.3 linux_gcc_64 --archives qtbase icu -O "${ROOT}/.tools/Qt"
