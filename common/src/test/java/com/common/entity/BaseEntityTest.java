package com.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    void newEntityIsNotDeleted() {
        SampleAuditEntity entity = new SampleAuditEntity("업체");

        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.getDeletedBy()).isNull();
    }

    @Test
    void softDeleteRecordsTimeAndActor() {
        SampleAuditEntity entity = new SampleAuditEntity("업체");
        UUID actorId = UUID.randomUUID();

        entity.delete(actorId);

        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isNotNull();
        assertThat(entity.getDeletedBy()).isEqualTo(actorId);
    }

    @Test
    void softDeleteAgainKeepsFirstDeletion() {
        SampleAuditEntity entity = new SampleAuditEntity("업체");
        UUID firstActorId = UUID.randomUUID();
        entity.delete(firstActorId);
        LocalDateTime firstDeletedAt = entity.getDeletedAt();

        entity.delete(UUID.randomUUID());

        assertThat(entity.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(entity.getDeletedBy()).isEqualTo(firstActorId);
    }

    @Test
    void restoreClearsDeletedTimeAndActor() {
        SampleAuditEntity entity = new SampleAuditEntity("업체");
        entity.delete(UUID.randomUUID());

        entity.reactivate();

        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.getDeletedBy()).isNull();
    }
}
