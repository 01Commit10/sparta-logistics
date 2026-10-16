package com.user.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AssignDeliveryManagerCommand;
import com.user.application.dto.ChangePasswordCommand;
import com.user.application.dto.ChangeRoleCommand;
import com.user.application.dto.CreateUserCommand;
import com.user.application.dto.DeactivateUserCommand;
import com.user.application.dto.InitialMasterCommand;
import com.user.application.dto.InitialMasterResult;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.dto.RoleChangeResult;
import com.user.application.dto.SignupCommand;
import com.user.application.dto.TemporaryPasswordResult;
import com.user.application.dto.UpdateSlackIdCommand;
import com.user.application.dto.UpdateUserCommand;
import com.user.application.dto.UserResult;
import com.user.application.dto.WithdrawCommand;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.service.LastMasterGuard;
import com.user.domain.service.PasswordHasher;
import com.user.domain.service.PasswordPolicy;
import com.user.domain.service.TemporaryPasswordGenerator;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
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
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    public UserCommandService(UserRepository userRepository, PasswordHasher passwordHasher,
                              TemporaryPasswordGenerator temporaryPasswordGenerator) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
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
        checkNotDuplicated(username, command.slackId());
        User user = User.signup(UUID.randomUUID(), username, command.name(),
                passwordHasher.hash(command.password()), command.slackId(),
                organizationType, command.organizationName());
        return UserResult.from(userRepository.save(user));
    }

    /**
     * USR-10 MASTER의 사용자 생성: 가입과 같은 입력·중복 규칙에 역할·배송담당 여부를 더한다.
     * 본사(HQ)는 MASTER만, 배송담당자는 허브 소속만 (domain). 다음 로그인 후 비밀번호 변경을 강제한다.
     * 확인을 동시에 통과한 요청은 save의 flush에서 UNIQUE에 걸려 같은 중복 오류가 된다.
     */
    @Transactional
    public UserResult createByMaster(CreateUserCommand command) {
        Username username = Username.of(command.username());
        PasswordPolicy.validate(command.password());
        OrganizationType organizationType = parseOrganizationType(command.organizationType());
        checkNotDuplicated(username, command.slackId());
        User user = User.createByMaster(UUID.randomUUID(), username, command.name(),
                passwordHasher.hash(command.password()), command.slackId(), organizationType,
                command.organizationName(), UserRole.valueOf(command.role()), command.deliveryManager(),
                command.masterId());
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

    /**
     * USR-15 배송담당자 지정·해제 (MASTER). 허브 소속이 아니면 DELIVERY_MANAGER_REQUIRES_HUB.
     * 배송담당 여부는 토큰 claim에 없으므로 토큰을 무효화하지 않는다. delivery-ms는 내부 API로 바로 새 값을 읽는다.
     */
    @Transactional
    public UserResult assignDeliveryManager(AssignDeliveryManagerCommand command) {
        User user = findActive(command.userId());
        user.assignDeliveryManager(command.deliveryManager());
        return UserResult.from(userRepository.save(user));
    }

    /**
     * USR-13 MASTER의 정보 수정 (부분 수정). 대상은 활성 사용자만.
     * 소속 유형을 바꾸면 소속명도 함께 보내야 한다 (유형과 이름이 어긋나지 않게). Slack ID 중복은 다른 활성 사용자 기준.
     */
    @Transactional
    public UserResult updateByMaster(UpdateUserCommand command) {
        User user = findActive(command.userId());
        OrganizationType organizationType = command.organizationType() == null
                ? user.getOrganizationType() : parseOrganizationType(command.organizationType());
        if (organizationType != user.getOrganizationType() && command.organizationName() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "소속 유형을 바꾸면 소속명도 함께 입력해야 합니다.");
        }
        String slackId = valueOr(command.slackId(), user.getSlackId());
        if (userRepository.existsActiveBySlackIdAndIdNot(slackId, user.getId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID);
        }
        user.updateProfile(valueOr(command.name(), user.getName()), slackId, organizationType,
                valueOr(command.organizationName(), user.getOrganizationName()));
        return UserResult.from(userRepository.save(user));
    }

    /**
     * USR-14 역할 변경. MASTER→USER면 활성 MASTER를 잠가 세어 마지막 MASTER를 지킨다 (본인 강등 포함).
     * 트랜잭션과 토큰 무효화는 UserAdminFacade가 한다 (역할은 Access claim에 있다).
     */
    public RoleChangeResult changeRole(ChangeRoleCommand command) {
        User user = findActive(command.userId());
        UserRole role = UserRole.valueOf(command.role());
        if (user.getRole() == role) {
            return new RoleChangeResult(UserResult.from(user), false);
        }
        user.checkRoleChangeable(role);
        if (user.isMaster()) {
            LastMasterGuard.checkRemovable(user, userRepository.countActiveMastersForUpdate());
        }
        user.changeRole(role);
        return new RoleChangeResult(UserResult.from(userRepository.save(user)), true);
    }

    /**
     * USR-17 비활성화 (MASTER). 탈퇴와 같은 논리 삭제이고 삭제자만 MASTER다. 트랜잭션은 UserAdminFacade가 연다.
     * 이미 삭제된 사용자는 USER_ALREADY_DELETED (조회는 삭제 포함). 배송담당 여부·역할은 재활성화를 위해 그대로 둔다.
     */
    public void deactivate(DeactivateUserCommand command) {
        if (command.userId().equals(command.masterId())) {
            throw new BusinessException(UserErrorCode.CANNOT_DEACTIVATE_SELF);
        }
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (user.isActive() && user.isMaster()) {
            LastMasterGuard.checkRemovable(user, userRepository.countActiveMastersForUpdate());
        }
        user.withdraw(command.masterId());
        userRepository.save(user);
    }

    /**
     * USR-21 비밀번호 초기화 (MASTER, 오프라인 문의 후). 임시 비밀번호의 해시만 저장하고 다음 로그인 후 변경을 강제한다.
     * 현재 비밀번호를 확인하지 않으므로 본인은 비밀번호 변경을 써야 한다. 트랜잭션과 토큰 무효화는 UserAdminFacade가 한다.
     */
    public TemporaryPasswordResult resetPassword(ResetPasswordCommand command) {
        if (command.userId().equals(command.masterId())) {
            throw new BusinessException(UserErrorCode.CANNOT_RESET_OWN_PASSWORD);
        }
        User user = findActive(command.userId());
        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.requirePasswordChange(passwordHasher.hash(temporaryPassword));
        userRepository.save(user);
        return new TemporaryPasswordResult(user.getId(), user.getUsername(), temporaryPassword);
    }

    /** username은 탈퇴자 포함 유일, slackId는 활성 사용자 사이에서만 유일 */
    private void checkNotDuplicated(Username username, String slackId) {
        if (userRepository.existsByUsername(username.value())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsActiveBySlackId(slackId)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID);
        }
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

    private static String valueOr(String value, String current) {
        return value == null ? current : value;
    }

    private static OrganizationType parseOrganizationType(String value) {
        return Arrays.stream(OrganizationType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_ORGANIZATION));
    }
}
