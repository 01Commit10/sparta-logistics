package com.user.domain.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 활성 MASTER는 항상 1명 이상 (마지막 MASTER의 탈퇴·강등·비활성화 금지).
 */
class LastMasterGuardTest {

    private final User master = User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자", "hashed", "U000");
    private final User user = User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동", "hashed", "U001",
            OrganizationType.COMPANY, "스파르타상사");

    @Test
    @DisplayName("대상이 MASTER이고 활성 MASTER가 1명뿐이면 LAST_MASTER")
    void lastMaster() {
        assertThatThrownBy(() -> LastMasterGuard.checkRemovable(master, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.LAST_MASTER);
    }

    @Test
    @DisplayName("활성 MASTER가 2명 이상이면 통과")
    void multipleMasters() {
        assertThatCode(() -> LastMasterGuard.checkRemovable(master, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대상이 USER면 MASTER 수와 관계없이 통과")
    void targetIsUser() {
        assertThatCode(() -> LastMasterGuard.checkRemovable(user, 1)).doesNotThrowAnyException();
    }
}
