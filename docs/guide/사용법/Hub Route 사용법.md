# Hub Route 사용법

> 기준: 현재 `hub-ms` 구현 · 최종 확인: 2026-10-17
> 대상: Postman·curl로 Hub Route API를 호출하거나, Hub 간 이동 거리·소요 시간 응답을 사용하는 서비스 개발자
> [원본 요구사항](https://app.notion.com/p/3f7530b4fe5980799f8eef0d0298acd9)의 고정 Hub·P2P 조회 범위를 바탕으로 현재 구현과 실제 연동 결과를 정리했습니다. 기존 [API 명세서](<../../design/04 API 명세서.md#hub-route>)와 구현의 차이, 공통 정렬 요구사항의 확인 사항은 7절에 구분했습니다.

## 1. 사용할 수 있는 API

외부 요청은 Gateway `http://localhost:19090`으로 보냅니다. `USER`와 `MASTER` 모두 조회할 수 있습니다.

| 하고 싶은 것 | 요청 | 성공 응답 |
|---|---|---|
| 전체 Hub Route 조회 | `GET /api/v1/hub-routes` | 200, 경로 객체의 배열 |
| 경로 ID로 단일 조회 | `GET /api/v1/hub-routes/{routeId}` | 200, 경로 객체 1개 |
| 출발·도착 Hub 조합으로 단일 조회 | `GET /api/v1/hub-routes/fromtosearch?fromHubId={UUID}&toHubId={UUID}` | 200, 경로 객체 1개 |

- 요청 본문은 없습니다. 헤더에 `Authorization: Bearer {accessToken}`을 넣습니다.
- 초기 데이터는 서로 다른 17개 Hub 사이의 모든 방향별 경로인 **272개(17 × 16)**입니다. 같은 Hub로 이동하는 경로는 포함하지 않습니다.
- 목록은 페이지네이션 없이 반환하며, 논리 삭제 여부에 따라 반환 개수가 달라집니다.
- 목록·ID 조회·조합 조회 모두 `deleted_at IS NULL`인 경로만 조회합니다.
- 고정 경로의 조회 API만 제공하며, 생성·수정·삭제 API는 제공하지 않습니다.

## 2. 검색·정렬 조건

출발·도착 Hub ID를 모두 알고 있다면 `/fromtosearch`를 사용합니다. 경로의 철자는 모두 소문자입니다.

| 파라미터 | 타입 | 필수 | 동작 |
|---|---|---|---|
| `fromHubId` | UUID | 예 | 출발 Hub ID와 정확히 일치 |
| `toHubId` | UUID | 예 | 도착 Hub ID와 정확히 일치 |

- 두 조건을 **동시에 만족하는 경로 하나**를 반환합니다. 조합의 유일성은 DB의 복합 유니크 제약으로 보장합니다.
- 출발·도착 순서를 바꾸면 역방향 경로를 조회합니다. 정방향과 역방향의 경로 ID·거리·소요 시간은 다를 수 있습니다.
- 한쪽 파라미터가 빠지거나 UUID 형식이 잘못되면 400 `INVALID_INPUT`입니다. 출발지 단독·도착지 단독 검색은 제공하지 않습니다.
- 조합에 맞는 경로가 없거나 논리 삭제됐으면 404 `RESOURCE_NOT_FOUND`입니다.
- 검색 조건은 `/fromtosearch`에 전달합니다. 전체 목록 경로인 `/api/v1/hub-routes`에 붙인 `fromHubId`·`toHubId`는 필터로 사용되지 않습니다.

전체 목록은 **생성일 내림차순**, 같은 생성일이면 **ID 오름차순**으로 반환합니다. 현재 `sort` 파라미터를 받지 않으므로 정렬 기준을 선택할 수 없습니다.

초기 데이터를 한 번에 적재하면 생성일이 같을 수 있습니다. 이 경우 ID 오름차순이 적용되므로, 전체 목록의 첫 번째 경로가 서울 출발 경로라고 가정하지 않습니다.

## 3. 응답 형식

전체 목록은 JSON 배열, ID 조회와 조합 조회는 JSON 객체를 반환합니다. 공통 `data` 래퍼나 페이지 응답 객체는 없습니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | String (UUID) | Hub Route 식별자. Hub ID와는 다른 값 |
| `fromHubId` | String (UUID) | 출발 Hub 식별자 |
| `toHubId` | String (UUID) | 도착 Hub 식별자 |
| `distanceKm` | Number | 이동 거리, km 단위. 소수 둘째 자리까지 저장 |
| `durationMin` | Integer | 소요 시간, 분 단위 |

서울특별시 센터에서 경기 북부 센터로 이동하는 초기 경로의 단일 조회 응답입니다.

```json
{
  "id": "e43b2eeb-da7e-5043-882b-dcd29a017942",
  "fromHubId": "10000000-0000-4000-8000-000000000001",
  "toHubId": "10000000-0000-4000-8000-000000000002",
  "distanceKm": 43.04,
  "durationMin": 49
}
```

| 초기 경로 | `id` | 거리(km) | 소요 시간(분) |
|---|---|---|---|
| 서울특별시 센터 → 경기 북부 센터 | `e43b2eeb-da7e-5043-882b-dcd29a017942` | 43.04 | 49 |
| 경기 북부 센터 → 서울특별시 센터 | `71f367ae-1597-5772-bf27-84c10ac606c9` | 44.08 | 46 |

전체 목록에 조회할 경로가 없으면 **200과 빈 배열 `[]`**을 반환합니다. 단일 조회의 미존재·논리 삭제는 **404**로 구분합니다.

## 4. curl로 호출하기

PostgreSQL·Redis, Eureka·user-ms·hub-ms·Gateway가 실행되어 있어야 합니다. Gateway가 Hub를 찾을 수 있도록 `hub-ms`의 Eureka 클라이언트 등록도 활성화합니다. 계정 생성과 로그인은 [User·Auth API 사용법](<User·Auth API 사용법.md#1-가입하고-로그인하기-gateway-localhost19090>)을 참고합니다. 발급받은 **Access Token**을 아래에 입력합니다.

```bash
# 같은 Bash 터미널에서 이어서 실행합니다. 토큰 입력은 화면에 표시되지 않습니다.
GW=http://localhost:19090
read -r -s -p 'Access Token 입력: ' ACCESS_TOKEN
printf '\n'

# 전체 조회: 초기 데이터 기준 200, 272개
curl --fail-with-body -sS -i "$GW/api/v1/hub-routes" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 서울 → 경기 북부: 200, 객체 1개, 43.04km / 49분
curl --fail-with-body -sS -i --get "$GW/api/v1/hub-routes/fromtosearch" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'fromHubId=10000000-0000-4000-8000-000000000001' \
  --data-urlencode 'toHubId=10000000-0000-4000-8000-000000000002'

# 경기 북부 → 서울: 200, 객체 1개, 44.08km / 46분
curl --fail-with-body -sS -i --get "$GW/api/v1/hub-routes/fromtosearch" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'fromHubId=10000000-0000-4000-8000-000000000002' \
  --data-urlencode 'toHubId=10000000-0000-4000-8000-000000000001'

# 서울 → 경기 북부 경로의 ID로 단일 조회: 200, 위 정방향과 같은 객체
curl --fail-with-body -sS -i \
  "$GW/api/v1/hub-routes/e43b2eeb-da7e-5043-882b-dcd29a017942" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 확인이 끝나면 터미널 변수에서 토큰을 제거합니다. 로그아웃은 별도 API입니다.
unset ACCESS_TOKEN
```

`GW`를 정의하므로 프로젝트 디렉터리 밖에서도 위 조회 명령을 실행할 수 있습니다. Postman에서도 같은 URL·파라미터와 Bearer Token을 사용합니다. 외부 요청의 `X-User-Id`, `X-User-Role`은 직접 지정하지 않고 Gateway가 검증 후 전달합니다.

## 5. 오류가 발생하면

| HTTP | `code` | 상황·확인할 것 |
|---|---|---|
| 400 | `INVALID_INPUT` | 조합 조회의 `fromHubId`·`toHubId` 누락 또는 UUID 형식이 아닌 `routeId`·검색 파라미터 |
| 401 | `TOKEN_MISSING` | Bearer Token 누락 |
| 401 | `TOKEN_EXPIRED` / `TOKEN_INVALID` / `TOKEN_REVOKED` | Access Token의 만료·유효성·폐기 여부 확인. 필요하면 재로그인 |
| 403 | `PASSWORD_CHANGE_REQUIRED` | 비밀번호 변경이 필요한 계정. 비밀번호를 먼저 변경 |
| 404 | `RESOURCE_NOT_FOUND` | ID 또는 출발·도착 조합에 해당하는 경로가 없거나 논리 삭제됨 |
| 503 | `AUTH_UNAVAILABLE` | Gateway가 Redis에서 토큰 상태를 확인하지 못함 |
| 503 | `SERVICE_UNAVAILABLE` | 서비스 기동·Eureka 등록·Gateway 회로 상태 확인 |
| 504 | `SERVICE_TIMEOUT` | 하위 서비스 응답 시간 초과 |

조합 조회에서 `toHubId`를 누락한 경우의 응답 예시입니다.

```json
{
  "code": "INVALID_INPUT",
  "message": "잘못된 요청입니다.",
  "errors": [
    {
      "field": "toHubId",
      "reason": "필수 파라미터입니다."
    }
  ]
}
```

단일 조회 대상이 없거나 논리 삭제된 경우의 응답 예시입니다.

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "해당하는 Hub 경로를 찾을 수 없습니다."
}
```

장애를 확인할 때는 응답 헤더의 `X-Request-Id`로 같은 요청의 로그를 찾습니다. 인증·라우팅 오류의 자세한 대처는 [Gateway 사용법](<Gateway 사용법.md>)을 참고합니다.

## 6. 검증 방법과 확인 범위

프로젝트 루트에서 다음 명령으로 테스트를 실행합니다. Docker가 실행되어 있어야 하며, PostgreSQL Testcontainers를 사용합니다.

```bash
# Hub Route 통합 테스트만 실행
bash gradlew :hub-ms:test --tests 'com.hub.hubroute.HubRouteSearchIntegrationTest'

# 기존 Hub 조회까지 포함한 모듈 전체 테스트를 다시 실행
bash gradlew :hub-ms:test --rerun-tasks
```

- `HubRouteSearchIntegrationTest`의 13개 테스트는 빈 목록, 목록·ID 조회의 응답 필드, 미존재 경로의 404, 논리 삭제 제외, 기본 생성일 내림차순, 출발·도착 조합 조회, 역방향 구분, 조합 미존재·삭제의 404, 필수 파라미터 누락의 400을 검증합니다.
- 위 통합 테스트는 MockMvc로 Hub에 요청하므로 Gateway의 JWT 검증은 별도 확인 대상입니다. 실행 중인 Hub 애플리케이션과는 별도 테스트 DB를 사용합니다.
- **2026-10-17 테스트 보고서 확인:** 모듈 전체 31개(Hub 조회 17개, Hub Route 조회 13개, 애플리케이션 컨텍스트 1개), 실패·오류·건너뛴 테스트 0개입니다.
- **2026-10-17 담당자의 실제 실행 결과로 확인한 흐름:** DB에서 Hub 17개·경로 272개 확인 → 로그인·토큰 발급 → Gateway 경유 서울→경기 북부 조회 200·43.04km/49분 → 경기 북부→서울 조회 200·44.08km/46분입니다.
- 4절의 전체 목록·경로 ID 조회는 수동 재확인용 예시이며, 위 정방향·역방향 수동 연동 결과와 구분합니다.

## 7. 요구사항·기존 명세와 현재 구현의 차이

[원본 요구사항](https://app.notion.com/p/3f7530b4fe5980799f8eef0d0298acd9)의 Hub Route 기본 조회 범위에 대한 현재 상태입니다.

| 요구사항 | 현재 상태 |
|---|---|
| 출발·도착 Hub ID, 이동 거리, 소요 시간으로 P2P 경로 모델링 | 구현 |
| 고정된 17개 Hub 사이의 직접 이동 경로 초기 적재 | 방향별 272개 경로를 `hub_routes.sql`로 적재하도록 설정 |
| 모든 로그인 사용자의 조회·검색 | 목록·경로 ID·출발/도착 조합 조회 제공 |
| 논리 삭제된 데이터 조회·검색 제외 | 모든 조회에 `deleted_at IS NULL` 적용 |
| 검색조건과 정렬 기능, 기본 생성일 역순 | 조합 검색과 목록의 기본 정렬 구현. 정렬 기준 선택은 미구현 |

공통 정렬 요구사항을 **호출자가 정렬 기준을 선택할 수 있어야 한다**는 기준으로 적용하면, 현재 Hub Route에는 보완이 필요합니다. 현재 테스트 통과는 구현된 동작을 검증한 결과이며, 정렬 선택까지 검증했다는 의미는 아닙니다. 지원할 기준을 정한 뒤 테스트와 API를 추가해야 합니다.

기존 [API 명세서](<../../design/04 API 명세서.md>)와 다른 항목은 다음과 같습니다.

| 기존 명세의 항목 | 현재 Hub Route 구현 |
|---|---|
| 목록 API에 출발·도착 Hub 검색 조건 전달 | `/fromtosearch`에서 `fromHubId`·`toHubId`를 받아 객체 1개 반환 |
| 공통 `page`·`size`·`sort` 규칙 | 목록은 페이지네이션 없이 고정 정렬의 JSON 배열 반환 |
| 경로 등록·수정·삭제 API | 미제공. 원본 요구사항의 초기 적재·조회 범위로 구현 |
| `GET /internal/v1/hub-routes?departureHubId={id}&arrivalHubId={id}` | 미구현. 내부 전용 경로·파라미터는 현재 공개 API 계약과 다름 |

배송 등 다른 서비스에서 호출할 때는 현재 `/api/v1/hub-routes/fromtosearch` 계약과 공통 헤더 전달 규칙을 맞춰야 합니다. 별도의 내부 전용 API는 실제 호출 계약을 확정한 뒤 검토합니다. 서비스 간 호출 규칙은 [Feign 호출 사용법](<Feign 호출 사용법.md>)을 참고합니다.

구현 확인 위치:

- [HubRouteController](../../../hub-ms/src/main/java/com/hub/hubroute/presentation/controller/HubRouteController.java): URL·필수 파라미터·응답 DTO 구성
- [HubRouteService](../../../hub-ms/src/main/java/com/hub/hubroute/application/service/HubRouteService.java): 읽기 전용 트랜잭션·응용 DTO 변환·미존재 경로 오류
- [HubRouteRepository](../../../hub-ms/src/main/java/com/hub/hubroute/domain/repository/HubRouteRepository.java): 도메인 조회 계약
- [HubRouteRepositoryImpl](../../../hub-ms/src/main/java/com/hub/hubroute/infrastructure/persistence/HubRouteRepositoryImpl.java), [HubRouteJpaRepository](../../../hub-ms/src/main/java/com/hub/hubroute/infrastructure/persistence/HubRouteJpaRepository.java): 기본 정렬·논리 삭제 제외·조합 조회
- [HubRoute](../../../hub-ms/src/main/java/com/hub/hubroute/domain/entity/HubRoute.java): 컬럼 매핑·출발/도착 복합 유니크 제약
- [HubRouteResponseDto](../../../hub-ms/src/main/java/com/hub/hubroute/presentation/dto/HubRouteResponseDto.java): 응답 필드
- [application.yaml](../../../hub-ms/src/main/resources/application.yaml), [hub_routes.sql](../../../hub-ms/src/main/resources/db/seed/hub_routes.sql): 초기 데이터 적재 설정·방향별 경로
- [HubRouteSearchIntegrationTest](../../../hub-ms/src/test/java/com/hub/hubroute/HubRouteSearchIntegrationTest.java): 조회 동작 통합 테스트
