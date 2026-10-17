package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-12 관리자 상세 조회 (U13 테스트 5): 삭제된 사용자도 조회, 없으면 404.
 */
class GetUserDetailTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserQueryService service = new UserQueryService(userRepository);

    private User saved(String username) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터"));
    }

    @Test
    @DisplayName("활성 사용자: 정보와 작성자(가입은 본인)를 돌려준다, active=true")
    void active() {
        User user = saved("user01");

        UserResult result = service.getUser(user.getId());

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.username()).isEqualTo("user01");
        assertThat(result.active()).isTrue();
        assertThat(result.createdBy()).isEqualTo(user.getId());
        assertThat(result.deletedAt()).isNull();
        assertThat(result.deletedBy()).isNull();
    }

    @Test
    @DisplayName("5. 삭제(비활성화)된 사용자도 조회된다: active=false, 삭제 일시·삭제자")
    void deleted() {
        User user = saved("user01");
        user.withdraw(MASTER_ID);

        UserResult result = service.getUser(user.getId());

        assertThat(result.active()).isFalse();
        assertThat(result.deletedAt()).isNotNull();
        assertThat(result.deletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("5. 없는 사용자는 USER_NOT_FOUND")
    void unknown() {
        assertThatThrownBy(() -> service.getUser(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }
}
