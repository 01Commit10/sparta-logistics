package com.common.auth.web;

import com.common.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// CurrentUserWebMvcTest용 Controller
@RestController
class CurrentUserTestController {

    @GetMapping("/test/me")
    Map<String, String> me(@CurrentUser AuthUser user) {
        return Map.of("id", user.id().toString(), "role", user.role().name());
    }

    @GetMapping("/test/optional")
    Map<String, String> optional(@CurrentUser(required = false) AuthUser user) {
        return Map.of("user", user == null ? "none" : user.id().toString());
    }

    /** 로그아웃처럼 Gateway가 넣는 토큰 헤더를 받는 API */
    @GetMapping("/test/token-header")
    Map<String, String> tokenHeader(@CurrentUser AuthUser user, @RequestHeader("X-Token-Jti") String jti) {
        return Map.of("jti", jti);
    }

    /** 클라이언트가 넣는 일반 필수 헤더 */
    @GetMapping("/test/client-header")
    Map<String, String> clientHeader(@RequestHeader("Idempotency-Key") String key) {
        return Map.of("key", key);
    }

    @GetMapping("/test/public")
    Map<String, String> publicApi() {
        return Map.of("result", "ok");
    }
}
