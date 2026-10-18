package com.user.domain.repository;

import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;

/**
 * USR-11 사용자 목록 검색 조건 (04 API 명세서). null인 조건은 걸지 않는다.
 * username·name은 부분 일치, role·deliveryManager·organizationType은 일치.
 * includeDeleted가 false면 활성 사용자만 (탈퇴·비활성화 제외).
 */
public record UserSearchCondition(
        String username,
        String name,
        UserRole role,
        Boolean deliveryManager,
        OrganizationType organizationType,
        boolean includeDeleted
) {

    /** 빈 문자열(username=)은 조건이 없는 것으로 본다 */
    public UserSearchCondition {
        username = blankToNull(username);
        name = blankToNull(name);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
