#!/usr/bin/env bash
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
MODE=""
TAG=""
BUMP=""
FORCE_PUSH=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-push) FORCE_PUSH=true; shift ;;
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
Usage: tools/git/git-push.sh MODE [--force-push]
Modes: --master | --branch | --tag TAG | --tag-major | --tag-minor | --tag-patch

--master, --branch  Push only the current main/master branch to origin.
--tag TAG           Push only an existing release tag at HEAD to origin.
                   The tag must match the pom.xml version.

--tag-major        Bump major, commit, tag, and push branch plus new tag.
--tag-minor        Bump minor, commit, tag, and push branch plus new tag.
--tag-patch        Bump patch, commit, tag, and push branch plus new tag.

--force-push       Allow a release without new commits, or recheck a no-op push.
                  Never overwrites remote history or bypasses a dirty checkout.

Requires committed changes and installs the repository-local pre-push hook.
Already-published refs skip checks. Behind/diverged branches fail early.
The hook runs all local checks before an actual push and blocks failures.
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
GIT_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel)"
[[ "$(cd -- "$GIT_ROOT" && pwd -P)" == "$ROOT" ]] || {
  echo 'The push helper must belong to the checkout root.' >&2
  exit 1
}
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || { echo 'Commit or stash changes before pushing.' >&2; exit 1; }
if [[ "$MODE" == release ]]; then
  echo "[git-push] Starting $BUMP release bump with local validation."
  RELEASE_ARGS=("--push-$BUMP")
  if [[ "$FORCE_PUSH" == true ]]; then
    RELEASE_ARGS+=(--force-push)
  fi
  exec bash "$ROOT/tools/git/push-release.sh" "${RELEASE_ARGS[@]}"
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
echo "[git-push] Checking whether origin needs $REF."
REMOTE_REF="$(git -C "$ROOT" ls-remote --refs origin "$REF")"
LOCAL_OID="$(git -C "$ROOT" rev-parse "$REF")"
if [[ -n "$REMOTE_REF" ]]; then
  REMOTE_OID="${REMOTE_REF%%$'\t'*}"
  if [[ "$REMOTE_OID" == "$LOCAL_OID" ]]; then
    echo "[git-push] Nothing to push: origin already has $REF. Checks skipped."
    if [[ "$FORCE_PUSH" == false ]]; then
      echo 'Use --force-push to run local checks again without rewriting history.'
      exit 0
    fi
    python3 "$ROOT/tools/development/pre-push-checks.py"
    echo '[git-push] Checks passed; origin is already current. No upload needed.'
    exit 0
  fi
  if [[ "$MODE" == tag ]]; then
    echo 'Remote tag differs; refusing to overwrite it. Checks skipped.' >&2
    exit 1
  fi
  git -C "$ROOT" fetch --no-tags origin "$REF"
  if ! git -C "$ROOT" merge-base --is-ancestor FETCH_HEAD "$REF"; then
    echo 'Branch is behind or diverged from origin; reconcile it first. Checks skipped.' >&2
    exit 1
  fi
fi
echo '[git-push] Enabling local validation hook.'
python3 "$ROOT/tools/git/install-hooks.py"
echo "[git-push] Pushing only $REF to origin; all local checks run before upload."
git -C "$ROOT" push --no-follow-tags --recurse-submodules=no origin "$REF:$REF"
echo "[git-push] Complete: $REF"
