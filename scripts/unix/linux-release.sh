#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
VERSION="$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text)')"
if [[ "${GITHUB_REF_TYPE:-}" == tag && "${GITHUB_REF_NAME:-}" != "v$VERSION" ]]; then
  echo "Release tag must match pom.xml version v$VERSION" >&2
  exit 1
fi
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./scripts/unix/desktop.sh build
python3 ./scripts/build/collect-release-sources.py
./scripts/unix/desktop.sh package --type deb
shopt -s nullglob
PACKAGES=("$ROOT"/dist/linux/deb/*.deb)
[[ "${#PACKAGES[@]}" -eq 1 ]] || { echo 'Expected exactly one generated DEB' >&2; exit 1; }
PACKAGE="${PACKAGES[0]}"
[[ "$(dpkg-deb -f "$PACKAGE" Package)" == oscar-ui ]]
[[ "$(dpkg-deb -f "$PACKAGE" Architecture)" == amd64 ]]
PACKAGE_VERSION="$(dpkg-deb -f "$PACKAGE" Version)"
[[ "$PACKAGE_VERSION" == "$VERSION" || "$PACKAGE_VERSION" == "$VERSION-1" ]]
# Check the actual package without installing anything on the build machine.
PROBE="$(mktemp -d)"
trap 'rm -rf -- "$PROBE"' EXIT
dpkg-deb -e "$PACKAGE" "$PROBE/control"
for maintainer in postinst prerm; do
  cmp "$ROOT/packaging/linux/$maintainer" "$PROBE/control/$maintainer"
done
dpkg-deb -x "$PACKAGE" "$PROBE"
BUILD_JDK="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
python3 "$ROOT/scripts/build/verify-java-distribution.py" \
  --runtime "$PROBE/opt/oscar-ui/lib/runtime" \
  --sources "$PROBE/opt/oscar-ui/lib/app/sources" --jdk-home "$BUILD_JDK"
env -u JAVA_HOME -u QT_PLUGIN_PATH -u LD_LIBRARY_PATH QT_QPA_PLATFORM=offscreen \
  timeout 40s "$PROBE/opt/oscar-ui/bin/OSCAR-UI" \
  --smoke-test "$ROOT/src/test/resources/simple-docsis.txt" | tee "$PROBE/smoke.log"
grep -q 'UI_SMOKE_OK' "$PROBE/smoke.log"
mkdir -p "$ROOT/dist/linux/release"
cp "$PACKAGE" "$ROOT/dist/linux/release/oscar-ui_amd64.deb"
