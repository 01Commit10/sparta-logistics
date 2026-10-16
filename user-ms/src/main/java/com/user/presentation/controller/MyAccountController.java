package com.user.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.user.application.facade.AccountFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import com.user.presentation.dto.request.ChangePasswordRequest;
import com.user.presentation.dto.request.UpdateMyInfoRequest;
import com.user.presentation.dto.request.WithdrawRequest;
import com.user.presentation.dto.response.MyInfoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 계정 API (docs/design/04 API 명세서 User '로그인'). 대상은 항상 Gateway가 넣은 X-User-Id다 (경로에 ID 없음).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class MyAccountController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final AccountFacade accountFacade;

    public MyAccountController(UserQueryService userQueryService, UserCommandService userCommandService,
                               AccountFacade accountFacade) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
        this.accountFacade = accountFacade;
    }

    /** USR-06 내 정보 조회. 비밀번호 변경이 필요한 상태(pcr)에서도 Gateway가 허용한다 */
    @GetMapping
    public MyInfoResponse getMe(@CurrentUser AuthUser user) {
        return MyInfoResponse.from(userQueryService.getActiveUser(user.id()));
    }

    /** USR-07 내 정보 수정 (Slack ID만) */
    @PatchMapping
    public MyInfoResponse updateMe(@CurrentUser AuthUser user, @Valid @RequestBody UpdateMyInfoRequest request) {
        return MyInfoResponse.from(userCommandService.updateSlackId(request.toCommand(user.id())));
    }

    /**
     * USR-08 비밀번호 변경. 비밀번호 변경이 필요한 상태(pcr)에서도 Gateway가 허용한다.
     * 성공하면 이 요청의 토큰을 포함해 모든 토큰이 무효가 되므로 새 비밀번호로 다시 로그인해야 한다.
     */
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@CurrentUser AuthUser user, @Valid @RequestBody ChangePasswordRequest request) {
        accountFacade.changePassword(request.toCommand(user.id()));
    }

    /** USR-09 회원탈퇴. 비밀번호는 DELETE 본문으로 받는다 (07 서비스 정책 2.3) */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@CurrentUser AuthUser user, @Valid @RequestBody WithdrawRequest request) {
        accountFacade.withdraw(request.toCommand(user.id()));
    }
}
