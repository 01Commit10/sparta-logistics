package com.common.remote;

import com.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * common에 등록되지 않은 원격 code를 그대로 담는다 (ErrorCodeRegistry에 없는 code). {@link #name()}이 원격 code다.
 * 잡지 않으면 GlobalExceptionHandler가 원격과 같은 상태·code·메시지로 응답한다.
 */
public record RemoteErrorCode(HttpStatus httpStatus, String code, String message) implements ErrorCode {

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String name() {
        return code;
    }
}
