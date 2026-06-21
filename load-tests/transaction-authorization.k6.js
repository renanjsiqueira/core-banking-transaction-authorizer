import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const accountIds = (__ENV.ACCOUNT_IDS || '')
  .split(',')
  .map((id) => id.trim())
  .filter((id) => id.length > 0);

const creditRatio = Number(__ENV.CREDIT_RATIO || '0.7');
const hotAccountRatio = Number(__ENV.HOT_ACCOUNT_RATIO || '0.2');
const amountMin = Number(__ENV.AMOUNT_MIN || '1');
const amountMax = Number(__ENV.AMOUNT_MAX || '20');
const sleepMs = Number(__ENV.SLEEP_MS || '0');

const lockTimeouts = new Counter('authorization_lock_timeouts');
const businessFailures = new Counter('authorization_business_failures');
const unexpectedStatuses = new Counter('authorization_unexpected_statuses');
const authorizationOk = new Rate('authorization_ok');

export const options = {
  scenarios: {
    authorization_load: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || '20'),
      duration: __ENV.DURATION || '2m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    authorization_ok: ['rate>0.95'],
  },
};

export function setup() {
  if (accountIds.length === 0) {
    throw new Error('ACCOUNT_IDS is required. Use scripts/run-load-test.sh or pass ACCOUNT_IDS=id1,id2.');
  }

  const health = http.get(`${baseUrl}/actuator/health`, {
    tags: { endpoint: 'health' },
  });
  if (health.status !== 200) {
    throw new Error(`API health check failed: status=${health.status}`);
  }
}

export default function () {
  const accountId = pickAccountId();
  const transactionId = uuidv4();
  const type = Math.random() < creditRatio ? 'CREDIT' : 'DEBIT';
  const amount = randomAmount();

  const payload = JSON.stringify({
    accountId,
    type,
    amount: {
      value: amount,
      currency: 'BRL',
    },
  });

  const response = http.post(`${baseUrl}/transactions/${transactionId}`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': `k6-${transactionId}`,
    },
    tags: {
      endpoint: 'authorize_transaction',
      transaction_type: type,
    },
  });

  const acceptedStatus = response.status === 200;
  const lockedStatus = response.status === 409;
  const ok = acceptedStatus || lockedStatus;

  authorizationOk.add(ok);
  lockTimeouts.add(lockedStatus ? 1 : 0);
  unexpectedStatuses.add(ok ? 0 : 1);

  check(response, {
    'status is 200 or lock conflict': () => ok,
    '2xx body has transaction payload': () => !acceptedStatus || hasTransactionPayload(response),
  });

  if (acceptedStatus && transactionFailedByBusinessRule(response)) {
    businessFailures.add(1);
  }

  if (sleepMs > 0) {
    sleep(sleepMs / 1000);
  }
}

export function handleSummary(data) {
  const summaryPath = __ENV.K6_SUMMARY_JSON || 'load-tests/results/transaction-authorization-summary.json';
  const duration = data.metrics.http_req_duration?.values || {};
  const requests = data.metrics.http_reqs?.values || {};
  const failed = data.metrics.http_req_failed?.values || {};
  const locks = data.metrics.authorization_lock_timeouts?.values || {};
  const business = data.metrics.authorization_business_failures?.values || {};
  const unexpected = data.metrics.authorization_unexpected_statuses?.values || {};

  const text = [
    '',
    'Core Banking authorization load test summary',
    `Requests: ${formatNumber(requests.count)} (${formatNumber(requests.rate)} req/s)`,
    `Latency: p95=${formatNumber(duration['p(95)'])}ms p99=${formatNumber(duration['p(99)'])}ms avg=${formatNumber(duration.avg)}ms`,
    `HTTP failed rate: ${formatPercent(failed.rate)}`,
    `Lock timeouts (HTTP 409): ${formatNumber(locks.count)}`,
    `Business-rule failed transactions: ${formatNumber(business.count)}`,
    `Unexpected statuses: ${formatNumber(unexpected.count)}`,
    `Full JSON summary: ${summaryPath}`,
    '',
  ].join('\n');

  return {
    stdout: text,
    [summaryPath]: JSON.stringify(data, null, 2),
  };
}

function pickAccountId() {
  if (accountIds.length === 1 || Math.random() < hotAccountRatio) {
    return accountIds[0];
  }
  return accountIds[Math.floor(Math.random() * accountIds.length)];
}

function randomAmount() {
  const value = amountMin + Math.random() * (amountMax - amountMin);
  return Number(value.toFixed(2));
}

function hasTransactionPayload(response) {
  try {
    const body = response.json();
    return body.transaction && body.transaction.id && body.transaction.status && body.account && body.account.id;
  } catch (ignored) {
    return false;
  }
}

function transactionFailedByBusinessRule(response) {
  try {
    return response.json('transaction.status') === 'FAILED';
  } catch (ignored) {
    return false;
  }
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16);
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function formatNumber(value) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '0';
  }
  return Number(value).toFixed(2);
}

function formatPercent(value) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '0.00%';
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}
