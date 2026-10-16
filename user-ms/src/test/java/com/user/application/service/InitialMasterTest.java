package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.InitialMasterCommand;
import com.user.application.dto.InitialMasterResult;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeTemporaryPasswordGenerator;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * USR-19: 기동 시 활성 MASTER가 없으면 .env 값으로 1회 생성한다.
 */
class InitialMasterTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final UserCommandService service = new UserCommandService(userRepository, new FakePasswordHasher(), new FakeTemporaryPasswordGenerator());

    private static final InitialMasterCommand COMMAND =
            new InitialMasterCommand("master01", "Master!2345", "관리자", "U00MASTER01");

    @Test
    @DisplayName("활성 MASTER가 없으면 본사 소속 MASTER를 만들고, 비밀번호는 해시로 저장한다")
    void createsWhenAbsent() {
        InitialMasterResult result = service.createInitialMasterIfAbsent(COMMAND);

        assertThat(result).isEqualTo(InitialMasterResult.CREATED);
        User master = userRepository.all().iterator().next();
        assertThat(master.getUsername()).isEqualTo("master01");
        assertThat(master.getRole()).isEqualTo(UserRole.MASTER);
        assertThat(master.getOrganizationType()).isEqualTo(OrganizationType.HQ);
        assertThat(master.getPasswordHash()).isEqualTo("hashed:Master!2345");
        assertThat(master.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("이미 활성 MASTER가 있으면 만들지 않는다 (재기동해도 1명)")
    void skipsWhenPresent() {
        service.createInitialMasterIfAbsent(COMMAND);

        InitialMasterResult second = service.createInitialMasterIfAbsent(
                new InitialMasterCommand("master99", "Master!2345", "다른관리자", "U00MASTER99"));

        assertThat(second).isEqualTo(InitialMasterResult.ALREADY_EXISTS);
        assertThat(userRepository.all()).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName(".env 값이 비어 있으면 만들지 않고 기동은 계속한다")
    void skipsWhenNotConfigured(String blank) {
        InitialMasterResult result = service.createInitialMasterIfAbsent(
                new InitialMasterCommand(blank, "Master!2345", "관리자", "U00MASTER01"));

        assertThat(result).isEqualTo(InitialMasterResult.NOT_CONFIGURED);
        assertThat(userRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("비밀번호가 규칙에 맞지 않으면 만들지 않고 실패한다 (잘못된 설정을 바로 알 수 있게)")
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> service.createInitialMasterIfAbsent(
                new InitialMasterCommand("master01", "weak", "관리자", "U00MASTER01")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_PASSWORD);
        assertThat(userRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 MASTER의 ID와 같으면 만들지 않는다 (ID 재사용 금지)")
    void rejectsReusedUsername() {
        User withdrawn = User.initialMaster(UUID.randomUUID(),
                Username.of("master01"), "옛관리자", "hashed", "U00OLD");
        withdrawn.withdraw(withdrawn.getId());
        userRepository.save(withdrawn);

        assertThatThrownBy(() -> service.createInitialMasterIfAbsent(COMMAND))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.DUPLICATE_USERNAME);
    }
}
