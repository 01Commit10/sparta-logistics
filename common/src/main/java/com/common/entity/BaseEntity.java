package com.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 공통 Audit 컬럼 6개 (docs/design/06 테이블 명세서 "공통 Audit 컬럼", 07 서비스 정책 1.3·1.4).
 * - 생성·수정 시각/작업자: JPA Auditing이 자동 기록 (CommonJpaAuditingAutoConfiguration)
 *   작업자는 Gateway가 넣은 X-User-Id. 헤더가 없으면(시스템 작업, 회원가입) 기록하지 않는다.
 * - 삭제 시각/작업자: Auditing 대상이 아니므로 softDelete()로 명시적으로 기록
 * - 삭제 데이터 조회 제외(deleted_at IS NULL)는 Repository에서 처리한다.
 *   (재활성화 등 삭제 데이터 조회가 필요한 도메인이 있어 @SQLRestriction을 두지 않음)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column
    private UUID updatedBy;

    @Column
    private LocalDateTime deletedAt;

    @Column
    private UUID deletedBy;

    // X-User-Id가 없는 요청에서 생성자를 직접 지정 (예: 회원가입은 본인 ID).
    // 헤더가 있으면 Auditing이 헤더 값으로 덮어쓴다.
    protected void recordCreatedBy(UUID actorId) {
        this.createdBy = actorId;
    }

    protected void softDelete(UUID actorId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actorId;
    }

    protected void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
