package com.gateway.contract;

import com.common.exception.ErrorCodeRegistry;
import com.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.support.TestTokenFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 테스트 (GW-12 테스트 5, SYS-01 6장): Gateway가 직접 만드는 오류 본문은 서비스와 같은 common ErrorResponse 형식이다.
 * 모르는 필드가 있으면 실패하도록 엄격하게 역직렬화하고, code는 common ErrorCodeRegistry에 있는 값이어야 한다
 * (클라이언트·Feign ErrorDecoder가 code로 오류를 구분하기 때문).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ErrorFormatContractTest {

    private static final ObjectMapper STRICT = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Autowired
    WebTestClient client;

    @ParameterizedTest(name = "{0} {1} ({2}) → {3} {4}")
    @CsvSource({
            "GET,  /api/v1/hubs,                 NONE,   401, TOKEN_MISSING",
            "GET,  /api/v1/hubs,                 BROKEN, 401, TOKEN_INVALID",
            "GET,  /api/v1/users,                USER,   403, ACCESS_DENIED",
            "GET,  /api/v1/unknown,              MASTER, 404, RESOURCE_NOT_FOUND",
            "GET,  /internal/v1/users/x,         MASTER, 404, RESOURCE_NOT_FOUND",
            "GET,  /api/v1/orders,               MASTER, 503, SERVICE_UNAVAILABLE"
    })
    void gatewayErrorsUseCommonFormat(String method, String path, String token, int status, String code) throws Exception {
        WebTestClient.RequestHeadersSpec<?> request = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        if (!token.equals("NONE")) {
            request.header(HttpHeaders.AUTHORIZATION, token.equals("BROKEN") ? "Bearer not-a-jwt" : TestTokenFactory.bearer(token));
        }

        byte[] body = request.exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().returnResult().getResponseBody();

        ErrorResponse error = STRICT.readValue(body, ErrorResponse.class);
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.message()).isNotBlank();
        assertThat(ErrorCodeRegistry.find(error.code()))
                .hasValueSatisfying(registered -> assertThat(registered.getHttpStatus().value()).isEqualTo(status));
        JsonNode raw = STRICT.readTree(body);
        assertThat(raw.properties()).extracting(java.util.Map.Entry::getKey).containsExactly("code", "message");
    }
}
