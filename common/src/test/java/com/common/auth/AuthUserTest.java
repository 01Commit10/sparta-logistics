package com.common.auth;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserTest {

    static final UUID ID = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    static final AuthUser MASTER = new AuthUser(ID, AuthRole.MASTER);
    static final AuthUser USER = new AuthUser(ID, AuthRole.USER);

    @Test
    @DisplayName("isMaster: MASTER는 true, USER는 false")
    void isMaster() {
        assertThat(MASTER.isMaster()).isTrue();
        assertThat(USER.isMaster()).isFalse();
    }

    @Test
    @DisplayName("requireMaster: MASTER는 통과, USER는 ACCESS_DENIED")
    void requireMaster() {
        assertThatCode(MASTER::requireMaster).doesNotThrowAnyException();
        assertAccessDenied(() -> USER.requireMaster());
    }

    @Test
    @DisplayName("requireSelfOrMaster: 본인 통과, MASTER는 타인이어도 통과, 타인 USER는 ACCESS_DENIED")
    void requireSelfOrMaster() {
        assertThatCode(() -> USER.requireSelfOrMaster(ID)).doesNotThrowAnyException();
        assertThatCode(() -> MASTER.requireSelfOrMaster(OTHER)).doesNotThrowAnyException();
        assertAccessDenied(() -> USER.requireSelfOrMaster(OTHER));
        assertAccessDenied(() -> USER.requireSelfOrMaster(null));
    }

    @Test
    @DisplayName("id·role 없이는 만들 수 없다")
    void requiresIdAndRole() {
        assertThatThrownBy(() -> new AuthUser(null, AuthRole.USER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthUser(ID, null)).isInstanceOf(NullPointerException.class);
    }

    private static void assertAccessDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }
}
