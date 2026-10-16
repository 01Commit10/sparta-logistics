package com.user.application.facade;

import com.user.application.dto.ChangeRoleCommand;
import com.user.application.dto.DeactivateUserCommand;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.dto.RoleChangeResult;
import com.user.application.dto.TemporaryPasswordResult;
import com.user.application.dto.UserResult;
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

    /** USR-14 역할 변경. 실제로 바뀐 경우에만 대상의 토큰을 무효화한다 (role claim이 달라지므로) */
    @Transactional
    public UserResult changeRole(ChangeRoleCommand command) {
        RoleChangeResult result = userCommandService.changeRole(command);
        if (result.changed()) {
            tokenService.revokeAll(command.userId());
        }
        return result.user();
    }

    /** USR-21 비밀번호 초기화. 대상이 이미 받은 토큰을 무효로 해 임시 비밀번호로 다시 로그인하게 한다 */
    @Transactional
    public TemporaryPasswordResult resetPassword(ResetPasswordCommand command) {
        TemporaryPasswordResult result = userCommandService.resetPassword(command);
        tokenService.revokeAll(command.userId());
        return result;
    }
}
