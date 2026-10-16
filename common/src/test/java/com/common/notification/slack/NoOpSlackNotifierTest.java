package com.common.notification.slack;

import com.common.notification.SlackMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpSlackNotifierTest {

    final NoOpSlackNotifier notifier = new NoOpSlackNotifier();

    @Test
    @DisplayName("6-1. 아무것도 보내지 않고 예외도 없다")
    void doesNothing() {
        assertThatCode(() -> notifier.send(SlackMessage.builder("주문 완료").build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("6-1. supplier를 호출하지 않는다 (URL 없는 환경에서 불필요한 원격 조회 방지, P9)")
    void doesNotCallSupplier() {
        AtomicInteger calls = new AtomicInteger();

        notifier.send("order-1", () -> {
            calls.incrementAndGet();
            return SlackMessage.builder("주문 완료").build();
        });

        assertThat(calls).hasValue(0);
    }
}
