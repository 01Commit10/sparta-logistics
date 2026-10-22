package com.common.notification;

/**
 * Slack 메시지의 "라벨: 값" 한 줄. 값이 없으면 "-"로 표시한다.
 * Slack 문법(이스케이프·길이 제한)은 렌더러가 처리하므로 여기에는 평문을 넣는다.
 */
public record SlackField(String label, String value) {

    public SlackField {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label은 비어 있을 수 없습니다");
        }
        if (value == null) {
            value = "-";
        }
    }
}
