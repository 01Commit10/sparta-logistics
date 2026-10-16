package com.user.application.facade;

import com.user.application.dto.DeactivateUserCommand;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MASTER의 사용자 변경(user 도메인) 중 대상의 토큰 무효화(token 도메인)가 필요한 것을 한 트랜잭션으로 묶는다.
 * Redis 기록은 커밋 전이라 Redis가 실패하면 DB 변경도 롤백된다 (AccountFacade와 같은 규칙).
 */
@Service
public class UserAdminFacade {

    private final UserCommandService userCommandService;
    private final TokenService tokenService;

    public UserAdminFacade(UserCommandService userCommandService, TokenService tokenService) {
        this.userCommandService = userCommandService;
        this.tokenService = tokenService;
    }

    /** USR-17 비활성화. 대상이 이미 받은 토큰도 바로 쓸 수 없게 한다 */
    @Transactional
    public void deactivate(DeactivateUserCommand command) {
        userCommandService.deactivate(command);
        tokenService.revokeAll(command.userId());
    }
}
