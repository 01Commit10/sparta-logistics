package com.company.company.infrastructure.client;

import com.common.exception.CommonErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Feign 호출 결과를 허브 존재 여부로 바꾼다. 404만 '없음'이고 나머지 실패는 그대로 던진다. */
class HubClientImplTest {

    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private static HubClientImpl client(HubFeignClient feign) {
        return new HubClientImpl(feign, new RemoteCallTemplate());
    }

    @Test
    @DisplayName("허브를 조회하면 true")
    void exists() {
        HubClientImpl client = client(id -> new HubFeignDto(id, "서울센터", "서울시"));

        assertThat(client.existsHub(HUB_ID)).isTrue();
    }

    @Test
    @DisplayName("hub-ms가 404면 false")
    void notFound() {
        HubClientImpl client = client(id -> {
            throw new RemoteBusinessException("hub-ms", 404, CommonErrorCode.RESOURCE_NOT_FOUND, "허브 없음");
        });

        assertThat(client.existsHub(HUB_ID)).isFalse();
    }

    @Test
    @DisplayName("404가 아닌 4xx는 그대로 던진다")
    void otherBusinessError() {
        HubClientImpl client = client(id -> {
            throw new RemoteBusinessException("hub-ms", 403, CommonErrorCode.ACCESS_DENIED, "권한 없음");
        });

        assertThatThrownBy(() -> client.existsHub(HUB_ID))
                .isInstanceOfSatisfying(RemoteBusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
    }

    @Test
    @DisplayName("예상하지 못한 실패는 RemoteServiceException(503)으로 바뀐다")
    void unavailable() {
        HubClientImpl client = client(id -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(() -> client.existsHub(HUB_ID)).isInstanceOf(RemoteServiceException.class);
    }
}
