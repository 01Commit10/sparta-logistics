package com.order.domain.entity;

import com.common.entity.BaseEntity;
import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderOperationStatus;
import com.order.domain.vo.OrderOperationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

@Entity
@Table(name = "p_order_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // NULL (생성 전)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OrderOperationType operationType;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false, length = 20)
    private OrderOperationStatus operationStatus;

    @Column(name = "response_code")
    private Integer responseCode;

    @ColumnDefault("0")
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // last_error | TEXT | NULL
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;


    public static OrderHistory create(OrderOperationType operationType, String idempotencyKey, String requestHash) {
        OrderHistory history = new OrderHistory();
        history.operationType = operationType;
        history.idempotencyKey = idempotencyKey;
        history.requestHash = requestHash;
        history.operationStatus = OrderOperationStatus.IN_PROGRESS;
        history.retryCount = 0;
        return history;
    }

    // 201 들어오면 성공
    public void succeed(int responseCode) {
        if (operationStatus.isCompleted()) {
            throw new BusinessException(OrderErrorCode.HISTORY_ALREADY_COMPLETED);
        }
        this.operationStatus = OrderOperationStatus.SUCCEEDED;
        this.responseCode = responseCode;
    }










/*    assertThat(history.getOperationStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
    assertThat(history.getOperationType()).isEqualTo(OperationType.ORDER_CREATE);
    assertThat(history.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    assertThat(history.getRequestHash()).isEqualTo(REQUEST_HASH);
    assertThat(history.getOrderId()).isNull();
    assertThat(history.getRetryCount()).isZero();*/
}
