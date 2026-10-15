package com.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.audit.UserIdAuditorAware;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 도메인 서비스와 같은 조건(@DataJpaTest 슬라이스 + common auto-configuration)에서
 * BaseEntity의 Audit 컬럼이 채워지는지 검증한다.
 */
@DataJpaTest
class BaseEntityAuditingTest {

    @Autowired
    private EntityManager em;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsCreatedAndUpdatedFromHeader() {
        UUID userId = UUID.randomUUID();
        bindUser(userId);

        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("업체"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo(userId);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    void keepsCreatedByWhenUpdatedByAnotherUser() {
        UUID creatorId = UUID.randomUUID();
        bindUser(creatorId);
        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("업체"));

        LocalDateTime createdUpdatedAt = saved.getUpdatedAt();

        UUID editorId = UUID.randomUUID();
        bindUser(editorId);
        saved.rename("수정된 업체");
        em.flush();
        em.clear();
        SampleAuditEntity updated = em.find(SampleAuditEntity.class, saved.getId());

        assertThat(updated.getCreatedBy()).isEqualTo(creatorId);
        assertThat(updated.getUpdatedBy()).isEqualTo(editorId);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdUpdatedAt);
    }

    @Test
    void keepsManualCreatedByWithoutHeader() {
        // 회원가입처럼 X-User-Id가 없는 요청은 생성자를 직접 지정
        UUID selfId = UUID.randomUUID();

        SampleAuditEntity saved = persistAndReload(SampleAuditEntity.createdBy("업체", selfId));

        assertThat(saved.getCreatedBy()).isEqualTo(selfId);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createdByIsNullForSystemTask() {
        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("시드 업체"));

        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsSoftDelete() {
        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("업체"));
        UUID actorId = UUID.randomUUID();

        saved.delete(actorId);
        em.flush();
        em.clear();
        SampleAuditEntity deleted = em.find(SampleAuditEntity.class, saved.getId());

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedBy()).isEqualTo(actorId);
    }

    private SampleAuditEntity persistAndReload(SampleAuditEntity entity) {
        em.persist(entity);
        em.flush();
        em.clear();
        return em.find(SampleAuditEntity.class, entity.getId());
    }

    private void bindUser(UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserIdAuditorAware.USER_ID_HEADER, userId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
