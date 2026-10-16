package com.company.company.infrastructure.client;

import com.common.exception.UserErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.common.remote.RemoteServiceException;
import com.company.company.domain.client.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** user-ms 내부 API(USR-18) 응답을 UserInfo로 바꾼다. 404(없거나 삭제됨)만 empty고 나머지 실패는 그대로 던진다. */
class UserClientImplTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static UserClientImpl client(UserFeignClient feign) {
        return new UserClientImpl(feign, new RemoteCallTemplate());
    }

    @Test
    @DisplayName("활성 사용자면 ID와 역할만 담아 돌려준다")
    void active() {
        UserClientImpl client = client(id -> new UserFeignDto(id, "홍길동", "USER", false, "U001", true));

        assertThat(client.findActiveUser(USER_ID)).contains(new UserInfo(USER_ID, "USER"));
    }

    @Test
    @DisplayName("user-ms가 404 USER_NOT_FOUND면 empty")
    void notFound() {
        UserClientImpl client = client(id -> {
            throw new RemoteBusinessException("user-ms", 404, UserErrorCode.USER_NOT_FOUND, "사용자 없음");
        });

        assertThat(client.findActiveUser(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("원격 5xx는 RemoteServiceException 그대로 던진다")
    void unavailable() {
        UserClientImpl client = client(id -> {
            throw new RemoteServiceException("user-ms", 500);
        });

        assertThatThrownBy(() -> client.findActiveUser(USER_ID)).isInstanceOf(RemoteServiceException.class);
    }
}
