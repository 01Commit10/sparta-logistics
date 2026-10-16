package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.TokenPairResult;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.TokenIssuer;
import com.user.domain.token.TokenStore;
import org.springframework.stereotype.Service;

/**
 * 토큰 발급·상태 기록 유스케이스 (token 도메인). DB를 쓰지 않으므로 트랜잭션이 없다.
 */
@Service
public class TokenService {

    private final TokenIssuer tokenIssuer;
    private final TokenStore tokenStore;

    public TokenService(TokenIssuer tokenIssuer, TokenStore tokenStore) {
        this.tokenIssuer = tokenIssuer;
        this.tokenStore = tokenStore;
    }

    /** Access·Refresh를 발급하고 Refresh jti를 저장한다. 새로 로그인하면 이전 Refresh는 무효가 된다 (세션 1개) */
    public TokenPairResult issue(AuthenticatedUser user) {
        IssuedToken access = tokenIssuer.issueAccess(
                new AccessTokenSpec(user.id(), user.role(), user.passwordChangeRequired()));
        IssuedToken refresh = tokenIssuer.issueRefresh(user.id());
        tokenStore.saveRefresh(user.id(), refresh.jti(), refresh.lifetime());
        return new TokenPairResult(access.value(), refresh.value());
    }
}
