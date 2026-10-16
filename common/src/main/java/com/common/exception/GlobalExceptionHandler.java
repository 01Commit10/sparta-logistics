package com.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
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
 * 서비스별 @RestControllerAdvice가 먼저 처리하도록 가장 낮은 우선순위를 가진다.
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String USER_HEADER_PREFIX = "X-User-";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode, e.getMessage(), List.of());
    }

    // @RequestBody @Valid 검증 실패 (필드 오류 + 클래스 레벨 제약 오류)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getAllErrors().stream()
                .map(error -> new ErrorResponse.FieldError(
                        error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return invalidInput(errors);
    }

    // 컨트롤러 @RequestParam, @PathVariable 등에 붙은 제약 조건 검증 실패
    // (@Valid @RequestBody와 함께 쓰이면 body 검증 오류도 여기로 오므로, 필드 오류는 필드명을 사용한다)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                error instanceof FieldError fe
                                        ? fe.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return invalidInput(errors);
    }

    // 서비스 계층 @Validated 검증 실패. 메서드명이 노출되지 않도록 경로의 마지막 노드만 사용한다.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastNodeName(v.getPropertyPath()), v.getMessage()))
                .toList();
        return invalidInput(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return invalidInput(e.getParameterName(), "필수 파라미터입니다.");
    }

    // X-User-* 헤더가 없으면 Gateway를 거치지 않은 요청으로 보고 401 (헤더명은 대소문자를 구분하지 않음)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if (e.getHeaderName().regionMatches(true, 0, USER_HEADER_PREFIX, 0, USER_HEADER_PREFIX.length())) {
            log.warn("[MissingUserHeader] {}", e.getHeaderName());
            return toResponse(CommonErrorCode.UNAUTHENTICATED);
        }
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
        return toResponse(CommonErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 처리되지 않은 모든 예외. 내부 메시지는 응답에 노출하지 않는다.
     * 415, 406, ResponseStatusException 등 Spring MVC 표준 예외는 원래 상태 코드를 유지한다.
     * (@ExceptionHandler는 Throwable 타입만 받을 수 있어 Spring ErrorResponse 인터페이스는 여기서 분기한다)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = springError.getStatusCode();
            log.warn("[SpringErrorResponse] status={}, message={}", status.value(), e.getMessage());
            ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
            return ResponseEntity.status(status)
                    .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
        }
        log.error("[UnhandledException]", e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private String lastNodeName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    private ResponseEntity<ErrorResponse> invalidInput(String field, String reason) {
        return invalidInput(List.of(new ErrorResponse.FieldError(field, reason)));
    }

    private ResponseEntity<ErrorResponse> invalidInput(List<ErrorResponse.FieldError> errors) {
        return toResponse(CommonErrorCode.INVALID_INPUT, CommonErrorCode.INVALID_INPUT.getMessage(), errors);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return toResponse(errorCode, errorCode.getMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message,
                                                     List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponse(errorCode.getCode(), message, errors));
    }
}
