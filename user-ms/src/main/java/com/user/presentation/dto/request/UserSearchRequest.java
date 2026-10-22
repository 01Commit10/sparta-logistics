package com.user.presentation.dto.request;

import com.user.application.dto.UserSearchQuery;
import jakarta.validation.constraints.Pattern;

/**
 * USR-11 사용자 목록 검색 조건 (쿼리 파라미터, 04 API 명세서). 모두 선택이고 보내지 않으면 조건을 걸지 않는다.
 * page·size·sort는 Pageable로 따로 받는다 (common PagePolicy).
 */
public record UserSearchRequest(
        String username,
        String name,
        @Pattern(regexp = "MASTER|USER", message = "MASTER 또는 USER만 가능합니다.") String role,
        Boolean isDeliveryManager,
        @Pattern(regexp = "COMPANY|HUB|HQ", message = "COMPANY, HUB, HQ만 가능합니다.") String organizationType,
        Boolean includeDeleted
) {

    public UserSearchQuery toQuery() {
        return new UserSearchQuery(username, name, role, isDeliveryManager, organizationType, includeDeleted);
    }
}
