# P01 측정 결과 (VU 50, 30s, 워밍업 10s)

## 인기 상품 TOP 10

| 단계 | req/s | 평균 ms | p50 ms | p95 ms | p99 ms | 실패 | DB seq scan | DB 읽은 행(seq) | DB idx scan | DB UPDATE | Redis hit | Redis miss |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4 인덱스 O + Caffeine·Redis 레이어드 캐시 | 95656 | 0.5 | 0.5 | 0.8 | 1.3 | 0.00% | 22 | 19,020,316 | 22 | 0 | 150 | 0 |
| S5 인덱스 O + hot (TOP N L1 스냅샷 · 순위 ZSET · single-flight) | 96663 | 0.5 | 0.4 | 0.8 | 1.2 | 0.00% | 0 | 0 | 0 | 0 | 66 | 0 |

## 상품 상세 (+조회수)

| 단계 | req/s | 평균 ms | p50 ms | p95 ms | p99 ms | 실패 | DB seq scan | DB 읽은 행(seq) | DB idx scan | DB UPDATE | Redis hit | Redis miss |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4 인덱스 O + Caffeine·Redis 레이어드 캐시 | 33865 | 1.4 | 1.4 | 2.1 | 3.0 | 0.00% | 0 | 0 | 20,039 | 19,784 | 1,066,527 | 0 |
| S5 인덱스 O + hot (TOP N L1 스냅샷 · 순위 ZSET · single-flight) | 24817 | 2.0 | 2.0 | 2.6 | 3.4 | 0.00% | 0 | 0 | 25,045 | 24,555 | 1,439,430 | 9 |

## 이름 검색

| 단계 | req/s | 평균 ms | p50 ms | p95 ms | p99 ms | 실패 | DB seq scan | DB 읽은 행(seq) | DB idx scan | DB UPDATE | Redis hit | Redis miss |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| S4 인덱스 O + Caffeine·Redis 레이어드 캐시 | 50356 | 0.9 | 0.5 | 1.1 | 1.7 | 0.00% | 0 | 0 | 4,423 | 3,949 | 2,985 | 214 |
| S5 인덱스 O + hot (TOP N L1 스냅샷 · 순위 ZSET · single-flight) | 34214 | 1.4 | 1.3 | 1.9 | 2.7 | 0.00% | 137 | 50,108,434 | 3,007 | 2,883 | 1,027,754 | 587 |
