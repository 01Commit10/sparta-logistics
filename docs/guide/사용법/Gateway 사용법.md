# Gateway 사용법

> 작성: 대진 · 기준: `dev` d6af869 · 최종 수정: 2026-10-16
> 대상: 서비스 개발자 (내 Controller에서 로그인 사용자를 받는 사람)
> 원리·설계 이유는 [[01 Gateway·인증 가이드]]에 있습니다. 이 문서는 **따라 하기만** 합니다.

## 3줄 요약

1. 모든 외부 요청은 Gateway(`localhost:19090`)로 들어옵니다. Gateway가 토큰을 검사하고, 통과한 요청에만 `X-User-Id`·`X-User-Role` 헤더를 붙여 서비스로 보냅니다.
2. 서비스는 **JWT를 다루지 않습니다.** Controller 파라미터에 `@CurrentUser AuthUser user`만 붙이면 됩니다.
3. Gateway는 "로그인했나", "MASTER 전용 주소인가"까지만 막습니다. **"이 데이터가 이 사람 것인가"는 서비스가 검사**합니다.

## 1. 현재 사용자 받기

```java
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;

@RestController
@RequestMapping("/api/v1/hubs")
public class HubController {

    @GetMapping                                            // 로그인만 필요
    public List<HubResponse> list(@CurrentUser AuthUser user) { ... }

    @PatchMapping("/{hubId}")                              // Gateway가 MASTER 전용으로 먼저 막음
    public HubResponse update(@CurrentUser AuthUser user, @PathVariable UUID hubId,
                              @Valid @RequestBody UpdateHubRequest request) {
        user.requireMaster();                              // 한 번 더 확인 (권장)
        return HubResponse.from(hubFacade.update(request.toCommand(hubId, user.id())));
    }
}
```

| 쓰고 싶은 것 | 코드 | 결과 |
|---|---|---|
| 사용자 ID | `user.id()` | `UUID` |
| 역할 | `user.role()` / `user.isMaster()` | `AuthRole.MASTER` / `USER` |
| MASTER만 허용 | `user.requireMaster()` | 아니면 403 `ACCESS_DENIED` |
| 본인 또는 MASTER만 허용 | `user.requireSelfOrMaster(ownerId)` | 아니면 403 `ACCESS_DENIED` |
| 로그인 안 해도 되는 API | `@CurrentUser(required = false) AuthUser user` | 헤더가 없으면 `null` |
| Controller 밖에서 | `CurrentUserProvider.current()` | `Optional<AuthUser>` |

- `AuthUser`는 Controller에서만 받고, application에는 **`user.id()` 값만** Command에 넣어 넘깁니다.
- 추가 설정은 없습니다. common 의존성만 있으면 자동으로 켜집니다.
- 배송담당자 여부, 소속 업체·허브는 헤더에 **없습니다.** 필요하면 [[User·Auth API 사용법]] 4장의 내부 API로 조회하세요.

## 2. 내 API 경로 확인하기

### 2.1 라우팅 (어느 서비스로 가나)

| 경로 | 서비스 | 로컬 포트 |
|---|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**` | user-ms | 19095 |
| `/api/v1/companies/**`, `/api/v1/products/**` | company-ms | 19091 |
| `/api/v1/hubs/**`, `/api/v1/hub-routes/**`, `/api/v1/inventories/**` | hub-ms | 19093 |
| `/api/v1/orders/**` | order-ms | 19094 |
| `/api/v1/deliveries/**`, `/api/v1/delivery-routes/**` | delivery-ms | 19092 |

- 경로는 **그대로** 전달됩니다. Controller도 `/api/v1/...` 전체 경로로 매핑하세요.
- 표에 없는 경로는 404입니다. 새 경로가 필요하면 **대진에게 경로 접두사와 서비스 이름**을 알려 주세요.

### 2.2 내 API는 어떤 종류인가

| 종류 | 예 | Gateway 동작 | 서비스에서 할 일 |
|---|---|---|---|
| 공개 | `POST /api/v1/auth/login` 등 4개 | 토큰 검사 안 함, `X-User-*` 안 붙임 | `@CurrentUser` 필수로 쓰지 않기 |
| 로그인 필요 (기본값) | 재고·주문·배송 API 전부 | 토큰 검사 후 통과 | 본인 데이터인지 직접 검사 |
| MASTER 전용 | `PATCH /api/v1/hubs/*`, `POST /api/v1/companies` 등 | USER면 403 | `requireMaster()`로 한 번 더 |
| 내부 | `/internal/**` | **외부에서 오면 404** | 다른 서비스가 Feign으로만 호출 |

- MASTER 전용 경로 전체 목록은 [[01 Gateway·인증 가이드]] 4.2에 있습니다. **내 API가 목록과 다르면 알려 주세요.**
- 내부 API는 `/internal/v1/...`로 만듭니다. 스케줄러에서 온 호출에는 사용자 헤더가 없으므로 **필수 `@CurrentUser`를 쓰지 마세요.**

## 3. 로컬에서 호출해 보기

### 3.1 Gateway로 (토큰 필요)

```bash
GW=http://localhost:19090

# 1) 로그인해서 토큰 받기 (MASTER는 .env의 MASTER_USERNAME / MASTER_PASSWORD)
ACCESS=$(curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user01","password":"Abcd1234!"}' | jq -r .accessToken)

# 2) 내 API 호출
curl -i $GW/api/v1/hubs -H "Authorization: Bearer $ACCESS"
```

- 계정이 없으면 먼저 가입하세요: [[User·Auth API 사용법]] 1장.
- 토큰은 **30분**이면 만료됩니다(`TOKEN_EXPIRED`). 다시 로그인하거나 재발급하세요.
- 로그인 없이 빠르게: `python3 scripts/dev-token.py --role MASTER` (DB에 없는 가짜 사용자라 통과 확인용으로만 쓰세요).
- Postman: [[Postman Gateway 테스트 가이드]]

### 3.2 서비스 포트로 직접 (Gateway 없이)

```http
GET http://localhost:19093/api/v1/hubs
X-User-Id: 00000000-0000-0000-0000-000000000001
X-User-Role: MASTER
```

- 헤더는 **둘 다 넣거나 둘 다 빼세요.** 하나만 넣거나 형식이 틀리면(UUID 아님, `master` 소문자 등) 모든 API가 401입니다.

## 4. 이런 응답이 오면

| 응답 | 이유 | 할 일 |
|---|---|---|
| 401 `TOKEN_MISSING` | `Authorization` 헤더 없음 | `Bearer {accessToken}` 넣기 |
| 401 `TOKEN_EXPIRED` | 30분 지남 | 재발급 또는 재로그인 |
| 401 `TOKEN_INVALID` | 토큰 위조·형식 오류, Refresh Token을 넣음 | Access Token인지 확인 |
| 401 `TOKEN_REVOKED` | 로그아웃·비밀번호 변경·탈퇴한 토큰 | 재로그인 |
| 401 `UNAUTHENTICATED` | 서비스에 사용자 헤더가 없거나 형식이 틀림 | Gateway를 거치거나 헤더 두 개를 바르게 |
| 403 `ACCESS_DENIED` | MASTER 전용 경로, `requireMaster()` 실패 | 역할 확인 |
| 403 `PASSWORD_CHANGE_REQUIRED` | 관리자가 비밀번호를 초기화함 | 비밀번호부터 변경 |
| 400 `INVALID_INPUT` | 경로에 `;`, `%2f`, `..` 같은 이상한 값 | 정상 경로로 호출 |
| 404 `RESOURCE_NOT_FOUND` | 라우트 없음, 또는 외부에서 `/internal/**` 호출 | 경로 확인 |
| 503 `AUTH_UNAVAILABLE` | Redis가 꺼짐 | `docker compose up -d redis` |
| 503 `SERVICE_UNAVAILABLE` | 서비스가 안 떠 있음, 막 떠서 아직 못 찾음, 계속 실패해서 Gateway가 잠시 차단 | 서비스 기동 확인, 5~10초 뒤 재시도 |
| 504 `SERVICE_TIMEOUT` | 서비스가 5초(주문 생성 경로는 15초) 안에 응답 안 함 | 느린 쿼리·외부 호출 확인 |

- 서비스가 보낸 오류(400, 409 등)는 Gateway가 **그대로** 전달합니다.
- 응답 헤더의 `X-Request-Id`로 Gateway·서비스 로그를 같은 요청끼리 묶어 찾을 수 있습니다.

## 5. 테스트하기: 개발 테스트 vs 실제 테스트

> 예시는 hub-ms 허브 API입니다. `HubController`, `HubFacade`, 경로는 **예시**이니 내 API로 바꿔 주세요. `@CurrentUser`, `AuthUser`, `AuthContract`, `AuthRole`은 실제 common 코드입니다.

| 구분 | 언제 돌리나 | 로그인 사용자는 | 확인하는 것 |
|---|---|---|---|
| **개발 테스트** (5.1·5.2) | 매 빌드 | 요청에 `X-User-Id`·`X-User-Role` 헤더 **직접** 넣기 (토큰·Gateway 없음) | 내 서비스의 권한 검사(`requireMaster`, 본인 확인), 401·403 응답 |
| **실제 테스트 – 수동** (5.3) | 기능 완성 후, PR 전 | **진짜 로그인 토큰**으로 Gateway 경유 | 라우팅, Gateway의 MASTER 경로 차단, 헤더 전달까지 한 번에 |

Gateway 자체(토큰 검증, 경로 정책)는 gateway-edge 테스트가 이미 확인합니다. 내 서비스는 **"헤더가 이렇게 오면 이렇게 응답한다"** 만 확인하면 됩니다.

### 5.1 개발 테스트 ① Controller만 (`@WebMvcTest`, 가장 빠름)

`@WebMvcTest`에도 `@CurrentUser` 처리와 공통 오류 응답이 자동으로 들어갑니다. Facade는 가짜(`@MockitoBean`)로 바꿉니다.

```java
@WebMvcTest(HubController.class)
class HubControllerTest {

    private static final String MASTER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final UUID HUB_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean HubFacade hubFacade;

    /** 로그인 사용자 = 헤더 두 개. Gateway가 넣어 주는 것과 같은 모양 */
    private static RequestPostProcessor loginAs(String userId, AuthRole role) {
        return request -> {
            request.addHeader(AuthContract.HEADER_USER_ID, userId);
            request.addHeader(AuthContract.HEADER_USER_ROLE, role.name());
            return request;
        };
    }

    @Test
    void MASTER는_수정할_수_있고_작업자_ID가_Facade로_간다() throws Exception {
        given(hubFacade.update(any())).willReturn(new HubResult(HUB_ID, "경기남부허브"));

        mockMvc.perform(patch("/api/v1/hubs/{id}", HUB_ID).with(loginAs(MASTER_ID, AuthRole.MASTER))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "name": "경기남부허브" }
                                """))
                .andExpect(status().isOk());

        then(hubFacade).should().update(argThat(command -> command.actorId().toString().equals(MASTER_ID)));
    }

    @Test
    void USER는_403이고_Facade를_부르지_않는다() throws Exception {
        mockMvc.perform(patch("/api/v1/hubs/{id}", HUB_ID).with(loginAs(USER_ID, AuthRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"name\": \"x\" }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        then(hubFacade).shouldHaveNoInteractions();
    }

    @Test
    void 헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/hubs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 헤더가_하나만_있으면_401() throws Exception {      // Gateway를 거치지 않은 위조 요청으로 봄
        mockMvc.perform(get("/api/v1/hubs").header(AuthContract.HEADER_USER_ID, USER_ID))
                .andExpect(status().isUnauthorized());
    }
}
```

### 5.2 개발 테스트 ② DB까지 (`@SpringBootTest`, H2)

"본인 데이터만", "감사 컬럼(`created_by`)이 로그인 사용자로 채워지나"는 DB까지 가야 확인됩니다.

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderOwnershipIntegrationTest {      // loginAs()·ID 상수는 5.1과 같음 (예시는 order-ms)

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 남의_주문은_403_본인_주문은_200() throws Exception {
        String orderId = createOrderAs(USER_A);                       // 헬퍼: POST 후 id 꺼내기

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(loginAs(USER_B, AuthRole.USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(loginAs(USER_A, AuthRole.USER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(loginAs(MASTER_ID, AuthRole.MASTER)))
                .andExpect(status().isOk());
    }

    @Test
    void 만든_사람이_created_by에_기록된다() throws Exception {
        String orderId = createOrderAs(USER_A);

        assertThat(jdbc.queryForObject("select created_by from orders.p_orders where id = ?",
                UUID.class, UUID.fromString(orderId))).hasToString(USER_A);   // 테이블 이름은 예시
    }
}
```

- 서비스 단위 테스트(Facade·Service)는 헤더가 아니라 Command의 `actorId`로 테스트합니다. `AuthUser`는 Controller까지만 옵니다.

### 5.3 실제 테스트 – 수동: 진짜 토큰으로 Gateway 경유

Eureka, user-ms, 내 서비스, Gateway를 띄운 뒤([[05 통합 테스트 가이드]] 6장) 아래 스크립트를 터미널에 붙여 넣으면 PASS/FAIL이 한 줄씩 나옵니다. MASTER 계정은 `.env`의 `MASTER_USERNAME`·`MASTER_PASSWORD`입니다.

```bash
GW=http://localhost:19090
login() { curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' \
            -d "{\"username\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }
check() { expected=$1; shift
          actual=$(curl -s -o /dev/null -w '%{http_code}' "$@")
          [ "$actual" = "$expected" ] && echo "PASS $expected  $*" || echo "FAIL 기대=$expected 실제=$actual  $*"; }

set -a; . ./.env; set +a                       # 프로젝트 루트에서: .env의 MASTER_USERNAME·MASTER_PASSWORD 읽기
MASTER=$(login "$MASTER_USERNAME" "$MASTER_PASSWORD")
USER=$(login user01 'Abcd1234!')               # User·Auth API 사용법 1장으로 가입한 계정
HUB_ID=<테스트할 허브 UUID>

check 401 $GW/api/v1/hubs                                                          # 토큰 없음
check 200 $GW/api/v1/hubs -H "Authorization: Bearer $USER"                         # 로그인만 필요
check 403 -X PATCH $GW/api/v1/hubs/$HUB_ID -H "Authorization: Bearer $USER" \
          -H 'Content-Type: application/json' -d '{"name":"x"}'                    # USER → MASTER 전용 (Gateway가 막음)
check 200 -X PATCH $GW/api/v1/hubs/$HUB_ID -H "Authorization: Bearer $MASTER" \
          -H 'Content-Type: application/json' -d '{"name":"경기남부허브"}'          # MASTER 통과
check 404 $GW/internal/v1/hubs/$HUB_ID -H "Authorization: Bearer $MASTER"           # 내부 API는 외부에서 404
check 200 $GW/api/v1/hubs -H "Authorization: Bearer $USER" -H 'X-User-Role: MASTER' -H 'X-User-Id: x'   # 위조 헤더는 Gateway가 지움
```

- 마지막 줄: 클라이언트가 넣은 가짜 `X-User-*`는 Gateway가 지우고 진짜 값으로 다시 넣으므로 200이 정상입니다. 401이 나오면 위조 헤더가 서비스까지 새어 들어간 것이니 대진에게 알려 주세요.
- 403 확인 줄에서 200이 나오면 Gateway 정책표에 내 경로가 없는 것입니다(2.2). 서비스의 `requireMaster()`가 403을 내 주는지도 같은 줄로 확인됩니다.

Postman으로 남겨 두려면 같은 요청을 컬렉션 57번부터 추가하고 Tests 탭에 기대값을 적습니다([[Postman Gateway 테스트 가이드]] 7장).

## 6. 하지 말 것

- 서비스에서 JWT를 파싱하거나 `Authorization` 헤더를 읽지 않기
- `"X-User-Id"` 문자열을 직접 쓰지 않기 → `AuthContract.HEADER_USER_ID` 상수 사용
- Gateway가 막아 준다고 서비스 권한 검사를 빼지 않기 (본인 데이터, 업체 관리자, 허브 담당자는 서비스 몫)
- 내부 API Controller에 필수 `@CurrentUser` 붙이지 않기

## 7. 막히면

- 더 자세한 설명: [[01 Gateway·인증 가이드]] (요청 흐름, 헤더 규약, 감사 컬럼)
- 실행 방법: [[로컬 실행·라우팅 확인 가이드]]
- 경로 추가, MASTER 전용 여부 변경, 타임아웃 조정: **대진**에게
