package com.user.presentation.check;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 개발 확인용 엔드포인트: Postman → Gateway(19090) → user-ms 라우팅을 직접 확인한다.
 * 업무 기능이 아니므로 application·domain을 거치지 않는다.
 * 제출·운영 시에는 app.routing-check.enabled=false로 끈다.
 *
 * <p>Gateway 인증(GW-05) 전에는 토큰 없이 호출되고, 이후에는 로그인 토큰이 필요하며
 * 응답의 X-User-Id·X-User-Role로 Gateway가 넣은 사용자 헤더를 확인할 수 있다.
 */
@RestController
@ConditionalOnProperty(name = "app.routing-check.enabled", havingValue = "true", matchIfMissing = true)
public class RoutingCheckController {

    /** 응답에 보여줄 헤더. Authorization·Cookie 같은 비밀값은 넣지 않는다. */
    private static final List<String> ECHO_HEADERS = List.of(
            "X-User-Id", "X-User-Role", "X-Token-Jti", "X-Token-Exp", "X-Request-Id",
            "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Forwarded-Port", "Forwarded");

    private final String serviceName;

    public RoutingCheckController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/api/v1/auth/routing-check")
    public RoutingCheckResponse check(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        ECHO_HEADERS.forEach(name -> {
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        });
        return new RoutingCheckResponse(serviceName, request.getMethod(), request.getRequestURI(),
                request.getQueryString(), headers);
    }
}
