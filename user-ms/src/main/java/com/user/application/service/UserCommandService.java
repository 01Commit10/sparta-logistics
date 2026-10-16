package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ChangePasswordCommand;
import com.user.application.dto.InitialMasterCommand;
import com.user.application.dto.InitialMasterResult;
import com.user.application.dto.SignupCommand;
import com.user.application.dto.UpdateSlackIdCommand;
import com.user.application.dto.UserResult;
import com.user.application.dto.WithdrawCommand;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.service.LastMasterGuard;
import com.user.domain.service.PasswordHasher;
import com.user.domain.service.PasswordPolicy;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

/**
 * 사용자 쓰기 유스케이스. user 도메인만 다룬다 (토큰까지 묶는 흐름은 facade).
 */
@Service
public class UserCommandService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserCommandService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /** USR-19: 활성 MASTER가 하나도 없을 때만 .env 값으로 첫 MASTER를 만든다. */
    @Transactional
    public InitialMasterResult createInitialMasterIfAbsent(InitialMasterCommand command) {
        if (!command.isConfigured()) {
            return InitialMasterResult.NOT_CONFIGURED;
        }
        if (userRepository.existsActiveMaster()) {
            return InitialMasterResult.ALREADY_EXISTS;
        }
        Username username = Username.of(command.username());
        PasswordPolicy.validate(command.password());
        if (userRepository.existsByUsername(username.value())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        User master = User.initialMaster(UUID.randomUUID(), username, command.name(),
                passwordHasher.hash(command.password()), command.slackId());
        userRepository.save(master);
        return InitialMasterResult.CREATED;
    }

    /** USR-01: 누구나 USER로 가입하고 즉시 활성화된다. 소속은 COMPANY·HUB만 고를 수 있다. */
    @Transactional
    public UserResult signup(SignupCommand command) {
        Username username = Username.of(command.username());
        PasswordPolicy.validate(command.password());
        OrganizationType organizationType = parseOrganizationType(command.organizationType());
        if (userRepository.existsByUsername(username.value())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsActiveBySlackId(command.slackId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID);
        }
        User user = User.signup(UUID.randomUUID(), username, command.name(),
                passwordHasher.hash(command.password()), command.slackId(),
                organizationType, command.organizationName());
        return UserResult.from(userRepository.save(user));
    }

    /**
     * USR-07 내 정보 수정: 본인은 Slack ID만 바꿀 수 있다. 다른 활성 사용자가 쓰는 값이면 DUPLICATE_SLACK_ID.
     * 확인을 동시에 통과한 요청은 save의 flush에서 부분 UNIQUE에 걸려 같은 오류가 된다.
     */
    @Transactional
    public UserResult updateSlackId(UpdateSlackIdCommand command) {
        User user = findActive(command.userId());
        if (userRepository.existsActiveBySlackIdAndIdNot(command.slackId(), user.getId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID);
        }
        user.updateSlackId(command.slackId());
        return UserResult.from(userRepository.save(user));
    }

    /**
     * USR-08 비밀번호 변경: 현재 비밀번호 확인 → 새 비밀번호 규칙 → 현재와 다른지 확인 → 변경(비밀번호 변경 필요 해제).
     * 트랜잭션은 토큰 무효화까지 묶는 AccountFacade가 연다.
     */
    public void changePassword(ChangePasswordCommand command) {
        User user = findActive(command.userId());
        verifyPassword(user, command.currentPassword());
        PasswordPolicy.validate(command.newPassword());
        if (passwordHasher.matches(command.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.SAME_PASSWORD);
        }
        user.changePassword(passwordHasher.hash(command.newPassword()));
        userRepository.save(user);
    }

    /**
     * USR-09 회원탈퇴: 비밀번호를 다시 확인하고 논리 삭제한다 (삭제자 = 본인). 트랜잭션은 AccountFacade가 연다.
     * MASTER일 때만 활성 MASTER를 잠가 센다. 두 MASTER가 동시에 탈퇴해 MASTER가 0명이 되는 것을 막는다.
     */
    public void withdraw(WithdrawCommand command) {
        User user = findActive(command.userId());
        verifyPassword(user, command.password());
        if (user.isMaster()) {
            LastMasterGuard.checkRemovable(user, userRepository.countActiveMastersForUpdate());
        }
        user.withdraw(user.getId());
        userRepository.save(user);
    }

    /** 본인 확인용 비밀번호 재입력. 틀리면 로그인 실패와 같은 INVALID_CREDENTIALS */
    private void verifyPassword(User user, String rawPassword) {
        if (!passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
    }

    /** 토큰은 아직 유효하지만 탈퇴·비활성화된 사용자는 없는 것으로 본다 */
    private User findActive(UUID userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private static OrganizationType parseOrganizationType(String value) {
        return Arrays.stream(OrganizationType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_ORGANIZATION));
    }
}
