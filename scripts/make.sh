#!/usr/bin/env bash
set -exuo pipefail

# Run from repo root regardless of where the script is invoked.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODULE_DIRS=(ih-vdn ih-audit)

SETTINGS_ARGS=()
if [ -f "$ROOT_DIR/settings.xml" ]; then
  SETTINGS_ARGS=(-s "$ROOT_DIR/settings.xml")
fi

build() {
  local module
  for module in "${MODULE_DIRS[@]}"; do
    mvn -f "$module/pom.xml" "${SETTINGS_ARGS[@]}" -U -DskipTests clean verify
  done
}

# Runs the test suites only; services the tests depend on must already be up
# (make-test.sh handles that orchestration and then calls this target).
test_modules() {
  local module
  for module in "${MODULE_DIRS[@]}"; do
    mvn -f "$module/pom.xml" "${SETTINGS_ARGS[@]}" test
  done
}

target="${1:-build}"
case "$target" in
  build) build ;;
  test) test_modules ;;
  *)
    echo "Unknown target: $target" >&2
    echo "Usage: $0 [build|test]" >&2
    exit 1
    ;;
esac
