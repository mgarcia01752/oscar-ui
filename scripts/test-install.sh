#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
unset JAVA_HOME

uname() {
  case "$1" in
    -s) printf '%s\n' Linux ;;
    -m) printf '%s\n' "${TEST_ARCH:-x86_64}" ;;
  esac
}
dpkg-query() {
  if [[ "${*: -1}" != "${TEST_MISSING:-}" ]]; then
    printf '%s' 'install ok installed'
  else
    return 1
  fi
}
apt-get() { printf '%s\n' 'Unexpected package installation' >&2; return 99; }
java() { printf 'openjdk version "%s.0.1"\n' "${TEST_JAVA:-21}" >&2; }
javac() { printf '%s\n' 'javac 21.0.1'; }
jpackage() { printf '%s\n' '21.0.1'; }
curl() { printf '%s\n' 'Unexpected download' >&2; return 99; }
bash() {
  if [[ "${TEST_STOP_BOOTSTRAP:-}" == 1 ]]; then
    [[ "$1" == */scripts/bootstrap-oscar.sh ]] || return 99
    [[ -d "${1%/scripts/bootstrap-oscar.sh}/development" ]] || return 99
    return 77
  fi
  command bash "$@"
}
export -f uname dpkg-query apt-get java javac jpackage curl bash

expect_failure() {
  local expected="$1"
  shift
  local output
  if output="$(bash "$ROOT/install.sh" "$@" 2>&1)"; then
    printf 'Expected failure: %s\n' "$*" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    printf 'Expected %s, got: %s\n' "$expected" "$output" >&2
    exit 1
  fi
}

output="$(bash "$ROOT/install.sh" --development --check)"
[[ "$output" == *'System prerequisites verified'* ]]
printf '%s\n' 'PASS: valid preflight without downloads'

export TEST_MISSING=libxcb-cursor0
expect_failure 'sudo apt-get install libxcb-cursor0' --development --check
unset TEST_MISSING
printf '%s\n' 'PASS: missing runtime package'

for dependency in git unzip python3-pil dpkg-dev fakeroot xvfb xauth; do
  export TEST_MISSING="$dependency"
  expect_failure "sudo apt-get install $dependency" --development --check
  unset TEST_MISSING
done
printf '%s\n' 'PASS: missing development and packaging dependencies'

export TEST_JAVA=17
expect_failure 'Select a Java 21 JDK' --development --check
unset TEST_JAVA
printf '%s\n' 'PASS: wrong Java version'

export TEST_ARCH=aarch64
expect_failure 'Linux x86_64 only' --development --check
unset TEST_ARCH
printf '%s\n' 'PASS: unsupported architecture'

expect_failure 'Unknown option' --invalid
expect_failure 'Usage:' --check
expect_failure 'Usage:'
bash "$ROOT/install.sh" --help >/dev/null
printf '%s\n' 'PASS: option handling and help'

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT
cp "$ROOT/install.sh" "$TEMP_ROOT/install.sh"
bash "$TEMP_ROOT/install.sh" --development --check >/dev/null
[[ ! -e "$TEMP_ROOT/development" ]]
printf '%s\n' 'PASS: preflight does not create development directory'

export TEST_STOP_BOOTSTRAP=1
status=0
/bin/bash "$TEMP_ROOT/install.sh" --development >/dev/null || status=$?
[[ "$status" == 77 && -d "$TEMP_ROOT/development" ]]
printf '%s\n' 'PASS: setup creates development directory before bootstrap'
