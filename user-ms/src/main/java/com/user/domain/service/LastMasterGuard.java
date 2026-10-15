package com.user.domain.service;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.domain.entity.User;

/**
 * 불변 조건: 활성 MASTER는 항상 1명 이상이다.
 * 탈퇴·역할 강등·비활성화 전에 호출한다. 활성 MASTER 수는 호출하는 쪽이 잠금 조회로 가져온다
 * (동시에 두 MASTER가 서로를 강등하는 경쟁 조건 방지).
 */
public final class LastMasterGuard {

    private LastMasterGuard() {
    }

    public static void checkRemovable(User target, long activeMasterCount) {
        if (target.isMaster() && activeMasterCount <= 1) {
            throw new BusinessException(UserErrorCode.LAST_MASTER);
        }
    }
}
