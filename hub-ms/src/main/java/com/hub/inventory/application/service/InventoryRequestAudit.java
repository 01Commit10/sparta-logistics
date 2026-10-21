package com.hub.inventory.application.service;

import com.common.entity.BaseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Capture the existing JPA auditing policy on the request thread, before handing work to a Consumer. */
@Component
@ConditionalOnProperty(name="inventory.stock.mode",havingValue="redis-streams")
public class InventoryRequestAudit {
    private final AuditingHandler handler;
    public InventoryRequestAudit(@Qualifier("jpaAuditingHandler") AuditingHandler handler) { this.handler=handler; }
    public Stamp capture() {
        AuditEntity value=handler.markCreated(new AuditEntity());
        return new Stamp(micros(value.getCreatedAt()),value.getCreatedBy(),micros(value.getUpdatedAt()),value.getUpdatedBy());
    }
    private LocalDateTime micros(LocalDateTime value) { return value==null?null:value.truncatedTo(ChronoUnit.MICROS); }
    public record Stamp(LocalDateTime createdAt, UUID createdBy, LocalDateTime updatedAt, UUID updatedBy) { }
    private static class AuditEntity extends BaseEntity { }
}
