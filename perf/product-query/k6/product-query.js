// P01 상품 조회 부하 테스트. company-ms(19091)에 직접 보내고 로그인은 X-User-* 헤더로 대신한다.
//   k6 run -e API=top|detail|search -e VUS=50 -e DURATION=30s perf/product-query/k6/product-query.js
// detail은 data/ids.json(조회수 상위 1만 상품 ID)이 필요하다 (run.py가 만든다)
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE || 'http://localhost:19091';
const API = __ENV.API || 'top';
const HEADERS = {
    headers: {
        'X-User-Id': '9b2f0a3e-5c1d-4e8a-9f00-000000000001',
        'X-User-Role': 'USER',
    },
    tags: { api: API },
};

// 품목 40종 (seed.sql과 같음). 앞쪽 품목일수록 자주 검색하게 치우친다
const KEYWORDS = ['고등어', '사과', '배', '감귤', '한우', '돼지고기', '닭가슴살', '쌀', '현미', '생수', '우유', '두부',
    '계란', '김치', '고추장', '된장', '참기름', '라면', '커피', '녹차', '오렌지주스', '바나나', '딸기', '포도', '수박',
    '양파', '감자', '고구마', '당근', '배추', '버섯', '새우', '오징어', '멸치', '김', '미역', '햄', '치즈', '요거트',
    '아이스크림'];

const IDS = API === 'detail' ? new SharedArray('ids', () => JSON.parse(open('../data/ids.json'))) : [];

export const options = {
    vus: Number(__ENV.VUS || 50),
    duration: __ENV.DURATION || '30s',
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: { http_req_failed: ['rate<0.01'] },
};

// 0에 가까운 쪽이 훨씬 자주 나오는 0..n-1 (인기 쏠림)
function skewed(n) {
    return Math.floor(Math.pow(Math.random(), 3) * n);
}

export default function () {
    let url;
    if (API === 'top') {
        url = `${BASE}/api/v1/products?sort=viewCount,desc&size=10`;
    } else if (API === 'detail') {
        url = `${BASE}/api/v1/products/${IDS[skewed(IDS.length)]}`;
    } else {
        url = `${BASE}/api/v1/products?name=${encodeURIComponent(KEYWORDS[skewed(KEYWORDS.length)])}&size=10`;
    }
    const res = http.get(url, HEADERS);
    check(res, { 'status 200': (r) => r.status === 200 });
}
