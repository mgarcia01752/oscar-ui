#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEVELOPMENT=0
CHECK_ONLY=0

# Print setup options and the boundary between local and system changes.
usage() {
  printf '%s\n' \
    'Usage: ./install.sh --development [--check]' \
    '' \
    '  -d, --development  Prepare the Linux x86_64 UI development environment' \
    '      --check        Check system prerequisites without downloads or builds' \
    '  -h, --help         Show this help message' \
    '' \
    'Requires Debian/Ubuntu system packages and a Java 21 JDK.' \
    'Missing system packages are reported for manual installation.' \
    'Qt and Oscar are installed locally under .tools/ and .m2/.'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--development) DEVELOPMENT=1 ;;
    --check) CHECK_ONLY=1 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

if [[ "$DEVELOPMENT" != 1 ]]; then
  usage >&2
  exit 1
fi
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
  printf '%s\n' 'Development setup supports Linux x86_64 only.' >&2
  exit 1
fi
if ! command -v dpkg-query >/dev/null || ! command -v apt-get >/dev/null; then
  printf '%s\n' 'System prerequisite checks currently support Debian/Ubuntu only.' >&2
  exit 1
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

PACKAGES=(
  # Repository tools, Maven downloads, Qt bootstrap, and icon conversion.
  ca-certificates curl coreutils git unzip python3 python3-venv python3-pil
  # Debian packaging and headless X11 launch verification.
  dpkg-dev fakeroot xvfb xauth
  libgl1 libegl1 libdbus-1-3 libfontconfig1 libfreetype6
  libx11-xcb1 libxkbcommon0 libxkbcommon-x11-0
  libxcb1 libxcb-cursor0 libxcb-icccm4 libxcb-image0
  libxcb-keysyms1 libxcb-render-util0 libxcb-shape0
  libxcb-randr0 libxcb-xfixes0 libxcb-sync1 libxcb-render0
  libsm6 libice6
)
MISSING=()
for package in "${PACKAGES[@]}"; do
  if [[ "$(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true)" != 'install ok installed' ]]; then
    MISSING+=("$package")
  fi
done

JDK_OK=1
for tool in java javac jpackage; do
  if ! command -v "$tool" >/dev/null; then
    JDK_OK=0
  fi
done
if [[ "$JDK_OK" == 1 ]]; then
  JAVA_VERSION="$(java -version 2>&1 || true)"
  JAVAC_VERSION="$(javac -version 2>&1 || true)"
  JPACKAGE_VERSION="$(jpackage --version 2>&1 || true)"
  if [[ "$JAVA_VERSION" != *'version "21.'* || "$JAVAC_VERSION" != 'javac 21.'* || "$JPACKAGE_VERSION" != 21.* ]]; then
    JDK_OK=0
  fi
fi
if [[ "$JDK_OK" != 1 ]]; then
  printf '%s\n' 'Select a Java 21 JDK (java, javac and jpackage), using JAVA_HOME or PATH.' >&2
  MISSING+=(openjdk-21-jdk)
fi
if [[ ${#MISSING[@]} -gt 0 ]]; then
  printf '%s\n' 'Install system prerequisites, then rerun this command:' >&2
  printf '  sudo apt-get update && sudo apt-get install' >&2
  printf ' %q' "${MISSING[@]}" >&2
  printf '\n' >&2
  exit 1
fi

printf '%s\n' 'System prerequisites verified (Java 21, Qt, development and DEB tools).'
if [[ "$CHECK_ONLY" == 1 ]]; then
  exit 0
fi

bash "$ROOT/scripts/unix/bootstrap-oscar.sh"
QT_VERSION="$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).findtext("{*}properties/{*}qtjambi.version"))' "$ROOT/pom.xml")"
QT_DIRECTORY="${QT_HOME:-${ROOT}/.tools/Qt/${QT_VERSION}/gcc_64}"
if [[ ! -x "$QT_DIRECTORY/bin/qmake" ]]; then
  if [[ -n "${QT_HOME:-}" ]]; then
    printf 'QT_HOME does not contain bin/qmake: %s\n' "$QT_HOME" >&2
    exit 1
  fi
  bash "$ROOT/scripts/unix/setup-qt.sh"
fi
if [[ "$("$QT_DIRECTORY/bin/qmake" -query QT_VERSION)" != "$QT_VERSION" ]]; then
  printf 'Qt %s is required; check QT_HOME or rerun scripts/unix/setup-qt.sh.\n' "$QT_VERSION" >&2
  exit 1
fi
bash "$ROOT/scripts/unix/desktop.sh" build
python3 "$ROOT/tools/git/install-hooks.py"
printf '%s\n' 'Development setup and build passed.' 'Launch the UI with: ./scripts/unix/desktop.sh run'
