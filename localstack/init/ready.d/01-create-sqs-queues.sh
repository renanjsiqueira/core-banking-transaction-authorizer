#!/usr/bin/env bash
set -euo pipefail

QUEUE_NAME="${QUEUE_NAME:-conta-bancaria-criada}"
DLQ_NAME="${DLQ_NAME:-${QUEUE_NAME}-dlq}"
MAX_RECEIVE_COUNT="${SQS_MAX_RECEIVE_COUNT:-5}"
VISIBILITY_TIMEOUT="${SQS_VISIBILITY_TIMEOUT:-30}"
RECEIVE_WAIT_TIME_SECONDS="${SQS_RECEIVE_WAIT_TIME_SECONDS:-10}"

echo "[localstack:init] Creating SQS DLQ: ${DLQ_NAME}"
DLQ_URL="$(awslocal sqs create-queue \
  --queue-name "${DLQ_NAME}" \
  --query 'QueueUrl' \
  --output text)"

DLQ_ARN="$(awslocal sqs get-queue-attributes \
  --queue-url "${DLQ_URL}" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)"

echo "[localstack:init] Creating SQS main queue: ${QUEUE_NAME}"
MAIN_QUEUE_URL="$(awslocal sqs create-queue \
  --queue-name "${QUEUE_NAME}" \
  --query 'QueueUrl' \
  --output text)"

ATTRIBUTES="$(printf '{"VisibilityTimeout":"%s","ReceiveMessageWaitTimeSeconds":"%s","RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"%s\\"}"}' \
  "${VISIBILITY_TIMEOUT}" \
  "${RECEIVE_WAIT_TIME_SECONDS}" \
  "${DLQ_ARN}" \
  "${MAX_RECEIVE_COUNT}")"

awslocal sqs set-queue-attributes \
  --queue-url "${MAIN_QUEUE_URL}" \
  --attributes "${ATTRIBUTES}"

echo "[localstack:init] SQS ready: queue=${MAIN_QUEUE_URL}, dlq=${DLQ_URL}, maxReceiveCount=${MAX_RECEIVE_COUNT}"
