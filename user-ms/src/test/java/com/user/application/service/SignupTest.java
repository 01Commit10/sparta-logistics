package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.SignupCommand;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-01 회원가입: 누구나 USER로 가입하고 즉시 활성화된다.
 */
class SignupTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher());

    private static SignupCommand command(String username, String password, String slackId, String organizationType) {
        return new SignupCommand(username, password, "홍길동", slackId, organizationType, "부산수산");
    }

    private static SignupCommand valid() {
        return command("user01", "Abcd123!", "U001", "COMPANY");
    }

    private void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("가입하면 USER로 저장하고, 비밀번호는 해시로 저장하고, 결과에 해시가 없다")
    void signup() {
        UserResult result = service.signup(valid());

        User saved = userRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed:Abcd123!");
        assertThat(saved.isActive()).isTrue();
        assertThat(result.username()).isEqualTo("user01");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.organizationType()).isEqualTo("COMPANY");
        assertThat(result.organizationName()).isEqualTo("부산수산");
        assertThat(result.deliveryManager()).isFalse();
        assertThat(result.passwordChangeRequired()).isFalse();
        assertThat(UserResult.class.getRecordComponents())
                .extracting(c -> c.getName().toLowerCase())
                .noneMatch(name -> name.contains("password") && !name.equals("passwordchangerequired"));
    }

    @Test
    @DisplayName("작성자(created_by)는 가입한 본인이다")
    void createdByIsSelf() {
        UserResult result = service.signup(valid());

        assertThat(userRepository.findById(result.id()).orElseThrow().getCreatedBy()).isEqualTo(result.id());
    }

    @Test
    @DisplayName("이미 쓰는 username이면 DUPLICATE_USERNAME")
    void duplicateUsername() {
        service.signup(valid());

        assertError(() -> service.signup(command("user01", "Abcd123!", "U002", "HUB")), UserErrorCode.DUPLICATE_USERNAME);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 username도 다시 쓸 수 없다")
    void withdrawnUsername() {
        User withdrawn = withdrawnUser("user01", "U009");
        userRepository.save(withdrawn);

        assertError(() -> service.signup(valid()), UserErrorCode.DUPLICATE_USERNAME);
    }

    @Test
    @DisplayName("활성 사용자의 slackId면 DUPLICATE_SLACK_ID")
    void duplicateSlackId() {
        service.signup(valid());

        assertError(() -> service.signup(command("user02", "Abcd123!", "U001", "COMPANY")), UserErrorCode.DUPLICATE_SLACK_ID);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 slackId는 다시 쓸 수 있다")
    void withdrawnSlackIdAllowed() {
        userRepository.save(withdrawnUser("user09", "U001"));

        assertThat(service.signup(valid()).username()).isEqualTo("user01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "ABCD123!", "Abcdefg!", "Ab1!", "Abcd1234!Abcd1234"})
    @DisplayName("비밀번호 정책 위반이면 INVALID_PASSWORD, 저장하지 않는다")
    void invalidPassword(String password) {
        assertError(() -> service.signup(command("user01", password, "U001", "COMPANY")), UserErrorCode.INVALID_PASSWORD);
        assertThat(userRepository.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usr", "User01", "user_01", "user0123456"})
    @DisplayName("username 규칙 위반이면 INVALID_USERNAME")
    void invalidUsername(String username) {
        assertError(() -> service.signup(command(username, "Abcd123!", "U001", "COMPANY")), UserErrorCode.INVALID_USERNAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HQ", "hq", "OTHER", ""})
    @DisplayName("소속은 COMPANY·HUB만 고를 수 있다 (본사 HQ, 알 수 없는 값 → INVALID_ORGANIZATION)")
    void invalidOrganization(String organizationType) {
        assertError(() -> service.signup(command("user01", "Abcd123!", "U001", organizationType)), UserErrorCode.INVALID_ORGANIZATION);
        assertThat(userRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("ID 중복 확인: 없으면 사용 가능, 활성·탈퇴 사용자가 쓰면 사용 불가")
    void usernameAvailability() {
        UserQueryService query = new UserQueryService(userRepository);
        service.signup(valid());
        userRepository.save(withdrawnUser("gone01", "U009"));

        assertThat(query.isUsernameAvailable("free01")).isTrue();
        assertThat(query.isUsernameAvailable("user01")).isFalse();
        assertThat(query.isUsernameAvailable("gone01")).isFalse();
    }

    @Test
    @DisplayName("ID 중복 확인도 username 규칙을 검사한다 (INVALID_USERNAME)")
    void availabilityValidatesFormat() {
        UserQueryService query = new UserQueryService(userRepository);

        assertError(() -> query.isUsernameAvailable("A!"), UserErrorCode.INVALID_USERNAME);
    }

    private static User withdrawnUser(String username, String slackId) {
        UUID id = UUID.randomUUID();
        User user = User.signup(id, Username.of(username), "탈퇴자", "hashed", slackId, OrganizationType.HUB, "허브");
        user.withdraw(id, LocalDateTime.now());
        return user;
    }
}
