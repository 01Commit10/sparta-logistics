package com.user.application.facade;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
import com.user.domain.entity.User;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeRefreshTokenParser;
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
 * 로그인 흐름: ID 조회 → 무효화 기준값 → 자격 조회 → 비밀번호 비교(트랜잭션 밖) → 토큰 발급 (U05 테스트 1·2·5).
 * 발급 도중 무효화가 끼어드는 경우는 AuthIssueRaceTest.
 */
class AuthFacadeLoginTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTokenIssuer tokenIssuer = new FakeTokenIssuer();
    private final FakeTokenStore tokenStore = new FakeTokenStore();
    private final AuthFacade facade = new AuthFacade(
            new UserQueryService(userRepository), new TokenService(tokenIssuer, new FakeRefreshTokenParser(), tokenStore, Clock.systemUTC()), passwordHasher);

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

    private User savedUser(String username, String rawPassword) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동",
                passwordHasher.hash(rawPassword), "U-" + username, OrganizationType.COMPANY, "스파르타상사"));
    }

    private void assertInvalidCredentials(String username, String rawPassword) {
        assertThatThrownBy(() -> facade.login(new LoginCommand(username, rawPassword)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS));
        assertThat(tokenIssuer.issuedCount()).isZero();
    }

    @Test
    @DisplayName("1. Access에는 사용자 ID·역할·비밀번호 변경 필요 여부를 담는다 (USER, 변경 불필요)")
    void accessSpecOfUser() {
        User user = savedUser("user01", "Abcd123!");

        LoginResult result = facade.login(new LoginCommand("user01", "Abcd123!"));

        assertThat(tokenIssuer.accessSpecs()).containsExactly(new AccessTokenSpec(user.getId(), "USER", false));
        assertThat(result.passwordChangeRequired()).isFalse();
    }

    @Test
    @DisplayName("1. MASTER는 역할이 MASTER로 나온다")
    void accessSpecOfMaster() {
        User master = savedMaster();

        facade.login(new LoginCommand("master01", "Temp123!"));

        assertThat(tokenIssuer.accessSpecs()).containsExactly(new AccessTokenSpec(master.getId(), "MASTER", true));
    }

    @Test
    @DisplayName("2. 없는 ID는 INVALID_CREDENTIALS, 비밀번호를 비교하지 않는다")
    void unknownUsername() {
        savedUser("user01", "Abcd123!");
        passwordHasher.duringNextMatches(() -> {
            throw new AssertionError("없는 ID인데 비밀번호를 비교했다");
        });

        assertInvalidCredentials("user02", "Abcd123!");
    }

    @Test
    @DisplayName("2. 틀린 비밀번호는 INVALID_CREDENTIALS")
    void wrongPassword() {
        savedUser("user01", "Abcd123!");

        assertInvalidCredentials("user01", "Abcd123?");
    }

    @Test
    @DisplayName("2. 탈퇴·비활성화된 계정은 비밀번호가 맞아도 INVALID_CREDENTIALS")
    void withdrawn() {
        User user = savedUser("user01", "Abcd123!");
        user.withdraw(user.getId());

        assertInvalidCredentials("user01", "Abcd123!");
    }

    @Test
    @DisplayName("2. 가입 규칙에 맞지 않는 ID도 형식 오류가 아니라 INVALID_CREDENTIALS (로그인에서 가입 규칙을 드러내지 않음)")
    void malformedUsername() {
        savedUser("user01", "Abcd123!");

        assertInvalidCredentials("USER01", "Abcd123!");
        assertInvalidCredentials("u", "Abcd123!");
    }
}
