package com.common.notification.slack;

import com.common.auth.AuthContract;
import com.common.notification.SlackMessage;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class SlackWebhookNotifierTest {

    static final WireMockServer SLACK = new WireMockServer(options().dynamicPort());
    static final String HOOK_PATH = "/services/T000/B000/secret-token";

    static {
        SLACK.start();
    }

    SlackWebhookNotifier notifier;

    @BeforeEach
    void setUp() {
        SLACK.resetAll();
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withBody("ok")));
        notifier = new SlackWebhookNotifier(properties());
    }

    @AfterEach
    void tearDown() {
        notifier.destroy();
    }

    @AfterAll
    static void stop() {
        SLACK.stop();
    }

    @Test
    @DisplayName("3-1. Webhook URL로 JSON {\"text\": 렌더 결과}를 POST한다")
    void postsRenderedText() {
        SlackMessage message = SlackMessage.builder("주문 완료")
                .field("상품", "생수 × 10")
                .mention("U07ABCDEF1")
                .reference("order-1")
                .build();

        notifier.send(message);

        awaitRequests(1);
        SLACK.verify(postRequestedFor(urlEqualTo(HOOK_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"text": "*[주문 완료]* <@U07ABCDEF1>\\n• 상품: 생수 × 10"}
                        """)));
    }

    @Test
    @DisplayName("3-2. send(reference, supplier)는 메시지 조립을 호출한 스레드가 아닌 알림 스레드에서 한다")
    void buildsMessageOnNotificationThread() {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> builder = new AtomicReference<>();

        notifier.send("order-1", () -> {
            builder.set(Thread.currentThread());
            return SlackMessage.builder("주문 완료").build();
        });

        awaitRequests(1);
        assertThat(builder.get()).isNotNull().isNotSameAs(caller);
        SLACK.verify(postRequestedFor(urlEqualTo(HOOK_PATH))
                .withRequestBody(equalToJson("{\"text\": \"*[주문 완료]*\"}")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 429, 500, 503})
    @DisplayName("4-1. Slack이 4xx·5xx를 주면 예외 없이 WARN에 ref·status를 남긴다")
    void httpError(int status, CapturedOutput output) {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(status).withBody("invalid_token")));

        assertThatCode(() -> notifier.send(orderMessage())).doesNotThrowAnyException();

        awaitLog(output, "Slack 알림 실패");
        assertThat(lineOf(output, "Slack 알림 실패"))
                .contains("WARN", "ref=order-1", "status=" + status);
    }

    @Test
    @DisplayName("4-2. 실패 로그에 Webhook URL·메시지 본문·Slack 응답 본문·스택 트레이스가 없다")
    void noSecretInLogs(CapturedOutput output) {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(500).withBody("invalid_token")));

        notifier.send(orderMessage());

        awaitLog(output, "Slack 알림 실패");
        assertNoSecret(output);
    }

    @Test
    @DisplayName("4-3. 응답이 read 타임아웃(2s)보다 늦으면 포기하고 WARN에 원인 클래스명만 남긴다")
    void readTimeout(CapturedOutput output) {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(3_000)));
        long start = System.nanoTime();

        notifier.send(orderMessage());

        awaitLog(output, "Slack 알림 실패");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2_900));
        assertThat(lineOf(output, "Slack 알림 실패")).contains("ref=order-1", "cause=SocketTimeoutException");
        assertNoSecret(output);
    }

    @Test
    @DisplayName("4-4. 연결이 거부되면 WARN에 원인 클래스명만 남긴다")
    void connectionRefused(CapturedOutput output) throws IOException {
        notifier.destroy();
        notifier = new SlackWebhookNotifier(properties("http://localhost:" + closedPort() + HOOK_PATH));

        notifier.send(orderMessage());

        awaitLog(output, "Slack 알림 실패");
        assertThat(lineOf(output, "Slack 알림 실패")).contains("ref=order-1", "cause=ConnectException");
        assertThat(output.getAll()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("4-5. supplier가 예외를 던지면 보내지 않고 WARN에 원인 클래스명만 남긴다 (예외 메시지는 남기지 않음)")
    void supplierFails(CapturedOutput output) {
        assertThatCode(() -> notifier.send("order-1", () -> {
            throw new IllegalStateException("010-1234-5678 개인정보가 담긴 메시지");
        })).doesNotThrowAnyException();

        awaitLog(output, "Slack 알림 메시지 조립 실패");
        assertThat(lineOf(output, "Slack 알림 메시지 조립 실패"))
                .contains("WARN", "ref=order-1", "cause=IllegalStateException");
        assertThat(output.getAll()).doesNotContain("010-1234-5678").doesNotContain("\tat ");
        assertThat(SLACK.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("5-1. Slack이 느려도(3s) send는 기다리지 않고 바로 반환한다")
    void returnsImmediately() {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(3_000)));
        long start = System.nanoTime();

        notifier.send(orderMessage());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("5-2. 대기열이 가득 차면 예외 없이 버리고 WARN을 남긴다")
    void rejectsWhenQueueFull(CapturedOutput output) {
        replaceNotifier(new SlackProperties(SLACK.baseUrl() + HOOK_PATH,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 1, Duration.ofMillis(100)));
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(1_000)));

        assertThatCode(() -> {
            notifier.send(SlackMessage.builder("1").reference("order-1").build());   // 실행 중
            notifier.send(SlackMessage.builder("2").reference("order-2").build());   // 대기열
            notifier.send(SlackMessage.builder("3").reference("order-3").build());   // 버림
        }).doesNotThrowAnyException();

        assertThat(lineOf(output, "Slack 알림 버림")).contains("WARN", "ref=order-3");
    }

    @Test
    @DisplayName("5-3. 호출한 스레드의 MDC를 알림 스레드에 복사하고, 끝나면 지운다")
    void copiesMdc() {
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<String> leftover = new AtomicReference<>("not-run");
        replaceNotifier(new SlackProperties(SLACK.baseUrl() + HOOK_PATH,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 10, Duration.ofSeconds(1)));

        MDC.put("traceId", "trace-1");
        try {
            notifier.send("order-1", () -> {
                seen.set(MDC.get("traceId"));
                return orderMessage();
            });
        } finally {
            MDC.remove("traceId");
        }
        notifier.send("order-2", () -> {
            leftover.set(MDC.get("traceId"));
            return orderMessage();
        });

        awaitRequests(2);
        assertThat(seen.get()).isEqualTo("trace-1");
        assertThat(leftover.get()).isNull();
    }

    @Test
    @DisplayName("5-4. 요청 안에서 호출하면 X-Request-Id를 WARN에 requestId로 남기고, 요청 밖이면 requestId=-")
    void logsRequestId(CapturedOutput output) {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(500)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthContract.HEADER_REQUEST_ID, "req-1");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            notifier.send(SlackMessage.builder("주문 완료").reference("order-1").build());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        notifier.send(SlackMessage.builder("주문 완료").reference("order-2").build());

        await().atMost(Duration.ofSeconds(5)).until(() -> output.getAll().contains("ref=order-2"));
        assertThat(lineOf(output, "ref=order-1")).contains("requestId=req-1", "status=500");
        assertThat(lineOf(output, "ref=order-2")).contains("requestId=-");
    }

    @Test
    @DisplayName("5-5. 종료 시 대기 중인 알림을 shutdownWait까지 보내고, 그 이상은 기다리지 않는다")
    void drainsOnShutdown() {
        replaceNotifier(new SlackProperties(SLACK.baseUrl() + HOOK_PATH,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 10, Duration.ofSeconds(2)));
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(300)));

        notifier.send(orderMessage());
        notifier.send(orderMessage());
        notifier.destroy();

        assertThat(SLACK.getAllServeEvents()).hasSize(2);

        replaceNotifier(new SlackProperties(SLACK.baseUrl() + HOOK_PATH,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 10, Duration.ofMillis(200)));
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(1_500)));
        notifier.send(orderMessage());
        notifier.send(orderMessage());
        long start = System.nanoTime();

        notifier.destroy();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("5-5b. 종료된 뒤 send를 호출해도 예외 없이 버리고 WARN을 남긴다")
    void sendAfterShutdown(CapturedOutput output) {
        notifier.destroy();

        assertThatCode(() -> notifier.send(orderMessage())).doesNotThrowAnyException();

        assertThat(lineOf(output, "Slack 알림 버림")).contains("ref=order-1");
    }

    @Test
    @DisplayName("5-6. 로그의 ref·requestId는 줄바꿈을 지우고 64자로 자른다 (로그 위조 방지)")
    void sanitizesLogValues(CapturedOutput output) {
        SLACK.stubFor(post(urlEqualTo(HOOK_PATH)).willReturn(aResponse().withStatus(500)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthContract.HEADER_REQUEST_ID, "req-1\r\nWARN 가짜 로그");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            notifier.send(SlackMessage.builder("주문 완료").reference("order-1\nWARN 가짜 로그" + "x".repeat(100)).build());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        awaitLog(output, "Slack 알림 실패");
        String line = lineOf(output, "Slack 알림 실패");
        assertThat(line).contains("requestId=req-1  WARN 가짜 로그", "ref=order-1 WARN 가짜 로그");
        assertThat(output.getAll().lines().filter(l -> l.startsWith("WARN 가짜 로그"))).isEmpty();
        String ref = line.substring(line.indexOf("ref=") + 4, line.indexOf(" requestId="));
        assertThat(ref).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://hooks.slack.com/services/T000/B000/secret-token",
            "hooks.slack.com/services/T000/B000/secret-token",
            "https:///services/T000/B000/secret-token",
            "https://hooks.slack.com/services/T000/B000/secret token",
            "  "})
    @DisplayName("6-2. URL이 http(s)·호스트가 있는 형식이 아니면 만들 때 IllegalArgumentException, 메시지에 URL 값은 없다 (P3)")
    void invalidUrl(String webhookUrl) {
        assertThatThrownBy(() -> new SlackWebhookNotifier(properties(webhookUrl)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("common.notification.slack.webhook-url")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"))
                .hasNoCause();
    }

    void replaceNotifier(SlackProperties properties) {
        notifier.destroy();
        notifier = new SlackWebhookNotifier(properties);
    }

    static SlackMessage orderMessage() {
        return SlackMessage.builder("주문 완료").field("상품", "생수 × 10").reference("order-1").build();
    }

    static void assertNoSecret(CapturedOutput output) {
        assertThat(output.getAll())
                .doesNotContain("secret-token")      // Webhook URL
                .doesNotContain("생수")               // 메시지 본문
                .doesNotContain("invalid_token")     // Slack 응답 본문
                .doesNotContain("\tat ");            // 스택 트레이스
    }

    static void awaitLog(CapturedOutput output, String text) {
        await().atMost(Duration.ofSeconds(5)).until(() -> output.getAll().contains(text));
    }

    static String lineOf(CapturedOutput output, String text) {
        return output.getAll().lines().filter(line -> line.contains(text)).findFirst().orElseThrow();
    }

    static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static SlackProperties properties() {
        return properties(SLACK.baseUrl() + HOOK_PATH);
    }

    static SlackProperties properties(String webhookUrl) {
        return new SlackProperties(webhookUrl,
                Duration.ofSeconds(1), Duration.ofSeconds(2), 2, 100, Duration.ofSeconds(3));
    }

    static void awaitRequests(int count) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(SLACK.getAllServeEvents()).hasSize(count));
    }
}
