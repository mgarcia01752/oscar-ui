#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=3.2.5
COMMIT=0b24f87e55706584e3afefc062c987c2dffd9316
SHA256=a6aa352c9806067b152476c913a541ed3bbe8847c677baaeffa4e3944564aa49
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
