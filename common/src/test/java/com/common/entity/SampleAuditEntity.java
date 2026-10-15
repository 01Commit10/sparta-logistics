package com.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// BaseEntity 검증용 테스트 엔티티
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SampleAuditEntity extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    SampleAuditEntity(String name) {
        this.name = name;
    }

    static SampleAuditEntity createdBy(String name, UUID actorId) {
        SampleAuditEntity entity = new SampleAuditEntity(name);
        entity.recordCreatedBy(actorId);
        return entity;
    }

    void rename(String name) {
        this.name = name;
    }

    void delete(UUID actorId) {
        softDelete(actorId);
    }

    void reactivate() {
        restore();
    }
}
