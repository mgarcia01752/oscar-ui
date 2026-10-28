#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=3.7.5
COMMIT=87102343d22cb50ea3586bef254b5eefdd07009e
SHA256=7f02f429d55d8152735ce5d1e0b88325f2d2aae193011d8b31d40fdca835b4a7
RELEASE="${ROOT}/.tools/oscar-${VERSION}"
mkdir -p "$RELEASE"
curl --fail --location --retry 2 "https://github.com/Comcast/Oscar/releases/download/v${VERSION}/oscar-thin.jar" -o "$RELEASE/oscar-thin.jar.download"
if command -v sha256sum >/dev/null 2>&1; then
  CHECKSUM=(sha256sum --check -)
else
  CHECKSUM=(shasum -a 256 --check -)
fi
printf '%s  %s\n' "$SHA256" "$RELEASE/oscar-thin.jar.download" | "${CHECKSUM[@]}"
mv "$RELEASE/oscar-thin.jar.download" "$RELEASE/oscar-thin.jar"
for name in LICENSE NOTICE; do
  curl --fail --location --retry 2 "https://raw.githubusercontent.com/Comcast/Oscar/${COMMIT}/${name}" -o "$RELEASE/$name"
done
"${ROOT}/mvnw" -B "-Dmaven.repo.local=${ROOT}/.m2" -f "${ROOT}/pom.xml" install:install-file "-Dfile=$RELEASE/oscar-thin.jar"
