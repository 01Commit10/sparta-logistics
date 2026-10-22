package com.common.notification.slack;

import com.common.notification.SlackMessage;
import com.common.notification.SlackNotifier;

import java.util.function.Supplier;

/**
 * Webhook URL이 설정되지 않은 환경(팀원 로컬 등)의 알림 (N13). 아무것도 보내지 않는다.
 * supplier도 호출하지 않아 메시지 조립용 원격 조회가 일어나지 않는다(P9).
 */
public class NoOpSlackNotifier implements SlackNotifier {

    @Override
    public void send(SlackMessage message) {
    }

    @Override
    public void send(String reference, Supplier<SlackMessage> message) {
    }
}
