package com.gateway.presentation.error;

import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * 라우팅 중 생긴 예외를 공통 오류 코드로 바꾼다 (docs/plan/gateway/GW-04 3장).
 * 감싸진 예외는 원인까지 따라가 판단한다.
 */
@Component
public class ExceptionErrorMapper {

    public ErrorCode map(Throwable error) {
        for (Throwable current = error; current != null; current = next(current)) {
            if (current instanceof ConnectException) {
                return CommonErrorCode.SERVICE_UNAVAILABLE;          // 연결 실패
            }
            if (current instanceof TimeoutException
                    || current instanceof org.springframework.cloud.gateway.support.TimeoutException) {
                return CommonErrorCode.SERVICE_TIMEOUT;              // 응답 타임아웃
            }
        }
        for (Throwable current = error; current != null; current = next(current)) {
            if (current instanceof ResponseStatusException statusException) {
                return byStatus(statusException.getStatusCode());
            }
        }
        return CommonErrorCode.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode byStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> CommonErrorCode.UNAUTHENTICATED;
            case 403 -> CommonErrorCode.ACCESS_DENIED;
            case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;         // SCG NotFoundException: 인스턴스 없음
            case 504 -> CommonErrorCode.SERVICE_TIMEOUT;
            default -> status.is4xxClientError() ? CommonErrorCode.INVALID_INPUT : CommonErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    private Throwable next(Throwable current) {
        Throwable cause = current.getCause();
        return cause == current ? null : cause;
    }
}
