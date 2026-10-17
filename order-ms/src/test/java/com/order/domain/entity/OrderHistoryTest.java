package com.order.domain.entity;

import com.common.exception.BusinessException;
import com.common.exception.OrderErrorCode;
import com.order.domain.vo.OrderOperationStatus;
import com.order.domain.vo.OrderOperationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/*
 * 일반적인 주문 상태 변경 이력이 아니라, API 요청 단위의 멱등성과 처리 결과를 관리하는 모델이다.
 * */
public class OrderHistoryTest {

    private static final String IDEMPOTENCY_KEY = "order-req-0001";
    private static final String REQUEST_HASH = "a".repeat(64);


    private OrderHistory createHistory() {
        return OrderHistory.create(OrderOperationType.CREATE, IDEMPOTENCY_KEY, REQUEST_HASH);
    }

    // 주문 생성 전 단계 : IN_PROGRESS, orderId 없음, 재시도 0
    @Test
    @DisplayName("이력을 생성하면 상태는 IN_PROGRESS이고, 주문은 연결되지 않았으며, 재시도 횟수는 0이다.")
    void startHistorySuccess() {
        OrderHistory history = createHistory();

        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.IN_PROGRESS);
        assertThat(history.getOrder()).isNull();
        assertThat(history.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("성공 처리하면 상태는 SUCCEEDED 고 응답 코드는 201을 내려준다.")
    void succeededHistorySuccess() {
        OrderHistory history = createHistory();
        history.succeed(201);

        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.SUCCEEDED);
        assertThat(history.getResponseCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("실패 처리하면 상태는 FAILED이고 응답 코드와 에러 내용이 기록된다.")
    void failHistorySuccess() {
        OrderHistory history = createHistory();
        history.fail(409, "재고 부족");

        assertThat(history.getOperationStatus()).isEqualTo(OrderOperationStatus.FAILED);
        assertThat(history.getResponseCode()).isEqualTo(409);
        assertThat(history.getLastError()).isEqualTo("재고 부족");
    }

    @Test
    @DisplayName("이미 완료된 이력을 다시 성공, 실패 처리 할 수 없다,")
    void completeHistoryFailAlreadyCompleted() {
        OrderHistory history = createHistory();
        history.succeed(201);

        // 같은 규칙이러 함께 테스트 진행
        assertThatThrownBy(() -> history.succeed(201)).asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.HISTORY_ALREADY_COMPLETED);

        assertThatThrownBy(() -> history.succeed(409)).asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(OrderErrorCode.HISTORY_ALREADY_COMPLETED);
    }


}
