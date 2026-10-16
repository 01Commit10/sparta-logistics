package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AuthenticatedUser;
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
 * USR-02 로그인의 ID·비밀번호 확인 (U05 테스트 1~2).
 */
class AuthenticateTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final UserQueryService service = new UserQueryService(userRepository, passwordHasher);

    private User saved(String username, String rawPassword) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동",
                passwordHasher.hash(rawPassword), "U-" + username, OrganizationType.COMPANY, "스파르타상사"));
    }

    private void assertInvalidCredentials(String username, String rawPassword) {
        assertThatThrownBy(() -> service.authenticate(username, rawPassword))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("ID·비밀번호가 맞으면 사용자 ID, 역할, 비밀번호 변경 필요 여부를 돌려준다")
    void success() {
        User user = saved("user01", "Abcd123!");

        AuthenticatedUser result = service.authenticate("user01", "Abcd123!");

        assertThat(result).isEqualTo(new AuthenticatedUser(user.getId(), "USER", false));
    }

    @Test
    @DisplayName("MASTER는 역할이 MASTER로 나온다")
    void master() {
        User master = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자",
                passwordHasher.hash("Master!2345"), "M001"));

        assertThat(service.authenticate("master01", "Master!2345"))
                .isEqualTo(new AuthenticatedUser(master.getId(), "MASTER", false));
    }

    @Test
    @DisplayName("관리자가 비밀번호를 초기화한 사용자는 변경 필요(true)로 나온다")
    void passwordChangeRequired() {
        User user = saved("user01", "Abcd123!");
        user.requirePasswordChange(passwordHasher.hash("Temp123!"));

        assertThat(service.authenticate("user01", "Temp123!").passwordChangeRequired()).isTrue();
    }

    @Test
    @DisplayName("없는 ID는 INVALID_CREDENTIALS")
    void unknownUsername() {
        saved("user01", "Abcd123!");

        assertInvalidCredentials("user02", "Abcd123!");
    }

    @Test
    @DisplayName("틀린 비밀번호는 INVALID_CREDENTIALS")
    void wrongPassword() {
        saved("user01", "Abcd123!");

        assertInvalidCredentials("user01", "Abcd123?");
    }

    @Test
    @DisplayName("탈퇴·비활성화된 계정은 비밀번호가 맞아도 INVALID_CREDENTIALS")
    void withdrawn() {
        User user = saved("user01", "Abcd123!");
        user.withdraw(user.getId());

        assertInvalidCredentials("user01", "Abcd123!");
    }

    @Test
    @DisplayName("가입 규칙에 맞지 않는 ID도 형식 오류가 아니라 INVALID_CREDENTIALS (로그인에서 가입 규칙을 드러내지 않음)")
    void malformedUsername() {
        saved("user01", "Abcd123!");

        assertInvalidCredentials("USER01", "Abcd123!");
        assertInvalidCredentials("u", "Abcd123!");
    }
}
