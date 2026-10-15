package com.user.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 공통 Audit 컬럼 6개 (docs/design/06 테이블 명세서 "공통 Audit 컬럼").
 *
 * <p>임시 구현: 공통 모듈의 BaseAuditEntity(권우)가 생기면 그것으로 교체한다.
 * 그때 updatedBy는 AuditorAware(X-User-Id)가 채우고, createdBy를 직접 지정하는 회원가입(본인 ID)과
 * 시스템 작업(NULL)이 덮어써지지 않는지 확인한다.
 */
@Getter
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected void recordCreatedBy(UUID actorId) {
        this.createdBy = actorId;
    }

    protected void recordUpdatedBy(UUID actorId) {
        this.updatedBy = actorId;
    }

    protected void markDeleted(UUID actorId, LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.deletedBy = actorId;
    }

    protected void clearDeleted() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
