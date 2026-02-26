import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 200,
  duration: '10m',
  thresholds: {
    http_req_duration: ['p(99)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const tenantId = __ENV.TENANT_ID;
const token = __ENV.JWT_TOKEN;

export default function () {
  const eventId = `k6-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    eventId,
    postedDate: '2026-02-25',
    transactionCurrency: 'USD',
    createdBy: 'k6',
    lines: [
      { accountCode: 'CASH', amountCents: 100, isCredit: false },
      { accountCode: 'REV', amountCents: 100, isCredit: true }
    ]
  });

  const res = http.post(`${baseUrl}/v1/journal-entries`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId,
      Authorization: `Bearer ${token}`,
    },
  });

  check(res, { 'created': (r) => r.status === 201 || r.status === 409 });
  sleep(0.01);
}
