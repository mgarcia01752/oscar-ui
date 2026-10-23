#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=3.7.0
COMMIT=85661ace2df754a9b396b0e6b4375a5582016049
SHA256=898ba5332ac4a28f2699d45077ca06ee0aa7226e28efbef2208b1b1d3a0e5ae6
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
