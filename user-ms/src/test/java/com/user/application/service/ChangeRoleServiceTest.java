package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ChangeRoleCommand;
import com.user.application.dto.RoleChangeResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-14 역할 변경 (U15 테스트 1~4).
 */
class ChangeRoleServiceTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher());

    private User savedUser(String username) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터"));
    }

    private User savedHubMaster(String username) {
        return userRepository.save(User.createByMaster(UUID.randomUUID(), Username.of(username), "관리자", "hashed",
                "U-" + username, OrganizationType.HUB, "서울센터", UserRole.MASTER, false, UUID.randomUUID()));
    }

    private static void assertError(Runnable action, UserErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("1. USER→MASTER: 바뀌고 changed=true, 잠금 조회는 하지 않는다")
    void promote() {
        User user = savedUser("user01");

        RoleChangeResult result = service.changeRole(new ChangeRoleCommand(user.getId(), "MASTER"));

        assertThat(result.changed()).isTrue();
        assertThat(result.user().role()).isEqualTo("MASTER");
        assertThat(userRepository.findById(user.getId()).orElseThrow().isMaster()).isTrue();
        assertThat(userRepository.lockCount()).isZero();
    }

    @Test
    @DisplayName("2. MASTER→USER: 활성 MASTER가 2명이면 성공 (잠금 조회로 센다)")
    void demote() {
        User a = savedHubMaster("master01");
        savedHubMaster("master02");

        RoleChangeResult result = service.changeRole(new ChangeRoleCommand(a.getId(), "USER"));

        assertThat(result.changed()).isTrue();
        assertThat(result.user().role()).isEqualTo("USER");
        assertThat(userRepository.lockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("2. MASTER→USER: 활성 MASTER가 1명뿐이면 LAST_MASTER, 바뀌지 않는다 (본인 강등 포함)")
    void lastMaster() {
        User a = savedHubMaster("master01");

        assertError(() -> service.changeRole(new ChangeRoleCommand(a.getId(), "USER")), UserErrorCode.LAST_MASTER);
        assertThat(a.isMaster()).isTrue();
    }

    @Test
    @DisplayName("3. 같은 역할이면 changed=false, 잠금 조회도 하지 않는다")
    void sameRole() {
        User user = savedUser("user01");
        User master = savedHubMaster("master01");

        assertThat(service.changeRole(new ChangeRoleCommand(user.getId(), "USER")).changed()).isFalse();
        assertThat(service.changeRole(new ChangeRoleCommand(master.getId(), "MASTER")).changed()).isFalse();
        assertThat(userRepository.lockCount()).isZero();
    }

    @Test
    @DisplayName("4. 본사(HQ) 소속 MASTER를 USER로 → INVALID_ORGANIZATION (다른 MASTER가 있어도)")
    void hqMaster() {
        User hq = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자",
                "hashed", "U000"));
        savedHubMaster("master02");

        assertError(() -> service.changeRole(new ChangeRoleCommand(hq.getId(), "USER")), UserErrorCode.INVALID_ORGANIZATION);
        assertThat(hq.isMaster()).isTrue();
    }

    @Test
    @DisplayName("4. 본사(HQ) MASTER가 유일한 MASTER여도 INVALID_ORGANIZATION (소속 규칙을 잠금 조회보다 먼저 본다)")
    void hqMasterAlone() {
        User hq = userRepository.save(User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자",
                "hashed", "U000"));

        assertError(() -> service.changeRole(new ChangeRoleCommand(hq.getId(), "USER")), UserErrorCode.INVALID_ORGANIZATION);
        assertThat(userRepository.lockCount()).isZero();
    }

    @Test
    @DisplayName("삭제된 사용자·없는 사용자는 USER_NOT_FOUND")
    void notFound() {
        User user = savedUser("user01");
        user.withdraw(user.getId());

        assertError(() -> service.changeRole(new ChangeRoleCommand(user.getId(), "MASTER")), UserErrorCode.USER_NOT_FOUND);
        assertError(() -> service.changeRole(new ChangeRoleCommand(UUID.randomUUID(), "MASTER")), UserErrorCode.USER_NOT_FOUND);
    }
}
