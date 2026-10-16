# Slack 알림 사용법

> 작성: 대진 · 기준: `dev` d6af869 · 최종 수정: 2026-10-16
> 대상: 내 서비스에서 Slack 채널로 알림을 보내는 사람 (주로 order-ms 주문 완료 알림, 다울)
> 원리·설계 이유는 [[04 Slack 알림 가이드]]에 있습니다. 이 문서는 **따라 하기만** 합니다.

## 3줄 요약

1. `SlackNotifier`를 주입받아 `send(메시지)`를 부르면 끝입니다. **의존성·설정 추가는 없습니다.**
2. `send`는 **즉시 반환하고 예외를 던지지 않습니다.** Slack이 느리거나 죽어도 내 API는 영향을 받지 않습니다. 실패는 WARN 로그 한 줄로 남고 재시도하지 않습니다.
3. `.env`에 `SLACK_WEBHOOK_URL`이 없으면 **아무것도 보내지 않고** 조용히 넘어갑니다. 그래서 로컬에서는 설정 없이 그냥 개발하면 됩니다.

## 1. 보내기 (3단계)

```java
@Component
@RequiredArgsConstructor
public class SomethingNotifier {

    private final SlackNotifier slackNotifier;                 // ① 주입

    public void notifyDone(UUID id, String itemName) {
        SlackMessage message = SlackMessage.builder("작업 완료")  // ② 메시지 만들기
                .field("ID", id.toString())
                .field("항목", itemName)
                .reference(id.toString())                      //    로그 추적용, Slack에는 안 보임
                .build();
        slackNotifier.send(message);                           // ③ 보내기
    }
}
```

Slack에는 이렇게 보입니다.

```text
*[작업 완료]*
• ID: 3fa85f64-…
• 항목: 생수 500ml
```

| 빌더 메서드 | 설명 |
|---|---|
| `SlackMessage.builder("제목")` | 제목 필수 (비어 있으면 `IllegalArgumentException`) |
| `.field("라벨", "값")` | 최대 10개. 값이 `null`이면 `-`로 표시 |
| `.mention("U07ABCDEF1")` | Slack 멤버 ID로 멘션. `null`이거나 형식이 틀리면 멘션 없이 보냄 |
| `.reference("주문ID")` | 로그에만 남는 추적값 |

- 값에 줄바꿈·`<`·`>`가 있어도 안전하게 바꿔서 보냅니다. `<!channel>` 같은 값을 넣어도 전체 알림이 되지 않습니다.
- 메시지는 **고정 채널 1개**로만 갑니다. DM은 보낼 수 없습니다.

## 2. 커밋된 뒤에 보내기 (주문 완료처럼 DB 저장과 엮일 때)

트랜잭션 안에서 바로 `send`하면 **롤백돼도 알림이 나갑니다.** 이벤트를 발행하고 커밋 뒤에 받습니다.

```java
// ① 저장하는 @Transactional 메서드 안에서 발행 (밖에서 발행하면 리스너가 실행되지 않음)
order.complete();
eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), order.getOrderedBy(), ...));

// ② 커밋 뒤에 실행되는 리스너
@Component
@RequiredArgsConstructor
public class OrderCompletedSlackListener {
    private final SlackNotifier slackNotifier;
    private final UserSlackIdReader slackIdReader;     // user-ms 내부 API로 slackId 조회 (3장)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        slackNotifier.send(event.orderId().toString(), () -> SlackMessage.builder("주문 완료")
                .field("주문 ID", event.orderId().toString())
                .field("상품", event.productName() + " × " + event.quantity())
                .mention(slackIdReader.findSlackIdOrNull(event.orderedBy()))
                .reference(event.orderId().toString())
                .build());
    }
}
```

- `send(reference, () -> ...)` 형태는 메시지 조립까지 **알림 스레드에서** 합니다. 원격 조회(slackId 등)가 있으면 이 형태를 쓰세요. 그래야 user-ms가 느려도 내 요청이 기다리지 않습니다.
- 리스너에 `@Async`는 필요 없습니다.
- 상태가 실제로 **바뀔 때만** 발행하세요. 이미 완료된 주문을 다시 저장할 때 발행하면 알림이 두 번 갑니다.

## 3. 멘션할 Slack ID 가져오기

user-ms 내부 API의 `slackId`를 씁니다. 조회에 실패해도 **멘션만 빼고** 알림은 보내도록 예외를 잡습니다.

```java
public String findSlackIdOrNull(UUID userId) {
    try {
        return remote.execute("user-ms", () -> userClient.getUser(userId)).slackId();
    } catch (RemoteBusinessException | RemoteServiceException | RemoteTimeoutException e) {
        return null;   // 없음·장애·타임아웃 → 멘션 없이 발송
    }
}
```

Feign 클라이언트 만드는 법은 [[Feign 호출 사용법]], 응답 필드는 [[User·Auth API 사용법]] 4장을 보세요.

## 4. 로컬에서 실제로 받아 보기 (선택, 약 5분)

1. **개인 Slack 워크스페이스**를 만듭니다. 팀 워크스페이스는 앱 개수 제한으로 설치되지 않습니다.
2. https://api.slack.com/apps → **Create New App** → **From scratch** → 개인 워크스페이스 선택
3. **Incoming Webhooks** 켜기 → **Add New Webhook to Workspace** → 채널 선택
4. 받은 URL을 **본인 `.env`에만** 넣고 서비스를 다시 띄웁니다.

```properties
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T.../B.../...
```

- 기동 로그에 `Slack 알림 비활성`이 보이면 URL이 안 읽힌 것입니다.
- URL 형식이 틀리면(`http(s)://`가 아님) 서비스가 **기동하지 않습니다.**
- URL은 비밀번호와 같습니다. 커밋·공유하지 말고, 노출되면 Slack에서 지우고 새로 만드세요.
- 멘션을 보려면 개인 워크스페이스의 **본인 멤버 ID**(프로필 → ⋮ → 멤버 ID 복사)로 시험하세요.

## 5. 테스트하기: 개발 테스트 vs 실제 테스트

> 예시는 order-ms 주문 완료 알림입니다. `OrderCompletedEvent`, `OrderCompletedSlackListener`, `UserSlackIdReader` 같은 이름은 **예시**이니 실제 코드 이름으로 바꿔 주세요. common API(`SlackNotifier`, `SlackMessage`)는 실제와 같습니다.

| 구분 | 언제 돌리나 | `SlackNotifier` | 확인하는 것 |
|---|---|---|---|
| **개발 테스트** (5.2~5.4) | 매 빌드 (`sh ./gradlew :order-ms:test`) | **가짜** (`RecordingSlackNotifier`) | 메시지 내용, 언제 보내나(커밋 후·롤백 시), 멘션 실패 처리 |
| **실제 테스트 – 자동** (5.5) | 기능 완성 후, PR 전 | **진짜** common 발송기 → WireMock(가짜 Slack 서버) | 진짜 HTTP 요청 형식, Slack이 500이어도 주문이 안 깨지는지 |
| **실제 테스트 – 수동** (5.6) | 시연 전 1번 | **진짜** 발송기 → 진짜 Slack | 채널에 보이는 모양, 멘션 |

> ✅ 5.2~5.5의 테스트 코드는 dev(`d6af869`) 복사본에 예시 클래스를 만들어 **그대로 돌려 9개 모두 통과**를 확인했습니다(2026-10-16).

common이 이미 테스트한 것(이스케이프, 타임아웃, 대기열, 로그에 URL 없음)은 **다시 테스트하지 않습니다.** order-ms는 "언제, 무엇을 보내나"만 책임집니다.

### 5.1 테스트하기 쉬운 구조로 나누기

메시지 조립을 리스너에서 떼어 두면, 조립은 Spring 없이 단위 테스트로 끝납니다.

```java
// 운영 코드 (order-ms) ─────────────────────────────────────────────

// ① 메시지 조립만 담당 (Spring 없음)
public final class OrderCompletedMessage {
    public static SlackMessage of(OrderCompletedEvent e, String slackId) {
        return SlackMessage.builder("주문 완료")
                .field("주문 ID", e.orderId().toString())
                .field("상품", e.productName() + " × " + e.quantity())
                .field("요청사항", e.hasRequestNote() ? "있음" : "없음")
                .mention(slackId)                       // null이면 멘션 없이
                .reference(e.orderId().toString())
                .build();
    }
}

// ② slackId 조회 (infrastructure Adapter가 구현, 실패하면 null)
public interface UserSlackIdReader {
    String findSlackIdOrNull(UUID userId);
}

// ③ 리스너: 커밋 후 → 알림 스레드에서 조회·조립
@Component
@RequiredArgsConstructor
public class OrderCompletedSlackListener {
    private final SlackNotifier slackNotifier;
    private final UserSlackIdReader slackIdReader;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        slackNotifier.send(event.orderId().toString(),
                () -> OrderCompletedMessage.of(event, slackIdReader.findSlackIdOrNull(event.orderedBy())));
    }
}
```

### 5.2 개발 테스트 ① 메시지 내용 (단위)

```java
class OrderCompletedMessageTest {

    private final OrderCompletedEvent event = new OrderCompletedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "생수 500ml", 10, "A상사", "B마트", "서울 허브", "부산 허브", true);

    @Test
    void 필드와_멘션이_들어간다() {
        SlackMessage m = OrderCompletedMessage.of(event, "U07ABCDEF1");

        assertThat(m.title()).isEqualTo("주문 완료");
        assertThat(m.fields()).extracting(SlackField::label, SlackField::value)
                .contains(tuple("상품", "생수 500ml × 10"), tuple("요청사항", "있음"));
        assertThat(m.mentionSlackId()).isEqualTo("U07ABCDEF1");
        assertThat(m.reference()).isEqualTo(event.orderId().toString());
    }

    @Test
    void 주문자_UUID는_메시지에_넣지_않는다() {
        SlackMessage m = OrderCompletedMessage.of(event, null);

        assertThat(m.fields()).extracting(SlackField::value)
                .noneMatch(value -> value.contains(event.orderedBy().toString()));
        assertThat(m.mentionSlackId()).isNull();
    }
}
```

### 5.3 개발 테스트 ② 리스너 (단위, 가짜 알림기)

> 5.3~5.5의 `event`는 5.2와 같은 `OrderCompletedEvent` 픽스처입니다.

가짜 알림기는 테스트 공용으로 한 번 만들어 둡니다(`src/test/java/com/order/fake/`). 진짜 발송기와 달리 **그 자리에서** 메시지를 만들기 때문에 기다릴 필요가 없습니다.

```java
public class RecordingSlackNotifier implements SlackNotifier {
    public final List<SlackMessage> sent = new CopyOnWriteArrayList<>();

    @Override public void send(SlackMessage message) { sent.add(message); }
    @Override public void send(String reference, Supplier<SlackMessage> message) { sent.add(message.get()); }
}
```

```java
class OrderCompletedSlackListenerTest {

    private final RecordingSlackNotifier slack = new RecordingSlackNotifier();

    @Test
    void 주문자의_slackId로_멘션한다() {
        new OrderCompletedSlackListener(slack, userId -> "U07ABCDEF1").on(event);

        assertThat(slack.sent).singleElement()
                .extracting(SlackMessage::mentionSlackId).isEqualTo("U07ABCDEF1");
    }

    @Test
    void slackId를_못_가져오면_멘션_없이_보낸다() {
        new OrderCompletedSlackListener(slack, userId -> null).on(event);   // user-ms 404·장애 → Adapter가 null

        assertThat(slack.sent).singleElement()
                .extracting(SlackMessage::mentionSlackId).isNull();
    }
}
```

### 5.4 개발 테스트 ③ 커밋 후에만 보내나 (통합, 가짜 알림기)

`AFTER_COMMIT`은 **진짜 트랜잭션이 커밋돼야** 실행되므로 Spring을 띄워 확인합니다. 주문 전체 흐름(재고·배송 호출) 없이 **이벤트 발행 → 리스너 연결**만 따로 확인하면 WireMock도 필요 없습니다.

```java
@SpringBootTest
class OrderCompletedSlackListenerIntegrationTest {      // ⚠️ 클래스·메서드에 @Transactional 붙이지 않기 (항상 롤백되어 알림 0건)

    @TestConfiguration
    static class Fakes {
        @Bean RecordingSlackNotifier slackNotifier() { return new RecordingSlackNotifier(); }   // 등록하면 common 자동 설정이 물러남
        @Bean @Primary UserSlackIdReader slackIdReader() { return userId -> "U07ABCDEF1"; }    // user-ms Feign 대신
    }

    @Autowired RecordingSlackNotifier slack;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher publisher;

    @AfterEach
    void clear() { slack.sent.clear(); }

    @Test
    void 커밋되면_1건_보낸다() {
        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        assertThat(slack.sent).hasSize(1);               // AFTER_COMMIT은 커밋한 스레드에서 바로 실행됨
    }

    @Test
    void 롤백되면_보내지_않는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publishEvent(event);
            throw new IllegalStateException("저장 뒤 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(slack.sent).isEmpty();
    }

    @Test
    void 트랜잭션_밖에서_발행하면_리스너가_실행되지_않는다() {   // 발행 위치를 잘못 잡은 버그를 잡아 줌
        publisher.publishEvent(event);

        assertThat(slack.sent).isEmpty();
    }
}
```

"이미 완료된 주문을 다시 저장해도 이벤트가 한 번만 나가나"는 Slack이 아니라 **주문 도메인 규칙**이므로, 주문 엔티티나 서비스 테스트에서 `complete()`를 두 번 불러 이벤트가 한 번만 쌓이는지로 확인합니다.

### 5.5 실제 테스트 – 자동: 진짜 발송기 + 가짜 Slack 서버 (WireMock)

이번에는 `SlackNotifier`를 바꾸지 않습니다. common의 **진짜 발송기**가 진짜 HTTP 요청을 보내고, 받는 쪽만 WireMock입니다.

```groovy
// order-ms build.gradle
testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
testImplementation 'org.awaitility:awaitility'          // 버전은 Spring Boot가 관리
```

```java
@SpringBootTest
class OrderSlackWebhookTest {

    static final WireMockServer SLACK = new WireMockServer(options().dynamicPort());
    static { SLACK.start(); }

    @DynamicPropertySource
    static void slack(DynamicPropertyRegistry registry) {
        // 이 값이 있으면 common이 NoOp 대신 진짜 발송기를 만든다 (.env의 값보다 우선)
        registry.add("common.notification.slack.webhook-url", () -> SLACK.baseUrl() + "/hook");
    }

    @TestConfiguration
    static class Fakes {
        @Bean @Primary UserSlackIdReader slackIdReader() { return userId -> "U07ABCDEF1"; }
    }

    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher publisher;

    @AfterEach void reset() { SLACK.resetAll(); }
    @AfterAll static void stop() { SLACK.stop(); }

    @Test
    void 커밋되면_Slack_형식으로_보낸다() {
        SLACK.stubFor(post("/hook").willReturn(ok()));

        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        // 알림은 별도 스레드에서 나가므로 잠깐 기다린다
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                SLACK.verify(1, postRequestedFor(urlEqualTo("/hook"))
                        .withHeader("Content-Type", containing("application/json"))
                        .withRequestBody(matchingJsonPath("$.text", containing("*[주문 완료]*")))
                        .withRequestBody(matchingJsonPath("$.text", containing("<@U07ABCDEF1>")))));
    }

    @Test
    void Slack이_500이어도_예외_없이_끝난다() {
        SLACK.stubFor(post("/hook").willReturn(serverError()));

        assertThatCode(() -> tx.executeWithoutResult(status -> publisher.publishEvent(event)))
                .doesNotThrowAnyException();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                SLACK.verify(1, postRequestedFor(urlEqualTo("/hook"))));   // 보내긴 했고, 재시도는 없음
    }
}
```

- 주문 API까지 엮어서 "Slack이 500이어도 주문 생성 API가 성공"을 보고 싶다면, 위 설정에 재고·배송 WireMock(05 가이드 4장)을 더하고 `tx…publishEvent` 대신 MockMvc로 주문 API를 호출합니다.
- `.withFixedDelay(5000)`으로 Slack을 느리게 만들어도, 주문 쪽 코드는 기다리지 않고 바로 끝나야 합니다.

### 5.6 실제 테스트 – 수동: 진짜 Slack 채널

1. 4장대로 개인 Webhook을 만들어 `.env`에 `SLACK_WEBHOOK_URL`을 넣습니다.
2. 발송 성공 로그(DEBUG)까지 보려면 그 서비스를 이렇게 띄웁니다: `sh ./gradlew :order-ms:bootRun --args='--logging.level.com.common.notification=DEBUG'` (실패 WARN은 설정 없이도 나옵니다)
3. 필요한 서비스를 띄우고([[05 통합 테스트 가이드]] 6장) Postman으로 주문을 하나 완료시킵니다.
4. 확인할 것:

| 확인 | 기대 |
|---|---|
| Slack 채널 | `[주문 완료]` 1건, 필드 줄바꿈 정상, 본인 멤버 ID면 멘션 표시 |
| 서비스 로그 | `Slack 알림 발송 ref=<주문ID> requestId=<요청ID>` (DEBUG) |
| 실패시키기: URL 끝을 바꿔 재기동 | 주문은 정상 완료, 로그에 WARN `Slack 알림 실패 … status=404` |
| 재고 부족으로 주문 실패 | Slack에 아무것도 안 옴 |

## 6. 하지 말 것

- 메시지에 **연락처, 상세 주소, 요청사항 원문, 토큰·비밀번호** 넣지 않기 (요청사항은 "있음/없음"만)
- 트랜잭션 안에서 바로 `send`하지 않기 → 2장 방식
- `send`를 `try/catch`로 감싸지 않기 (예외를 던지지 않습니다)
- 실패했다고 직접 재시도하지 않기 (알림은 한 번 시도하고 끝이 원칙)
- Slack 호출을 Feign으로 만들지 않기 (`@FeignClient(url)`이면 기동 실패)

## 7. 막히면

| 증상 | 확인 |
|---|---|
| 알림이 안 옴, 오류도 없음 | `.env`에 `SLACK_WEBHOOK_URL`이 있는지, 기동 로그에 `Slack 알림 비활성`이 있는지 |
| 리스너가 실행되지 않음 | 이벤트를 `@Transactional` 메서드 **안에서** 발행했는지 |
| WARN `Slack 알림 실패 ... status=404` | Webhook이 삭제됨 → 새로 발급 |
| WARN `Slack 알림 메시지 조립 실패` | Supplier 안에서 예외 발생 (예: slackId 조회 예외를 안 잡음) |
| 멘션이 이름 대신 ID로 보임 | 그 워크스페이스에 없는 멤버 ID |

- 더 자세한 설명: [[04 Slack 알림 가이드]] (스레드·타임아웃, 로그 정책, 미결 항목)
- 설계: [[NTF 주문 알림 설계]]
