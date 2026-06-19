#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: ./scripts/build-all.sh [maven-args...]

Builds every Maven module and runs the full test suite.

Default command:
  mvn clean verify

Examples:
  ./scripts/build-all.sh
  ./scripts/build-all.sh -Dspring.profiles.active=test
  ./scripts/build-all.sh -Dtest='!*IntegrationTest'
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_command mvn

cd "${ROOT_DIR}"

cmd=(mvn clean verify "$@")

echo "Building all modules and running tests..."
printf 'Command:'
printf ' %q' "${cmd[@]}"
printf '\n'

"${cmd[@]}"
