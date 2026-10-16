package com.user.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.user.application.service.UserCommandService;
import com.user.presentation.dto.request.DeliveryManagerRequest;
import com.user.presentation.dto.response.UserDetailResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 관리자(MASTER)의 사용자 관리 API (docs/design/04 API 명세서 User '관리자').
 * Gateway가 MASTER 경로로 막지만, Gateway를 거치지 않은 요청에 대비해 모든 메서드가 MASTER를 다시 확인한다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserCommandService userCommandService;

    public UserAdminController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    /** USR-15 배송담당자 지정·해제 */
    @PatchMapping("/{userId}/delivery-manager")
    public UserDetailResponse changeDeliveryManager(@CurrentUser AuthUser user, @PathVariable UUID userId,
                                                    @Valid @RequestBody DeliveryManagerRequest request) {
        user.requireMaster();
        return UserDetailResponse.from(userCommandService.assignDeliveryManager(request.toCommand(userId)));
    }
}
