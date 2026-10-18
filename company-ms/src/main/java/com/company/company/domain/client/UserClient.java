package com.company.company.domain.client;

import java.util.Optional;
import java.util.UUID;

/**
 * user-ms 사용자 조회 Port. 구현은 infrastructure.client (Feign, USR-18 내부 API).
 */
public interface UserClient {

    /** 삭제되지 않은 사용자만 반환한다. 없거나 삭제된 사용자는 empty */
    Optional<UserInfo> findActiveUser(UUID userId);
}
