package com.delivery.domain.entity;

import com.common.entity.BaseEntity;
import com.delivery.domain.vo.DeliveryOperationStatus;
import com.delivery.domain.vo.DeliveryOperationType;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "p_delivery_histories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_histories_operation_type_idempotency_key",
                columnNames = {"operation_type", "idempotency_key"}))
public class DeliveryHistory extends BaseEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "operation_type", nullable = false, length = 32)
    private DeliveryOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "operation_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryOperationStatus operationStatus;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
