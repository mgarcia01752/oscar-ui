#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE=""
TAG=""
BUMP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --master|--branch)
      [[ -z "$MODE" ]] || { echo 'Choose only one push mode.' >&2; exit 2; }
      MODE=branch
      shift
      ;;
    --tag-major|--tag-minor|--tag-patch)
      [[ -z "$MODE" ]] || { echo 'Choose only one push mode.' >&2; exit 2; }
      MODE=release
      BUMP="${1#--tag-}"
      shift
      ;;
    --tag)
      [[ -z "$MODE" && $# -ge 2 ]] || { echo 'Use --tag vMAJOR.MINOR.PATCH as the only push mode.' >&2; exit 2; }
      MODE=tag
      TAG="$2"
      shift 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: tools/git/git-push.sh MODE
Modes: --master | --branch | --tag TAG | --tag-major | --tag-minor | --tag-patch

--master, --branch  Push only the current main/master branch to origin.
--tag TAG           Push only an existing release tag at HEAD to origin.
                   The tag must match the pom.xml version.

--tag-major        Bump major, commit, tag, and push branch plus new tag.
--tag-minor        Bump minor, commit, tag, and push branch plus new tag.
--tag-patch        Bump patch, commit, tag, and push branch plus new tag.

Requires committed changes and installs the repository-local pre-push hook.
The hook runs all local checks and blocks upload if any check fails.
Never force-pushes. Branch/existing-tag modes do not create commits or tags.
Release-bump modes delegate to push-release.sh and run its preflight checks.
A tag push can trigger release CI and publication after required checks pass.
Choose exactly one mode. No version bump is selected by default.
EOF
      exit 0
      ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done
[[ -n "$MODE" ]] || { echo 'Choose a push mode (see --help).' >&2; exit 2; }
[[ "$(git -C "$ROOT" rev-parse --show-toplevel)" == "$ROOT" ]] || exit 1
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || { echo 'Commit or stash changes before pushing.' >&2; exit 1; }
if [[ "$MODE" == release ]]; then
  echo "[git-push] Starting $BUMP release bump with local validation."
  exec bash "$ROOT/tools/git/push-release.sh" "--push-$BUMP"
fi
if [[ "$MODE" == branch ]]; then
  BRANCH="$(git -C "$ROOT" symbolic-ref --quiet --short HEAD)" || { echo 'Check out main or master before pushing.' >&2; exit 1; }
  [[ "$BRANCH" == main || "$BRANCH" == master ]] || { echo 'Branch mode only permits main or master.' >&2; exit 1; }
  REF="refs/heads/$BRANCH"
else
  [[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Tag must be vMAJOR.MINOR.PATCH.' >&2; exit 2; }
  REF="refs/tags/$TAG"
  git -C "$ROOT" show-ref --verify --quiet "$REF" || { echo "Local tag does not exist: $TAG" >&2; exit 1; }
  [[ "$(git -C "$ROOT" rev-parse "$REF^{commit}")" == "$(git -C "$ROOT" rev-parse HEAD)" ]] || { echo 'Check out the tagged commit before pushing it.' >&2; exit 1; }
  VERSION="$(python3 -c 'import sys, xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).getroot().find("{http://maven.apache.org/POM/4.0.0}version").text)' "$ROOT/pom.xml")"
  [[ "$TAG" == "v$VERSION" ]] || { echo "Tag must match pom.xml version v$VERSION." >&2; exit 1; }
fi
if [[ "$(git -C "$ROOT" config --bool --get remote.origin.mirror || true)" == true ]]; then
  echo 'Refusing origin configured as a mirror; this helper pushes only one ref.' >&2
  exit 1
fi
echo '[git-push] Enabling local validation hook.'
python3 "$ROOT/tools/git/install-hooks.py"
echo "[git-push] Pushing only $REF to origin; all local checks run before upload."
git -C "$ROOT" push --no-follow-tags --recurse-submodules=no origin "$REF:$REF"
echo "[git-push] Complete: $REF"
