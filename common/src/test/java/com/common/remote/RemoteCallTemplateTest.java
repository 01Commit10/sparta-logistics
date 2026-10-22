package com.common.remote;

import com.common.exception.CommonErrorCode;
import com.common.exception.UserErrorCode;
import feign.Request;
import feign.RetryableException;
import feign.codec.DecodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feign 예외 → 원격 예외 3종 (C02 테스트 7~11, E5).
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

    /** 2xx를 받았지만 본문을 DTO로 못 바꿈. Feign은 디코더 예외를 DecodeException으로 감싼다 */
    private static DecodeException decodeFailure(Throwable cause) {
        return new DecodeException(200, "decode failed", REQUEST, cause);
    }

    /** 실제 Spring 디코더 원인: 깨진 JSON·타입 불일치는 Jackson 예외(IOException), HTML 등은 RestClientException(IOException 아님) */
    static Stream<Throwable> decodeCauses() {
        return Stream.of(new IOException("Unexpected end-of-input"),
                new org.springframework.web.client.RestClientException("no suitable HttpMessageConverter for text/html"));
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

        assertThatThrownBy(() -> template.execute(HUB, failing(feignIo(cause))))
                .isInstanceOfSatisfying(RemoteTimeoutException.class, e -> {
                    assertThat(e.getTarget()).isEqualTo(HUB);
                    assertThat(e.getErrorCode()).isSameAs(CommonErrorCode.SERVICE_TIMEOUT);
                    assertThat(e).hasRootCause(cause);
                });
    }

    @Test
    @DisplayName("연결 거부 → RemoteServiceException (요청이 닿지 않아 처리되지 않은 것이 확실함, E5)")
    void connectionRefused() {
        assertThatThrownBy(() -> template.execute(HUB, failing(feignIo(new ConnectException("Connection refused")))))
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
    @DisplayName("ErrorDecoder가 만든 원격 예외는 감싼 것을 벗겨 그대로 던진다")
    void unwrapsDecodedExceptions() {
        RemoteBusinessException business = new RemoteBusinessException(HUB, 404, UserErrorCode.USER_NOT_FOUND, "없음");
        RemoteServiceException service = new RemoteServiceException(HUB, 503);

        assertThatThrownBy(() -> template.execute(HUB, failing(business))).isSameAs(business);
        assertThatThrownBy(() -> template.execute(HUB, failing(service))).isSameAs(service);
    }

    @ParameterizedTest(name = "원인 {0}")
    @MethodSource("decodeCauses")
    @DisplayName("리뷰: 2xx 응답 본문 변환 실패(DecodeException) → 원인과 상관없이 RemoteTimeoutException (원격은 처리했으니 '처리 안 됨'이 아님)")
    void decodeFailureIsUnknown(Throwable cause) {
        DecodeException decode = decodeFailure(cause);

        assertThatThrownBy(() -> template.execute(HUB, failing(decode)))
                .isInstanceOfSatisfying(RemoteTimeoutException.class, e -> {
                    assertThat(e.getTarget()).isEqualTo(HUB);
                    assertThat(e).hasCause(decode);
                });
    }

    @Test
    @DisplayName("그 밖의 예외(I/O·변환 실패가 아닌 것) → RemoteServiceException")
    void unknownFailure() {
        IllegalStateException cause = new IllegalStateException("unexpected");

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
            throw feignIo(new ConnectException("Connection refused"));
        })).isInstanceOf(RemoteServiceException.class);
    }
}
