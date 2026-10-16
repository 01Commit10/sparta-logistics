package com.user.infrastructure.persistence;

import com.common.auth.AuthContract;
import com.user.domain.entity.User;
import com.user.domain.repository.UserRepository;
import com.user.domain.vo.OrganizationType;
import com.user.domain.vo.UserRole;
import com.user.domain.vo.Username;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 컬럼은 common BaseEntity + JPA Auditing이 채운다 (07 서비스 정책 1.4, C01 D7).
 * 요청 안 + 헤더 → 그 사용자 / 요청 안 + 헤더 없음 → 직접 지정한 값 유지 / 요청 밖 → 시스템 UUID
 */
@DataJpaTest
@Import(UserRepositoryImpl.class)
class UserAuditingTest {

    static final UUID MASTER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager em;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    static void bindAnonymousRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    static void bindMasterRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthContract.HEADER_USER_ID, MASTER_ID.toString());
        request.addHeader(AuthContract.HEADER_USER_ROLE, "MASTER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    static User signupUser() {
        return User.signup(UUID.randomUUID(), Username.of("user01"), "홍길동", "hashed", "U001",
                OrganizationType.COMPANY, "스파르타상사");
    }

    User saveAndReload(User user) {
        userRepository.save(user);
        em.flush();
        em.clear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    @DisplayName("회원가입(요청 안, 헤더 없음): created_by는 본인, updated_by는 비어 있고 두 시각은 기록된다")
    void signup() {
        bindAnonymousRequest();

        User saved = saveAndReload(signupUser());

        assertThat(saved.getCreatedBy()).isEqualTo(saved.getId());
        assertThat(saved.getUpdatedBy()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("초기 MASTER(요청 밖, 기동 시): created_by·updated_by는 시스템 UUID")
    void initialMaster() {
        User saved = saveAndReload(User.initialMaster(UUID.randomUUID(), Username.of("master01"), "관리자",
                "hashed", "M001"));

        assertThat(saved.getCreatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
    }

    @Test
    @DisplayName("MASTER가 만든 계정(요청 안, MASTER 헤더): created_by·updated_by는 그 MASTER")
    void createByMaster() {
        bindMasterRequest();

        User saved = saveAndReload(User.createByMaster(UUID.randomUUID(), Username.of("user02"), "김배송",
                "hashed", "U002", OrganizationType.HUB, "서울센터", UserRole.USER, true, MASTER_ID));

        assertThat(saved.getCreatedBy()).isEqualTo(MASTER_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("MASTER가 수정하면 updated_by는 그 MASTER로 바뀌고 created_by(본인)는 남는다")
    void updatedByMaster() {
        bindAnonymousRequest();
        User saved = saveAndReload(signupUser());

        bindMasterRequest();
        saved.changeRole(UserRole.MASTER);
        userRepository.save(saved);
        em.flush();
        em.clear();
        User updated = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(updated.getCreatedBy()).isEqualTo(saved.getId());
        assertThat(updated.getUpdatedBy()).isEqualTo(MASTER_ID);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
    }
}
