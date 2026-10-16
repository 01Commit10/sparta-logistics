package com.common.remote;

import com.common.exception.CommonErrorCode;
import com.common.exception.UserErrorCode;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feign·Circuit Breaker 예외 → 원격 예외 3종 (C02 테스트 7~11, E5).
 */
class RemoteCallTemplateTest {

    private static final String HUB = "hub-ms";
    private static final Request REQUEST = Request.create(Request.HttpMethod.POST, "http://hub-ms/api/v1/x",
            Map.of(), null, StandardCharsets.UTF_8, null);

    private final RemoteCallTemplate template = new RemoteCallTemplate();

    /** Feign은 IOException을 RetryableException으로 감싸 던진다 (Retryer는 기본 NEVER_RETRY) */
    private static RetryableException feignIo(Exception cause) {
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.POST, cause, (Long) null, REQUEST);
    }

    /** fallback이 없는 Feign Circuit Breaker는 실패를 NoFallbackAvailableException으로 감싼다 */
    private static NoFallbackAvailableException noFallback(Throwable cause) {
        return new NoFallbackAvailableException("No fallback available.", cause);
    }

    private static CallNotPermittedException circuitOpen() {
        return CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("HubClient#decrease()"));
    }

    private static Supplier<String> failing(RuntimeException e) {
        return () -> {
            throw e;
        };
    }

    @Test
    @DisplayName("성공하면 값을 그대로 돌려준다")
    void success() {
        assertThat(template.execute(HUB, () -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("read 타임아웃 → RemoteTimeoutException (결과 모름), 대상 서비스와 원인을 담는다")
    void readTimeout() {
        SocketTimeoutException cause = new SocketTimeoutException("Read timed out");

        assertThatThrownBy(() -> template.execute(HUB, failing(noFallback(feignIo(cause)))))
                .isInstanceOfSatisfying(RemoteTimeoutException.class, e -> {
                    assertThat(e.getTarget()).isEqualTo(HUB);
                    assertThat(e.getErrorCode()).isSameAs(CommonErrorCode.SERVICE_TIMEOUT);
                    assertThat(e).hasRootCause(cause);
                });
    }

    @Test
    @DisplayName("연결 거부 → RemoteServiceException (요청이 닿지 않아 처리되지 않은 것이 확실함, E5)")
    void connectionRefused() {
        assertThatThrownBy(() -> template.execute(HUB, failing(noFallback(feignIo(new ConnectException("Connection refused"))))))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e.getTarget()).isEqualTo(HUB));
    }

    @Test
    @DisplayName("연결 타임아웃(JDK는 SocketTimeoutException \"Connect timed out\") → RemoteServiceException (E5)")
    void connectTimeout() {
        assertThatThrownBy(() -> template.execute(HUB, failing(feignIo(new SocketTimeoutException("Connect timed out")))))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    @DisplayName("응답을 읽다 연결이 끊김(그 밖의 I/O 오류) → RemoteTimeoutException (보낸 뒤라 결과 모름)")
    void otherIoAfterSend() {
        assertThatThrownBy(() -> template.execute(HUB, failing(feignIo(new java.io.IOException("Connection reset")))))
                .isInstanceOf(RemoteTimeoutException.class);
    }

    @Test
    @DisplayName("Circuit Breaker OPEN → RemoteServiceException (호출하지 않음)")
    void circuitBreakerOpen() {
        CallNotPermittedException open = circuitOpen();

        assertThatThrownBy(() -> template.execute(HUB, failing(noFallback(open))))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e).hasCause(open));
    }

    @Test
    @DisplayName("ErrorDecoder가 만든 원격 예외는 감싼 것을 벗겨 그대로 던진다")
    void unwrapsDecodedExceptions() {
        RemoteBusinessException business = new RemoteBusinessException(HUB, 404, UserErrorCode.USER_NOT_FOUND, "없음");
        RemoteServiceException service = new RemoteServiceException(HUB, 503);

        assertThatThrownBy(() -> template.execute(HUB, failing(noFallback(business)))).isSameAs(business);
        assertThatThrownBy(() -> template.execute(HUB, failing(service))).isSameAs(service);
    }

    @Test
    @DisplayName("그 밖의 예외(응답 변환 실패 등) → RemoteServiceException")
    void unknownFailure() {
        IllegalStateException cause = new IllegalStateException("decode failed");

        assertThatThrownBy(() -> template.execute(HUB, failing(cause)))
                .isInstanceOfSatisfying(RemoteServiceException.class, e -> assertThat(e).hasCause(cause));
    }

    @Test
    @DisplayName("run: 반환값이 없는 호출도 같은 규칙으로 바꾼다")
    void runVoid() {
        AtomicInteger calls = new AtomicInteger();
        template.run(HUB, calls::incrementAndGet);

        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> template.run(HUB, () -> {
            throw noFallback(circuitOpen());
        })).isInstanceOf(RemoteServiceException.class);
    }

    @Nested
    @DisplayName("executeWithRetryOnce (멱등성 키가 있는 호출만)")
    class RetryOnce {

        private Supplier<String> failThenSucceed(RuntimeException first, AtomicInteger calls) {
            return () -> {
                if (calls.incrementAndGet() == 1) {
                    throw first;
                }
                return "ok";
            };
        }

        @Test
        @DisplayName("타임아웃 1회 후 성공 → 값 (2번 호출)")
        void retriesTimeoutOnce() {
            AtomicInteger calls = new AtomicInteger();

            String result = template.executeWithRetryOnce(HUB,
                    failThenSucceed(feignIo(new SocketTimeoutException("Read timed out")), calls));

            assertThat(result).isEqualTo("ok");
            assertThat(calls).hasValue(2);
        }

        @Test
        @DisplayName("5xx 1회 후 성공 → 값 (2번 호출)")
        void retriesServiceErrorOnce() {
            AtomicInteger calls = new AtomicInteger();

            assertThat(template.executeWithRetryOnce(HUB, failThenSucceed(new RemoteServiceException(HUB, 503), calls)))
                    .isEqualTo("ok");
            assertThat(calls).hasValue(2);
        }

        @Test
        @DisplayName("두 번 모두 실패하면 두 번째 실패를 던진다 (최대 2번 호출)")
        void retriesOnlyOnce() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                calls.incrementAndGet();
                throw feignIo(new SocketTimeoutException("Read timed out"));
            })).isInstanceOf(RemoteTimeoutException.class);
            assertThat(calls).hasValue(2);
        }

        @Test
        @DisplayName("리뷰: 타임아웃 뒤 재시도가 5xx·연결 실패로 끝나도 결과 모름(Timeout)이다 (첫 요청이 처리됐을 수 있음, 재시도 실패는 suppressed)")
        void timeoutThenServiceErrorStaysUnknown() {
            AtomicInteger calls = new AtomicInteger();
            RemoteServiceException retryFailure = new RemoteServiceException(HUB, 503);

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw feignIo(new SocketTimeoutException("Read timed out"));
                }
                throw retryFailure;
            })).isInstanceOfSatisfying(RemoteTimeoutException.class, e -> {
                assertThat(e.getTarget()).isEqualTo(HUB);
                assertThat(e.getSuppressed()).containsExactly(retryFailure);
            });
            assertThat(calls).hasValue(2);
        }

        @Test
        @DisplayName("리뷰: 타임아웃 뒤 재시도가 Circuit Breaker OPEN에 막혀도 결과 모름(Timeout)이다")
        void timeoutThenCircuitOpenStaysUnknown() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw feignIo(new SocketTimeoutException("Read timed out"));
                }
                throw noFallback(circuitOpen());
            })).isInstanceOf(RemoteTimeoutException.class);
            assertThat(calls).hasValue(2);
        }

        @Test
        @DisplayName("리뷰: 타임아웃 뒤 재시도가 업무 거절(4xx)이면 그 응답을 쓴다 (멱등성 키로 받은 확정 응답)")
        void timeoutThenBusinessUsesAnswer() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw feignIo(new SocketTimeoutException("Read timed out"));
                }
                throw new RemoteBusinessException(HUB, 409, UserErrorCode.DUPLICATE_USERNAME, "중복");
            })).isInstanceOf(RemoteBusinessException.class);
        }

        @Test
        @DisplayName("5xx 뒤 재시도도 5xx면 처리 안 됨(Service)이다 (두 번 다 처리되지 않음)")
        void serviceErrorTwiceStaysService() {
            AtomicInteger calls = new AtomicInteger();
            RemoteServiceException second = new RemoteServiceException(HUB, 502);

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RemoteServiceException(HUB, 503);
                }
                throw second;
            })).isSameAs(second);
        }

        @Test
        @DisplayName("5xx 뒤 재시도가 타임아웃이면 결과 모름(Timeout)이다")
        void serviceErrorThenTimeoutIsUnknown() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RemoteServiceException(HUB, 503);
                }
                throw feignIo(new SocketTimeoutException("Read timed out"));
            })).isInstanceOf(RemoteTimeoutException.class);
        }

        @Test
        @DisplayName("업무 거절(4xx)은 다시 보내도 같으므로 재시도하지 않는다")
        void noRetryOnBusiness() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, failThenSucceed(
                    new RemoteBusinessException(HUB, 409, UserErrorCode.DUPLICATE_USERNAME, "중복"), calls)))
                    .isInstanceOf(RemoteBusinessException.class);
            assertThat(calls).hasValue(1);
        }

        @Test
        @DisplayName("Circuit Breaker OPEN은 바로 다시 보내도 막히므로 재시도하지 않는다")
        void noRetryOnCircuitOpen() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> template.executeWithRetryOnce(HUB, failThenSucceed(noFallback(circuitOpen()), calls)))
                    .isInstanceOf(RemoteServiceException.class);
            assertThat(calls).hasValue(1);
        }
    }
}
