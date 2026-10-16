package com.common.audit;

import com.common.auth.AuthContract;
import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

/**
 * 감사 작업자(created_by, updated_by) 결정 (docs/design/07 서비스 정책 1.4, C01 D7).
 * <ul>
 *     <li>요청 안 + 사용자 헤더 → 그 사용자 UUID</li>
 *     <li>요청 안 + 헤더 없음(회원가입·로그인 등 공개 경로) → empty: Auditing이 작업자 칸을 건드리지 않아 직접 지정한 값이 남는다</li>
 *     <li>요청 밖(스케줄러, 기동 시 초기화, 시드) → {@link AuthContract#SYSTEM_USER_ID}</li>
 * </ul>
 * 헤더는 직접 읽지 않고 {@link CurrentUserProvider}에 맡긴다 (리졸버·Feign과 같은 해석).
 * 한계: {@code @Async}처럼 요청에서 시작했지만 다른 스레드에서 도는 작업은 시스템으로 기록된다.
 */
public class UserIdAuditorAware implements AuditorAware<UUID> {

    private final CurrentUserProvider currentUserProvider;

    public UserIdAuditorAware(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Optional<UUID> getCurrentAuditor() {
        if (!currentUserProvider.inRequest()) {
            return Optional.of(AuthContract.SYSTEM_USER_ID);
        }
        return currentUserProvider.current().map(AuthUser::id);
    }
}
