package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.CreateUserCommand;
import com.user.application.dto.UserResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-10 MASTER의 사용자 생성 (U12 테스트 1~3). 가입과 같은 입력 규칙을 따르고 역할·배송담당 여부를 정한다.
 */
class CreateByMasterTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher());

    private static CreateUserCommand command(String username, String password, String slackId,
                                             String organizationType, String role, boolean deliveryManager) {
        return new CreateUserCommand(MASTER_ID, username, password, "김배송", slackId, organizationType, "서울센터",
                role, deliveryManager);
    }

    private static CreateUserCommand valid() {
        return command("driver01", "Abcd123!", "U001", "HUB", "USER", true);
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. 역할·배송담당 여부를 지정해 저장하고, 작성자는 MASTER, 비밀번호는 해시, 다음 로그인 후 변경 필요")
    void create() {
        UserResult result = service.createByMaster(valid());

        User saved = userRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).isEqualTo("hashed:Abcd123!");
        assertThat(saved.getCreatedBy()).isEqualTo(MASTER_ID);
        assertThat(result.username()).isEqualTo("driver01");
        assertThat(result.name()).isEqualTo("김배송");
        assertThat(result.slackId()).isEqualTo("U001");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.deliveryManager()).isTrue();
        assertThat(result.organizationType()).isEqualTo("HUB");
        assertThat(result.organizationName()).isEqualTo("서울센터");
        assertThat(result.organizationId()).isNull();
        assertThat(result.passwordChangeRequired()).isTrue();
        assertThat(result.active()).isTrue();
        assertThat(result.id()).isNotEqualTo(MASTER_ID);
    }

    @ParameterizedTest(name = "{0} 소속 {1}")
    @CsvSource({"HQ, MASTER", "HUB, MASTER", "COMPANY, MASTER", "COMPANY, USER", "HUB, USER"})
    @DisplayName("1. MASTER 역할도 만들 수 있다 (본사 HQ 포함)")
    void createRoles(String organizationType, String role) {
        UserResult result = service.createByMaster(command("user01", "Abcd123!", "U001", organizationType, role, false));

        assertThat(result.role()).isEqualTo(role);
        assertThat(result.organizationType()).isEqualTo(organizationType);
        assertThat(result.deliveryManager()).isFalse();
    }

    @Test
    @DisplayName("2. 이미 쓰는 username이면 DUPLICATE_USERNAME (탈퇴자 포함)")
    void duplicateUsername() {
        userRepository.save(withdrawnUser("driver01", "U009"));

        assertError(() -> service.createByMaster(valid()), UserErrorCode.DUPLICATE_USERNAME);
        assertThat(userRepository.all()).hasSize(1);
    }

    @Test
    @DisplayName("2. 활성 사용자의 slackId면 DUPLICATE_SLACK_ID, 탈퇴자의 slackId는 쓸 수 있다")
    void duplicateSlackId() {
        userRepository.save(withdrawnUser("gone01", "U001"));
        service.createByMaster(valid());

        assertError(() -> service.createByMaster(command("driver02", "Abcd123!", "U001", "HUB", "USER", false)),
                UserErrorCode.DUPLICATE_SLACK_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "Ab1!", "Abcd1234!Abcd1234"})
    @DisplayName("2. 비밀번호 정책 위반이면 INVALID_PASSWORD, 저장하지 않는다")
    void invalidPassword(String password) {
        assertError(() -> service.createByMaster(command("driver01", password, "U001", "HUB", "USER", false)),
                UserErrorCode.INVALID_PASSWORD);
        assertThat(userRepository.all()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usr", "User01", "user0123456"})
    @DisplayName("2. username 규칙 위반이면 INVALID_USERNAME")
    void invalidUsername(String username) {
        assertError(() -> service.createByMaster(command(username, "Abcd123!", "U001", "HUB", "USER", false)),
                UserErrorCode.INVALID_USERNAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "OTHER", ""})
    @DisplayName("2. 알 수 없는 소속 유형이면 INVALID_ORGANIZATION")
    void unknownOrganization(String organizationType) {
        assertError(() -> service.createByMaster(command("driver01", "Abcd123!", "U001", organizationType, "USER", false)),
                UserErrorCode.INVALID_ORGANIZATION);
        assertThat(userRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("3. 본사(HQ) 소속은 MASTER만: USER면 INVALID_ORGANIZATION, 저장하지 않는다")
    void hqRequiresMaster() {
        assertError(() -> service.createByMaster(command("user01", "Abcd123!", "U001", "HQ", "USER", false)),
                UserErrorCode.INVALID_ORGANIZATION);
        assertThat(userRepository.all()).isEmpty();
    }

    @ParameterizedTest(name = "{0} 소속 {1}")
    @CsvSource({"COMPANY, USER", "HQ, MASTER"})
    @DisplayName("3. 배송담당자는 허브 소속만: 아니면 DELIVERY_MANAGER_REQUIRES_HUB, 저장하지 않는다")
    void deliveryManagerRequiresHub(String organizationType, String role) {
        assertError(() -> service.createByMaster(command("user01", "Abcd123!", "U001", organizationType, role, true)),
                UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB);
        assertThat(userRepository.all()).isEmpty();
    }

    private static User withdrawnUser(String username, String slackId) {
        UUID id = UUID.randomUUID();
        User user = User.signup(id, Username.of(username), "탈퇴자", "hashed", slackId, OrganizationType.HUB, "허브");
        user.withdraw(id);
        return user;
    }
}
