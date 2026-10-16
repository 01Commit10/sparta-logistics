package com.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.auth.AuthContract;
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
 * BaseEntity의 Audit 컬럼이 채워지는지 검증.
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
        // 회원가입처럼 X-User-*가 없는 요청(요청 안)은 생성자를 직접 지정
        bindAnonymousRequest();
        UUID selfId = UUID.randomUUID();

        SampleAuditEntity saved = persistAndReload(SampleAuditEntity.createdBy("업체", selfId));

        assertThat(saved.getCreatedBy()).isEqualTo(selfId);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void systemTaskRecordsSystemUser() {
        // 요청 밖(스케줄러·기동·시드)의 작업자는 시스템 UUID (C01 D7)
        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("시드 업체"));

        assertThat(saved.getCreatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void systemEditReplacesPreviousEditor() {
        // 시스템이 수정하면 updated_by가 이전 사용자로 남지 않는다 (예전 NULL 정책의 문제)
        UUID userId = UUID.randomUUID();
        bindUser(userId);
        SampleAuditEntity saved = persistAndReload(new SampleAuditEntity("업체"));

        RequestContextHolder.resetRequestAttributes();
        saved.rename("스케줄러가 수정한 업체");
        em.flush();
        em.clear();
        SampleAuditEntity updated = em.find(SampleAuditEntity.class, saved.getId());

        assertThat(updated.getCreatedBy()).isEqualTo(userId);
        assertThat(updated.getUpdatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
    }

    @Test
    void systemOverridesManualCreatedByOutsideRequest() {
        // 요청 밖에서는 직접 지정한 생성자보다 시스템이 우선한다 (Auditing은 작업자가 있으면 덮어씀)
        SampleAuditEntity saved = persistAndReload(SampleAuditEntity.createdBy("업체", UUID.randomUUID()));

        assertThat(saved.getCreatedBy()).isEqualTo(AuthContract.SYSTEM_USER_ID);
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
        request.addHeader(AuthContract.HEADER_USER_ID, userId.toString());
        request.addHeader(AuthContract.HEADER_USER_ROLE, "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void bindAnonymousRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
}
