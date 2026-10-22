package com.user.application.facade;

import com.user.application.dto.ChangePasswordCommand;
import com.user.application.dto.WithdrawCommand;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본인 계정 변경(user 도메인)과 토큰 무효화(token 도메인)를 한 트랜잭션으로 묶는다 (U10 2장, 01 구현 원칙 2.4).
 * Redis 기록은 커밋 전에 한다. Redis가 실패하면 예외로 DB 변경이 롤백되어 "비밀번호는 바뀌었는데 이전 토큰이 살아 있는" 상태가 없다.
 * 반대로 Redis 기록 뒤 커밋이 실패하면 토큰만 무효가 되는데, 다시 로그인하면 되므로 허용한다 (SYS-01 4.5).
 */
@Service
public class AccountFacade {

    private final UserCommandService userCommandService;
    private final TokenService tokenService;

    public AccountFacade(UserCommandService userCommandService, TokenService tokenService) {
        this.userCommandService = userCommandService;
        this.tokenService = tokenService;
    }

    /** USR-08 비밀번호 변경. 성공하면 이 사용자의 모든 토큰이 무효가 되어 다시 로그인해야 한다 */
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        userCommandService.changePassword(command);
        tokenService.revokeAll(command.userId());
    }

    /** USR-09 회원탈퇴. 탈퇴 전에 받은 토큰도 바로 쓸 수 없게 한다 */
    @Transactional
    public void withdraw(WithdrawCommand command) {
        userCommandService.withdraw(command);
        tokenService.revokeAll(command.userId());
    }
}
