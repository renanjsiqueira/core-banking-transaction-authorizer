#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:-20}"
DURATION="${DURATION:-2m}"
ACCOUNT_LIMIT="${ACCOUNT_LIMIT:-50}"
CREDIT_RATIO="${CREDIT_RATIO:-0.7}"
HOT_ACCOUNT_RATIO="${HOT_ACCOUNT_RATIO:-0.2}"
SLEEP_MS="${SLEEP_MS:-0}"
ACCOUNT_IDS="${ACCOUNT_IDS:-}"

usage() {
  cat <<'EOF'
Usage: ./scripts/run-load-test.sh [options]

Runs a local k6 load test against the transaction authorization API.

Options:
  --base-url URL          API base URL. Default: http://localhost:8080
  --vus N                Number of virtual users. Default: 20
  --duration DURATION    k6 duration, e.g. 30s, 2m, 10m. Default: 2m
  --account-limit N      Number of enabled accounts to read from local Postgres. Default: 50
  --account-ids IDS      Comma-separated account ids. Skips Docker/Postgres lookup.
  --credit-ratio N       CREDIT probability from 0.0 to 1.0. Default: 0.7
  --hot-account-ratio N  Probability of targeting the first account id. Default: 0.2
  --sleep-ms N           Sleep between iterations per VU. Default: 0
  -h, --help             Show this help.

Examples:
  ./scripts/run-load-test.sh
  VUS=50 DURATION=5m ./scripts/run-load-test.sh
  ./scripts/run-load-test.sh --hot-account-ratio 1.0 --vus 30 --duration 1m
  ./scripts/run-load-test.sh --account-ids id1,id2,id3
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --vus)
      VUS="$2"
      shift 2
      ;;
    --duration)
      DURATION="$2"
      shift 2
      ;;
    --account-limit)
      ACCOUNT_LIMIT="$2"
      shift 2
      ;;
    --account-ids)
      ACCOUNT_IDS="$2"
      shift 2
      ;;
    --credit-ratio)
      CREDIT_RATIO="$2"
      shift 2
      ;;
    --hot-account-ratio)
      HOT_ACCOUNT_RATIO="$2"
      shift 2
      ;;
    --sleep-ms)
      SLEEP_MS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_command k6
require_command curl

if [[ -z "${ACCOUNT_IDS}" ]]; then
  require_command docker

  echo "Reading up to ${ACCOUNT_LIMIT} enabled accounts from core-banking-postgres..."
  account_query="select id from accounts where status = 'ENABLED' order by id limit ${ACCOUNT_LIMIT};"
  account_lines="$(docker exec core-banking-postgres \
    psql -U app -d transaction_authorization -tAc "${account_query}" || true)"

  ACCOUNT_IDS="$(printf '%s\n' "${account_lines}" \
    | sed '/^[[:space:]]*$/d' \
    | paste -sd, -)"
fi

if [[ -z "${ACCOUNT_IDS}" ]]; then
  echo "No account ids found. Start the stack and wait for the listener to import accounts, or pass --account-ids." >&2
  exit 1
fi

echo "Checking API health at ${BASE_URL}/actuator/health..."
curl -fsS "${BASE_URL}/actuator/health" >/dev/null

mkdir -p "${ROOT_DIR}/load-tests/results"
cd "${ROOT_DIR}"

echo "Running k6 authorization load test..."
echo "BASE_URL=${BASE_URL}"
echo "VUS=${VUS}"
echo "DURATION=${DURATION}"
echo "ACCOUNT_COUNT=$(awk -F',' '{print NF}' <<< "${ACCOUNT_IDS}")"
echo "CREDIT_RATIO=${CREDIT_RATIO}"
echo "HOT_ACCOUNT_RATIO=${HOT_ACCOUNT_RATIO}"

BASE_URL="${BASE_URL}" \
ACCOUNT_IDS="${ACCOUNT_IDS}" \
VUS="${VUS}" \
DURATION="${DURATION}" \
CREDIT_RATIO="${CREDIT_RATIO}" \
HOT_ACCOUNT_RATIO="${HOT_ACCOUNT_RATIO}" \
SLEEP_MS="${SLEEP_MS}" \
k6 run "load-tests/transaction-authorization.k6.js"
