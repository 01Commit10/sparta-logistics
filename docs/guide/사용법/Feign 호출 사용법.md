# Feign 호출 사용법 (공통 장애 대응)

> 작성: 대진 · 기준: `dev` d6af869 · 최종 수정: 2026-10-16
> 대상: 내 서비스에서 **다른 우리 서비스**를 호출하는 사람 (order→hub·company·delivery, delivery→user·hub 등)
> 원리·설계 이유는 [[02 장애 대응(Feign) 가이드]]에 있습니다. 이 문서는 **따라 하기만** 합니다.

## 3줄 요약

1. `@FeignClient(name = "서비스이름")`으로 클라이언트를 만들고 `RemoteCallTemplate`으로 감싸 호출하면, **타임아웃(연결 1초·응답 2초)·Circuit Breaker·오류 분류가 자동**으로 붙습니다.
2. 실패는 예외 **3종**으로만 옵니다. 업무 거절(4xx), 서비스 못 씀(503), **결과 모름**(504). 이 셋만 구분해서 처리하면 됩니다.
3. 로그인 사용자 헤더(`X-User-Id`·`X-User-Role`)와 `X-Request-Id`는 **자동으로 이어 보냅니다.** 직접 넣지 않아도 됩니다.

> 처음이라면 **[[예시 - 다른 서비스에서 사용자 조회]]** 부터 보세요. 파일 하나하나를 처음부터 끝까지 만들어 보는 예제입니다.
> 주문·배송에 필요한 조회(상품·업체, 허브 경로)는 **[[예시 - 주문·배송에서 다른 도메인 조회]]** 에 있습니다.

## 1. 준비 (서비스당 한 번)

**① `build.gradle`에 두 줄**

```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'   // 빠뜨리면 CB 없이 동작
}
```

**② 애플리케이션 클래스에 `@EnableFeignClients`**

```java
@SpringBootApplication
@EnableFeignClients
public class OrderMsApplication { ... }
```

끝입니다. 타임아웃·CB·오류 해석·헤더 전달은 common이 자동으로 설정합니다(`application.yaml`의 `spring.profiles.include: common`은 이미 6개 서비스 모두 들어 있습니다).

## 2. 클라이언트 만들기 (3개 파일)

예: order-ms가 user-ms에서 사용자 정보를 가져오는 경우

```java
// ① domain — Feign을 모르는 인터페이스
public interface UserPort {
    Optional<UserSnapshot> findActiveUser(UUID userId);
}

// ② infrastructure/client — HTTP 주소와 DTO만
@FeignClient(name = "user-ms")                       // Eureka 이름. url은 쓰지 않습니다
public interface UserClient {
    @GetMapping("/internal/v1/users/{userId}")
    InternalUserResponse getUser(@PathVariable("userId") UUID userId);
}

// ③ infrastructure/client — Adapter: RemoteCallTemplate으로 감싸기
@Component
@RequiredArgsConstructor
public class UserAdapter implements UserPort {
    private final UserClient userClient;
    private final RemoteCallTemplate remote;          // common이 빈으로 등록해 둠

    @Override
    public Optional<UserSnapshot> findActiveUser(UUID userId) {
        try {
            InternalUserResponse r = remote.execute("user-ms", () -> userClient.getUser(userId));
            return Optional.of(new UserSnapshot(r.id(), r.name(), r.isDeliveryManager(), r.slackId()));
        } catch (RemoteBusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
```

`RemoteCallTemplate` 메서드 3개:

| 메서드 | 언제 |
|---|---|
| `remote.execute("hub-ms", () -> client.get(...))` | 반환값이 있는 호출 |
| `remote.run("hub-ms", () -> client.restore(...))` | 반환값이 없는 호출 |
| `remote.executeWithRetryOnce("hub-ms", () -> ...)` | 실패하면 **즉시 1번 더**. 조회(GET)나 `Idempotency-Key`가 있는 호출에만 (4장) |

## 3. 실패 처리: 예외 3종

| 예외 | 언제 | 뜻 | 잡지 않으면 |
|---|---|---|---|
| `RemoteBusinessException` | 상대가 4xx로 거절 (재고 부족, 없음 등) | **확실히 실패.** 상대는 아무것도 안 함 | 상대와 같은 상태·code로 응답 (예: 409) |
| `RemoteServiceException` | 상대가 5xx(504 제외), 꺼져 있음, 연결 안 됨, CB 차단 중 | **처리 안 됨**으로 봄 | 503 `SERVICE_UNAVAILABLE` |
| `RemoteTimeoutException` | 요청은 보냈는데 2초 안에 응답 없음, 상대가 504, 2xx 응답을 내 DTO로 못 바꿈 | **결과 모름.** 상대가 처리했을 수도 있음 | 504 `SERVICE_TIMEOUT` |

```java
try {
    inventoryPort.decrease(key, productId, quantity);
} catch (RemoteBusinessException e) {          // 재고 부족 등 → 되돌릴 것 없음
    order.fail(e.getErrorCode().getCode());
} catch (RemoteServiceException e) {           // 안 됐음 → 되돌릴 것 없음
    order.fail("HUB_UNAVAILABLE");
} catch (RemoteTimeoutException e) {           // 모름 → 되돌리는 호출(보상) 필요
    compensateRestore(key, productId, quantity);
    order.fail("HUB_TIMEOUT");
}
```

- **조회만 하는 호출이면 안 잡아도 됩니다.** 그대로 503/504가 클라이언트에 나갑니다.
- 상대 오류 code 비교:
  - common에 등록된 code(`USER_NOT_FOUND` 등)는 `e.getErrorCode() == UserErrorCode.USER_NOT_FOUND`처럼 enum으로
  - 그 밖의 code(hub의 `INSUFFICIENT_STOCK` 등)는 `"INSUFFICIENT_STOCK".equals(e.getErrorCode().getCode())`처럼 문자열로

## 4. 재시도 규칙

- 자동 재시도는 **없습니다.** 필요하면 `executeWithRetryOnce`만 씁니다.
- 쓸 수 있는 경우: GET 조회, 또는 받는 쪽이 같은 `Idempotency-Key`로 다시 와도 **처음 결과를 그대로 돌려주는** API
- 키와 요청 본문은 **람다 밖에서 한 번만** 만드세요.
- 다시 보내지 않는 경우: 4xx, CB 차단 중, 응답 변환 실패(다시 보내도 같은 응답).
- **첫 번째가 결과 모름(`RemoteTimeoutException`)이고 두 번째가 503류로 실패하면 첫 번째 `RemoteTimeoutException`이 나옵니다.** 첫 요청이 처리됐을 수 있으니 "안 됐음"으로 바뀌지 않게 한 것입니다. 이때도 보상·조회로 처리하세요.

```java
String key = order.getId() + ":decrease";                     // 람다 밖에서 한 번
StockRequest request = new StockRequest(productId, quantity);
remote.executeWithRetryOnce("hub-ms", () -> { hubClient.decrease(key, request); return null; });
```

## 5. 상태 확인

```bash
# 서비스 포트로 직접 (Gateway는 /actuator를 전달하지 않습니다)
curl -s localhost:19094/actuator/circuitbreakers | jq '.circuitBreakers | map_values(.state)'
```

- CB는 **Feign 메서드마다 따로** 열립니다. `HubClient#decrease`가 열려도 `HubClient#get`은 계속 호출됩니다.
- 최근 10건 중 5건 이상, 실패율 50% 이상이면 10초 동안 호출을 막습니다(`CLOSED` → `OPEN`). 4xx는 실패로 세지 않습니다.
- 처음 호출하기 전에는 목록에 없습니다.

## 6. 타임아웃이 모자랄 때

기본 응답 대기 2초로 부족한 호출이 있으면 **호출하는 서비스**의 `application.yaml`에 클라이언트 이름으로만 덮어씁니다. 바꾸기 전에 **대진에게 먼저 알려 주세요.** Gateway 타임아웃(일반 5초, 주문 생성 경로 15초)과 함께 맞춰야 합니다.

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          delivery-ms:          # @FeignClient(name) 값
            read-timeout: 5000
```

## 7. 테스트하기: 도메인 안에서 따로 vs Feign으로 서비스끼리

> 예시는 order-ms → hub-ms 재고 차감입니다. `InventoryPort`, `HubClient`, `HubInventoryAdapter`, `OrderFacade`, 경로·DTO는 **예시**입니다([[02 장애 대응(Feign) 가이드]] 3.4와 같은 이름). 예외 3종, `RemoteCallTemplate`, `RemoteErrorCode`는 실제 common 코드입니다.

테스트는 **두 갈래**로 나눕니다.

| 갈래 | 상대 서비스 | 필요한 것 | 언제 |
|---|---|---|---|
| **A. 도메인 안에서 따로** (7.1·7.2) | 없음. 가짜 Port 또는 가짜 서버(WireMock) | 아무것도 안 띄움. `sh ./gradlew :order-ms:test` | 매 빌드. 상대 서비스가 아직 없어도 됨 |
| **B. Feign으로 서비스끼리** (7.3·7.4) | **진짜 hub-ms** (Eureka로 찾아감) | Eureka + 상대 서비스 + DB 실행 | 상대 서비스가 완성된 뒤, PR 전, 시연 전 |

| # | 테스트 | 갈래 | 상대 | 확인하는 것 |
|---|---|---|---|---|
| 7.1 | 가짜 Port 단위 테스트 | A | `FakeInventoryPort` (Feign·HTTP 없음) | 예외 3종을 받았을 때 내 업무 처리(실패, 보상 호출) |
| 7.2 | 진짜 Feign + WireMock | A | 가짜 hub-ms 서버 | 경로·DTO·헤더, HTTP 응답 → 예외 3종 변환, 재시도 키, CB |
| 7.3 | 진짜 Feign + 진짜 hub-ms (자동, `@Tag("e2e")`) | B | 실행 중인 hub-ms | **두 서비스의 계약이 실제로 맞는지** (경로·필드 이름·오류 code) |
| 7.4 | 서비스 전체 수동 확인 | B | 실행 중인 전체 서비스 | Gateway부터 끝까지, 장애 시 동작 |

> ✅ 7.2의 테스트 6개, 7.1의 예외 생성 코드, 7.3의 방식(진짜 user-ms를 Eureka로 찾아 호출하는 테스트 2개)은 dev(`d6af869`) 복사본에서 **그대로 돌려 통과**를 확인했습니다(2026-10-16). 7.1의 `OrderFacade`는 주문 구현에 따라 달라 실행하지 않았습니다.

A는 상대가 어떤 응답을 줄지 **내가 정해서** 모든 분기를 빠르게 확인하고, B는 상대가 **실제로** 그 응답을 주는지 확인합니다. 분기는 A에서 촘촘히, B는 대표 경우 몇 개만 두세요.

### 7.1 [A] 가짜 Port로 업무 처리 확인 (단위)

domain의 Port만 가짜로 바꿉니다. 원하는 예외를 마음대로 던지게 만들 수 있습니다.

```java
// src/test/java/com/order/fake/FakeInventoryPort.java
public class FakeInventoryPort implements InventoryPort {
    public final List<String> calls = new ArrayList<>();
    private final Deque<RuntimeException> failures = new ArrayDeque<>();

    /** 다음 decrease 호출에서 던질 예외 */
    public void failNextDecrease(RuntimeException e) { failures.add(e); }

    @Override
    public void decrease(String key, UUID productId, int quantity) {
        calls.add("decrease:" + key);
        if (!failures.isEmpty()) throw failures.poll();
    }

    @Override
    public void restore(String key, UUID productId, int quantity) {
        calls.add("restore:" + key);
    }
}
```

예외 3종은 생성자로 바로 만듭니다.

```java
// 업무 거절 (hub가 409 INSUFFICIENT_STOCK을 준 상황)
new RemoteBusinessException("hub-ms", 409,
        new RemoteErrorCode(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."), "재고가 부족합니다.");
// 처리 안 됨 (hub 500, 꺼짐, CB 차단)
new RemoteServiceException("hub-ms", 500);
// 결과 모름 (응답 시간 초과)
new RemoteTimeoutException("hub-ms", new SocketTimeoutException("Read timed out"));
// 결과 모름 (hub가 504를 줌, getStatus() == 504)
new RemoteTimeoutException("hub-ms", 504);
```

```java
class OrderFacadeTest {

    private final FakeInventoryPort inventory = new FakeInventoryPort();
    private final FakeOrderRepository orders = new FakeOrderRepository();
    private final OrderFacade facade = new OrderFacade(orders, inventory /* , 다른 Port들도 가짜로 */);

    @Test
    void 재고_부족이면_FAILED이고_복원하지_않는다() {
        inventory.failNextDecrease(new RemoteBusinessException("hub-ms", 409,
                new RemoteErrorCode(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."), "재고가 부족합니다."));

        UUID orderId = facade.create(command);

        assertThat(orders.find(orderId).status()).isEqualTo(OrderStatus.FAILED);
        assertThat(inventory.calls).noneMatch(call -> call.startsWith("restore"));
    }

    @Test
    void 결과를_모르면_같은_키로_복원한다() {
        inventory.failNextDecrease(new RemoteTimeoutException("hub-ms", new SocketTimeoutException()));

        UUID orderId = facade.create(command);

        assertThat(orders.find(orderId).status()).isEqualTo(OrderStatus.FAILED);
        String key = inventory.calls.get(0).substring("decrease:".length());
        assertThat(inventory.calls).contains("restore:" + key);      // 차감과 같은 키로 보상
    }

    @Test
    void hub가_죽어_있으면_FAILED이고_복원하지_않는다() {
        inventory.failNextDecrease(new RemoteServiceException("hub-ms", 503));

        UUID orderId = facade.create(command);

        assertThat(orders.find(orderId).status()).isEqualTo(OrderStatus.FAILED);
        assertThat(inventory.calls).noneMatch(call -> call.startsWith("restore"));
    }
}
```

- 여기서는 **재시도 횟수를 세지 않습니다.** 재시도는 Adapter(`executeWithRetryOnce`) 안에 있어서 가짜 Port에는 보이지 않습니다. 7.2에서 확인합니다.

### 7.2 [A] 진짜 Feign + 가짜 hub-ms 서버 (WireMock)

```groovy
// order-ms build.gradle
testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
```

```java
@SpringBootTest(properties = {
        "spring.cloud.openfeign.client.config.default.read-timeout=300",              // 타임아웃을 0.3초로 줄여 빠르게
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=1s"})
class HubInventoryAdapterTest {

    static final WireMockServer HUB = new WireMockServer(options().dynamicPort());
    static { HUB.start(); }

    @DynamicPropertySource
    static void hub(DynamicPropertyRegistry registry) {
        // @FeignClient(name = "hub-ms")가 Eureka 대신 이 주소로 간다. @FeignClient에 url을 넣지 않는다
        registry.add("spring.cloud.discovery.client.simple.instances.hub-ms[0].uri", HUB::baseUrl);
    }

    @Autowired InventoryPort inventory;                  // 진짜 HubInventoryAdapter
    @Autowired CircuitBreakerRegistry circuitBreakers;

    private static final String DECREASE = "/internal/v1/inventories/decrease";
    private final UUID productId = UUID.randomUUID();

    @AfterEach
    void reset() {
        HUB.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);   // 앞 테스트의 실패 기록 지우기
    }

    @AfterAll
    static void stop() { HUB.stop(); }

    @Test
    void 정상이면_경로_헤더_본문이_계약대로_간다() {
        HUB.stubFor(post(DECREASE).willReturn(ok()));

        inventory.decrease("order-1:decrease", productId, 3);

        HUB.verify(postRequestedFor(urlEqualTo(DECREASE))
                .withHeader("Idempotency-Key", equalTo("order-1:decrease"))
                .withRequestBody(matchingJsonPath("$.productId", equalTo(productId.toString())))
                .withRequestBody(matchingJsonPath("$.quantity", equalTo("3"))));
    }

    @Test
    void 업무_거절_409는_RemoteBusinessException이고_원격_code가_살아있다() {
        HUB.stubFor(post(DECREASE).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"INSUFFICIENT_STOCK","message":"재고가 부족합니다."}
                        """)));

        assertThatThrownBy(() -> inventory.decrease("k", productId, 3))
                .isInstanceOfSatisfying(RemoteBusinessException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(e.getStatus()).isEqualTo(409);
                });
        HUB.verify(1, postRequestedFor(urlEqualTo(DECREASE)));          // 업무 거절은 재시도하지 않음
    }

    @Test
    void 서버_오류_500은_RemoteServiceException() {
        HUB.stubFor(post(DECREASE).willReturn(serverError()));

        assertThatThrownBy(() -> inventory.decrease("k", productId, 3))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void 응답이_늦으면_같은_키로_한_번_더_보내고_RemoteTimeoutException() {
        HUB.stubFor(post(DECREASE).willReturn(ok().withFixedDelay(1000)));    // read-timeout 300ms보다 길게

        assertThatThrownBy(() -> inventory.decrease("order-1:decrease", productId, 3))
                .isInstanceOf(RemoteTimeoutException.class);

        List<LoggedRequest> calls = HUB.findAll(postRequestedFor(urlEqualTo(DECREASE)));
        assertThat(calls).hasSize(2)                                         // executeWithRetryOnce: 최대 2번
                .extracting(r -> r.getHeader("Idempotency-Key")).containsOnly("order-1:decrease");
    }

    @Test
    void 연속으로_실패하면_CB가_열려_요청이_가지_않는다() {
        HUB.stubFor(post(DECREASE).willReturn(serverError()));
        for (int i = 0; i < 5; i++) {
            catchThrowable(() -> inventory.decrease("k", productId, 3));
        }
        HUB.resetRequests();

        assertThatThrownBy(() -> inventory.decrease("k", productId, 3))
                .isInstanceOf(RemoteServiceException.class);
        HUB.verify(0, postRequestedFor(urlEqualTo(DECREASE)));               // 차단 중이라 hub에 안 감
    }

    @Test
    void 사용자_요청_안에서_부르면_사용자_헤더가_자동으로_간다() {
        HUB.stubFor(post(DECREASE).willReturn(ok()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthContract.HEADER_USER_ID, "00000000-0000-0000-0000-000000000002");
        request.addHeader(AuthContract.HEADER_USER_ROLE, "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));   // 요청 처리 중인 상황 흉내
        try {
            inventory.decrease("k", productId, 3);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        HUB.verify(postRequestedFor(urlEqualTo(DECREASE))
                .withHeader(AuthContract.HEADER_USER_ID, equalTo("00000000-0000-0000-0000-000000000002"))
                .withHeader(AuthContract.HEADER_USER_ROLE, equalTo("USER"))
                .withoutHeader("Authorization"));                                // 토큰은 넘어가지 않음
    }
}
```

- 연결 실패(상대가 꺼짐)도 보고 싶으면 아무도 안 듣는 포트를 서비스 주소로 등록합니다. 결과는 `RemoteServiceException`입니다([[05 통합 테스트 가이드]] 4.3).
- 받는 쪽(hub-ms)은 자기 내부 API를 MockMvc로 계약 테스트합니다(05 가이드 4.6). **양쪽 테스트가 같은 경로·필드 이름을 쓰는지**가 서비스 간 계약입니다.

### 7.3 [B] 진짜 Feign + 진짜 hub-ms (자동, `@Tag("e2e")`)

7.2의 WireMock 응답은 **내가 상상한** hub-ms 응답입니다. hub-ms가 경로나 필드 이름을 바꾸면 7.2는 계속 통과하는데 실제로는 깨집니다. 이 테스트는 order-ms의 **진짜 Adapter**가 Eureka로 **실행 중인 hub-ms**를 찾아 진짜로 호출해서 그 차이를 잡습니다.

**① build.gradle: 평소 빌드에서는 빼 두기**

상대 서비스가 떠 있어야 하므로, 평소 `test`에서는 제외하고 `-PincludeE2e`를 붙였을 때만 돌립니다.

```groovy
// order-ms build.gradle
tasks.named('test') {
    useJUnitPlatform {
        if (!project.hasProperty('includeContainer')) {
            excludeTags 'container'          // Docker 테스트 (3장, 05 가이드)
        }
        if (!project.hasProperty('includeE2e')) {
            excludeTags 'e2e'                // 실제 서비스가 떠 있어야 하는 테스트
        }
    }
}
```

**② 테스트: Eureka만 다시 켜기**

테스트 프로필은 Eureka를 꺼 두므로(`eureka.client.enabled: false`), 이 테스트에서만 켭니다. WireMock 주소 연결(`simple.instances`)은 쓰지 않습니다.

```java
@Tag("e2e")
@SpringBootTest(properties = {
        "eureka.client.enabled=true",                 // test 프로필에서 꺼 둔 Eureka를 다시 켠다
        "eureka.client.register-with-eureka=false",   // 테스트 자신은 등록하지 않는다 (Gateway·다른 서비스가 이리로 보내지 않게)
        "eureka.client.fetch-registry=true",
        "eureka.client.service-url.defaultZone=${E2E_EUREKA_URL:http://localhost:8761/eureka/}"})
class HubInventoryE2ETest {

    @Autowired InventoryPort inventory;                  // 진짜 HubInventoryAdapter → 진짜 hub-ms

    /** 테스트 전용 상품. hub-ms에 미리 만들어 두고 ID를 넘긴다 (③) */
    private final UUID productId = UUID.fromString(System.getProperty("e2e.productId"));

    @Test
    void 재고_차감과_복원이_진짜_hub_ms와_맞물린다() {
        String key = "e2e-" + UUID.randomUUID();             // 실행마다 새 키 (멱등성 키가 겹치지 않게)

        inventory.decrease(key, productId, 1);
        inventory.restore(key, productId, 1);                // 테스트가 남긴 변화를 되돌린다
    }

    @Test
    void 재고보다_많이_빼면_hub_ms의_실제_오류_code가_온다() {
        assertThatThrownBy(() -> inventory.decrease("e2e-" + UUID.randomUUID(), productId, 1_000_000))
                .isInstanceOfSatisfying(RemoteBusinessException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo("INSUFFICIENT_STOCK"));   // hub-ms가 정한 code
    }

    @Test
    void 같은_키로_두_번_보내도_한_번만_빠진다() {          // 재시도 전제(멱등성)를 진짜로 확인
        String key = "e2e-" + UUID.randomUUID();
        int before = stockOf(productId);                     // 재고 조회: hub-ms 조회 API를 Feign으로 (예시)

        inventory.decrease(key, productId, 1);
        inventory.decrease(key, productId, 1);

        assertThat(stockOf(productId)).isEqualTo(before - 1);
        inventory.restore(key, productId, 1);
    }
}
```

**③ 준비하고 돌리기**

```bash
# 1) 상대 서비스와 Eureka 띄우기 (Gateway는 필요 없음)
docker compose up -d postgres redis
sh ./gradlew :eureka-registry:bootRun        # 터미널 1
sh ./gradlew :hub-ms:bootRun                 # 터미널 2

# 2) 테스트 전용 상품·재고 만들기 (hub-ms API, MASTER 토큰 또는 서비스 포트 직접) → ID 메모

# 3) 실행
sh ./gradlew :order-ms:test --tests '*E2ETest' -PincludeE2e -De2e.productId=<상품 UUID>
```

- `-De2e.productId`처럼 테스트에 값을 넘기려면 `build.gradle`의 `test` 블록에 `systemProperty 'e2e.productId', System.getProperty('e2e.productId')`도 추가하세요. 값이 고정이라면 테스트 안에 상수로 둬도 됩니다.
- `-PincludeE2e` 없이 `--tests '*E2ETest'`만 주면 "No tests found"로 **빌드가 실패**합니다. 태그로 빠졌다는 뜻이니 정상입니다.
- **진짜 DB에 흔적이 남습니다.** 테스트 전용 상품을 쓰고, 뺀 재고는 같은 테스트에서 복원하세요. 공용 시연 데이터로 돌리지 마세요.
- Eureka가 hub-ms를 아직 모르면(기동 직후) `RemoteServiceException`이 납니다. Eureka 대시보드(http://localhost:8761)에서 `HUB-MS`가 `UP`인지 먼저 보세요.
- 이 테스트는 **계약 확인용**이라 몇 개면 충분합니다. 지연·CB 같은 장애는 진짜 서비스로 만들기 어려우니 7.2(WireMock)에서 확인합니다.

**받는 쪽(hub-ms)에서 할 일:** 이 테스트가 통과하려면 hub-ms가 경로·필드 이름·오류 `code`를 약속대로 지켜야 합니다. 그래서 hub-ms도 자기 내부 API를 MockMvc로 고정해 둡니다([[05 통합 테스트 가이드]] 4.6). **보내는 쪽 7.2 + 받는 쪽 4.6이 평소의 계약 확인이고, 7.3은 둘이 정말 맞는지 가끔 실물로 맞춰 보는 단계**입니다.

### 7.4 [B] 수동: 서비스 전체를 띄워 확인

Eureka, user-ms, hub-ms, order-ms, Gateway를 띄운 뒤([[05 통합 테스트 가이드]] 6장) Postman으로 주문을 만들며 확인합니다.

| # | 하는 것 | 기대 | 어디서 보나 |
|---|---|---|---|
| 1 | 정상 주문 | 주문 성공, 재고 감소 | 응답, 재고 조회 API |
| 2 | 재고보다 많이 주문 | 주문 실패, 재고 그대로 | 응답 code, 재고 조회 |
| 3 | hub-ms 터미널 Ctrl+C 후 주문 | 주문 실패(재고 차감 안 됨), 응답이 오래 걸리지 않음 | 응답, order-ms 로그 |
| 4 | `kill -STOP <hub pid>` 후 주문 | 2초 뒤 시간 초과 → 같은 키로 1번 더 → 보상 후 실패 | order-ms 로그 (`RemoteTimeoutException[target=hub-ms]`) |
| 5 | 4 상태로 주문 5번 이상 | 이후 주문은 hub에 가지 않고 바로 실패 | `curl -s localhost:19094/actuator/circuitbreakers \| jq '.circuitBreakers \| map_values(.state)'` → `OPEN` |
| 6 | `kill -CONT <hub pid>` 후 10초 대기 | 다시 정상 | 같은 actuator → `CLOSED` |
| 7 | 한 요청을 끝까지 따라가기 | 같은 `X-Request-Id`가 hub-ms까지 전달 | 응답 헤더 `X-Request-Id` → Gateway 접근 로그, hub-ms에서 받은 헤더 |

- `<hub pid>`는 `jps -l`에서 `com.hub.HubMsApplication` 줄의 번호입니다.
- 4번이 Gateway에서 먼저 끊기지 않는지도 확인하세요. 주문 경로는 Gateway가 15초까지 기다립니다.

## 8. 하지 말 것

- `@FeignClient(url = "...")` 금지. 서비스가 **기동하지 않습니다**(`FeignClientUrlGuard`). 외부 API(Slack 등)는 `RestClient`로 부릅니다.
- Feign 호출을 `try { } catch (Exception e) { }`로 삼키지 않기. 3종을 구분해서 처리합니다.
- 직접 재시도 루프(`for`, `while`)를 만들지 않기. 실패가 쌓여 CB만 더 빨리 열립니다.
- 키 없는 POST/PATCH/DELETE에 `executeWithRetryOnce` 쓰지 않기. 두 번 처리될 수 있습니다.
- `@FeignClient`, `feign.*`를 domain·application에 두지 않기. infrastructure에만 둡니다.

## 9. 막히면

| 증상 | 확인 |
|---|---|
| 기동 시 `Feign 클라이언트에 고정 url이 있습니다` | `@FeignClient`나 `client.config.*.url`의 `url` 제거 |
| 상대가 401을 줘서 503이 남 | 호출 쪽에서 사용자 헤더를 직접 지정하다 하나만 넣었는지 확인 (자동 전달에 맡기기) |
| 계속 503, 상대 서비스는 멀쩡함 | CB가 `OPEN`인지 5장으로 확인. 10초 뒤 자동 회복 |
| actuator에 CB가 없음 | resilience4j 의존성 누락, 또는 아직 한 번도 호출 안 함 |

- 더 자세한 설명: [[02 장애 대응(Feign) 가이드]] (Saga 단계별 처리, 테스트 방법)
- 사용자 조회 내부 API 계약: [[User·Auth API 사용법]] 4장
