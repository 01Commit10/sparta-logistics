package com.user.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.user.application.dto.DeactivateUserCommand;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import com.user.presentation.dto.request.ChangeRoleRequest;
import com.user.presentation.dto.request.CreateUserRequest;
import com.user.presentation.dto.request.DeliveryManagerRequest;
import com.user.presentation.dto.request.UpdateUserRequest;
import com.user.presentation.dto.response.TemporaryPasswordResponse;
import com.user.presentation.dto.response.UserDetailResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * 관리자(MASTER)의 사용자 관리 API (docs/design/04 API 명세서 User '관리자').
 * Gateway가 MASTER 경로로 막지만, Gateway를 거치지 않은 요청에 대비해 모든 메서드가 MASTER를 다시 확인한다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final UserAdminFacade userAdminFacade;

    public UserAdminController(UserQueryService userQueryService, UserCommandService userCommandService,
                               UserAdminFacade userAdminFacade) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
        this.userAdminFacade = userAdminFacade;
    }

    /** USR-10 사용자 생성. 다음 로그인 후 비밀번호 변경이 강제된다 (MASTER가 정한 비밀번호이므로) */
    @PostMapping
    public ResponseEntity<UserDetailResponse> create(@CurrentUser AuthUser user,
                                                     @Valid @RequestBody CreateUserRequest request) {
        user.requireMaster();
        UserDetailResponse created = UserDetailResponse.from(userCommandService.createByMaster(request.toCommand(user.id())));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    /** USR-12 사용자 상세 (삭제된 사용자 포함). /users/me는 MyAccountController가 먼저 받는다 (경로 우선순위) */
    @GetMapping("/{userId}")
    public UserDetailResponse getDetail(@CurrentUser AuthUser user, @PathVariable UUID userId) {
        user.requireMaster();
        return UserDetailResponse.from(userQueryService.getUser(userId));
    }

    /** USR-13 정보 수정 (이름·Slack ID·소속 유형·소속명, 부분 수정) */
    @PatchMapping("/{userId}")
    public UserDetailResponse update(@CurrentUser AuthUser user, @PathVariable UUID userId,
                                     @Valid @RequestBody UpdateUserRequest request) {
        user.requireMaster();
        return UserDetailResponse.from(userCommandService.updateByMaster(request.toCommand(userId)));
    }

    /** USR-14 역할 변경. 바뀐 사용자의 기존 토큰은 무효가 된다 (본인 강등이면 본인도 다시 로그인) */
    @PatchMapping("/{userId}/role")
    public UserDetailResponse changeRole(@CurrentUser AuthUser user, @PathVariable UUID userId,
                                         @Valid @RequestBody ChangeRoleRequest request) {
        user.requireMaster();
        return UserDetailResponse.from(userAdminFacade.changeRole(request.toCommand(userId)));
    }

    /** USR-15 배송담당자 지정·해제 */
    @PatchMapping("/{userId}/delivery-manager")
    public UserDetailResponse changeDeliveryManager(@CurrentUser AuthUser user, @PathVariable UUID userId,
                                                    @Valid @RequestBody DeliveryManagerRequest request) {
        user.requireMaster();
        return UserDetailResponse.from(userCommandService.assignDeliveryManager(request.toCommand(userId)));
    }

    /** USR-21 비밀번호 초기화. 임시 비밀번호는 응답으로 1회만 나가므로 캐시하지 않게 한다 */
    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(@CurrentUser AuthUser user, @PathVariable UUID userId) {
        user.requireMaster();
        TemporaryPasswordResponse response = TemporaryPasswordResponse.from(
                userAdminFacade.resetPassword(new ResetPasswordCommand(userId, user.id())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /** USR-17 비활성화 (논리 삭제, 대상 토큰 무효화). 본인은 탈퇴(DELETE /users/me)를 써야 한다 */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@CurrentUser AuthUser user, @PathVariable UUID userId) {
        user.requireMaster();
        userAdminFacade.deactivate(new DeactivateUserCommand(userId, user.id()));
    }
}
