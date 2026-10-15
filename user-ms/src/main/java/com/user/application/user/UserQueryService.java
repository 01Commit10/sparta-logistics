package com.user.application.user;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.user.dto.UserResult;
import com.user.domain.user.UserRepository;
import com.user.domain.user.Username;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 사용자 읽기 유스케이스.
 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** USR-20: 탈퇴한 사용자의 ID도 재사용할 수 없으므로 삭제된 사용자까지 확인한다 */
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(Username.of(username).value());
    }

    /** USR-18: 다른 서비스의 사용자 검증용. 탈퇴·비활성화된 사용자는 없는 것으로 본다 */
    public UserResult getActiveUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(UserResult::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
