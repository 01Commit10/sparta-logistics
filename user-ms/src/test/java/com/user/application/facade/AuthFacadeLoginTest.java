package com.user.application.facade;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
import com.user.domain.entity.User;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeTokenIssuer;
import com.user.fake.FakeTokenStore;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인 흐름: 인증 → 토큰 발급 (U05 테스트 5).
 */
class AuthFacadeLoginTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final AuthFacade facade = new AuthFacade(
            new UserQueryService(userRepository, passwordHasher), new TokenService(tokenIssuer, tokenStore, Clock.systemUTC()));

    private User savedMaster() {
        User master = User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자",
                passwordHasher.hash("Master!2345"), "M001");
        master.requirePasswordChange(passwordHasher.hash("Temp123!"));
        return userRepository.save(master);
    }

    @Test
    @DisplayName("인증에 성공하면 두 토큰과 비밀번호 변경 필요 여부를 돌려주고 Refresh jti를 저장한다")
    void success() {
        User master = savedMaster();

        LoginResult result = facade.login(new LoginCommand("master01", "Temp123!"));

        assertThat(result.accessToken()).startsWith("access-");
        assertThat(result.refreshToken()).isEqualTo(tokenIssuer.lastRefresh().value());
        assertThat(result.passwordChangeRequired()).isTrue();
        assertThat(tokenStore.refreshOf(master.getId())).isPresent();
    }

    @Test
    @DisplayName("인증에 실패하면 토큰을 발급하지도 저장하지도 않는다")
    void failureIssuesNothing() {
        User master = savedMaster();

        assertThatThrownBy(() -> facade.login(new LoginCommand("master01", "Wrong123!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS));
        assertThat(tokenIssuer.issuedCount()).isZero();
        assertThat(tokenStore.refreshOf(master.getId())).isEmpty();
    }
}
