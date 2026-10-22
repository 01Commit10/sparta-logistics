# Hub 사용법

> 기준: 현재 `hub-ms` 구현 · 최종 확인: 2026-10-16
> 대상: Postman·curl로 Hub API를 호출하거나, Hub 조회 응답을 사용하는 서비스 개발자
> 기존 [API 명세서](<../../design/04 API 명세서.md#hub>)의 경로·로그인 권한을 바탕으로, 현재 구현과 실제 연동 결과를 정리했습니다. 명세와 구현이 다른 항목은 7절에 구분했습니다.

## 1. 사용할 수 있는 API

외부 요청은 Gateway `http://localhost:19090`으로 보냅니다. `USER`와 `MASTER` 모두 조회할 수 있습니다.

| 하고 싶은 것 | 요청 | 성공 응답 |
|---|---|---|
| 전체 Hub 조회 | `GET /api/v1/hubs` | 200, Hub 객체의 배열 |
| 이름 검색·정렬 | `GET /api/v1/hubs?name=경기&sort=name,asc` | 200, 조건에 맞는 Hub 객체의 배열 |
| 단일 Hub 조회 | `GET /api/v1/hubs/{hubId}` | 200, Hub 객체 1개 |

- 요청 본문은 없습니다. 헤더에 `Authorization: Bearer {accessToken}`을 넣습니다.
- 목록은 페이지네이션 없이 반환합니다. `page`, `size`, 전체 건수 등 페이지 관련 응답 필드는 없습니다.
- 초기 데이터는 17개입니다. 검색 조건과 논리 삭제 여부에 따라 반환 개수는 달라집니다.
- 목록·검색·단일 조회 모두 `deleted_at IS NULL`인 Hub만 조회합니다.

## 2. 검색·정렬 조건

| 파라미터 | 타입 | 필수 | 동작 |
|---|---|---|---|
| `name` | String | 아니요 | Hub 이름의 부분 일치 검색. 생략·빈 문자열·공백만 전달하면 전체 조회 |
| `sort` | String | 아니요 | 아래 네 가지 중 하나. 생략하면 `createdAt,desc` |

검색어 앞뒤 공백은 제거합니다. `name=경기`는 **이름**에 `경기`가 포함된 Hub를 찾으며, 주소는 검색하지 않습니다. `%`, `_`, `\`도 와일드카드 대신 실제 문자로 검색합니다.

| `sort` 값 | 의미 |
|---|---|
| `createdAt,desc` | 생성일 최신순, 기본값 |
| `createdAt,asc` | 생성일 오래된순 |
| `name,asc` | 한국어 Hub명 오름차순 |
| `name,desc` | 한국어 Hub명 내림차순 |

- 1차 정렬값이 같으면 **ID 오름차순**으로 순서를 결정합니다. 검색 결과에도 같은 정렬 규칙을 적용합니다.
- 정렬값은 표의 철자 그대로 전달합니다. `sort=`, `sort=address,asc`, `sort=name,ASC`는 400 `INVALID_INPUT`입니다.
- 이름 오름차순의 예: **경기 남부 센터 → 경기 북부 센터 → 서울특별시 센터**.
- 초기 데이터를 한 번에 적재하면 생성일이 같을 수 있습니다. 이 경우 생성일 오름차순·내림차순 결과가 같아도 정상이며, ID 오름차순이 적용됩니다.

## 3. 응답 형식

목록·검색은 JSON 배열, 단일 조회는 JSON 객체를 반환합니다. 공통 `data` 래퍼나 페이지 응답 객체는 없습니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | String (UUID) | Hub 식별자 |
| `name` | String | Hub명 |
| `address` | String | Hub 주소 |

`name=경기&sort=name,asc`의 응답 예시입니다.

```json
[
  {
    "id": "10000000-0000-4000-8000-000000000003",
    "name": "경기 남부 센터",
    "address": "경기도 이천시 덕평로 257-21"
  },
  {
    "id": "10000000-0000-4000-8000-000000000002",
    "name": "경기 북부 센터",
    "address": "경기도 고양시 덕양구 권율대로 570"
  }
]
```

검색 결과가 없으면 **200과 빈 배열 `[]`**을 반환합니다. 단일 조회에서 없거나 논리 삭제된 ID를 요청하면 **404**를 반환합니다.

## 4. curl로 호출하기

PostgreSQL·Redis, Eureka·user-ms·hub-ms·Gateway가 실행되어 있어야 합니다. 계정 생성과 로그인은 [User·Auth API 사용법](<User·Auth API 사용법.md#1-가입하고-로그인하기-gateway-localhost19090>)을 참고합니다. 발급받은 **Access Token**을 아래에 입력합니다.

```bash
# 같은 Bash 터미널에서 이어서 실행합니다. 토큰 입력은 화면에 표시되지 않습니다.
GW=http://localhost:19090
read -r -s -p 'Access Token 입력: ' ACCESS_TOKEN
printf '\n'

# 전체 조회: 초기 데이터 기준 200, 17개
curl --fail-with-body -sS -i "$GW/api/v1/hubs" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 이름 검색 + 한국어 오름차순: 경기 남부 → 경기 북부
curl --fail-with-body -sS -i --get "$GW/api/v1/hubs" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'name=경기' \
  --data-urlencode 'sort=name,asc'

# 생성일 오래된순으로 전체 조회
curl --fail-with-body -sS -i --get "$GW/api/v1/hubs" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'sort=createdAt,asc'

# 검색 결과 없음: 초기 데이터 기준 200, []
curl --fail-with-body -sS -i --get "$GW/api/v1/hubs" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'name=제주'

# 단일 조회: 현재 초기 데이터의 서울 Hub ID
curl --fail-with-body -sS -i \
  "$GW/api/v1/hubs/10000000-0000-4000-8000-000000000001" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 확인이 끝나면 터미널 변수에서 토큰을 제거합니다. 로그아웃은 별도 API입니다.
unset ACCESS_TOKEN
```

`GW`를 정의하므로 프로젝트 디렉터리 밖에서도 위 조회 명령을 실행할 수 있습니다. Postman에서도 같은 URL·파라미터와 Bearer Token을 사용합니다. 외부 요청의 `X-User-Id`, `X-User-Role`은 직접 지정하지 않고 Gateway가 검증 후 전달합니다.

## 5. 오류가 발생하면

| HTTP | `code` | 상황·확인할 것 |
|---|---|---|
| 400 | `INVALID_INPUT` | 지원하지 않는 정렬값 또는 UUID 형식이 아닌 `hubId` |
| 401 | `TOKEN_MISSING` | Bearer Token 누락 |
| 401 | `TOKEN_EXPIRED` / `TOKEN_INVALID` / `TOKEN_REVOKED` | Access Token의 만료·유효성·폐기 여부 확인. 필요하면 재로그인 |
| 403 | `PASSWORD_CHANGE_REQUIRED` | 비밀번호 변경이 필요한 계정. 비밀번호를 먼저 변경 |
| 404 | `RESOURCE_NOT_FOUND` | 단일 조회 대상이 없거나 논리 삭제됨 |
| 503 | `AUTH_UNAVAILABLE` | Gateway가 Redis에서 토큰 상태를 확인하지 못함 |
| 503 | `SERVICE_UNAVAILABLE` | 서비스 기동·Eureka 등록·Gateway 회로 상태 확인 |
| 504 | `SERVICE_TIMEOUT` | 하위 서비스 응답 시간 초과 |

지원하지 않는 정렬값의 응답 예시입니다.

```json
{
  "code": "INVALID_INPUT",
  "message": "지원하지 않는 정렬 조건입니다."
}
```

단일 조회 대상이 없거나 논리 삭제된 경우의 응답 예시입니다.

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "해당하는 허브를 찾을 수 없습니다."
}
```

UUID 형식 오류에는 `errors` 필드에 필드별 상세 내용이 추가될 수 있습니다. 장애를 확인할 때는 응답 헤더의 `X-Request-Id`로 같은 요청의 로그를 찾습니다. 인증·라우팅 오류의 자세한 대처는 [Gateway 사용법](<Gateway 사용법.md>)을 참고합니다.

## 6. 검증 방법과 확인 범위

프로젝트 루트에서 다음 명령으로 Hub 테스트를 실행합니다. Docker가 실행되어 있어야 하며, PostgreSQL Testcontainers를 사용합니다.

```bash
bash gradlew :hub-ms:test
```

- `HubSearchIntegrationTest`: 이름 부분 검색·결과 없음·전체 조회, 생성일·한국어 이름 정렬, 같은 생성일의 ID 순서, 특수문자 검색, 삭제된 Hub 제외, 삭제된 Hub의 404, 지원하지 않는 정렬값의 400을 검증합니다.
- 위 통합 테스트는 MockMvc로 Hub에 요청하므로 Gateway의 JWT 검증은 별도 확인 대상입니다.
- **2026-10-16 담당자의 실제 실행 결과로 확인한 흐름:** 회원가입 201 → 로그인·토큰 발급 → Gateway를 통한 전체 조회 200·17개 → `name=경기&sort=name,asc`에서 남부·북부 순서 → 서울 Hub 단일 조회 200. 토큰 없는 전체 조회의 401 `TOKEN_MISSING`도 확인했습니다.
- 생성일 오름차순·검색 결과 없음 등 4절의 다른 호출은 재확인용 예시이며, 위 수동 연동 확인 목록과 구분합니다.

## 7. 기존 명세와 현재 구현의 차이

[API 명세서](<../../design/04 API 명세서.md>)는 예상 설계임을 명시하고 있습니다. 팀원이 현재 구현된 API를 사용할 때는 아래 차이를 함께 확인합니다.

| 기존 명세의 항목 | 현재 Hub 구현 |
|---|---|
| Hub 목록의 `페이지 조건`, 공통 `page`·`size` 규칙 | `name`·`sort`를 받고 JSON 배열을 반환. 페이지네이션 미적용 |
| `PATCH /api/v1/hubs/{hubId}` | 미제공. [과제 요구사항](<../../design/숙련프로젝트발제.md>)에 따라 고정 Hub의 조회 API만 제공 |
| `GET /internal/v1/hubs/{hubId}` | 2026-10-19 구현. 활성 허브는 id·name·address 반환, 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND |

현재 company-ms의 `HubFeignClient`는 `/api/v1/hubs/{hubId}`를 호출합니다. 소비자에서 `/internal/v1/hubs/{hubId}`로 경로를 변경할 수 있으며 응답 필드는 동일합니다. 내부 조회는 사용자 헤더를 필수로 요구하지 않습니다. 사용자 헤더 전달과 서비스 호출자 인증은 별개이며, 현재 Hub의 별도 서비스 호출자 인증은 미구현입니다. 내부 API 계약·검증 범위는 [Hub-MS Feign 연동 사용법](<Hub-MS Feign 연동 사용법.md>)을 참고합니다.

구현 확인 위치:

- [HubController](../../../hub-ms/src/main/java/com/hub/hub/presentation/controller/HubController.java): URL·파라미터·정렬값 검증
- [HubService](../../../hub-ms/src/main/java/com/hub/hub/application/service/HubService.java): 검색어 처리·기본 정렬·단일 조회 오류
- [HubRepositoryImpl](../../../hub-ms/src/main/java/com/hub/hub/infrastructure/persistence/HubRepositoryImpl.java), [HubJpaRepository](../../../hub-ms/src/main/java/com/hub/hub/infrastructure/persistence/HubJpaRepository.java): 한국어 정렬·논리 삭제 제외·문자 검색
- [HubResponseDto](../../../hub-ms/src/main/java/com/hub/hub/presentation/dto/HubResponseDto.java): 응답 필드
