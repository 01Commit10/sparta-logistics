# User·Auth API 사용법

> 작성: 대진 · 기준: `dev` d6af869 · 최종 수정: 2026-10-16
> 대상: Postman·curl로 API를 테스트하는 사람, 다른 서비스에서 사용자 정보가 필요한 사람
> 원리·전체 필드·오류 코드 전체는 [[03 User·Auth API 가이드]]에 있습니다. 이 문서는 **따라 하기만** 합니다.

## 3줄 요약

1. **가입 → 로그인**하면 `accessToken`(30분)과 `refreshToken`(7일)을 받습니다. API를 부를 때는 `Authorization: Bearer {accessToken}`을 붙입니다.
2. 30분이 지나면 `refreshToken`으로 **재발급**합니다. 응답의 **두 토큰 모두** 새것으로 바꿔 저장하세요. 7일 동안 한 번도 안 쓰면 다시 로그인해야 합니다.
3. 다른 서비스가 사용자 정보(이름, Slack ID, 배송담당자 여부)가 필요하면 **내부 API `GET /internal/v1/users/{userId}`** 를 Feign으로 부릅니다.

## 1. 가입하고 로그인하기 (Gateway `localhost:19090`)

```bash
GW=http://localhost:19090

# 가입 (항상 USER로 만들어짐)
curl -s -X POST $GW/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"username":"user01","password":"Abcd1234!","name":"홍길동","slackId":"U01ABCDEF","organizationType":"HUB","organizationName":"서울허브"}'

# 로그인
curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user01","password":"Abcd1234!"}'
# → { "accessToken": "eyJ...", "refreshToken": "eyJ...", "passwordChangeRequired": false }
```

가입 규칙:

| 필드 | 규칙 |
|---|---|
| `username` | 영문 소문자·숫자 4~10자 (탈퇴한 ID도 재사용 불가) |
| `password` | 8~15자, 대문자·소문자·숫자·특수문자 각 1개 이상 |
| `slackId` | 필수, 다른 사용자와 중복 불가 |
| `organizationType` | `COMPANY` 또는 `HUB` (대문자 정확히) |
| `name`, `organizationName` | 필수 |

**MASTER 계정**은 가입으로 만들 수 없습니다. `.env`에 `MASTER_USERNAME`·`MASTER_PASSWORD`·`MASTER_NAME`·`MASTER_SLACK_ID`를 넣고 user-ms를 띄우면 자동으로 1명 생깁니다(활성 MASTER가 하나도 없을 때만). 그 값으로 로그인하세요.

## 2. API 한눈에 보기

| 하고 싶은 것 | 요청 | 로그인 | 성공 응답 |
|---|---|---|---|
| 가입 | `POST /api/v1/auth/signup` | ❌ | 201 |
| ID 중복 확인 | `GET /api/v1/auth/check-username?username=user01` | ❌ | 200 `{ "available": true }` |
| 로그인 | `POST /api/v1/auth/login` | ❌ | 200 토큰 2개 |
| 토큰 재발급 | `POST /api/v1/auth/reissue` `{ "refreshToken": "..." }` | ❌ | 200 토큰 2개 (둘 다 교체) |
| 로그아웃 | `POST /api/v1/auth/logout` (본문 없음) | ✅ | 204 |
| 내 정보 | `GET /api/v1/users/me` | ✅ | 200 |
| Slack ID 변경 | `PATCH /api/v1/users/me` `{ "slackId": "U09NEWID" }` | ✅ | 200 |
| 비밀번호 변경 | `PATCH /api/v1/users/me/password` `{ "currentPassword": "...", "newPassword": "..." }` | ✅ | 204 → **다시 로그인** |
| 탈퇴 | `DELETE /api/v1/users/me` `{ "password": "..." }` (DELETE지만 본문 필요) | ✅ | 204 → 로그인 불가 |

- MASTER 관리자 API(사용자 목록·역할 변경·배송담당자 지정 등, U12~U20)는 **아직 없습니다.** 만들어지면 이 표에 추가합니다.

## 3. 토큰이 막힐 때

| 응답 | 이유 | 할 일 |
|---|---|---|
| 401 `TOKEN_EXPIRED` | Access 30분 지남 | `reissue` 호출 |
| 401 `REFRESH_TOKEN_INVALID` | Refresh 만료, 이미 쓴 Refresh를 다시 씀, 로그아웃함 | 다시 로그인 |
| 401 `TOKEN_REVOKED` | 로그아웃·비밀번호 변경·탈퇴한 토큰 | 다시 로그인 |
| 401 `INVALID_CREDENTIALS` | ID·비밀번호 틀림 (탈퇴 계정도 같은 응답) | 입력값 확인 |
| 403 `PASSWORD_CHANGE_REQUIRED` | 관리자가 비밀번호를 초기화함 | `PATCH /users/me/password` 먼저 |
| 503 `AUTH_UNAVAILABLE` | Redis 꺼짐 | Redis 기동 |

- **재발급 후 예전 Refresh를 다시 쓰면** 도용으로 보고 그 사용자의 Refresh를 지웁니다. 이미 받은 Access는 30분 동안 계속 쓸 수 있고, 그다음에는 다시 로그인해야 합니다. Postman에서 예전 값을 붙여 넣지 않도록 주의하세요.

## 4. 다른 서비스에서 사용자 조회 (내부 API)

`GET /internal/v1/users/{userId}` (user-ms, Feign 전용, 외부에서 부르면 404)

```json
{ "id": "3f0c...uuid", "name": "홍길동", "role": "USER", "isDeliveryManager": true, "slackId": "U01ABCDEF", "active": true }
```

| 상황 | 응답 |
|---|---|
| 활성 사용자 | 200, 위 6개 필드 |
| 없음 · 탈퇴 | 404 `USER_NOT_FOUND` → Feign에서는 `RemoteBusinessException` |
| UUID 형식 아님 | 400 `INVALID_INPUT` |

- 만드는 방법(파일 배치, 코드, 테스트)은 **[[예시 - 다른 서비스에서 사용자 조회]]** 에 처음부터 끝까지 있습니다.
- **배송담당자 확인**은 200인지와 `isDeliveryManager == true`인지를 **함께** 보세요. 담당자가 아니어도 200입니다.
- `username`, 소속 정보는 이 응답에 없습니다. 필요하면 대진에게 요청해 주세요.
- 로컬에서 바로 확인: `curl http://localhost:19095/internal/v1/users/{userId}`

## 5. 테스트하기: 개발 테스트 vs 실제 테스트

> 여기서는 **다른 서비스가 user-ms 내부 API를 쓸 때**의 테스트를 다룹니다(예: delivery-ms가 배송담당자인지 확인). `UserPort`, `UserClient`, `UserAdapter`, `DeliveryManagerChecker`는 **예시**입니다([[Feign 호출 사용법]] 2장과 같은 이름). user-ms API 자체의 테스트는 대진이 user-ms 안에서 이미 하고 있습니다.

| 구분 | 언제 돌리나 | user-ms는 | 확인하는 것 |
|---|---|---|---|
| **개발 테스트** (5.1) | 매 빌드 | **가짜 Port** (`FakeUserPort`) | 담당자 아님·없는 사용자·user-ms 장애일 때 내 업무 처리 |
| **실제 테스트 – 자동** (5.2) | Adapter를 만들거나 고쳤을 때 | **진짜 Feign** → WireMock(가짜 user-ms) | 응답 6개 필드 매핑, 404 → `Optional.empty()` |
| **실제 테스트 – 자동** (5.3) | user-ms와 맞물리는지 볼 때 | **진짜 Feign** → Eureka → **진짜 user-ms** | 실제 응답 필드·404 본문과 내 Adapter가 맞는지 |
| **실제 테스트 – 수동** (5.4) | 시연 전 | **진짜 user-ms** | 실제 가입 사용자로 조회, 배송담당자 데이터 |

> ✅ 5.2의 테스트 3개는 dev(`d6af869`) 복사본에서 **그대로 돌려 통과**를 확인했습니다(2026-10-16).

### 5.1 개발 테스트: 가짜 Port

```java
// src/test/java/com/delivery/fake/FakeUserPort.java
public class FakeUserPort implements UserPort {
    private final Map<UUID, UserSnapshot> users = new HashMap<>();
    private RuntimeException failure;

    public void add(UUID id, boolean deliveryManager) {
        users.put(id, new UserSnapshot(id, "홍길동", deliveryManager, "U07ABCDEF1"));
    }
    public void failWith(RuntimeException e) { this.failure = e; }

    @Override
    public Optional<UserSnapshot> findActiveUser(UUID userId) {
        if (failure != null) throw failure;
        return Optional.ofNullable(users.get(userId));
    }
}
```

```java
class DeliveryManagerCheckerTest {

    private final FakeUserPort users = new FakeUserPort();
    private final DeliveryManagerChecker checker = new DeliveryManagerChecker(users);
    private final UUID userId = UUID.randomUUID();

    @Test
    void 배송담당자면_배정할_수_있다() {
        users.add(userId, true);
        assertThatCode(() -> checker.requireDeliveryManager(userId)).doesNotThrowAnyException();
    }

    @Test
    void 사용자는_있지만_담당자가_아니면_거절한다() {       // 200이지만 isDeliveryManager=false인 경우
        users.add(userId, false);
        assertThatThrownBy(() -> checker.requireDeliveryManager(userId))
                .isInstanceOf(BusinessException.class);   // 내 서비스 오류 code로
    }

    @Test
    void 없거나_탈퇴한_사용자면_거절한다() {
        assertThatThrownBy(() -> checker.requireDeliveryManager(userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void user_ms가_죽어_있으면_503으로_끝난다() {
        users.failWith(new RemoteServiceException("user-ms", 503));
        assertThatThrownBy(() -> checker.requireDeliveryManager(userId))
                .isInstanceOf(RemoteServiceException.class);   // 잡지 않으면 클라이언트에 503
    }
}
```

### 5.2 실제 테스트 – 자동: 진짜 Feign + 가짜 user-ms (WireMock)

준비(`wiremock-standalone`, 서비스 이름을 WireMock 주소로 연결)는 [[Feign 호출 사용법]] 7.2와 같습니다. 서비스 이름만 `user-ms`입니다.

```java
@SpringBootTest
class UserAdapterTest {

    static final WireMockServer USER_MS = new WireMockServer(options().dynamicPort());
    static { USER_MS.start(); }

    @DynamicPropertySource
    static void userMs(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.user-ms[0].uri", USER_MS::baseUrl);
    }

    @Autowired UserPort users;                           // 진짜 UserAdapter
    private final UUID userId = UUID.randomUUID();

    @AfterEach void reset() { USER_MS.resetAll(); }
    @AfterAll static void stop() { USER_MS.stop(); }

    @Test
    void 응답_필드를_읽어온다() {
        // 실제 user-ms 응답과 같은 모양 (4장). 필드 이름이 바뀌면 이 테스트가 깨져야 한다
        USER_MS.stubFor(get("/internal/v1/users/" + userId).willReturn(okJson("""
                {"id":"%s","name":"홍길동","role":"USER","isDeliveryManager":true,"slackId":"U07ABCDEF1","active":true}
                """.formatted(userId))));

        assertThat(users.findActiveUser(userId)).hasValueSatisfying(user -> {
            assertThat(user.deliveryManager()).isTrue();
            assertThat(user.slackId()).isEqualTo("U07ABCDEF1");
        });
    }

    @Test
    void 없는_사용자는_404_USER_NOT_FOUND이고_empty가_된다() {
        USER_MS.stubFor(get("/internal/v1/users/" + userId).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"USER_NOT_FOUND","message":"사용자를 찾을 수 없습니다."}
                        """)));

        assertThat(users.findActiveUser(userId)).isEmpty();      // Adapter가 UserErrorCode.USER_NOT_FOUND를 == 로 비교
    }

    @Test
    void user_ms_500은_RemoteServiceException() {
        USER_MS.stubFor(get("/internal/v1/users/" + userId).willReturn(serverError()));

        assertThatThrownBy(() -> users.findActiveUser(userId)).isInstanceOf(RemoteServiceException.class);
    }
}
```

- `USER_NOT_FOUND`는 common에 등록된 오류 코드라 `e.getErrorCode() == UserErrorCode.USER_NOT_FOUND`로 비교됩니다. 이 테스트가 그 비교까지 확인합니다.

### 5.3 실제 테스트 – 자동: 진짜 Feign + 진짜 user-ms (`@Tag("e2e")`)

user-ms는 이미 동작하므로 **지금 바로** 돌릴 수 있습니다. 설정(`-PincludeE2e` 태그, Eureka 다시 켜기)은 [[Feign 호출 사용법]] 7.3과 같고, 상대만 user-ms입니다.

```java
@Tag("e2e")
@SpringBootTest(properties = {
        "eureka.client.enabled=true",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=true",
        "eureka.client.service-url.defaultZone=${E2E_EUREKA_URL:http://localhost:8761/eureka/}"})
class UserAdapterE2ETest {

    @Autowired UserPort users;                           // 진짜 UserAdapter → Eureka → 실행 중인 user-ms

    @Test
    void 진짜_user_ms에서_사용자를_읽어온다() {
        UUID userId = UUID.fromString("<실제 사용자 UUID>");  // 예: 초기 MASTER의 id (5.4의 조회로 확인)
        assertThat(users.findActiveUser(userId)).hasValueSatisfying(user ->
                assertThat(user.name()).isNotBlank());
    }

    @Test
    void 진짜_user_ms에_없는_사용자는_empty() {           // 실제 404 USER_NOT_FOUND 본문이 empty로 바뀌는지
        assertThat(users.findActiveUser(UUID.randomUUID())).isEmpty();
    }
}
```

실행: Eureka와 user-ms를 띄운 뒤 `sh ./gradlew :delivery-ms:test --tests '*E2ETest' -PincludeE2e`

> ✅ 위 2개는 dev 복사본에서 별도 Eureka + 실제 user-ms(PostgreSQL)로 돌려 통과를 확인했습니다(2026-10-16).

### 5.4 실제 테스트 – 수동: 진짜 user-ms

```bash
# 1) 가입하고 ID 받기 (1장 계정)
USER_ID=$(curl -s -X POST localhost:19090/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"username":"dm01","password":"Abcd1234!","name":"김배송","slackId":"U0DM01","organizationType":"HUB","organizationName":"서울허브"}' | jq -r .id)

# 2) 내부 API 직접 확인 (user-ms 포트, Gateway로는 404)
curl -s localhost:19095/internal/v1/users/$USER_ID | jq
# → "isDeliveryManager": false

# 3) 배송담당자로 만들기: 지정 API(U16)가 아직 없으므로 지금은 DB에서 직접 (로컬 전용)
set -a; . ./.env; set +a
docker exec -it sparta-logistics-postgres psql -U "$DB_USER" -d "$DB_NAME" \
  -c "update auth.p_users set is_delivery_manager = true where username = 'dm01';"
curl -s localhost:19095/internal/v1/users/$USER_ID | jq .isDeliveryManager
# → true

# 4) 없는 사용자
curl -s localhost:19095/internal/v1/users/00000000-0000-0000-0000-0000000000ff | jq .code
# → "USER_NOT_FOUND"
```

그다음 내 서비스 API(예: 배송 담당자 배정)를 Gateway로 호출해, 담당자 → 성공 / 일반 사용자 → 거절 / user-ms를 끈 상태 → 503이 나오는지 확인합니다.

**그 밖의 테스트 도구**

- **Postman**: `docs/postman/sparta-logistics-gateway.postman_collection.json`. 가입부터 탈퇴까지 순서대로 실행하면 토큰이 자동으로 저장됩니다. 사용법은 [[Postman Gateway 테스트 가이드]].
- **가짜 토큰**: `python3 scripts/dev-token.py --role MASTER`. DB에 없는 사용자라 `/users/me`처럼 실제 사용자가 필요한 API는 404입니다. Gateway 통과 확인용으로만 쓰세요.

## 6. 막히면

- 필드 전체, 오류 코드 전체, 인증 흐름 그림: [[03 User·Auth API 가이드]]
- 내부 API 응답 필드 추가, 관리자 API 일정: **대진**에게
