package com.user.domain.token;

import java.util.UUID;

/**
 * 토큰 발급 Port. 구현은 infrastructure.jwt (jjwt, HS256).
 */
public interface TokenIssuer {

    IssuedToken issueAccess(AccessTokenSpec spec);

    /** Refresh Token에는 role·pcr을 넣지 않는다 (재발급 때 DB에서 다시 읽음) */
    IssuedToken issueRefresh(UUID userId);
}
