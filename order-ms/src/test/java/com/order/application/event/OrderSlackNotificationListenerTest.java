package com.order.application.event;

import com.common.notification.SlackField;
import com.common.notification.SlackMessage;
import com.common.notification.SlackNotifier;
import com.order.domain.client.UserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderSlackNotificationListenerTest {

    @Mock
    private SlackNotifier slackNotifier;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private OrderSlackNotificationListener listener;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ORDERED_BY = UUID.randomUUID();

    private OrderCompletedEvent event() {
        return new OrderCompletedEvent(ORDER_ID, UUID.randomUUID(), 3, UUID.randomUUID(), ORDERED_BY, true);
    }

    // send에 넘긴 Supplier를 잡아서 실제로 메시지를 만들어 본다
    @SuppressWarnings("unchecked")
    private SlackMessage capturedMessage() {
        ArgumentCaptor<Supplier<SlackMessage>> captor = ArgumentCaptor.forClass(Supplier.class);
        then(slackNotifier).should().send(eq(ORDER_ID.toString()), captor.capture());
        return captor.getValue().get();
    }

    @Test
    @DisplayName("주문 완료 알림에 주문 정보와 주문자 멘션이 들어간다")
    void sendWithMention() {
        given(userClient.findSlackId(ORDERED_BY)).willReturn(Optional.of("U07ABCDEF12"));

        listener.onOrderCompleted(event());

        SlackMessage message = capturedMessage();
        assertThat(message.title()).isEqualTo("주문 완료");
        assertThat(message.mentionSlackId()).isEqualTo("U07ABCDEF12");
        assertThat(message.fields()).contains(new SlackField("요청사항", "있음"));
    }

    @Test
    @DisplayName("주문자 slackId를 못 찾으면 멘션 없이 보낸다")
    void sendWithoutMention() {
        given(userClient.findSlackId(ORDERED_BY)).willReturn(Optional.empty());

        listener.onOrderCompleted(event());

        assertThat(capturedMessage().mentionSlackId()).isNull();
    }
}
