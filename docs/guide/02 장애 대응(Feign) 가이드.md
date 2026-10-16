# 02. 장애 대응(Feign) 가이드

> 작성자: 대진 · 기준: dev `d6af869` (GW-09·10, NTF 머지) · 최종 수정: 2026-10-16
> 대상: 다울(order-ms, Saga), 이찬(hub-ms), 영은(delivery-ms), 권우(company-ms)
> 원칙: **코드가 기준**입니다. 설계 문서([[SYS-13 장애 대응 설계]])와 다르면 이 문서와 코드를 따라 주세요.

## 0. 요약

1. 서비스 간 호출은 `@FeignClient(name = "hub-ms")` + common의 `RemoteCallTemplate`으로 감싸면 타임아웃·Circuit Breaker·오류 분류가 자동으로 붙습니다.
   - **쉽게 말해:** 다른 서비스에 "전화"를 걸 때 통화 시간 제한, 계속 안 받는 번호 잠시 차단, 실패 이유 분류가 저절로 붙는다고 생각하면 됩니다. 직접 시간을 재거나 try/catch로 HTTP 상태를 나눌 필요가 없습니다.
2. 실패는 **3가지 예외**로만 옵니다: `RemoteBusinessException`(4xx, 확실히 실패) / `RemoteServiceException`(처리 안 됨, 503) / `RemoteTimeoutException`(**결과 모름**, 504).
   - **쉽게 말해:** 
     - `RemoteBusinessException`: 상대가 "안 돼요"라고 분명히 답했습니다(예: 재고 부족). 처리가 안 된 게 확실합니다.
     - `RemoteServiceException`: 상대가 전화를 아예 못 받았습니다(서버 다운, 차단 중). 이것도 처리가 안 된 게 확실합니다.
     - `RemoteTimeoutException`: 응답을 기다리다 끊었습니다. 상대가 **처리했는지 모릅니다.** 재고가 이미 차감됐을 수도 있으니 되돌리기(보상)나 재확인이 필요한, 가장 조심해야 하는 경우입니다.
3. 타임아웃은 연결 1초·응답 2초, CB는 **Feign 메서드 단위**, 4xx는 CB 실패로 세지 않습니다. 값은 `application-common.yaml` 한 곳에 있습니다.
   - **쉽게 말해:** Circuit Breaker(CB)는 누전 차단기입니다. 같은 호출이 계속 실패하면 잠시 호출 자체를 막아 장애가 우리 서비스까지 번지지 않게 합니다. 메서드 단위라서 "재고 차감"이 고장 나도 "허브 조회"는 계속 됩니다. 4xx는 상대 서버가 멀쩡히 "안 된다"고 답한 것이라 고장으로 세지 않습니다.
4. **자동 재시도는 없습니다.** 멱등성 키가 있는 호출만 `executeWithRetryOnce`로 직접 1회 재시도합니다.
   - **쉽게 말해:** 무작정 다시 보내면 같은 요청이 두 번 처리될 수 있습니다(재고 두 번 차감). "같은 키로 온 요청은 한 번만 처리한다"는 보장(멱등성 키)이 있는 호출만 다시 보내도 안전합니다.
5. Feign은 우리 서비스끼리만 씁니다. `url`을 지정하면 기동이 실패합니다. 외부 API(Slack 등)는 `RestClient`입니다.
   - **쉽게 말해:** Feign은 Eureka에 등록된 **서비스 이름**으로 우리 서비스를 찾아갑니다. 주소(url)를 직접 적으면 이 구조를 우회하고, 사용자 헤더 같은 내부 정보가 외부로 나갈 수도 있어서 아예 막아 두었습니다.

## 1. 상태

| 항목 | 상태 | 위치 |
|---|---|---|
| Feign 타임아웃·CB·오류 분류·예외 3종·`RemoteCallTemplate` | ✅ 사용 가능 (C02 dev 머지) | `common/.../com/common/remote/**`, `application-common.yaml` |
| `FeignClientUrlGuard` (url 지정 시 기동 실패) | ✅ 사용 가능 | `com.common.remote.feign` |
| 사용자 헤더 자동 전달 (`X-User-Id`·`X-User-Role`·`X-Request-Id`) | ✅ 사용 가능 (C01) | `UserHeaderRelayInterceptor` |
| CB 상태 확인 (`/actuator/circuitbreakers`) | ✅ 사용 가능 | `application-common.yaml` `management.endpoints` |
| Gateway 라우트 타임아웃 (연결 2초, 응답 5초, 주문 15초) | ✅ 사용 가능 (GW-09, PR #24) | `gateway-edge/src/main/resources/application.yaml` |
| Gateway 서비스별 CB (OPEN이면 503) | ✅ 사용 가능 (GW-10, PR #24) | 같은 파일. 연결 실패·타임아웃만 실패로 셈, fallbackUri 없음 |
| Slack 알림 (`RestClient`, 실패 무시) | ✅ common 사용 가능 (NTF, PR #25) | [[04 Slack 알림 가이드]] |
| 서비스 코드에서 Feign 사용 | ❌ 아직 없음 | 각 서비스 `build.gradle`에 의존성부터 추가해야 합니다 (3장) |

## 2. 전체 그림

```
Client ─①─▶ Gateway ─①─▶ order-ms ─②─▶ company-ms / hub-ms / delivery-ms ─②─▶ hub-ms / user-ms
               │                     └─④─▶ Slack Webhook (RestClient)
               └─③─▶ Redis (토큰 상태)
```

| 층 | 보호 | 값 | 실패 시 | 상태 |
|---|---|---|---|---|
| ① Gateway 라우트 | 타임아웃 | 연결 2초, 응답 5초, 주문 라우트 15초 | 응답 초과 504 `SERVICE_TIMEOUT`, 연결 실패 503 | ✅ |
| ① Gateway 라우트 | CB | 서비스(라우트)당 1개, 기준값은 ②와 같음(OPEN 10초 `GW_CB_OPEN_WAIT`). 하위 4xx·5xx 응답은 실패로 안 셈 | 503 `SERVICE_UNAVAILABLE` | ✅ |
| ② Feign (common) | 타임아웃 | 연결 1초, 응답 2초 (order→delivery만 5초 예정) | 예외 3종 (4장) | ✅ |
| ② Feign (common) | CB | 메서드 단위, 10건 중 50% → 10초 차단 | `RemoteServiceException` | ✅ |
| ③ Redis (Gateway) | 타임아웃 | 연결·응답 500ms (`REDIS_TIMEOUT`, `REDIS_CONNECT_TIMEOUT`) | 503 `AUTH_UNAVAILABLE` (fail-closed) | ✅ |
| ④ Slack | 타임아웃만, 실패 무시 | 연결 1초·응답 2초 (`SLACK_CONNECT_TIMEOUT`·`SLACK_READ_TIMEOUT`) | WARN 로그만, 주문은 진행 | ✅ |

## 3. 내 서비스에 Feign 클라이언트 붙이기

### 3.1 의존성

common은 Feign·Resilience4j를 `compileOnly`로만 가집니다. **Feign을 쓰는 서비스가 두 줄을 직접 추가**해야 공통 설정(`CommonRemoteAutoConfiguration`)이 켜집니다.

```groovy
// order-ms/build.gradle (delivery-ms, company-ms 등 Feign을 쓰는 서비스만)
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'   // 빠뜨리면 CB 없이 동작
    testImplementation 'org.wiremock:wiremock-standalone:3.9.2'                                 // 9장 테스트용
}
```

- actuator·eureka-client(LoadBalancer 포함)는 루트 `build.gradle`이 모든 `-ms`에 이미 넣습니다.
- 타임아웃·CB 값은 각 서비스 `application.yaml`의 `spring.profiles.include: common`으로 읽힙니다(6개 서비스 모두 설정되어 있음).

### 3.2 `@EnableFeignClients`

```java
@SpringBootApplication
@EnableFeignClients            // 이 클래스의 패키지(com.order) 아래 @FeignClient를 찾습니다
public class OrderMsApplication { ... }
```

### 3.3 배치 (4계층 + DIP)

| 계층 | 예 (order-ms → hub-ms) | 규칙 |
|---|---|---|
| domain | `domain/inventory/InventoryPort` | Feign을 모르는 인터페이스 |
| infrastructure | `infrastructure/client/HubClient` (`@FeignClient`) | HTTP 계약(경로·DTO)만 |
| infrastructure | `infrastructure/client/HubInventoryAdapter implements InventoryPort` | `RemoteCallTemplate`으로 감쌈 |
| application | `OrderFacade` | 원격 예외 3종을 잡아 업무 처리 |

- `feign.*`, `@FeignClient`는 infrastructure에만 둡니다. 원격 예외 3종은 common `BusinessException` 계열이라 application에서 잡아도 됩니다.

### 3.4 코드 예 (order-ms → hub-ms 재고 차감)

> 경로·DTO는 **예시**입니다. hub-ms 재고 API는 이찬님이 확정합니다.

```java
// domain/inventory/InventoryPort.java
public interface InventoryPort {
    void decrease(String idempotencyKey, UUID productId, int quantity);
    void restore(String idempotencyKey, UUID productId, int quantity);
}

// infrastructure/client/HubClient.java
@FeignClient(name = "hub-ms")                          // Eureka 이름. url 금지 (10장)
public interface HubClient {
    @PostMapping("/internal/v1/inventories/decrease")
    void decrease(@RequestHeader("Idempotency-Key") String key, @RequestBody StockRequest request);

    @PostMapping("/internal/v1/inventories/restore")
    void restore(@RequestHeader("Idempotency-Key") String key, @RequestBody StockRequest request);
}

// infrastructure/client/HubInventoryAdapter.java
@Component
@RequiredArgsConstructor
public class HubInventoryAdapter implements InventoryPort {
    private static final String TARGET = "hub-ms";       // 예외의 getTarget()·로그에만 쓰임
    private final HubClient hubClient;
    private final RemoteCallTemplate remote;              // common이 빈으로 등록

    @Override
    public void decrease(String key, UUID productId, int quantity) {
        StockRequest request = new StockRequest(productId, quantity);   // 재시도해도 같은 키·같은 본문
        remote.executeWithRetryOnce(TARGET, () -> {
            hubClient.decrease(key, request);
            return null;
        });
    }

    @Override
    public void restore(String key, UUID productId, int quantity) {
        remote.run(TARGET, () -> hubClient.restore(key, new StockRequest(productId, quantity)));
    }
}
```

`RemoteCallTemplate` API (`com.common.remote`):

| 메서드 | 용도 |
|---|---|
| `<T> T execute(String target, Supplier<T> call)` | 반환값 있는 호출 |
| `void run(String target, Runnable call)` | 반환값 없는 호출 |
| `<T> T executeWithRetryOnce(String target, Supplier<T> call)` | 멱등 호출만, 실패 시 즉시 1회 재시도 (5장) |

## 4. 실패 처리

| 예외 | 언제 | 잡지 않으면 | CB 실패로 셈? | 호출한 쪽이 할 일 |
|---|---|---|---|---|
| `RemoteBusinessException` | 원격 4xx (401 제외) | 원래 상태·code·메시지 (예: 409 `INSUFFICIENT_STOCK`) | ❌ (`ignore-exceptions`) | **확실히 실패.** 업무 실패 처리 |
| `RemoteServiceException` | 원격 5xx, **원격 401**, CB OPEN, **연결 실패**(거부·연결 타임아웃), 인스턴스 없음 | 503 `SERVICE_UNAVAILABLE` | ✅ (CB OPEN 자체는 호출 안 함) | **처리 안 됨**으로 보고 실패 처리 |
| `RemoteTimeoutException` | 요청을 보낸 뒤 응답을 못 받음 (read 타임아웃, 응답 중 연결 끊김) | 504 `SERVICE_TIMEOUT` | ✅ | **결과 모름.** 같은 키로 재시도 또는 보상/조회 |

- 원격 401은 "호출 쪽이 사용자 헤더를 안 넣은 내부 버그"로 보고 `RemoteServiceException`입니다 (C02 E3). 클라이언트에 재로그인을 안내하지 않습니다.
- 대상 서비스 이름은 클라이언트 메시지에 넣지 않습니다. `getTarget()`·`toString()`(로그)에만 있습니다 (E6).
- `RemoteBusinessException.getErrorCode()`는 원격 code를 복원한 값입니다. `ErrorCodeRegistry`에 등록된 enum이면 `==` 비교, 아니면 `RemoteErrorCode`이므로 `getErrorCode().getCode()` 문자열로 비교합니다. 지금 등록된 enum은 `CommonErrorCode`·`AuthErrorCode`·`UserErrorCode`뿐이라, hub의 `INSUFFICIENT_STOCK` 등은 **문자열 비교**를 쓰세요(새 enum을 만들면 `ErrorCodeRegistry.ALL`에 추가).
- `RemoteServiceException.getStatus()`는 원격 HTTP 상태(`Integer`), 응답이 없으면(CB OPEN·연결 실패) `null`입니다.

> ※ SYS-13 5장·C02 2장 표의 "연결 실패 → `RemoteTimeoutException`"은 **변경됐습니다** (E5). 연결 실패는 요청이 닿지 않은 것이 확실하므로 `RemoteServiceException`입니다. "결과 모름"은 read 타임아웃뿐입니다.

**application에서 잡는 예 (Saga 단계)**

```java
try {
    inventoryPort.decrease(key, productId, quantity);
} catch (RemoteBusinessException e) {                 // 예: 재고 부족 → 되돌릴 것 없음
    order.fail(e.getErrorCode().getCode());
    return;
} catch (RemoteServiceException e) {                  // 처리 안 됨 → 되돌릴 것 없음
    order.fail("HUB_UNAVAILABLE");
    return;
} catch (RemoteTimeoutException e) {                  // 재시도까지 했는데도 모름 → 복원 호출(P1 전제)
    compensateRestore(key, productId, quantity);
    order.fail("HUB_TIMEOUT");
    return;
}
```

- `RemoteTimeoutException`은 `RemoteServiceException`의 하위 클래스가 아니므로 catch 순서는 자유롭지만, 세 가지를 모두 따로 처리해 주세요.

## 5. 재시도

| 규칙 | 내용 |
|---|---|
| 자동 재시도 | **없음.** Feign `Retryer` 기본값(재시도 안 함), common은 Retryer 빈을 등록하지 않습니다 |
| 직접 재시도 | `executeWithRetryOnce`만 사용. **즉시 1회**(대기 없음), 최대 2번 호출 |
| 재시도하는 경우 | `RemoteTimeoutException`, `RemoteServiceException`(5xx·401·연결 실패) |
| 재시도하지 않는 경우 | `RemoteBusinessException`(4xx), **CB OPEN**(원인이 `CallNotPermittedException`) |
| 두 번 모두 실패 | 두 번째 실패를 그대로 던집니다 |

**전제 (지키지 않으면 쓰지 마세요)**

1. 받는 쪽이 같은 `Idempotency-Key`로 다시 오면 **처음 결과를 그대로** 돌려줘야 합니다 (P2·P3).
2. 키와 요청 본문은 **람다 밖에서 한 번만** 만듭니다. 람다 안에서 `UUID.randomUUID()`를 부르면 재시도마다 키가 바뀝니다.
3. GET 조회는 부작용이 없으므로 키 없이 써도 됩니다. **키 없는 POST/PATCH/DELETE에는 금지**입니다.
4. 재시도 실패도 CB 실패로 한 번 더 셉니다. 반복 루프로 감싸지 마세요(보상 재시도 3회는 9장 F1과 함께 다울님이 정합니다).

## 6. Circuit Breaker

| 항목 | 값 | 속성 (`resilience4j.circuitbreaker.configs.default.*`) |
|---|---|---|
| 집계 방식 | 최근 호출 10건 | `sliding-window-type: COUNT_BASED`, `sliding-window-size: 10` |
| 최소 호출 수 | 5건 | `minimum-number-of-calls: 5` |
| 실패율 기준 | 50% 이상 → OPEN | `failure-rate-threshold: 50` |
| OPEN 유지 | 10초 (환경 변수 `CB_WAIT_IN_OPEN`) | `wait-duration-in-open-state` |
| HALF_OPEN 시험 호출 | 3건 | `permitted-number-of-calls-in-half-open-state: 3` |
| 자동 HALF_OPEN 전환 | 켬 | `automatic-transition-from-open-to-half-open-enabled: true` |
| 실패로 안 셈 | `RemoteBusinessException` (4xx) | `ignore-exceptions` |
| TimeLimiter | **없음** | `spring.cloud.circuitbreaker.resilience4j.disable-thread-pool: true` |

환경 변수로 바꿀 수 있는 값은 `FEIGN_CONNECT_TIMEOUT`(기본 1000ms), `FEIGN_READ_TIMEOUT`(기본 2000ms), `CB_WAIT_IN_OPEN`(기본 10s) 세 개뿐입니다.

**단위·이름**

- CB는 **Feign 메서드마다 하나**입니다. `hub-ms` 전체가 아니라 `HubClient#decrease`만 열립니다. 같은 클라이언트의 다른 메서드는 계속 호출됩니다 (테스트 17).
- 이름은 Feign configKey에서 영숫자만 남긴 값입니다: `HubClient#decrease(String,StockRequest)` → `HubClientdecreaseStringStockRequest`. 정확한 이름은 아래 actuator로 확인하세요.
- Feign 호출은 **호출한 스레드에서 그대로** 실행됩니다. 그래서 사용자 헤더 전달·트랜잭션·MDC가 유지되고, 타임아웃은 Feign 값 하나뿐입니다 (E2).

> ※ SYS-13 4.2·11.1, C02 1장·4장의 "대상 서비스 단위 CB(`group.enabled: true`)"와 "TimeLimiter 6s"는 **변경됐습니다** (E1·E2). 현재 `application-common.yaml`에 `group` 설정과 `timelimiter`는 없습니다. C02 8장의 `.circuitBreakers["hub-ms"]`도 메서드 이름으로 바꿔 읽어 주세요.

**상태 확인** (서비스 포트로 직접, Gateway는 `/actuator`를 라우팅하지 않습니다)

```bash
curl -s localhost:19094/actuator/circuitbreakers | jq '.circuitBreakers | map_values(.state)'
curl -s localhost:19094/actuator/circuitbreakerevents                       # 상태 전환·실패 기록
```

- CB 인스턴스는 **처음 호출될 때** 생깁니다. 호출 전에는 목록에 없습니다.
- OPEN일 때는 원격에 요청이 가지 않고 `RemoteServiceException`(원인 `CallNotPermittedException`, `getStatus() == null`)이 납니다.

## 7. 타임아웃 조정이 필요할 때

기본값(연결 1초·응답 2초)은 단건 조회·변경 기준입니다. 더 긴 호출이 있으면 **호출하는 서비스**의 `application.yaml`에 클라이언트 이름(`@FeignClient(name)`)으로만 덮어씁니다.

```yaml
# order-ms/src/main/resources/application.yaml (다울) — 예정: 배송 생성 안에서 Hub 경로 조회를 다시 호출
spring:
  cloud:
    openfeign:
      client:
        config:
          delivery-ms:
            read-timeout: 5000
```

- 지금 코드에 서비스별 예외는 **하나도 없습니다.** order→delivery 5초는 설계상 유일한 예정 예외입니다.
- 같은 위치에 `url`은 넣지 마세요. `FeignClientUrlGuard`가 `client.config.*.url`도 검사해 기동을 멈춥니다.
- **바깥 > 안쪽 합** 원칙: Gateway 주문 라우트(15초, `GW_ORDER_RESPONSE_TIMEOUT_MS`) > 주문 내부 호출 합(업체 2 + 재고 2 + 배송 5 + 복원 2 + 여유). `executeWithRetryOnce`는 해당 구간을 최대 2배로 늘리므로 합에 포함해 계산합니다.
- 값을 바꾸거나 새 예외를 추가하려면 **대진에게 먼저 공유**해 주세요. Gateway 값과 함께 맞춥니다.

## 8. Saga 연계 (주문 생성, 다울)

SYS-13 8.1을 현재 예외 분류(E5)에 맞춘 제안입니다. 확정은 다울님이 합니다.

| 단계 | `RemoteBusinessException` | `RemoteServiceException` | `RemoteTimeoutException` |
|---|---|---|---|
| ① 업체·상품 검증 → company | FAILED | FAILED (되돌릴 것 없음) | FAILED (조회라 되돌릴 것 없음) |
| ② 재고 차감 → hub | 재고 부족 → FAILED | FAILED | 같은 키로 1회 재시도 → 그래도 모르면 **재고 복원** 후 FAILED |
| ③ 배송 생성 → delivery | 재고 복원 → FAILED | 재고 복원 → FAILED | 같은 키로 1회 재시도 → 그래도 모르면 **배송 취소 + 재고 복원** 후 FAILED |
| ④ 보상: 재고 복원 → hub | - | 같은 키로 재시도 | 같은 키로 재시도 |
| ⑤ Slack 알림 | 로그만 | 로그만 | 로그만 |

**받는 쪽 전제 (이찬·영은 확인 필요)**

| # | 전제 | 담당 |
|---|---|---|
| P1 | 재고 복원: 차감 기록이 없으면 아무것도 하지 않고 **성공** | 이찬 |
| P2 | 재고 차감·복원: 같은 `Idempotency-Key`면 **처음 결과 그대로** | 이찬 |
| P3 | 배송 생성: 같은 키(또는 `orderId`)면 **기존 배송 반환** | 영은 |
| P4 | 배송 취소: 배송이 없으면 아무것도 하지 않고 **성공** | 영은 |

P1~P4가 없으면 `executeWithRetryOnce`와 "모르면 보상" 전략을 쓸 수 없습니다.

**미결정 — ⚠️ 다울 결정 필요**

| # | 항목 |
|---|---|
| F1 | 보상(재고 복원)이 끝까지 실패할 때 주문 상태 (`PROCESSING` 유지 + 로그 / 별도 상태) |
| F2 | `PROCESSING`으로 멈춘 주문을 누가 언제 정리할지 (스케줄러 등) |
| F3 | 주문 생성 동기·비동기. 설계는 `202 Accepted`, 구조는 Feign 동기라 `201`과 미정 ([[07 서비스 정책]] 9장 11번). Gateway 주문 라우트 타임아웃(15초)과 직결 |

**비동기로 가면 주의:** `UserHeaderRelayInterceptor`는 `RequestContextHolder`에서 헤더를 읽으므로, `@Async`·스케줄러 등 **요청 스레드 밖**의 Feign 호출에는 `X-User-Id`·`X-User-Role`·`X-Request-Id`가 붙지 않습니다. 받는 쪽 감사 기록·역할 확인이 필요하면 Feign 메서드에 `@RequestHeader`로 직접 넘기세요(직접 지정한 헤더는 인터셉터가 덮어쓰지 않습니다. 사용자 헤더 두 개는 한 쌍이라 둘 다 넣어 주세요).

## 9. 테스트 방법

common의 `FeignResilienceIntegrationTest`와 같은 방식입니다. **url 없이** WireMock 주소를 서비스 이름에 등록하므로 `FeignClientUrlGuard`를 끌 필요가 없습니다.

```java
@SpringBootTest(properties = {
        "spring.cloud.openfeign.client.config.default.read-timeout=300",            // 타임아웃을 빠르게
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=1s"})
class HubInventoryAdapterIntegrationTest {

    static final WireMockServer HUB = new WireMockServer(options().dynamicPort());
    static { HUB.start(); }

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) {     // Eureka 대신 SimpleDiscoveryClient
        registry.add("spring.cloud.discovery.client.simple.instances.hub-ms[0].uri",
                () -> "http://localhost:" + HUB.port());
    }

    @AfterAll static void stop() { HUB.stop(); }

    @Autowired InventoryPort inventoryPort;
    @Autowired CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        HUB.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);   // 테스트 간 CB 상태 초기화
    }

    @Test
    void 응답이_늦으면_결과모름() {
        HUB.stubFor(post("/internal/v1/inventories/decrease")
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
        assertThatThrownBy(() -> inventoryPort.decrease("k1", UUID.randomUUID(), 1))
                .isInstanceOf(RemoteTimeoutException.class);
        HUB.verify(2, postRequestedFor(urlEqualTo("/internal/v1/inventories/decrease"))
                .withHeader("Idempotency-Key", equalTo("k1")));                     // 재시도 1회, 같은 키
    }
}
```

| 시뮬레이션 | 방법 |
|---|---|
| read 타임아웃 | `withFixedDelay(1000)` + `read-timeout=300` → `RemoteTimeoutException` |
| 5xx / CB OPEN | `withStatus(503)` 5회 → `RemoteServiceException`, 다음 호출은 WireMock에 안 닿음 |
| 업무 거절 | `withStatus(409)` + `{"code":"INSUFFICIENT_STOCK","message":"..."}` → `RemoteBusinessException`, CB는 CLOSED |
| 연결 실패 | 아무도 듣지 않는 포트를 인스턴스로 등록 → `RemoteServiceException` |
| 인스턴스 없음 | 인스턴스를 등록하지 않음 → `RemoteServiceException` |

- 서비스 테스트 프로필(`test`)은 common `testFixtures`의 `application-test.yaml`로 Eureka가 꺼져 있고, `application.yaml`의 `include: common`으로 공통 Feign·CB 값이 그대로 적용됩니다.
- Facade 단위 테스트는 `InventoryPort` 가짜 구현이 예외 3종을 던지게 하면 충분합니다(Feign 불필요).
- 실제 시연: `docker compose stop hub-ms` 후 주문 → 503/504, 5건 이상이면 `/actuator/circuitbreakers`에서 OPEN, 재시작 10초 뒤 HALF_OPEN → CLOSED.

## 10. Do / Don't

| Do | Don't |
|---|---|
| 모든 Feign 호출을 `RemoteCallTemplate`으로 감싸기 | Adapter 밖에서 `HubClient`를 직접 호출 |
| `@FeignClient(name = "서비스이름")`만 사용 | `url = ...` 지정, `client.config.*.url` 지정 (기동 실패) |
| 외부 API(Slack)는 `RestClient` | 외부 API에 Feign 사용 (헤더 유출·오류 오분류) |
| 예외 3종을 각각 다르게 처리 | `RemoteTimeoutException`을 "확실한 실패"로 단정하고 끝내기 |
| 멱등 키가 있는 호출만 `executeWithRetryOnce` | 키 없는 변경 호출 재시도, 직접 루프 재시도 |
| 키·본문을 람다 밖에서 한 번 생성 | 람다 안에서 새 키 생성 |
| 실패를 예외로 올리기 | `@FeignClient(fallback)`로 가짜 성공 반환 (SYS-13 7장) |
| 타임아웃 예외는 대진에게 공유 | 서비스에서 CB·타임아웃 기본값 임의 변경 |
| 원격 호출 동안 로컬 트랜잭션을 짧게 | 긴 `@Transactional` 안에서 여러 원격 호출(같은 스레드라 DB 커넥션을 계속 잡음) |
| `@FeignClient`·`feign.*`는 infrastructure에만 | domain·application에서 Feign import |

## 11. FAQ

**Q. 의존성을 안 넣었는데 컴파일이 됩니다.** `RemoteCallTemplate` 등은 common에 있어 컴파일은 되지만, `spring-cloud-starter-openfeign`이 없으면 `CommonRemoteAutoConfiguration`(`@ConditionalOnClass(name = "feign.Feign")`)이 꺼져 빈이 없습니다. resilience4j 스타터가 없으면 CB 없이 호출됩니다. 두 줄 모두 넣어 주세요.

**Q. 기동 시 "Feign 클라이언트에 고정 url이 있습니다"로 실패합니다.** `@FeignClient(url)` 또는 `spring.cloud.openfeign.client.config.*.url`을 지우고 Eureka 이름으로 부르세요. 테스트에서만 `common.feign.allow-direct-url=true`로 허용합니다.

**Q. 재고 부족 409가 많이 나면 CB가 열리나요?** 아니요. `RemoteBusinessException`은 `ignore-exceptions`라 세지 않습니다 (테스트 15).

**Q. hub-ms가 꺼졌는데 504가 아니라 503이 나옵니다.** 정상입니다. 연결 실패는 `RemoteServiceException`(503)입니다. 504는 응답을 기다리다 끊긴 경우뿐입니다.

**Q. CB가 열렸는데 같은 서비스의 다른 API는 됩니다.** 정상입니다. CB는 메서드 단위입니다.

**Q. 원격이 401을 줍니다.** 호출 쪽이 사용자 헤더를 안 보낸 버그입니다(`RemoteServiceException`, ERROR 로그). 요청 스레드 밖에서 호출했는지 확인하세요(8장).

**Q. `CallNotPermittedException`을 잡아야 하나요?** 아니요. `RemoteCallTemplate`이 `RemoteServiceException`으로 바꿉니다. 원인은 `getCause()`에 있습니다.

## 12. 참고 문서

| 문서 | 내용 |
|---|---|
| [[SYS-13 장애 대응 설계]] | 타임아웃·CB 원칙, 8장 호출별 실패 처리(P1~P4, F1~F3) |
| [[01 Gateway·인증 가이드]] | Gateway 라우트 타임아웃·CB (GW-09·10) |
| [[NTF 주문 알림 설계]] · [[04 Slack 알림 가이드]] | Slack `RestClient` 알림 |
| 코드 | `common/src/main/java/com/common/remote/**`, `common/src/main/resources/application-common.yaml` |
| 테스트 예 | `common/src/test/java/com/common/remote/integration/FeignResilienceIntegrationTest.java`, `RemoteCallTemplateTest.java` |
