package com.common.notification.slack;

import com.common.auth.AuthContract;
import com.common.notification.SlackMessage;
import com.common.notification.SlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Slack Incoming Webhook 발송기 (N1, N4, N11).
 * 전용 스레드에서 RestClient로 보낸다. executor는 빈으로 노출하지 않는다(P1):
 * Executor 빈이 생기면 Boot 기본 applicationTaskExecutor가 등록되지 않아 서비스의 @Async 동작이 바뀐다.
 * 모든 실패는 WARN 한 줄로 끝낸다(N5). 로그에는 상태 코드·원인 예외 클래스명만 남긴다:
 * RestClient 예외 메시지에는 Webhook URL(N3)과 Slack 응답 본문이, supplier 예외 메시지에는 호출한 쪽 데이터가 들어 있을 수 있다.
 * 알림 스레드에는 요청 컨텍스트가 없으므로 호출 시점의 MDC를 복사하고 X-Request-Id를 캡처해 로그에 남긴다(P2).
 */
@Slf4j
public class SlackWebhookNotifier implements SlackNotifier, DisposableBean {

    private static final int MAX_LOG_VALUE = 64;
    private static final Pattern LINE_BREAK_CHAR = Pattern.compile("[\\r\\n]");

    private final URI webhookUri;
    private final RestClient restClient;
    private final SlackMessageRenderer renderer = new SlackMessageRenderer();
    private final ThreadPoolTaskExecutor executor;

    public SlackWebhookNotifier(SlackProperties properties) {
        this.webhookUri = webhookUri(properties.webhookUrl());
        this.restClient = RestClient.builder().requestFactory(requestFactory(properties)).build();
        this.executor = executor(properties);
    }

    /**
     * 설정은 됐지만 형식이 틀린 URL은 기동을 막는다(P3). 메시지와 원인 예외에 URL 값을 넣지 않는다:
     * URISyntaxException 메시지에는 입력값 전체가 들어 있고, 이 예외는 기동 실패 로그에 그대로 찍힌다.
     */
    private static URI webhookUri(String webhookUrl) {
        URI uri;
        try {
            uri = new URI(webhookUrl);
        } catch (URISyntaxException e) {
            throw invalidUrl();
        }
        boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalidUrl();
        }
        return uri;
    }

    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException(
                "common.notification.slack.webhook-url 형식이 올바르지 않습니다 (http/https 절대 URL이어야 함, 값은 보안상 생략)");
    }

    @Override
    public void send(SlackMessage message) {
        send(message == null ? null : message.reference(), () -> message);
    }

    @Override
    public void send(String reference, Supplier<SlackMessage> message) {
        String ref = logValue(reference);
        String requestId = logValue(currentRequestId());
        try {
            Supplier<SlackMessage> supplier = message == null ? () -> null : message;
            executor.execute(() -> deliver(ref, requestId, supplier));
        } catch (TaskRejectedException e) {
            log.warn("Slack 알림 버림(대기열 가득 또는 종료됨) ref={} requestId={}", ref, requestId);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }

    private void deliver(String ref, String requestId, Supplier<SlackMessage> supplier) {
        SlackMessage message;
        try {
            message = supplier.get();
        } catch (RuntimeException e) {
            log.warn("Slack 알림 메시지 조립 실패 ref={} requestId={} cause={}", ref, requestId, rootCauseName(e));
            return;
        }
        if (message == null) {
            log.warn("Slack 알림 메시지 없음 ref={} requestId={}", ref, requestId);
            return;
        }
        try {
            post(message);
            log.debug("Slack 알림 발송 ref={} requestId={}", ref, requestId);
        } catch (RestClientResponseException e) {
            log.warn("Slack 알림 실패 ref={} requestId={} status={}", ref, requestId, e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.warn("Slack 알림 실패 ref={} requestId={} cause={}", ref, requestId, rootCauseName(e));
        }
    }

    private static String currentRequestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(AuthContract.HEADER_REQUEST_ID);
        }
        return null;
    }

    /** 로그 위조 방지(P11): 줄바꿈 → 공백, 64자 제한. 값이 없으면 "-". */
    private static String logValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String oneLine = LINE_BREAK_CHAR.matcher(value).replaceAll(" ");
        return oneLine.length() <= MAX_LOG_VALUE ? oneLine : oneLine.substring(0, MAX_LOG_VALUE);
    }

    private static String rootCauseName(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private void post(SlackMessage message) {
        restClient.post()
                .uri(webhookUri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", renderer.render(message)))
                .retrieve()
                .toBodilessEntity();
    }

    private static SimpleClientHttpRequestFactory requestFactory(SlackProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    private static ThreadPoolTaskExecutor executor(SlackProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("slack-notifier-");
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(properties.shutdownWait().toMillis());
        executor.setTaskDecorator(SlackWebhookNotifier::withCallerMdc);
        executor.initialize();
        return executor;
    }

    /** 호출 스레드의 MDC를 알림 스레드에 복사하고, 끝나면 알림 스레드의 원래 MDC로 되돌린다. */
    private static Runnable withCallerMdc(Runnable task) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setMdc(callerMdc);
            try {
                task.run();
            } finally {
                setMdc(previous);
            }
        };
    }

    private static void setMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
