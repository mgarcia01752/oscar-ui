#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=3.5.1
COMMIT=99cf8344323ade5d210de137c53bb43854b91171
SHA256=008928bd63dcc5d6a0521dbdd1d27b7430aebc6cc769c124048251de8570af7e
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
"${ROOT}/mvnw" -B "-Dmaven.repo.local=${ROOT}/.m2" org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file "-Dfile=$RELEASE/oscar-thin.jar"
