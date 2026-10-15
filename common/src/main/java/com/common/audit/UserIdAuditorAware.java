package com.common.audit;

import com.common.web.UserHeaders;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

/**
 * Gateway가 넣은 X-User-Id 헤더를 작업자로 사용 (docs/Common/SYS-01 5.1).
 * 다음 경우엔 작업자를 특정할 수 없으므로 empty를 반환하고, Auditing은 작업자 컬럼을 건드리지 않는다.
 * - HTTP 요청 밖(스케줄러, 비동기 스레드 등)
 * - 헤더가 없음(회원가입, 시스템 작업) 또는 UUID 형식이 아님
 */
public class UserIdAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return UserHeaders.currentUserId().flatMap(UserIdAuditorAware::parse);
    }

    private static Optional<UUID> parse(String userId) {
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
