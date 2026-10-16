package com.common.auth;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;

import java.util.Objects;
import java.util.UUID;

/**
 * Gateway가 검증한 현재 사용자 (X-User-Id, X-User-Role).
 * 각 서비스의 presentation 계층에서만 받고, application에는 actorId 등 값으로 넘긴다.
 * 역할 확인은 방어용이다. 데이터를 봐야 아는 권한(업체 관리자, 담당자 등)은 각 서비스가 판단한다.
 */
public record AuthUser(UUID id, AuthRole role) {

    public AuthUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public boolean isMaster() {
        return role == AuthRole.MASTER;
    }

    public void requireMaster() {
        if (!isMaster()) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    public void requireSelfOrMaster(UUID ownerId) {
        if (!isMaster() && !id.equals(ownerId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
