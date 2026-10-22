package com.common.remote.integration;

import com.common.remote.RemoteCallTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 요청 안에서 Feign을 부른다. 원격 예외를 잡지 않아 GlobalExceptionHandler가 응답을 만든다 (C02 테스트 18·19) */
@RestController
class RemoteTestController {

    private final HubTestClient hubClient;
    private final RemoteCallTemplate remote;

    RemoteTestController(HubTestClient hubClient, RemoteCallTemplate remote) {
        this.hubClient = hubClient;
        this.remote = remote;
    }

    @GetMapping("/test/hub-ok")
    String ok() {
        return remote.execute("hub-ms", hubClient::ok);
    }

    @GetMapping("/test/hub-unstable")
    String unstable() {
        return remote.execute("hub-ms", hubClient::unstable);
    }
}
