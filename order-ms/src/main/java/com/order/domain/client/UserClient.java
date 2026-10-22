package com.order.domain.client;

import java.util.Optional;
import java.util.UUID;

public interface UserClient {
    // 조회 실패, 없음이면 empty (알림은 멘션 없이 보낸다)
    Optional<String> findSlackId(UUID userId);
}
