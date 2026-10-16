# Sparta Logistics

> 전국 17개 허브 센터를 거점으로 업체 간 주문을 받아, 허브를 거쳐 배송하는 **B2B 물류 관리·배송 시스템**입니다.
> Spring Cloud 기반 MSA로 서비스를 나누고, Gateway 인증·인가, Eureka 서비스 발견, OpenFeign 서비스 간 통신, 서비스별 스키마 분리를 적용했습니다.

## 목차

1. [프로젝트 목적](#1-프로젝트-목적)
2. [팀원 역할분담](#2-팀원-역할분담)
3. [기술 스택](#3-기술-스택)
4. [서비스 구성](#4-서비스-구성)
5. [실행 방법](#5-실행-방법)
6. [ERD](#6-erd)
7. [API](#7-api)
8. [핵심 설계](#8-핵심-설계)
9. [구현 현황](#9-구현-현황)
10. [문서 안내](#10-문서-안내)

---

## 1. 프로젝트 목적

| 항목 | 내용 |
|---|---|
| 주제 | 업체(공급·수령) 간 주문 → 허브 재고 차감 → 허브 간 이동(P2P) → 업체 배송까지 관리하는 물류 플랫폼 |
| 목표 | ① Spring Cloud 기반 MSA로 서비스 경계와 데이터 소유권을 나눈다 ② OpenFeign으로 서비스 간 API를 연동하고, 실패 시 보상(롤백)으로 데이터 정합성을 지킨다 ③ Gateway 한 곳에서 인증·인가를 처리한다 |
| 기간 | 2026-10-13 ~ 2026-10-21 |

**주요 도메인**

- **사용자**: 역할은 `MASTER`(관리자)·`USER`(일반) 2단계. 배송담당자는 역할이 아니라 `isDeliveryManager` 속성으로 부여합니다.
- **허브**: 전국 17개 허브와 허브 간 P2P 이동 경로, 허브별 상품 재고와 변동 이력.
- **업체·상품**: 업체는 관리 허브에 속하고, 상품은 업체에 속합니다.
- **주문**: 공급 업체 → 수령 업체 주문. 재고 차감·배송 생성을 조정(Saga)하고 실패하면 보상합니다.
- **배송**: 주문 1건당 배송 1건, 허브 간 구간(`HUB_TO_HUB`)과 최종 배송 구간(`HUB_TO_COMPANY`)으로 나눠 기록합니다.

## 2. 팀원 역할분담

| 이름 | 담당 | 주요 작업 |
|---|---|---|
| 대진 | User/Auth Service, Gateway, 공통(인증·장애 대응·알림) | 회원·인증 API, JWT 발급·재발급·로그아웃(Redis 토큰 상태), Gateway 라우팅·JWT 인증·경로 인가·타임아웃·Circuit Breaker, common 인증 헤더(`@CurrentUser`)·Feign 장애 대응·Slack 알림 |
| 이찬 | Hub/Inventory Service | 허브 17개·P2P 경로 시드, 허브·경로 조회, 재고 등록·차감·복원(조건부 UPDATE, 멱등성) |
| 권우 | Company/Product Service, 공통 인프라 | 업체·상품 API, 프로젝트 골격, Eureka, Docker Compose, common(`BaseEntity`·공통 응답·예외) |
| 다울 | Order Service | 주문 생성 Saga, 실패 보상·주문 취소, 주문 완료 Slack 알림 연동 |
| 영은 | Delivery Service | 배송·구간 자동 생성, 배송 상태 전이, 배송담당자 확인 |

## 3. 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16, Spring Data JPA, Validation, spring-security-crypto(BCrypt) |
| MSA | Spring Cloud 2025.0.3 — Gateway(WebFlux), Netflix Eureka, OpenFeign, LoadBalancer, Resilience4j |
| Auth | JWT (jjwt 0.12.6), Redis(Refresh Token·로그아웃·토큰 무효화 상태) |
| Database | PostgreSQL 18 (컨테이너 1개, 서비스별 스키마 5개), Redis 7.4 |
| Build / Infra | Gradle 멀티 모듈, Docker, Docker Compose |
| Test | JUnit 5, Mockito, H2, WireMock, Testcontainers, Postman |
| Notification | Slack Incoming Webhook |
| Collaboration | Git, GitHub |

## 4. 서비스 구성

### 4.1 아키텍처

```mermaid
flowchart TB
    client([Client])
    slack[[Slack Webhook]]

    gateway["gateway-edge :19090<br/>JWT 인증 · 경로 인가 · 라우팅<br/>Timeout · Circuit Breaker"]
    eureka["eureka-registry :8761<br/>Service Discovery"]
    redis[("Redis<br/>토큰 상태")]

    subgraph services["Business Services"]
        direction LR
        user["user-ms :19095<br/>회원 · 인증"]
        hub["hub-ms :19093<br/>허브 · 경로 · 재고"]
        company["company-ms :19091<br/>업체 · 상품"]
        order["order-ms :19094<br/>주문 · Saga"]
        delivery["delivery-ms :19092<br/>배송 · 구간"]
    end

    subgraph db["PostgreSQL (1 container · 5 schemas)"]
        direction LR
        s_auth[(auth)]
        s_hub[(hub)]
        s_company[(company)]
        s_orders[(orders)]
        s_delivery[(delivery)]
    end

    client --> gateway
    gateway -- "lb://서비스명" --> services
    gateway -. "로그아웃 여부 확인" .-> redis
    user -. "Refresh Token 저장" .-> redis
    gateway & services -. "등록 · 조회" .-> eureka

    order -- "Feign" --> company & hub & delivery
    delivery -- "Feign" --> hub & user

    user --> s_auth
    hub --> s_hub
    company --> s_company
    order --> s_orders
    delivery --> s_delivery

    order -. "주문 완료 알림" .-> slack
```

### 4.2 모듈

| 모듈 | 포트 | 역할 | 스키마 |
|---|---|---|---|
| `eureka-registry` | 8761 | 서비스 등록·발견 | - |
| `gateway-edge` | 19090 | 외부 요청 단일 진입점. JWT 검증, MASTER 경로 인가, `X-User-*` 헤더 주입, 라우트별 타임아웃·Circuit Breaker | - |
| `user-ms` | 19095 | 회원가입·로그인·토큰, 내 정보, MASTER 사용자 관리, 사용자 검증 내부 API | `auth` |
| `hub-ms` | 19093 | 허브 17개, 허브 간 경로, 재고·재고 변동 이력 | `hub` |
| `company-ms` | 19091 | 업체, 상품 | `company` |
| `order-ms` | 19094 | 주문 생성·취소, Saga 조정, Slack 알림 호출 | `orders` |
| `delivery-ms` | 19092 | 배송, 배송 구간(경로) | `delivery` |
| `common` | - | `BaseEntity`·감사(Audit), 공통 오류 응답, `@CurrentUser`·Feign 사용자 헤더 전달, Feign 장애 대응(`RemoteCallTemplate`), `SlackNotifier` | - |

각 서비스는 **4계층(presentation → application → domain ← infrastructure)** 구조이며, 다른 서비스의 데이터는 DB 외래키 없이 **UUID 참조 + Feign API**로만 확인합니다.

### 4.3 Gateway 라우팅

| 경로 | 서비스 |
|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**` | `user-ms` |
| `/api/v1/companies/**`, `/api/v1/products/**` | `company-ms` |
| `/api/v1/hubs/**`, `/api/v1/hub-routes/**`, `/api/v1/inventories/**` | `hub-ms` |
| `/api/v1/orders/**` | `order-ms` |
| `/api/v1/deliveries/**`, `/api/v1/delivery-routes/**` | `delivery-ms` |

- 공개 경로(토큰 불필요): `POST /api/v1/auth/signup`, `login`, `reissue`, `GET /api/v1/auth/check-username`. 그 밖의 `/api/v1/**`는 로그인이 필요합니다.
- `/internal/**`는 Gateway에서 404로 차단합니다. 내부 API는 서비스끼리 Feign으로만 호출합니다.
- MASTER 전용 경로와 헤더 규약은 [Gateway·인증 가이드](<docs/guide/01 Gateway·인증 가이드.md>)를 참고합니다.

## 5. 실행 방법

### 5.1 준비

- Docker Desktop, Java 21
- 루트에 `.env` 작성 (`.env.example` 복사 후 값 채우기, **Git에 올리지 않음**)

```properties
DB_NAME=sparta_logistics
DB_PORT=5432
DB_USER=sparta_admin              # Postgres 관리자 (컨테이너용)
DB_PASSWORD=...
AUTH_DB_USER=auth_svc             # 서비스별 DB 계정 (HUB_/COMPANY_/ORDER_/DELIVERY_ 동일 형식)
AUTH_DB_PASSWORD=...

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=...                # openssl rand -hex 24

JWT_SECRET_KEY=...                # openssl rand -base64 32 (Gateway·user-ms 공용)

# 초기 MASTER (user-ms 첫 기동 시 1회 생성)
MASTER_USERNAME=master01
MASTER_PASSWORD=...               # 8~15자, 대·소문자·숫자·특수문자 포함
MASTER_NAME=관리자
MASTER_SLACK_ID=...

SLACK_WEBHOOK_URL=                # 선택. 비워 두면 알림을 보내지 않음
```

### 5.2 실행 순서

```bash
# 1) 인프라: PostgreSQL(스키마·서비스 계정 자동 생성) + Redis
docker compose up -d postgres redis

# 2) Eureka → 3) 서비스 → 4) Gateway (터미널을 나눠서)
./gradlew :eureka-registry:bootRun
./gradlew :user-ms:bootRun          # hub-ms, company-ms, order-ms, delivery-ms 도 같은 방식
./gradlew :gateway-edge:bootRun
```

> 현재 Docker Compose에는 PostgreSQL·Redis만 정의되어 있고, 애플리케이션 컨테이너화는 진행 중입니다.

### 5.3 동작 확인

| 확인 | 주소 |
|---|---|
| Eureka 대시보드 | http://localhost:8761 |
| Gateway 헬스 | http://localhost:19090/actuator/health |

```bash
# 로그인 → Access Token 발급
curl -s localhost:19090/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"<MASTER_USERNAME>","password":"<MASTER_PASSWORD>"}'

# 토큰으로 내 정보 조회
curl -s localhost:19090/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```

- Postman: `docs/postman/sparta-logistics-gateway.postman_collection.json`을 Import 후 위에서부터 순서대로 실행합니다.
- 자주 만나는 문제(기동 직후 503, Redis 미기동, `JWT_SECRET_KEY` 불일치 등)는 [로컬 실행·라우팅 확인 가이드](<docs/Common/로컬 실행·라우팅 확인 가이드.md>) 4장에 정리했습니다.

### 5.4 테스트

```bash
./gradlew :gateway-edge:test
./gradlew :user-ms:test
./gradlew :common:test
./gradlew :user-ms:test -PincludeContainer   # Docker가 필요한 테스트 포함 (Testcontainers)
```

## 6. ERD

- DBMS: PostgreSQL 단일 컨테이너, 서비스별 스키마 5개(`auth`, `hub`, `company`, `orders`, `delivery`), 서비스마다 자기 스키마만 접근 가능한 DB 계정 사용
- 모든 테이블 PK는 `UUID`, Audit 컬럼 6개(`created/updated/deleted_at·by`), Soft Delete
- 실선은 같은 스키마 안의 FK, 점선은 다른 서비스의 UUID 논리 참조(DB FK 없음, API로 검증)

```mermaid
flowchart LR
    subgraph AUTH["auth · user-ms"]
        users[p_users]
    end
    subgraph HUB["hub · hub-ms"]
        hubs[p_hubs]
        hub_routes[p_hub_routes]
        inventories[p_inventories]
        inv_mv[p_inventory_movements]
    end
    subgraph COMPANY["company · company-ms"]
        companies[p_companies]
        products[p_products]
    end
    subgraph ORDERS["orders · order-ms"]
        orders[p_orders]
        order_hist[p_order_histories]
    end
    subgraph DELIVERY["delivery · delivery-ms"]
        deliveries[p_deliveries]
        dlv_routes[p_delivery_routes]
        dlv_hist[p_delivery_histories]
    end

    hubs --> hub_routes
    hubs --> inventories
    inventories --> inv_mv
    companies --> products
    orders --> order_hist
    deliveries --> dlv_routes
    deliveries --> dlv_hist

    products -.-> inventories
    orders -.-> inv_mv
    hubs -.-> companies
    users -.-> companies
    companies -.-> orders
    products -.-> orders
    users -.-> orders
    orders -.-> deliveries
    hubs -.-> deliveries
    users -.-> deliveries
    hubs -.-> dlv_routes
    users -.-> dlv_routes
    orders -.-> dlv_hist
```

컬럼까지 포함한 전체 ERD는 [ERD](<docs/design/05 ERD.md>), 컬럼별 상세는 [테이블 명세서](<docs/design/06 테이블 명세서.md>)를 참고합니다.

![전체 ERD](<docs/assets/3f72dc3e-logistics_msa_erd_named_primary_keys_staging-01_물류_MSA__전체_ERD.jpg>)

## 7. API

전체 명세는 [API 명세서](<docs/design/04 API 명세서.md>)에 있습니다. 외부 API는 `/api/v1/**`, 서비스 간 내부 API는 `/internal/v1/**`입니다.

| 도메인 | 주요 API |
|---|---|
| Auth | 회원가입, ID 중복 확인, 로그인, 토큰 재발급, 로그아웃 |
| User | 내 정보 조회·수정, 비밀번호 변경, 탈퇴 / (MASTER) 사용자 생성·목록·상세·수정·역할 변경·배송담당자 지정·비활성화·재활성화·비밀번호 초기화 |
| Hub · Hub Route · Inventory | 허브·경로 조회·관리, 재고 등록·조회, 재고 변동 이력 |
| Company · Product | 업체·상품 등록·조회·수정·삭제 |
| Order | 주문 생성·조회·취소·삭제 |
| Delivery · Delivery Route | 배송·구간 조회, 배송 상태 변경 |
| Internal (Feign) | 사용자 검증, 허브·업체·상품 검증, 재고 차감·복원, 허브 경로 조회, 배송 생성·취소 |

**오류 응답 형식** (Gateway·서비스 공통)

```json
{ "code": "INVALID_INPUT", "message": "...", "errors": [ { "field": "username", "reason": "..." } ] }
```

HTTP 상태 코드는 응답 상태로 전달하고, `errors`는 입력값 검증 실패 때만 채웁니다.

## 8. 핵심 설계

### 8.1 인증·인가

```mermaid
sequenceDiagram
    participant C as Client
    participant G as gateway-edge
    participant R as Redis
    participant S as 서비스

    C->>G: Authorization: Bearer {accessToken}
    G->>G: 경로 정규화 · 공개/차단 경로 확인
    G->>G: JWT 서명·만료·typ 검증
    G->>R: 로그아웃·무효화 여부 확인
    G->>G: MASTER 전용 경로 인가
    G->>S: X-User-Id, X-User-Role, X-Request-Id 주입
    S->>S: @CurrentUser로 사용자 확인, 소유권 검사
```

- **401은 Gateway, 403(소유권)은 서비스**가 판단합니다. 역할 단위(MASTER 전용) 인가는 Gateway에서 처리합니다.
- 클라이언트가 보낸 `X-User-*` 헤더는 Gateway가 지우고 다시 넣어, 헤더 위조를 막습니다.
- Access Token 30분, Refresh Token은 Redis에 저장하고 재발급 시 교체(Rotation)합니다. 로그아웃·비밀번호 변경·탈퇴 시 이전 토큰은 `TOKEN_REVOKED`가 됩니다.
- MASTER가 비밀번호를 초기화한 사용자는 비밀번호를 바꿀 때까지 일부 API만 호출할 수 있습니다.

### 8.2 주문 흐름과 실패 보상(롤백)

```mermaid
flowchart LR
    A[주문 생성] --> B[업체·상품 검증<br/>company-ms]
    B --> C[재고 차감<br/>hub-ms · 조건부 UPDATE]
    C --> D[배송·구간 생성<br/>delivery-ms]
    D --> E[주문 COMPLETED<br/>Slack 알림]
    C -- 재고 부족 --> F[주문 FAILED]
    D -- 배송 생성 실패 --> G[재고 복원 보상<br/>hub-ms] --> F
```

- 주문 서비스가 오케스트레이터로 각 서비스를 Feign으로 호출합니다.
- **롤백 시나리오**: 배송 생성이 실패하면 차감했던 재고를 복원하고 주문을 `FAILED`로 남깁니다.
- 재고 변경은 `Idempotency-Key`로 같은 요청이 다시 와도 한 번만 반영합니다.

### 8.3 장애 대응

- **Gateway**: 라우트별 타임아웃과 서비스별 Circuit Breaker. 하위 서비스 장애 시 503 `SERVICE_UNAVAILABLE`로 빠르게 응답합니다.
- **Feign (common)**: 공통 타임아웃·Circuit Breaker, 실패를 예외 3종(호출 실패·상대 거절·결과 모름)으로 나눠 Saga에서 처리 방식을 고를 수 있게 했습니다.
- **Slack 알림**: 알림 실패가 주문 처리에 영향을 주지 않으며, Webhook URL이 없으면 알림을 보내지 않습니다(NoOp).

## 9. 구현 현황

> 기준: 2026-10-16

| 영역 | 상태 | 내용 |
|---|---|---|
| Gateway | ✅ | 라우팅, JWT 인증, MASTER 경로 인가, 토큰 상태 확인, 요청 ID·접근 로그, 타임아웃·Circuit Breaker, 경로 정규화 |
| common | ✅ | `BaseEntity`·감사, 공통 예외, `@CurrentUser`, Feign 헤더 전달·장애 대응, `SlackNotifier` |
| user-ms | 🚧 | 가입·로그인·재발급·로그아웃·내 정보·비밀번호 변경·탈퇴·내부 조회 완료 / MASTER 관리자 API 진행 중 |
| hub-ms | 🚧 | 허브 엔티티·조회, 17개 허브 시드 |
| company-ms, order-ms, delivery-ms | 🚧 | 구현 중 |
| Docker Compose | 🚧 | PostgreSQL·Redis 구성 완료 / 애플리케이션 컨테이너화 예정 |

## 10. 문서 안내

| 분류 | 문서 |
|---|---|
| 설계 | [프로젝트 소개](<docs/design/00 프로젝트 소개.md>) · [인프라 설계](<docs/design/02 인프라 설계.md>) · [도메인 모델](<docs/design/03 도메인 모델.md>) · [API 명세서](<docs/design/04 API 명세서.md>) · [ERD](<docs/design/05 ERD.md>) · [테이블 명세서](<docs/design/06 테이블 명세서.md>) · [서비스 정책](<docs/design/07 서비스 정책.md>) · [요구사항 정리](<docs/design/요구사항정리.md>) |
| 공통 설계 | [게이트웨이 인증·인가](<docs/Common/SYS-01 게이트웨이 인증 인가 설계.md>) · [장애 대응](<docs/Common/SYS-13 장애 대응 설계.md>) · [주문 알림](<docs/Common/NTF 주문 알림 설계.md>) |
| 개발 가이드 | [가이드 목차](<docs/guide/00 개발 가이드 목차.md>) · [Gateway·인증](<docs/guide/01 Gateway·인증 가이드.md>) · [장애 대응(Feign)](<docs/guide/02 장애 대응(Feign) 가이드.md>) · [User·Auth API](<docs/guide/03 User·Auth API 가이드.md>) · [Slack 알림](<docs/guide/04 Slack 알림 가이드.md>) |
| 실행·테스트 | [로컬 실행·라우팅 확인](<docs/Common/로컬 실행·라우팅 확인 가이드.md>) · [Postman Gateway 테스트](<docs/Common/Postman Gateway 테스트 가이드.md>) |
| 협업 | [PR 템플릿](<docs/conventions/PULL_REQUEST_TEMPLATE.md>) · [구현 우선순위](<docs/구현 우선순위.md>) |
