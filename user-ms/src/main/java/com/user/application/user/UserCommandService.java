package com.user.application.user;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.user.dto.InitialMasterCommand;
import com.user.application.user.dto.InitialMasterResult;
import com.user.application.user.dto.SignupCommand;
import com.user.application.user.dto.UserResult;
import com.user.domain.user.OrganizationType;
import com.user.domain.user.PasswordHasher;
import com.user.domain.user.PasswordPolicy;
import com.user.domain.user.User;
import com.user.domain.user.UserRepository;
import com.user.domain.user.Username;
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

    private static OrganizationType parseOrganizationType(String value) {
        return Arrays.stream(OrganizationType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_ORGANIZATION));
    }
}
