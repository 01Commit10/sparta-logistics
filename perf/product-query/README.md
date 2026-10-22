# 상품 조회 최적화 실험 (P01)

인기 상품 TOP 10 · 상품 상세 · 이름 검색을 **인덱스 X → 인덱스 O → Redis 캐시 → Caffeine + Redis 레이어드 캐시** 4단계로 바꿔 가며 k6로 잽니다. 결과 해설은 [상품 조회 최적화 레포트](<../../docs/report/상품 조회 최적화 레포트.md>)에 있습니다.

## 준비

- `docker compose up -d postgres redis` (다른 서비스는 필요 없음. company-ms는 스크립트가 직접 띄움)
- `brew install k6`
- `sh ./gradlew :company-ms:bootJar`

## 실행 (저장소 루트에서)

```bash
python3 perf/product-query/run.py seed      # 업체 100 + 상품 100만 (약 10초, 다시 해도 중복 없음)
python3 perf/product-query/run.py run       # S1~S4 × top·detail·search, VU 50, 워밍업 10초 + 측정 30초 (약 10분)
python3 perf/product-query/run.py run --stages S3,S4 --apis detail --vus 100 --duration 60s
python3 perf/product-query/run.py run --stages S4,S5 --vus 50,200 --results results/p03   # P03 재측정 (기존 결과는 그대로)
python3 perf/product-query/run.py cleanup   # 실험 데이터·인덱스·캐시 키 지우기
```

| 단계 | 인덱스 (`sql/index-*.sql`) | 캐시 (`PRODUCT_CACHE_MODE`) | 상세 조회수 |
|---|---|---|---|
| S1 | X | none | DB UPDATE |
| S2 | O | none | DB UPDATE |
| S3 | O | redis | Redis 누적 + 5초마다 DB 반영 |
| S4 | O | layered | Redis 누적 + 5초마다 DB 반영 |
| S5 | O | hot (P03) | Redis 누적(+ 순위 ZSET) + 5초마다 DB 반영 |

- 단계마다 company-ms를 다시 띄우고, 실험 캐시 키(`company-ms::product-*`)만 지운 뒤 잽니다.
- 측정 전후 `pg_stat_user_tables`(p_products)와 Redis `INFO stats`의 차이를 함께 남깁니다.

## 결과 파일 (`results/`)

| 파일 | 내용 |
|---|---|
| `summary.md` | API별 단계 비교 표 (req/s, 평균·p50·p95·p99, 실패율, DB 스캔·UPDATE, Redis hit/miss) |
| `records.json` | 위 표의 원본 |
| `S*-{api}.json` | k6 요약 원본 |
| `S*-explain.txt` | 단계별 실행 계획 (`sql/explain.sql`, 검색어 "고등어") |
| `S*-company-ms.log` | 단계별 서버 로그 |

## 파일

| 파일 | 내용 |
|---|---|
| `sql/seed.sql` | 실험 데이터. 업체 ID `c0000000-0000-4000-8000-000000000001~100` |
| `sql/index-on.sql` / `index-off.sql` | `(view_count DESC, id) WHERE deleted_at IS NULL`, `pg_trgm` GIN `lower(name)` |
| `sql/explain.sql` | 앱과 같은 모양의 쿼리 실행 계획 |
| `k6/product-query.js` | `-e API=top|detail|search`. 상세·검색은 인기 쏠림(앞쪽이 자주) |
| `run.py` | 단계 전환·측정·요약 |

주의: 앱·DB·k6가 모두 한 노트북에서 돌아 절대값보다 **단계 간 비율**을 봐야 합니다.
