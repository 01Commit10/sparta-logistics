# Hub-MS Feign 연동 사용법

> 기준: `feature/hubms-internal-api`의 현재 Controller·DTO · 확인: 2026-10-19
> 대상: Order·Delivery 등에서 Hub를 호출하는 개발자. 아래 코드는 소비자 서비스에 작성할 예시이며, 서비스 간 실제 Feign 통신 검증과 구분합니다.

## 1. 현재 호출 계약

| 목적 | 메서드·경로 | 전달값 | 성공 응답 |
|---|---|---|---|
| 허브 유효성 검증 | `GET /internal/v1/hubs/{hubId}` | Path: `hubId` | 활성 허브는 200, 없거나 삭제됐으면 404 |
| 재고 조회 | `GET /api/v1/inventories` | Query: `hubId`, `productId` (각각 선택) | 200, 재고 배열 |
| 주문 재고 차감 | `POST /internal/v1/inventories/decrease` | Header: `Idempotency-Key`, JSON: `orderId`, `hubId`, `productId`, `quantity` | 200, 차감 결과 객체 |
| 허브 간 경로 조회 | `GET /internal/v1/hub-routes` | Query: `departureHubId`, `arrivalHubId` (둘 다 필수) | 200, 경로 객체 |
| 상품 삭제에 따른 재고 정리 | `DELETE /internal/v1/inventories/products/{productId}` | Path: `productId`, 사용자 헤더는 아래 감사 규칙 참고 | 204, 본문 없음. 재고 없음·이미 삭제도 동일 |

재고 조회는 두 조건을 모두 주면 AND 검색하며, 두 조건이 없으면 전체 조회합니다. 조회 결과가 없으면 빈 배열 `[]`입니다. 조회 수량은 참고값이며, 이후 차감 성공을 보장하지 않습니다. Order는 차감 성공 응답을 확인한 뒤 Delivery 생성을 요청합니다. Delivery가 별도로 재고 차감 여부를 조회하는 API는 현재 없습니다.

## 2. Feign에 어떻게 적나

**DB 컬럼명이 아니라 HTTP 계약의 이름을 맞춥니다.** `@RequestParam("hubId") UUID hubId`는 인자로 받은 UUID를 `?hubId=값`으로 전달한다는 뜻입니다. `hub_id`라고 바꾸면 현재 조회 계약과 다릅니다. Java 메서드명·DTO 클래스명은 소비자 서비스에서 정할 수 있지만, 경로·HTTP 메서드·필드 이름·타입·배열/객체 구조는 일치해야 합니다.

| 전달 위치 | Feign 표기 | 이 API의 예 |
|---|---|---|
| URL의 검색 조건 | `@RequestParam` | `hubId`, `productId`, `departureHubId`, `arrivalHubId` |
| URL의 경로 변수 | `@PathVariable` | `/{hubId}`, `/products/{productId}` |
| HTTP 헤더 | `@RequestHeader` | `Idempotency-Key` |
| JSON 본문 | `@RequestBody` + 요청 DTO | 차감 요청의 주문·허브·상품·수량 |

호출하는 서비스의 `infrastructure/client`에 다음처럼 정의합니다. 반환 타입은 Hub 엔티티·응용 DTO를 직접 공유하지 않고 소비자 측 통신 DTO로 만듭니다.

```java
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "hub-ms")
public interface HubFeignClient {

    @GetMapping("/internal/v1/hubs/{hubId}")
    HubResponse getHub(@PathVariable("hubId") UUID hubId);

    @GetMapping("/api/v1/inventories")
    List<InventoryResponse> getInventories(
            @RequestParam(value = "hubId", required = false) UUID hubId,
            @RequestParam(value = "productId", required = false) UUID productId
    );

    @PostMapping(value = "/internal/v1/inventories/decrease",
            consumes = "application/json")
    DecreaseResponse decrease(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody DecreaseRequest request
    );

    @GetMapping("/internal/v1/hub-routes")
    RouteResponse getRoute(
            @RequestParam("departureHubId") UUID departureHubId,
            @RequestParam("arrivalHubId") UUID arrivalHubId
    );

    @DeleteMapping("/internal/v1/inventories/products/{productId}")
    void deleteProductInventories(@PathVariable("productId") UUID productId);

    record HubResponse(UUID id, String name, String address) {}

    record InventoryResponse(UUID id, UUID hubId, UUID productId, int quantity) {}

    record DecreaseRequest(UUID orderId, UUID hubId, UUID productId, int quantity) {}

    record DecreaseResponse(
            UUID operationId, UUID orderId, String status,
            UUID inventoryId, int changedQuantity, int remainingQuantity
    ) {}

    record RouteResponse(
            UUID id, UUID departureHubId, UUID arrivalHubId,
            BigDecimal distanceKm, int durationMin
    ) {}
}
```

- 호출 측은 OpenFeign 의존성과 `@EnableFeignClients` 스캔 범위를 설정합니다. Eureka에 `hub-ms`로 등록된 인스턴스를 호출하며 고정 `url`은 지정하지 않습니다. `/internal/**`는 Gateway에서 차단하므로 Gateway 주소로 호출하지 않습니다.
- 재고의 공개 GET은 `X-User-Id`가 필요하고, 내부 허브·경로 조회와 재고 정리는 사용자 헤더를 필수로 요구하지 않습니다. 사용자 요청 안의 동기 Feign 호출에서는 common 인터셉터가 검증한 `X-User-Id`·`X-User-Role`·`X-Request-Id`를 이어 보냅니다. 요청 밖·다른 스레드에서도 자동 생성된다고 가정하지 않습니다.
- 차감의 `Idempotency-Key`는 호출 측에서 명시합니다. 길이는 1~120자이고 공백만인 값은 허용하지 않습니다. 주문·허브·상품 ID는 UUID, 수량은 양수입니다.
- 소비자 응용 서비스는 자신의 외부 통신 계약에 의존하고, infrastructure Adapter에서 Feign을 `remote.execute("hub-ms", () -> client.decrease(key, request))`로 감싼 뒤 자신의 결과 모델로 변환합니다. 준비 방법은 [Feign 호출 사용법](<Feign 호출 사용법.md>)을 참고합니다.

내부 허브 조회는 `{ "id": "UUID", "name": "허브명", "address": "주소" }`, 내부 경로 조회는 다음 구조의 객체 하나를 반환합니다. 출발·도착 파라미터 누락은 400 `INVALID_INPUT`, 허브·경로의 미존재·논리 삭제는 404 `RESOURCE_NOT_FOUND`입니다.

```json
{
  "id": "e43b2eeb-da7e-5043-882b-dcd29a017942",
  "departureHubId": "10000000-0000-4000-8000-000000000001",
  "arrivalHubId": "10000000-0000-4000-8000-000000000002",
  "distanceKm": 43.04,
  "durationMin": 49
}
```

DB·응용 DTO의 `fromHubId/toHubId`를 내부 응답 DTO에서만 `departureHubId/arrivalHubId`로 매핑합니다. 기존 공개 `/api/v1/hub-routes/fromtosearch`의 파라미터·응답은 `fromHubId/toHubId`를 유지합니다.

상품 삭제용 DELETE는 **그 상품의 모든 허브에 있는 활성 재고**에 삭제·수정 시각과 작업자를 기록하며, 수량·Operation·History를 보존합니다. 이미 삭제된 행은 수정하지 않으므로 재호출 시 최초 삭제 정보가 유지됩니다. 사용자 헤더가 있으면 해당 사용자를, 없으면 `AuthContract.SYSTEM_USER_ID`를 삭제자로 기록합니다. 이 UUID는 감사 기록용이며 호출자 인증 수단이 아닙니다.

이 DELETE는 Company-MS의 상품 삭제에 따른 정리 명령입니다. MASTER·업체 매니저가 재고를 직접 삭제하는 사용자용 API와 구분합니다. **현재 Company의 상품 삭제 코드에는 Hub 호출이 연결되어 있지 않습니다.** Company 담당자가 상품 삭제 권한 검증과 호출·실패 시 재처리를 연결해야 합니다. 상품 삭제와 재고 등록이 동시에 진행되는 경우의 전체 서비스 정합성도 그 흐름에서 검토해야 합니다.

현재 공통 정책상 `/internal/**`는 Gateway에서 차단하지만, Hub의 별도 서비스 호출자 인증은 아직 구현되어 있지 않습니다. 이 경로만으로 접근 통제가 완성되는 것은 아니며, 내부 API는 허가된 서비스만 접근 가능한 환경을 전제로 합니다.

## 3. 차감 응답 해석과 현재 한계

재고 100개에서 30개를 차감한 경우의 예시입니다. UUID는 설명용이며 실제 응답의 작업·주문·재고 ID는 해당 요청에 따라 달라집니다.

```json
{
  "operationId": "60000000-0000-4000-8000-000000000001",
  "orderId": "70000000-0000-4000-8000-000000000001",
  "status": "SUCCEEDED",
  "inventoryId": "50000000-0000-4000-8000-000000000001",
  "changedQuantity": -30,
  "remainingQuantity": 70
}
```

- 200은 재고 변경·Operation·History가 함께 커밋된 정상 차감 결과입니다. `remainingQuantity`는 해당 작업 직후의 값이며, 이후 다른 주문으로 바뀔 수 있습니다.
- 현재 대상 없음·수량 부족·논리 삭제는 차감을 적용하지 않고 400 `INVALID_INPUT`으로 묶어 응답합니다. 원인별 오류 구분과 `REJECTED` 기록은 후속 구현입니다.
- **키 UNIQUE는 있지만 동일 요청의 기존 결과 재응답은 아직 미구현입니다.** 현재 차감에는 `executeWithRetryOnce`를 붙이지 않습니다. 타임아웃·응답 변환 실패는 처리 결과가 불명확할 수 있으며, 작업 결과 조회·안전한 재요청·복원 계약을 완성해야 합니다. 5xx라는 이유만으로 미처리를 단정하지 않습니다.
- 복원·작업 결과 조회·재고 증가 API와 내부 서비스 호출자 검증은 후속 구현입니다. 이 문서는 현재 HTTP 연결 계약이며 주문 Saga 연동 전체가 완료되었다는 의미는 아닙니다.

## 4. 왜 재고 테이블을 세 개로 나누나

| 테이블 | 답하는 질문 | 30개 차감 예시 |
|---|---|---|
| `p_inventories` | 지금 몇 개 남았나? | `quantity = 70` |
| `p_inventory_operations` | 이 요청은 무엇이고 어떤 결과가 났나? | 주문·요청 키·요청 수량 30·성공·당시 잔량 70 |
| `p_inventory_histories` | 실제로 얼마나 바뀌었나? | 작업 ID·재고 ID·주문 ID·증감 -30·변경 후 70 |

Inventory만 있으면 현재 70개라는 사실로는 특정 주문의 차감 여부를 알 수 없습니다. 응답 유실 후 다시 요청할 때 중복 차감을 판단하거나, 나중에 정확한 원본 수량을 복원할 근거가 부족합니다.

History만 추가해도 성공한 변경의 추적과 중복 판단 근거를 둘 수 있습니다. **Operation을 따로 둔 이유는 재고가 바뀌지 않은 거절 요청도 결과를 보존하고, 요청 재전달에 같은 결과를 돌려줄 책임을 실제 수량 변경 기록과 구분하기 위해서입니다.** 예를 들어 재고 부족으로 거절된 요청은 향후 Operation에 `REJECTED`로 기록하되 History는 만들지 않는 설계입니다. 현재는 성공 기록만 구현되어 있습니다.

세 테이블이 유일한 정답은 아닙니다. 성공 변경만 추적하면 두 테이블로도 설계할 수 있습니다. 이 프로젝트는 Order 재시도·응답 유실·Saga 보상까지 다루므로 요청·결과 저장을 분리합니다. 저장량과 INSERT가 늘어나는 비용은 있으며, 지금 구조는 성능 향상이 아니라 정합성과 복구 근거 확보를 위한 선택입니다.

## 5. 구현 확인 위치

- 재고 조회·등록: [InventoryController](../../../hub-ms/src/main/java/com/hub/inventory/presentation/controller/InventoryController.java)
- 내부 차감 계약: [DecreaseInventoryController](../../../hub-ms/src/main/java/com/hub/inventory/presentation/controller/DecreaseInventoryController.java), [요청 DTO](../../../hub-ms/src/main/java/com/hub/inventory/presentation/dto/DecreaseInventoryRequestDto.java), [응답 DTO](../../../hub-ms/src/main/java/com/hub/inventory/presentation/dto/DecreaseInventoryResponseDto.java)
- 경로 조회: [HubRouteController](../../../hub-ms/src/main/java/com/hub/hubroute/presentation/controller/HubRouteController.java), [Hub Route 사용법](<Hub Route 사용법.md>)
- 내부 API: [HubInternalController](../../../hub-ms/src/main/java/com/hub/hub/presentation/controller/HubInternalController.java), [HubRouteInternalController](../../../hub-ms/src/main/java/com/hub/hubroute/presentation/controller/HubRouteInternalController.java), [InventoryInternalController](../../../hub-ms/src/main/java/com/hub/inventory/presentation/controller/InventoryInternalController.java)
- 테이블·제약조건: [ERD](<../../design/05 ERD.md>), [테이블 명세서](<../../design/06 테이블 명세서.md>)
- 실제 DB 차감·기록·롤백 검증: [InventoryStockIntegrationTest](../../../hub-ms/src/test/java/com/hub/inventory/InventoryStockIntegrationTest.java). Order·Delivery에서 이 Feign 예시를 호출하는 통합 테스트는 별도 확인 대상입니다.
- 내부 3종 API의 HTTP·PostgreSQL 동작: [HubInternalApiIntegrationTest](../../../hub-ms/src/test/java/com/hub/HubInternalApiIntegrationTest.java). 실제 Company·Delivery 서비스 간 Feign 호출은 별도 검증 대상입니다.
