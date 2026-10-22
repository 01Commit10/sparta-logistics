# Company 사용법

> 작성자: 권우 · 기준: 현재 `company-ms` 구현 · 최종 확인: 2026-10-21
> 대상: Postman·curl로 업체·상품 API를 호출하거나, 업체·상품 조회 응답을 쓰는 서비스 개발자
> 경로·권한 요약은 [API 명세서](<../../design/04 API 명세서.md>), 정책은 [07 서비스 정책](<../../design/07 서비스 정책.md>) 4장에 있습니다. 이 문서는 **현재 코드 기준**입니다.

## 1. 사용할 수 있는 API

외부 요청은 Gateway `http://localhost:19090`으로 보내고, 헤더에 `Authorization: Bearer {accessToken}`을 넣습니다. (company-ms 직접 포트: 19091)

### 업체 `/api/v1/companies`

| 하고 싶은 것 | 요청 | 권한 | 성공 |
|---|---|---|---|
| 업체 등록 | `POST /api/v1/companies` | MASTER | 201 |
| 목록·검색 | `GET /api/v1/companies?hubId=&name=&page=&size=&sort=` | 로그인 | 200 |
| 상세 조회 | `GET /api/v1/companies/{companyId}` | 로그인 | 200 |
| 수정 | `PATCH /api/v1/companies/{companyId}` | MASTER·업체 관리자 본인 | 200 |
| 삭제 | `DELETE /api/v1/companies/{companyId}` | MASTER | 204 |

### 상품 `/api/v1/products`

| 하고 싶은 것 | 요청 | 권한 | 성공 |
|---|---|---|---|
| 상품 등록 | `POST /api/v1/companies/{companyId}/products` | MASTER·그 업체 관리자 | 201 |
| 목록·검색 | `GET /api/v1/products?companyId=&name=&page=&size=&sort=` | 로그인 | 200 |
| 업체별 목록 | `GET /api/v1/companies/{companyId}/products?page=&size=&sort=` | 로그인 | 200 |
| 상세 조회 | `GET /api/v1/products/{productId}` (**조회수 +1**) | 로그인 | 200 |
| 수정 | `PATCH /api/v1/products/{productId}` | MASTER·그 업체 관리자 | 200 |
| 삭제 | `DELETE /api/v1/products/{productId}` (**허브 재고도 정리**, 3장) | MASTER·그 업체 관리자 | 204 |

- "업체 관리자"는 `p_companies.manager_user_id == 로그인 사용자 ID`인 사용자입니다. 역할(`USER`)이나 소속 연결(`organization_id`)만으로는 권한이 생기지 않습니다.
- 삭제된 업체·상품은 조회·수정·삭제 모두 404입니다. **삭제된 업체의 상품**도 404이며 목록·검색에서 빠집니다. 업체를 삭제해도 상품 행은 따로 삭제하지 않습니다.

## 2. 요청 규칙

### 본문

| API | 필드 | 규칙 |
|---|---|---|
| 업체 등록 | `name`, `address`, `hubId`, `managerUserId` | 모두 필수. `name` ≤ 150자, `address` ≤ 255자, 공백만 불가 |
| 업체 수정 | 위 4개 중 보낸 것만 | 빈 본문 `{}`은 400. 보낸 `name`·`address`는 공백만 불가 |
| 상품 등록 | `name`, `description`(선택), `unitPrice` | `name` ≤ 150자. `unitPrice` 0 이상, 정수 13자리·소수 2자리까지 |
| 상품 수정 | 위 3개 중 보낸 것만 | 빈 본문 `{}`은 400 |

- **업체 등록·수정 시 원격 검증:** `hubId`는 hub-ms, `managerUserId`는 user-ms 내부 API로 존재를 확인합니다. 없으면 400 `COMPANY_HUB_NOT_FOUND` / `COMPANY_MANAGER_NOT_FOUND`.
- **업체 수정 중 `hubId`·`managerUserId` 변경은 MASTER만** 할 수 있습니다. 업체 관리자가 현재 값과 **같은 값**을 보내면 변경이 아니라서 통과하고, 원격 검증도 하지 않습니다.

### 목록·검색

| 파라미터 | 동작 |
|---|---|
| `hubId` / `companyId` | 검색 조건. 없거나 삭제된 허브·업체를 넣어도 404가 아니라 **빈 목록** |
| `name` | 부분 일치. 150자 초과는 400 |
| `page` | 0부터 |
| `size` | 1~100. 밖이면 10으로 바꿈(400 아님) |
| `sort` | 업체: `createdAt`·`name` / 상품: `createdAt`·`name`·`unitPrice`·`viewCount`. 그 외 400 `INVALID_SORT_PROPERTY`. 기본 `createdAt,desc`, 보조 정렬 `id` |

- 업체별 목록(`/companies/{companyId}/products`)은 경로의 업체가 없거나 삭제됐으면 **404**입니다. 검색의 `companyId` 조건(빈 목록)과 다릅니다.
- 인기 상품은 `sort=viewCount,desc`입니다.
- 응답 형식은 공통 `PageResponse`입니다 → [페이지네이션 사용법](<페이지네이션 사용법.md>)

## 3. 상품 삭제 흐름

```text
상품·소속 업체 확인 → 권한 확인
  → hub-ms DELETE /internal/v1/inventories/products/{productId}  (트랜잭션 밖)
  → 트랜잭션 안에서 상품을 다시 읽어 확인·권한 확인 → 상품 논리 삭제
```

- **재고를 먼저 지우는 이유:** hub-ms의 재고 정리 API는 멱등입니다(대상이 없거나 이미 삭제돼도 204). 그래서 원격 호출이 실패해도 상품이 남아 있으면 **같은 삭제 요청을 다시 보내면 됩니다**. 상품을 먼저 지우면 실패했을 때 다시 시도할 수 없고(삭제된 상품은 404) 재고만 남습니다.
- **트랜잭션 안에서 다시 읽는 이유:** 원격 호출을 기다리는 동안 상품·업체가 삭제되거나 관리자가 바뀌었을 수 있습니다. 업체 수정도 같은 이유로 원격 검증 뒤 다시 읽어서 저장합니다.

| hub-ms 호출 결과 | 상품 | 응답 | 대처 |
|---|---|---|---|
| 성공 (204) | 삭제됨 | 204 | - |
| 4xx 거절 | 남음 | hub-ms와 같은 상태·code | 원인 확인 |
| 5xx·연결 실패·CB 차단 | 남음 | 503 `SERVICE_UNAVAILABLE` | 잠시 뒤 같은 요청 재시도 |
| 타임아웃·504 (결과 모름) | 남음 | 504 `SERVICE_TIMEOUT` | 같은 요청 재시도 (재고 정리가 멱등이라 안전) |

예외 3종의 뜻은 [Feign 호출 사용법](<Feign 호출 사용법.md>)을 봅니다. 상품 삭제와 재고 등록이 동시에 들어오는 경우의 정합성은 아직 검토하지 않았습니다.

## 4. 오류 코드

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 본문·파라미터 검증 실패 (길이, 필수, 빈 수정 본문 등) |
| 400 | `INVALID_SORT_PROPERTY` | 허용하지 않은 정렬 필드 |
| 400 | `COMPANY_HUB_NOT_FOUND` | 업체 등록·수정의 `hubId` 허브가 없음 |
| 400 | `COMPANY_MANAGER_NOT_FOUND` | 업체 등록·수정의 `managerUserId` 사용자가 없거나 삭제됨 |
| 400 | `PRODUCT_INVALID_UNIT_PRICE` | 단가가 없거나 음수 (도메인 검사. 보통은 요청 검증에서 `INVALID_INPUT`으로 먼저 막힘) |
| 403 | `ACCESS_DENIED` | MASTER·업체 관리자가 아님, 또는 업체 관리자가 `hubId`·`managerUserId`를 바꾸려 함 |
| 404 | `COMPANY_NOT_FOUND` | 업체가 없거나 삭제됨. 상품 API에서는 **소속 업체가 삭제된 경우**도 |
| 404 | `PRODUCT_NOT_FOUND` | 상품이 없거나 삭제됨 |
| 503 / 504 | `SERVICE_UNAVAILABLE` / `SERVICE_TIMEOUT` | 업체 등록·수정의 원격 검증, 상품 삭제의 재고 정리 호출 실패 (3장) |

401·Gateway 단계 오류는 [Gateway 사용법](<Gateway 사용법.md>)을 봅니다.

## 5. 다른 서비스에서 조회 (내부 API)

| 요청 | 응답 필드 | 비고 |
|---|---|---|
| `GET /internal/v1/companies/{companyId}` | `id`, `name`, `address`, `hubId`, `managerUserId` | 없거나 삭제됨 404 `COMPANY_NOT_FOUND` |
| `GET /internal/v1/products/{productId}` | `id`, `companyId`, `name`, `unitPrice` | 없거나 삭제됨 404 `PRODUCT_NOT_FOUND`, 소속 업체 삭제 404 `COMPANY_NOT_FOUND`. **조회수를 올리지 않고 캐시도 안 씀**(주문 시점 단가) |

- `/internal/**`는 Gateway에서 막히므로 서비스끼리 Feign으로만 부릅니다. 따라 만드는 예제는 [예시 - 주문·배송에서 다른 도메인 조회](<예시 - 주문·배송에서 다른 도메인 조회.md>)에 있습니다.
- 응답 필드를 바꾸면 호출하는 서비스(order-ms, hub-ms)의 Feign DTO도 함께 바꿔야 합니다.

## 6. 조회수와 캐시 설정

상품 상세 조회수와 사용자 상품 조회 캐시는 `PRODUCT_CACHE_MODE`(`product.cache.mode`)로 단계를 고릅니다. 측정 결과와 단계 선택 이유는 [상품 조회 최적화 레포트](<../../report/상품 조회 최적화 레포트.md>), 측정 방법은 [상품 조회 성능 테스트 사용법](<상품 조회 성능 테스트 사용법.md>)에 있습니다.

| 모드 | 캐시 | 조회수 |
|---|---|---|
| `none` (기본) | 없음 | 조회마다 DB에서 바로 +1 |
| `redis` | Redis | Redis에 누적하고 `view-count.flush-interval`(기본 5초)마다 DB 반영 |
| `layered` | Caffeine(L1) + Redis(L2) | `redis`와 같음 |

| 설정 (`company-ms` application.yaml) | 기본값 | 뜻 |
|---|---|---|
| `product.cache.ttl.product-detail` | 5m | 상품 상세 캐시 |
| `product.cache.ttl.product-search` | 30s | 상품 목록·검색 캐시 |
| `product.cache.local.ttl` / `maximum-size` | 10s / 10000 | L1(서버 메모리) 캐시, `layered`에서만 |
| `product.view-count.flush-interval` | 5s | Redis 조회수 → DB 반영 주기 |

**캐시 모드에서 알아 둘 것**

- **상세 캐시**는 상품 수정·삭제 시 지웁니다. **목록·검색 캐시**는 지우지 않고 TTL(30초)로만 갱신하므로, 수정·삭제 직후 목록에 이전 값이 보일 수 있습니다.
- 응답의 `viewCount`는 Redis 누적값이라 정확합니다. 하지만 **DB의 `view_count`와 `sort=viewCount` 정렬은 반영 주기만큼 늦습니다**.
- Redis가 DB 반영 전에 사라지면 마지막 반영 이후의 조회수는 잃습니다(처리량을 우선한 결정).
- 캐시 모드는 Redis가 필요합니다. 내부 API·업체 API·업체별 상품 목록은 캐시하지 않습니다.
- 같은 사용자의 반복 조회, MASTER·업체 관리자의 조회도 모두 조회수에 셉니다(07 정책 4장).

## 7. 코드 구조와 테스트

company-ms 안에 **업체(`com.company.company`)와 상품(`com.company.product`) 두 도메인**이 있고, 둘 다 4계층(presentation / application / domain / infrastructure)을 따릅니다.

- 상품은 업체의 하위 리소스라 같은 서비스 안에서 `CompanyRepository`를 직접 씁니다(소속 업체 확인·권한). 다른 서비스 DB 접근이 아니므로 규칙 위반이 아닙니다.
- 원격 호출은 domain의 인터페이스(`HubClient`, `UserClient`, `InventoryClient`)를 infrastructure의 `*ClientImpl`이 `RemoteCallTemplate`으로 구현합니다. hub-ms·user-ms의 404만 "없음"으로 해석하고, 나머지 실패는 그대로 던집니다.
- 원격 호출은 **트랜잭션 밖**에서 합니다(응답을 기다리는 동안 DB 커넥션을 잡지 않도록).

| 테스트 위치 | 내용 |
|---|---|
| `fake/` | `FakeCompanyRepository`, `FakeProductRepository`, `FakeHubClient`, `FakeUserClient`, `FakeInventoryClient` — application 단위 테스트용 |
| `architecture/ArchitectureTest` | 계층 의존 방향 검증 |
| `*/infrastructure/persistence/*SchemaTest` | 컬럼·인덱스가 06 테이블 명세서와 맞는지 |
| `*/presentation/controller/Internal*IntegrationTest` | 내부 API 계약(응답 필드·404) |

## 하지 말 것

- 상품 삭제를 "상품 먼저 삭제 → 재고 정리"로 바꾸지 마세요. 원격 실패 시 다시 시도할 수 없게 됩니다(3장).
- 원격 검증·호출을 `@Transactional` 안으로 옮기지 마세요.
- 내부 API 응답에 외부 응답 DTO(`CompanyResponse`, `ProductResponse`)를 쓰지 마세요. 계약이 섞입니다.
- 주문 검증처럼 정확한 단가가 필요한 곳에서 캐시된 상품 조회(`ProductQueryService`)를 쓰지 마세요.
