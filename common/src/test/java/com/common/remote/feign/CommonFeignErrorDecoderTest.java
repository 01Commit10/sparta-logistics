package com.common.remote.feign;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.common.exception.UserErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteErrorCode;
import com.common.remote.RemoteServiceException;
import com.common.remote.RemoteTimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원격 오류 응답 분류 (C02 테스트 3~6, E3).
 */
class CommonFeignErrorDecoderTest {

    private final CommonFeignErrorDecoder decoder = new CommonFeignErrorDecoder(new ObjectMapper());

    interface HubClient {
    }

    private static Response response(int status, String body) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(HubClient.class, "hub-ms", "http://hub-ms"));
        Request request = Request.create(Request.HttpMethod.POST, "http://hub-ms/api/v1/inventories/decrease",
                Map.of(), null, StandardCharsets.UTF_8, template);
        Response.Builder builder = Response.builder().status(status).reason("reason").request(request).headers(Map.of());
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return builder.build();
    }

    private Exception decode(int status, String body) {
        return decoder.decode("HubClient#decrease()", response(status, body));
    }

    @Test
    @DisplayName("4xx + 등록된 code → RemoteBusinessException, 원래 enum·원격 메시지·상태·대상 서비스를 보존한다")
    void registeredCode() {
        Exception e = decode(409, """
                {"code":"DUPLICATE_USERNAME","message":"이미 사용 중인 아이디입니다. (user01)"}""");

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class, r -> {
            assertThat(r.getErrorCode()).isSameAs(UserErrorCode.DUPLICATE_USERNAME);
            assertThat(r.getMessage()).isEqualTo("이미 사용 중인 아이디입니다. (user01)");
            assertThat(r.getStatus()).isEqualTo(409);
            assertThat(r.getTarget()).isEqualTo("hub-ms");
        });
    }

    @Test
    @DisplayName("4xx + 모르는 code → RemoteErrorCode에 원격 상태·code·메시지를 그대로 담는다")
    void unknownCode() {
        Exception e = decode(409, """
                {"code":"UNREGISTERED_TEST_ERROR","message":"등록되지 않은 업무 오류입니다."}""");

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class, r ->
                assertThat(r.getErrorCode()).isEqualTo(
                        new RemoteErrorCode(HttpStatus.CONFLICT, "UNREGISTERED_TEST_ERROR", "등록되지 않은 업무 오류입니다.")));
    }

    @Test
    @DisplayName("본문에 message가 없으면 등록된 enum의 기본 메시지를 쓴다")
    void missingMessage() {
        Exception e = decode(404, """
                {"code":"USER_NOT_FOUND"}""");

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class, r ->
                assertThat(r.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @ParameterizedTest(name = "{0} + 본문 \"{1}\" → {2}")
    @CsvSource(delimiter = '|', value = {
            "400 | <없음>          | INVALID_INPUT",
            "400 | not json        | INVALID_INPUT",
            "403 | <html>403</html> | ACCESS_DENIED",
            "404 | {}              | RESOURCE_NOT_FOUND",
            "405 | []              | METHOD_NOT_ALLOWED",
    })
    @DisplayName("4xx + 본문이 없거나 공통 형식이 아님 → 상태별 공통 코드")
    void unreadableBody(int status, String body, String code) {
        Exception e = decode(status, "<없음>".equals(body) ? null : body);

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class, r -> {
            assertThat(r.getErrorCode()).isSameAs(CommonErrorCode.valueOf(code));
            assertThat(r.getStatus()).isEqualTo(status);
        });
    }

    @Test
    @DisplayName("공통 코드가 없는 4xx(409 등) + 본문 이상 → 원격 상태를 지킨 INVALID_INPUT")
    void otherClientErrorWithoutBody() {
        Exception e = decode(409, null);

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class, r -> {
            assertThat(r.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(r.getErrorCode().getCode()).isEqualTo("INVALID_INPUT");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 501, 502, 503, 599})
    @DisplayName("504가 아닌 5xx → RemoteServiceException (본문 code와 상관없이)")
    void serverError(int status) {
        Exception e = decode(status, """
                {"code":"INTERNAL_SERVER_ERROR","message":"서버 내부 오류가 발생했습니다."}""");

        assertThat(e).isInstanceOfSatisfying(RemoteServiceException.class, r -> {
            assertThat(r.getStatus()).isEqualTo(status);
            assertThat(r.getTarget()).isEqualTo("hub-ms");
            assertThat(r.getErrorCode()).isSameAs(CommonErrorCode.SERVICE_UNAVAILABLE);
        });
    }

    @ParameterizedTest(name = "504 + 본문 \"{0}\" → RemoteTimeoutException")
    @ValueSource(strings = {"{\"code\":\"SERVICE_TIMEOUT\",\"message\":\"x\"}", "{\"code\":\"INTERNAL_SERVER_ERROR\"}", "<없음>"})
    @DisplayName("리뷰: 원격 504 → RemoteTimeoutException (원격이 그다음 호출에서 타임아웃 → 그쪽에서 처리됐을 수 있어 결과 모름, 본문 code와 상관없이)")
    void gatewayTimeoutIsUnknown(String body) {
        Exception e = decode(504, "<없음>".equals(body) ? null : body);

        assertThat(e).isInstanceOfSatisfying(RemoteTimeoutException.class, r -> {
            assertThat(r.getStatus()).isEqualTo(504);
            assertThat(r.getTarget()).isEqualTo("hub-ms");
            assertThat(r.getErrorCode()).isSameAs(CommonErrorCode.SERVICE_TIMEOUT);
            assertThat(r.getMessage()).doesNotContain("hub-ms");
            assertThat(r.toString()).contains("hub-ms").contains("504");
        });
    }

    @Test
    @DisplayName("401 → RemoteServiceException (서비스 간 401은 호출 쪽 헤더 누락 버그, 클라이언트에 '다시 로그인'을 안내하지 않음)")
    void unauthorizedIsServiceError() {
        Exception e = decode(401, """
                {"code":"UNAUTHENTICATED","message":"인증이 필요합니다."}""");

        assertThat(e).isInstanceOfSatisfying(RemoteServiceException.class, r -> assertThat(r.getStatus()).isEqualTo(401));
    }

    @Test
    @DisplayName("403 등 401이 아닌 인증 관련 4xx는 업무 거절로 그대로 전달한다")
    void forbiddenIsBusiness() {
        Exception e = decode(403, """
                {"code":"PASSWORD_CHANGE_REQUIRED","message":"비밀번호를 변경한 뒤 이용할 수 있습니다."}""");

        assertThat(e).isInstanceOfSatisfying(RemoteBusinessException.class,
                r -> assertThat(r.getErrorCode()).isSameAs(AuthErrorCode.PASSWORD_CHANGE_REQUIRED));
    }

    @Test
    @DisplayName("대상 정보가 없는 요청이면 methodKey의 클라이언트 이름을 대상으로 쓴다")
    void targetFromMethodKey() {
        Request request = Request.create(Request.HttpMethod.GET, "http://x/y", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(500).reason("x").request(request).headers(Map.of()).build();

        Exception e = decoder.decode("HubClient#decrease()", response);

        assertThat(e).isInstanceOfSatisfying(RemoteServiceException.class,
                r -> assertThat(r.getTarget()).isEqualTo("HubClient"));
    }

    @Test
    @DisplayName("클라이언트에게 가는 메시지에 대상 서비스 이름이 없고, 로그(toString)에는 있다")
    void targetOnlyInToString() {
        Exception e = decode(503, null);

        assertThat(e.getMessage()).doesNotContain("hub-ms");
        assertThat(e.toString()).contains("hub-ms").contains("503");
    }
}
