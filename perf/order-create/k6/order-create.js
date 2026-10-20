// P02 주문 생성 부하 테스트. order-ms(19094)에 직접 보내고 로그인은 X-User-* 헤더로 대신한다.
//   k6 run -e SCENARIO=A|B -e PEAK=120 [-e BASE_RATE=30 -e READ_RATE=200] perf/order-create/k6/order-create.js
//   보정용 일정 부하: -e MODE=constant -e PEAK=80 -e DURATION=40s
// A = 주문 생성만, B = 주문 생성 + 주문 단건 조회(같은 커넥션 풀을 쓰는 읽기)
// 매 요청 새 Idempotency-Key. data/world.json·read-ids.json은 run.py seed가 만든다
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE || 'http://localhost:19094';
const SCENARIO = __ENV.SCENARIO || 'A';
const MODE = __ENV.MODE || 'spike';
const PEAK = Number(__ENV.PEAK || 100);
const BASE_RATE = Number(__ENV.BASE_RATE || Math.max(1, Math.round(PEAK / 4)));
const READ_RATE = Number(__ENV.READ_RATE || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 3000);

const WORLD = JSON.parse(open('../data/world.json'));
const READ_IDS = new SharedArray('read-ids', () => JSON.parse(open('../data/read-ids.json')));

const HEADERS = {
    'Content-Type': 'application/json',
    'X-User-Id': WORLD.orderer,
    'X-User-Role': 'USER',
};

// 응답 코드별 개수 (0 = 타임아웃·연결 실패)
const createStatus = {
    201: new Counter('create_201'), 409: new Counter('create_409'), 500: new Counter('create_500'),
    503: new Counter('create_503'), other: new Counter('create_other'), 0: new Counter('create_0'),
};
const readOk = new Counter('read_200');
const readFail = new Counter('read_fail');

// 블랙프라이데이: 평소 30초 → 10초 만에 PEAK → 60초 유지 → 10초 만에 평소 → 회복 30초 (총 2분 20초)
const SPIKE = [
    { target: BASE_RATE, duration: '30s' },
    { target: PEAK, duration: '10s' },
    { target: PEAK, duration: '60s' },
    { target: BASE_RATE, duration: '10s' },
    { target: BASE_RATE, duration: '30s' },
];
const TOTAL = MODE === 'constant' ? (__ENV.DURATION || '40s') : '140s';

const scenarios = {
    create: MODE === 'constant'
        ? { executor: 'constant-arrival-rate', rate: PEAK, timeUnit: '1s', duration: TOTAL,
            preAllocatedVUs: 200, maxVUs: MAX_VUS, exec: 'create', gracefulStop: '40s' }
        : { executor: 'ramping-arrival-rate', startRate: BASE_RATE, timeUnit: '1s', stages: SPIKE,
            preAllocatedVUs: 200, maxVUs: MAX_VUS, exec: 'create', gracefulStop: '40s' },
};
if (SCENARIO === 'B') {
    scenarios.read = { executor: 'constant-arrival-rate', rate: READ_RATE, timeUnit: '1s', duration: TOTAL,
        preAllocatedVUs: 100, maxVUs: MAX_VUS, exec: 'read', gracefulStop: '40s' };
}

// 시나리오별 하위 지표를 요약에 남기려고 항상 통과하는 기준을 건다
const thresholds = {
    'http_req_duration{scenario:create}': ['max>=0'],
    'http_req_failed{scenario:create}': ['rate>=0'],
};
if (SCENARIO === 'B') {
    thresholds['http_req_duration{scenario:read}'] = ['max>=0'];
    thresholds['http_req_failed{scenario:read}'] = ['rate>=0'];
}

export const options = {
    scenarios,
    thresholds,
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    discardResponseBodies: true,
};

export function create() {
    const product = WORLD.products[Math.floor(Math.random() * WORLD.products.length)];
    const body = JSON.stringify({
        requesterCompanyId: WORLD.requester, supplierCompanyId: WORLD.supplier, productId: product,
        quantity: 1, unitPrice: 5000, requestNote: 'P02 부하 테스트',
    });
    const res = http.post(`${BASE}/api/v1/orders`, body, {
        headers: { ...HEADERS, 'Idempotency-Key': crypto.randomUUID() },
        timeout: '60s',
        responseCallback: http.expectedStatuses(201),
    });
    (createStatus[res.status] || createStatus.other).add(1);
    check(res, { 'created 201': (r) => r.status === 201 });
}

export function read() {
    const id = READ_IDS[Math.floor(Math.random() * READ_IDS.length)];
    const res = http.get(`${BASE}/api/v1/orders/${id}`, { headers: HEADERS, timeout: '60s' });
    (res.status === 200 ? readOk : readFail).add(1);
}

export function handleSummary(data) {
    return __ENV.SUMMARY ? { [__ENV.SUMMARY]: JSON.stringify(data) } : {};
}
