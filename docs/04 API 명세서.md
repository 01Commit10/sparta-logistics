# 4. API 명세서

**API Base URL:** `/api/v1`

**인증 방식:** JWT

**내부 서비스 통신:** `/internal/v1`

## Auth

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 회원가입 | POST | `/auth/signup` | 비회원 | 회원 및 소속 정보 | 201 |
| 로그인 | POST | `/auth/login` | 비회원 | username, password | 200 |

## User

| 기능 | Method | Endpoint | 권한 | 요청 | 응답 |
|---|---|---|---|---|---|
| 내 정보 조회 | GET | `/users/me` | 로그인 | - | 200 |
| 내 정보 수정 | PATCH | `/users/me` | 로그인 | name, slackId | 200 |
| 사용자 목록 조회 | GET | `/users` | MASTER | 검색·페이지 조건 | 200 |
| 사용자 상세 조회 | GET | `/users/{userId}` | MASTER | userId | 200 |
| 사용자 권한 변경 | PATCH | `/users/{userId}/role` | MASTER | role | 200 |
| 배송담당자 지정 | PATCH | `/users/{userId}/delivery-manager` | MASTER | isDeliveryManager | 200 |
| 사용자 소속 연결 | PATCH | `/users/{userId}/organization` | MASTER | organizationType, organizationUuid | 200 |
| 사용자 비활성화 | DELETE | `/users/{userId}` | MASTER | userId | 204 |

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
| 사용자 검증 | GET | `/users/{userId}` | 사용자·배송담당자 확인 |
| 허브 검증 | GET | `/hubs/{hubId}` | 허브 존재 여부 확인 |
| 상품 검증 | GET | `/products/{productId}` | 상품·공급업체 확인 |
| 업체 검증 | GET | `/companies/{companyId}` | 업체·관리 허브 확인 |
| 재고 차감 | POST | `/inventories/decrease` | 주문 시 원자적 재고 차감 |
| 재고 복원 | POST | `/inventories/restore` | Saga 실패 시 보상 트랜잭션 |
| 배송 생성 | POST | `/deliveries` | 주문에 연결된 배송 생성 |
| 주문 완료 처리 | POST | `/orders/{orderId}/complete` | Saga 완료 상태 반영 |
| 주문 실패 처리 | POST | `/orders/{orderId}/fail` | Saga 실패 상태 반영 |

*내부 API는 일반 사용자에게 공개하지 않으며 서비스 간 인증이 필요합니다.*

---

## 공통 API 설계 규칙

| 항목 | 규칙 |
|---|---|
| API 버전 | `/api/v1` |
| 인증 | JWT |
| 요청 검증 | Bean Validation |
| 조회 페이지네이션 | page, size |
| PK | UUID |
| 데이터 삭제 | Soft Delete |
| 중복 요청 방지 | Idempotency-Key |
| 재고 동시성 | 조건부 UPDATE 또는 잠금 |
| 분산 트랜잭션 | Saga 패턴 |
| 서비스 간 통신 | OpenFeign / HTTP |
| 외부 API 진입점 | API Gateway |
| 내부 API 보호 | 서비스 인증 및 네트워크 접근 제한 |

**주의:** 경로, DTO, 권한 및 응답 코드는 예상 설계입니다. 실제 구현 전 확정해야 하며, 특히 배송 상태값과 주문 취소 가능 조건은 별도의 비즈니스 규칙이 필요합니다.

|  도메인  |  Method  |  Endpoint  |  기능  |  권한  |  응답  |
|---|---|---|---|---|---|
| 사용자 | `POST` | `/api/users/signup` | 회원가입 | 공개 | `201` |
| 사용자 | `POST` | `/api/users/login` | 로그인·JWT 발급 | 공개 | `200` |
| 사용자 | `POST` | `/api/users/refresh` | Access Token 재발급 | Refresh Token 필요 | `200` |
| 메뉴 | `POST` | `/api/menus` | 메뉴 등록 | OWNER | `201` |
| 메뉴 | `GET` | `/api/menus` | 메뉴 목록 조회 | 공개 | `200` |
| 메뉴 | `GET` | `/api/menus/{menuId}` | 메뉴 상세 조회 | 공개 | `200` |
| 메뉴 | `PUT` | `/api/menus/{menuId}` | 본인 메뉴 수정 | OWNER | `200` |
| 메뉴 | `DELETE` | `/api/menus/{menuId}` | 본인 메뉴 논리 삭제 | OWNER | `204` |
| 주문 | `POST` | `/api/orders` | 주문 생성 | CUSTOMER | `201` |
| 주문 | `GET` | `/api/orders` | 사용자별 주문 목록 | 로그인 필요 | `200` |
| 주문 | `PATCH` | `/api/orders/{orderId}/cancel` | 본인 주문 취소 | CUSTOMER | `204` |
| 주문 | `PATCH` | `/api/orders/{orderId}/status` | 주문 수락·배달 완료 | OWNER | `204` |
| 결제 | `POST` | `/api/orders/{orderId}/payments` | 카드 결제 처리 | CUSTOMER | `201` |
