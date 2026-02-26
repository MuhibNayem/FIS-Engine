import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    sustained: {
      executor: 'constant-arrival-rate',
      rate: 10000,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 1000,
      maxVUs: 5000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const tenantId = __ENV.TENANT_ID;
const token = __ENV.JWT_TOKEN;

export default function () {
  const eventId = `ing-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    eventId,
    eventType: 'LOAD_EVENT',
    occurredAt: new Date().toISOString(),
    postedDate: '2026-02-25',
    transactionCurrency: 'USD',
    createdBy: 'k6',
    lines: [
      { accountCode: 'CASH', amountCents: 100, isCredit: false },
      { accountCode: 'REV', amountCents: 100, isCredit: true }
    ]
  });

  const res = http.post(`${baseUrl}/v1/events`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId,
      'X-Source-System': 'K6',
      Authorization: `Bearer ${token}`,
    },
  });

  check(res, { accepted: (r) => r.status === 202 || r.status === 409 });
}
