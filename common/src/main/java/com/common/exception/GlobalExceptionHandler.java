package com.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 도메인 서비스(*-ms) 공통 예외 처리.
 * 컴포넌트 스캔이 아닌 {@link com.common.config.CommonExceptionAutoConfiguration}으로 등록된다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode, ErrorResponse.of(errorCode, e.getMessage()));
    }

    // @RequestBody @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    // 컨트롤러 @RequestParam, @PathVariable 등에 붙은 제약 조건 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    // 서비스 계층 @Validated 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return invalidInput(e.getParameterName(), "필수 파라미터입니다.");
    }

    // Gateway가 넣어주는 X-User-* 헤더가 없는 경우 포함
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return invalidInput(e.getHeaderName(), "필수 헤더입니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidInput(e.getName(), "타입이 올바르지 않습니다.");
    }

    // JSON 형식 오류, enum 변환 실패 등
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT,
                ErrorResponse.of(CommonErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED, ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.RESOURCE_NOT_FOUND, ErrorResponse.of(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    // 처리되지 않은 모든 예외. 내부 메시지는 응답에 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[UnhandledException]", e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ErrorResponse> invalidInput(String field, String reason) {
        return toResponse(CommonErrorCode.INVALID_INPUT,
                ErrorResponse.of(CommonErrorCode.INVALID_INPUT, List.of(new ErrorResponse.FieldError(field, reason))));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}
