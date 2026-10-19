# 4. API 명세서

**API Base URL:** `/api/v1`

**인증 방식:** JWT

**내부 서비스 통신:** `/internal/v1`

## Auth

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 회원가입 | POST | `/auth/signup` | 비회원 | 회원 및 소속 정보 (organizationType: COMPANY·HUB) | 201 |
| ID 중복 확인 | GET | `/auth/check-username` | 비회원 | username | 200 (available) |
| 로그인 | POST | `/auth/login` | 비회원 | username, password | 200 (accessToken, refreshToken, passwordChangeRequired) |
| 토큰 재발급 | POST | `/auth/reissue` | 비회원 | refreshToken | 200 (accessToken, refreshToken) |
| 로그아웃 | POST | `/auth/logout` | 로그인 | - | 204 |

*토큰 정책과 Gateway 검증 순서는 [[SYS-01 게이트웨이 인증 인가 설계]]를 따릅니다.*

## User

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 내 정보 조회 | GET | `/users/me` | 로그인 | - | 200 |
| 내 정보 수정 | PATCH | `/users/me` | 로그인 | slackId | 200 |
| 비밀번호 변경 | PATCH | `/users/me/password` | 로그인 | currentPassword, newPassword | 204 |
| 회원탈퇴 | DELETE | `/users/me` | 로그인 | password | 204 |
| 사용자 생성 | POST | `/users` | MASTER | 회원가입 항목 + role, isDeliveryManager (둘 다 필수, organizationType HQ는 MASTER만) | 201 (passwordChangeRequired=true: 첫 로그인 후 비밀번호 변경 강제) |
| 사용자 목록 조회 | GET | `/users` | MASTER | username, name, role, isDeliveryManager, organizationType, includeDeleted, page, size(1~100, 밖이면 10), sort(createdAt·username·name, 기본 createdAt 역순) | 200 (페이지) |
| 사용자 상세 조회 | GET | `/users/{userId}` | MASTER | userId (삭제된 사용자 포함) | 200 |
| 사용자 정보 수정 | PATCH | `/users/{userId}` | MASTER | name, slackId, organizationType, organizationName (부분 수정, 유형을 바꾸면 소속명 필수) | 200 |
| 사용자 권한 변경 | PATCH | `/users/{userId}/role` | MASTER | role (바뀌면 대상 토큰 무효) | 200 |
| 배송담당자 지정 | PATCH | `/users/{userId}/delivery-manager` | MASTER | isDeliveryManager (허브 소속만, 아니면 409) | 200 |
| 사용자 소속 연결 | PATCH | `/users/{userId}/organization` | MASTER | organizationType, organizationId (COMPANY·HUB만, 존재 확인 안 함), organizationName (유형을 바꾸면 필수) | 200 |
| 사용자 비활성화 | DELETE | `/users/{userId}` | MASTER | userId (본인은 409, 대상 토큰 무효) | 204 |
| 사용자 재활성화 | POST | `/users/{userId}/restore` | MASTER | userId (비밀번호·역할 그대로) | 200 |
| 비밀번호 초기화 | POST | `/users/{userId}/password-reset` | MASTER | userId (오프라인 문의 후 실행, 본인은 409) | 200 (userId, username, temporaryPassword: 1회만 노출, 대상 토큰 무효, 다음 로그인 후 변경 강제) |

*관리자 API의 오류 코드와 예시는 [[User·Auth API 사용법]] 2.1, Postman 57~90번을 봅니다.*

## Hub

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 허브 목록 조회 | GET | `/hubs` | 로그인 | 페이지 조건 | 200 |
| 허브 상세 조회 | GET | `/hubs/{hubId}` | 로그인 | hubId | 200 |
| 허브 정보 수정 | PATCH | `/hubs/{hubId}` | MASTER | name, address | 200 |

*허브는 사전 정의된 17개를 사용하므로 일반 생성·삭제 API는 제외했습니다.*

## Hub Route

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 허브 경로 목록 | GET | `/hub-routes` | 로그인 | 출발·도착 허브 | 200 |
| 허브 경로 상세 | GET | `/hub-routes/{routeId}` | 로그인 | routeId | 200 |
| 허브 경로 등록 | POST | `/hub-routes` | MASTER | 출발·도착 허브, 거리, 시간 | 201 |
| 허브 경로 수정 | PATCH | `/hub-routes/{routeId}` | MASTER | 거리, 소요시간 | 200 |
| 허브 경로 삭제 | DELETE | `/hub-routes/{routeId}` | MASTER | routeId | 204 |

## Inventory

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 재고 목록 조회 | GET | `/inventories` | 관리자·허브담당 | hubId, productId | 200 |
| 재고 상세 조회 | GET | `/inventories/{inventoryId}` | 관리자·허브담당 | inventoryId | 200 |
| 재고 신규 등록 | POST | `/inventories` | 관리자·허브담당 | hubId, productId, initialQuantity | 201 |
| 재고 수량 증가 | POST | `/inventories/{inventoryId}/increase` | 관리자·허브담당 | quantity, Idempotency-Key | 200 |
| 재고 변동 기록 조회 | GET | `/inventories/{inventoryId}/movements` | 관리자·허브담당 | 페이지·변경 유형 | 200 |

*재고 차감·복원은 주문 Saga에서 호출하는 내부 API로 분리합니다.*

## Company

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 업체 목록 조회 | GET | `/companies` | 로그인 | hubId, name | 200 |
| 업체 상세 조회 | GET | `/companies/{companyId}` | 로그인 | companyId | 200 |
| 업체 등록 | POST | `/companies` | MASTER | name, address, hubId, managerUserId | 201 |
| 업체 수정 | PATCH | `/companies/{companyId}` | MASTER·업체관리자 | 업체 변경 정보 | 200 |
| 업체 삭제 | DELETE | `/companies/{companyId}` | MASTER | companyId | 204 |

## Product

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 상품 목록 조회 | GET | `/products` | 로그인 | companyId, name | 200 |
| 상품 상세 조회 | GET | `/products/{productId}` | 로그인 | productId | 200 |
| 업체별 상품 조회 | GET | `/companies/{companyId}/products` | 로그인 | companyId | 200 |
| 상품 등록 | POST | `/companies/{companyId}/products` | MASTER·업체관리자 | name, description, unitPrice | 201 |
| 상품 수정 | PATCH | `/products/{productId}` | MASTER·업체관리자 | 상품 변경 정보 | 200 |
| 상품 삭제 | DELETE | `/products/{productId}` | MASTER·업체관리자 | productId | 204 |

## Order

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 주문 생성 | POST | `/orders` | 로그인·소속확인 | 업체, 상품, 수량, 요청사항, Idempotency-Key | 202 |
| 주문 목록 조회 | GET | `/orders` | 로그인 | status, companyId | 200 |
| 주문 상세 조회 | GET | `/orders/{orderId}` | 주문 관계자 | orderId | 200 |
| 주문 취소 | POST | `/orders/{orderId}/cancel` | 주문권한자·MASTER | 사유, Idempotency-Key | 202 |
| 주문 처리 기록 조회 | GET | `/orders/{orderId}/histories` | 주문 관계자 | orderId | 200 |

*주문 생성 시 Company Service에서 상품 단가를 확인하여* *`unit_price_snapshot`**에 저장하고, 주문 수량을 기준으로* *`total_amount`**를 계산합니다. 주문 조회 응답에는 주문 당시 단가와 총금액을 포함합니다. 주문 생성은 Saga를 시작하며, 재고 차감과 배송 생성이 완료될 때까지* *`PROCESSING`* *상태를 유지합니다.*

## Delivery

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 배송 목록 조회 | GET | `/deliveries` | 관리자·관계자 | 상태·담당자 검색 | 200 |
| 배송 상세 조회 | GET | `/deliveries/{deliveryId}` | 관리자·관계자 | deliveryId | 200 |
| 주문별 배송 조회 | GET | `/deliveries/by-order/{orderId}` | 관리자·관계자 | orderId | 200 |
| 배송담당자 배정 | PATCH | `/deliveries/{deliveryId}/manager` | 관리자·배정권한자 | deliveryManagerId | 200 |
| 배송 상태 변경 | PATCH | `/deliveries/{deliveryId}/status` | 담당배송자·MASTER | deliveryStatus, Idempotency-Key | 200 |
| 배송 처리 기록 조회 | GET | `/deliveries/{deliveryId}/histories` | 관리자·관계자 | deliveryId | 200 |

*배송 생성은 Order Service가 호출하는 내부 API로 관리합니다.*

## Delivery Route

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 배송 구간 조회 | GET | `/deliveries/{deliveryId}/routes` | 관리자·관계자 | deliveryId | 200 |
| 배송 경로 구성 | POST | `/deliveries/{deliveryId}/routes` | 관리자·배정권한자 | 배송 구간 목록 | 201 |
| 배송 구간 상태 변경 | PATCH | `/delivery-routes/{routeId}/status` | 담당배송자·MASTER | routeStatus, Idempotency-Key | 200 |
| 배송 구간 실측 정보 수정 | PATCH | `/delivery-routes/{routeId}/metrics` | 담당배송자·MASTER | 실제 거리·소요시간 | 200 |

---

## Internal API (Feign)

**Base URL:** `/internal/v1`

| 기능 | Method | Endpoint | 호출 목적 |
|---|---|---|---|
| 사용자 검증 | GET | `/users/{userId}` | 사용자·배송담당자 확인. 응답: id, name, role, isDeliveryManager, slackId, active. 삭제된 사용자는 404 |
| 허브 검증 | GET | `/hubs/{hubId}` | 활성 허브면 200(id, name, address), 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND |
| 상품 검증 | GET | `/products/{productId}` | 상품·공급업체 확인 |
| 업체 검증 | GET | `/companies/{companyId}` | 업체·관리 허브 확인 |
| 재고 차감 | POST | `/inventories/decrease` | 주문 시 원자적 재고 차감 |
| 재고 복원 | POST | `/inventories/restore` | Saga 실패 시 보상 트랜잭션 |
| 상품 재고 정리 | DELETE | `/inventories/products/{productId}` | 상품의 모든 허브 재고를 논리 삭제. 없음·이미 삭제도 204, 수량·Operation·History 보존 |
| 허브 경로 조회 | GET | `/hub-routes?departureHubId={id}&arrivalHubId={id}` | 200(id, departureHubId, arrivalHubId, distanceKm, durationMin). 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND |
| 배송 생성 | POST | `/deliveries` | 주문에 연결된 배송 생성 |
| 배송 취소 | POST | `/deliveries/{deliveryId}/cancel` | 주문 취소 시 배송·구간 취소 (Saga 보상) |
| 주문 완료 처리 | POST | `/orders/{orderId}/complete` | Saga 완료 상태 반영 |
| 주문 실패 처리 | POST | `/orders/{orderId}/fail` | Saga 실패 상태 반영 |

*내부 API는 일반 사용자에게 공개하지 않으며 서비스 간 인증이 필요합니다.*

**Hub 구현 참고(2026-10-19):** 위 허브 검증·경로 조회·상품 재고 정리 API를 제공합니다. 상세 필드·감사 작업자·현재 접근 통제와 Company 연동 범위는 [Hub-MS Feign 연동 사용법](<../guide/사용법/Hub-MS Feign 연동 사용법.md>)을 참고합니다. Hub의 별도 서비스 호출자 인증과 Company의 상품 삭제 후 Hub 호출은 아직 연결되어 있지 않습니다.

*주문 완료·실패 처리 API는 비동기 Saga에서만 필요합니다. Order Service가 동기로 오케스트레이션하는 초기 구현에서는 생략할 수 있습니다.*

---

## 공통 API 설계 규칙

| 항목 | 규칙 |
|---|---|
| API 버전 | `/api/v1` |
| 인증 | JWT |
| 요청 검증 | Bean Validation |
| 조회 페이지네이션 | page(0부터), size(1~100, 기본 10), sort(기본 createdAt,desc) → [[SYS-08 페이지네이션 정렬 설계]] |
| PK | UUID |
| 데이터 삭제 | Soft Delete |
| 중복 요청 방지 | Idempotency-Key |
| 재고 동시성 | 조건부 UPDATE 또는 잠금 |
| 분산 트랜잭션 | Saga 패턴 |
| 서비스 간 통신 | OpenFeign / HTTP |
| 외부 API 진입점 | API Gateway |
| 내부 API 보호 | 서비스 인증 및 네트워크 접근 제한 |

**주의:** 경로, DTO, 권한 및 응답 코드는 예상 설계입니다. 실제 구현 전 확정해야 하며, 특히 배송 상태값과 주문 취소 가능 조건은 별도의 비즈니스 규칙이 필요합니다.
