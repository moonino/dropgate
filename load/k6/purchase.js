import http from 'k6/http';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const VIRTUAL_USERS = 500;
const MAX_DURATION = '5m';
const CONFIRMED_STATUS = 201;
const CONFLICT_STATUS = 409;
const SOLD_OUT_CODE = 'SOLD_OUT';
const ALREADY_PURCHASED_CODE = 'ALREADY_PURCHASED';

const baseUrl = __ENV.DROPGATE_LOAD_BASE_URL;
const dropId = __ENV.DROPGATE_LOAD_DROP_ID;
const requestTokens = new SharedArray('purchase request tokens', () =>
  JSON.parse(open(__ENV.DROPGATE_LOAD_REQUEST_TOKENS_FILE)),
);

const confirmedPurchases = new Counter('purchase_confirmed');
const soldOutRejections = new Counter('purchase_sold_out');
const alreadyPurchasedRejections = new Counter('purchase_already_purchased');
const unexpectedResponses = new Counter('purchase_unexpected');

export const options = {
  scenarios: {
    purchase: {
      executor: 'shared-iterations',
      vus: VIRTUAL_USERS,
      iterations: requestTokens.length,
      maxDuration: MAX_DURATION,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: {
    'http_req_duration{status:201}': ['max>=0'],
    'http_req_duration{status:409}': ['max>=0'],
    purchase_unexpected: ['count==0'],
  },
};

const readErrorCode = (response) => {
  try {
    return response.json('code');
  } catch (error) {
    return 'UNREADABLE_BODY';
  }
};

const recordOutcome = (response) => {
  if (response.status === CONFIRMED_STATUS) {
    confirmedPurchases.add(1);
    return;
  }
  const code = readErrorCode(response);
  if (response.status === CONFLICT_STATUS) {
    if (code === SOLD_OUT_CODE) {
      soldOutRejections.add(1);
      return;
    }
    if (code === ALREADY_PURCHASED_CODE) {
      alreadyPurchasedRejections.add(1);
      return;
    }
  }
  unexpectedResponses.add(1, { status: String(response.status), code: String(code) });
};

export default function () {
  const token = requestTokens[exec.scenario.iterationInTest];
  const response = http.post(`${baseUrl}/api/drops/${dropId}/orders`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
  recordOutcome(response);
}
