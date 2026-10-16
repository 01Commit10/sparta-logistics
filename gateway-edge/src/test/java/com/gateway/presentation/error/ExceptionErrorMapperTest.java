package com.gateway.presentation.error;

import com.common.exception.CommonErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라우팅 중 생긴 예외를 공통 오류 코드로 바꾼다 (GW-04 3장 매핑 표).
 */
class ExceptionErrorMapperTest {

    private final ExceptionErrorMapper mapper = new ExceptionErrorMapper();

    @Test
    @DisplayName("라우트 없음(404) → RESOURCE_NOT_FOUND")
    void notFound() {
        assertThat(mapper.map(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("메서드 불일치(405) → METHOD_NOT_ALLOWED")
    void methodNotAllowed() {
        assertThat(mapper.map(new MethodNotAllowedException(HttpMethod.TRACE, List.of(HttpMethod.GET))))
                .isEqualTo(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("대상 서비스 인스턴스 없음 → SERVICE_UNAVAILABLE")
    void noInstance() {
        // LoadBalancer가 실제로 던지는 형태 (spring.cloud.gateway.loadbalancer.use404=false 기본값 → 503)
        assertThat(mapper.map(new NotFoundException("Unable to find instance for order-ms")))
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("연결 실패 → SERVICE_UNAVAILABLE")
    void connectionRefused() {
        assertThat(mapper.map(new ConnectException("Connection refused")))
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("응답 타임아웃 → SERVICE_TIMEOUT")
    void responseTimeout() {
        assertThat(mapper.map(new TimeoutException("Response took longer than timeout")))
                .isEqualTo(CommonErrorCode.SERVICE_TIMEOUT);
        assertThat(mapper.map(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(CommonErrorCode.SERVICE_TIMEOUT);
    }

    @Test
    @DisplayName("Circuit Breaker 열림 → SERVICE_UNAVAILABLE")
    void circuitOpen() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("order-ms");
        breaker.transitionToOpenState();

        assertThat(mapper.map(CallNotPermittedException.createCallNotPermittedException(breaker)))
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("SCG CircuitBreaker 필터가 열림을 바꿔 던진 ServiceUnavailableException → SERVICE_UNAVAILABLE (GW-10, fallbackUri 없음)")
    void circuitOpen_wrappedByGateway() {
        assertThat(mapper.map(new org.springframework.cloud.gateway.support.ServiceUnavailableException()))
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("원인 예외로 감싸져 있어도 원인으로 판단한다")
    void unwrapsCause() {
        assertThat(mapper.map(new RuntimeException(new ConnectException("Connection refused"))))
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("요청 형식 오류(4xx) → INVALID_INPUT")
    void badRequest() {
        assertThat(mapper.map(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("그 외 예외 → INTERNAL_SERVER_ERROR")
    void unknown() {
        assertThat(mapper.map(new IllegalStateException("boom")))
                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
}
