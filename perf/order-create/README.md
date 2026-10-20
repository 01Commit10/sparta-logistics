# P02 주문 생성 커넥션 풀 부하 테스트

order-ms 주문 생성(`POST /api/v1/orders`)을 블랙프라이데이 모양 부하로 측정합니다. 트랜잭션 분리 전/후 × Hikari 풀 10/20, 시나리오 A(생성만)·B(생성 + 주문 조회)입니다. 5·6단계는 delivery-ms 배송 생성도 트랜잭션을 나눈 버전입니다. 결과와 분석은 [주문 생성 커넥션 풀 부하 테스트 레포트](<../../docs/report/주문 생성 커넥션 풀 부하 테스트 레포트.md>)에 있습니다.

## 준비

- Docker(postgres·redis), eureka·gateway가 떠 있어야 합니다. 나머지 서비스는 `boot`가 지표 설정을 넣어 다시 띄웁니다.
- macOS는 임시 포트가 부족해 초당 600건부터 연결이 실패합니다. 측정 전에 아래를 실행합니다(재부팅하면 원래대로).
  ```bash
  sudo sysctl -w net.inet.ip.portrange.first=10000 net.inet.tcp.msl=1000
  ```
- k6 2.3.0 이상

## 실행 (저장소 루트에서)

```bash
python3 perf/order-create/run.py seed                       # 업체 2, 상품 50(재고 각 1천만), 조회용 주문 1만
sh ./gradlew :order-ms:bootJar && python3 perf/order-create/run.py jar before   # 측정할 order-ms jar 보관 (before/after)
python3 perf/order-create/run.py boot --jar before --pool 10 # 서비스 재기동 (관리 포트 2909x, -Dhttp.maxConnections=200)
python3 perf/order-create/run.py probe --rates 450,600,800   # 일정 부하 30~40초로 고갈 지점 찾기
python3 perf/order-create/run.py run --id 1A --scenario A --peak 800 --base 200
python3 perf/order-create/run.py run --id 1B --scenario B --peak 800 --base 200 --read-rate 200
python3 perf/order-create/run.py boot --jar before --pool 20 --order-only        # order-ms만 바꿔 띄움
python3 perf/order-create/run.py jar after --module delivery-ms                  # delivery-ms jar 보관 (before/after)
python3 perf/order-create/run.py boot --jar after --pool 10 --delivery after      # 5단계
python3 perf/order-create/run.py cleanup                     # 만든 업체·상품·주문·배송·재고 삭제
```

- 부하: 평소 BASE 30초 → 10초 만에 PEAK → 60초 유지 → 10초 만에 BASE → 30초 (k6 도착률 기반, 가상 사용자 상한 3,000)
- 측정 중 1초마다 서비스별 Hikari(active·idle·pending·timeout·점유·대여 대기), Tomcat 사용 중 스레드, `pg_stat_activity`를 모읍니다.

## 결과 파일 (`results/`)

| 파일 | 내용 |
|---|---|
| `records.json` | 측정별 요약 (응답 코드, p50·p95·p99, 서비스별 풀 최대값·고갈 초) |
| `{id}-pool.csv` | 1초 단위 서비스별 풀·스레드·DB 연결 |
| `{id}-timeseries.csv` | 1초 단위 시나리오별 요청 수·실패·p50·p95·p99 |
| `{id}-k6.json` | k6 요약 |

`probe-1-600`·`probe-1-800`·`probe-1-1000`은 포트 설정 전 측정이라 연결 실패가 섞여 있습니다(레포트 1장). 비교에는 `probe-2-*`를 씁니다.
