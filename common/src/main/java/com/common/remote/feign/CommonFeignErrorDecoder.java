package com.common.remote.feign;

import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.ErrorCodeRegistry;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteErrorCode;
import com.common.remote.RemoteServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 우리 서비스의 오류 응답(공통 ErrorResponse)을 원격 예외로 바꾼다 (C02 3.1). Feign은 우리 서비스끼리만 쓴다는 전제다.
 * <ul>
 *     <li>4xx(401 제외) → {@link RemoteBusinessException}. 본문 code를 {@link ErrorCodeRegistry}로 복원하고,
 *         모르는 code는 {@link RemoteErrorCode}, 본문이 공통 형식이 아니면 상태별 공통 코드</li>
 *     <li>5xx·401 → {@link RemoteServiceException}. 서비스 간 401은 호출 쪽이 사용자 헤더를 안 넣은 내부 버그다 (E3)</li>
 * </ul>
 */
public class CommonFeignErrorDecoder implements ErrorDecoder {

    /** 오류 본문은 짧다. 큰 본문(잘못된 대상, HTML 오류 페이지)을 전부 읽지 않는다 */
    private static final int MAX_BODY_BYTES = 16 * 1024;

    private final ObjectMapper objectMapper;

    public CommonFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String target = target(methodKey, response);
        int status = response.status();
        if (status == HttpStatus.UNAUTHORIZED.value() || status < 400 || status >= 500) {
            return new RemoteServiceException(target, status);
        }
        return readBody(response)
                .map(body -> toBusinessException(target, status, body))
                .orElseGet(() -> fallback(target, status));
    }

    private RemoteBusinessException toBusinessException(String target, int status, RemoteError body) {
        Optional<ErrorCode> registered = ErrorCodeRegistry.find(body.code());
        ErrorCode errorCode = registered.orElseGet(() ->
                new RemoteErrorCode(httpStatus(status), body.code(), body.message() != null ? body.message() : body.code()));
        String message = body.message() != null ? body.message() : errorCode.getMessage();
        return new RemoteBusinessException(target, status, errorCode, message);
    }

    private static RemoteBusinessException fallback(String target, int status) {
        ErrorCode errorCode = switch (status) {
            case 400 -> CommonErrorCode.INVALID_INPUT;
            case 403 -> CommonErrorCode.ACCESS_DENIED;
            case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            // 공통 코드가 없는 4xx: 원격 상태는 지키고 code는 INVALID_INPUT
            default -> new RemoteErrorCode(httpStatus(status), CommonErrorCode.INVALID_INPUT.getCode(),
                    CommonErrorCode.INVALID_INPUT.getMessage());
        };
        return new RemoteBusinessException(target, status, errorCode, errorCode.getMessage());
    }

    private Optional<RemoteError> readBody(Response response) {
        if (response.body() == null) {
            return Optional.empty();
        }
        try (InputStream in = response.body().asInputStream()) {
            JsonNode node = objectMapper.readTree(in.readNBytes(MAX_BODY_BYTES));
            if (node == null || !node.path("code").isTextual() || node.path("code").asText().isBlank()) {
                return Optional.empty();
            }
            JsonNode message = node.path("message");
            return Optional.of(new RemoteError(node.path("code").asText(), message.isTextual() ? message.asText() : null));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** @FeignClient(name)이 대상 서비스 이름이다. 정보가 없으면 methodKey("HubClient#decrease()")의 클라이언트 이름 */
    private static String target(String methodKey, Response response) {
        if (response.request() != null && response.request().requestTemplate() != null
                && response.request().requestTemplate().feignTarget() != null) {
            return response.request().requestTemplate().feignTarget().name();
        }
        int hash = methodKey == null ? -1 : methodKey.indexOf('#');
        return hash > 0 ? methodKey.substring(0, hash) : String.valueOf(methodKey);
    }

    private static HttpStatus httpStatus(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved : HttpStatus.BAD_REQUEST;
    }

    private record RemoteError(String code, String message) {
    }
}
