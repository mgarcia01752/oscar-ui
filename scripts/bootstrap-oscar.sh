#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=3.1.0
COMMIT=1767ba35b5d13b30d1d51745f709edbe8459dd68
SHA256=6c53290c55a405017e2c79b56e239de063e25f5ed268283cfad7989d744c9e85
RELEASE="${ROOT}/.tools/oscar-${VERSION}"
mkdir -p "$RELEASE"
curl --fail --location --retry 2 "https://github.com/Comcast/Oscar/releases/download/v${VERSION}/oscar-thin.jar" -o "$RELEASE/oscar-thin.jar.download"
printf '%s  %s\n' "$SHA256" "$RELEASE/oscar-thin.jar.download" | sha256sum --check -
mv "$RELEASE/oscar-thin.jar.download" "$RELEASE/oscar-thin.jar"
for name in LICENSE NOTICE; do
  curl --fail --location --retry 2 "https://raw.githubusercontent.com/Comcast/Oscar/${COMMIT}/${name}" -o "$RELEASE/$name"
done
"${ROOT}/mvnw" -B "-Dmaven.repo.local=${ROOT}/.m2" org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file "-Dfile=$RELEASE/oscar-thin.jar"
