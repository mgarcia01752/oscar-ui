#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=3.6.0
COMMIT=66dd66c36f9199ab708092210554ee3f67761325
SHA256=0948d361124dbdfc3d4a69133e812017f51123e9f0302ac53b0057f35bb0f635
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
