package com.common.auth.web;

import com.common.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/test/public")
    Map<String, String> publicApi() {
        return Map.of("result", "ok");
    }
}
