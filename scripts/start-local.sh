#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

MODE="full"
DETACHED="false"
BUILD="true"
CLEAN="false"
SKIP_IMAGE_CHECK="false"
PULL_RETRIES="${START_LOCAL_PULL_RETRIES:-3}"
PULL_RETRY_DELAY_SECONDS="${START_LOCAL_PULL_RETRY_DELAY_SECONDS:-5}"

usage() {
  cat <<'EOF'
Usage: ./scripts/start-local.sh [options]

Starts the local Core Banking stack with Docker Compose.

Options:
  --infra-only      Start only local dependencies: postgres, redis, localstack and message-generator.
  -d, --detached    Run containers in the background.
  --no-build        Do not rebuild application images.
  --clean           Run docker compose down -v --remove-orphans before starting.
  --skip-image-check
                   Skip the sequential Docker image availability check.
  -h, --help        Show this help.

Examples:
  ./scripts/start-local.sh
  ./scripts/start-local.sh --detached
  ./scripts/start-local.sh --infra-only --detached
  ./scripts/start-local.sh --clean
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

pull_image_with_retries() {
  local image="$1"
  local attempt=1
  local sleep_seconds

  if docker image inspect "${image}" >/dev/null 2>&1; then
    echo "Image already available: ${image}"
    return 0
  fi

  while [[ "${attempt}" -le "${PULL_RETRIES}" ]]; do
    echo "Pulling Docker image (${attempt}/${PULL_RETRIES}): ${image}"
    if docker pull "${image}"; then
      return 0
    fi

    if [[ "${attempt}" -eq "${PULL_RETRIES}" ]]; then
      echo "Failed to pull Docker image after ${PULL_RETRIES} attempts: ${image}" >&2
      return 1
    fi

    sleep_seconds=$((attempt * PULL_RETRY_DELAY_SECONDS))
    echo "Pull failed. Retrying in ${sleep_seconds}s..."
    sleep "${sleep_seconds}"
    attempt=$((attempt + 1))
  done
}

ensure_required_images() {
  local images=(
    postgres:16-alpine
    redis:7-alpine
    localstack/localstack:3.7.2
    golang:1.24.3
  )
  local image

  if [[ "${MODE}" == "full" && "${BUILD}" == "true" ]]; then
    images+=(
      maven:3.9-eclipse-temurin-21
      eclipse-temurin:21-jre
    )
  fi

  echo "Checking Docker images..."
  for image in "${images[@]}"; do
    pull_image_with_retries "${image}"
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --infra-only)
      MODE="infra"
      shift
      ;;
    -d|--detached)
      DETACHED="true"
      shift
      ;;
    --no-build)
      BUILD="false"
      shift
      ;;
    --clean)
      CLEAN="true"
      shift
      ;;
    --skip-image-check)
      SKIP_IMAGE_CHECK="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command docker

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required. Check if 'docker compose' is available." >&2
  exit 1
fi

cd "${ROOT_DIR}"

compose=(docker compose -f "${COMPOSE_FILE}")

if [[ "${CLEAN}" == "true" ]]; then
  echo "Cleaning local Docker Compose stack..."
  "${compose[@]}" down -v --remove-orphans
fi

if [[ "${SKIP_IMAGE_CHECK}" == "false" ]]; then
  ensure_required_images
fi

cmd=("${compose[@]}" up)

if [[ "${DETACHED}" == "true" ]]; then
  cmd+=(-d)
fi

if [[ "${BUILD}" == "true" && "${MODE}" == "full" ]]; then
  cmd+=(--build)
fi

if [[ "${MODE}" == "infra" ]]; then
  cmd+=(postgres redis localstack message-generator)
fi

echo "Starting local stack..."
printf 'Command:'
printf ' %q' "${cmd[@]}"
printf '\n'

"${cmd[@]}"

if [[ "${DETACHED}" == "true" ]]; then
  echo
  "${compose[@]}" ps
  echo
  echo "API health:      http://localhost:8080/actuator/health"
  echo "Listener health: http://localhost:8081/actuator/health"
  echo "Swagger:         http://localhost:8080/swagger-ui.html"
fi
