#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
COMMIT=f7050e591e2f170cb00df7fdd501b2a88cc79860
CHECKOUT="${ROOT}/.tools/oscar-${COMMIT}"
if [[ ! -d "$CHECKOUT" ]]; then
  git clone https://github.com/Comcast/Oscar.git "$CHECKOUT"
  git -C "$CHECKOUT" checkout --detach "$COMMIT"
fi
[[ "$(git -C "$CHECKOUT" rev-parse HEAD)" == "$COMMIT" ]] || { echo 'Oscar checkout has the wrong commit' >&2; exit 1; }
[[ -z "$(git -C "$CHECKOUT" status --porcelain --untracked-files=no)" ]] || { echo 'Oscar checkout has tracked changes' >&2; exit 1; }
"${ROOT}/mvnw" -B -f "$CHECKOUT/pom.xml" "-Dmaven.repo.local=${ROOT}/.m2" -DskipTests install
