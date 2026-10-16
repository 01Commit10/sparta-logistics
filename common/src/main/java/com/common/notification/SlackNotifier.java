package com.common.notification;

import java.util.function.Supplier;

/**
 * Slack 채널 알림 (NTF, 설계 3.2). 언제·누구에게·무엇을 보낼지는 호출하는 서비스가 정하고, 발송은 이 인터페이스가 맡는다.
 * 두 메서드 모두 즉시 반환하고 예외를 던지지 않는다. 실패는 WARN 로그로만 남고 재시도하지 않는다(N5).
 * Webhook URL이 설정되지 않은 환경에서는 아무것도 하지 않는다(N13).
 */
public interface SlackNotifier {

    void send(SlackMessage message);

    /**
     * 메시지 조립(예: 사용자 slackId 원격 조회)까지 알림 스레드에서 한다. supplier 예외도 WARN으로 끝난다.
     *
     * @param reference 로그 추적용 값(예: 주문 ID). 메시지를 만들기 전에 실패해도 로그에 남는다
     */
    void send(String reference, Supplier<SlackMessage> message);
}
