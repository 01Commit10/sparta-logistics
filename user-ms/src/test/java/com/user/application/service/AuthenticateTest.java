package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.UserCredential;
import com.user.domain.entity.User;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.Username;
import com.user.fake.FakePasswordHasher;
import com.user.fake.FakeUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USR-02 로그인의 조회 단계 (U05 테스트 1~2). 비밀번호 비교와 INVALID_CREDENTIALS는 AuthFacadeLoginTest.
 * 조회는 읽기 트랜잭션에서, BCrypt 비교는 트랜잭션 밖에서 하도록 둘을 나눴다 (리뷰: readOnly 트랜잭션 안의 BCrypt).
 */
class AuthenticateTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final UserQueryService service = new UserQueryService(userRepository);

    private User saved(String username, String rawPassword) {
        return userRepository.save(User.signup(UUID.randomUUID(), Username.of(username), "홍길동",
                passwordHasher.hash(rawPassword), "U-" + username, OrganizationType.COMPANY, "스파르타상사"));
    }

    @Test
    @DisplayName("활성 사용자의 username으로 ID를 찾는다")
    void findsActiveId() {
        User user = saved("user01", "Abcd123!");

        assertThat(service.findActiveIdByUsername("user01")).contains(user.getId());
    }

    @Test
    @DisplayName("없는 ID·탈퇴(비활성화) 계정·가입 규칙에 맞지 않는 ID는 empty (형식 오류를 내지 않음)")
    void unknownOrWithdrawn() {
        User user = saved("user01", "Abcd123!");
        saved("user02", "Abcd123!").withdraw(user.getId());

        assertThat(service.findActiveIdByUsername("user03")).isEmpty();
        assertThat(service.findActiveIdByUsername("user02")).isEmpty();
        assertThat(service.findActiveIdByUsername("USER01")).isEmpty();
        assertThat(service.findActiveIdByUsername("u")).isEmpty();
    }

    @Test
    @DisplayName("자격 조회: 저장된 해시와 토큰에 넣을 ID·역할·비밀번호 변경 필요 여부")
    void credential() {
        User user = saved("user01", "Abcd123!");
        user.requirePasswordChange(passwordHasher.hash("Temp123!"));

        UserCredential credential = service.findActiveCredential(user.getId()).orElseThrow();

        assertThat(credential.user()).isEqualTo(new AuthenticatedUser(user.getId(), "USER", true));
        assertThat(credential.passwordHash()).isEqualTo("hashed:Temp123!");
    }

    @Test
    @DisplayName("자격 조회: 탈퇴(비활성화)한 사용자는 empty")
    void credentialOfWithdrawn() {
        User user = saved("user01", "Abcd123!");
        user.withdraw(user.getId());

        assertThat(service.findActiveCredential(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("자격 조회 결과의 toString에 해시가 나오지 않는다 (로그 실수 방지)")
    void credentialToStringMasksHash() {
        User user = saved("user01", "Abcd123!");

        assertThat(service.findActiveCredential(user.getId()).orElseThrow().toString())
                .doesNotContain("hashed:").contains("***");
    }
}
