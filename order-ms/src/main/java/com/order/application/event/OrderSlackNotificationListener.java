package com.order.application.event;

import com.common.notification.SlackMessage;
import com.common.notification.SlackNotifier;
import com.order.domain.client.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderSlackNotificationListener {
    private final SlackNotifier slackNotifier;
    private final UserClient userClient;

    // 커밋 성공 후에만 실행.
    // send는 즉시 반환하고, 메시지 조립(slackId 조회 포함)과 발송은 알림 스레드에서
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        String orderId = event.orderId().toString();
        slackNotifier.send(orderId, () -> SlackMessage.builder("주문 완료")
                .field("주문 ID", orderId)
                .field("상품 ID", event.productId().toString())
                .field("수량", String.valueOf(event.quantity()))
                .field("배송 ID", event.deliveryId().toString())
                .field("요청사항", event.hasRequestNote() ? "있음" : "없음")
                .mention(userClient.findSlackId(event.orderedBy()).orElse(null))
                .reference(orderId)
                .build());
    }
}
